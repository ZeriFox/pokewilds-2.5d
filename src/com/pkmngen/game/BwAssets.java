package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** World art with independent source-pixel sampling and world-space placement.
 * The original B/W atlas remains the default; custom entries override it by name.
 * TextureRegions are cached, read-only, and owned by this shared asset cache. */
public final class BwAssets {
   private static final String BASE_TEXTURE = "visual/unova/world-atlas.png";
   private static final String BASE_ATLAS = "visual/unova/world-atlas.json";
   private static final String OVERRIDES = "visual/custom/world-overrides.json";
   private static BwAssets shared;
   private final Map<String, Texture> textures = new LinkedHashMap<>();
   private final Map<String, TextureRegion> regions = new HashMap<>();
   private final Map<String, TextureRegion[]> cells = new HashMap<>();
   private final Map<String, VisualSampling> sampling = new HashMap<>();
   private final Map<TextureRegion, SpriteLayout> layouts = new IdentityHashMap<>();

   private BwAssets() {
      try {
         loadAtlas(Gdx.files.internal(BASE_ATLAS));
         FileHandle overrides = Gdx.files.internal(OVERRIDES);
         if (overrides.exists()) loadAtlas(overrides);
      } catch (RuntimeException error) {
         disposeTextures();
         throw error;
      }
   }

   private void loadAtlas(FileHandle file) {
      JsonValue root = new JsonReader().parse(file);
      if (!root.isObject()) throw new IllegalArgumentException(file + ": atlas must be an object.");
      JsonValue entries = root.get("regions");
      String defaultTexture = BASE_TEXTURE;
      if (entries == null) entries = root;
      else defaultTexture = root.getString("texture", BASE_TEXTURE);
      if (!entries.isObject()) throw new IllegalArgumentException(file + ": regions must be an object.");
      for (JsonValue entry = entries.child; entry != null; entry = entry.next) {
         try {
            loadRegion(entry, defaultTexture);
         } catch (RuntimeException error) {
            throw new IllegalArgumentException(file + ": invalid region '" + entry.name + "': " + error.getMessage(), error);
         }
      }
   }

   private void loadRegion(JsonValue entry, String defaultTexture) {
      if (!entry.isObject()) throw new IllegalArgumentException("Region must be an object.");
      Texture texture = texture(entry.getString("texture", defaultTexture));
      int x = integer(entry, "x", 0), y = integer(entry, "y", 0);
      int width = integer(entry, "width", -1), height = integer(entry, "height", -1);
      if (x < 0 || y < 0 || width <= 0 || height <= 0
         || (long)x + width > texture.getWidth() || (long)y + height > texture.getHeight())
         throw new IllegalArgumentException("Crop must be positive and inside its texture.");
      VisualSampling grid = new VisualSampling(width, height,
         integer(entry, "sampleWidth", Math.min(16, width)),
         integer(entry, "sampleHeight", Math.min(16, height)));
      SpriteLayout layout = new SpriteLayout(entry);
      TextureRegion region = new TextureRegion(texture, x, y, width, height);
      TextureRegion[] parts = new TextureRegion[grid.count()];
      for (int i = 0; i < parts.length; i++)
         parts[i] = new TextureRegion(region, grid.sourceX(i), grid.sourceY(i), grid.sampleWidth, grid.sampleHeight);
      TextureRegion previous = regions.put(entry.name, region);
      if (previous != null) layouts.remove(previous);
      layouts.put(region, layout);
      sampling.put(entry.name, grid);
      cells.put(entry.name, parts);
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

   /** Visual sizes are world units, anchors are fractions measured from left/bottom.
    * Missing fields preserve each renderer's existing size and anchor. */
   public static final class SpriteLayout {
      private final float worldWidth, worldHeight, anchorX, anchorY;
      public final float offsetX, offsetY, elevation;
      public final boolean customized;

      private SpriteLayout(JsonValue entry) {
         worldWidth = optionalPositive(entry, "worldWidth");
         worldHeight = optionalPositive(entry, "worldHeight");
         anchorX = optionalAnchor(entry, "anchorX");
         anchorY = optionalAnchor(entry, "anchorY");
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

   /** A sample can contain 16, 32, 48, 64, ... source pixels while occupying one
    * unchanged world tile. The region's sample grid defines the repeat pattern. */
   public TextureRegion cell(String name, float x, float y) {
      VisualSampling grid = sampling.get(name);
      return grid == null ? null : cells.get(name)[grid.index(x, y)];
   }

   public TextureRegion trainer(Player player, float seconds, boolean moving) {
      return trainer(player == null ? "gold" : player.character, player == null ? "down" : player.dirFacing, seconds, moving);
   }

   public TextureRegion trainer(String character, String direction, float seconds, boolean moving) {
      String name = character == null ? "" : character.toLowerCase(Locale.ROOT);
      boolean female = name.equals("kris") || name.equals("lyra") || name.equals("leaf") || name.equals("may")
         || name.equals("dawn") || name.equals("hilda") || name.equals("rosa") || name.equals("serena")
         || name.equals("selene") || name.equals("gloria") || name.equals("elaine") || name.equals("summer");
      String dir = "up".equals(direction) || "left".equals(direction) || "right".equals(direction) ? direction : "down";
      // The two B/W protagonists replace the original avatar skins by gender;
      // their original character identity and save field remain untouched.
      int frame = moving ? new int[]{0, 1, 2, 1}[(int)(seconds * 8f) & 3] : 1;
      return named("trainer_" + (female ? "female" : "male") + "_walk_" + dir + "_" + frame);
   }

   public TextureRegion terrain(Tile tile) { return cell(terrainName(tile), tile.position.x, tile.position.y); }

   static String terrainName(Tile tile) {
      String name = tile.name == null ? "" : tile.name.toLowerCase(Locale.ROOT);
      String biome = tile.biome == null ? "" : tile.biome;
      if (tile.isLava || name.startsWith("lava")) return "water";
      if (tile.isWater || name.startsWith("water") || name.startsWith("tidal")) return name.equals("water1") ? "water" : "water_shallow";
      if (name.contains("bridge")) return "wood_floor";
      if (name.contains("rug") || name.contains("carpet")) return "rug";
      if (name.contains("floor") || name.contains("interior") || name.startsWith("house")) return name.contains("cave") ? "mountain" : "wood_floor";
      if (name.contains("snow") || biome.contains("snow")) return "snow";
      if (name.startsWith("volcano") || name.startsWith("soot") || biome.equals("volcano")) return name.equals("volcano2") ? "volcano_path" : "basalt";
      if (name.startsWith("cave") || name.startsWith("mountain") || name.startsWith("rock") || name.startsWith("ledge")) return "mountain";
      if (name.startsWith("sand") || name.startsWith("desert")) return "sand";
      if (name.startsWith("ruin") || name.contains("regi") || name.startsWith("dungeon")) return "ruin_floor";
      if (name.startsWith("path") || name.startsWith("ground")) return "path";
      if (name.contains("savanna") || biome.contains("savanna")) return "grass_dry";
      if (name.startsWith("green") || name.startsWith("grass") || name.startsWith("flower") || name.startsWith("tree")
         || name.startsWith("bush") || name.isEmpty()) return biome.contains("forest") ? "grass" : "grass_light";
      // Unknown mechanical tiles (puzzles, warp marks, traps) keep their cues.
      return null;
   }

   public TextureRegion object(Tile tile) {
      String name = tile.nameUpper == null ? "" : tile.nameUpper.toLowerCase(Locale.ROOT);
      if (name.isEmpty()) name = tile.name == null ? "" : tile.name.toLowerCase(Locale.ROOT);
      if (name.contains("planted") || name.contains("berrytree")) return null;
      if (name.startsWith("rock") && !name.contains("cracked")) return named(name.contains("ice") ? "rock_ice" : "rock");
      if (name.contains("flower")) return named(name.contains("fairy") ? "flowers_fairy" : "flowers");
      if (name.startsWith("grass")) return named(name.contains("savanna") ? "grass_dry_tuft" : "grass_tuft");
      if (name.contains("plant") || name.contains("bush")) return named("tree_top");
      if (name.contains("stool") || name.contains("chair")) return named("chair");
      if (name.contains("couch") || name.contains("bed")) return named("couch");
      if (name.contains("desk") || name.contains("table") || name.contains("shelf") || name.contains("vanity")) return named("desk");
      if (name.contains("wardrobe")) return named("door_red");
      return null;
   }
}
