package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
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
import com.pkmngen.game.util.texture.DynamicTextures;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.IdentityHashMap;
import java.util.Map;

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
   private final TextureRegion[] terrain = new TextureRegion[16];
   private final TextureRegion spritePart = new TextureRegion();
   private final FloatBuffer originalClearColor = BufferUtils.newFloatBuffer(4);
   private TextureRegion pickupSprite;
   private TextureRegion fossilSprite;
   private ShaderProgram shader;
   private Texture atlas;
   private Texture foliageAtlas;
   private final TextureRegion[] foliage = new TextureRegion[4];
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
         actorModels.beginFrame(camera);
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
         || game.map.tiles != game.map.overworldTiles || game.map.tiles.isEmpty()
         || game.player.dontDrawMapDuringBattle || game.cinematic
         || game.player.isSleeping || game.player.isSitting || game.player.isFishing
         || game.player.isCrafting || game.player.currPlanting != null
         || !game.player.currFieldMove.isEmpty()
         || "night".equals(game.map.timeOfDay) || CycleDayNight.fadeToDay || CycleDayNight.fadeToNight) {
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
         + "if(v_style>1.5&&c.b>c.r*1.06&&c.b>c.g*1.08&&min(c.r,c.g)<0.74){"
         + "float t=clamp((dot(c.rgb,vec3(0.299,0.587,0.114))-0.2)/0.65,0.0,1.0);"
         + "c.rgb=mix(vec3(0.04,0.33,0.66),vec3(0.24,0.69,0.82),t);}" 
         + "c*=v_color;"
         + "if(c.a<0.1)discard;gl_FragColor=vec4(c.rgb*u_tint,c.a);}\n";
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
      FileHandle foliageFile = Gdx.files.internal("visual/johto/foliage-sprites.png");
      if (foliageFile.exists()) {
         // Atlas packing is described by four source rectangles. Tighten their
         // alpha bounds at load time, without modifying the generated image.
         Pixmap source = new Pixmap(foliageFile);
         Pixmap packed = new Pixmap(256,128,Pixmap.Format.RGBA8888);
         packed.setFilter(Pixmap.Filter.NearestNeighbour);
         int[][] cells = {{0,0,710,710},{710,0,544,710},{0,710,710,544},{710,710,544,544}};
         try {
            for(int i=0;i<cells.length;i++) {
               int[] c=cells[i]; int x0=c[0]+c[2], y0=c[1]+c[3], x1=c[0], y1=c[1];
               for(int sy=c[1];sy<c[1]+c[3];sy++) for(int sx=c[0];sx<c[0]+c[2];sx++) {
                  if((source.getPixel(sx,sy)&255)>32) { x0=Math.min(x0,sx);y0=Math.min(y0,sy);x1=Math.max(x1,sx);y1=Math.max(y1,sy); }
               }
               if(x1<x0 || y1<y0) throw new IllegalArgumentException("Empty foliage atlas cell "+i);
               int width=x1-x0+1,height=y1-y0+1;
               float scale=Math.min(62f/width,94f/height);
               int tw=Math.max(1,Math.round(width*scale)), th=Math.max(1,Math.round(height*scale));
               packed.drawPixmap(source,x0,y0,width,height,i*64+1,1,tw,th);
               // Regions are attached after creating the shared GPU texture.
               cells[i]=new int[]{i*64+1,1,tw,th};
            }
            foliageAtlas=new Texture(packed);
            foliageAtlas.setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);
            for(int i=0;i<4;i++) foliage[i]=new TextureRegion(foliageAtlas,cells[i][0],cells[i][1],cells[i][2],cells[i][3]);
         } finally { source.dispose();packed.dispose(); }
      }
      // Borrow the same cached sheets used by DrawMapGrass. These are not owned by
      // this renderer, and their regions never mutate the original Sprite objects.
      pickupSprite = new TextureRegion(TextureCache.get(Gdx.files.internal("tiles/pokeball1.png")));
      fossilSprite = new TextureRegion(TextureCache.get(Gdx.files.internal("tiles/fossil1.png")), 0, 0, 16, 16);
      FileHandle file = Gdx.files.internal("visual/johto/terrain-atlas.png");
      if (!file.exists()) {
         file = Gdx.files.local("assets/visual/johto/terrain-atlas.png");
      }
      if (file.exists()) {
         Pixmap source = new Pixmap(file);
         Pixmap normalized = new Pixmap(128, 128, Pixmap.Format.RGBA8888);
         try {
            if (source.getWidth() < 16 || source.getHeight() < 16) {
               throw new IllegalArgumentException("Terrain atlas must contain a four by four grid.");
            }
            // Technical texture preparation only: one 32px pixel-art cell per
            // logical tile, avoiding subpixel shimmer from the generated source.
            // The original asset remains untouched on disk.
            normalized.setFilter(Pixmap.Filter.NearestNeighbour);
            for (int row = 0; row < 4; row++) {
               for (int col = 0; col < 4; col++) {
                  int sx = col * source.getWidth() / 4;
                  int sy = row * source.getHeight() / 4;
                  int sw = (col + 1) * source.getWidth() / 4 - sx;
                  int sh = (row + 1) * source.getHeight() / 4 - sy;
                  normalized.drawPixmap(source, sx, sy, sw, sh, col * 32, row * 32, 32, 32);
               }
            }
            atlas = new Texture(normalized);
         } finally {
            normalized.dispose();
            source.dispose();
         }
         atlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
         for (int i = 0; i < terrain.length; i++) {
            float insetX = 0.5f / atlas.getWidth();
            float insetY = 0.5f / atlas.getHeight();
            terrain[i] = new TextureRegion(atlas, (i % 4) * 0.25f + insetX, (i / 4) * 0.25f + insetY,
               (i % 4 + 1) * 0.25f - insetX, (i / 4 + 1) * 0.25f - insetY);
         }
      } else {
         Gdx.app.log("JohtoRenderer", "No terrain atlas found: original terrain textures retained.");
      }
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
               tile(tile);
            }
         }
      }
      for (Pokemon pokemon : game.map.onscreenPokemon) {
         pokemon(game, pokemon);
      }
      for (Tile tile : game.map.onscreenFossils) {
         pickup(tile);
      }
      for (Tile tile : game.map.onscreenExteriorTidal) {
         floor(DrawMapGrass.tidalSprite, tile.position.x, tile.position.y, TILE, TILE, 0.08f,
            Color.toFloatBits(1f, 1f, 1f, 0.5f), translucent, 2f);
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
            if (actorModels.queuePlayer(other, 0f)) {
               shadow(other.position.x + 8f, other.position.y + 7f, 5f, 3f);
            } else {
               actor(other.currSprite, other.position.x, other.position.y, 0f);
            }
         }
      }
      float playerLift = Math.max(0, DrawPlayerUpper.pokemonOffsetY);
      if (actorModels.queuePlayer(game.player, playerLift)) {
         shadow(game.player.position.x + 8f, game.player.position.y + 7f, 5f, 3f);
      } else {
         actor(game.player.currSprite, game.player.position.x, game.player.position.y, playerLift);
      }
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

   private void tile(Tile tile) {
      float x = tile.position.x;
      float y = tile.position.y;
      String lower = tile.name == null ? "" : tile.name;
      String upper = tile.nameUpper == null ? "" : tile.nameUpper;
      boolean tree = isTree(tile, lower, upper);
      boolean rock = tile.isSmashable && upper.startsWith("rock") && !upper.contains("cracked");
      TextureRegion ground = tile.sprite;
      int cell = terrainCell(tile, lower);
      if (tree && lower.contains("tree")) {
         cell = lower.contains("snow") || upper.equals("tree4") ? 6 : 0;
      }
      if (atlas != null && cell >= 0) {
         ground = terrain[cell];
         floor(ground, x, y, TILE, TILE, 0f, WHITE, geometry);
      } else if (tree && lower.contains("tree")) {
         floor(white, x, y, TILE, TILE, 0f, color(0.40f, 0.66f, 0.35f), geometry);
      } else {
         floor(ground, tile.sprite.getX(), tile.sprite.getY(), tile.sprite.getRegionWidth(),
            tile.sprite.getRegionHeight(), 0f, WHITE, geometry, originalTerrainStyle(lower));
      }
      if (tile.shoreOcean != null) {
         TextureRegion shore = DynamicTextures.get(tile.shoreOcean, MoveWater.shoreIndex);
         if (shore != null) floor(shore, x, y, TILE, TILE, 0.045f, WHITE, geometry, 2f);
      }
      if (tile.shoreTidal != null) {
         TextureRegion shore = DynamicTextures.get(tile.shoreTidal, MoveWater.shoreIndex);
         if (shore != null) floor(shore, x, y, TILE, TILE, 0.06f, Color.toFloatBits(1f, 1f, 1f, 0.5f), translucent, 2f);
      }
      if (tree) {
         tree(tile, x + 8f, y + 8f, lower + upper);
      } else if (rock) {
         if (foliage[3]!=null) {
            shadow(x+8,y+7,6,3);
            upright(foliage[3],x,y+7,.15f,16,12,upper.contains("ice")?color(.75f,.90f,1f):WHITE);
         } else {
            TextureRegion sprite=tile.overSprite!=null?tile.overSprite:tile.sprite;
            upright(sprite,x,y+7,.15f,16,16,WHITE);
         }
      } else if (tile.overSprite != null && tile.overSprite != tile.sprite) {
         SmolSprite sprite = tile.overSprite;
         boolean flat = tile.drawUpperBelowPlayer || upper.contains("floor") || upper.contains("bridge")
            || upper.contains("stairs") || upper.contains("warp") || upper.contains("roof");
         if (flat) {
            floor(sprite, sprite.getX(), sprite.getY() - tile.yOffset,
               sprite.getRegionWidth(), sprite.getRegionHeight(), 0.09f, WHITE, geometry);
         } else {
            upright(sprite, sprite.getX(), y + 7f, 0.1f,
               sprite.getRegionWidth(), sprite.getRegionHeight(), WHITE,
               upper.startsWith("grass") || upper.startsWith("bush") || lower.startsWith("grass") ? 1f : 0f);
         }
      }
   }

   private static float originalTerrainStyle(String name) {
      if (name.startsWith("water") || name.contains("tidalwater")) return 2f;
      if (name.startsWith("green") || name.startsWith("grass") || name.startsWith("flower")
         || name.startsWith("ground") || name.startsWith("sand") || name.startsWith("desert")
         || name.startsWith("mountain") || name.startsWith("ledge") || name.startsWith("path")
         || name.startsWith("tree") || name.startsWith("bush")) return 1f;
      return 0f;
   }

   private static boolean isTree(Tile tile, String lower, String upper) {
      if (upper.contains("planted") || upper.contains("berrytree") || upper.contains("stump")) return false;
      return tile.isTree || lower.matches("tree[1-7]") || lower.startsWith("tree_large")
         || upper.matches("tree[1-7]") || upper.startsWith("tree_large") || upper.equals("tree_savanna1");
   }

   private static int terrainCell(Tile tile, String name) {
      // Exact names only: masks, holes, ramps, cliffs, doors and coast combinations
      // retain their original texture and thus their visual movement cues.
      switch (name) {
         case "green1": case "green2": case "grass2": case "grass3": case "grass4":
         case "grass_short2": case "grass_short3": return tile.biome.contains("forest") ? 1 : 0;
         case "green_woodedlake": case "green_deepforest": return 1;
         case "sand1": case "sand2": case "sand3": case "sand4":
         case "desert1": case "desert2": case "desert3": case "desert4": case "desert6": return 2;
         case "ground1": case "ground2": case "ground3": return 3;
         case "water1": case "water2": return 4;
         case "water3": case "tidalwater1": return 5;
         case "snow1": case "snow2": case "snow3": return 6;
         case "mountain1": case "mountain2": case "mountain3": case "mountain4": return 7;
         case "savanna1": case "savanna2": case "green_savanna1": return 8;
         case "path1": case "path2": return 9;
         case "volcano1": case "volcano2": case "soot1": return 10;
         case "lava1": case "lava2": return 11;
         default: return -1;
      }
   }

   private void pokemon(Game game, Pokemon pokemon) {
      if (pokemon == null || pokemon.currOwSprite == null || pokemon.mapTiles != game.map.tiles
         || pokemon.inBattle || (pokemon.drawLower == null && pokemon.drawUpper == null)) return;
      boolean following = pokemon.drawLower != null && pokemon.drawLower.following
         || pokemon.drawUpper != null && pokemon.drawUpper.following;
      if (!pokemon.drawThisFrame && !following) return;
      if (actorModels.queuePokemon(pokemon)) {
         if (!pokemon.inWater) shadow(pokemon.position.x + 8f, pokemon.position.y + 7f, 5f, 3f);
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

   private void tree(Tile tile, float x, float y, String name) {
      if (!"on".equalsIgnoreCase(System.getProperty("pokewilds.models","off"))) {
         TextureRegion sprite=foliage[name.contains("tree4") || name.contains("snow")?1:name.contains("savanna")?2:0];
         if(sprite==null) sprite=tile.overSprite!=null?tile.overSprite:tile.sprite;
         float width=name.contains("large")?31:name.contains("savanna")?30:24;
         float height=width*sprite.getRegionHeight()/Math.max(1f,sprite.getRegionWidth());
         shadow(x,y,9,4); upright(sprite,x+0.0f-width/2,y,.2f,width,height,WHITE);
         return;
      }
      int variation = Math.floorMod((int)x * 31 + (int)y * 17, 4);
      float height = 32f + variation * 2f;
      boolean snow = name.contains("tree4") || tile.name.contains("snow");
      boolean savanna = name.contains("savanna");
      float radius = savanna ? 17f : name.contains("large") ? 13f : 10.5f;
      shadow(x + 3f, y - 2f, radius * 0.92f, radius * 0.52f);
      box(x - 2.2f, y - 2.2f, 0.15f, 4.4f, 4.4f, height * 0.55f,
         color(0.49f, 0.32f, 0.20f));
      if (snow) {
         crown(x, y, height * 0.38f, height * 0.82f, radius, 0.38f, 0.59f, 0.57f);
         crown(x, y, height * 0.63f, height, radius * 0.72f, 0.88f, 0.94f, 0.91f);
      } else if (savanna) {
         crown(x, y, height * 0.60f, height * 0.94f, radius, 0.49f, 0.63f, 0.24f);
      } else {
         crown(x, y, height * 0.34f, height * 0.83f, radius, 0.25f, 0.50f, 0.30f);
         crown(x, y, height * 0.61f, height, radius * 0.76f, 0.38f, 0.65f, 0.37f);
      }
   }

   private void rock(float x, float y, boolean ice) {
      shadow(x + 1.5f, y - 1f, 7f, 4f);
      crown(x, y, 0.1f, ice ? 15f : 10f, 7.5f,
         ice ? 0.67f : 0.54f, ice ? 0.86f : 0.57f, ice ? 0.90f : 0.53f);
   }

   /** A faceted 3D crown with a broad middle, separate shoulder and raised cap. */
   private void crown(float x, float y, float base, float top, float radius, float r, float g, float b) {
      Geometry mesh = batch(geometry, whiteTexture);
      int sides = 8;
      float middle = base + (top - base) * 0.43f;
      for (int i = 0; i < sides; i++) {
         float angle0 = i * MathUtils.PI2 / sides;
         float angle1 = (i + 1) * MathUtils.PI2 / sides;
         float x0 = MathUtils.cos(angle0) * radius;
         float z0 = MathUtils.sin(angle0) * radius;
         float x1 = MathUtils.cos(angle1) * radius;
         float z1 = MathUtils.sin(angle1) * radius;
         float light = 0.79f + 0.17f * MathUtils.cos(angle0 - 2.2f);
         float side = color(r * light, g * light, b * light);
         quad(mesh, x + x0 * 0.75f, base, -y + z0 * 0.75f,
            x + x1 * 0.75f, base, -y + z1 * 0.75f,
            x + x1, middle, -y + z1, x + x0, middle, -y + z0, white, side);
         float cap = color(Math.min(1f, r * (light + 0.24f)), Math.min(1f, g * (light + 0.24f)), Math.min(1f, b * (light + 0.24f)));
         triangle(mesh, x + x0, middle, -y + z0, x + x1, middle, -y + z1,
            x, top, -y, cap);
      }
   }

   private void box(float x, float y, float base, float width, float depth, float height, float tint) {
      Geometry mesh = batch(geometry, whiteTexture);
      float z = -y;
      float back = z - depth;
      quad(mesh, x, base, z, x + width, base, z, x + width, height, z, x, height, z, white, tint);
      quad(mesh, x + width, base, z, x + width, base, back, x + width, height, back, x + width, height, z, white, tint);
      quad(mesh, x, base, back, x, base, z, x, height, z, x, height, back, white, tint);
      quad(mesh, x + width, base, back, x, base, back, x, height, back, x + width, height, back, white, tint);
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
      for (Geometry geometry : groups.values()) {
         int count = geometry.vertices.size / FLOATS_PER_VERTEX;
         if (count == 0) continue;
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
         actorModels.render(camera, tint);
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
      if (atlas != null) atlas.dispose();
      if (foliageAtlas != null) foliageAtlas.dispose();
      if (whiteTexture != null) whiteTexture.dispose();
      shader = null;
      atlas = null;
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
