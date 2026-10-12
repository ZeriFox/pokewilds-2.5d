package com.pkmngen.game;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import java.util.Map;

/** Read-only world-space nature signals. No gameplay clocks, RNG or saved data. */
final class WorldNatureMotion {
   static final float MAX_SHORE_DISTANCE=16f;
   private final Vector2 lookup=new Vector2();
   private final float[] neighbours=new float[9*2];
   private final boolean[] beach=new boolean[9];
   private int count;

   /** A common wind field bends the tips, while every root remains exactly fixed. */
   static float wind(float x,float y,float time,float fraction,float amplitude,boolean reducedMotion) {
      if(reducedMotion||fraction<=0||amplitude==0)return 0;
      float f=MathUtils.clamp(fraction,0,1);
      float broad=MathUtils.sin(x*.037f+y*.023f-time*1.35f);
      float ripple=MathUtils.sin(x*.091f-y*.047f-time*2.1f);
      return amplitude*f*f*(broad*.78f+ripple*.22f);
   }

   /** Only catalogued leafy crowns bend; the lower trunk is a rigid anchor. */
   static boolean treeFlexible(String key) {
      return "tree".equals(key)||"tree_oak".equals(key)||"tree_pine".equals(key)
         ||"tree_snow".equals(key)||"forest_tree_willow".equals(key);
   }
   static float crownWind(float x,float y,float time,float fraction,float amplitude,boolean reducedMotion) {
      if(reducedMotion||fraction<=.45f||amplitude==0)return 0;
      float f=MathUtils.clamp((fraction-.45f)/.55f,0,1);
      // Follow the same gust as the undergrowth, with a slower branch response.
      float gust=wind(x,y,time,1,1,false);
      float branch=MathUtils.sin(x*.025f+y*.019f-time*.83f);
      return amplitude*f*f*(3-2*f)*(gust*.72f+branch*.28f);
   }

   static boolean water(Tile tile) {
      return tile!=null&&tile.isWater&&!tile.isLava&&!tile.isWaterfall;
   }
   static boolean sand(Tile tile) {
      if(tile==null||tile.isWater||tile.isLava||tile.isWaterfall||tile.isLedge
         ||ModernWorldGenerator.isRamp(tile))return false;
      String material=WorldMaterials.terrainName(tile);
      return material.equals("sand")||material.startsWith("sand_");
   }
   /** Live tide connections are presentation only. Puddles stay separate pools.
    * Bits describe the real same-height wet neighbours: down, up, left, right. */
   static int tidalConnections(Map<Vector2,Tile> tiles,Tile source,WorldElevation elevation,Vector2 lookup) {
      if(source==null||!source.isTidal)return 0;
      int mask=0;float level=elevation.tileHeight(source);
      for(int side=0;side<4;side++) {
         int dx=side==2?-16:side==3?16:0,dy=side==0?-16:side==1?16:0;
         Tile next=tiles.get(lookup.set(source.position.x+dx,source.position.y+dy));
         if(next!=null&&!next.isLava&&!next.isWaterfall&&!next.isLedge&&!ModernWorldGenerator.isRamp(next)
            &&(next.isTidal||water(next))&&Math.abs(elevation.tileHeight(next)-level)<=.35f)mask|=1<<side;
      }
      return mask;
   }
   static boolean flexible(String identity) {
      if(identity==null)return false;
      return !identity.contains("cactus")&&!identity.contains("aloe")&&!identity.contains("dead")
         &&!identity.contains("charred")&&!identity.contains("desert_decor")
         &&!identity.contains("rock")&&!identity.contains("stump")&&!identity.equals("bush_dry");
   }

   /** The opposite shore is a real adjacent cell at the same terrain level.
    * Store its footprint once per tile, then sample without map lookups/allocations. */
   void prepare(Map<Vector2,Tile> tiles,Tile source,WorldElevation elevation) {
      count=0;
      boolean wet=water(source);
      if(source.isLedge||ModernWorldGenerator.isRamp(source)||(!wet&&!sand(source)))return;
      float x=source.position.x,y=source.position.y,level=elevation.tileHeight(source);
      for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
         if(dx==0&&dy==0)continue;
         Tile other=tiles.get(lookup.set(x+dx*16,y+dy*16));
         if(other==null||other.isLava||other.isWaterfall||water(other)==wet)continue;
         if(other.isLedge||ModernWorldGenerator.isRamp(other))continue;
         // A high cliff is not a beach. Foam never climbs onto its plateau.
         if(Math.abs(elevation.tileHeight(other)-level)>.35f)continue;
         neighbours[count*2]=other.position.x;neighbours[count*2+1]=other.position.y;
         beach[count]=wet&&sand(other);count++;
      }
   }
   boolean hasShore(){return count>0;}
   boolean hasBeach(){for(int i=0;i<count;i++)if(beach[i])return true;return false;}
   float distance(float x,float y) {
      return distance(x,y,false);
   }
   float beachDistance(float x,float y) {
      return distance(x,y,true);
   }
   private float distance(float x,float y,boolean beachOnly) {
      float squared=MAX_SHORE_DISTANCE*MAX_SHORE_DISTANCE;
      for(int i=0;i<count;i++) {
         if(beachOnly&&!beach[i])continue;
         float left=neighbours[i*2],bottom=neighbours[i*2+1];
         float dx=Math.max(Math.max(left-x,0),x-left-16);
         float dy=Math.max(Math.max(bottom-y,0),y-bottom-16);
         squared=Math.min(squared,dx*dx+dy*dy);
      }
      return (float)Math.sqrt(squared);
   }
}
