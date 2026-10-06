package com.pkmngen.game;

public final class VisualSamplingTest {
   private static int checks;
   private static void eq(int expected, int actual) {
      checks++;
      if (expected != actual) throw new AssertionError(expected + " != " + actual);
   }
   private static void rejects(Runnable action) {
      checks++;
      try { action.run(); } catch (IllegalArgumentException | IndexOutOfBoundsException expected) { return; }
      throw new AssertionError("Expected rejection");
   }
   public static void main(String[] args) {
      int[][] legacy = {{64,64},{32,32},{16,16},{16,32},{16,8},{8,32},{32,64},{16,64},{32,16}};
      float[] coordinates = {-1024,-65,-64,-63.99f,-32,-16,-15.99f,-1,-.01f,0,.01f,1,15.99f,16,32,63.99f,64,65,1024};
      for (int[] size : legacy) {
         int cols = Math.max(1,size[0]/16), rows = Math.max(1,size[1]/16);
         VisualSampling sample = new VisualSampling(size[0],size[1],Math.min(16,size[0]),Math.min(16,size[1]));
         eq(cols*rows,sample.count());
         for (float x : coordinates) for (float y : coordinates) {
            int old = Math.floorMod((int)Math.floor(-y/16f),rows)*cols + Math.floorMod((int)Math.floor(x/16f),cols);
            eq(old,sample.index(x,y));
            eq((old%cols)*16,sample.sourceX(old));
            eq((old/cols)*16,sample.sourceY(old));
         }
      }
      for (int resolution : new int[]{24,32,48,64,96,128,256}) {
         VisualSampling one = new VisualSampling(resolution,resolution,resolution,resolution);
         eq(1,one.count());
         for (float x : coordinates) for (float y : coordinates) eq(0,one.index(x,y));
      }
      VisualSampling mosaic = new VisualSampling(128,192,64,48);
      eq(8,mosaic.count());
      eq(0,mosaic.index(0,0)); eq(1,mosaic.index(16,0));
      eq(2,mosaic.index(0,-16)); eq(6,mosaic.index(0,16));
      eq(1,mosaic.index(-16,0)); eq(7,mosaic.index(-16,16));
      eq(0,mosaic.index(32,-64)); eq(64,mosaic.sourceX(7)); eq(144,mosaic.sourceY(7));
      rejects(() -> new VisualSampling(0,16,16,16));
      rejects(() -> new VisualSampling(16,16,0,16));
      rejects(() -> new VisualSampling(64,64,48,48));
      rejects(() -> new VisualSampling(16,16,32,32));
      rejects(() -> new VisualSampling(Integer.MAX_VALUE,Integer.MAX_VALUE,1,1));
      rejects(() -> mosaic.index(Float.NaN,0));
      rejects(() -> mosaic.index(0,Float.POSITIVE_INFINITY));
      rejects(() -> mosaic.sourceX(-1)); rejects(() -> mosaic.sourceY(8));
      System.out.println("VisualSampling: " + checks + " checks passed.");
   }
}
