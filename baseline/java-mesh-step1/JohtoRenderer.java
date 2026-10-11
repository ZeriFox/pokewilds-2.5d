package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
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
import com.badlogic.gdx.utils.FloatArray;
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
   private static final int FLOATS_PER_VERTEX = 8;
   private static final int MAX_VERTICES = 65532;
   private final PerspectiveCamera camera = new PerspectiveCamera(35f, 160f, 144f);
   private final Vector2 lookup = new Vector2();
   private final Map<Texture, Geometry> geometry = new IdentityHashMap<>();
   private final Map<Texture, Geometry> translucent = new IdentityHashMap<>();
   private final Map<Texture, Geometry> shadows = new IdentityHashMap<>();
   private final Map<Texture, Geometry> emotes = new IdentityHashMap<>();
   private final Map<Class<?>, Field> emoteOwners = new IdentityHashMap<>();
   private final ActorModelRenderer actorModels = new ActorModelRenderer();
   private final Map<Object, Motion> motion = new WeakHashMap<>();
   private BwAssets assets;
   private float seconds;
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
         clearGeometry();
         elevation.update(game.map.tiles);
         configureCamera(game);
         seconds += Math.min(0.1f, Math.max(0f, Gdx.graphics.getDeltaTime()));
         collectWorld(game);
         // Prepare the complete scene before drawing it in one world pass.
         upload(geometry);
         upload(translucent);
         upload(shadows);
         upload(emotes);
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
         + "uniform mat4 u_projTrans;\nvarying vec4 v_color;\nvarying vec2 v_texCoords;\nvarying float v_style;\nvarying vec2 v_world;\n"
         + "void main(){v_color=a_color;v_color.a*=255.0/254.0;v_texCoords=a_texCoord0;v_style=a_style;"
         + "v_world=a_position.xz;gl_Position=u_projTrans*vec4(a_position,1.0);"
         + "if(a_style>3.5)gl_Position.z=a_anchorDepth*gl_Position.w;}\n";
      String fragment = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
         + "uniform sampler2D u_texture;\nuniform vec3 u_tint;\nuniform vec4 u_lights[8];\nvarying vec2 v_world;\n"
         + "uniform vec3 u_fogColor;uniform float u_fogDensity;uniform float u_desaturation;uniform float u_time;\n"
         + "varying vec4 v_color;\nvarying vec2 v_texCoords;\nvarying float v_style;\n"
         + "void main(){vec4 c=texture2D(u_texture,v_texCoords);"
         + "c*=v_color;"
         + "float glow=0.0;for(int i=0;i<8;i++){vec4 l=u_lights[i];"
         + "float f=clamp(1.0-distance(v_world,l.xy)/max(l.z,1.0),0.0,1.0);glow=max(glow,f*f*l.w);}"
         + "vec3 light=max(u_tint,mix(u_tint,vec3(1.0,.91,.77),glow));"
         + "if(c.a<0.1)discard;c.rgb*=v_style>2.5&&v_style<3.5?mix(light,vec3(1.0),0.65):light;"
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
      quad(batch(emotes, sprite.getTexture()), x, 20f, -y - 7f,
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
      boolean tree = isTree(tile, lower, upper);
      BiomeProfiles.Profile profile=BiomeProfiles.visualForTile(tile);
      TextureRegion ground = assets.terrain(tile, seconds);
      if(assets.namedAnimated(BwAssets.terrainName(tile),seconds)==null)
         assets.reportMissing(BwAssets.terrainName(tile),tile,game.map.timeOfDay);
      if (ground == null) ground = assets.cell("grass_light", x, y);
      if(tile.isLava) {
         for(int[] direction:new int[][]{{0,-16},{0,16},{-16,0},{16,0}}) {
            Tile next=game.map.tiles.get(lookup.set(x+direction[0],y+direction[1]));
            if(next!=null&&!next.isLava){ground=assets.cell(profile.string("fluid","cooledLava","lava_cooled"),x,y,seconds);break;}
         }
      }
      if(!tile.isWaterfall&&!names.contains("lavafall")) terrainSurface(tile,ground);
      if(!tile.isWater && !tile.isLava && (tile.isTidal || lower.contains("puddle"))) {
         // High tide is a walkable film over sand, not an opaque deep-water tile.
         // Read the live tide flag: CycleDayNight changes names before init().
         TextureRegion shallow=assets.cell(profile.string("fluid","shallow","water_shallow"),x,y,seconds);
         float inset=tile.isTidal?0:3;
         floor(shallow,x+inset,y+inset,16-inset*2,16-inset*2,.045f,
            Color.toFloatBits(.92f,1,1,tile.isTidal?.34f:.45f),translucent);
      }
      // Coast geometry follows actual wet/dry adjacency; never adds collision.
      if (tile.isWater && !tile.isLava) coast(game, tile);
      if(tile.isWaterfall || names.contains("lavafall")) { waterfall(tile,profile);return; }
      terrainEdges(game,tile,profile);
      if (ModernWorldGenerator.isRamp(tile) && elevation.isSloped(tile)) ramp(tile,profile);
      if (ModernWorldGenerator.isRamp(tile)) return;
      // The growing action owns its one animated sprout, not a second static prop.
      if (growingPlants.contains(tile.position)) return;
      if (tile.isLedge && (tile.ledgeDir != null || upper.startsWith("ledges3"))) {
         return;
      }
      if (tree) { tree(tile, x + 8f, y + 8f, names); return; }
      if (staticPokemon(tile, x+8, y+7)) return;
      if (names.contains("pokeball") || names.contains("ultraball")) { ball(x+8,y+7,names.contains("ultraball")); return; }
      if (building(game, tile, x, y, names)) return;
      TextureRegion object = assets.object(tile, seconds);
      if(object==null && BwAssets.objectName(tile)!=null)
         assets.reportMissing(BwAssets.objectName(tile),tile,game.map.timeOfDay);
      if (object != null) {
         String identity=BwAssets.objectName(tile);
         if("warp".equals(identity)||"pressure_plate".equals(identity)||"cracked".equals(identity)||"hole".equals(identity)||"steps".equals(identity)) {
            floor(object,x,y,16,16,.08f,WHITE,geometry);return;
         }
         boolean seedling="seedling".equals(identity)||"plant_growing".equals(identity);
         boolean plant = names.contains("grass") || names.contains("flower") || seedling;
         float maxWidth = seedling ? Math.min(9f,object.getRegionWidth()) : names.contains("rock") ? 17f : plant ? 16f : 18f;
         float scale = VisualGeometry.fitScale(object.getRegionWidth(), object.getRegionHeight(), maxWidth, 25f);
         float width = assets.worldWidth(object, object.getRegionWidth() * scale);
         float height = assets.worldHeight(object, object.getRegionHeight() * scale);
         if (!plant) assetShadow(object, x + 8, y + 7, width * .3f, 2.4f);
         upright(object, x + 8 - width/2, y + 7, .15f, width, height, WHITE);
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
      float dark=color(.09f,.13f,.18f);
      disc(x,y,.25f,4f,dark,dark);
      disc(x,y-.02f,.25f,3.2f,ultra?color(.94f,.74f,.18f):color(.88f,.21f,.26f),color(.91f,.94f,.95f));
      upright(white,x-3.7f,y-3.05f,.25f+2.1f,7.4f,1f,dark);
      disc(x,y-.04f,.25f+1.7f,1.5f,dark,dark);
      disc(x,y-.06f,.25f+2.1f,1.1f,WHITE,WHITE);
   }

   private void disc(float x,float y,float base,float radius,float top,float bottom) {
      Geometry mesh=batch(geometry,whiteTexture);
      float rise=MathUtils.cosDeg(50),back=MathUtils.sinDeg(50);
      for(int i=0;i<20;i++) {
         float a=i*MathUtils.PI2/20,b=(i+1)*MathUtils.PI2/20;
         float h1=radius+MathUtils.sin(a)*radius,h2=radius+MathUtils.sin(b)*radius;
         triangle(mesh,x,base+radius*rise,-y-radius*back,
            x+MathUtils.cos(a)*radius,base+h1*rise,-y-h1*back,
            x+MathUtils.cos(b)*radius,base+h2*rise,-y-h2*back,i<10?top:bottom);
      }
   }

   private void coast(Game game, Tile tile) {
      int[][] dirs = {{0,-16},{0,16},{-16,0},{16,0}};
      float x = tile.position.x, y = tile.position.y;
      for (int i=0;i<4;i++) {
         Tile next = game.map.tiles.get(lookup.set(x+dirs[i][0],y+dirs[i][1]));
         if (next == null || next.isWater || next.isWaterfall || next.isLava) continue;
         float sx=x,sy=y,w=16,d=2;
         if(i==1) sy+=14;
         else if(i==2) { w=2;d=16; }
         else if(i==3) { sx+=14;w=2;d=16; }
         BiomeProfiles.Profile land=BiomeProfiles.visualForTile(next);
         float width=land.number("transition","shoreWidth",2);
         if(i==0){d=width;}else if(i==1){sy=y+16-width;d=width;}
         else if(i==2){w=width;}else{sx=x+16-width;w=width;}
         float depth=BiomeProfiles.visualForTile(tile).number("height","shoreDepth",2);
         floor(assets.named("water_ripple"),sx,sy,w,d,-depth+.035f,Color.toFloatBits(.72f,.95f,.93f,.80f),translucent);
      }
   }

   private void ramp(Tile tile,BiomeProfiles.Profile profile) {
      float x=tile.position.x,y=tile.position.y,previousLift=surfaceLift;surfaceLift=0;
      float a=elevation.height(x+.01f,y+.01f),b=elevation.height(x+15.99f,y+.01f);
      float c=elevation.height(x+15.99f,y+15.99f),d=elevation.height(x+.01f,y+15.99f);
      // Subtle treads follow the actual incline; a flat tile never gets a staircase.
      boolean horizontal=Math.abs((b+c)-(a+d))>Math.abs((c+d)-(a+b));
      for(int step=1;step<5;step++) {
         float f=step/5f,px=horizontal?x+16*f:x,py=horizontal?y:y+16*f;
         float ex=px+(horizontal?.45f:16),ey=py+(horizontal?16:.45f);
         float h0=elevation.height(MathUtils.clamp(px,x+.01f,x+15.99f),MathUtils.clamp(py,y+.01f,y+15.99f));
         float h1=elevation.height(MathUtils.clamp(ex,x+.01f,x+15.99f),MathUtils.clamp(py,y+.01f,y+15.99f));
         float h2=elevation.height(MathUtils.clamp(ex,x+.01f,x+15.99f),MathUtils.clamp(ey,y+.01f,y+15.99f));
         float h3=elevation.height(MathUtils.clamp(px,x+.01f,x+15.99f),MathUtils.clamp(ey,y+.01f,y+15.99f));
         quad(batch(translucent,whiteTexture),px,h0+.06f,-py,ex,h1+.06f,-py,ex,h2+.06f,-ey,px,h3+.06f,-ey,
            white,Color.toFloatBits(.72f,.69f,.62f,.34f));
      }
      surfaceLift=previousLift;
   }

   private void waterfall(Tile tile,BiomeProfiles.Profile profile) {
      waterfallFrames++;
      float x=tile.position.x,y=tile.position.y;
      String fluid=profile.string("fluid",tile.isLava?"lava":"water",tile.isLava?"lava_bright":"water");
      TextureRegion art=assets.cell(fluid,x,y,seconds);
      float dx="left".equals(tile.ledgeDir)?-1:"right".equals(tile.ledgeDir)?1:0;
      float dy=dx!=0?0:"up".equals(tile.ledgeDir)?1:-1,tx=-dy,ty=dx;
      float lx=x+8+dx*8,ly=y+8+dy*8,hx=x+8-dx*8,hy=y+8-dy*8;
      float bottom=elevation.height(lx+dx*.01f,ly+dy*.01f)-surfaceLift
         -(tile.isLava?0:profile.number("height","shoreDepth",2));
      float top=elevation.height(hx-dx*.01f,hy-dy*.01f)-surfaceLift;
      // The falling sheet is upright world geometry; it never becomes a ground decal.
      quad(batch(geometry,art.getTexture()),lx-tx*8,bottom,-ly+ty*8,lx+tx*8,bottom,-ly-ty*8,
         hx+tx*8,top,-hy-ty*8,hx-tx*8,top,-hy+ty*8,art,WHITE,tile.isLava?3:0);
      for(int i=0;i<4;i++) {
         float travel=(seconds*.7f+i*.25f)%1f;
         float xx=MathUtils.lerp(hx,lx,travel),yy=MathUtils.lerp(hy,ly,travel),hh=MathUtils.lerp(top,bottom,travel);
         floor(white,xx-(dx==0?6:.3f),yy-(dx==0?.3f:6),dx==0?12:.6f,dx==0?.6f:12,
            hh+.12f,Color.toFloatBits(.84f,.96f,1,.35f),translucent);
      }
      ring(lx+dx,ly+dy,5+.7f*MathUtils.sin(seconds*5),.6f,bottom+.14f,Color.toFloatBits(.76f,.92f,.95f,.4f));
   }

   /** Read real scripted night encounters; never create an actor as an asset fallback. */
   private void ghost(Game game,Action action) {
      if(!game.actionStack.contains(action))return;
      if (diagnosedGhosts.put(action, Boolean.TRUE) == null) {
         Gdx.app.log("GhostDiagnostic", "identity=" + action.getClass().getSimpleName()
            + " source=scripted-encounter isGhost=not-a-Pokemon asset=procedural-spirit fallback=false"
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
      float bob=6+MathUtils.sin(seconds*3)*1.5f;
      shadow(x,y,5,2.4f);
      Geometry mesh=batch(translucent,whiteTexture);
      for(int layer=0;layer<4;layer++) {
         float radius=8-layer*1.4f,tint=Color.toFloatBits(.62f+layer*.08f,.69f+layer*.07f,.90f,alpha*(.12f+layer*.05f));
         for(int i=0;i<24;i++) {
            float a=i*MathUtils.PI2/24,b=(i+1)*MathUtils.PI2/24;
            triangle(mesh,x,bob+6,-y-5,x+MathUtils.cos(a)*radius,bob+6+MathUtils.sin(a)*radius,-y-5,
               x+MathUtils.cos(b)*radius,bob+6+MathUtils.sin(b)*radius,-y-5,tint);
         }
      }
      ring(x,y,6+.4f*MathUtils.sin(seconds*4),.6f,.18f,Color.toFloatBits(.57f,.74f,.95f,alpha*.4f));
   }

   /** A terrain cell is its upper surface, not a flat floor with a wall prop. */
   private void terrainSurface(Tile tile,TextureRegion ground) {
      float x=tile.position.x,y=tile.position.y,previous=surfaceLift;surfaceLift=0;
      TextureRegion art=ground;
      float a=terrainHeight(tile,x+.01f,y+.01f),b=terrainHeight(tile,x+15.99f,y+.01f);
      float c=terrainHeight(tile,x+15.99f,y+15.99f),d=terrainHeight(tile,x+.01f,y+15.99f);
      // Directional light makes the incline legible even when it shares the
      // plateau's material. Flat ground keeps its original palette.
      float gx=((b+c)-(a+d))/32f,gy=((c+d)-(a+b))/32f;
      float sun=(.82f+.30f*gx+.32f*gy)/(float)Math.sqrt(1+gx*gx+gy*gy);
      float light=tile.isLava?1:MathUtils.clamp(.60f+.40f*sun/.82f,.60f,1f);
      quad(batch(geometry,art.getTexture()),x,a,-y,x+16,b,-y,x+16,c,-y-16,x,d,-y-16,art,color(light,light,light),tile.isLava?3f:0f);
      surfaceLift=previous;
   }

   private float terrainHeight(Tile tile,float x,float y) {
      return elevation.height(x,y)-(tile.isWater&&!tile.isWaterfall?BiomeProfiles.forTile(tile).number("height","shoreDepth",2):0);
   }

   /** Stitch the real upper and lower surfaces at every exposed step. Never
    * extrude a fallback fence when both sides have the same elevation. */
   private void terrainEdges(Game game,Tile tile,BiomeProfiles.Profile profile) {
      float x=tile.position.x,y=tile.position.y,previous=surfaceLift;surfaceLift=0;
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
         // A cliff uses its complete vertical artwork; ground sampling must not
         // cut alternating horizontal strips out of its face.
         TextureRegion face=assets.named(profile.cliff("front"));
         float shade=side==0?.90f:side==1?.66f:side==2?.78f:.64f;
         quad(batch(geometry,face.getTexture()),x0,lowA,-y0,x1,lowB,-y1,x1,b,-y1,x0,a,-y0,
            face,color(shade,shade,shade));
         // A fine edge on the plateau and a contact shadow at the foot give
         // complementary cues for going up and down, without a thick wall cap.
         if(Math.max(a-lowA,b-lowB)>3) {
            float ix=-dx*.65f,iy=-dy*.65f;
            float ha=terrainHeight(tile,MathUtils.clamp(x0+ix,x+.01f,x+15.99f),MathUtils.clamp(y0+iy,y+.01f,y+15.99f));
            float hb=terrainHeight(tile,MathUtils.clamp(x1+ix,x+.01f,x+15.99f),MathUtils.clamp(y1+iy,y+.01f,y+15.99f));
            quad(batch(translucent,whiteTexture),x0,a+.065f,-y0,x1,b+.065f,-y1,
               x1+ix,hb+.065f,-y1-iy,x0+ix,ha+.065f,-y0-iy,white,Color.toFloatBits(.88f,.87f,.79f,.22f));
            float ox=dx*1.5f,oy=dy*1.5f;
            float la=terrainHeight(next,MathUtils.clamp(x0+ox,next.position.x+.01f,next.position.x+15.99f),
               MathUtils.clamp(y0+oy,next.position.y+.01f,next.position.y+15.99f));
            float lb=terrainHeight(next,MathUtils.clamp(x1+ox,next.position.x+.01f,next.position.x+15.99f),
               MathUtils.clamp(y1+oy,next.position.y+.01f,next.position.y+15.99f));
            quad(batch(translucent,whiteTexture),x0,lowA+.07f,-y0,x1,lowB+.07f,-y1,
               x1+ox,lb+.07f,-y1-oy,x0+ox,la+.07f,-y0-oy,white,Color.toFloatBits(.05f,.06f,.08f,.30f));
         }
      }
      surfaceLift=previous;
   }

   private boolean building(Game game, Tile tile, float x, float y, String name) {
      String identity=BwAssets.objectName(tile);
      if(identity!=null&&java.util.Arrays.asList("chair","couch","bed","desk","table","shelf","wardrobe","pot").contains(identity)) {
         TextureRegion art=assets.named(identity);
         if(art==null)return false;
         float scale=VisualGeometry.fitScale(art.getRegionWidth(),art.getRegionHeight(),24,29);
         float width=assets.worldWidth(art,art.getRegionWidth()*scale),height=assets.worldHeight(art,art.getRegionHeight()*scale);
         assetShadow(art,x+8,y+7,width*.28f,2.4f);
         upright(art,x+8-width*.5f,y+7,.16f,width,height,WHITE);
         return true;
      }
      if(name.contains("bridge")||name.contains("stairs")) {
         floor(assets.named(name.contains("stairs")?"steps":"wood_floor"),x,y,16,16,.1f,WHITE,geometry);return true;
      }
      if(name.contains("fence")||name.contains("gate")) {
         if(tile.isSolid)upright(assets.named("fence_wood"),x,y+8,.1f,16,12,WHITE);
         else floor(assets.named("wood_floor"),x,y,16,16,.1f,WHITE,geometry);
         return true;
      }
      if(name.contains("roof")) {
         floor(assets.named("roof"),x,y,16,16,22,WHITE,geometry);return true;
      }
      if(name.contains("door")||name.contains("pkmnmansion_ext_locked")) {
         floor(assets.named("steps"),x,y,16,12,.1f,WHITE,geometry);
         upright(assets.named(name.contains("locked")?"door_locked":tile.isSolid?"door":"door_open"),x,y+14,.1f,16,22,WHITE);
         return true;
      }
      String upper=tile.nameUpper==null?"":tile.nameUpper.toLowerCase(java.util.Locale.ROOT);
      String lower=tile.name==null?"":tile.name.toLowerCase(java.util.Locale.ROOT);
      boolean caveWall=lower.startsWith("cave")&&upper.isEmpty()&&!lower.contains("regi");
      boolean structural=tile.isSolid&&("wall".equals(identity)||"window".equals(identity)||name.contains("pkmnmansion_ext")
         ||caveWall&&VisualGeometry.buildingWall(lower,upper,true));
      if(structural) {
         TextureRegion face=assets.named(caveWall?"cliff_dark":name.contains("ruin")?"ruin_wall":"wall");
         TextureRegion cap=assets.named(caveWall?"mountain":name.contains("ruin")?"ruin_floor":"wall_cap");
         if(cap==null)cap=assets.named("wood_floor");
         float height=caveWall?16:24,z=-y;
         // The cap can cover the actor even when the visible front face is far
         // below them on screen. Fade the complete intervening wall segment;
         // retain its geometry and neighbour culling, so corners never gain holes.
         float alpha=interiorWallOccludesPlayer(game,x,y,height)?.12f:1f;
         Map<Texture,Geometry> groups=alpha<1f?translucent:geometry;
         float wallTint=Color.toFloatBits(1,1,1,alpha);
         Geometry mesh=batch(groups,face.getTexture());
         if(!structuralNeighbour(game,x,y-16))quad(mesh,x,0,z,x+16,0,z,x+16,height,z,x,height,z,face,wallTint);
         if(!structuralNeighbour(game,x,y+16))quad(mesh,x+16,0,z-16,x,0,z-16,x,height,z-16,x+16,height,z-16,face,Color.toFloatBits(.82f,.85f,.87f,alpha));
         if(!structuralNeighbour(game,x-16,y))quad(mesh,x,0,z-16,x,0,z,x,height,z,x,height,z-16,face,Color.toFloatBits(.88f,.90f,.91f,alpha));
         if(!structuralNeighbour(game,x+16,y))quad(mesh,x+16,0,z,x+16,0,z-16,x+16,height,z-16,x+16,height,z,face,Color.toFloatBits(.73f,.78f,.81f,alpha));
         floor(cap,x,y,16,16,height,wallTint,groups);
         if(name.contains("window")) {
            TextureRegion window=assets.named("window");
            if(window!=null)quad(batch(groups,window.getTexture()),x+3,7,z+.05f,x+13,7,z+.05f,x+13,19,z+.05f,x+3,19,z+.05f,window,wallTint);
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
      String identity=BwAssets.objectName(tile);
      return "wall".equals(identity)||"window".equals(identity);
   }

   private void prism(float x,float y,float base,float width,float depth,float top,TextureRegion face,float tint) {
      Geometry mesh=batch(geometry,face.getTexture());float z=-y;
      quad(mesh,x,base,z,x+width,base,z,x+width,top,z,x,top,z,face,tint);
      quad(mesh,x+width,base,z,x+width,base,z-depth,x+width,top,z-depth,x+width,top,z,face,tint);
      quad(mesh,x,base,z-depth,x,base,z,x,top,z,x,top,z-depth,face,tint);
      quad(mesh,x+width,base,z-depth,x,base,z-depth,x,top,z-depth,x+width,top,z-depth,face,tint);
      floor(face,x,y,width,depth,top,tint,geometry);
   }

   private static boolean isTree(Tile tile, String lower, String upper) {
      if (upper.contains("planted") || upper.contains("berrytree") || upper.contains("stump")) return false;
      return tile.isTree || lower.matches("tree[1-7]") || lower.startsWith("tree_large")
         || upper.matches("tree[1-7]") || upper.startsWith("tree_large") || upper.equals("tree_savanna1")
         || lower.startsWith("tree_fairy") || upper.startsWith("tree_fairy");
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
         PmdPokemonSprites.Frame idle = sprites.frame(pokemon, "down", "Idle", 0f);
         float scale = Math.min(.85f, 30f / Math.max(1f, idle == null ? Math.max(frame.width, frame.height) : Math.max(idle.width, idle.height)));
         float anchorY = frame.anchorY;
         TextureRegion region = frame.region;
         if (pokemon.inWater && pokemon.drawUpper != null) {
            int hidden = Math.max(0, Math.min(region.getRegionHeight() - 1, Math.round(anchorY + 5)));
            spritePart.setRegion(region); spritePart.setRegionHeight(region.getRegionHeight() - hidden);
            region = spritePart; anchorY -= hidden;
         } else shadow(pokemon.position.x + 8, pokemon.position.y + 7, Math.min(7, frame.width * scale * .25f), 2.5f);
         anchored(region, pokemon.position.x + 8, pokemon.position.y + 7, .15f,
            frame.anchorX, anchorY, scale);
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
      trainerImage(region,32,player.position.x+8,player.position.y+7,.15f+lift,3,.85f);
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
         float height = fieldPokemon(companion,player.dirFacing,x,y,altitude,walking ? "Walk" : "Idle");
         surfaceLift=elevation.height(x,y);
         lift += altitude + Math.max(6f, Math.min(13f, height*.38f));
         // Shorten the rider's lower legs; the entire mount stays visible below.
         trainerImage(person,25,x,y-.7f,lift,0,.78f);
         return;
      }
      if (companion != null && move != null && !move.isEmpty()) {
         float side = "left".equals(player.dirFacing) ? 14f : -14f;
         Tile beside=game.map.tiles.get(lookup.set(MathUtils.floor((x+side)/16)*16,MathUtils.floor(y/16)*16));
         if(beside==null || beside.isSolid || beside.isWater || beside.isLava) side=-side;
         fieldPokemon(companion,player.dirFacing,x+side,y+5,.2f,walking ? "Walk" : "Idle");
         if ("POWER".equals(move) || "FLASH".equals(move)) {
            float pulse = .45f + .12f*MathUtils.sin(seconds*4);
            ring(x+side,y+5,7.5f,.65f,.13f,Color.toFloatBits(1f,.83f,.38f,pulse));
         }
         surfaceLift=elevation.height(x,y);
      }
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
            person=assets.trainer(player.character,"down",seconds,false);
            trainerImage(person,14,x,y+pillow*MathUtils.sinDeg(50f),
               bedLift+pillow*MathUtils.cosDeg(50f),0,.55f);
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
         trainerImage(person,32,x,y,lift+Math.max(0,DrawPlayerUpper.pokemonOffsetY)*.4f,3,.85f);
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
      surfaceLift=elevation.height(x,y);
      PmdPokemonSprites sprites=PmdPokemonSprites.get();
      PmdPokemonSprites.Frame frame=sprites.frame(pokemon,direction,animation,seconds);
      if(frame==null) {
         if(pokemon.currOwSprite!=null) actor(pokemon.currOwSprite,x-8,y-7,lift);
         return 24f;
      }
      PmdPokemonSprites.AnimationBounds bounds=sprites.bounds(pokemon,direction,animation);
      float h=bounds==null?frame.height:bounds.height;
      float w=bounds==null?frame.width:bounds.width;
      float scale=Math.min(.85f,32f/Math.max(1f,Math.max(w,h)));
      float bottom=bounds==null?-frame.anchorY:bounds.minY;
      shadow(x,y,Math.min(8,w*scale*.28f),2.7f);
      anchored(frame.region,x,y,lift-Math.min(0,bottom)*scale*MathUtils.cosDeg(50),frame.anchorX,frame.anchorY,scale);
      return h*scale;
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
      Geometry mesh=batch(translucent,whiteTexture);
      for(int i=0;i<24;i++) {
         float a=i*MathUtils.PI2/24,b=(i+1)*MathUtils.PI2/24,inner=radius-thickness;
         quad(mesh,x+MathUtils.cos(a)*inner,elevation,-y+MathUtils.sin(a)*inner,
            x+MathUtils.cos(a)*radius,elevation,-y+MathUtils.sin(a)*radius,
            x+MathUtils.cos(b)*radius,elevation,-y+MathUtils.sin(b)*radius,
            x+MathUtils.cos(b)*inner,elevation,-y+MathUtils.sin(b)*inner,white,tint);
      }
   }

   private void line(float x1,float h1,float y1,float x2,float h2,float y2,float width,float tint) {
      quad(batch(geometry,whiteTexture),x1-width,h1,-y1,x1+width,h1,-y1,
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
      upright(sprite,x-width/2,y,.2f,width,height,WHITE);
   }

   private String treeKey(Tile tile, String name) {
      BiomeProfiles.Profile profile=BiomeProfiles.visualForTile(tile);
      String key = profile.id.equals("volcano")||profile.id.equals("graveyard")||profile.id.equals("desert") ? profile.decor("tree")
         : name.contains("tree4") || name.contains("snow") ? "tree_snow"
         : name.contains("savanna") ? "tree_dry" : name.contains("tree2") ? "tree_pine" : "tree";
      return key;
   }

   /** Read the original action clock; never execute an action or advance RNG here. */
   private void fieldEffect(Game game, Action action) {
      if (!game.actionStack.contains(action)) return;
      if (action instanceof PlantTree) {
         PlantTree plant = (PlantTree)action;
         TextureRegion art = assets.named("seedling");
         float progress = MathUtils.clamp(plant.timer / 24f, 0, 1);
         float height = 4 + progress * 10, width = height * art.getRegionWidth() / art.getRegionHeight();
         surfaceLift = elevation.height(plant.pos.x + 8, plant.pos.y + 8);
         uprightRaw(art, plant.pos.x + 8 - width/2, plant.pos.y + 7, .2f, width, height, WHITE, 0);
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
      float height = assets.worldHeight(art, Math.min(42, art.getRegionHeight()));
      float width = assets.worldWidth(art, height * art.getRegionWidth() / art.getRegionHeight());
      surfaceLift = elevation.height(tile.position.x + 8, tile.position.y + 8);
      int half = Math.max(1, art.getRegionWidth()/2);
      cutLeft.setRegion(art,0,0,half,art.getRegionHeight());
      cutRight.setRegion(art,half,0,art.getRegionWidth()-half,art.getRegionHeight());
      float spread = tick < 20 ? 0 : 2 + (tick >= 39 ? 2 : 0) + (tick >= 43 ? 2 : 0) + (tick >= 47 ? 2 : 0);
      float leftWidth = width * half / art.getRegionWidth();
      float x = tile.position.x + 8 - width/2, y = tile.position.y + 8;
      uprightRaw(cutLeft,x-spread,y,.2f,leftWidth,height,WHITE,0);
      uprightRaw(cutRight,x+leftWidth+spread,y,.2f,width-leftWidth,height,WHITE,0);
      fieldEffectFrames++;
   }

   private void assetShadow(TextureRegion region,float x,float y,float width,float depth) {
      BwAssets.SpriteLayout layout=assets.layout(region);
      shadow(x+(layout==null?0:layout.offsetX),y+(layout==null?0:layout.offsetY),width,depth);
   }

   private void shadow(float x, float y, float width, float depth) {
      Geometry mesh = batch(shadows, whiteTexture);
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
      upright(region, x, y, base, width, height, tint, 0f);
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
      quad(batch(geometry, region.getTexture()),
         x, base, -y, x + width, base, -y, x + width, base + rise, -y-back, x, base + rise, -y-back, region, tint, style);
   }

   private void floor(TextureRegion region, float x, float y, float width, float depth, float elevation, float tint, Map<Texture, Geometry> groups) {
      floor(region, x, y, width, depth, elevation, tint, groups, 0f);
   }

   private void floor(TextureRegion region, float x, float y, float width, float depth, float elevation, float tint, Map<Texture, Geometry> groups, float style) {
      quad(batch(groups, region.getTexture()), x, elevation, -y, x + width, elevation, -y,
         x + width, elevation, -y - depth, x, elevation, -y - depth, region, tint, style);
   }

   private static float color(float r, float g, float b) {
      return Color.toFloatBits(r, g, b, 1f);
   }

   private Geometry batch(Map<Texture, Geometry> groups, Texture texture) {
      Geometry batch = groups.get(texture);
      if (batch == null) {
         batch = new Geometry();
         groups.put(texture, batch);
      }
      return batch;
   }

   private void quad(Geometry mesh,
      float x0, float y0, float z0, float x1, float y1, float z1,
      float x2, float y2, float z2, float x3, float y3, float z3, TextureRegion uv, float tint) {
      quad(mesh, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, uv, tint, 0f);
   }

   private void quad(Geometry mesh,
      float x0, float y0, float z0, float x1, float y1, float z1,
      float x2, float y2, float z2, float x3, float y3, float z3, TextureRegion uv, float tint, float style) {
      vertex(mesh, x0, y0, z0, tint, uv.getU(), uv.getV2(), style);
      vertex(mesh, x1, y1, z1, tint, uv.getU2(), uv.getV2(), style);
      vertex(mesh, x2, y2, z2, tint, uv.getU2(), uv.getV(), style);
      vertex(mesh, x2, y2, z2, tint, uv.getU2(), uv.getV(), style);
      vertex(mesh, x3, y3, z3, tint, uv.getU(), uv.getV(), style);
      vertex(mesh, x0, y0, z0, tint, uv.getU(), uv.getV2(), style);
   }

   private void triangle(Geometry mesh, float x0, float y0, float z0,
      float x1, float y1, float z1, float x2, float y2, float z2, float tint) {
      vertex(mesh, x0, y0, z0, tint, 0.5f, 0.5f, 0f);
      vertex(mesh, x1, y1, z1, tint, 0.5f, 0.5f, 0f);
      vertex(mesh, x2, y2, z2, tint, 0.5f, 0.5f, 0f);
   }

   private void vertex(Geometry mesh, float x, float y, float z, float color, float u, float v, float style) {
      mesh.vertices.add(x);
      mesh.vertices.add(y+surfaceLift);
      mesh.vertices.add(z);
      mesh.vertices.add(color);
      mesh.vertices.add(u);
      mesh.vertices.add(v);
      mesh.vertices.add(style);
      mesh.vertices.add(style>3.5f?projectedAnchor.set(x,y+surfaceLift+.12f,actorPlaneZ).prj(camera.combined).z:0f);
   }

   private void clearGeometry() {
      for (Geometry mesh : geometry.values()) mesh.vertices.clear();
      for (Geometry mesh : translucent.values()) mesh.vertices.clear();
      for (Geometry mesh : shadows.values()) mesh.vertices.clear();
      for (Geometry mesh : emotes.values()) mesh.vertices.clear();
   }

   private static void upload(Map<Texture, Geometry> groups) {
      java.util.Iterator<Geometry> iterator = groups.values().iterator();
      while (iterator.hasNext()) {
         Geometry geometry = iterator.next();
         int count = geometry.vertices.size / FLOATS_PER_VERTEX;
         // The PMD atlas cache may evict unseen species. Do not retain mesh and
         // texture keys for every sheet ever visited by the player.
         if (count == 0) { geometry.dispose(); iterator.remove(); continue; }
         // Mesh has no index buffer; chunks keep compatibility with older GL drivers.
         int chunks = (count + MAX_VERTICES - 1) / MAX_VERTICES;
         if (geometry.meshes == null || geometry.meshes.length < chunks) {
            Mesh[] replacement = new Mesh[chunks];
            if (geometry.meshes != null) System.arraycopy(geometry.meshes, 0, replacement, 0, geometry.meshes.length);
            geometry.meshes = replacement;
         }
         for (int i = 0; i < chunks; i++) {
            int vertices = Math.min(MAX_VERTICES, count - i * MAX_VERTICES);
            Mesh mesh = geometry.meshes[i];
            if (mesh == null || mesh.getMaxVertices() < vertices) {
               if (mesh != null) mesh.dispose();
               mesh = new Mesh(false, Math.min(MAX_VERTICES, Math.max(256, vertices * 2)), 0,
                  new VertexAttribute(Usage.Position, 3, "a_position"),
                  new VertexAttribute(Usage.ColorPacked, 4, "a_color"),
                  new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                  new VertexAttribute(Usage.Generic, 1, "a_style"),
                  new VertexAttribute(Usage.Generic, 1, "a_anchorDepth"));
               geometry.meshes[i] = mesh;
            }
            mesh.setVertices(geometry.vertices.items, i * MAX_VERTICES * FLOATS_PER_VERTEX, vertices * FLOATS_PER_VERTEX);
         }
      }
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
         if(fire || tile.isTorch)index=light(index,tile.position.x+8,tile.position.y+8,fire?65:42,.88f+.05f*MathUtils.sin(seconds*7));
      }
      // Cluster lava illumination on a stable world lattice; do not spend every slot on one pool.
      for(Tile tile:game.map.onscreenTiles) {
         if(index>=8)break;
         if(tile.isLava && Math.floorMod((int)tile.position.x/16,3)==0 && Math.floorMod((int)tile.position.y/16,3)==0)
            index=light(index,tile.position.x+8,tile.position.y+8,35,
               BiomeProfiles.visualForTile(tile).number("ambient","glow",.42f)+.05f*MathUtils.sin(seconds*2));
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
      shader.setUniformf("u_fogDensity",fogDensity);shader.setUniformf("u_desaturation",desaturation);shader.setUniformf("u_time",seconds);
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
         renderGroups(geometry);
         shader.end();
         shaderBegun = false;
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
         renderGroups(translucent);
         renderGroups(shadows);
         // Alarm/hearts/status balloons are overlays in the original game; keep
         // them readable above foreground crowns without changing their lifetime.
         Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
         renderGroups(emotes);
      } finally {
         if (shaderBegun) shader.end();
         // Preserve the caller's actual clear color, including special transitions.
         Gdx.gl.glClearColor(originalClearColor.get(0), originalClearColor.get(1),
            originalClearColor.get(2), originalClearColor.get(3));
      }
   }

   private void renderGroups(Map<Texture, Geometry> groups) {
      for (Map.Entry<Texture, Geometry> entry : groups.entrySet()) {
         Geometry geometry = entry.getValue();
         int count = geometry.vertices.size / FLOATS_PER_VERTEX;
         if (count == 0) continue;
         entry.getKey().bind(0);
         for (int i = 0; i * MAX_VERTICES < count; i++) {
            geometry.meshes[i].render(shader, GL20.GL_TRIANGLES, 0, Math.min(MAX_VERTICES, count - i * MAX_VERTICES));
         }
      }
   }

   public void dispose() {
      for (Geometry batch : geometry.values()) batch.dispose();
      for (Geometry batch : translucent.values()) batch.dispose();
      for (Geometry batch : shadows.values()) batch.dispose();
      for (Geometry batch : emotes.values()) batch.dispose();
      geometry.clear();
      translucent.clear();
      shadows.clear();
      emotes.clear();
      emoteOwners.clear();
      actorModels.dispose();
      if (shader != null) shader.dispose();
      if (whiteTexture != null) whiteTexture.dispose();
      shader = null;
      assets = null;
      motion.clear();
      whiteTexture = null;
      initialized = false;
      // Textures borrowed from the game caches are deliberately never disposed here.
   }

   private static final class Geometry {
      final FloatArray vertices = new FloatArray(false, 1536);
      Mesh[] meshes;

      void dispose() {
         if (meshes != null) for (Mesh mesh : meshes) if (mesh != null) mesh.dispose();
      }
   }
}
