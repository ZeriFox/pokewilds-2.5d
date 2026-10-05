package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Verified Black/White textures. Regions are read-only and share one GPU atlas.
 * Source sheets, exact crops, preparation and credits live in visual/unova. */
public final class BwAssets {
   private static BwAssets shared;
   private final Texture texture;
   private final Map<String, TextureRegion> regions = new HashMap<>();
   private final Map<String, TextureRegion[]> cells = new HashMap<>();

   private BwAssets() {
      texture = new Texture(Gdx.files.internal("visual/unova/world-atlas.png"));
      texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
      JsonValue atlas = new JsonReader().parse(Gdx.files.internal("visual/unova/world-atlas.json"));
      for (JsonValue entry = atlas.child; entry != null; entry = entry.next) {
         TextureRegion region = new TextureRegion(texture, entry.getInt("x"), entry.getInt("y"),
            entry.getInt("width"), entry.getInt("height"));
         regions.put(entry.name, region);
         int cols = Math.max(1, region.getRegionWidth() / 16), rows = Math.max(1, region.getRegionHeight() / 16);
         TextureRegion[] parts = new TextureRegion[cols * rows];
         for (int y = 0; y < rows; y++) for (int x = 0; x < cols; x++)
            parts[y * cols + x] = new TextureRegion(region, x * 16, y * 16,
               Math.min(16, region.getRegionWidth()), Math.min(16, region.getRegionHeight()));
         cells.put(entry.name, parts);
      }
   }

   public static BwAssets get() { if (shared == null) shared = new BwAssets(); return shared; }
   public static void disposeShared() { if (shared != null) { shared.texture.dispose(); shared = null; } }
   public TextureRegion named(String name) { return regions.get(name); }

   /** Stable world tiling, preserving the original texture's pixel density. */
   public TextureRegion cell(String name, float x, float y) {
      TextureRegion region = regions.get(name);
      if (region == null) return null;
      int cols = Math.max(1, region.getRegionWidth() / 16), rows = Math.max(1, region.getRegionHeight() / 16);
      return cells.get(name)[Math.floorMod((int)Math.floor(-y / 16f), rows) * cols
         + Math.floorMod((int)Math.floor(x / 16f), cols)];
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
