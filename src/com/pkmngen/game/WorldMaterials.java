package com.pkmngen.game;

/** Semantic world appearance. No textures are loaded here and no tile, collision,
 * random generator or saved state is changed. BwAssets owns image resources;
 * JohtoRenderer builds geometry from these explicit roles. */
public final class WorldMaterials {
   private WorldMaterials() {}
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
   public static String objectName(Tile tile) {
      if (tile == null) return null;
      String upper = lower(tile.nameUpper), name = upper.isEmpty() ? lower(tile.name) : upper;
      BiomeProfiles.Profile profile = BiomeProfiles.visualForTile(tile);
      if (name.equals("solid") || name.contains("_hidden") || name.contains("nosprite")) return null;
      // Pure flooring is not an upright object. Authored floor puzzles remain
      // explicit ground details; suppressing them would hide live interactions.
      if ((name.contains("floor") || name.contains("carpet") || name.contains("rug"))
         && !name.contains("_cracked") && !name.contains("onpress") && !name.contains("switch")
         && !name.contains("warp") && !name.contains("hole") && !name.contains("pit")
         && !name.contains("stairs") && !name.contains("ramp")) return null;
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

   /** Geometry role, independent of source-pixel dimensions and ground name.
    * The selected upper object always takes precedence over the floor below it. */
   public enum Kind { NONE, HIDDEN, TREE, ROCK, VEGETATION, FURNITURE,
      WALL, WINDOW, DOOR, ROOF, FENCE, BRIDGE, GROUND_DETAIL, PROP }

   public static Kind kind(Tile tile, String key) {
      if (tile == null) return Kind.NONE;
      String lower = lower(tile.name), upper = lower(tile.nameUpper);
      String name = upper.isEmpty() ? lower : upper;
      if (name.equals("solid") || name.contains("_hidden") || name.contains("nosprite")) return Kind.HIDDEN;
      if (isTree(tile)) return Kind.TREE;
      if (key != null) {
         switch (key) {
            case "chair": case "couch": case "bed": case "desk": case "table":
            case "shelf": case "wardrobe": case "pot": return Kind.FURNITURE;
            case "warp": case "pressure_plate": case "cracked": case "volcanic_cracked":
            case "hole": case "steps": return Kind.GROUND_DETAIL;
            case "window": return tile.isSolid ? Kind.WINDOW : Kind.PROP;
            case "wall": return tile.isSolid ? Kind.WALL : Kind.PROP;
            case "door": case "door_locked": case "door_open": return Kind.DOOR;
            case "roof": return Kind.ROOF;
            case "fence_wood": case "ruin_gate": return Kind.FENCE;
            case "seedling": case "plant_growing": case "berry_full": case "berry_empty":
            case "berry_growing": case "flowers": case "flowers_fairy": return Kind.VEGETATION;
            default: break;
         }
         if (key.startsWith("rock") || key.endsWith("_rock") || key.equals("cave_stalagmite")) return Kind.ROCK;
         if (key.contains("grass") || key.contains("bush") || key.contains("cactus") || key.equals("aloe")) return Kind.VEGETATION;
         return Kind.PROP;
      }
      if (name.contains("bridge")) return Kind.BRIDGE;
      if (caveWall(tile) && VisualGeometry.buildingWall(lower, upper, tile.isSolid)) return Kind.WALL;
      return Kind.NONE;
   }

   /** Trees keep the authored whole sprite; saplings, stumps and berries are props. */
   public static boolean isTree(Tile tile) {
      if (tile == null) return false;
      String lower = lower(tile.name), upper = lower(tile.nameUpper);
      if (upper.contains("planted") || upper.contains("berrytree") || upper.contains("stump")) return false;
      return tile.isTree || lower.matches("tree[1-7]") || lower.startsWith("tree_large")
         || upper.matches("tree[1-7]") || upper.startsWith("tree_large") || upper.equals("tree_savanna1")
         || lower.startsWith("tree_fairy") || upper.startsWith("tree_fairy");
   }

   public static String treeKey(Tile tile) {
      String name = lower(tile.name) + " " + lower(tile.nameUpper);
      BiomeProfiles.Profile profile = BiomeProfiles.visualForTile(tile);
      return profile.id.equals("volcano") || profile.id.equals("graveyard") || profile.id.equals("desert") ? profile.decor("tree")
         : name.contains("tree4") || name.contains("snow") ? "tree_snow"
         : name.contains("savanna") ? "tree_dry" : name.contains("tree2") ? "tree_pine" : "tree";
   }

   public static boolean caveWall(Tile tile) {
      return tile != null && tile.isSolid && lower(tile.name).startsWith("cave")
         && lower(tile.nameUpper).isEmpty() && !lower(tile.name).contains("regi");
   }

   public static String wallFace(Tile tile) {
      return caveWall(tile) ? "cliff_dark" : (lower(tile.name) + " " + lower(tile.nameUpper)).contains("ruin") ? "ruin_wall" : "wall";
   }
   public static String wallCap(Tile tile) {
      return caveWall(tile) ? "mountain" : (lower(tile.name) + " " + lower(tile.nameUpper)).contains("ruin") ? "ruin_floor" : "wall_cap";
   }
   private static String lower(String value) { return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT); }
}
