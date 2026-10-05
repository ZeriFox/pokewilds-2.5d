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
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.FloatArray;
import com.pkmngen.game.util.SmolSprite;
import com.pkmngen.game.util.TextureCache;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Optional perspective presentation of the existing overworld.
 *
 * Coordinates, tile identity, actors, random generators and action execution belong
 * exclusively to Game. This renderer only reads them after the normal map actions.
 * In particular it must never call Tile.init(), setView(), any draw/step action or
 * any method which advances simulation. Unsupported scenes keep the normal image.
 */
public final class JohtoRenderer {
   private static final float TILE = 16f;
   private static final float WHITE = Color.WHITE_FLOAT_BITS;
   private static final int FLOATS_PER_VERTEX = 7;
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
   private TextureRegion pickupSprite;
   private TextureRegion fossilSprite;
   private ShaderProgram shader;
   private Texture whiteTexture;
   private TextureRegion white;
   private boolean initialized;
   private boolean failed;
   private boolean reported;
   private long renderedFrames;

   public JohtoRenderer() {
      camera.near = 1f;
      camera.far = 1800f;
   }

   /** Returns false without clearing the original image when the scene is unsupported. */
   public boolean render(Game game) {
      if (failed || !supports(game)) {
         return false;
      }
      try {
         if (!initialized) {
            initialize();
         }
         clearGeometry();
         configureCamera(game);
         seconds += Math.min(0.1f, Math.max(0f, Gdx.graphics.getDeltaTime()));
         collectWorld(game);
         // Upload all geometry before clearing the original image; allocation and
         // shader failures therefore retain the already rendered 2D fallback.
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
         Gdx.app.error("JohtoRenderer", "Visual renderer disabled after an error. The original renderer resumes; use run.cmd for classic graphics.", ex);
         return false;
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

   public long getPlayerModelFrames() { return actorModels.getPlayerModelFrames(); }
   public long getPokemonModelFrames() { return actorModels.getPokemonModelFrames(); }
   public long getAnimatedModelFrames() { return actorModels.getAnimatedModelFrames(); }
   public int getLoadedSpeciesCount() { return actorModels.getLoadedSpeciesCount(); }
   public boolean hasLoadedSpecies(String name) { return actorModels.hasLoadedSpecies(name); }

   private boolean supports(Game game) {
      if (game == null || game.map == null || game.player == null || game.cam == null
         || game.map.tiles.isEmpty()
         || game.player.dontDrawMapDuringBattle || game.cinematic
         || game.player.isSleeping || game.player.isSitting || game.player.isFishing
         || game.player.isCrafting || game.player.currPlanting != null
         || !game.player.currFieldMove.isEmpty()) {
         return false;
      }
      boolean hasMap = false;
      for (Action action : game.actionStack) {
         if (action == null) {
            continue;
         }
         if (action instanceof DrawMap) {
            hasMap = true;
         }
         String name = action.getClass().getSimpleName();
         // Preserve original map-space overlays, touch targeting and transitions.
         if (action instanceof DrawMiniMap || action instanceof TileEditor
            || name.startsWith("Battle") || name.startsWith("DrawBattle")
            || name.startsWith("Special") || name.startsWith("Throw")
            || name.startsWith("Catch") || name.equals("DrawWhiteScreen")
            || name.equals("FadeAnim") || name.equals("LightFadeIn")
            || name.equals("LightFadeOut") || name.equals("GenerateWorld")
            || name.equals("DrawSetupMenu")) {
            return false;
         }
      }
      return hasMap && game.player.currSprite != null && game.player.currSprite.getTexture() != null;
   }

   private void initialize() {
      String vertex = "attribute vec3 a_position;\n"
         + "attribute vec4 a_color;\nattribute vec2 a_texCoord0;\nattribute float a_style;\n"
         + "uniform mat4 u_projTrans;\nvarying vec4 v_color;\nvarying vec2 v_texCoords;\nvarying float v_style;\n"
         + "void main(){v_color=a_color;v_color.a*=255.0/254.0;v_texCoords=a_texCoord0;v_style=a_style;"
         + "gl_Position=u_projTrans*vec4(a_position,1.0);}\n";
      String fragment = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
         + "uniform sampler2D u_texture;\nuniform vec3 u_tint;\n"
         + "varying vec4 v_color;\nvarying vec2 v_texCoords;\nvarying float v_style;\n"
         + "void main(){vec4 c=texture2D(u_texture,v_texCoords);"
         // Apply only to explicitly tagged original terrain, never to actors,
         // objects, new terrain or geometric meshes sharing an atlas texture.
         + "if(v_style>0.5&&v_style<1.5&&c.g>c.r*1.05&&c.g>c.b*1.12){"
         + "float t=clamp((c.g-0.18)/0.82,0.0,1.0);"
         + "c.rgb=mix(vec3(0.15,0.34,0.24),vec3(0.48,0.71,0.35),t);}"
         + "if(v_style>1.5&&v_style<2.5&&c.b>c.r*1.06&&c.b>c.g*1.08&&min(c.r,c.g)<0.74){"
         + "float t=clamp((dot(c.rgb,vec3(0.299,0.587,0.114))-0.2)/0.65,0.0,1.0);"
         + "c.rgb=mix(vec3(0.04,0.33,0.66),vec3(0.24,0.69,0.82),t);}"
         + "if(v_style>2.5){float t=dot(c.rgb,vec3(0.299,0.587,0.114));c.rgb=mix(vec3(0.48,0.055,0.018),vec3(1.0,0.78,0.16),clamp(t*2.4,0.0,1.0));}"
         + "c*=v_color;"
         + "if(c.a<0.1)discard;gl_FragColor=vec4(c.rgb*(v_style>2.5?mix(u_tint,vec3(1.0),0.65):u_tint),c.a);}\n";
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
      // Borrowed item sheets preserve the item identity and pickup rules.
      pickupSprite = new TextureRegion(TextureCache.get(Gdx.files.internal("tiles/pokeball1.png")));
      fossilSprite = new TextureRegion(TextureCache.get(Gdx.files.internal("tiles/fossil1.png")), 0, 0, 16, 16);
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
      camera.position.set(game.cam.position.x, MathUtils.sin(angle) * distance,
         -game.cam.position.y + MathUtils.cos(angle) * distance);
      camera.up.set(0f, 1f, 0f);
      camera.lookAt(game.cam.position.x, 0f, -game.cam.position.y);
      camera.update();
   }

   private void collectWorld(Game game) {
      float halfWidth = game.cam.viewportWidth * game.cam.zoom * 0.5f;
      float halfHeight = game.cam.viewportHeight * game.cam.zoom * 0.5f;
      int left = MathUtils.floor((game.cam.position.x - halfWidth - 64f) / TILE);
      int right = MathUtils.ceil((game.cam.position.x + halfWidth + 64f) / TILE);
      int bottom = MathUtils.floor((game.cam.position.y - halfHeight - 48f) / TILE);
      int top = MathUtils.ceil((game.cam.position.y + halfHeight + 80f) / TILE);
      for (int row = bottom; row <= top; row++) {
         for (int col = left; col <= right; col++) {
            Tile tile = game.map.tiles.get(lookup.set(col * TILE, row * TILE));
            if (tile != null && tile.sprite != null) {
               tile(game, tile);
            }
         }
      }
      for (Pokemon pokemon : game.map.onscreenPokemon) {
         pokemon(game, pokemon);
      }
      for (Tile tile : game.map.onscreenFossils) {
         pickup(tile);
      }
      for (Pokemon pokemon : game.map.onscreenBurrowed) {
         pokemon(game, pokemon);
      }
      if (game.player.hmPokemon != null && !game.map.onscreenPokemon.contains(game.player.hmPokemon)) {
         pokemon(game, game.player.hmPokemon);
      }
      for (Player other : game.players.values()) {
         if (other != null && other.network != null && other.network.tiles == game.map.tiles
            && !other.isSleeping && other.currSprite != null) {
            trainer(other, 0f);
         }
      }
      trainer(game.player, Math.max(0, DrawPlayerUpper.pokemonOffsetY));
      // Read the same map-action snapshot used in this frame. The original action
      // already advanced its timer; rendering it here must never call step again.
      for (Action action : game.actionStackCopy) {
         if (action instanceof Pokemon.Emote) {
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
      TextureRegion region;
      if (item.contains("fossil") || item.equals("old amber")) {
         int row;
         switch (item) {
            case "old amber": row = 0; break;
            case "helix fossil": row = 1; break;
            case "dome fossil": row = 2; break;
            case "root fossil": row = 3; break;
            case "claw fossil": row = 4; break;
            case "shield fossil": row = 5; break;
            case "skull fossil": row = 6; break;
            default: return;
         }
         fossilSprite.setRegion(0, row * 16, 16, 16);
         region = fossilSprite;
      } else {
         if (tile.isSign() || item.equals("secret key") || item.equals("pok\u00e9 ball")) return;
         region = pickupSprite;
      }
      shadow(tile.position.x + 8f, tile.position.y + 7f, 4f, 2f);
      upright(region, tile.position.x, tile.position.y + 7f, 0.2f,
         region.getRegionWidth(), region.getRegionHeight(), WHITE);
   }

   private void tile(Game game, Tile tile) {
      float x = tile.position.x, y = tile.position.y;
      String lower = tile.name == null ? "" : tile.name;
      String upper = tile.nameUpper == null ? "" : tile.nameUpper;
      String names = (lower + " " + upper).toLowerCase(java.util.Locale.ROOT);
      boolean tree = isTree(tile, lower, upper);
      TextureRegion ground = assets.terrain(tile);
      if (ground == null) ground = tile.sprite;
      if (tile.isLava) ground = assets.cell("water", x + (int)(seconds * 2f) * 16, y);
      float tint = lower.equals("volcano2") ? color(.66f,.67f,.68f) : WHITE;
      floor(ground, x, y, TILE, TILE, 0f, tint, geometry, tile.isLava ? 3f : 0f);
      // Coast geometry follows actual wet/dry adjacency; never adds collision.
      if (tile.isWater && !tile.isLava) coast(game, tile);
      if (ModernWorldGenerator.isRamp(tile)) {
         floor(assets.named("steps"), x + 2, y + 1, 12, 14, .08f, color(.82f,.82f,.73f), geometry);
         return;
      }
      if (tile.isLedge && (tile.ledgeDir != null || upper.startsWith("ledges3"))) {
         cliff(tile, x, y);
         return;
      }
      if (tree) { tree(tile, x + 8f, y + 8f, names); return; }
      if (building(tile, x, y, names)) return;
      TextureRegion object = assets.object(tile);
      if (object != null) {
         boolean plant = names.contains("grass") || names.contains("flower");
         float width = names.contains("rock") ? 17f : plant ? 16f : 18f;
         float height = Math.min(25f, width * object.getRegionHeight() / Math.max(1f, object.getRegionWidth()));
         if (!plant) shadow(x + 8, y + 7, width * .3f, 2.4f);
         upright(object, x + 8 - width/2, y + 7, .15f, width, height, WHITE);
      } else if (tile.overSprite != null && tile.overSprite != tile.sprite) {
         // Unknown interactive machinery, doors, puzzle symbols and rare events
         // retain their source cue instead of inventing a misleading replacement.
         SmolSprite sprite = tile.overSprite;
         if (tile.drawUpperBelowPlayer || names.contains("warp") || names.contains("stairs"))
            floor(sprite, sprite.getX(), sprite.getY() - tile.yOffset, sprite.getRegionWidth(), sprite.getRegionHeight(), .09f, WHITE, geometry);
         else upright(sprite, sprite.getX(), y + 7, .1f, sprite.getRegionWidth(), sprite.getRegionHeight(), WHITE);
      }
   }

   private void coast(Game game, Tile tile) {
      int[][] dirs = {{0,-16},{0,16},{-16,0},{16,0}};
      float x = tile.position.x, y = tile.position.y;
      for (int i=0;i<4;i++) {
         Tile next = game.map.tiles.get(lookup.set(x+dirs[i][0],y+dirs[i][1]));
         if (next == null || next.isWater || next.isLava) continue;
         float sx=x,sy=y,w=16,d=2;
         if(i==1) sy+=14;
         else if(i==2) { w=2;d=16; }
         else if(i==3) { sx+=14;w=2;d=16; }
         floor(assets.named("water_ripple"),sx,sy,w,d,.035f,Color.toFloatBits(.72f,.95f,.93f,.80f),translucent);
      }
   }

   /** Only real ledges receive relief. No inferred global height or new barriers. */
   private void cliff(Tile tile, float x, float y) {
      boolean volcano = ModernWorldGenerator.isVolcanic(tile);
      String upper = tile.nameUpper == null ? "" : tile.nameUpper;
      volcano |= upper.startsWith("ledges3volcano");
      boolean snow = upper.startsWith("ledges3snow");
      TextureRegion face = assets.cell(volcano ? "basalt" : "cliff", x, y);
      TextureRegion cap = assets.cell(volcano ? "basalt" : snow ? "snow" : "mountain", x, y);
      Geometry mesh = batch(geometry, face.getTexture());
      float h = 8f, z = -y;
      String direction = tile.ledgeDir;
      int edges=0; // N=front/down, S=back/up, E=left, W=right: original Tile convention.
      if(upper.startsWith("ledges3")) {
         String[] parts=upper.split("_");
         if(parts.length>1)for(char c:parts[1].toCharArray())edges|=c=='N'?1:c=='S'?2:c=='E'?4:c=='W'?8:0;
      } else edges="down".equals(direction)?1:"up".equals(direction)?2:"left".equals(direction)?4:"right".equals(direction)?8:0;
      if(edges==0) {
         // The original ledges3_none cell is solid, with no singled-out edge.
         // Preserve its visible blocked footprint rather than dropping its art.
         if(tile.isSolid)prism(x,y,0,16,16,h,face,WHITE);
         return;
      }
      // Inner corners only touch a lower diagonal neighbour. A short corner
      // return preserves that cue while keeping the walkable centre unobscured.
      boolean inner=upper.contains("_inner");
      float a=inner?((edges&4)!=0?0:13):0, b=inner?a+3:16;
      float c=inner?((edges&1)!=0?0:13):0, d=inner?c+3:16;
      if((edges&1)!=0){
         quad(mesh,x+a,0,z,x+b,0,z,x+b,h,z,x+a,h,z,face,WHITE);
         floor(cap,x+a,y,b-a,3,h,WHITE,geometry);
      }
      if((edges&2)!=0){
         quad(mesh,x+b,0,z-16,x+a,0,z-16,x+a,h,z-16,x+b,h,z-16,face,color(.77f,.78f,.80f));
         floor(cap,x+a,y+13,b-a,3,h,WHITE,geometry);
      }
      if((edges&4)!=0){
         quad(mesh,x,0,z-d,x,0,z-c,x,h,z-c,x,h,z-d,face,color(.82f,.83f,.84f));
         floor(cap,x,y+c,3,d-c,h,WHITE,geometry);
      }
      if((edges&8)!=0){
         quad(mesh,x+16,0,z-c,x+16,0,z-d,x+16,h,z-d,x+16,h,z-c,face,color(.70f,.72f,.75f));
         floor(cap,x+13,y+c,3,d-c,h,WHITE,geometry);
      }
   }

   private boolean building(Tile tile, float x, float y, String name) {
      if (name.contains("house_plant")) {
         prism(x+5,y+5,0,6,6,5,assets.named("wall_wood"),color(.72f,.52f,.35f));
         upright(assets.named("tree_small"),x+2,y+9,3,12,18,WHITE);
         return true;
      }
      if (name.contains("couch") || name.contains("bed")) {
         TextureRegion wood=assets.named("wood_floor"), fabric=assets.named("rug");
         prism(x,y+2,0,16,12,5,wood,color(.78f,.70f,.56f));
         floor(fabric,x+1,y+3,14,10,5.1f,WHITE,geometry);
         if(name.contains("bed"))floor(assets.named("tile_pale"),x+2,y+10,12,4,5.2f,WHITE,geometry);
         else {
            prism(x,y+13,5,16,3,11,fabric,WHITE);
            prism(x,y+2,5,2,11,8,wood,WHITE);prism(x+14,y+2,5,2,11,8,wood,WHITE);
         }
         return true;
      }
      if (name.contains("bridge")) {
         floor(assets.named("wood_floor"),x,y,16,16,.1f,WHITE,geometry); return true;
      }
      if (name.contains("stairs")) {
         floor(assets.named("steps"),x,y,16,16,.1f,WHITE,geometry); return true;
      }
      if (name.contains("fence") || name.contains("gate")) {
         // Gates stay open visually when their original collision is open.
         if (tile.isSolid) upright(assets.named("fence_wood"),x,y+8,.1f,16,12,WHITE);
         else floor(assets.named("wood_floor"),x,y,16,16,.1f,WHITE,geometry);
         return true;
      }
      if (name.contains("roof")) {
         floor(assets.named("brick_floor"),x,y,16,16,12f,color(.65f,.35f,.30f),geometry); return true;
      }
      if (name.contains("door")) {
         floor(assets.named("steps"),x,y,16,12,.1f,WHITE,geometry);
         // Keep the doorway footprint open; the frame rises on its back edge.
         upright(assets.named("door_red"),x,y+14,.1f,16,19,WHITE); return true;
      }
      boolean wall = name.contains("wall") || name.matches(".*house[0-9].*")
         || name.contains("cave") && tile.isSolid && !name.contains("regi");
      if (wall && tile.isSolid) {
         TextureRegion face=assets.named(name.contains("cave") ? "cliff_dark" : name.contains("ruin") ? "ruin_wall" : "wall");
         float h=name.contains("cave")?14:19;
         quad(batch(geometry,face.getTexture()),x,0,-y,x+16,0,-y,x+16,h,-y,x,h,-y,face,WHITE);
         floor(assets.named("tile_pale"),x,y,16,16,h,color(.87f,.86f,.80f),geometry);
         return true;
      }
      return false;
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
      upright(sprite, x, y + 7f, 0.15f + lift, width, height, sprite.getColor().toFloatBits());
   }

   private void trainer(Player player, float lift) {
      TextureRegion region = assets.trainer(player, seconds, moving(player, player.position.x, player.position.y));
      shadow(player.position.x + 8, player.position.y + 7, 4.5f, 2.5f);
      anchored(region, player.position.x + 8, player.position.y + 7, .15f + lift, 16f, 3f, .85f);
   }

   private void anchored(TextureRegion region, float groundX, float groundY, float lift, float anchorX, float anchorY, float scale) {
      upright(region, groundX - anchorX * scale, groundY - anchorY * scale * MathUtils.sinDeg(50f),
         lift - anchorY * scale * MathUtils.cosDeg(50f), region.getRegionWidth() * scale, region.getRegionHeight() * scale, WHITE);
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
      String key = name.contains("tree4") || name.contains("snow") ? "tree_snow"
         : name.contains("savanna") ? "tree_dry" : name.contains("tree2") ? "tree_pine" : "tree";
      TextureRegion sprite = assets.named(key);
      float height = name.contains("large") ? 38 : 31;
      float width = height * sprite.getRegionWidth() / sprite.getRegionHeight();
      shadow(x,y,Math.min(width*.35f,10f),4);
      upright(sprite,x-width/2,y,.2f,width,height,WHITE);
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

   private static void quad(Geometry mesh,
      float x0, float y0, float z0, float x1, float y1, float z1,
      float x2, float y2, float z2, float x3, float y3, float z3, TextureRegion uv, float tint) {
      quad(mesh, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, uv, tint, 0f);
   }

   private static void quad(Geometry mesh,
      float x0, float y0, float z0, float x1, float y1, float z1,
      float x2, float y2, float z2, float x3, float y3, float z3, TextureRegion uv, float tint, float style) {
      vertex(mesh, x0, y0, z0, tint, uv.getU(), uv.getV2(), style);
      vertex(mesh, x1, y1, z1, tint, uv.getU2(), uv.getV2(), style);
      vertex(mesh, x2, y2, z2, tint, uv.getU2(), uv.getV(), style);
      vertex(mesh, x2, y2, z2, tint, uv.getU2(), uv.getV(), style);
      vertex(mesh, x3, y3, z3, tint, uv.getU(), uv.getV(), style);
      vertex(mesh, x0, y0, z0, tint, uv.getU(), uv.getV2(), style);
   }

   private static void triangle(Geometry mesh, float x0, float y0, float z0,
      float x1, float y1, float z1, float x2, float y2, float z2, float tint) {
      vertex(mesh, x0, y0, z0, tint, 0.5f, 0.5f, 0f);
      vertex(mesh, x1, y1, z1, tint, 0.5f, 0.5f, 0f);
      vertex(mesh, x2, y2, z2, tint, 0.5f, 0.5f, 0f);
   }

   private static void vertex(Geometry mesh, float x, float y, float z, float color, float u, float v, float style) {
      mesh.vertices.add(x);
      mesh.vertices.add(y);
      mesh.vertices.add(z);
      mesh.vertices.add(color);
      mesh.vertices.add(u);
      mesh.vertices.add(v);
      mesh.vertices.add(style);
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
                  new VertexAttribute(Usage.Generic, 1, "a_style"));
               geometry.meshes[i] = mesh;
            }
            mesh.setVertices(geometry.vertices.items, i * MAX_VERTICES * FLOATS_PER_VERTEX, vertices * FLOATS_PER_VERTEX);
         }
      }
   }

   private void draw(Game game) {
      Color tint = game.mapBatch.getColor();
      float r = tint.r;
      float g = tint.g;
      float b = tint.b;
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
