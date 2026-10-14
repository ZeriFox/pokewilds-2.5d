package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.*;

/** Identity-specific B/W-style presentation, with original player-mod support.
 * Owns its textures, never a Player/TextureCache texture. No save-schema change. */
final class TrainerCatalog {
   static final String[] CAST={"leaf","gold","kris","brendan","may","hilbert","hilda","rosa","calem",
      "serena","chase","elaine","gloria","mark","mint","lunick","summer","kellyn","lyra"};
   private final LinkedHashMap<String,Sheet> cache=new LinkedHashMap<>(32,.75f,true);
   private final Set<Texture> owned=Collections.newSetFromMap(new IdentityHashMap<>());
   private static final class Sheet {
      final Texture texture;final Pixmap pixels;final TextureRegion[][] frames=new TextureRegion[4][3];long used;
      Sheet(Pixmap source){pixels=new Pixmap(source.getWidth(),source.getHeight(),Pixmap.Format.RGBA8888);
         pixels.setBlending(Pixmap.Blending.None);pixels.drawPixmap(source,0,0);
         // Keep the upload source separate from our retained readable copy.
         // Existing normalized battle frames preserve their complete UV bounds
         // after the caller releases source; mounted composition reads pixels.
         texture=new Texture(source);texture.setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);
         for(int d=0;d<4;d++)for(int f=0;f<3;f++)frames[d][f]=new TextureRegion(texture,f*32,d*32,32,32);}
      void dispose(){texture.dispose();pixels.dispose();}
   }
   static FileHandle source(String character,String pose) {
      if(character==null||!character.matches("[A-Za-z0-9_-]+"))return null;
      FileHandle mod=Gdx.files.local("mods/player/"+character+"/"+pose+".png");
      return mod.exists()?mod:Gdx.files.internal("player/"+character+"-"+pose+".png");
   }
   static boolean complete(String name) {
      for(String pose:new String[]{"walking","running","fishing","sleepingbag","sitting","back"}) {
         FileHandle file=source(name,pose);if(file==null||!file.exists())return false;
      }return true;
   }
   static boolean builtin(String name) {return Arrays.asList(CAST).contains(name);}
   static boolean hasMod(String name) {
      return name!=null&&name.matches("[A-Za-z0-9_-]+")
         &&Gdx.files.local("mods/player/"+name+"/walking.png").exists();
   }
   static boolean costumeColors(String name) {
      return hasMod(name)||builtin(name)&&!name.equals("hilbert")&&!name.equals("hilda");
   }
   static int direction(String name) {return "up".equals(name)?1:"left".equals(name)?2:"right".equals(name)?3:0;}
   static int animationFrame(float seconds,boolean moving,boolean running) {
      float time=Float.isFinite(seconds)?Math.max(0,seconds):0;
      int tick=(int)(time*(running?12f:8f))&3;
      return moving?(tick==3?1:tick):1;
   }
   private void reserve(long frame) {
      // Never dispose a texture already submitted by another player/card this frame.
      Iterator<Sheet> it=cache.values().iterator();
      while(cache.size()>=48&&it.hasNext()) {
         Sheet old=it.next();if(old.used!=frame){owned.remove(old.texture);old.dispose();it.remove();}
      }
   }
   TextureRegion modernFrame(String id,String direction,float seconds,boolean moving,boolean running) {
      return modernFrame(id,direction,seconds,moving,running,null);
   }
   TextureRegion modernFrame(String id,String direction,float seconds,boolean moving,boolean running,Color costume) {
      if(!builtin(id)||id.equals("hilbert")||id.equals("hilda"))return null;
      // The historical default selection means the artist's original outfit palette.
      boolean original=costume==null||Math.abs(costume.r-248f/255f)<.001f
         &&Math.abs(costume.g-56f/255f)<.001f&&Math.abs(costume.b-8f/255f)<.001f;
      String key="bw:"+id+":"+(running?"run:":"walk:")+(original?"original":Integer.toHexString(Color.rgba8888(costume)));
      Sheet sheet=cache.get(key);long frame=Gdx.graphics.getFrameId();
      if(sheet==null) {
         FileHandle file=Gdx.files.internal("visual/trainers-bw/"+id+".png");
         if(!file.exists())throw new IllegalStateException("Missing B/W trainer sheet: "+id);
         Pixmap pixels=new Pixmap(file);
         try {
            if(pixels.getWidth()!=96||pixels.getHeight()!=128)
               throw new IllegalArgumentException("B/W trainer sheet must be 96x128: "+id);
            if(!original)recolor(pixels,id,costume);
            reserve(frame);
            if(running){Pixmap run=CharacterAppearance.running(pixels);try{sheet=new Sheet(run);}finally{run.dispose();}}
            else sheet=new Sheet(pixels);
            cache.put(key,sheet);owned.add(sheet.texture);
         } finally {pixels.dispose();}
      }
      sheet.used=frame;
      return sheet.frames[direction(direction)][animationFrame(seconds,moving,running)];
   }
   TextureRegion appearanceFrame(String id,String direction,float seconds,boolean moving,boolean running,CharacterAppearance appearance,Color costume) {
      if(!builtin(id)||appearance==null||!appearance.active())return null;
      String key="parts:"+id+":"+(running?"run:":"walk:")+appearance.key()+":"+(costume==null?"original":Integer.toHexString(Color.rgba8888(costume)));
      Sheet sheet=cache.get(key);long frame=Gdx.graphics.getFrameId();
      if(sheet==null) {
         Pixmap original=CharacterAppearance.original(id,running),composed=null,run=null;
         try {
            if(costume!=null&&!id.equals("hilbert")&&!id.equals("hilda")&&Color.rgba8888(costume)!=0xf83808ff)recolor(original,id,costume);
            composed=appearance.compose(original);
            if(running&&!id.equals("hilbert")&&!id.equals("hilda"))run=CharacterAppearance.running(composed);
            reserve(frame);sheet=new Sheet(run==null?composed:run);cache.put(key,sheet);owned.add(sheet.texture);
         }finally{original.dispose();if(composed!=null)composed.dispose();if(run!=null)run.dispose();}
      }
      sheet.used=frame;return sheet.frames[direction(direction)][animationFrame(seconds,moving,running)];
   }
   private static void recolor(Pixmap pixels,String id,Color costume) {
      FileHandle file=Gdx.files.internal("visual/trainers-bw/"+id+"-mask.png");
      if(!file.exists())throw new IllegalStateException("Missing B/W trainer costume mask: "+id);
      Pixmap mask=new Pixmap(file);
      try {
         if(mask.getWidth()!=pixels.getWidth()||mask.getHeight()!=pixels.getHeight())
            throw new IllegalArgumentException("Costume mask dimensions differ: "+id);
         int brightest=1;
         for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++)
            if((mask.getPixel(x,y)&255)>0) {
               int source=pixels.getPixel(x,y);
               brightest=Math.max(brightest,Math.max(source>>>24,Math.max(source>>>16&255,source>>>8&255)));
            }
         pixels.setBlending(Pixmap.Blending.None);
         for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++) {
            int source=pixels.getPixel(x,y);
            if((mask.getPixel(x,y)&255)==0||(source&255)==0)continue;
            float value=Math.max(source>>>24,Math.max(source>>>16&255,source>>>8&255))/(float)brightest;
            float shade=.25f+.75f*value;
            int r=Math.round(Math.max(0,Math.min(1,costume.r))*255*shade);
            int g=Math.round(Math.max(0,Math.min(1,costume.g))*255*shade);
            int b=Math.round(Math.max(0,Math.min(1,costume.b))*255*shade);
            pixels.drawPixel(x,y,r<<24|g<<16|b<<8|source&255);
         }
      } finally {mask.dispose();}
   }
   TextureRegion frame(String character,String direction,float seconds,boolean moving,Color costume) {
      return frame(character,direction,seconds,moving,false,costume);
   }
   TextureRegion frame(String character,String direction,float seconds,boolean moving,boolean running,Color costume) {
      String id=character==null?"gold":character;
      String pose=running?"running":"walking";
      FileHandle file=source(id,pose);if(file==null||!file.exists())return null;
      String key="classic:"+id+":"+pose+":"+(costume==null?"original":Integer.toHexString(Color.rgba8888(costume)));
      Sheet sheet=cache.get(key);long frame=Gdx.graphics.getFrameId();
      if(sheet==null) {
         reserve(frame);
         Pixmap source=new Pixmap(file),target=new Pixmap(96,128,Pixmap.Format.RGBA8888);
         try {
            if(source.getWidth()<128||source.getHeight()<16)return null;
            int replace=hasMod(id)?0xf83808ff:primary(id),with=costume==null?replace:Color.rgba8888(costume);
            int hair=id.equals("elaine")?11:id.equals("gloria")?10:9;
            for(int d=0;d<4;d++)for(int f=0;f<3;f++)for(int y=0;y<16;y++)for(int x=0;x<16;x++) {
               boolean flip=f==2&&d<2&&(id.equals("hilda")||y>=hair);
               int color=source.getPixel((f==1?0:64)+d*16+(flip?15-x:x),y);
               if(color==replace)color=with;
               for(int yy=0;yy<2;yy++)for(int xx=0;xx<2;xx++)target.drawPixel(f*32+x*2+xx,d*32+y*2+yy,color);
            }
            sheet=new Sheet(target);cache.put(key,sheet);owned.add(sheet.texture);
         } finally {source.dispose();target.dispose();}
      }
      sheet.used=frame;return sheet.frames[direction(direction)][animationFrame(seconds,moving,running)];
   }
   private static int primary(String id) {
      if(id.equals("lunick"))return 0x5948ffff;if(id.equals("kellyn"))return 0xf60801ff;
      return Arrays.asList("gold","mark","red","chase","gloria","hilbert","lyra","serena","brendan","summer").contains(id)?0xf83808ff:0x5048f8ff;
   }
   boolean owns(TextureRegion region){return region!=null&&owned.contains(region.getTexture());}
   Pixmap copyPixels(TextureRegion region) {
      if(region==null)return null;
      for(Sheet sheet:cache.values())if(sheet.texture==region.getTexture())return MountedTrainerSprites.copyRegion(sheet.pixels,region);
      return null;
   }
   void dispose(){for(Sheet s:cache.values())s.dispose();cache.clear();owned.clear();}
}
