package com.pkmngen.game;

/** Standalone deterministic regression checks, no GPU or game state required. */
public final class VisualGeometryTest {
   private static int checks;
   private static void check(boolean ok, String why) {
      checks++;
      if (!ok) throw new AssertionError(why);
   }
   private static void near(float a, float b, String why) { check(Math.abs(a-b) < .0001f, why); }
   public static void main(String[] args) {
      check(VisualGeometry.battleFacing(true).equals("up-right"), "friendly faces opponent");
      check(VisualGeometry.battleFacing(false).equals("down-left"), "opponent faces friendly");
      for (int width : new int[]{8,16,24,32,48,64,96,128,256}) {
         for (int height : new int[]{8,16,24,32,48,64,96,128,256}) {
            float scale = VisualGeometry.fitScale(width,height,18,25);
            check(width*scale <= 18.0001f && height*scale <= 25.0001f, "fits bounds");
            near(width*scale/(height*scale), (float)width/height, "keeps aspect ratio");
            near(scale/2,VisualGeometry.fitScale(width*2,height*2,18,25),"HD replacement keeps size");
         }
      }
      near(VisualGeometry.fitScale(16,32,16,25),25f/32,"tall grass is not squashed");
      for (float bad : new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY}) {
         boolean rejected=false;
         try { VisualGeometry.fitScale(bad,16,16,25); } catch(IllegalArgumentException e) { rejected=true; }
         check(rejected,"reject invalid dimension");
      }
      check(VisualGeometry.cliffMask("ledges3_none",null,false)==0,"walkable ground stays open");
      check(VisualGeometry.cliffMask("ledges3_none",null,true)==511,"solid none block stays visible");
      String[] tokens={"N","S","E","W","NE","NW","SE","SW","NS","EW","NSEW"};
      for(String token:tokens) {
         int mask=VisualGeometry.cliffMask("ledges3_"+token,null,true);
         check(mask==VisualGeometry.cliffMask("ledges3snow_"+token.toLowerCase(),null,true),"case/biome mask");
         check(mask>0 && mask<=511,"valid mask");
         check(!VisualGeometry.occupied(mask,1,1),"rim never fills walkable centre");
         float area=0;
         for(int r=0;r<3;r++)for(int c=0;c<3;c++)if(VisualGeometry.occupied(mask,c,r))
            area+=(VisualGeometry.boundary(c+1)-VisualGeometry.boundary(c))*(VisualGeometry.boundary(r+1)-VisualGeometry.boundary(r));
         check(area>0 && area<=156,"cap rectangles do not overlap");
      }
      for(String token:new String[]{"NE","NW","SE","SW"}) {
         int mask=VisualGeometry.cliffMask("ledges3_"+token+"_inner",null,true);
         check(Integer.bitCount(mask)==1,"inner corner is one closed corner, not crossing strips");
      }
      check(VisualGeometry.cliffMask("", "down",true)==7,"down edge");
      check(VisualGeometry.cliffMask("", "up",true)==448,"up edge");
      check(VisualGeometry.cliffMask("", "left",true)==73,"left edge");
      check(VisualGeometry.cliffMask("", "right",true)==292,"right edge");
      check(!VisualGeometry.buildingWall("house5_floor1","house_chair1",true),"chair not a wall");
      check(!VisualGeometry.buildingWall("house5_floor1","house_desk1",true),"desk not a wall");
      check(!VisualGeometry.buildingWall("house5_floor1","",true),"floor not a wall");
      check(VisualGeometry.buildingWall("interiorwall1","",true),"real wall");
      check(VisualGeometry.buildingWall("green1","house5_wall1",true),"upper wall");
      check(!VisualGeometry.buildingWall("cave1","",false),"walkable cave floor");
      check(!VisualGeometry.buildingWall("cave_door1","",true),"cave door not sealed");
      check(!VisualGeometry.buildingWall("regicave1","",true),"puzzle unchanged");
      System.out.println("VisualGeometryTest PASS: "+checks+" checks");
   }
}
