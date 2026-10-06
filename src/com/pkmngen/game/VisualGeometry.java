package com.pkmngen.game;

import java.util.Locale;

/** Pure presentation math. Never changes the map, collisions or simulation. */
public final class VisualGeometry {
   private VisualGeometry() {}

   /** Fixed battle slots: friendly bottom-left, opponent top-right.
    * Select an actual PMD diagonal pose; do not rotate a flat sprite to face it. */
   public static String battleFacing(boolean friendly) {
      return friendly ? "up-right" : "down-left";
   }

   /** Uniform fitting: clamping height alone would squash tall sprites. */
   public static float fitScale(float width, float height, float maxWidth, float maxHeight) {
      if (!Float.isFinite(width) || !Float.isFinite(height)
         || !Float.isFinite(maxWidth) || !Float.isFinite(maxHeight)
         || width <= 0 || height <= 0 || maxWidth <= 0 || maxHeight <= 0)
         throw new IllegalArgumentException("Sprite and bounding-box dimensions must be finite and positive.");
      return Math.min(maxWidth / width, maxHeight / height);
   }

   /** Non-overlapping 3x3 footprint, split at 0, 3, 13, 16 world units.
    * Original Tile convention: N=down, S=up, E=left, W=right.
    * A bit is one cap rectangle. Its exposed sides form a closed cliff rim. */
   public static int cliffMask(String upper, String direction, boolean solid) {
      String name = upper == null ? "" : upper.toLowerCase(Locale.ROOT);
      int edges = 0;
      if (name.startsWith("ledges3")) {
         String[] parts = name.split("_");
         if (parts.length > 1 && !parts[1].equals("none")) {
            for (char c : parts[1].toCharArray()) {
               edges |= c == 'n' ? 1 : c == 's' ? 2 : c == 'e' ? 4 : c == 'w' ? 8 : 0;
            }
         }
      } else {
         edges = "down".equals(direction) ? 1 : "up".equals(direction) ? 2
            : "left".equals(direction) ? 4 : "right".equals(direction) ? 8 : 0;
      }
      if (edges == 0) return solid ? 511 : 0;
      boolean inner = name.contains("_inner");
      int mask = 0;
      for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
         boolean horizontal = row == 0 && (edges & 1) != 0 || row == 2 && (edges & 2) != 0;
         boolean vertical = col == 0 && (edges & 4) != 0 || col == 2 && (edges & 8) != 0;
         if (inner ? horizontal && vertical : horizontal || vertical) mask |= 1 << (row * 3 + col);
      }
      return mask;
   }

   public static boolean occupied(int mask, int col, int row) {
      return col >= 0 && col < 3 && row >= 0 && row < 3 && (mask & (1 << (row * 3 + col))) != 0;
   }

   public static float boundary(int index) {
      switch (index) {
         case 0: return 0f;
         case 1: return 3f;
         case 2: return 13f;
         case 3: return 16f;
         default: throw new IllegalArgumentException("Cliff boundary index must be 0..3.");
      }
   }

   /** A solid item standing on house5_floor1 is not a 19-unit-high house wall. */
   public static boolean buildingWall(String lower, String upper, boolean solid) {
      return solid && (wallPart(lower) || wallPart(upper));
   }

   private static boolean wallPart(String value) {
      String name = value == null ? "" : value.toLowerCase(Locale.ROOT);
      if (name.contains("floor") || name.contains("door") || name.contains("roof")
         || name.contains("stairs") || name.contains("ramp") || name.contains("bridge")) return false;
      return name.contains("wall") || name.matches("house[0-9]+(?:_.*)?")
         || name.contains("cave") && !name.contains("regi");
   }
}
