package com.pkmngen.game;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import java.util.Locale;
import java.util.Map;

/** Presentation decisions read from the real collision/action state. No simulation writes. */
final class WorldReadability {
   private WorldReadability() {}
   static boolean smallJumpLedge(Tile tile) {
      if(tile==null||!tile.isLedge||tile.ledgeDir==null||tile.isWaterfall)return false;
      String names=names(tile);
      return !names.contains("ledges3")&&!names.contains("fence")&&!names.contains("gate")
         &&!names.contains("waterfall")&&!names.contains("lavafall");
   }
   /** This is the actual CUT rule in PlayerStanding: evolution, not a made-up level. */
   static int cutTier(Tile tile) {
      if(tile==null||!tile.isCuttable||!WorldMaterials.isTree(tile))return 0;
      return lower(tile.name).contains("tree_large1")||lower(tile.nameUpper).equals("tree_savanna1")?2:1;
   }
   static boolean largeTree(Tile tile) { return names(tile).contains("tree_large"); }
   static boolean hiddenTreeFootprint(Tile tile) { return names(tile).contains("tree_large1_nosprite"); }
   static boolean plainFloor(String name) {
      name=lower(name);
      return (name.contains("floor")||name.contains("carpet")||name.contains("rug"))
         &&!name.contains("cracked")&&!name.contains("onpress")&&!name.contains("switch")
         &&!name.contains("warp")&&!name.contains("hole")&&!name.contains("pit")
         &&!name.contains("stairs")&&!name.contains("ramp");
   }
   static boolean open(Map<Vector2,Tile> tiles,float x,float y,Vector2 lookup) {
      Tile t=tiles.get(lookup.set(MathUtils.floor(x/16)*16,MathUtils.floor(y/16)*16));
      return t!=null&&!t.isSolid&&!t.isWater&&!t.isLava&&!t.isLedge;
   }
   /** Prefer a side/front cell. Never park an ability actor on a blocked tree or in water. */
   static boolean companionTarget(Map<Vector2,Tile> tiles,float x,float y,String direction,Vector2 result,Vector2 lookup) {
      float dx="left".equals(direction)?-1:"right".equals(direction)?1:0;
      float dy=dx!=0?0:"up".equals(direction)?1:-1;
      float sx=dy,sy=-dx;
      float[][] offsets={{sx*20+dx*3,sy*20+dy*3},{-sx*20+dx*3,-sy*20+dy*3},
         {dx*23,dy*23},{sx*16-dx*16,sy*16-dy*16},{-sx*16-dx*16,-sy*16-dy*16},{-dx*23,-dy*23}};
      for(float[] p:offsets) {
         float xx=x+p[0],yy=y+p[1];
         // All five foot samples must be open: a clear centre alone can straddle a wall.
         if(open(tiles,xx,yy,lookup)&&open(tiles,xx-4,yy,lookup)&&open(tiles,xx+4,yy,lookup)
            &&open(tiles,xx,yy-3,lookup)&&open(tiles,xx,yy+3,lookup)) {
            result.set(xx,yy);return true;
         }
      }
      return false;
   }
   static String facing(float dx,float dy,String fallback) {
      if(Math.abs(dx)+Math.abs(dy)<.2f)return fallback;
      return Math.abs(dx)>Math.abs(dy)?(dx<0?"left":"right"):(dy<0?"down":"up");
   }
   private static String names(Tile tile){return lower(tile.name)+" "+lower(tile.nameUpper);}
   private static String lower(String value){return value==null?"":value.toLowerCase(Locale.ROOT);}
}
