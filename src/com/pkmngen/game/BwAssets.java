package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Shared landscape/animated presentation assets with optional custom sampling.
 * Tile state, collision and saves never change. Landscape credits: visual/landscape;
 * B/W avatars: visual/unova. Cached regions are read-only and owned here. */
public final class BwAssets {
   private static final String BASE_TEXTURE = "visual/stardew/world-atlas.png";
   private static final String BASE_ATLAS = "visual/stardew/world-atlas.json";
   private static final String TRAINER_TEXTURE = "visual/unova/world-atlas.png";
   private static final String TRAINER_ATLAS = "visual/unova/world-atlas.json";
   private static final String OVERRIDES = "visual/custom/world-overrides.json";
   private static BwAssets shared;
   private final Map<String, Texture> textures = new LinkedHashMap<>();
   private final Map<String, TextureRegion> regions = new HashMap<>();
   private final Map<String, TextureRegion[]> cells = new HashMap<>();
   private final Map<String, VisualSampling> sampling = new HashMap<>();
   private final Map<TextureRegion, SpriteLayout> layouts = new IdentityHashMap<>();
   private final Map<String, String[]> animations = new HashMap<>();
   private final Map<String, Float> speeds = new HashMap<>();
   private final Map<String, Float> anchorsX = new HashMap<>();
   private final Map<String, Float> anchorsY = new HashMap<>();
   private final Set<String> missing = new HashSet<>();

   private BwAssets() {
      try {
         loadAtlas(Gdx.files.internal(BASE_ATLAS), BASE_TEXTURE, true, false);
         // The previous world atlas contributes avatars only, never old scenery.
         loadAtlas(Gdx.files.internal(TRAINER_ATLAS), TRAINER_TEXTURE, true, true);
         FileHandle overrides = Gdx.files.internal(OVERRIDES);
         if (overrides.exists()) loadAtlas(overrides, BASE_TEXTURE, false, false);
      } catch (RuntimeException error) {
         disposeTextures();
         throw error;
      }
   }

   private void loadAtlas(FileHandle file, String defaultTexture, boolean builtin, boolean trainersOnly) {
      JsonValue root = new JsonReader().parse(file);
      if (!root.isObject()) throw new IllegalArgumentException(file + ": atlas must be an object.");
      int schema = integer(root,"schemaVersion",1);
      if(schema<1||schema>2)throw new IllegalArgumentException(file+": unsupported material schema "+schema);
      if(schema==2) {
         JsonValue contract=root.get("coordinateContract");
         String expected=builtin?"source-pixels":"normalized";
         if(contract==null||!expected.equals(contract.getString("anchorUnit","")))
            throw new IllegalArgumentException(file+": schema 2 anchorUnit must be "+expected);
      }
      JsonValue entries = root.get("regions");
      if (entries == null) entries = root;
      else defaultTexture = root.getString("texture", defaultTexture);
      if (!entries.isObject()) throw new IllegalArgumentException(file + ": regions must be an object.");
      for (JsonValue entry = entries.child; entry != null; entry = entry.next) {
         if (trainersOnly && !entry.name.startsWith("trainer_")) continue;
         try {
            loadRegion(entry, defaultTexture, builtin);
         } catch (RuntimeException error) {
            throw new IllegalArgumentException(file + ": invalid region '" + entry.name + "': " + error.getMessage(), error);
         }
      }
      JsonValue sequences = builtin && !trainersOnly ? root.get("animations") : null;
      if (sequences != null) for (JsonValue entry = sequences.child; entry != null; entry = entry.next) {
         String[] frames = entry.get("frames").asStringArray();
         float fps = number(entry, "fps", 0);
         if (frames.length == 0 || fps <= 0) throw new IllegalArgumentException(file + ": invalid animation '" + entry.name + "'.");
         for (String frame : frames) if (!regions.containsKey(frame))
            throw new IllegalArgumentException(file + ": missing animation frame '" + frame + "'.");
         animations.put(entry.name, frames);
         speeds.put(entry.name, fps);
      }
   }

   private void loadRegion(JsonValue entry, String defaultTexture, boolean builtin) {
      if (!entry.isObject()) throw new IllegalArgumentException("Region must be an object.");
      Texture texture = texture(entry.getString("texture", defaultTexture));
      int x = integer(entry, "x", 0), y = integer(entry, "y", 0);
      int width = integer(entry, "width", -1), height = integer(entry, "height", -1);
      if (x < 0 || y < 0 || width <= 0 || height <= 0
         || (long)x + width > texture.getWidth() || (long)y + height > texture.getHeight())
         throw new IllegalArgumentException("Crop must be positive and inside its texture.");
      int sampleWidth = integer(entry, "sampleWidth", Math.min(16, width));
      int sampleHeight = integer(entry, "sampleHeight", Math.min(16, height));
      int gridWidth = width, gridHeight = height;
      if (builtin && entry.get("sampleWidth") == null && entry.get("sampleHeight") == null) {
         // Preserve the exact old 16px cell mosaic for irregular built-in props.
         // named() still exposes their complete image. Custom regions below must
         // divide exactly: no custom source pixels are silently discarded.
         gridWidth = Math.max(1, width / 16) * sampleWidth;
         gridHeight = Math.max(1, height / 16) * sampleHeight;
      }
      VisualSampling grid = new VisualSampling(gridWidth, gridHeight, sampleWidth, sampleHeight);
      SpriteLayout layout = new SpriteLayout(entry, width, height, builtin);
      TextureRegion region = new TextureRegion(texture, x, y, width, height);
      TextureRegion[] parts = new TextureRegion[grid.count()];
      for (int i = 0; i < parts.length; i++)
         parts[i] = new TextureRegion(region, grid.sourceX(i), grid.sourceY(i), grid.sampleWidth, grid.sampleHeight);
      TextureRegion previous = regions.put(entry.name, region);
      if (previous != null) layouts.remove(previous);
      layouts.put(region, layout);
      sampling.put(entry.name, grid);
      cells.put(entry.name, parts);
      // Public legacy anchor access remains source pixels for existing consumers.
      anchorsX.put(entry.name, layout.anchorX(.5f) * width);
      anchorsY.put(entry.name, layout.anchorY(0f) * height);
      if (!builtin) {
         // Replacing an animation alias is an explicit static override; replacing
         // an individual frame instead preserves the original animation sequence.
         animations.remove(entry.name);
         speeds.remove(entry.name);
      }
   }

   private Texture texture(String path) {
      if (path == null || path.isEmpty()) throw new IllegalArgumentException("Missing texture path.");
      path = path.replace('\\', '/');
      if (path.startsWith("/") || path.contains(":") || path.equals("..") || path.startsWith("../")
         || path.contains("/../") || path.endsWith("/.."))
         throw new IllegalArgumentException("Texture paths must be relative internal asset paths.");
      Texture texture = textures.get(path);
      if (texture == null) {
         texture = new Texture(Gdx.files.internal(path));
         // Register ownership before setting GL state so constructor failures also clean up.
         textures.put(path, texture);
         texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
         texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
      }
      return texture;
   }

   private void disposeTextures() {
      for (Texture texture : textures.values()) texture.dispose();
      textures.clear();
      regions.clear();
      cells.clear();
      sampling.clear();
      layouts.clear();
      animations.clear();
      speeds.clear();
      anchorsX.clear();
      anchorsY.clear();
      missing.clear();
   }

   private static int integer(JsonValue object, String key, int fallback) {
      JsonValue value = object.get(key);
      if (value == null) return fallback;
      double number = value.isNumber() ? value.asDouble() : Double.NaN;
      if (!Double.isFinite(number) || number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)
         throw new IllegalArgumentException(key + " must be an integer.");
      return (int)number;
   }

   private static float number(JsonValue object, String key, float fallback) {
      JsonValue value = object.get(key);
      if (value == null) return fallback;
      float result = value.isNumber() ? value.asFloat() : Float.NaN;
      if (!Float.isFinite(result)) throw new IllegalArgumentException(key + " must be finite.");
      return result;
   }

   private static float optionalPositive(JsonValue object, String key) {
      float result = number(object, key, Float.NaN);
      if (!Float.isNaN(result) && result <= 0f) throw new IllegalArgumentException(key + " must be positive.");
      return result;
   }

   private static float optionalAnchor(JsonValue object, String key) {
      float result = number(object, key, Float.NaN);
      if (!Float.isNaN(result) && (result < 0f || result > 1f))
         throw new IllegalArgumentException(key + " must be in [0, 1].");
      return result;
   }

   /** Built-in landscape metadata measures anchors in source pixels. Custom
    * metadata is deliberately a separate format with normalized [0,1] anchors. */
   private static float pixelAnchor(JsonValue entry, String key, int size) {
      float pixels = number(entry, key, Float.NaN);
      if (Float.isNaN(pixels)) return Float.NaN;
      if (pixels < 0 || pixels > size) throw new IllegalArgumentException(key + " must lie inside its built-in region.");
      return pixels / size;
   }

   /** Visual sizes are world units, anchors are fractions measured from left/bottom.
    * Missing fields preserve each renderer's existing size and anchor. */
   public static final class SpriteLayout {
      private final float worldWidth, worldHeight, anchorX, anchorY;
      public final float offsetX, offsetY, elevation;
      public final boolean customized;

      private SpriteLayout(JsonValue entry, int width, int height, boolean builtin) {
         worldWidth = optionalPositive(entry, "worldWidth");
         worldHeight = optionalPositive(entry, "worldHeight");
         anchorX = builtin ? pixelAnchor(entry, "anchorX", width) : optionalAnchor(entry, "anchorX");
         anchorY = builtin ? pixelAnchor(entry, "anchorY", height) : optionalAnchor(entry, "anchorY");
         offsetX = number(entry, "offsetX", 0f);
         offsetY = number(entry, "offsetY", 0f);
         elevation = number(entry, "elevation", 0f);
         customized = !Float.isNaN(worldWidth) || !Float.isNaN(worldHeight)
            || !Float.isNaN(anchorX) || !Float.isNaN(anchorY)
            || offsetX != 0f || offsetY != 0f || elevation != 0f;
      }

      public float width(TextureRegion region, float fallback) {
         if (!Float.isNaN(worldWidth)) return worldWidth;
         return Float.isNaN(worldHeight) ? fallback : worldHeight * region.getRegionWidth() / region.getRegionHeight();
      }
      public float height(TextureRegion region, float fallback) {
         if (!Float.isNaN(worldHeight)) return worldHeight;
         return Float.isNaN(worldWidth) ? fallback : worldWidth * region.getRegionHeight() / region.getRegionWidth();
      }
      public float anchorX(float fallback) { return Float.isNaN(anchorX) ? fallback : anchorX; }
      public float anchorY(float fallback) { return Float.isNaN(anchorY) ? fallback : anchorY; }
   }

   public static BwAssets get() { if (shared == null) shared = new BwAssets(); return shared; }
   public static void disposeShared() { if (shared != null) { shared.disposeTextures(); shared = null; } }
   public TextureRegion named(String name) { return regions.get(name); }
   public SpriteLayout layout(TextureRegion region) { return layouts.get(region); }
   public float worldWidth(TextureRegion region, float fallback) {
      SpriteLayout layout = layouts.get(region);
      return layout == null ? fallback : layout.width(region, fallback);
   }
   public float worldHeight(TextureRegion region, float fallback) {
      SpriteLayout layout = layouts.get(region);
      return layout == null ? fallback : layout.height(region, fallback);
   }
   /** Missing props stay invisible. Keep enough context to diagnose a real asset gap. */
   public void reportMissing(String key, Tile tile, String timeOfDay) {
      String biome = BiomeProfiles.visualForTile(tile).id;
      String ground = tile == null ? "<none>" : String.valueOf(tile.name);
      String object = tile == null ? "<none>" : String.valueOf(tile.nameUpper);
      String message = "asset=" + key + " biome=" + biome + " tile=" + ground
         + " object=" + object + " time=" + timeOfDay;
      if (missing.size() < 256 && missing.add(message)) Gdx.app.error("ModernAssets", message);
   }
   public float anchorX(String name) { return anchorsX.getOrDefault(name, 0f); }
   public float anchorY(String name) { return anchorsY.getOrDefault(name, 0f); }
   /** Frame selection is pure; negative/invalid clocks cannot escape the frame array. */
   public TextureRegion namedAnimated(String name, float seconds) { return named(frameName(name, seconds)); }
   private String frameName(String name, float seconds) {
      String[] frames = animations.get(name);
      if (frames == null) return name;
      float time = Float.isFinite(seconds) ? seconds : 0f;
      return frames[Math.floorMod((int)Math.floor(time * speeds.get(name)), frames.length)];
   }
   /** Source-pixel samples occupy the same 16-unit world cells at any resolution. */
   public TextureRegion cell(String name, float x, float y) {
      VisualSampling grid = sampling.get(name);
      return grid == null ? null : cells.get(name)[grid.index(x, y)];
   }
   public TextureRegion cell(String name, float x, float y, float seconds) {
      return cell(frameName(name, seconds), x, y);
   }
   public TextureRegion trainer(Player player, float seconds, boolean moving) {
      return trainer(player == null ? "gold" : player.character, player == null ? "down" : player.dirFacing, seconds, moving);
   }
   public TextureRegion trainer(String character, String direction, float seconds, boolean moving) {
      String name = lower(character);
      boolean female = name.equals("kris") || name.equals("lyra") || name.equals("leaf") || name.equals("may")
         || name.equals("dawn") || name.equals("hilda") || name.equals("rosa") || name.equals("serena")
         || name.equals("selene") || name.equals("gloria") || name.equals("elaine") || name.equals("summer");
      String dir = "up".equals(direction) || "left".equals(direction) || "right".equals(direction) ? direction : "down";
      int step = (int)(seconds * 8f) & 3;
      int frame = moving ? (step == 3 ? 1 : step) : 1;
      return named("trainer_" + (female ? "female" : "male") + "_walk_" + dir + "_" + frame);
   }
   public TextureRegion terrain(Tile tile) { return terrain(tile, 0f); }
   /** Total terrain mapping: even unknown biome tiles use an explicit modern material. */
   public TextureRegion terrain(Tile tile, float seconds) {
      String name = terrainName(tile);
      TextureRegion result = cell(name, tile == null || tile.position == null ? 0 : tile.position.x,
         tile == null || tile.position == null ? 0 : tile.position.y, seconds);
      return result != null ? result : named("grass_light");
   }
   public static String terrainName(Tile tile) {
      if (tile == null) return "grass_light";
      String name = lower(tile.name);
      BiomeProfiles.Profile profile = BiomeProfiles.visualForTile(tile);
      if (name.contains("bridge")) return "wood_floor";
      if (tile.isLava || name.contains("lava")) return profile.string("fluid", "lava", "lava_bright");
      // Tidal flats and puddles retain a visible dry bed. The renderer adds their
      // shallow water separately; isTidal is walkable and is not deep water.
      if (tile.isWater || name.startsWith("water"))
         return profile.string("fluid", name.equals("water1") || name.contains("waterfall") ? "water" : "shallow", "water_shallow");
      if (name.contains("rug") || name.contains("carpet")) return "rug";
      if (name.contains("pkmnmansion") || name.contains("regi") || name.contains("ruin") || name.contains("dungeon")) return "tile_floor";
      if (name.contains("floor") || name.contains("interior") || name.startsWith("house") || name.startsWith("building"))
         return name.contains("cave") ? "cave_floor" : "wood_floor";
      if (name.contains("snow") || name.startsWith("ice")) return "snow";
      if (name.startsWith("soot")) return profile.terrain("ash");
      if (name.startsWith("volcano")) {
         if (name.equals("volcano2")) return profile.terrain("path");
         // Coarse, stable patches belong to the material, never to gameplay RNG.
         int x = tile.position == null ? 0 : Math.floorDiv((int)Math.floor(tile.position.x), 64);
         int y = tile.position == null ? 0 : Math.floorDiv((int)Math.floor(tile.position.y), 64);
         int patch = (x * 73428767) ^ (y * 912931);
         return profile.terrain(Math.floorMod(patch, 5) == 0 ? "path" : "ground");
      }
      if (name.startsWith("cave") || name.equals("black1") || name.equals("blank1")) return "cave_floor";
      if (name.startsWith("ledge_grass")) return profile.terrain("ground");
      if (name.startsWith("mountain") || name.startsWith("rock") || name.startsWith("ledge")) return profile.terrain("rock");
      if (name.startsWith("path") || name.startsWith("ground")) return profile.terrain("path");
      return profile.terrain("ground");
   }
   public TextureRegion object(Tile tile) { return object(tile, 0f); }
   public TextureRegion object(Tile tile, float seconds) {
      String key = objectName(tile);
      return key == null ? null : namedAnimated(key, seconds);
   }
   /** Visual identity only. Callers still use the original flags for geometry/collision.
    * Null means a pure surface or an intentionally invisible script marker. */
   public static String objectName(Tile tile) {
      if (tile == null) return null;
      String upper = lower(tile.nameUpper), name = upper.isEmpty() ? lower(tile.name) : upper;
      BiomeProfiles.Profile profile = BiomeProfiles.visualForTile(tile);
      if (name.equals("solid") || name.contains("_hidden") || name.contains("nosprite")) return null;
      if (name.contains("berrytree")) return name.contains("full") ? "berry_full" : name.contains("empty") ? "berry_empty" : "berry_growing";
      if (name.contains("planted")) return "seedling";
      if (name.contains("mansion_key")) return "key";
      if (name.contains("ultraball")) return "ultraball";
      if (name.contains("pokeball")) return "pokeball";
      if (name.contains("pokedoll") || name.contains("plush")) return "doll";
      if (name.contains("regi") || name.contains("volcarona") || name.contains("spiritomb") || name.contains("overw")) return "statue";
      if (name.contains("campfire") || name.contains("torch")) return name.contains("__off") ? "stump" : "fire";
      if (name.contains("lavafall")) return profile.string("fluid", "lava", "lava_bright");
      if (name.contains("waterfall")) return profile.string("fluid", "water", "water");
      if (name.contains("kiln")) return "kiln";
      if (name.contains("chimney")) return "chimney";
      if (name.contains("machine") || name.contains("fossilreviver") || name.contains("pokecenter")) return name.contains("__off") ? "machine" : "machine_active";
      if (name.contains("cables")) return "wall";
      if (name.contains("warp")) return "warp";
      if (name.contains("onpress") || name.contains("switch")) return "pressure_plate";
      if (name.contains("_cracked")) return profile.id.equals("volcano") ? "volcanic_cracked" : "cracked";
      if (name.contains("stairs") || name.contains("ramp")) return profile.cliff("ramp");
      if (name.contains("hole") || name.contains("pit")) return "hole";
      if (name.contains("sign")) return "sign";
      if (name.contains("chest")) return "chest";
      if (name.contains("gravestone") || name.contains("keystone")) return profile.decor("gravestone");
      if (name.contains("statue") || name.contains("pedistal") || name.contains("pedestal")) return "statue";
      if (name.contains("stalagmite")) return "cave_stalagmite";
      if (name.contains("rock") || name.contains("rubble") || name.contains("block")) return name.contains("ice") ? "rock_ice" : profile.decor("rock");
      if (name.contains("cactus")) return profile.id.equals("desert") ? "desert_cactus" : "cactus";
      if (name.contains("aloe")) return "aloe";
      if (name.contains("tree")) return name.contains("snow") || name.equals("tree4") ? "tree_snow" : profile.decor("tree");
      if (name.contains("flower")) return name.contains("fairy") ? "flowers_fairy" : "flowers";
      if (name.contains("grass")) return profile.decor("grass");
      if (name.contains("bush")) return profile.decor("bush");
      if (name.contains("plant") || name.contains("potted") || name.equals("pot1")) return "pot";
      if (name.contains("stool") || name.contains("chair")) return "chair";
      if (name.contains("sleeping_bag") || name.contains("bed")) return "bed";
      if (name.contains("couch")) return "couch";
      if (name.contains("shelf")) return "shelf";
      if (name.contains("wardrobe")) return "wardrobe";
      if (name.contains("desk") || name.contains("vanity") || name.contains("gym")) return "desk";
      if (name.contains("table")) return "table";
      if (name.contains("window")) return "window";
      if (name.contains("picture")) return "sign";
      if (name.contains("door") || name.contains("locked")) return name.contains("locked") ? "door_locked" : "door_open";
      if (name.contains("gate")) return "ruin_gate";
      if (name.contains("fence")) return "fence_wood";
      if (name.contains("roof")) return "roof";
      if (name.contains("floor") || name.contains("carpet") || name.contains("rug") || name.contains("bridge") || name.contains("ledge")) return null;
      if (name.contains("wall") || name.contains("pkmnmansion") || name.contains("house") || name.contains("building") || name.contains("pillar")) return "wall";
      // Script markers and missing texture identities must not become visible
      // placeholder ghosts, stones or plates, especially during night effects.
      if (!upper.isEmpty()) return null;
      return null;
   }
   private static String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
}
