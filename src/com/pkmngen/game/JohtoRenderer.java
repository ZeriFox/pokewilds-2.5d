package com.pkmngen.game;

import com.pkmngen.game.WorldMeshBatch.Buffer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.BufferUtils;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Complete perspective presentation of the world and field actions.
 *
 * Coordinates, tile identity, actors, random generators and action execution belong
 * exclusively to Game. This renderer only reads them after the normal map actions.
 * In particular it must never call Tile.init(), setView(), any draw/step action or
 * any method which advances simulation. The legacy world batch never submits pixels
 * in modern mode: this renderer is the world, not an overlay on the old map.
 */
public final class JohtoRenderer {
   private static final float TILE = VisualSampling.WORLD_TILE_SIZE;
   private static final float WHITE = Color.WHITE_FLOAT_BITS;
   private static final String[] ACTOR_DIRECTIONS={"down","up","left","right"};
   private static final String[] LOCOMOTION_ANIMATIONS={"Idle","Walk"};
   private static final float[] CROWN_ROWS={0,.225f,.45f,.5875f,.725f,.8625f,1};
   private static final int CLIFF_CORNER_SEGMENTS=6;
   private final float[] plateauOutline=new float[4*(CLIFF_CORNER_SEGMENTS+1)*2];
   private final PerspectiveCamera camera = new PerspectiveCamera(35f, 160f, 144f);
   private final Vector2 lookup = new Vector2();
   private final WorldMeshBatch geometry = new WorldMeshBatch();
   private final WorldMeshBatch translucent = new WorldMeshBatch();
   private final WorldMeshBatch shadows = new WorldMeshBatch();
   private final WorldMeshBatch emotes = new WorldMeshBatch();
   private final WorldAmbientOverlay ambientOverlays = new WorldAmbientOverlay();
   private final Map<Class<?>, Field> emoteOwners = new IdentityHashMap<>();
   private final ActorModelRenderer actorModels = new ActorModelRenderer();
   private final Map<Object, Motion> motion = new WeakHashMap<>();
   private BwAssets assets;
   private float seconds;
   private float natureSeconds, vegetationBend, crownBend, daylight;
   private boolean reducedMotion;
   private final WorldNatureMotion shore=new WorldNatureMotion();
   private final WorldNatureMotion heightShore=new WorldNatureMotion();
   private Map<Vector2,Tile> natureTiles;
   private Tile heightShoreTile;
   private final TextureRegion spritePart = new TextureRegion();
   private final FloatBuffer originalClearColor = BufferUtils.newFloatBuffer(4);
   private ShaderProgram shader;
   private Texture whiteTexture;
   private TextureRegion white;
   private boolean initialized;
   private boolean failed;
   private boolean reported;
   private long renderedFrames;
   private final float[] lights = new float[8 * 4];
   private final WorldElevation elevation = new WorldElevation();
   private float surfaceLift;
   private float actorPlaneZ;
   private final Vector3 projectedAnchor = new Vector3();
   private boolean captureBedDepth, bedDepthValid, matchBedDepth;
   private float bedBottomScreenY, bedTopScreenY, bedBottomDepth, bedTopDepth;
   private final BoundingBox wallOcclusionBounds = new BoundingBox();
   private final Ray wallOcclusionRay = new Ray();
   private final Vector3 wallOcclusionMin = new Vector3(), wallOcclusionMax = new Vector3(), wallOcclusionTarget = new Vector3();
   private final Color ambientTint = new Color(Color.WHITE), fogColor = new Color();
   private float fogDensity, desaturation;
   private long ghostFrames;
   private long waterfallFrames;
   private long fieldEffectFrames;
   private final Map<Tile, HeadbuttTreeAnim> shakingTrees = new IdentityHashMap<>();
   private final java.util.Set<Vector2> growingPlants = new java.util.HashSet<>();
   private final Map<Action, Boolean> diagnosedGhosts = new WeakHashMap<>();
   private final TextureRegion cutLeft = new TextureRegion(), cutRight = new TextureRegion();
   private final Vector2 companionPosition=new Vector2(),companionGoal=new Vector2();
   private Pokemon presentedCompanion;
   private Map<Vector2,Tile> companionMap;
   private float companionLastTime;
   private long jumpCueFrames,obstacleFootprintFrames,activeCompanionFrames,shinyFrames;
   private long roundedCornerFrames,stairTreadFrames;

   public JohtoRenderer() {
      camera.near = 1f;
      camera.far = 1800f;
   }

   /** False means no world is available; the modern scene compositor owns that frame. */
   public boolean render(Game game) {
      if (failed || !supports(game)) {
         return false;
      }
      try {
         if (!initialized) {
            initialize();
         }
         geometry.clear();
         translucent.clear();
         shadows.clear();
         emotes.clear();
         elevation.update(game.map.tiles);
         configureCamera(game);
         seconds += Math.min(0.1f, Math.max(0f, Gdx.graphics.getDeltaTime()));
         reducedMotion=game.modernUi!=null&&game.modernUi.reducedMotion();
         natureSeconds=reducedMotion?0:seconds;
         collectWorld(game);
         ambientOverlays.prepare(game, elevation, seconds, whiteTexture);
         // Prepare the complete scene before drawing it in one world pass.
         geometry.upload();
         translucent.upload();
         shadows.upload();
         emotes.upload();
         draw(game);
         renderedFrames++;
         if (!reported) {
            Gdx.app.log("JohtoRenderer", "Perspective overworld active: pitch 50 degrees, real upright geometry; simulation unchanged.");
            reported = true;
         }
         return true;
      } catch (RuntimeException ex) {
         failed = true;
         Gdx.app.error("JohtoRenderer", "Modern world rendering failed.", ex);
         // A frozen last-good snapshot hid permanent renderer failures. Keep the
         // error observable to the desktop launcher and native regression tests.
         throw new IllegalStateException("Modern world rendering failed", ex);
      } finally {
         // The following original UI pass uses SpriteBatch and its own projection.
         Gdx.gl.glDepthMask(true);
         Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
         Gdx.gl.glDisable(GL20.GL_CULL_FACE);
         Gdx.gl.glEnable(GL20.GL_BLEND);
         Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
         Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
      }
   }

   public long getRenderedFrames() {
      return renderedFrames;
   }
   public WorldElevation getElevation() { return elevation; }
   public long getGhostFrames() { return ghostFrames; }
   public long getWaterfallFrames() { return waterfallFrames; }
   public long getFieldEffectFrames() { return fieldEffectFrames; }
   public long getJumpCueFrames() { return jumpCueFrames; }
   public long getObstacleFootprintFrames() { return obstacleFootprintFrames; }
   public long getActiveCompanionFrames() { return activeCompanionFrames; }
   public long getShinyFrames() { return shinyFrames; }
   public long getRoundedCornerFrames() { return roundedCornerFrames; }
   public long getStairTreadFrames() { return stairTreadFrames; }
   public float getFogDensity() { return fogDensity; }

   public long getPlayerModelFrames() { return actorModels.getPlayerModelFrames(); }
   public long getPokemonModelFrames() { return actorModels.getPokemonModelFrames(); }
   public long getAnimatedModelFrames() { return actorModels.getAnimatedModelFrames(); }
   public int getLoadedSpeciesCount() { return actorModels.getLoadedSpeciesCount(); }
   public boolean hasLoadedSpecies(String name) { return actorModels.hasLoadedSpecies(name); }

   private boolean supports(Game game) {
      if (game == null || game.map == null || game.player == null || game.cam == null
         || game.map.tiles.isEmpty()
         || game.player.dontDrawMapDuringBattle) {
         return false;
      }
      return game.player.currSprite != null && game.player.currSprite.getTexture() != null;
   }

   private void initialize() {
      String vertex = "attribute vec3 a_position;\n"
         + "attribute vec4 a_color;\nattribute vec2 a_texCoord0;\nattribute float a_style;\nattribute float a_anchorDepth;\n"
         + "uniform mat4 u_projTrans;\nvarying vec4 v_color;\nvarying vec2 v_texCoords;\nvarying float v_style;\nvarying vec2 v_world;\nvarying vec3 v_position;\nvarying float v_shore;\n"
         + "void main(){v_color=a_color;v_color.a*=255.0/254.0;v_texCoords=a_texCoord0;v_style=a_style;"
         + "v_world=a_position.xz;v_position=a_position;v_shore=a_anchorDepth;gl_Position=u_projTrans*vec4(a_position,1.0);"
         + "if(a_style>3.5)gl_Position.z=a_anchorDepth*gl_Position.w;}\n";
      String fragment = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
         + "uniform sampler2D u_texture;\nuniform vec3 u_tint;\nuniform vec4 u_lights[8];uniform vec3 u_cameraPosition;uniform float u_daylight;\nvarying vec2 v_world;varying vec3 v_position;\n"
         + "uniform vec3 u_fogColor;uniform float u_fogDensity;uniform float u_desaturation;uniform float u_time;\n"
         + "varying vec4 v_color;\nvarying vec2 v_texCoords;\nvarying float v_style;\nvarying float v_shore;\n"
         + "void main(){vec4 c=texture2D(u_texture,v_texCoords);"
         + "c*=v_color;vec3 materialNormal=vec3(0.0,1.0,0.0);float wetness=0.0;"
         + "vec3 viewDirection=normalize(u_cameraPosition-v_position);"
         + "if(v_style>.5&&v_style<1.5){float l=dot(c.rgb,vec3(.299,.587,.114));"
         + "c.rgb=mix(mix(c.rgb,vec3(l),.30),vec3(.37,.49,.28),.42);}"
         + "if(v_style>4.5){float l=dot(c.rgb,vec3(.299,.587,.114));c.rgb=mix(c.rgb,vec3(l),.20);}"
         // A dedicated repeating molten material; only magma channels glow.
         // Its world UVs are not shared with an atlas or with ordinary floors.
         + "if(v_style>3.05&&v_style<3.3){c=texture2D(u_texture,v_texCoords+vec2(u_time*.0025,-u_time*.0016))*v_color;"
         + "float hot=smoothstep(.18,.55,c.r-max(c.b,c.g*.52));"
         + "float pulse=.92+.08*sin(dot(v_world,vec2(.07,.045))-u_time*.8);"
         + "c.rgb*=mix(.95,pulse,hot);c.rgb+=vec3(.14,.048,.002)*hot*(.5+.5*sin(v_world.x*.17-v_world.y*.13-u_time*1.1));}"
         // Negative styles are actual fluid/beach surfaces. A single world-space
         // field continues across atlas cells; no UV scrolling crosses sprite bounds.
         + "if(v_style<-2.5){vec2 p=vec2(v_world.x,-v_world.y);vec2 local=mod(p,16.0)-8.0;"
         + "float mask=0.0;float wave=sin(dot(p,vec2(.24,.16))-u_time*1.45);"
         + "float noise=.30*sin(dot(p,vec2(.19,.31)))+.20*sin(dot(p,vec2(-.37,.14)));"
         + "if(v_style>-3.6){float bits=floor((-v_style-3.0)*32.0+.1);"
         + "float left=mod(floor(bits/4.0),2.0),right=mod(floor(bits/8.0),2.0);"
         + "float down=mod(bits,2.0),up=mod(floor(bits/2.0),2.0);"
         + "vec2 q=abs(local)-5.2;"
         + "if((local.x<0.0&&left>.5)||(local.x>=0.0&&right>.5))q.x=-2.8;"
         + "if((local.y<0.0&&down>.5)||(local.y>=0.0&&up>.5))q.y=-2.8;"
         + "float edge=length(max(q,0.0))+min(max(q.x,q.y),0.0)-2.8;"
         + "mask=1.0-smoothstep(-1.45,.05,edge+noise);"
         + "float lip=1.0-smoothstep(.10,.55,abs(edge+1.0+.35*wave));"
         + "c.rgb=mix(c.rgb,vec3(.82,.93,.89),lip*.28);"
         + "}else{vec2 bank=local+vec2(.90*sin(dot(p,vec2(.41,.29))),.70*sin(dot(p,vec2(-.33,.47))));vec2 oval=bank/vec2(6.2,4.7);"
         + "float edge=dot(oval,oval)+noise*.38;mask=1.0-smoothstep(.64,1.0,edge);"
         + "c.rgb=mix(c.rgb,vec3(.77,.89,.88),smoothstep(.89,.98,sin(length(local)*2.6-u_time*1.8))*.16);}"
         + "c.rgb=mix(c.rgb,vec3(.22,.53,.59),.23);c.a*=mask*(.84+.10*wave);"
         + "materialNormal=normalize(vec3(-.12*cos(p.x*.24-u_time),1.0,.10*sin(p.y*.27+u_time)));wetness=.72;"
         + "}else if(v_style<-.5){vec2 p=vec2(v_world.x,-v_world.y);"
         // Move the painted water as well as its lighting: a slow single UV drift
         // looked frozen beside the much larger stationary texture crests.
         // Both currents are world-continuous and the refraction stays below
         // one world pixel, so banks, tiles and sprite silhouettes never swim.
         + "if(v_style> -1.5){vec2 refract=vec2(sin(dot(p,vec2(.09,.06))-u_time*1.30),"
         + "cos(dot(p,vec2(-.07,.11))+u_time*.90))*.0075;"
         + "vec2 current=vec2(u_time*.021,-u_time*.012);"
         + "vec4 flowing=texture2D(u_texture,v_texCoords+current+refract);"
         + "vec4 crossing=texture2D(u_texture,v_texCoords+vec2(-u_time*.009,u_time*.016)-refract.yx*.65);"
         + "c=mix(flowing,crossing,.16)*v_color;}"
         + "float bend=.92*sin(dot(p,vec2(.032,.021)))+.38*sin(p.y*.043-p.x*.013-u_time*.11);"
         + "float swell=sin(dot(p,vec2(.13,.085))+bend-u_time*1.35);"
         + "float small=sin(dot(p,vec2(-.067,.19))+.35*sin(p.x*.043)+u_time*.75);"
         + "float reach=.65+2.45*sin(u_time*.92+sin(dot(p,vec2(.016,.013)))*.32);"
         + "float signedShore=v_style> -1.5?-v_shore:v_shore;float surfBend=.72*sin(dot(p,vec2(.083,.057)))+.28*sin(dot(p,vec2(-.17,.13)));"
         + "float foam=(1.0-smoothstep(.14,.68,abs(signedShore-reach+surfBend)))"
         + "*(.58+.42*smoothstep(-.6,.7,small));"
         + "foam=max(foam,(1.0-smoothstep(.05,1.05,v_shore))*(.42+.14*sin(dot(p,vec2(.41,.28))-u_time*.6)));"
         + "if(v_style> -1.5){float offshore=smoothstep(2.0,16.0,v_shore);"
         // Long swells carry crossed ripples instead of repeating a flat atlas
         // cell. Both colour and analytical normals are continuous world fields.
         + "float longA=dot(p,vec2(.038,.024))-u_time*.61;"
         + "float longB=dot(p,vec2(-.022,.046))+u_time*.43;"
         + "float rippleA=dot(p,vec2(.21,.13))+bend-u_time*1.63;"
         + "float rippleB=dot(p,vec2(-.12,.27))+.45*sin(longA)+u_time*1.14;"
         + "float ocean=sin(longA)*.62+sin(longB)*.38;"
         + "vec2 slope=vec2(.55,.35)*cos(longA)+vec2(-.20,.42)*cos(longB)"
         + "+vec2(.22,.14)*cos(rippleA)+vec2(-.10,.23)*cos(rippleB);"
         + "vec3 normal=normalize(vec3(-slope.x*.54,1.0,-slope.y*.54));materialNormal=normal;wetness=1.0;"
         + "float diffuse=dot(normal,normalize(vec3(-.42,.85,.32)));"
         + "float sparkle=pow(max(0.0,dot(normal,normalize(vec3(-.12,.98,.14)))),30.0);"
         + "float crest=smoothstep(.84,.998,sin(rippleA))*smoothstep(.05,.85,sin(rippleB));"
         + "vec3 depthColor=mix(vec3(.12,.49,.72),vec3(.055,.265,.54),offshore);"
         + "c.rgb=mix(c.rgb,depthColor,.40+offshore*.13);"
         + "c.rgb*=.79+diffuse*.21+ocean*(.045+offshore*.095);"
         + "c.rgb=mix(c.rgb,vec3(.62,.83,.87),sparkle*(.10+offshore*.24));"
         + "float broadCrest=pow(max(0.0,sin(longA+.28*sin(longB))),14.0);"
         + "float brokenCrest=smoothstep(-.28,.42,sin(p.x*.15-p.y*.09+u_time*.12));"
         + "c.rgb=mix(c.rgb,vec3(.49,.79,.92),max(crest*.18,broadCrest*brokenCrest*(.13+offshore*.18)));"
         + "float fresnel=.12+.36*pow(1.0-max(0.0,dot(normal,viewDirection)),3.0);"
         + "vec3 sky=mix(vec3(.29,.57,.71),vec3(.73,.84,.87),clamp(normal.z*.9+.45,0.0,1.0));"
         + "float cloud=smoothstep(.30,.82,sin(p.x*.013+p.y*.019+u_time*.025)+.28*sin(p.x*.035-p.y*.011));"
         + "sky=mix(sky,vec3(.78,.86,.87),cloud*.24);c.rgb=mix(c.rgb,sky,fresnel);"
         + "float sunGlint=pow(max(0.0,dot(reflect(normalize(vec3(.38,-.84,-.30)),normal),viewDirection)),38.0);"
         + "c.rgb+=vec3(1.0,.92,.72)*sunGlint*(.14+.23*u_daylight);"
         + "float breaker=smoothstep(.87,.99,sin(v_shore*.68+u_time*1.05+bend*.32))*(1.0-smoothstep(2.0,11.0,v_shore));"
         + "c.rgb=mix(c.rgb,vec3(.89,.96,.98),max(foam*.82,breaker*.30));}"
         + "else{float wet=1.0-smoothstep(.4,5.0,v_shore);"
         + "c.rgb*=mix(1.0,.77,wet);float wash=(1.0-smoothstep(reach-.35,reach+.35,v_shore))*.20;"
         + "c.rgb=mix(c.rgb,vec3(.30,.57,.59),wash);c.rgb=mix(c.rgb,vec3(.87,.95,.92),foam*.52);}"
         // Both sides of a sandy coast meet in the same wet-sand/wash colour.
         // This hides the old square texture boundary without moving collision.
         + "if(v_style< -1.05){float seam=1.0-smoothstep(.0,5.5,v_shore);float wash=smoothstep(-2.2,3.4,reach-signedShore-surfBend);vec3 strand=mix(vec3(.69,.66,.48),vec3(.27,.57,.60),wash);strand*=.98+.025*sin(p.x*1.17+p.y*.91);strand=mix(strand,vec3(.86,.94,.90),foam*.58);c.rgb=mix(c.rgb,strand,seam);}}"
         + "float glow=0.0,lightReflection=0.0;for(int i=0;i<8;i++){vec4 l=u_lights[i];"
         + "float f=clamp(1.0-distance(v_world,l.xy)/max(l.z,1.0),0.0,1.0);glow=max(glow,f*f*l.w);"
         + "vec3 toward=normalize(vec3(l.x-v_position.x,20.0,l.y-v_position.z));"
         + "float highlight=pow(max(0.0,dot(materialNormal,normalize(toward+viewDirection))),26.0);"
         + "lightReflection+=highlight*f*f*l.w*wetness*.38;}"
         + "vec3 light=max(u_tint,mix(u_tint,vec3(1.14,1.03,.82),clamp(glow,0.0,1.0)));"
         // Crown shadows remain translucent and local illumination opens them
         // up. Actor/UI colours never receive the ground's shadow multiplier.
         + "bool crownShadow=v_style>1.5&&v_style<2.5;"
         + "if(crownShadow){c.rgb=vec3(.04,.075,.12);c.a*=1.0-clamp(glow*.72,0.0,.72);}"
         + "if(c.a<((crownShadow||v_style<-2.5)?.003:.1))discard;"
         + "c.rgb*=v_style>2.5&&v_style<3.5?mix(light,vec3(1.0),0.65):light;"
         + "c.rgb+=vec3(1.0,.74,.36)*min(lightReflection,.42);"
         + "if(v_style>5.5)c.rgb=max(c.rgb,texture2D(u_texture,v_texCoords).rgb*.48);"
         + "c.rgb=mix(c.rgb,vec3(dot(c.rgb,vec3(.299,.587,.114))),u_desaturation);"
         + "float mist=u_fogDensity*(.65+.35*sin(v_world.x*.036+u_time*.21)*sin(v_world.y*.047-u_time*.16));"
         + "gl_FragColor=vec4(mix(c.rgb,u_fogColor,clamp(mist,0.0,.45)),c.a);}\n";
      shader = new ShaderProgram(vertex, fragment);
      if (!shader.isCompiled()) {
         throw new IllegalStateException("Perspective shader compilation failed: " + shader.getLog());
      }
      Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
      pixel.setColor(Color.WHITE);
      pixel.fill();
      whiteTexture = new Texture(pixel);
      pixel.dispose();
      white = new TextureRegion(whiteTexture);
      assets = BwAssets.get();
      initialized = true;
   }

   private void configureCamera(Game game) {
      float width = Math.max(1f, game.cam.viewportWidth * game.cam.zoom);
      float height = Math.max(1f, game.cam.viewportHeight * game.cam.zoom);
      camera.viewportWidth = width;
      camera.viewportHeight = height;
      // A slightly tighter footprint retains the original simulation's actor range.
      float distance = height * 0.77f / (2f * (float)Math.tan(17.5f * MathUtils.degreesToRadians));
      float angle = 50f * MathUtils.degreesToRadians;
      float heightAtPlayer=elevation.height(game.player.position.x+8,game.player.position.y+8);
      camera.position.set(game.cam.position.x, heightAtPlayer + MathUtils.sin(angle) * distance,
         -game.cam.position.y + MathUtils.cos(angle) * distance);
      camera.up.set(0f, 1f, 0f);
      camera.lookAt(game.cam.position.x, heightAtPlayer, -game.cam.position.y);
      camera.update();
   }

   private void collectWorld(Game game) {
      bedDepthValid=false;
      Color sceneLight=game.mapBatch.getColor();
      daylight=MathUtils.clamp((sceneLight.r+sceneLight.g)*.5f,0,1);
      natureTiles=game.map.tiles;heightShoreTile=null;
      shakingTrees.clear();
      growingPlants.clear();
      for (Action action : game.actionStack) {
         if (action instanceof HeadbuttTreeAnim) {
            HeadbuttTreeAnim shake = (HeadbuttTreeAnim)action;
            shakingTrees.put(shake.tile, shake);
         } else if (action instanceof PlantTree) growingPlants.add(((PlantTree)action).pos);
      }
      float halfWidth = game.cam.viewportWidth * game.cam.zoom * 0.5f;
      float halfHeight = game.cam.viewportHeight * game.cam.zoom * 0.5f;
      int left = MathUtils.floor((game.cam.position.x - halfWidth - 64f) / TILE);
      int right = MathUtils.ceil((game.cam.position.x + halfWidth + 64f) / TILE);
      int bottom = MathUtils.floor((game.cam.position.y - halfHeight - 48f) / TILE);
      int top = MathUtils.ceil((game.cam.position.y + halfHeight + 80f) / TILE);
      for (int row = bottom; row <= top; row++) {
         for (int col = left; col <= right; col++) {
            Tile tile = game.map.tiles.get(lookup.set(col * TILE, row * TILE));
            if (tile != null) {
               elevation.observe(tile);
               surfaceLift=elevation.height(tile.position.x+8,tile.position.y+8);
               tile(game, tile);
            }
         }
      }
      for (Pokemon pokemon : game.map.onscreenPokemon) {
         if (pokemon == game.player.hmPokemon && !game.player.currFieldMove.isEmpty()) continue;
         pokemon(game, pokemon);
      }
      for (Tile tile : game.map.onscreenFossils) {
         surfaceLift=elevation.height(tile.position.x+8,tile.position.y+8);
         pickup(tile);
      }
      for (Pokemon pokemon : game.map.onscreenBurrowed) {
         pokemon(game, pokemon);
      }
      if (game.player.hmPokemon != null && game.player.currFieldMove.isEmpty() && !game.map.onscreenPokemon.contains(game.player.hmPokemon)) {
         pokemon(game, game.player.hmPokemon);
      }
      for (Player other : game.players.values()) {
         if (other != null && other.network != null && other.network.tiles == game.map.tiles
            && !other.isSleeping && other.currSprite != null) {
            trainer(other, 0f);
         }
      }
      fieldPlayer(game);
      fieldTarget(game);
      // Read the same map-action snapshot used in this frame. The original action
      // already advanced its timer; rendering it here must never call step again.
      for (Action action : game.actionStackCopy) {
         if (action instanceof SpawnGhost || action instanceof DrawGhost || action instanceof DespawnGhost) {
            ghost(game, action);
         } else if (action instanceof CutTreeAnim || action instanceof PlantTree) {
            fieldEffect(game, action);
         } else if (action instanceof Pokemon.Emote) {
            Pokemon.Emote emote = (Pokemon.Emote)action;
            Pokemon owner = enclosingOwner(emote, Pokemon.class);
            // Match Emote.step exactly; it does not consult drawThisFrame.
            if (owner.mapTiles == game.map.tiles) {
               emote(emote.sprite, owner.position.x, owner.position.y);
            }
         } else if (action instanceof Player.Emote) {
            Player.Emote emote = (Player.Emote)action;
            Player owner = enclosingOwner(emote, Player.class);
            emote(emote.sprite, owner.position.x, owner.position.y);
         }
      }
   }

   private void emote(Sprite sprite, float x, float y) {
      surfaceLift=elevation.height(x+8,y+8);
      if (sprite == null || sprite.getTexture() == null) return;
      if (!camera.frustum.sphereInFrustum(x + 8f, 28f, -y - 7f, 18f)) return;
      quad(emotes.buffer(sprite.getTexture()), x, 20f, -y - 7f,
         x + 16f, 20f, -y - 7f, x + 16f, 36f, -y - 7f, x, 36f, -y - 7f,
         sprite, sprite.getColor().toFloatBits());
   }

   private <T> T enclosingOwner(Object action, Class<T> ownerType) {
      // Java inner actions keep their owner in a compiler-generated field. Find
      // it by its declared type instead of relying on a compiler-specific name.
      // This is a cached read only; neither action nor owner fields are changed.
      Field owner = emoteOwners.get(action.getClass());
      if (owner == null) {
         for (Field candidate : action.getClass().getDeclaredFields()) {
            if (candidate.isSynthetic() && candidate.getType() == ownerType) {
               candidate.setAccessible(true);
               owner = candidate;
               emoteOwners.put(action.getClass(), owner);
               break;
            }
         }
         if (owner == null) {
            throw new IllegalStateException("Cannot locate visual emote owner for " + action.getClass().getName());
         }
      }
      try {
         return ownerType.cast(owner.get(action));
      } catch (IllegalAccessException ex) {
         throw new IllegalStateException("Cannot read visual emote owner", ex);
      }
   }

   private void pickup(Tile tile) {
      String item = tile.hasItem;
      if (item == null) return;
      TextureRegion region = null;
      if (item.contains("fossil") || item.equals("old amber")) {
         region = assets.named("fossil");
      } else {
         if (tile.isSign() || item.equals("secret key") || item.equals("pok\u00e9 ball")) return;
         ball(tile.position.x+8,tile.position.y+7,item.contains("ultra"));
         return;
      }
      shadow(tile.position.x + 8f, tile.position.y + 7f, 4f, 2f);
      upright(region, tile.position.x+2, tile.position.y + 7f, 0.2f,12,10,WHITE);
   }

   private void tile(Game game, Tile tile) {
      float x = tile.position.x, y = tile.position.y;
      String lower = tile.name == null ? "" : tile.name;
      String upper = tile.nameUpper == null ? "" : tile.nameUpper;
      String names = (lower + " " + upper).toLowerCase(java.util.Locale.ROOT);
      String identity = WorldMaterials.objectName(tile);
      WorldMaterials.Kind kind = WorldMaterials.kind(tile, identity);
      BiomeProfiles.Profile profile=BiomeProfiles.visualForTile(tile);
      TextureRegion ground = assets.terrain(tile, natureSeconds);
      if(assets.namedAnimated(BwAssets.terrainName(tile),natureSeconds)==null)
         assets.reportMissing(BwAssets.terrainName(tile),tile,game.map.timeOfDay);
      if (ground == null) ground = assets.cell("grass_light", x, y);
      if(tile.isLava && TerrainReliefAssets.get().lava()!=null) ground=TerrainReliefAssets.get().lava();
      if(WorldNatureMotion.water(tile)&&!tile.isWaterfall&&TerrainReliefAssets.get().water()!=null)ground=TerrainReliefAssets.get().water();
      if(!tile.isWaterfall&&!names.contains("lavafall")) {
         terrainSurface(game,tile,ground);
         terrainTransitions(game,tile);
      }
      if(!tile.isWater && !tile.isLava && (tile.isTidal || lower.contains("puddle"))) {
         // High tide is a walkable film over sand, not an opaque deep-water tile.
         // Read the live tide flag: CycleDayNight changes names before init().
         TextureRegion shallow=assets.cell(profile.string("fluid","shallow","water_shallow"),x,y,natureSeconds);
         // Round only exposed margins; neighbouring tidal cells share one film.
         // The pool's soft shape lives in world coordinates, never an opaque square.
         int connected=WorldNatureMotion.tidalConnections(game.map.tiles,tile,elevation,lookup);
         float style=tile.isTidal?-3f-connected/32f:-4f;
         floor(shallow,x,y,16,16,.045f,
            Color.toFloatBits(.92f,1,1,tile.isTidal?.38f:.48f),translucent,style);
      }
      if(tile.isWaterfall || names.contains("lavafall")) { waterfall(tile,profile);return; }
      terrainEdges(game,tile,profile);
      if (ModernWorldGenerator.isRamp(tile)) ramp(tile,profile);
      if (ModernWorldGenerator.isRamp(tile)) return;
      // The growing action owns its one animated sprout, not a second static prop.
      if (growingPlants.contains(tile.position)) return;
      if (tile.isLedge && (tile.ledgeDir != null || upper.startsWith("ledges3"))) {
         if(!names.contains("fence")) {jumpLedgeCue(tile);return;}
         // A jumpable fence still needs its fence art; it is not a terrain cliff.
      }
      if(tile.isSolid)obstacleFootprint(game,tile,kind,profile);
      if (kind == WorldMaterials.Kind.HIDDEN) {
         // Indoor opaque collision fillers previously looked like walkable empty floor.
         // Tree extensions have roots above; scripted hidden actors remain invisible.
         if(identity==null&&"solid".equals(upper.isEmpty()?lower:upper)&&game.map.tiles!=game.map.overworldTiles)
            prism(x+.35f,y+.35f,0,15.3f,15.3f,3.2f,assets.named("wall_cap"),WHITE);
         return;
      }
      if (kind == WorldMaterials.Kind.TREE) { tree(tile, x + 8f, y + 8f, names); return; }
      if (staticPokemon(tile, x+8, y+7)) return;
      if (names.contains("pokeball") || names.contains("ultraball")) { ball(x+8,y+7,names.contains("ultraball")); return; }
      if (building(game, tile, x, y, names)) return;
      TextureRegion berry=UserSpriteAssets.get().berry(tile,natureSeconds);
      TextureRegion object = berry!=null?berry:identity == null ? null : assets.namedAnimated(identity, natureSeconds);
      if(object==null && BwAssets.objectName(tile)!=null)
         assets.reportMissing(BwAssets.objectName(tile),tile,game.map.timeOfDay);
      if (object != null) {
         if (kind == WorldMaterials.Kind.GROUND_DETAIL) {
            floor(object,x,y,16,16,.08f,WHITE,geometry);return;
         }
         boolean seedling="seedling".equals(identity)||"plant_growing".equals(identity);
         boolean plant = names.contains("grass") || names.contains("flower") || seedling;
         float maxWidth = berry!=null?14f:seedling ? Math.min(9f,object.getRegionWidth()) : kind == WorldMaterials.Kind.ROCK ? 17f : plant ? 16f : 18f;
         float scale = VisualGeometry.fitScale(object.getRegionWidth(), object.getRegionHeight(), maxWidth, berry!=null?22f:25f);
         float width = assets.worldWidth(object, object.getRegionWidth() * scale);
         float height = assets.worldHeight(object, object.getRegionHeight() * scale);
         if (!plant) assetShadow(object, x + 8, y + 7, width * .3f, 2.4f);
         // Only living low vegetation bends. Actors, trunks and rigid desert
         // plants retain their exact positions and normal depth planes.
         vegetationBend=kind==WorldMaterials.Kind.VEGETATION&&WorldNatureMotion.flexible(identity)
            ?MathUtils.clamp(height*.055f,.35f,1.0f):0;
         try {upright(object, x + 8 - width/2, y + 7, .15f, width, height, WHITE);}
         finally {vegetationBend=0;}
      } else if (tile.isSolid && !tile.isLava && !tile.isWater && !names.contains("_hidden") && !names.contains("nosprite")) {
         // Unknown solid cells still show their real blocked footprint using the
         // current biome material; old map textures never leak into this pass.
         prism(x+1,y+1,0,14,14,8,assets.cell(profile.terrain("rock"),x,y),WHITE);
      }
   }

   private boolean staticPokemon(Tile tile,float x,float y) {
      String name=(tile.name+" "+tile.nameUpper).toLowerCase(java.util.Locale.ROOT);
      if(name.contains("_hidden")) return false;
      String species=tile.name!=null && tile.name.matches("cave1_regi[123]")?"regigigas":null;
      for(String candidate:new String[]{"regirock","regice","registeel","regigigas","regidrago","regieleki","volcarona","spiritomb","raikou","entei","suicune","mewtwo","mega_gengar"}) {
         if(name.contains(candidate)) {species=candidate.equals("mega_gengar")?"mgengar":candidate;break;}
      }
      if(species==null)return false;
      PmdPokemonSprites sprites=PmdPokemonSprites.get();
      PmdPokemonSprites.Frame frame=sprites.frame(species,"down","Idle",seconds);
      if(frame==null)return false;
      PmdPokemonSprites.AnimationBounds bounds=sprites.bounds(species,"down","Idle");
      float scale=Math.min(.85f,30f/Math.max(bounds.width,bounds.height));
      shadow(x,y,6,3);
      anchored(frame.region,x,y,.2f-Math.min(0,bounds.minY)*scale*MathUtils.cosDeg(50),frame.anchorX,frame.anchorY,scale);
      return true;
   }

   private void ball(float x,float y,boolean ultra) {
      shadow(x,y,3.8f,2f);
      TextureRegion art=UserSpriteAssets.get().ball(ultra?"ultra ball":"poke ball",0);
      upright(art,x-4.5f,y,.25f,9,9,WHITE);
   }

   /** Natural exposed soil, turf lip and shadow at the actual high surface.
    * Height is conveyed by terrain; no directional glyphs are painted on it. */
   private void jumpLedgeCue(Tile tile) {
      // Large geological contours have their own rock shoulder. The short soil
      // shelf belongs only to an actual jumpable hill, never a mountain face.
      if(!WorldReadability.smallJumpLedge(tile))return;
      float dx="left".equals(tile.ledgeDir)?-1:"right".equals(tile.ledgeDir)?1:0;
      float dy=dx!=0?0:"up".equals(tile.ledgeDir)?1:-1,tx=-dy,ty=dx;
      float cx=tile.position.x+8,cy=tile.position.y+8,previous=surfaceLift;surfaceLift=0;
      float edgeX=cx+dx*7.7f,edgeY=cy+dy*7.7f;
      int firstCorner=dy<0?0:dy>0?3:dx<0?0:1,lastCorner=dy<0?1:dy>0?2:dx<0?3:2;
      float span0=(dx==0?tile.position.x:tile.position.y)+(cliffBevel(tile,firstCorner)?cliffRadius(tile):.05f);
      float span1=(dx==0?tile.position.x:tile.position.y)+16-(cliffBevel(tile,lastCorner)?cliffRadius(tile):.05f);
      // A 3px exposed soil shelf stays readable when a side face is edge-on to
      // the fixed camera. This is textured terrain at the real high elevation.
      float ix=edgeX-dx*2.7f,iy=edgeY-dy*2.7f;
      TextureRegion soil=TerrainReliefAssets.get().earth();
      if(soil==null)soil=assets.named("dirt");
      float soilTint=color(.86f,.89f,.85f);
      surfacePatch(tile,soil,dx==0?span0:Math.min(edgeX,ix),dx==0?Math.min(edgeY,iy):span0,
         dx==0?span1:Math.max(edgeX,ix),dx==0?Math.max(edgeY,iy):span1,.08f,
         soilTint,soilTint,soilTint,soilTint,geometry,0);
      Tile landing=natureTiles.get(lookup.set(tile.position.x+dx*16,tile.position.y+dy*16));
      if(landing!=null&&!landing.isWater&&!landing.isLava) {
         float lx=cx+dx*8.3f,ly=cy+dy*8.3f,ox=lx+dx*4.4f,oy=ly+dy*4.4f;
         float dark=Color.toFloatBits(.17f,.12f,.07f,.54f),clear=Color.toFloatBits(.30f,.24f,.13f,.08f);
         surfacePatch(landing,white,Math.min(lx,ox)-Math.abs(tx)*7.95f,Math.min(ly,oy)-Math.abs(ty)*7.95f,
            Math.max(lx,ox)+Math.abs(tx)*7.95f,Math.max(ly,oy)+Math.abs(ty)*7.95f,.11f,
            dx>0||dy>0?dark:clear,dx<0||dy>0?dark:clear,dx<0||dy<0?dark:clear,dx>0||dy<0?dark:clear,translucent,0);
      }
      // The inner edge stays covered by the actual plateau material, avoiding
      // the previous yellow rail and arrows even on a camera-facing low ledge.
      TextureRegion turf=assets.terrain(tile,natureSeconds);
      if(turf!=null)surfacePatch(tile,turf,dx==0?span0:Math.min(ix,ix-dx*.75f),dx==0?Math.min(iy,iy-dy*.75f):span0,
         dx==0?span1:Math.max(ix,ix-dx*.75f),dx==0?Math.max(iy,iy-dy*.75f):span1,.13f,
         WHITE,WHITE,WHITE,WHITE,geometry,WorldMaterials.softGrass(WorldMaterials.terrainName(tile))?1:0);
      surfaceLift=previous;jumpCueFrames++;
   }

   /** The collision cell is a whole 16px tile even when the trunk is slender.
    * Natural roots/soil and furniture plinths show that occupied footprint. */
   private void obstacleFootprint(Game game,Tile tile,WorldMaterials.Kind kind,BiomeProfiles.Profile profile) {
      boolean woody=kind==WorldMaterials.Kind.TREE||WorldReadability.hiddenTreeFootprint(tile);
      boolean shrub=kind==WorldMaterials.Kind.VEGETATION;
      if(!woody&&!shrub&&kind!=WorldMaterials.Kind.ROCK&&kind!=WorldMaterials.Kind.FURNITURE&&kind!=WorldMaterials.Kind.PROP)return;
      float x=tile.position.x,y=tile.position.y;
      if(kind==WorldMaterials.Kind.FURNITURE) {
         floor(assets.named("wood_floor"),x+.5f,y+.5f,15,15,.12f,Color.toFloatBits(.62f,.57f,.48f,1),geometry);
      } else {
         // A low, irregular soil skirt occupies the blocked cell. No debug-like
         // square outlines: roots and earth show the obstruction naturally.
         float tint=Color.toFloatBits(.30f,.24f,.13f,woody?.33f:.23f);
         Buffer soil=translucent.buffer(whiteTexture);
         for(int segment=0;segment<12;segment++) {
            float a=segment*MathUtils.PI2/12,b=(segment+1)*MathUtils.PI2/12;
            float r0=segment%3==0?7.8f:7.1f,r1=(segment+1)%3==0?7.8f:7.1f;
            triangle(soil,x+8,.11f,-y-8,x+8+MathUtils.cos(a)*r0,.11f,-y-8+MathUtils.sin(a)*r0,
               x+8+MathUtils.cos(b)*r1,.11f,-y-8+MathUtils.sin(b)*r1,tint);
         }
         if(woody&&!WorldReadability.hiddenTreeFootprint(tile)) {
            float centerX=x+(WorldReadability.largeTree(tile)?16:8),centerY=y+(WorldReadability.largeTree(tile)?16:8);
            float length=WorldReadability.largeTree(tile)?14.8f:7.4f;
            for(int root=0;root<5;root++) {
               float angle=.35f+root*MathUtils.PI2/5,dx=MathUtils.cos(angle),dy=MathUtils.sin(angle);
               float tx=-dy,ty=dx;
               triangle(geometry.buffer(whiteTexture),centerX-tx,.5f,-centerY+ty,centerX+tx,.5f,-centerY-ty,
                  centerX+dx*length,.14f,-centerY+dy*length,color(.37f,.27f,.13f));
            }
         }
      }
      obstacleFootprintFrames++;
   }

   private void disc(float x,float y,float base,float radius,float top,float bottom) {
      Buffer mesh=geometry.buffer(whiteTexture);
      float rise=MathUtils.cosDeg(50),back=MathUtils.sinDeg(50);
      for(int i=0;i<20;i++) {
         float a=i*MathUtils.PI2/20,b=(i+1)*MathUtils.PI2/20;
         float h1=radius+MathUtils.sin(a)*radius,h2=radius+MathUtils.sin(b)*radius;
         triangle(mesh,x,base+radius*rise,-y-radius*back,
            x+MathUtils.cos(a)*radius,base+h1*rise,-y-h1*back,
            x+MathUtils.cos(b)*radius,base+h2*rise,-y-h2*back,i<10?top:bottom);
      }
   }

   private void ramp(Tile tile,BiomeProfiles.Profile profile) {
      float x=tile.position.x,y=tile.position.y,previousLift=surfaceLift;surfaceLift=0;
      float a=elevation.height(x+.01f,y+.01f),b=elevation.height(x+15.99f,y+.01f);
      float c=elevation.height(x+15.99f,y+15.99f),d=elevation.height(x+.01f,y+15.99f);
      TextureRegion steps=TerrainReliefAssets.get().stair(tile,profile);
      if(steps==null)steps=assets.named(profile.cliff("ramp"));
      boolean horizontal=Math.abs((b+c)-(a+d))>Math.abs((c+d)-(a+b));
      boolean positive=horizontal?b+c>=a+d:c+d>=a+b;
      int joinedBefore=rampRun(tile,horizontal,-1),joinedAfter=rampRun(tile,horizontal,1);
      float acrossMin=joinedBefore>0?0:1.05f,acrossMax=joinedAfter>0?16:14.95f;
      float noseMin=joinedBefore>0?0:1.15f,noseMax=joinedAfter>0?16:14.85f;
      float flightOrigin=(horizontal?y:x)-joinedBefore*16+1.05f;
      float flightWidth=(1+joinedBefore+joinedAfter)*16-2.1f;
      // Six broad stone treads follow the existing continuous walkable slope.
      // Their small raised lips and recessed seams provide depth without adding
      // a new staircase to the collision surface or changing actors' foot height.
      for(int step=0;step<6;step++) {
         float start=step*16f/6,end=(step+1)*16f/6;
         float x0=horizontal?x+start:x+acrossMin,y0=horizontal?y+acrossMin:y+start;
         float x1=horizontal?x+end:x+acrossMax,y1=horizontal?y+acrossMax:y+end;
         float tint=rockLight(step%2==0?1f:.94f,profile);
         if(steps!=null)stairPatch(tile,steps,x0,y0,x1,y1,horizontal,flightOrigin,flightWidth,.14f,tint,tint,tint,tint,geometry);
         float nose=positive?start+.20f:end-.70f;
         float px=horizontal?x+nose:x+noseMin,py=horizontal?y+noseMin:y+nose;
         float ex=horizontal?px+.50f:x+noseMax,ey=horizontal?y+noseMax:py+.50f;
         float bright=Color.toFloatBits(.96f,.94f,.84f,.68f);
         surfacePatch(tile,white,px,py,ex,ey,.24f,bright,bright,bright,bright,translucent,0);
         // A fine real vertical lip catches the light at each tread's low edge.
         float nx=horizontal?(positive?px:ex):px,ny=horizontal?py:(positive?py:ey);
         float fx=horizontal?nx:ex,fy=horizontal?ey:ny;
         float h0=terrainHeight(tile,MathUtils.clamp(nx,x+.01f,x+15.99f),MathUtils.clamp(ny,y+.01f,y+15.99f));
         float h1=terrainHeight(tile,MathUtils.clamp(fx,x+.01f,x+15.99f),MathUtils.clamp(fy,y+.01f,y+15.99f));
         quad(geometry.buffer(whiteTexture),nx,h0+.045f,-ny,fx,h1+.045f,-fy,
            fx,h1+.24f,-fy,nx,h0+.24f,-ny,white,rockLight(.70f,profile));
         float seam=Color.toFloatBits(.20f,.22f,.22f,.48f);
         float gap=positive?end-.32f:start+.05f;
         surfacePatch(tile,white,horizontal?x+gap:x+acrossMin,horizontal?y+acrossMin:y+gap,
            horizontal?x+gap+.25f:x+acrossMax,horizontal?y+acrossMax:y+gap+.25f,.17f,
            seam,seam,seam,seam,translucent,0);
         stairTreadFrames++;
      }
      // Weathered shoulders taper into the adjoining terrain. Both end landings
      // remain at precisely the same elevation as the connected ground.
      float edge=Color.toFloatBits(.77f,.78f,.70f,.50f),clear=Color.toFloatBits(.77f,.78f,.70f,0);
      if(horizontal) {
         if(joinedBefore==0)surfacePatch(tile,white,x,y,x+16,y+1.05f,.10f,clear,clear,edge,edge,translucent,0);
         if(joinedAfter==0)surfacePatch(tile,white,x,y+14.95f,x+16,y+16,.10f,edge,edge,clear,clear,translucent,0);
      } else {
         if(joinedBefore==0)surfacePatch(tile,white,x,y,x+1.05f,y+16,.10f,clear,edge,edge,clear,translucent,0);
         if(joinedAfter==0)surfacePatch(tile,white,x+14.95f,y,x+16,y+16,.10f,edge,clear,clear,edge,translucent,0);
      }
      surfaceLift=previousLift;
   }

   private void stairPatch(Tile tile,TextureRegion art,float x0,float y0,float x1,float y1,boolean horizontal,
      float flightOrigin,float flightWidth,float lift,float c0,float c1,float c2,float c3,WorldMeshBatch batch) {
      float x=tile.position.x,y=tile.position.y;
      float a=terrainHeight(tile,MathUtils.clamp(x0,x+.01f,x+15.99f),MathUtils.clamp(y0,y+.01f,y+15.99f))+lift;
      float b=terrainHeight(tile,MathUtils.clamp(x1,x+.01f,x+15.99f),MathUtils.clamp(y0,y+.01f,y+15.99f))+lift;
      float c=terrainHeight(tile,MathUtils.clamp(x1,x+.01f,x+15.99f),MathUtils.clamp(y1,y+.01f,y+15.99f))+lift;
      float d=terrainHeight(tile,MathUtils.clamp(x0,x+.01f,x+15.99f),MathUtils.clamp(y1,y+.01f,y+15.99f))+lift;
      float across0=((horizontal?y0:x0)-flightOrigin)/flightWidth;
      float across1=((horizontal?y1:x1)-flightOrigin)/flightWidth;
      float u0=MathUtils.lerp(art.getU(),art.getU2(),across0),u1=MathUtils.lerp(art.getU(),art.getU2(),across1);
      // The authored six-tread image covers the complete connector exactly once.
      // Rotating these UV axes with the incline keeps sideways stairs readable.
      float travel0=horizontal?(x0-x)/16:(y0-y)/16,travel1=horizontal?(x1-x)/16:(y1-y)/16;
      float v0=MathUtils.lerp(art.getV2(),art.getV(),travel0),v1=MathUtils.lerp(art.getV2(),art.getV(),travel1);
      Buffer mesh=batch.buffer(art.getTexture());
      vertex(mesh,x0,a,-y0,c0,u0,v0,0);vertex(mesh,x1,b,-y0,c1,horizontal?u0:u1,horizontal?v1:v0,0);
      vertex(mesh,x1,c,-y1,c2,u1,v1,0);vertex(mesh,x1,c,-y1,c2,u1,v1,0);
      vertex(mesh,x0,d,-y1,c3,horizontal?u1:u0,horizontal?v0:v1,0);vertex(mesh,x0,a,-y0,c0,u0,v0,0);
   }

   /** Parallel compatible connectors share one wide flight, including UVs and
    * nose lines. Only its two outside shoulders receive a weathered border. */
   private int rampRun(Tile tile,boolean horizontal,int direction) {
      Tile previous=tile;int count=0;
      for(;count<natureTiles.size();count++) {
         Tile next=natureTiles.get(lookup.set(previous.position.x+(horizontal?0:direction*16),
            previous.position.y+(horizontal?direction*16:0)));
         if(!parallelRamp(previous,next,horizontal))break;
         previous=next;
      }
      return count;
   }
   private boolean parallelRamp(Tile tile,Tile next,boolean horizontal) {
      if(next==null||!ModernWorldGenerator.isRamp(next)||!BiomeProfiles.visualForTile(tile).id.equals(BiomeProfiles.visualForTile(next).id))return false;
      float x=next.position.x,y=next.position.y;
      float a=elevation.height(x+.01f,y+.01f),b=elevation.height(x+15.99f,y+.01f);
      float c=elevation.height(x+15.99f,y+15.99f),d=elevation.height(x+.01f,y+15.99f);
      if(horizontal!=(Math.abs((b+c)-(a+d))>Math.abs((c+d)-(a+b))))return false;
      float seam=horizontal?Math.max(tile.position.y,next.position.y):Math.max(tile.position.x,next.position.x);
      for(int sample=0;sample<3;sample++) {
         float f=sample==0?.01f:sample==1?8:15.99f;
         float tx=horizontal?tile.position.x+f:seam,ty=horizontal?seam:tile.position.y+f;
         float h0=terrainHeight(tile,MathUtils.clamp(tx,tile.position.x+.01f,tile.position.x+15.99f),
            MathUtils.clamp(ty,tile.position.y+.01f,tile.position.y+15.99f));
         float h1=terrainHeight(next,MathUtils.clamp(tx,x+.01f,x+15.99f),MathUtils.clamp(ty,y+.01f,y+15.99f));
         if(Math.abs(h0-h1)>.15f)return false;
      }
      return true;
   }

   /** Shared-corner patches follow the same triangle split as the terrain. */
   private void surfacePatch(Tile tile,TextureRegion art,float x0,float y0,float x1,float y1,float lift,
      float c0,float c1,float c2,float c3,WorldMeshBatch batch,float style) {
      float x=tile.position.x,y=tile.position.y;
      float a=terrainHeight(tile,MathUtils.clamp(x0,x+.01f,x+15.99f),MathUtils.clamp(y0,y+.01f,y+15.99f))+lift;
      float b=terrainHeight(tile,MathUtils.clamp(x1,x+.01f,x+15.99f),MathUtils.clamp(y0,y+.01f,y+15.99f))+lift;
      float c=terrainHeight(tile,MathUtils.clamp(x1,x+.01f,x+15.99f),MathUtils.clamp(y1,y+.01f,y+15.99f))+lift;
      float d=terrainHeight(tile,MathUtils.clamp(x0,x+.01f,x+15.99f),MathUtils.clamp(y1,y+.01f,y+15.99f))+lift;
      float u0=MathUtils.lerp(art.getU(),art.getU2(),(x0-x)/16),u1=MathUtils.lerp(art.getU(),art.getU2(),(x1-x)/16);
      float v0=MathUtils.lerp(art.getV2(),art.getV(),(y0-y)/16),v1=MathUtils.lerp(art.getV2(),art.getV(),(y1-y)/16);
      if(style>3.05f&&style<3.3f) {
         u0=x0/64;u1=x1/64;v0=-y0/64;v1=-y1/64;
      }
      if(style> -1.5f&&style<-.5f) {
         u0=x0/80;u1=x1/80;v0=-y0/80;v1=-y1/80;
      }
      Buffer mesh=batch.buffer(art.getTexture());
      vertex(mesh,x0,a,-y0,c0,u0,v0,style);vertex(mesh,x1,b,-y0,c1,u1,v0,style);vertex(mesh,x1,c,-y1,c2,u1,v1,style);
      vertex(mesh,x1,c,-y1,c2,u1,v1,style);vertex(mesh,x0,d,-y1,c3,u0,v1,style);vertex(mesh,x0,a,-y0,c0,u0,v0,style);
   }

   /** Feather only adjoining dry materials on the same continuous surface.
    * Actual water, cliff, stair and structure boundaries remain explicit. */
   private void terrainTransitions(Game game,Tile tile) {
      if(!WorldMaterials.blendable(tile))return;
      float x=tile.position.x,y=tile.position.y,previous=surfaceLift;surfaceLift=0;
      // A feather strip has its own diagonal. On a twisted old cell it could
      // cross the original mesh plane, so leave that unusual edge explicit.
      if(Math.abs(terrainHeight(tile,x+.01f,y+.01f)+terrainHeight(tile,x+15.99f,y+15.99f)
         -terrainHeight(tile,x+15.99f,y+.01f)-terrainHeight(tile,x+.01f,y+15.99f))>.025f) {
         surfaceLift=previous;return;
      }
      String material=WorldMaterials.terrainName(tile);
      float edge=Color.toFloatBits(1,1,1,.5f),clear=Color.toFloatBits(1,1,1,0);
      float width=MathUtils.clamp(BiomeProfiles.visualForTile(tile).number("transition","blendWidth",4)*.5f,1,4);
      for(int side=0;side<4;side++) {
         int dx=side==2?-1:side==3?1:0,dy=side==0?-1:side==1?1:0;
         Tile next=game.map.tiles.get(lookup.set(x+dx*16,y+dy*16));
         if(!WorldMaterials.blendable(next))continue;
         String neighbor=WorldMaterials.terrainName(next);if(material.equals(neighbor))continue;
         float mx=x+8+dx*8,my=y+8+dy*8;
         if(Math.abs(terrainHeight(tile,mx-dx*.01f,my-dy*.01f)-terrainHeight(next,mx+dx*.01f,my+dy*.01f))>.15f)continue;
         TextureRegion art=assets.cell(neighbor,x,y,natureSeconds);if(art==null)continue;
         float x0=x,y0=y,x1=x+16,y1=y+16;
         float c0=clear,c1=clear,c2=clear,c3=clear;
         if(side==0){y1=y+width;c0=c1=edge;}else if(side==1){y0=y+16-width;c2=c3=edge;}
         else if(side==2){x1=x+width;c0=c3=edge;}else{x0=x+16-width;c1=c2=edge;}
         surfacePatch(tile,art,x0,y0,x1,y1,.035f,c0,c1,c2,c3,translucent,WorldMaterials.softGrass(neighbor)?1:0);
      }
      surfaceLift=previous;
   }

   private void waterfall(Tile tile,BiomeProfiles.Profile profile) {
      waterfallFrames++;
      float x=tile.position.x,y=tile.position.y;
      String fluid=profile.string("fluid",tile.isLava?"lava":"water",tile.isLava?"lava_bright":"water");
      TextureRegion art=assets.cell(fluid,x,y,natureSeconds);
      float dx="left".equals(tile.ledgeDir)?-1:"right".equals(tile.ledgeDir)?1:0;
      float dy=dx!=0?0:"up".equals(tile.ledgeDir)?1:-1,tx=-dy,ty=dx;
      float lx=x+8+dx*8,ly=y+8+dy*8,hx=x+8-dx*8,hy=y+8-dy*8;
      float bottom=elevation.height(lx+dx*.01f,ly+dy*.01f)-surfaceLift
         -(tile.isLava?0:profile.number("height","shoreDepth",2));
      float top=elevation.height(hx-dx*.01f,hy-dy*.01f)-surfaceLift;
      // The falling sheet is upright world geometry; it never becomes a ground decal.
      quad(geometry.buffer(art.getTexture()),lx-tx*8,bottom,-ly+ty*8,lx+tx*8,bottom,-ly-ty*8,
         hx+tx*8,top,-hy-ty*8,hx-tx*8,top,-hy+ty*8,art,WHITE,tile.isLava?3:0);
      for(int i=0;i<4;i++) {
         float travel=(natureSeconds*.7f+i*.25f)%1f;
         float xx=MathUtils.lerp(hx,lx,travel),yy=MathUtils.lerp(hy,ly,travel),hh=MathUtils.lerp(top,bottom,travel);
         floor(white,xx-(dx==0?6:.3f),yy-(dx==0?.3f:6),dx==0?12:.6f,dx==0?.6f:12,
            hh+.12f,Color.toFloatBits(.84f,.96f,1,.35f),translucent);
      }
      ring(lx+dx,ly+dy,5+.7f*MathUtils.sin(natureSeconds*5),.6f,bottom+.14f,Color.toFloatBits(.76f,.92f,.95f,.4f));
   }

   /** Read real scripted night encounters; never create an actor as an asset fallback. */
   private void ghost(Game game,Action action) {
      if(!game.actionStack.contains(action))return;
      if (diagnosedGhosts.put(action, Boolean.TRUE) == null) {
         Gdx.app.log("GhostDiagnostic", "identity=" + action.getClass().getSimpleName()
            + " source=scripted-encounter isGhost=not-a-Pokemon asset=lavender-spirit fallback=false"
            + " biome=" + game.map.currBiome + " time=" + game.map.timeOfDay
            + " shader=world-atmosphere layer=translucent");
      }
      float x,y,alpha=1;
      if(action instanceof SpawnGhost) {
         SpawnGhost spawn=(SpawnGhost)action;x=spawn.position.x+16;y=spawn.position.y+16;
         alpha=spawn.part1>0?.25f:(1-spawn.part2/40f)*.8f;
      } else if(action instanceof DespawnGhost) {
         DespawnGhost despawn=(DespawnGhost)action;x=despawn.position.x+16;y=despawn.position.y+16;alpha=despawn.part1/80f;
      } else {
         DrawGhost ghost=(DrawGhost)action;
         if(ghost.inBattle && ghost.noEncounterTimer%4<2)return;
         x=ghost.basePos.x+16;y=ghost.basePos.y+16;
      }
      surfaceLift=elevation.height(x,y);ghostFrames++;
      alpha=MathUtils.clamp(alpha,0,1);
      if(alpha<=.001f)return;
      float bob=6+MathUtils.sin(natureSeconds*3)*1.5f;
      shadow(x,y,5,2.4f);
      TextureRegion spirit=GhostVisualAssets.get().frame(natureSeconds);
      float height=32,width=height*spirit.getRegionWidth()/spirit.getRegionHeight();
      float rise=height*MathUtils.cosDeg(50),back=height*MathUtils.sinDeg(50);
      actorPlaneZ=-y;
      quad(translucent.buffer(spirit.getTexture()),x-width*.5f,bob,-y,x+width*.5f,bob,-y,
         x+width*.5f,bob+rise,-y-back,x-width*.5f,bob+rise,-y-back,
         spirit,Color.toFloatBits(1,1,1,alpha),6f);
      ring(x,y,6+.4f*MathUtils.sin(natureSeconds*4),.6f,.18f,Color.toFloatBits(.57f,.74f,.95f,alpha*.4f));
   }

   /** A terrain cell is its upper surface, not a flat floor with a wall prop. */
   private void terrainSurface(Game game,Tile tile,TextureRegion ground) {
      float x=tile.position.x,y=tile.position.y,previous=surfaceLift;surfaceLift=0;
      TextureRegion art=terrainArt(tile,ground);
      float a=terrainHeight(tile,x+.01f,y+.01f),b=terrainHeight(tile,x+15.99f,y+.01f);
      float c=terrainHeight(tile,x+15.99f,y+15.99f),d=terrainHeight(tile,x+.01f,y+15.99f);
      // Directional light makes the incline legible even when it shares the
      // plateau's material. Flat ground keeps its original palette.
      float gx=((b+c)-(a+d))/32f,gy=((c+d)-(a+b))/32f;
      float sun=(.82f+.30f*gx+.32f*gy)/(float)Math.sqrt(1+gx*gx+gy*gy);
      float light=tile.isLava?1:MathUtils.clamp(.60f+.40f*sun/.82f,.60f,1f);
      shore.prepare(game.map.tiles,tile,elevation);
      float style=tile.isLava?3.125f:WorldNatureMotion.water(tile)?(shore.hasBeach()?-1.125f:-1f)
         :WorldNatureMotion.sand(tile)&&shore.hasShore()?-2f:WorldMaterials.softGrass(WorldMaterials.terrainName(tile))?1f:0f;
      if(style<-.5f&&shore.hasShore()) {
         // Only coastal cells need extra samples. The same world coordinate has
         // the same shore distance on either side of a tile/biome boundary.
         for(int iy=0;iy<4;iy++)for(int ix=0;ix<4;ix++)
            surfacePatch(tile,art,x+ix*4,y+iy*4,x+(ix+1)*4,y+(iy+1)*4,0,
               color(light,light,light),color(light,light,light),color(light,light,light),color(light,light,light),geometry,style);
      } else if(tile.isLava||WorldNatureMotion.water(tile))surfacePatch(tile,art,x,y,x+16,y+16,0,
         WHITE,WHITE,WHITE,WHITE,geometry,style);
      else if(hasCliffBevel(tile)) plateauSurface(tile,art,light,style);
      else if(art==TerrainReliefAssets.get().cap()) {
         Buffer top=geometry.buffer(art.getTexture());float tint=color(light,light,light);
         plateauVertex(top,art,x,y,a,0,0,tint,style);plateauVertex(top,art,x,y,b,16,0,tint,style);
         plateauVertex(top,art,x,y,c,16,16,tint,style);plateauVertex(top,art,x,y,c,16,16,tint,style);
         plateauVertex(top,art,x,y,d,0,16,tint,style);plateauVertex(top,art,x,y,a,0,0,tint,style);
      } else quad(geometry.buffer(art.getTexture()),x,a,-y,x+16,b,-y,x+16,c,-y-16,x,d,-y-16,art,color(light,light,light),style);
      surfaceLift=previous;
   }

   /** All top-facing fragments, including a rounded lower wedge, share the same
    * material resolution. A saved interior floor always takes precedence. */
   private TextureRegion terrainArt(Tile tile,TextureRegion ground) {
      String base=tile.name==null?"":tile.name;
      if(!tile.isWater&&!tile.isLava&&!ModernWorldGenerator.isRamp(tile)
         &&!WorldReadability.plainFloor(tile.nameUpper)&&elevation.tileHeight(tile)>3
         &&(base.startsWith("mountain")||base.startsWith("rock"))) {
         TextureRegion rockCap=TerrainReliefAssets.get().cap();
         if(rockCap!=null)return rockCap;
      }
      return ground;
   }

   /** Rounded convex rock corners stay inside the old cell footprint. Slopes,
    * shores and concave joins retain their exact shared-corner surface. */
   private boolean cliffBevel(Tile tile,int corner) {
      if(tile==null||tile.isWater||tile.isLava||tile.isWaterfall||ModernWorldGenerator.isRamp(tile))return false;
      float x=tile.position.x,y=tile.position.y,h=elevation.tileHeight(tile);
      if(Math.abs(terrainHeight(tile,x+.01f,y+.01f)-h)>.03f||Math.abs(terrainHeight(tile,x+15.99f,y+.01f)-h)>.03f
         ||Math.abs(terrainHeight(tile,x+15.99f,y+15.99f)-h)>.03f||Math.abs(terrainHeight(tile,x+.01f,y+15.99f)-h)>.03f)return false;
      int dx=corner==0||corner==3?-16:16,dy=corner<2?-16:16;
      Tile sideX=natureTiles.get(lookup.set(x+dx,y)),sideY=natureTiles.get(lookup.set(x,y+dy));
      return sideX!=null&&sideY!=null&&!ModernWorldGenerator.isRamp(sideX)&&!ModernWorldGenerator.isRamp(sideY)
         &&!sideX.isWater&&!sideY.isWater&&!sideX.isLava&&!sideY.isLava
         &&!sideX.isWaterfall&&!sideY.isWaterfall&&!elevation.isSloped(sideX)&&!elevation.isSloped(sideY)
         &&h-elevation.tileHeight(sideX)>3&&h-elevation.tileHeight(sideY)>3
         &&Math.abs(elevation.tileHeight(sideX)-elevation.tileHeight(sideY))<.1f;
   }
   private boolean hasCliffBevel(Tile tile) {
      return cliffBevel(tile,0)||cliffBevel(tile,1)||cliffBevel(tile,2)||cliffBevel(tile,3);
   }
   private float cliffRadius(Tile tile) { return WorldReadability.smallJumpLedge(tile)?3.5f:5.8f; }
   private float cornerX(int corner,float radius,float f) {
      float center=corner==0||corner==3?radius:16-radius;
      return center+MathUtils.cos(MathUtils.PI+corner*MathUtils.PI*.5f+f*MathUtils.PI*.5f)*radius;
   }
   private float cornerY(int corner,float radius,float f) {
      float center=corner<2?radius:16-radius;
      return center+MathUtils.sin(MathUtils.PI+corner*MathUtils.PI*.5f+f*MathUtils.PI*.5f)*radius;
   }
   private void plateauSurface(Tile tile,TextureRegion art,float light,float style) {
      float x=tile.position.x,y=tile.position.y,h=terrainHeight(tile,x+8,y+8),tint=color(light,light,light);
      float[] points=plateauOutline;int count=0;float radius=cliffRadius(tile);
      for(int corner=0;corner<4;corner++) {
         float cx=corner==0||corner==3?0:16,cy=corner<2?0:16;
         if(cliffBevel(tile,corner)) {
            for(int segment=0;segment<=CLIFF_CORNER_SEGMENTS;segment++) {
               float f=segment/(float)CLIFF_CORNER_SEGMENTS;
               points[count++]=cornerX(corner,radius,f);points[count++]=cornerY(corner,radius,f);
            }
         } else {points[count++]=cx;points[count++]=cy;}
      }
      Buffer mesh=geometry.buffer(art.getTexture());
      for(int i=0;i<count;i+=2) {
         int next=(i+2)%count;
         plateauVertex(mesh,art,x,y,h,8,8,tint,style);
         plateauVertex(mesh,art,x,y,h,points[i],points[i+1],tint,style);
         plateauVertex(mesh,art,x,y,h,points[next],points[next+1],tint,style);
      }
   }
   private void plateauVertex(Buffer mesh,TextureRegion art,float x,float y,float h,float px,float py,float tint,float style) {
      boolean geological=art==TerrainReliefAssets.get().cap();
      float u=geological?(x+px)/32f:px/16,v=geological?(y+py)/32f:py/16;
      vertex(mesh,x+px,h,-y-py,tint,MathUtils.lerp(art.getU(),art.getU2(),u),
         MathUtils.lerp(art.getV2(),art.getV(),v),style);
   }

   private float terrainHeight(Tile tile,float x,float y) {
      float depth=tile.isWater&&!tile.isWaterfall?BiomeProfiles.forTile(tile).number("height","shoreDepth",2):0;
      if(depth>0&&natureTiles!=null&&WorldNatureMotion.water(tile)) {
         if(heightShoreTile!=tile) {heightShore.prepare(natureTiles,tile,elevation);heightShoreTile=tile;}
         // Sandy coasts grade into the sea. The same height function also builds
         // exposed edges, so no old vertical bank remains below the animated wash.
         float beach=MathUtils.clamp(heightShore.beachDistance(x,y)/8f,0,1);
         depth*=beach*beach*(3-2*beach);
      }
      return elevation.height(x,y)-depth;
   }

   /** Stitch the real upper and lower surfaces at every exposed step. Never
    * extrude a fallback fence when both sides have the same elevation. */
   private void terrainEdges(Game game,Tile tile,BiomeProfiles.Profile profile) {
      float x=tile.position.x,y=tile.position.y,previous=surfaceLift;surfaceLift=0;
      TextureRegion face=TerrainReliefAssets.get().face(tile,profile);
      boolean authored=face!=null;
      if(face==null)face=assets.named(profile.cliff("front"));
      float radius=cliffRadius(tile);
      for(int side=0;side<4;side++) {
         int dx=side==2?-1:side==3?1:0,dy=side==0?-1:side==1?1:0;
         Tile next=game.map.tiles.get(lookup.set(x+dx*16,y+dy*16));
         if(next==null||next.isWaterfall||next.name.contains("lavafall"))continue;
         float x0=side==3?x+16:x,y0=side==1?y+16:y;
         float x1=side<2?x+16:x0,y1=side<2?y0:y+16;
         float a=terrainHeight(tile,MathUtils.clamp(x0,x+.01f,x+15.99f),MathUtils.clamp(y0,y+.01f,y+15.99f));
         float b=terrainHeight(tile,MathUtils.clamp(x1,x+.01f,x+15.99f),MathUtils.clamp(y1,y+.01f,y+15.99f));
         float lowA=terrainHeight(next,MathUtils.clamp(x0+dx*.02f,next.position.x+.01f,next.position.x+15.99f),
            MathUtils.clamp(y0+dy*.02f,next.position.y+.01f,next.position.y+15.99f));
         float lowB=terrainHeight(next,MathUtils.clamp(x1+dx*.02f,next.position.x+.01f,next.position.x+15.99f),
            MathUtils.clamp(y1+dy*.02f,next.position.y+.01f,next.position.y+15.99f));
         if(a-lowA<.08f&&b-lowB<.08f)continue;
         // Two slopes can cross along an edge: emit only the exposed portion.
         if(a<lowA||b<lowB) {
            float t=(a-lowA)/((a-lowA)-(b-lowB));
            float xx=MathUtils.lerp(x0,x1,t),yy=MathUtils.lerp(y0,y1,t),hh=MathUtils.lerp(a,b,t);
            if(a<lowA){x0=xx;y0=yy;a=lowA=hh;}else{x1=xx;y1=yy;b=lowB=hh;}
         }
         // Brighter directional light reveals strata on the whole exposed face.
         // Dedicated repeating rock remains anchored in world height, so layers
         // flow continuously between adjoining tiles and rounded corners.
         float shade=cliffDirectionLight(side);
         int cornerA=side==0?0:side==1?3:side==2?0:1;
         int cornerB=side==0?1:side==1?2:side==2?3:2;
         if(cliffBevel(tile,cornerA)){if(side<2)x0+=radius;else y0+=radius;}
         if(cliffBevel(tile,cornerB)){if(side<2)x1-=radius;else y1-=radius;}
         cliffFace(tile,profile,face,authored,side,x0,y0,x1,y1,lowA,lowB,a,b,shade);
         // An irregular weathered shoulder belongs to the actual upper ground,
         // with a contact shadow below. It is neither a rail nor a flat sand lid.
         if(Math.max(a-lowA,b-lowB)>3) {
            for(int segment=0;segment<8;segment++) {
               float f0=segment/8f,f1=(segment+1)/8f;
               terrainShoulder(tile,profile,MathUtils.lerp(x0,x1,f0),MathUtils.lerp(y0,y1,f0),
                  MathUtils.lerp(x0,x1,f1),MathUtils.lerp(y0,y1,f1),
                  MathUtils.lerp(a,b,f0),MathUtils.lerp(a,b,f1),dx,dy,dx,dy);
            }
            float footWidth=MathUtils.clamp(Math.max(a-lowA,b-lowB)*.34f,2.7f,5.2f);
            float ox=dx*footWidth,oy=dy*footWidth;
            float la=terrainHeight(next,MathUtils.clamp(x0+ox,next.position.x+.01f,next.position.x+15.99f),
               MathUtils.clamp(y0+oy,next.position.y+.01f,next.position.y+15.99f));
            float lb=terrainHeight(next,MathUtils.clamp(x1+ox,next.position.x+.01f,next.position.x+15.99f),
               MathUtils.clamp(y1+oy,next.position.y+.01f,next.position.y+15.99f));
            Buffer contact=shadows.buffer(whiteTexture);
            float dark=Color.toFloatBits(.06f,.075f,.11f,.28f),clear=Color.toFloatBits(.06f,.075f,.11f,0);
            vertex(contact,x0,lowA+.07f,-y0,dark,.5f,.5f,2);
            vertex(contact,x1,lowB+.07f,-y1,dark,.5f,.5f,2);
            vertex(contact,x1+ox,lb+.07f,-y1-oy,clear,.5f,.5f,2);
            vertex(contact,x1+ox,lb+.07f,-y1-oy,clear,.5f,.5f,2);
            vertex(contact,x0+ox,la+.07f,-y0-oy,clear,.5f,.5f,2);
            vertex(contact,x0,lowA+.07f,-y0,dark,.5f,.5f,2);
         }
      }
      for(int corner=0;corner<4;corner++)if(cliffBevel(tile,corner)) {
         float cx=x+(corner==0||corner==3?0:16),cy=y+(corner<2?0:16);
         float sx=corner==0||corner==3?1:-1,sy=corner<2?1:-1;
         Tile low=natureTiles.get(lookup.set(x-sx*16,y));
         float bottom=elevation.tileHeight(low),top=terrainHeight(tile,x+8,y+8);
         TextureRegion floor=assets.terrain(low,natureSeconds);
         if(floor==null)floor=assets.cell(WorldMaterials.terrainName(low),low.position.x,low.position.y,natureSeconds);
         if(floor==null)floor=white;
         floor=terrainArt(low,floor);
         // Both the top and face use the same smooth quarter-circle. Fill
         // the small removed wedge at the real lower height, sealing the contour.
         for(int segment=0;segment<CLIFF_CORNER_SEGMENTS;segment++) {
            float f0=segment/(float)CLIFF_CORNER_SEGMENTS,f1=(segment+1)/(float)CLIFF_CORNER_SEGMENTS;
            float ax=x+cornerX(corner,radius,f0),ay=y+cornerY(corner,radius,f0);
            float bx=x+cornerX(corner,radius,f1),by=y+cornerY(corner,radius,f1);
            float angle0=MathUtils.PI+corner*MathUtils.PI*.5f+f0*MathUtils.PI*.5f;
            float angle1=MathUtils.PI+corner*MathUtils.PI*.5f+f1*MathUtils.PI*.5f;
            float shade0=cliffNormalLight(MathUtils.cos(angle0),MathUtils.sin(angle0));
            float shade1=cliffNormalLight(MathUtils.cos(angle1),MathUtils.sin(angle1));
            cliffSegment(tile,profile,face,authored,ax,ay,bx,by,bottom,bottom,top,top,shade0,shade1);
            Buffer groundMesh=geometry.buffer(floor.getTexture());
            plateauVertex(groundMesh,floor,x,y,bottom,cx-x,cy-y,WHITE,0);
            plateauVertex(groundMesh,floor,x,y,bottom,ax-x,ay-y,WHITE,0);
            plateauVertex(groundMesh,floor,x,y,bottom,bx-x,by-y,WHITE,0);
            float nx0=MathUtils.cos(angle0),ny0=MathUtils.sin(angle0);
            float nx1=MathUtils.cos(angle1),ny1=MathUtils.sin(angle1);
            terrainShoulder(tile,profile,ax,ay,bx,by,top,top,nx0,ny0,nx1,ny1);
         }
         roundedCornerFrames++;
      }
      surfaceLift=previous;
   }

   /** The broken stone/soil edge fades inward, following the same height plane
    * as the ground. World-space width and UVs remain continuous between cells. */
   private void terrainShoulder(Tile tile,BiomeProfiles.Profile profile,float ax,float ay,float bx,float by,
         float topA,float topB,float nx0,float ny0,float nx1,float ny1) {
      if(tile.isWater||tile.isLava||ModernWorldGenerator.isRamp(tile))return;
      TextureRegion art=TerrainReliefAssets.get().cap();if(art==null)return;
      float width=WorldReadability.smallJumpLedge(tile)?2.8f:5.0f;
      float wa=width+.48f*MathUtils.sin(ax*.29f+ay*.23f)+.25f*MathUtils.sin(ax*.79f-ay*.37f);
      float wb=width+.48f*MathUtils.sin(bx*.29f+by*.23f)+.25f*MathUtils.sin(bx*.79f-by*.37f);
      float ix0=ax-nx0*wa,iy0=ay-ny0*wa,ix1=bx-nx1*wb,iy1=by-ny1*wb;
      float x=tile.position.x,y=tile.position.y;
      float innerA=terrainHeight(tile,MathUtils.clamp(ix0,x+.01f,x+15.99f),MathUtils.clamp(iy0,y+.01f,y+15.99f));
      float innerB=terrainHeight(tile,MathUtils.clamp(ix1,x+.01f,x+15.99f),MathUtils.clamp(iy1,y+.01f,y+15.99f));
      float red=.68f,green=.66f,blue=.56f;
      if(profile.id.equals("snow")){red=.73f;green=.80f;blue=.88f;}
      else if(profile.id.equals("volcano")){red=.43f;green=.42f;blue=.52f;}
      else if(TerrainReliefAssets.earth(tile,profile)){red=.61f;green=.69f;blue=.46f;}
      else if(profile.id.equals("desert")||profile.id.equals("beach")||profile.id.equals("oasis")){red=.79f;green=.68f;blue=.48f;}
      float outer=Color.toFloatBits(red*.94f,green*.94f,blue*.94f,.98f);
      float middle=Color.toFloatBits(red,green,blue,.80f),inner=Color.toFloatBits(red,green,blue,0);
      float mx0=MathUtils.lerp(ax,ix0,.46f),my0=MathUtils.lerp(ay,iy0,.46f);
      float mx1=MathUtils.lerp(bx,ix1,.46f),my1=MathUtils.lerp(by,iy1,.46f);
      float middleA=terrainHeight(tile,MathUtils.clamp(mx0,x+.01f,x+15.99f),MathUtils.clamp(my0,y+.01f,y+15.99f));
      float middleB=terrainHeight(tile,MathUtils.clamp(mx1,x+.01f,x+15.99f),MathUtils.clamp(my1,y+.01f,y+15.99f));
      Buffer cap=translucent.buffer(art.getTexture());
      plateauVertex(cap,art,x,y,topA+.09f,ax-x,ay-y,outer,0);
      plateauVertex(cap,art,x,y,topB+.09f,bx-x,by-y,outer,0);
      plateauVertex(cap,art,x,y,middleB+.09f,mx1-x,my1-y,middle,0);
      plateauVertex(cap,art,x,y,middleB+.09f,mx1-x,my1-y,middle,0);
      plateauVertex(cap,art,x,y,middleA+.09f,mx0-x,my0-y,middle,0);
      plateauVertex(cap,art,x,y,topA+.09f,ax-x,ay-y,outer,0);
      plateauVertex(cap,art,x,y,middleA+.09f,mx0-x,my0-y,middle,0);
      plateauVertex(cap,art,x,y,middleB+.09f,mx1-x,my1-y,middle,0);
      plateauVertex(cap,art,x,y,innerB+.09f,ix1-x,iy1-y,inner,0);
      plateauVertex(cap,art,x,y,innerB+.09f,ix1-x,iy1-y,inner,0);
      plateauVertex(cap,art,x,y,innerA+.09f,ix0-x,iy0-y,inner,0);
      plateauVertex(cap,art,x,y,middleA+.09f,mx0-x,my0-y,middle,0);
   }

   private void cliffFace(Tile tile,BiomeProfiles.Profile profile,TextureRegion art,boolean authored,int side,float x0,float y0,float x1,float y1,
         float lowA,float lowB,float highA,float highB,float shade) {
      float start=cliffCornerLight(tile,side,x0,y0,shade),end=cliffCornerLight(tile,side,x1,y1,shade);
      cliffSegment(tile,profile,art,authored,x0,y0,x1,y1,lowA,lowB,highA,highB,start,end);
   }
   /** Eroded facets stay inside the high cell and meet the exact upper/lower
    * surface. Broad vertical rock shapes replace the old horizontal wafer. */
   private void cliffSegment(Tile tile,BiomeProfiles.Profile profile,TextureRegion art,boolean authored,float x0,float y0,float x1,float y1,
         float lowA,float lowB,float highA,float highB,float start,float end) {
      Buffer mesh=geometry.buffer(art.getTexture());
      float edgeX=x1-x0,edgeY=y1-y0,length=(float)Math.sqrt(edgeX*edgeX+edgeY*edgeY);
      float outwardX=length>.01f?edgeY/length:0,outwardY=length>.01f?-edgeX/length:0;
      if(outwardX*((x0+x1)*.5f-tile.position.x-8)+outwardY*((y0+y1)*.5f-tile.position.y-8)<0) {
         outwardX=-outwardX;outwardY=-outwardY;
      }
      for(int col=0;col<6;col++) {
         float f0=col/6f,f1=(col+1)/6f;
         float a=MathUtils.lerp(start,end,f0),b=MathUtils.lerp(start,end,f1);
         float ax=MathUtils.lerp(x0,x1,f0),ay=MathUtils.lerp(y0,y1,f0);
         float bx=MathUtils.lerp(x0,x1,f1),by=MathUtils.lerp(y0,y1,f1);
         float bottomA=MathUtils.lerp(lowA,lowB,f0),bottomB=MathUtils.lerp(lowA,lowB,f1);
         float topA=MathUtils.lerp(highA,highB,f0),topB=MathUtils.lerp(highA,highB,f1);
         float nx0=cliffOutward(tile,ax,ay,true,outwardX),ny0=cliffOutward(tile,ax,ay,false,outwardY);
         float nx1=cliffOutward(tile,bx,by,true,outwardX),ny1=cliffOutward(tile,bx,by,false,outwardY);
         float erosionA=authored?Math.min(1.15f,(topA-bottomA)*.095f)*cliffErosion(ax,ay):0;
         float erosionB=authored?Math.min(1.15f,(topB-bottomB)*.095f)*cliffErosion(bx,by):0;
         for(int row=0;row<5;row++) {
            float t0=cliffRow(row),t1=cliffRow(row+1);
            float shade0=.82f+.21f*t0,shade1=.82f+.21f*t1;
            float ha0=MathUtils.lerp(bottomA,topA,t0),ha1=MathUtils.lerp(bottomA,topA,t1);
            float hb0=MathUtils.lerp(bottomB,topB,t0),hb1=MathUtils.lerp(bottomB,topB,t1);
            float insetA0=erosionA*cliffInset(row),insetA1=erosionA*cliffInset(row+1);
            float insetB0=erosionB*cliffInset(row),insetB1=erosionB*cliffInset(row+1);
            float u0=MathUtils.lerp(art.getU(),art.getU2(),f0),u1=MathUtils.lerp(art.getU(),art.getU2(),f1);
            float v0=MathUtils.lerp(art.getV2(),art.getV(),t0),v1=MathUtils.lerp(art.getV2(),art.getV(),t1);
            float vb0=v0,vb1=v1;
            if(authored) {
               // Exact shared world vertices carry identical UVs even where a
               // curved segment changes direction, avoiding a visible arc seam.
               u0=art.getU()+(art.getU2()-art.getU())*(ax+ay)/48f;
               u1=art.getU()+(art.getU2()-art.getU())*(bx+by)/48f;
               v0=art.getV2()+(art.getV()-art.getV2())*ha0/32f;
               v1=art.getV2()+(art.getV()-art.getV2())*ha1/32f;
               vb0=art.getV2()+(art.getV()-art.getV2())*hb0/32f;
               vb1=art.getV2()+(art.getV()-art.getV2())*hb1/32f;
            }
            boolean soil=WorldReadability.smallJumpLedge(tile);
            float c00=rockLight(a*shade0,profile,soil),c10=rockLight(b*shade0,profile,soil);
            float c11=rockLight(b*shade1,profile,soil),c01=rockLight(a*shade1,profile,soil);
            vertex(mesh,ax-nx0*insetA0,ha0,-ay+ny0*insetA0,c00,u0,v0,0);
            vertex(mesh,bx-nx1*insetB0,hb0,-by+ny1*insetB0,c10,u1,vb0,0);
            vertex(mesh,bx-nx1*insetB1,hb1,-by+ny1*insetB1,c11,u1,vb1,0);
            vertex(mesh,bx-nx1*insetB1,hb1,-by+ny1*insetB1,c11,u1,vb1,0);
            vertex(mesh,ax-nx0*insetA1,ha1,-ay+ny0*insetA1,c01,u0,v1,0);
            vertex(mesh,ax-nx0*insetA0,ha0,-ay+ny0*insetA0,c00,u0,v0,0);
         }
      }
      // The plateau's own vegetation hangs over the upper soil edge. This real
      // material strip explains a grassy terrace without a yellow rail or icon.
      if(TerrainReliefAssets.earth(tile,profile)&&WorldMaterials.softGrass(WorldMaterials.terrainName(tile))) {
         TextureRegion turf=assets.terrain(tile,natureSeconds);
         if(turf!=null) {
            float ax=x0+(x0-tile.position.x-8)*.001f,ay=y0+(y0-tile.position.y-8)*.001f;
            float bx=x1+(x1-tile.position.x-8)*.001f,by=y1+(y1-tile.position.y-8)*.001f;
            float lipA=Math.min(highA-lowA,.85f+.22f*MathUtils.sin(x0*.47f+y0*.31f));
            float lipB=Math.min(highB-lowB,.85f+.22f*MathUtils.sin(x1*.47f+y1*.31f));
            quad(geometry.buffer(turf.getTexture()),ax,highA-lipA,-ay,bx,highB-lipB,-by,
               bx,highB+.015f,-by,ax,highA+.015f,-ay,turf,rockLight(.92f,profile),1);
         }
      }
   }
   private static float cliffRow(int row) {
      return row==0?0:row==1?.14f:row==2?.26f:row==3?.78f:row==4?.92f:1;
   }
   private static float cliffInset(int row) { return row==0||row==5?0:row==1?.65f:row==4?.50f:1; }
   private static float cliffErosion(float x,float y) {
      return .64f+.20f*MathUtils.sin(x*.29f+y*.23f)+.14f*MathUtils.sin(x*.71f-y*.27f);
   }
   private float cliffOutward(Tile tile,float px,float py,boolean horizontal,float fallback) {
      float x=px-tile.position.x,y=py-tile.position.y,radius=cliffRadius(tile);
      for(int corner=0;corner<4;corner++) {
         boolean left=corner==0||corner==3,top=corner<2;
         if((left?x<=radius+.01f:x>=16-radius-.01f)&&(top?y<=radius+.01f:y>=16-radius-.01f)&&cliffBevel(tile,corner)) {
            float dx=x-(left?radius:16-radius),dy=y-(top?radius:16-radius);
            float length=(float)Math.sqrt(dx*dx+dy*dy);
            if(length>.01f)return (horizontal?dx:dy)/length;
         }
      }
      // Raw convex, concave and stair-side junctions use the same world vertex.
      // Summing its low quadrants gives both incident high owners one normal;
      // considering only a tile's two side neighbours misses a concave notch.
      int sx=x<.01f?-1:x>15.99f?1:0,sy=y<.01f?-1:y>15.99f?1:0;
      if(sx!=0&&sy!=0) {
         float height=terrainHeight(tile,MathUtils.clamp(px,tile.position.x+.01f,tile.position.x+15.99f),
            MathUtils.clamp(py,tile.position.y+.01f,tile.position.y+15.99f));
         float vx=Math.round(px/16)*16,vy=Math.round(py/16)*16,nx=0,ny=0;
         for(int qy=-1;qy<=0;qy++)for(int qx=-1;qx<=0;qx++) {
            Tile candidate=natureTiles.get(lookup.set(vx+qx*16,vy+qy*16));if(candidate==null)continue;
            float next=terrainHeight(candidate,MathUtils.clamp(vx,candidate.position.x+.01f,candidate.position.x+15.99f),
               MathUtils.clamp(vy,candidate.position.y+.01f,candidate.position.y+15.99f));
            if(height-next>.1f){nx+=qx<0?-1:1;ny+=qy<0?-1:1;}
         }
         float length=(float)Math.sqrt(nx*nx+ny*ny);
         if(length>.01f)return(horizontal?nx:ny)/length;
      }
      return fallback;
   }
   private static float rockLight(float light,BiomeProfiles.Profile profile) { return rockLight(light,profile,false); }
   private static float rockLight(float light,BiomeProfiles.Profile profile,boolean soil) {
      light=MathUtils.clamp(light,.64f,1);
      if(soil)return color(light*.94f,light*.83f,light*.64f);
      if(profile.id.equals("desert")||profile.id.equals("beach")||profile.id.equals("oasis"))
         return color(light,light*.91f,light*.73f);
      if(profile.id.equals("snow"))return color(light*.84f,light*.94f,light);
      if(profile.id.equals("volcano"))return color(light*.68f,light*.66f,light*.90f);
      return color(light*.96f,light,light*.94f);
   }
   private static float cliffNormalLight(float nx,float ny) { return .92f-.045f*nx-.05f*ny; }
   private static float cliffDirectionLight(int side) {
      return cliffNormalLight(side==2?-1:side==3?1:0,side==0?-1:side==1?1:0);
   }
   private float cliffCornerLight(Tile tile,int side,float x,float y,float original) {
      int perpendicular=side<2?(x<tile.position.x+8?2:3):(y<tile.position.y+8?0:1);
      int dx=perpendicular==2?-16:perpendicular==3?16:0,dy=perpendicular==0?-16:perpendicular==1?16:0;
      Tile next=natureTiles==null?null:natureTiles.get(lookup.set(tile.position.x+dx,tile.position.y+dy));
      if(next==null||elevation.tileHeight(tile)-elevation.tileHeight(next)<3)return original;
      float adjacent=cliffDirectionLight(perpendicular);
      return (original+adjacent)*.5f;
   }

   private boolean building(Game game, Tile tile, float x, float y, String name) {
      String identity=WorldMaterials.objectName(tile);
      WorldMaterials.Kind kind=WorldMaterials.kind(tile,identity);
      if(kind==WorldMaterials.Kind.FURNITURE) {
         TextureRegion art=assets.named(identity);
         if(art==null)return false;
         float scale=VisualGeometry.fitScale(art.getRegionWidth(),art.getRegionHeight(),24,29);
         float width=assets.worldWidth(art,art.getRegionWidth()*scale),height=assets.worldHeight(art,art.getRegionHeight()*scale);
         assetShadow(art,x+8,y+7,width*.28f,2.4f);
         captureBedDepth="bed".equals(identity)&&game.player.isSleeping
            &&tile.position.equals(game.player.sleepingDir);
         try {upright(art,x+8-width*.5f,y+7,.16f,width,height,WHITE);}
         finally {captureBedDepth=false;}
         return true;
      }
      if(kind==WorldMaterials.Kind.BRIDGE) {
         floor(assets.named("wood_floor"),x,y,16,16,.1f,WHITE,geometry);return true;
      }
      if(kind==WorldMaterials.Kind.FENCE) {
         if(tile.isSolid)upright(assets.named("fence_wood"),x,y+8,.1f,16,12,WHITE);
         else floor(assets.named("wood_floor"),x,y,16,16,.1f,WHITE,geometry);
         return true;
      }
      if(kind==WorldMaterials.Kind.ROOF) {
         floor(assets.named("roof"),x,y,16,16,22,WHITE,geometry);return true;
      }
      if(kind==WorldMaterials.Kind.DOOR) {
         floor(assets.named("steps"),x,y,16,12,.1f,WHITE,geometry);
         upright(assets.named("door_locked".equals(identity)?"door_locked":tile.isSolid?"door":"door_open"),x,y+14,.1f,16,22,WHITE);
         return true;
      }
      String upper=tile.nameUpper==null?"":tile.nameUpper.toLowerCase(java.util.Locale.ROOT);
      String lower=tile.name==null?"":tile.name.toLowerCase(java.util.Locale.ROOT);
      boolean caveWall=WorldMaterials.caveWall(tile);
      boolean structural=kind==WorldMaterials.Kind.WALL||kind==WorldMaterials.Kind.WINDOW;
      if(structural) {
         TextureRegion face=assets.named(WorldMaterials.wallFace(tile));
         TextureRegion cap=assets.named(WorldMaterials.wallCap(tile));
         if(cap==null)cap=assets.named("wood_floor");
         float height=caveWall?16:24,z=-y;
         // The cap can cover the actor even when the visible front face is far
         // below them on screen. Fade the complete intervening wall segment;
         // retain its geometry and neighbour culling, so corners never gain holes.
         float alpha=interiorWallOccludesPlayer(game,x,y,height)?.12f:1f;
         WorldMeshBatch groups=alpha<1f?translucent:geometry;
         float wallTint=Color.toFloatBits(1,1,1,alpha);
         Buffer mesh=groups.buffer(face.getTexture());
         if(!structuralNeighbour(game,x,y-16))quad(mesh,x,0,z,x+16,0,z,x+16,height,z,x,height,z,face,wallTint);
         if(!structuralNeighbour(game,x,y+16))quad(mesh,x+16,0,z-16,x,0,z-16,x,height,z-16,x+16,height,z-16,face,Color.toFloatBits(.82f,.85f,.87f,alpha));
         if(!structuralNeighbour(game,x-16,y))quad(mesh,x,0,z-16,x,0,z,x,height,z,x,height,z-16,face,Color.toFloatBits(.88f,.90f,.91f,alpha));
         if(!structuralNeighbour(game,x+16,y))quad(mesh,x+16,0,z,x+16,0,z-16,x+16,height,z-16,x+16,height,z,face,Color.toFloatBits(.73f,.78f,.81f,alpha));
         floor(cap,x,y,16,16,height,wallTint,groups);
         if(kind==WorldMaterials.Kind.WINDOW) {
            TextureRegion window=assets.named("window");
            if(window!=null)quad(groups.buffer(window.getTexture()),x+3,7,z+.05f,x+13,7,z+.05f,x+13,19,z+.05f,x+3,19,z+.05f,window,wallTint);
         }
         return true;
      }
      if(upper.isEmpty()&&lower.contains("floor"))return true;
      return false;
   }

   /** Presentation only: test sight lines to feet/torso, never alter a map tile. */
   private boolean interiorWallOccludesPlayer(Game game,float x,float y,float height) {
      if(game.map.tiles==game.map.overworldTiles)return false;
      float px=game.player.position.x+8,py=game.player.position.y+7;
      // Behind/alongside walls keep their full appearance. Camera pitch is fixed;
      // the wall must lie wholly on the camera side of the player's ground point.
      if(y+TILE>py)return false;
      wallOcclusionBounds.set(wallOcclusionMin.set(x,surfaceLift,-y-TILE),
         wallOcclusionMax.set(x+TILE,surfaceLift+height,-y));
      float ground=elevation.height(px,py)+.15f;
      for(int row=0;row<3;row++) {
         float h=row*8f-1f;
         for(int col=-1;col<=1;col++) {
            wallOcclusionTarget.set(px+col*8f,ground+h*MathUtils.cosDeg(50),-py-h*MathUtils.sinDeg(50));
            wallOcclusionRay.set(camera.position,wallOcclusionTarget.sub(camera.position).nor());
            if(Intersector.intersectRayBoundsFast(wallOcclusionRay,wallOcclusionBounds))return true;
         }
      }
      return false;
   }

   private boolean structuralNeighbour(Game game,float x,float y) {
      Tile tile=game.map.tiles.get(lookup.set(x,y));
      if(tile==null||!tile.isSolid)return false;
      WorldMaterials.Kind kind=WorldMaterials.kind(tile,WorldMaterials.objectName(tile));
      return kind==WorldMaterials.Kind.WALL||kind==WorldMaterials.Kind.WINDOW;
   }

   private void prism(float x,float y,float base,float width,float depth,float top,TextureRegion face,float tint) {
      Buffer mesh=geometry.buffer(face.getTexture());float z=-y;
      quad(mesh,x,base,z,x+width,base,z,x+width,top,z,x,top,z,face,tint);
      quad(mesh,x+width,base,z,x+width,base,z-depth,x+width,top,z-depth,x+width,top,z,face,tint);
      quad(mesh,x,base,z-depth,x,base,z,x,top,z,x,top,z-depth,face,tint);
      quad(mesh,x+width,base,z-depth,x,base,z-depth,x,top,z-depth,x+width,top,z-depth,face,tint);
      floor(face,x,y,width,depth,top,tint,geometry);
   }

   private static boolean isTree(Tile tile, String lower, String upper) {
      return WorldMaterials.isTree(tile);
   }

   private void pokemon(Game game, Pokemon pokemon) {
      if (pokemon == null || pokemon.currOwSprite == null || pokemon.mapTiles != game.map.tiles
         || pokemon.inBattle || (pokemon.drawLower == null && pokemon.drawUpper == null)) return;
      boolean following = pokemon.drawLower != null && pokemon.drawLower.following
         || pokemon.drawUpper != null && pokemon.drawUpper.following;
      if (!pokemon.drawThisFrame && !following) return;
      surfaceLift=elevation.height(pokemon.position.x+8,pokemon.position.y+8);
      boolean moving = moving(pokemon, pokemon.position.x, pokemon.position.y);
      PmdPokemonSprites sprites = PmdPokemonSprites.get();
      PmdPokemonSprites.Frame frame = pokemon.isEgg ? null : sprites.frame(pokemon, pokemon.dirFacing, moving ? "Walk" : "Idle", seconds);
      if (frame != null) {
         // One envelope for both locomotion animations and all directions: no
         // resizing on a turn, and pixels below the PMD anchor stay above ground.
         float extent=1,bottom=0;
         for(String direction:ACTOR_DIRECTIONS)for(String animation:LOCOMOTION_ANIMATIONS) {
            PmdPokemonSprites.AnimationBounds bounds=sprites.bounds(pokemon,direction,animation);
            if(bounds!=null){extent=Math.max(extent,Math.max(bounds.width,bounds.height));bottom=Math.min(bottom,bounds.minY);}
         }
         float scale = Math.min(.85f, 28f / extent);
         float anchorY = frame.anchorY;
         TextureRegion region = frame.region;
         if (pokemon.inWater && pokemon.drawUpper != null) {
            int hidden = Math.max(0, Math.min(region.getRegionHeight() - 1, Math.round(anchorY + 5)));
            spritePart.setRegion(region); spritePart.setRegionHeight(region.getRegionHeight() - hidden);
            region = spritePart; anchorY -= hidden;
         } else shadow(pokemon.position.x + 8, pokemon.position.y + 7, Math.min(7, frame.width * scale * .25f), 2.5f);
         anchored(region, pokemon.position.x + 8, pokemon.position.y + 7,
            .15f-(pokemon.inWater?0:bottom*scale*MathUtils.cosDeg(50)),
            frame.anchorX, anchorY, scale);
         shinySparkles(pokemon,pokemon.position.x+8,pokemon.position.y+7,24);
         return;
      }
      Sprite source = pokemon.currOwSprite;
      if(!pokemon.isEgg && !pokemon.isGhost)assets.reportMissing("pokemon/"+pokemon.specie.name,
         game.map.tiles.get(lookup.set(MathUtils.floor(pokemon.position.x/16)*16,MathUtils.floor(pokemon.position.y/16)*16)),game.map.timeOfDay);
      if (pokemon.inWater && pokemon.drawUpper != null) {
         int height = Math.max(1, source.getRegionHeight() - 8 + pokemon.drawUpper.floatOffset + pokemon.drawUpper.floatOffset2);
         spritePart.setRegion(source);
         spritePart.setRegionHeight(height);
         upright(spritePart, pokemon.position.x, pokemon.position.y + 7f, 0.4f,
            source.getRegionWidth(), height, WHITE);
      } else {
         actor(source, pokemon.position.x, pokemon.position.y, 0f);
      }
      shinySparkles(pokemon,pokemon.position.x+8,pokemon.position.y+7,24);
   }

   private void actor(Sprite sprite, float x, float y, float lift) {
      if (sprite == null || sprite.getTexture() == null) return;
      float width = sprite.getRegionWidth();
      float height = sprite.getRegionHeight();
      shadow(x + width * 0.5f, y + 7f, width * 0.30f, 2.5f);
      actorPlaneZ=-y-7f;
      upright(sprite, x, y + 7f, 0.15f + lift, width, height, sprite.getColor().toFloatBits(),4f);
   }

   private void trainer(Player player, float lift) {
      surfaceLift=elevation.height(player.position.x+8,player.position.y+8);
      TextureRegion region = assets.trainer(player, seconds, moving(player, player.position.x, player.position.y));
      assetShadow(region, player.position.x + 8, player.position.y + 7, 4.5f, 2.5f);
      trainerImage(region,32,player.position.x+8,player.position.y+7,.15f+lift,3,.95f);
   }

   /** Field moves use the same PMD/BW actors as walking, never composite GB sprites. */
   private void fieldPlayer(Game game) {
      Player player = game.player;
      float x = player.position.x + 8, y = player.position.y + 7;
      surfaceLift=elevation.height(x,y);
      String move = player.currFieldMove;
      boolean walking = moving(player, player.position.x, player.position.y);
      TextureRegion person = assets.trainer(player, seconds, walking);
      float lift = .15f;
      Pokemon companion = player.hmPokemon;
      boolean mount = companion != null && ("SURF".equals(move) || "RIDE".equals(move) || "FLY".equals(move));
      if (mount) {
         float altitude = 0f;
         if ("FLY".equals(move) && player.flyingAction != null) {
            altitude = Math.max(0, player.flyingAction.yOffset) * .64f;
            x += player.flyingAction.xOffset;
         }
         if ("SURF".equals(move)) {
            float ripple = 1f + MathUtils.sin(seconds * 4f) * .1f;
            ring(x,y,11*ripple,.65f,.09f,Color.toFloatBits(.78f,.96f,1f,.5f));
            ring(x,y,8*ripple,.45f,.1f,Color.toFloatBits(.78f,.96f,1f,.3f));
            altitude += .6f + MathUtils.sin(seconds * 3f) * .5f;
         } else shadow(x,y,8f,3f);
         float height = fieldPokemon(companion,player.dirFacing,x,y,altitude,walking ? "Walk" : "Idle",true);
         surfaceLift=elevation.height(x,y);
         float backFraction="SURF".equals(move)?.38f:"FLY".equals(move)?.70f:.60f;
         lift += altitude + Math.max(6f, Math.min("SURF".equals(move)?13f:23f, height*backFraction));
         person=assets.mountedTrainer(player,move,seconds);
         // The seat anchor lies at the hip. Bent legs remain below it, with
         // the complete dedicated riding pose visible above the mount.
         float seatX="left".equals(player.dirFacing)?2.2f:"right".equals(player.dirFacing)?-2.2f:0;
         float seatY="down".equals(player.dirFacing)?2.4f:"up".equals(player.dirFacing)?-1.6f:0;
         trainerImage(person,32,x+seatX,y+seatY,lift+MountedTrainerSprites.sway(move,seconds,walking),6,.72f);
         return;
      }
      if (companion != null && move != null && !move.isEmpty()) {
         abilityCompanion(game,companion,move,x,y,walking);
         surfaceLift=elevation.height(x,y);
      } else {presentedCompanion=null;companionMap=null;}
      if (player.isSleeping) {
         if (player.sleepingDir != null) {
            // The saved bed target differs from the walkable cell beside it.
            // Match the bed billboard's pillow plane, leaving its blanket visible.
            x=player.sleepingDir.x+8;y=player.sleepingDir.y+7;
            surfaceLift=elevation.height(x,y);
            TextureRegion bed=assets.named("bed");
            float bedScale=VisualGeometry.fitScale(bed.getRegionWidth(),bed.getRegionHeight(),24,29);
            float bedWidth=assets.worldWidth(bed,bed.getRegionWidth()*bedScale);
            float bedHeight=assets.worldHeight(bed,bed.getRegionHeight()*bedScale);
            float pillow=bedHeight*.68f,bedLift=.24f;
            BwAssets.SpriteLayout bedLayout=assets.layout(bed);
            if (bedLayout!=null && bedLayout.customized) {
               x+=bedLayout.offsetX+bedWidth*(.5f-bedLayout.anchorX(.5f));
               y+=bedLayout.offsetY-bedHeight*bedLayout.anchorY(0f)*MathUtils.sinDeg(50f);
               bedLift+=bedLayout.elevation-bedHeight*bedLayout.anchorY(0f)*MathUtils.cosDeg(50f);
            }
            person=assets.trainer(player,"down",seconds,false);
            // The bed's GPU depth interpolates between its actual endpoints.
            // Re-projecting a separate head plane differs under perspective,
            // even with identical ground anchors, and can hide the whole face.
            matchBedDepth=bedDepthValid;
            try {trainerImage(person,14,x,y+pillow*MathUtils.sinDeg(50f),
               bedLift+pillow*MathUtils.cosDeg(50f),0,.55f);}
            finally {matchBedDepth=false;}
         } else {
            // Ground sleeping keeps its own bag; furniture sleep uses the actual bed.
            floor(white,x-7,y-7,14,22,.16f,color(.16f,.32f,.39f),geometry);
            floor(white,x-6,y+8,12,6,.2f,color(.81f,.86f,.78f),geometry);
            trainerImage(person,14,x,y+6,.3f,0,.85f);
         }
      } else if (player.isSitting) {
         for (Action action : game.actionStack) if (action instanceof Player.Sitting) {
            Player.Sitting sitting=(Player.Sitting)action;
            x=sitting.sittingOn.position.x+sitting.offsetX+8;
            y=sitting.sittingOn.position.y+sitting.offsetY+7;
            surfaceLift=elevation.height(x,y);
            break;
         }
         trainerImage(person,26,x,y,.3f,0,.85f);
      } else {
         assetShadow(person,x,y,4.5f,2.5f);
         trainerImage(person,32,x,y,lift+Math.max(0,DrawPlayerUpper.pokemonOffsetY)*.4f,3,.95f);
      }
      if (player.isFishing) {
         Vector2 target=player.facingPos();
         float endX=target.x+8, endY=target.y+8;
         line(x+4,9,y,endX,15,endY,.65f,color(.50f,.32f,.16f));
         line(endX,15,endY,endX,.2f,endY+4,.25f,color(.87f,.92f,.89f));
         floor(white,endX-1.2f,endY+3,2.4f,2.4f,.2f,color(.98f,.36f,.24f),geometry);
         ring(endX,endY+4,3f,.35f,.12f,Color.toFloatBits(.8f,.97f,1,.6f));
      }
   }

   private float fieldPokemon(Pokemon pokemon,String direction,float x,float y,float lift,String animation) {
      return fieldPokemon(pokemon,direction,x,y,lift,animation,false);
   }

   private float fieldPokemon(Pokemon pokemon,String direction,float x,float y,float lift,String animation,boolean mounted) {
      surfaceLift=elevation.height(x,y);
      PmdPokemonSprites sprites=PmdPokemonSprites.get();
      PmdPokemonSprites.Frame frame=sprites.frame(pokemon,direction,animation,seconds);
      if(frame==null) {
         if(pokemon.currOwSprite!=null) actor(pokemon.currOwSprite,x-8,y-7,lift);
         shinySparkles(pokemon,x,y,24);
         return 24f;
      }
      PmdPokemonSprites.AnimationBounds bounds=sprites.bounds(pokemon,direction,animation);
      float h=bounds==null?frame.height:bounds.height;
      float w=bounds==null?frame.width:bounds.width;
      float extent=Math.max(w,h);
      for(String facing:ACTOR_DIRECTIONS)for(String pose:mounted?LOCOMOTION_ANIMATIONS:new String[]{"Idle","Walk","Attack"}) {
         PmdPokemonSprites.AnimationBounds envelope=sprites.bounds(pokemon,facing,pose);
         if(envelope!=null)extent=Math.max(extent,Math.max(envelope.width,envelope.height));
      }
      float scale=Math.min(mounted?1.15f:.85f,(mounted?46f:32f)/Math.max(1f,extent));
      float bottom=bounds==null?-frame.anchorY:bounds.minY;
      shadow(x,y,Math.min(8,w*scale*.28f),2.7f);
      anchored(frame.region,x,y,lift-Math.min(0,bottom)*scale*MathUtils.cosDeg(50),frame.anchorX,frame.anchorY,scale);
      shinySparkles(pokemon,x,y,h*scale);
      return h*scale;
   }

   /** The companion has its own visible movement and action pose. Gameplay keeps
    * the original Follow action, timers, positions and network protocol. */
   private void abilityCompanion(Game game,Pokemon pokemon,String move,float x,float y,boolean walking) {
      if(!WorldReadability.companionTarget(game.map.tiles,x,y,game.player.dirFacing,companionGoal,lookup)) {
         // A completely enclosed player has no honest floor location for a
         // second actor. Do not stack it inside the trainer or through a wall.
         presentedCompanion=null;return;
      }
      boolean reset=presentedCompanion!=pokemon||companionMap!=game.map.tiles||companionPosition.dst2(companionGoal)>96*96;
      float dt=MathUtils.clamp(seconds-companionLastTime,0,.1f);companionLastTime=seconds;
      if(reset)companionPosition.set(companionGoal);
      float previousX=companionPosition.x,previousY=companionPosition.y;
      float follow=Math.min(1,dt*7.5f);
      float nx=MathUtils.lerp(previousX,companionGoal.x,follow),ny=MathUtils.lerp(previousY,companionGoal.y,follow);
      if(WorldReadability.open(game.map.tiles,nx,ny,lookup)&&WorldReadability.open(game.map.tiles,nx-4,ny,lookup)
         &&WorldReadability.open(game.map.tiles,nx+4,ny,lookup)&&WorldReadability.open(game.map.tiles,nx,ny-3,lookup)
         &&WorldReadability.open(game.map.tiles,nx,ny+3,lookup)&&Vector2.dst2(nx,ny,x,y)>14*14)
         companionPosition.set(nx,ny);
      else companionPosition.set(companionGoal);
      presentedCompanion=pokemon;companionMap=game.map.tiles;
      boolean moving=walking||companionPosition.dst2(previousX,previousY)>.003f;
      boolean working=false;
      for(Action action:game.actionStackCopy) {
         if(!game.actionStack.contains(action))continue;
         if(action instanceof CutTreeAnim&&((CutTreeAnim)action).timer<24
            ||action instanceof HeadbuttTreeAnim&&((HeadbuttTreeAnim)action).index<48
            ||action instanceof PlantTree) {working=true;break;}
      }
      boolean charged="POWER".equals(move)||"FLASH".equals(move);
      if(charged&&!moving)working=seconds%3.2f<.75f;
      if(game.player.isCrafting)working=true;
      String direction=working?game.player.dirFacing:moving
         ?WorldReadability.facing(companionPosition.x-previousX,companionPosition.y-previousY,game.player.dirFacing)
         :game.player.dirFacing;
      float bob=reducedMotion?0:.28f+.22f*MathUtils.sin(seconds*3.8f);
      fieldPokemon(pokemon,direction,companionPosition.x,companionPosition.y,.2f+bob,working?"Attack":moving?"Walk":"Idle");
      if(charged) {
         float pulse=.40f+(reducedMotion?0:.14f*MathUtils.sin(seconds*4));
         ring(companionPosition.x,companionPosition.y,7.5f,.65f,.13f,Color.toFloatBits(1,.83f,.38f,pulse));
      }
      if(working)activeCompanionFrames++;
   }

   private void shinySparkles(Pokemon pokemon,float x,float y,float height) {
      if(pokemon==null||!pokemon.isShiny||pokemon.isEgg||pokemon.isGhost)return;
      // Sparse four-point gleams remain attached to the actor in world space.
      // Reduced motion keeps one quiet sparkle instead of flashing/rotating.
      float clock=reducedMotion?.45f:seconds%2.8f;
      if(clock>1.1f)return;
      float pulse=reducedMotion?.65f:MathUtils.sin(clock/1.1f*MathUtils.PI);
      for(int i=0;i<(reducedMotion?1:4);i++) {
         float angle=i*MathUtils.PI2/4+(reducedMotion?0:clock*.45f);
         float px=x+MathUtils.cos(angle)*10,py=y-1;
         float lift=4+height*.3f+(MathUtils.sin(angle)+1)*height*.22f;
         float radius=(1.1f+.55f*(i%2))*pulse;
         float tint=i%2==0?color(1,.87f,.38f):WHITE;
         actorPlaneZ=-py;
         uprightRaw(white,px-radius,py,lift,2*radius,.65f,tint,4);
         uprightRaw(white,px-.32f,py,lift-radius,.65f,2*radius,tint,4);
      }
      shinyFrames++;
   }

   /** Reads the existing validator's result; collision, costs and placement stay in DrawBuildTile. */
   private void fieldTarget(Game game) {
      Player player=game.player;
      boolean build="BUILD".equals(player.currFieldMove) || "DIG".equals(player.currFieldMove);
      if(!build && player.currPlanting==null) return;
      Vector2 pos=player.facingPos();
      surfaceLift=elevation.height(pos.x+8,pos.y+8);
      Color tint=game.mapBatch instanceof WorldBatch?((WorldBatch)game.mapBatch).getTargetTint():Color.WHITE;
      float packed=tint.toFloatBits();
      floor(white,pos.x+.5f,pos.y+.5f,15,15,.16f,Color.toFloatBits(tint.r,tint.g,tint.b,.18f),translucent);
      floor(white,pos.x+.5f,pos.y+.5f,15,.7f,.2f,packed,translucent);
      floor(white,pos.x+.5f,pos.y+14.8f,15,.7f,.2f,packed,translucent);
      floor(white,pos.x+.5f,pos.y+.5f,.7f,15,.2f,packed,translucent);
      floor(white,pos.x+14.8f,pos.y+.5f,.7f,15,.2f,packed,translucent);
      TextureRegion preview=null;
      if(build && player.currBuildTile!=null) {
         preview=assets.object(player.currBuildTile,seconds);
         if(preview==null) {
            TextureRegion ground=assets.terrain(player.currBuildTile,seconds);
            floor(ground,pos.x+2,pos.y+2,12,12,.18f,packed,translucent);
         }
      } else preview=assets.named("seedling");
      if(preview!=null) {
         float size=Math.min(1f,22f/Math.max(preview.getRegionWidth(),preview.getRegionHeight()));
         upright(preview,pos.x+8-preview.getRegionWidth()*size/2,pos.y+8,.25f,
            preview.getRegionWidth()*size,preview.getRegionHeight()*size,packed);
      }
   }

   private void ring(float x,float y,float radius,float thickness,float elevation,float tint) {
      Buffer mesh=translucent.buffer(whiteTexture);
      for(int i=0;i<24;i++) {
         float a=i*MathUtils.PI2/24,b=(i+1)*MathUtils.PI2/24,inner=radius-thickness;
         quad(mesh,x+MathUtils.cos(a)*inner,elevation,-y+MathUtils.sin(a)*inner,
            x+MathUtils.cos(a)*radius,elevation,-y+MathUtils.sin(a)*radius,
            x+MathUtils.cos(b)*radius,elevation,-y+MathUtils.sin(b)*radius,
            x+MathUtils.cos(b)*inner,elevation,-y+MathUtils.sin(b)*inner,white,tint);
      }
   }

   private void line(float x1,float h1,float y1,float x2,float h2,float y2,float width,float tint) {
      quad(geometry.buffer(whiteTexture),x1-width,h1,-y1,x1+width,h1,-y1,
         x2+width,h2,-y2,x2-width,h2,-y2,white,tint);
   }

   private void anchored(TextureRegion region, float groundX, float groundY, float lift, float anchorX, float anchorY, float scale) {
      // Keep pixel proportions camera-facing, but test depth against a vertical
      // plane through the feet. Slopes and cliffs behind cannot slice the body;
      // terrain and props actually in front can still occlude it.
      actorPlaneZ=-groundY;
      uprightRaw(region, groundX - anchorX * scale, groundY - anchorY * scale * MathUtils.sinDeg(50f),
         lift - anchorY * scale * MathUtils.cosDeg(50f), region.getRegionWidth() * scale, region.getRegionHeight() * scale, WHITE,4f);
   }

   /** Trainer crops are proportions of the original 32px frame, not source pixels.
    * Keep field poses and the feet depth plane coherent with HD/custom avatars. */
   private void trainerImage(TextureRegion full,int rows,float groundX,float groundY,float lift,float anchorY,float scale) {
      float fullHeight=32f*scale,fullWidth=fullHeight*full.getRegionWidth()/full.getRegionHeight();
      float ax=.5f,ay=anchorY/32f,ox=0,oy=0,raise=0;
      BwAssets.SpriteLayout layout=assets.layout(full);
      if(layout!=null) {
         fullWidth=layout.width(full,fullWidth);fullHeight=layout.height(full,fullHeight);
         ax=layout.anchorX(ax);ay=layout.anchorY(ay);
         ox=layout.offsetX;oy=layout.offsetY;raise=layout.elevation;
      }
      TextureRegion visible=full;
      if(rows<32) {
         spritePart.setRegion(full);
         spritePart.setRegionHeight(Math.max(1,Math.round(full.getRegionHeight()*rows/32f)));
         visible=spritePart;
      }
      float fraction=(float)visible.getRegionHeight()/full.getRegionHeight();
      float height=fullHeight*fraction,feet=Math.max(0,ay-(1-fraction))*fullHeight;
      actorPlaneZ=-groundY-oy;
      uprightRaw(visible,groundX+ox-fullWidth*ax,groundY+oy-feet*MathUtils.sinDeg(50f),
         lift+raise-feet*MathUtils.cosDeg(50f),fullWidth,height,WHITE,4f);
   }

   private boolean moving(Object actor, float x, float y) {
      Motion sample = motion.get(actor);
      if (sample == null) { motion.put(actor,new Motion(x,y)); return false; }
      if (Math.abs(sample.x-x)+Math.abs(sample.y-y)>.01f) sample.until=seconds+.14f;
      sample.x=x;sample.y=y;
      return seconds<sample.until;
   }

   private static final class Motion {
      float x,y,until;
      Motion(float x,float y) { this.x=x;this.y=y; }
   }

   private void tree(Tile tile, float x, float y, String name) {
      if (name.contains("nosprite")) return; // Additional collision cell of a multi-tile tree.
      if(WorldReadability.largeTree(tile)){x+=8;y+=8;}
      String key = treeKey(tile, name);
      TextureRegion sprite = assets.named(key);
      HeadbuttTreeAnim shake = shakingTrees.get(tile);
      if (shake != null) {
         int tick = shake.index - 1;
         if (tick >= 21 && tick < 48) x += (tick / 3 % 4 == 3 ? 1.6f : tick / 3 % 4 == 1 ? -1.6f : 0);
         fieldEffectFrames++;
      }
      float height = assets.worldHeight(sprite,key.contains("dead") || key.contains("charred")?36:name.contains("large") ? 62 : key.equals("tree_pine") || key.equals("tree_snow") ? 46 : 42);
      float width = assets.worldWidth(sprite,height * sprite.getRegionWidth() / sprite.getRegionHeight());
      assetShadow(sprite,x,y,Math.min(width*.35f,10f),4);
      actorPlaneZ=-y;
      boolean leafy=WorldNatureMotion.treeFlexible(key);
      // Gameplay shaking owns the complete tree during Headbutt. Growth and
      // cutting have separate presentation paths and never inherit wind state.
      crownBend=leafy&&shake==null?MathUtils.clamp(height*.038f,1f,2f):0;
      try {
         if(leafy)canopyShadow(tile,sprite,x,y,width,height);
         upright(sprite,x-width/2,y,.2f,width,height,WHITE,5f);
      } finally {crownBend=0;}
      int tier=WorldReadability.cutTier(tile);
      // Mature CUT trees have two deep bark bands at the trunk; ordinary trees
      // have one pale band. The distinction survives regional crown colours.
      if(tier>0)for(int band=0;band<tier;band++)
         upright(white,x-(tier==2?3.2f:2.1f),y-.05f,2.5f+band*2.1f,tier==2?6.4f:4.2f,1.15f,
            tier==2?color(.48f,.27f,.10f):color(.76f,.61f,.35f));
   }

   private String treeKey(Tile tile, String name) { return WorldMaterials.treeKey(tile); }

   /** Read the original action clock; never execute an action or advance RNG here. */
   private void fieldEffect(Game game, Action action) {
      if (!game.actionStack.contains(action)) return;
      if (action instanceof PlantTree) {
         PlantTree plant = (PlantTree)action;
         TextureRegion art = assets.named("seedling");
         float progress = MathUtils.clamp(plant.timer / 24f, 0, 1);
         float height = 4 + progress * 10, width = height * art.getRegionWidth() / art.getRegionHeight();
         surfaceLift = elevation.height(plant.pos.x + 8, plant.pos.y + 8);
         actorPlaneZ=-plant.pos.y-7;
         uprightRaw(art, plant.pos.x + 8 - width/2, plant.pos.y + 7, .2f, width, height, WHITE, 4);
         fieldEffectFrames++;
         return;
      }
      CutTreeAnim cut = (CutTreeAnim)action;
      int tick = cut.timer - 1;
      if (tick < 19 || tick >= 49) return;
      // Match the original split-and-blink timing after the collision tile is removed.
      if (tick == 37 || tick == 38 || tick == 41 || tick == 42 || tick == 45 || tick == 46) return;
      Tile tile = cut.tile;
      String name = tile.name + " " + tile.nameUpper;
      TextureRegion art = isTree(tile, tile.name, tile.nameUpper) ? assets.named(treeKey(tile, name)) : assets.object(tile, seconds);
      if (art == null) art = assets.named("bush");
      float height = assets.worldHeight(art, WorldReadability.largeTree(tile)?62:Math.min(42, art.getRegionHeight()));
      float width = assets.worldWidth(art, height * art.getRegionWidth() / art.getRegionHeight());
      surfaceLift = elevation.height(tile.position.x + 8, tile.position.y + 8);
      int half = Math.max(1, art.getRegionWidth()/2);
      cutLeft.setRegion(art,0,0,half,art.getRegionHeight());
      cutRight.setRegion(art,half,0,art.getRegionWidth()-half,art.getRegionHeight());
      float spread = tick < 20 ? 0 : 2 + (tick >= 39 ? 2 : 0) + (tick >= 43 ? 2 : 0) + (tick >= 47 ? 2 : 0);
      float leftWidth = width * half / art.getRegionWidth();
      float x = tile.position.x + (WorldReadability.largeTree(tile)?16:8) - width/2;
      float y = tile.position.y + (WorldReadability.largeTree(tile)?16:8);
      actorPlaneZ=-y;
      uprightRaw(cutLeft,x-spread,y,.2f,leftWidth,height,WHITE,5);
      uprightRaw(cutRight,x+leftWidth+spread,y,.2f,width-leftWidth,height,WHITE,5);
      fieldEffectFrames++;
   }

   private void assetShadow(TextureRegion region,float x,float y,float width,float depth) {
      BwAssets.SpriteLayout layout=assets.layout(region);
      shadow(x+(layout==null?0:layout.offsetX),y+(layout==null?0:layout.offsetY),width,depth);
   }

   /** Soft projected crown shade, clipped to real dry ground at the tree's level.
    * Each eight-unit patch follows terrain elevation: no floating screen overlay
    * and no dark band climbing a cliff, building, actor or water surface. */
   private void canopyShadow(Tile tree,TextureRegion region,float x,float y,float width,float height) {
      if(natureTiles==null)return;
      BwAssets.SpriteLayout layout=assets.layout(region);
      x+=layout==null?0:layout.offsetX;y+=layout==null?0:layout.offsetY;
      float movement=WorldNatureMotion.crownWind(x,y,natureSeconds,1,crownBend,reducedMotion);
      float cx=x+height*.27f+movement*.65f,cy=y-height*.20f;
      float rx=MathUtils.clamp(width*.43f,8,22),ry=MathUtils.clamp(height*.21f,5,14);
      float strength=.07f+.30f*daylight,level=elevation.tileHeight(tree),previous=surfaceLift;
      Buffer mesh=shadows.buffer(whiteTexture);surfaceLift=0;
      int left=MathUtils.floor((cx-rx)/8),right=MathUtils.ceil((cx+rx)/8);
      int bottom=MathUtils.floor((cy-ry)/8),top=MathUtils.ceil((cy+ry)/8);
      for(int row=bottom;row<top;row++)for(int col=left;col<right;col++) {
         float px=col*8,py=row*8;
         Tile ground=natureTiles.get(lookup.set(MathUtils.floor((px+4)/16)*16,MathUtils.floor((py+4)/16)*16));
         if(ground==null||ground.isWater||ground.isLava||ground.isWaterfall||ground.isLedge
            ||ModernWorldGenerator.isRamp(ground)||Math.abs(elevation.tileHeight(ground)-level)>.35f)continue;
         float a=crownShade(px,py,cx,cy,rx,ry,strength),b=crownShade(px+8,py,cx,cy,rx,ry,strength);
         float c=crownShade(px+8,py+8,cx,cy,rx,ry,strength),d=crownShade(px,py+8,cx,cy,rx,ry,strength);
         if(a+b+c+d<.006f)continue;
         a=Color.toFloatBits(1,1,1,a);b=Color.toFloatBits(1,1,1,b);
         c=Color.toFloatBits(1,1,1,c);d=Color.toFloatBits(1,1,1,d);
         float ha=elevation.height(px+.01f,py+.01f)+.10f,hb=elevation.height(px+7.99f,py+.01f)+.10f;
         float hc=elevation.height(px+7.99f,py+7.99f)+.10f,hd=elevation.height(px+.01f,py+7.99f)+.10f;
         vertex(mesh,px,ha,-py,a,.5f,.5f,2);vertex(mesh,px+8,hb,-py,b,.5f,.5f,2);
         vertex(mesh,px+8,hc,-py-8,c,.5f,.5f,2);vertex(mesh,px+8,hc,-py-8,c,.5f,.5f,2);
         vertex(mesh,px,hd,-py-8,d,.5f,.5f,2);vertex(mesh,px,ha,-py,a,.5f,.5f,2);
      }
      surfaceLift=previous;
   }

   private static float crownShade(float x,float y,float cx,float cy,float rx,float ry,float strength) {
      float dx=(x-cx)/rx,dy=(y-cy)/ry,edge=MathUtils.clamp(1-dx*dx-dy*dy,0,1);
      return strength*edge*edge*(3-2*edge);
   }

   private void shadow(float x, float y, float width, float depth) {
      Buffer mesh = shadows.buffer(whiteTexture);
      float tint = Color.toFloatBits(0.13f, 0.22f, 0.19f, 0.26f);
      for (int i = 0; i < 16; i++) {
         float a = i * MathUtils.PI2 / 16;
         float b = (i + 1) * MathUtils.PI2 / 16;
         triangle(mesh, x, 0.12f, -y,
            x + MathUtils.cos(a) * width, 0.12f, -y + MathUtils.sin(a) * depth,
            x + MathUtils.cos(b) * width, 0.12f, -y + MathUtils.sin(b) * depth, tint);
      }
   }

   private void upright(TextureRegion region, float x, float y, float base, float width, float height, float tint) {
      // Every billboard, including furniture and vegetation, sorts through the
      // same vertical ground plane as the actors. Mixing tilted prop depth with
      // upright actor depth cuts heads/bodies through foreground crowns.
      actorPlaneZ=-y;
      upright(region, x, y, base, width, height, tint, 4f);
   }

   private void upright(TextureRegion region, float x, float y, float base, float width, float height, float tint, float style) {
      BwAssets.SpriteLayout layout=assets==null?null:assets.layout(region);
      if(layout!=null && layout.customized) {
         float groundX=x+width*.5f,groundY=y;
         width=layout.width(region,width);height=layout.height(region,height);
         float anchorX=layout.anchorX(.5f),anchorY=layout.anchorY(0f);
         if(style>3.5f)actorPlaneZ=-groundY-layout.offsetY;
         uprightRaw(region,groundX+layout.offsetX-width*anchorX,
            groundY+layout.offsetY-height*anchorY*MathUtils.sinDeg(50f),
            base+layout.elevation-height*anchorY*MathUtils.cosDeg(50f),width,height,tint,style);
         return;
      }
      uprightRaw(region,x,y,base,width,height,tint,style);
   }

   private void uprightRaw(TextureRegion region, float x, float y, float base, float width, float height, float tint, float style) {
      // Camera-facing sprites keep their pixel proportions in the tilted world.
      float rise = height * MathUtils.cosDeg(50f), back = height * MathUtils.sinDeg(50f);
      if((vegetationBend>0||crownBend>0)&&!reducedMotion) {
         Buffer mesh=geometry.buffer(region.getTexture());
         // Grass has rooted tips; trees have an entirely fixed lower trunk.
         // Subdivision deforms only X, preserving sprite UVs and the feet depth
         // plane shared with Pokémon and the trainer behind/in front of foliage.
         int strips=crownBend>0?6:3;
         for(int strip=0;strip<strips;strip++) {
            float f0=crownBend>0?CROWN_ROWS[strip]:strip/(float)strips;
            float f1=crownBend>0?CROWN_ROWS[strip+1]:(strip+1)/(float)strips;
            float dx0=crownBend>0?WorldNatureMotion.crownWind(x+width*.5f,y,natureSeconds,f0,crownBend,false)
               :WorldNatureMotion.wind(x+width*.5f,y,natureSeconds,f0,vegetationBend,false);
            float dx1=crownBend>0?WorldNatureMotion.crownWind(x+width*.5f,y,natureSeconds,f1,crownBend,false)
               :WorldNatureMotion.wind(x+width*.5f,y,natureSeconds,f1,vegetationBend,false);
            float v0=MathUtils.lerp(region.getV2(),region.getV(),f0),v1=MathUtils.lerp(region.getV2(),region.getV(),f1);
            vertex(mesh,x+dx0,base+rise*f0,-y-back*f0,tint,region.getU(),v0,style);
            vertex(mesh,x+width+dx0,base+rise*f0,-y-back*f0,tint,region.getU2(),v0,style);
            vertex(mesh,x+width+dx1,base+rise*f1,-y-back*f1,tint,region.getU2(),v1,style);
            vertex(mesh,x+width+dx1,base+rise*f1,-y-back*f1,tint,region.getU2(),v1,style);
            vertex(mesh,x+dx1,base+rise*f1,-y-back*f1,tint,region.getU(),v1,style);
            vertex(mesh,x+dx0,base+rise*f0,-y-back*f0,tint,region.getU(),v0,style);
         }
         return;
      }
      if(captureBedDepth) {
         // Capture after SpriteLayout has applied dimensions, anchors and offsets.
         bedBottomScreenY=projectedAnchor.set(x,base+surfaceLift,-y).prj(camera.combined).y;
         bedTopScreenY=projectedAnchor.set(x,base+rise+surfaceLift,-y-back).prj(camera.combined).y;
         bedBottomDepth=projectedAnchor.set(x,base+surfaceLift+.12f,actorPlaneZ).prj(camera.combined).z;
         bedTopDepth=projectedAnchor.set(x,base+rise+surfaceLift+.12f,actorPlaneZ).prj(camera.combined).z;
         bedDepthValid=Math.abs(bedTopScreenY-bedBottomScreenY)>.000001f;
      }
      quad(geometry.buffer(region.getTexture()),
         x, base, -y, x + width, base, -y, x + width, base + rise, -y-back, x, base + rise, -y-back, region, tint, style);
   }

   private void floor(TextureRegion region, float x, float y, float width, float depth, float elevation, float tint, WorldMeshBatch groups) {
      floor(region, x, y, width, depth, elevation, tint, groups, 0f);
   }

   private void floor(TextureRegion region, float x, float y, float width, float depth, float elevation, float tint, WorldMeshBatch groups, float style) {
      quad(groups.buffer(region.getTexture()), x, elevation, -y, x + width, elevation, -y,
         x + width, elevation, -y - depth, x, elevation, -y - depth, region, tint, style);
   }

   private static float color(float r, float g, float b) {
      return Color.toFloatBits(r, g, b, 1f);
   }

   private void quad(Buffer mesh,
      float x0, float y0, float z0, float x1, float y1, float z1,
      float x2, float y2, float z2, float x3, float y3, float z3, TextureRegion uv, float tint) {
      quad(mesh, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, uv, tint, 0f);
   }

   private void quad(Buffer mesh,
      float x0, float y0, float z0, float x1, float y1, float z1,
      float x2, float y2, float z2, float x3, float y3, float z3, TextureRegion uv, float tint, float style) {
      vertex(mesh, x0, y0, z0, tint, uv.getU(), uv.getV2(), style);
      vertex(mesh, x1, y1, z1, tint, uv.getU2(), uv.getV2(), style);
      vertex(mesh, x2, y2, z2, tint, uv.getU2(), uv.getV(), style);
      vertex(mesh, x2, y2, z2, tint, uv.getU2(), uv.getV(), style);
      vertex(mesh, x3, y3, z3, tint, uv.getU(), uv.getV(), style);
      vertex(mesh, x0, y0, z0, tint, uv.getU(), uv.getV2(), style);
   }

   private void triangle(Buffer mesh, float x0, float y0, float z0,
      float x1, float y1, float z1, float x2, float y2, float z2, float tint) {
      vertex(mesh, x0, y0, z0, tint, 0.5f, 0.5f, 0f);
      vertex(mesh, x1, y1, z1, tint, 0.5f, 0.5f, 0f);
      vertex(mesh, x2, y2, z2, tint, 0.5f, 0.5f, 0f);
   }

   private void vertex(Buffer mesh, float x, float y, float z, float color, float u, float v, float style) {
      // Scene-dependent transforms stay here; storage/upload belong to the batch.
      float depth=style>3.5f?projectedAnchor.set(x,y+surfaceLift+.12f,actorPlaneZ).prj(camera.combined).z:0f;
      if(style<-.5f&&style> -2.5f)depth=shore.distance(x,-z);
      if(style>3.5f&&matchBedDepth) {
         float screenY=projectedAnchor.set(x,y+surfaceLift,z).prj(camera.combined).y;
         depth=MathUtils.lerp(bedBottomDepth,bedTopDepth,(screenY-bedBottomScreenY)/(bedTopScreenY-bedBottomScreenY))-.000001f;
      }
      mesh.vertex(x, y+surfaceLift, z, color, u, v, style,depth);
   }

   private void collectLights(Game game) {
      java.util.Arrays.fill(lights,0f);
      int index=0;
      Pokemon hm=game.player.hmPokemon;
      if(hm!=null && hm.hms.contains("FLASH")) {
         Vector2 pos=game.player.currFieldMove.isEmpty()?hm.position:game.player.position;
         index=light(index,pos.x+8,pos.y+8,86,1f);
      }
      for(Pokemon pokemon:game.map.onscreenPokemon) {
         if(index>=8)break;
         if(pokemon!=hm && pokemon.mapTiles==game.map.tiles && pokemon.hms.contains("FLASH"))
            index=light(index,pokemon.position.x+8,pokemon.position.y+8,75,.95f);
      }
      for(Tile tile:game.map.onscreenTiles) {
         if(index>=8)break;
         boolean fire="campfire1".equals(tile.nameUpper) || "volcarona".equals(tile.nameUpper);
         if(fire || tile.isTorch)index=light(index,tile.position.x+8,tile.position.y+8,fire?65:42,.88f+.05f*MathUtils.sin(natureSeconds*7));
      }
      // Cluster lava illumination on a stable world lattice; do not spend every slot on one pool.
      for(Tile tile:game.map.onscreenTiles) {
         if(index>=8)break;
         if(tile.isLava && Math.floorMod((int)tile.position.x/16,3)==0 && Math.floorMod((int)tile.position.y/16,3)==0)
            index=light(index,tile.position.x+8,tile.position.y+8,35,
               BiomeProfiles.visualForTile(tile).number("ambient","glow",.42f)+.05f*MathUtils.sin(natureSeconds*2));
      }
   }

   private void atmosphere(Game game) {
      ambientTint.set(0,0,0,1);fogColor.set(0,0,0,1);fogDensity=desaturation=0;float total=0;
      for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++) {
         float x=MathUtils.floor(game.player.position.x/16)*16+dx*16,y=MathUtils.floor(game.player.position.y/16)*16+dy*16;
         Tile tile=game.map.tiles.get(lookup.set(x,y));if(tile==null)continue;
         BiomeProfiles.Profile profile=BiomeProfiles.visualForTile(tile);float weight=1f/(1+dx*dx+dy*dy);
         Color tint=Color.valueOf(profile.string("ambient","tint","ffffff"));
         Color fog=Color.valueOf(profile.string("ambient","fogColor","a5b5b0"));
         ambientTint.r+=tint.r*weight;ambientTint.g+=tint.g*weight;ambientTint.b+=tint.b*weight;
         fogColor.r+=fog.r*weight;fogColor.g+=fog.g*weight;fogColor.b+=fog.b*weight;
         fogDensity+=profile.number("ambient","fogDensity",0)*weight;
         desaturation+=profile.number("ambient","desaturation",0)*weight;total+=weight;
      }
      if(total==0){ambientTint.set(Color.WHITE);return;}
      ambientTint.mul(1/total);fogColor.mul(1/total);fogDensity/=total;desaturation/=total;
   }

   private void shaderAtmosphere() {
      shader.setUniformf("u_fogColor",fogColor.r,fogColor.g,fogColor.b);
      shader.setUniformf("u_cameraPosition",camera.position);shader.setUniformf("u_daylight",daylight);
      shader.setUniformf("u_fogDensity",fogDensity);shader.setUniformf("u_desaturation",desaturation);shader.setUniformf("u_time",natureSeconds);
   }

   private int light(int index,float x,float y,float radius,float strength) {
      int at=index*4;lights[at]=x;lights[at+1]=-y;lights[at+2]=radius;lights[at+3]=strength;
      return index+1;
   }

   private void draw(Game game) {
      collectLights(game);
      atmosphere(game);
      Color tint = game.mapBatch.getColor();
      float r = tint.r*ambientTint.r;
      float g = tint.g*ambientTint.g;
      float b = tint.b*ambientTint.b;
      // Fog retains the scene's day/night illumination instead of whitening the night.
      fogColor.mul(r,g,b,1);
      originalClearColor.clear();
      Gdx.gl.glGetFloatv(GL20.GL_COLOR_CLEAR_VALUE, originalClearColor);
      boolean shaderBegun = false;
      try {
         Gdx.gl.glClearColor(0.61f * r, 0.77f * g, 0.78f * b, 1f);
         Gdx.gl.glDepthMask(true);
         Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
         Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
         Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
         Gdx.gl.glDisable(GL20.GL_CULL_FACE);
         Gdx.gl.glEnable(GL20.GL_BLEND);
         Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
         shader.begin();
         shaderBegun = true;
         shader.setUniformMatrix("u_projTrans", camera.combined);
         shader.setUniformi("u_texture", 0);
         shader.setUniformf("u_tint", r, g, b);
         shader.setUniform4fv("u_lights", lights, 0, lights.length);
         shaderAtmosphere();
         geometry.render(shader);
         shader.end();
         shaderBegun = false;
         // World-space accents test terrain/actor depth and never cover UI/emotes.
         ambientOverlays.render(camera,r,g,b);
         // Actor rendering remains in the same sprite geometry pass.
         // ModelBatch owns a different shader/render context. Restore the state
         // explicitly before continuing our water, shadow and emote passes.
         Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
         Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
         Gdx.gl.glDisable(GL20.GL_CULL_FACE);
         Gdx.gl.glEnable(GL20.GL_BLEND);
         Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
         Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
         shader.begin();
         shaderBegun = true;
         shader.setUniformMatrix("u_projTrans", camera.combined);
         shader.setUniformi("u_texture", 0);
         shader.setUniformf("u_tint", r, g, b);
         shader.setUniform4fv("u_lights", lights, 0, lights.length);
         shaderAtmosphere();
         // Water overlays and shadows blend only after the opaque ground. They
         // never write depth; texture-group iteration cannot erase the ground.
         Gdx.gl.glDepthMask(false);
         translucent.render(shader);
         shadows.render(shader);
         // Alarm/hearts/status balloons are overlays in the original game; keep
         // them readable above foreground crowns without changing their lifetime.
         Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
         emotes.render(shader);
      } finally {
         if (shaderBegun) shader.end();
         // Preserve the caller's actual clear color, including special transitions.
         Gdx.gl.glClearColor(originalClearColor.get(0), originalClearColor.get(1),
            originalClearColor.get(2), originalClearColor.get(3));
      }
   }

   public void dispose() {
      geometry.dispose();
      translucent.dispose();
      shadows.dispose();
      emotes.dispose();
      ambientOverlays.dispose();
      emoteOwners.clear();
      actorModels.dispose();
      GhostVisualAssets.disposeShared();
      if (shader != null) shader.dispose();
      if (whiteTexture != null) whiteTexture.dispose();
      shader = null;
      assets = null;
      motion.clear();
      whiteTexture = null;
      initialized = false;
      // Textures borrowed from the game caches are deliberately never disposed here.
   }

}
