package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.*;
import java.util.function.Function;

/** Seated mounted bodies keep the resolved idle identity, outfit and wardrobe.
 * Generated component artwork replaces standing legs rather than cropping them.
 * Texture ownership and pose clocks are presentation-only; no Player is mutated. */
final class MountedTrainerSprites {
   private Pixmap bodies;
   private final LinkedHashMap<Key,Sheet> cache=new LinkedHashMap<>(48,.75f,true);
   private static final class Key {
      final Texture texture;final int x,y,w,h,pose,direction;final boolean flipX,flipY;
      Key(TextureRegion source,int pose,int direction){texture=source.getTexture();x=source.getRegionX();y=source.getRegionY();
         w=source.getRegionWidth();h=source.getRegionHeight();flipX=source.isFlipX();flipY=source.isFlipY();this.pose=pose;this.direction=direction;}
      @Override public int hashCode(){return Objects.hash(System.identityHashCode(texture),x,y,w,h,pose,direction,flipX,flipY);}
      @Override public boolean equals(Object object){if(!(object instanceof Key))return false;Key key=(Key)object;
         return texture==key.texture&&x==key.x&&y==key.y&&w==key.w&&h==key.h&&pose==key.pose&&direction==key.direction&&flipX==key.flipX&&flipY==key.flipY;}
   }
   private static final class Sheet {
      final Texture texture;final TextureRegion region;long used;
      Sheet(Pixmap pixels){texture=new Texture(pixels);texture.setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);
         texture.setWrap(Texture.TextureWrap.ClampToEdge,Texture.TextureWrap.ClampToEdge);region=new TextureRegion(texture);}
   }
   static String pose(String move){return "SURF".equals(move)?"surf":"FLY".equals(move)?"fly":"ride";}
   static int poseIndex(String move){return "SURF".equals(move)?1:"FLY".equals(move)?2:0;}
   static float sway(String move,float seconds,boolean moving) {
      float time=Float.isFinite(seconds)?Math.max(0,seconds):0;
      return (float)Math.sin(time*("FLY".equals(move)?3f:"SURF".equals(move)?2.2f:4.6f))
         *("RIDE".equals(move)?moving?.22f:0f:.14f);
   }
   TextureRegion frame(TextureRegion idle,String move,String direction,Function<TextureRegion,Pixmap> pixels) {
      int mode=poseIndex(move),facing=TrainerCatalog.direction(direction);
      Key key=new Key(idle,mode,facing);long now=Gdx.graphics.getFrameId();Sheet sheet=cache.get(key);
      if(sheet==null) {
         if(bodies==null){bodies=new Pixmap(Gdx.files.internal("visual/mounted-trainers/bodies.png"));
            if(bodies.getWidth()!=96||bodies.getHeight()!=128)throw new IllegalArgumentException("Mounted bodies must be 96x128");}
         Pixmap original=pixels.apply(idle),posed=null;
         try {
            posed=compose(original,bodies,mode,facing);
            Iterator<Sheet> oldest=cache.values().iterator();
            while(cache.size()>=48&&oldest.hasNext()){Sheet previous=oldest.next();if(previous.used!=now){previous.texture.dispose();oldest.remove();}}
            sheet=new Sheet(posed);cache.put(key,sheet);
         }finally{original.dispose();if(posed!=null)posed.dispose();}
      }
      sheet.used=now;return sheet.region;
   }
   static Pixmap copyRegion(Pixmap source,TextureRegion region) {
      int width=region.getRegionWidth(),height=region.getRegionHeight();
      Pixmap copy=new Pixmap(width,height,Pixmap.Format.RGBA8888);copy.setBlending(Pixmap.Blending.None);
      for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
         int sx=Math.min(source.getWidth()-1,Math.max(0,(int)((region.getU()+(region.getU2()-region.getU())*(x+.5f)/width)*source.getWidth())));
         int sy=Math.min(source.getHeight()-1,Math.max(0,(int)((region.getV()+(region.getV2()-region.getV())*(y+.5f)/height)*source.getHeight())));
         copy.drawPixel(x,y,source.getPixel(sx,sy));
      }
      return copy;
   }
   static Pixmap compose(Pixmap original,Pixmap bodies,int mode,int direction) {
      Pixmap source=new Pixmap(32,32,Pixmap.Format.RGBA8888),out=new Pixmap(32,32,Pixmap.Format.RGBA8888);
      source.setBlending(Pixmap.Blending.None);out.setBlending(Pixmap.Blending.None);
      for(int y=0;y<32;y++)for(int x=0;x<32;x++)source.drawPixel(x,y,original.getPixel(
         Math.min(original.getWidth()-1,(int)((x+.5f)*original.getWidth()/32f)),
         Math.min(original.getHeight()-1,(int)((y+.5f)*original.getHeight()/32f))));
      try {
         int shirt=representative(source,19,25,0xdb493dff,false),pants=representative(source,24,28,0x485568ff,false);
         int shoes=representative(source,28,32,0x454854ff,false),skin=representative(source,8,21,0xe8b880ff,true);
         for(int y=18;y<32;y++)for(int x=0;x<32;x++) {
            int color=bodies.getPixel(mode*32+x,direction*32+y);if((color&255)==0)continue;
            int r=color>>>24,g=color>>>16&255,b=color>>>8&255,max=Math.max(r,Math.max(g,b));
            if(max>65) {
               int material=r>g*1.22f&&r>b*1.2f?0:b>r*1.2f&&b>g*1.05f?1:g>r*1.12f&&g>b*1.1f?2:r>105&&g>80&&b<g*.8f?3:-1;
               if(material>=0) {
                  int chosen=material==0?shirt:material==1?pants:material==2?shoes:skin;
                  // The existing jacket/raincoat and rear pack pattern retain
                  // their material pixels when the generated torso is posed.
                  if(material==0) {
                     int sx=Math.min(25,Math.max(7,x)),sy=Math.min(24,Math.max(19,y));
                     int pattern=source.getPixel(sx,sy);
                     if((pattern&255)>200&&!skin(pattern)&&brightness(pattern)>65)chosen=pattern;
                  }
                  float shade=.3f+.7f*max/255f;
                  color=Math.round((chosen>>>24)*shade)<<24|Math.round((chosen>>>16&255)*shade)<<16|
                     Math.round((chosen>>>8&255)*shade)<<8|(color&255);
               }
            }
            out.drawPixel(x,y,color);
         }
         // Copy the original head, cap, hair and face without recoloring it.
         // No standing or walking leg pixels are copied into the mounted pose.
         for(int y=0;y<=18;y++)for(int x=0;x<32;x++) {
            int color=source.getPixel(x,y);if((color&255)>0)out.drawPixel(x,y,color);
         }
      }finally{source.dispose();}
      return out;
   }
   private static int representative(Pixmap source,int top,int bottom,int fallback,boolean requireSkin) {
      Map<Integer,Integer> histogram=new HashMap<>();
      for(int y=top;y<bottom;y++)for(int x=8;x<25;x++) {
         int color=source.getPixel(x,y);if((color&255)<200||brightness(color)<65||skin(color)!=requireSkin)continue;
         histogram.put(color,histogram.getOrDefault(color,0)+1);
      }
      int chosen=fallback,count=0;
      for(Map.Entry<Integer,Integer> entry:histogram.entrySet())if(entry.getValue()>count||entry.getValue()==count&&Integer.compareUnsigned(entry.getKey(),chosen)>0){chosen=entry.getKey();count=entry.getValue();}
      return chosen;
   }
   private static int brightness(int color){return Math.max(color>>>24,Math.max(color>>>16&255,color>>>8&255));}
   private static boolean skin(int color){int r=color>>>24,g=color>>>16&255,b=color>>>8&255;
      return r>150&&g>95&&b>40&&r-g>=8&&r-g<100&&g-b>=8&&g-b<100;}
   void dispose(){for(Sheet sheet:cache.values())sheet.texture.dispose();cache.clear();if(bodies!=null){bodies.dispose();bodies=null;}}
}
