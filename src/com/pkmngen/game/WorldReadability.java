package com.pkmngen.game;

import java.util.Locale;

/** Read-only presentation policy. Never modifies tiles, input, collision or RNG. */
public final class WorldReadability {
   private WorldReadability() {}

   /** Diagnostic comparison switch for this presentation step, not a legacy renderer. */
   public static boolean enabled() {
      return !"off".equalsIgnoreCase(System.getProperty("pokewilds.readability", "on"));
   }

   /** Only natural, dry upper surfaces may feather into one another. Structures,
    * puzzles, water/lava, ledges and traversal ramps always keep their full outline. */
   public static int materialRank(Tile tile) {
      if (tile == null || tile.isWater || tile.isLava || tile.isWaterfall || tile.isLedge
         || tile.isTidal || ModernWorldGenerator.isRamp(tile)) return -1;
      String name = tile.name == null ? "" : tile.name.toLowerCase(Locale.ROOT);
      if (name.contains("floor") || name.contains("house") || name.contains("interior")
         || name.contains("building") || name.contains("bridge") || name.contains("rug")
         || name.contains("carpet") || name.contains("cave") || name.contains("regi")
         || name.contains("mansion") || name.contains("ruin") || name.contains("puddle")
         || name.contains("hole") || name.contains("onpress") || name.contains("cracked")) return -1;
      String key = WorldMaterials.terrainName(tile);
      if (key.equals("grass") || key.equals("grass_light") || key.equals("grass_dry")) return 1;
      if (key.equals("path") || key.equals("dirt") || key.equals("graveyard_path")) return 2;
      if (key.equals("sand") || key.startsWith("desert_") && !key.contains("cliff")) return 3;
      if (key.equals("mountain")) return 4;
      if (key.equals("snow")) return 5;
      return -1;
   }

   public static boolean feather(Tile lower, Tile higher) {
      int a = materialRank(lower), b = materialRank(higher);
      return a >= 0 && b > a;
   }

   public static float ease(float value) {
      float x = Math.max(0, Math.min(1, value));
      return x * x * (3 - 2 * x);
   }
}
