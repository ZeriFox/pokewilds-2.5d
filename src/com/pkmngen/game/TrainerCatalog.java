package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.*;

/** Existing complete trainer sheets, not names mapped onto two identical avatars.
 * Classic cast is opt-in: the detailed B/W presentation remains available.
 * Owns generated textures, never a Player/TextureCache texture. No save-schema change.
 */
final class TrainerCatalog {
   static final String[] CAST={"leaf","gold","kris","brendan","may","hilbert","hilda","rosa","calem",
      "serena","chase","elaine","gloria","mark","mint","lunick","summer","kellyn","lyra"};
   private final LinkedHashMap<String,Sheet> cache=new LinkedHashMap<>(32,.75f,true);
   private static final class Sheet {
      final Texture texture;final TextureRegion[][] frames=new TextureRegion[4][3];long used;
      Sheet(Pixmap pixels){texture=new Texture(pixels);texture.setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);
         for(int d=0;d<4;d++)for(int f=0;f<3;f++)frames[d][f]=new TextureRegion(texture,f*32,d*32,32,32);}
   }
   static FileHandle source(String character,String pose) {
      if(character==null||!character.matches("[A-Za-z0-9_-]+"))return null;
      FileHandle mod=Gdx.files.local("mods/player/"+character+"/"+pose+".png");
      return mod.exists()?mod:Gdx.files.internal("player/"+character+"-"+pose+".png");
   }
   static boolean complete(String name) {
      for(String pose:new String[]{"walking","running","fishing","sleepingbag","sitting"}) {
         FileHandle file=source(name,pose);if(file==null||!file.exists())return false;
      }return true;
   }
   TextureRegion frame(String character,String direction,float seconds,boolean moving,Color costume) {
      String id=character==null?"gold":character;
      FileHandle file=source(id,"walking");if(file==null||!file.exists())return null;
      String key=id+":"+(costume==null?"original":Integer.toHexString(Color.rgba8888(costume)));
      Sheet sheet=cache.get(key);long frame=Gdx.graphics.getFrameId();
      if(sheet==null) {
         // Never evict a region already submitted by a different player/card this frame.
         Iterator<Sheet> it=cache.values().iterator();
         while(cache.size()>=48&&it.hasNext()){Sheet old=it.next();if(old.used!=frame){old.texture.dispose();it.remove();}}
         Pixmap source=new Pixmap(file),target=new Pixmap(96,128,Pixmap.Format.RGBA8888);
         try {
            if(source.getWidth()<128||source.getHeight()<16)return null;
            int replace=primary(id),with=costume==null?replace:Color.rgba8888(costume);
            int hair=id.equals("elaine")?11:id.equals("gloria")?10:9;
            for(int d=0;d<4;d++)for(int f=0;f<3;f++)for(int y=0;y<16;y++)for(int x=0;x<16;x++) {
               boolean flip=f==2&&d<2&&(id.equals("hilda")||y>=hair);
               int color=source.getPixel((f==1?0:64)+d*16+(flip?15-x:x),y);
               if(color==replace)color=with;
               for(int yy=0;yy<2;yy++)for(int xx=0;xx<2;xx++)target.drawPixel(f*32+x*2+xx,d*32+y*2+yy,color);
            }
            sheet=new Sheet(target);cache.put(key,sheet);
         } finally {source.dispose();target.dispose();}
      }
      sheet.used=frame;int dir=direction.equals("up")?1:direction.equals("left")?2:direction.equals("right")?3:0;
      int tick=(int)(Math.max(0,seconds)*8)&3;return sheet.frames[dir][moving?(tick==3?1:tick):1];
   }
   private static int primary(String id) {
      if(id.equals("lunick"))return 0x5948ffff;if(id.equals("kellyn"))return 0xf60801ff;
      return Arrays.asList("gold","mark","red","chase","gloria","hilbert","lyra","serena","brendan","summer").contains(id)?0xf83808ff:0x5048f8ff;
   }
   void dispose(){for(Sheet s:cache.values())s.texture.dispose();cache.clear();}
}
