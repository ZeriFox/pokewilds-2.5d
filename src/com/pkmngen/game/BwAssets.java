package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Shared presentation assets. Tile state, collision and saved names are never changed.
 * Landscape crops and source credits: visual/landscape; B/W avatars: visual/unova. */
public final class BwAssets {
   private static BwAssets shared;
   private final Texture texture;
   private final Texture trainerTexture;
   private final Map<String, TextureRegion> regions = new HashMap<>();
   private final Map<String, TextureRegion[]> cells = new HashMap<>();
   private final Map<String, String[]> animations = new HashMap<>();
   private final Map<String, Float> speeds = new HashMap<>();
   private final Map<String, Float> anchorsX = new HashMap<>();
   private final Map<String, Float> anchorsY = new HashMap<>();
   private final Set<String> missing = new HashSet<>();

   private BwAssets() {
      texture = load("visual/landscape/world-atlas.png");
      JsonValue atlas = new JsonReader().parse(Gdx.files.internal("visual/landscape/world-atlas.json"));
      for (JsonValue entry = atlas.get("regions").child; entry != null; entry = entry.next) add(entry, texture);
      for (JsonValue entry = atlas.get("animations").child; entry != null; entry = entry.next) {
         animations.put(entry.name, entry.get("frames").asStringArray());
         speeds.put(entry.name, entry.getFloat("fps"));
      }
      // Only avatars are retained from this atlas. No old landscape-region fallback.
      trainerTexture = load("visual/unova/world-atlas.png");
      JsonValue trainers = new JsonReader().parse(Gdx.files.internal("visual/unova/world-atlas.json"));
      for (JsonValue entry = trainers.child; entry != null; entry = entry.next)
         if (entry.name.startsWith("trainer_")) add(entry, trainerTexture);
   }
   private static Texture load(String path) {
      Texture result = new Texture(Gdx.files.internal(path));
      result.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
      return result;
   }
   private void add(JsonValue entry, Texture atlas) {
      TextureRegion region = new TextureRegion(atlas, entry.getInt("x"), entry.getInt("y"),
         entry.getInt("width"), entry.getInt("height"));
      regions.put(entry.name, region);
      anchorsX.put(entry.name, entry.getFloat("anchorX", region.getRegionWidth() / 2f));
      anchorsY.put(entry.name, entry.getFloat("anchorY", 0f));
      int cols = Math.max(1, region.getRegionWidth() / 16), rows = Math.max(1, region.getRegionHeight() / 16);
      TextureRegion[] parts = new TextureRegion[cols * rows];
      for (int y = 0; y < rows; y++) for (int x = 0; x < cols; x++)
         parts[y * cols + x] = new TextureRegion(region, x * 16, y * 16,
            Math.min(16, region.getRegionWidth()), Math.min(16, region.getRegionHeight()));
      cells.put(entry.name, parts);
   }
   public static BwAssets get() { if (shared == null) shared = new BwAssets(); return shared; }
   public static void disposeShared() {
      if (shared != null) { shared.texture.dispose(); shared.trainerTexture.dispose(); shared = null; }
   }
   public TextureRegion named(String name) { return regions.get(name); }
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
   /** Stable world tiling at native 16px density, without per-frame allocations. */
   public TextureRegion cell(String name, float x, float y) {
      TextureRegion region = regions.get(name);
      if (region == null) return null;
      int cols = Math.max(1, region.getRegionWidth() / 16), rows = Math.max(1, region.getRegionHeight() / 16);
      return cells.get(name)[Math.floorMod((int)Math.floor(-y / 16f), rows) * cols
         + Math.floorMod((int)Math.floor(x / 16f), cols)];
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
