package com.pkmngen.game;

/** Pixel sampling is independent of the simulation's world grid. No GL required. */
public final class VisualSampling {
   public static final float WORLD_TILE_SIZE = 16f;
   private static final int MAX_CELLS = 1048576;
   public final int sampleWidth, sampleHeight, columns, rows;

   public VisualSampling(int width, int height, int sampleWidth, int sampleHeight) {
      if (width <= 0 || height <= 0 || sampleWidth <= 0 || sampleHeight <= 0)
         throw new IllegalArgumentException("Region and sample dimensions must be positive.");
      if (width % sampleWidth != 0 || height % sampleHeight != 0)
         throw new IllegalArgumentException("Region dimensions must be multiples of sampleWidth/sampleHeight; no pixels are silently discarded.");
      long count = (long)(width / sampleWidth) * (height / sampleHeight);
      if (count < 1 || count > MAX_CELLS)
         throw new IllegalArgumentException("Invalid or excessive sample count: " + count);
      this.sampleWidth = sampleWidth;
      this.sampleHeight = sampleHeight;
      columns = width / sampleWidth;
      rows = height / sampleHeight;
   }

   public int count() { return columns * rows; }

   /** Atlas rows grow downward, world Y upward; negative coordinates wrap too. */
   public int index(float worldX, float worldY) {
      if (!Float.isFinite(worldX) || !Float.isFinite(worldY))
         throw new IllegalArgumentException("World coordinates must be finite.");
      long col = (long)Math.floor((double)worldX / WORLD_TILE_SIZE);
      long row = (long)Math.floor(-(double)worldY / WORLD_TILE_SIZE);
      return (int)Math.floorMod(row, (long)rows) * columns
         + (int)Math.floorMod(col, (long)columns);
   }

   public int sourceX(int index) { checkIndex(index); return index % columns * sampleWidth; }
   public int sourceY(int index) { checkIndex(index); return index / columns * sampleHeight; }

   private void checkIndex(int index) {
      if (index < 0 || index >= count()) throw new IndexOutOfBoundsException("Sample " + index);
   }
}
