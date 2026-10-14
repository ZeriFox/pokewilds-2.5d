package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.*;

/** Exact regions from the supplied sheets. No guessed species or item identity. */
public final class UserSpriteAssets {
   private static UserSpriteAssets shared;
   private final Map<String,Texture> sheets=new HashMap<>();
   private final Map<String,TextureRegion> regions=new HashMap<>();
   private final Map<String,Pixmap> sources=new HashMap<>();
   private final List<Texture> crops=new ArrayList<>();
   private static final Map<String,ItemRegion> ITEMS=new HashMap<>();
   private static final class ItemRegion {
      final String file;final int x,y,w,h;
      ItemRegion(int cell){this("items-bw.png",cell%26*32,cell/26*32,32,32);}
      ItemRegion(String file,int x,int y,int w,int h){this.file=file;this.x=x;this.y=y;this.w=w;this.h=h;}
      String key(){return "item:"+file+":"+x+":"+y;}
   }
   private static final String[] BERRIES=("cheri chesto pecha rawst aspear leppa oran persim lum sitrus figy wiki mago aguav iapapa razz bluk nanab wepear pinap pomeg kelpsy qualot hondew grepa tamato cornn magost rabuta nomel spelon pamtre watmel durin belue occa passho wacan rindo yache chople kebia shuca coba payapa tanga charti kasib haban colbur babiri chilan liechi ganlon salac petaya apicot lansat starf enigma micle custap jaboca rowap").split(" ");
   private UserSpriteAssets(){
      ITEMS.clear();
      com.badlogic.gdx.utils.JsonValue entries=new com.badlogic.gdx.utils.JsonReader().parse(Gdx.files.internal("visual/supplied/item-regions.json"));
      // Coordinates are independently audited against named reference art. The
      // supplied sheet compacts TMs and omits IDs, so numeric game order is unsafe.
      for(com.badlogic.gdx.utils.JsonValue v=entries.child;v!=null;v=v.next)ITEMS.put(v.name,v.isNumber()?new ItemRegion(v.asInt()):
         new ItemRegion(v.getString("sheet"),v.getInt("x"),v.getInt("y"),v.getInt("width",32),v.getInt("height",32)));
      alias("thunderstone","thunder stone");alias("nevermeltice","never melt ice");alias("blackglasses","black glasses");alias("twistedspoon","twisted spoon");
      alias("shield fossil","armor fossil");alias("ragecandybar","rage candy bar");
      alias("soft feather","pretty wing");
   }
   private static void alias(String name,String source){ItemRegion region=ITEMS.get(source);if(region!=null)ITEMS.put(name,region);}
   static String normalized(String name){return name==null?"":name.toLowerCase(Locale.ROOT).replace('é','e').replace('_',' ').trim();}
   public static UserSpriteAssets get(){if(shared==null)shared=new UserSpriteAssets();return shared;}
   public static void disposeShared(){if(shared!=null){for(Texture t:shared.sheets.values())t.dispose();for(Texture t:shared.crops)t.dispose();for(Pixmap p:shared.sources.values())p.dispose();shared=null;}}
   private Texture sheet(String file){return sheets.computeIfAbsent(file,key->{Texture t=new Texture(Gdx.files.internal("visual/supplied/"+key));t.setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);return t;});}
   public TextureRegion item(String name){
      String key=normalized(name);ItemRegion region=ITEMS.get(key);
      if(region==null) {
         int col=ballColumn(key);
         if(col>=0)return ball(key,0);
         return null;
      }
      return region(region);
   }
   private TextureRegion region(ItemRegion r){
      if(!r.file.equals("items-bw.png"))return cleanRegion(r.key(),r.file,r.x,r.y,r.w,r.h,true);
      return regions.computeIfAbsent(r.key(),k->new TextureRegion(sheet(r.file),r.x,r.y,r.w,r.h));
   }
   public static int ballColumn(String name){
      String[] names={"master","ultra","great","poke","safari","net","dive","nest","repeat","timer","luxury","premier","dusk","heal","quick","cherish","fast","level","lure","heavy","love","friend","moon","sport","park","dream","beast"};
      String key=normalized(name);for(int i=0;i<names.length;i++)if(key.equals(names[i]+" ball"))return i;
      return -1;
   }
   public TextureRegion ball(String name,int frame){
      String normalized=normalized(name);
      if(normalized.equals("dream ball")||normalized.equals("beast ball")) {
         ItemRegion r=ITEMS.get(normalized);
         // Inventory cells have wide padding; match the battle sheet's visible
         // ball size instead of shrinking these balls to half the diameter.
         if(r!=null)return normalized.equals("dream ball")?cleanRegion("ball:dream",r.file,r.x+5,r.y+5,20,20,true):
            cleanRegion("ball:beast",r.file,r.x+5,r.y+5,22,22,true);
      }
      int col=ballColumn(name);if(col<0||col>24)col=3;
      int row=Math.max(0,Math.min(9,frame));
      return cleanRegion("ball:"+col+":"+row,"balls-battle.png",col*16,row*16,16,16,true);
   }
   public TextureRegion berry(Tile tile,float seconds){
      if(tile==null)return null;
      String upper=tile.nameUpper==null?"":tile.nameUpper.toLowerCase(Locale.ROOT);
      if(upper.equals("berry_planted"))return berry(0,0,seconds);
      if(!upper.startsWith("berrytree_"))return null;
      String[] words=upper.split("_");int species=-1;
      if(words.length>1)for(int i=0;i<BERRIES.length;i++)if(BERRIES[i].equals(words[1])){species=i;break;}
      if(species<0)return null;
      int stage=upper.contains("full")?2:upper.contains("empty")?1:0;
      return berry(species,stage,seconds);
   }
   TextureRegion berry(int species,int stage,float seconds){
      int group=species/16,col=species%16,frame=((int)(Math.max(0,seconds)*2))%2;
      // Each species has two22x34 frames, a one-pixel frame divider,
      // and a two-pixel species/group border. Exclude all grid lines.
      int x=2+col*47+frame*23,y=2+group*106+Math.max(0,Math.min(2,stage))*35;
      return cleanRegion("berry:"+species+":"+stage+":"+frame,"berry-plants.png",x,y,22,34,true);
   }
   /** Remove only the edge-connected sheet background. White pixels inside art survive. */
   private TextureRegion cleanRegion(String key,String file,int sx,int sy,int w,int h,boolean background){
      TextureRegion cached=regions.get(key);if(cached!=null)return cached;
      Pixmap source=sources.computeIfAbsent(file,f->new Pixmap(Gdx.files.internal("visual/supplied/"+f)));
      Pixmap crop=new Pixmap(w,h,Pixmap.Format.RGBA8888);
      try {
         crop.setBlending(Pixmap.Blending.None);crop.drawPixmap(source,0,0,sx,sy,w,h);
         if(background) {
            int color=crop.getPixel(0,0);boolean[] seen=new boolean[w*h];int[] queue=new int[w*h];int head=0,tail=0;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)if((x==0||y==0||x==w-1||y==h-1)&&crop.getPixel(x,y)==color){int p=y*w+x;if(!seen[p]){seen[p]=true;queue[tail++]=p;}}
            while(head<tail){int p=queue[head++],x=p%w,y=p/w;crop.drawPixel(x,y,0);
               for(int d=0;d<4;d++){int nx=x+(d==0?-1:d==1?1:0),ny=y+(d==2?-1:d==3?1:0);if(nx<0||ny<0||nx>=w||ny>=h)continue;int n=ny*w+nx;
                  if(!seen[n]&&crop.getPixel(nx,ny)==color){seen[n]=true;queue[tail++]=n;}}
            }
         }
         Texture t=new Texture(crop);t.setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);crops.add(t);
         cached=new TextureRegion(t);regions.put(key,cached);return cached;
      }finally{crop.dispose();}
   }
   public TextureRegion partyBall(){return ball("poke ball",0);}
   public TextureRegion status(String condition){
      String key=normalized(condition);int y=key.contains("burn")?266:key.contains("freez")?277:key.contains("poison")?288:key.contains("paral")?299:key.contains("sleep")?310:-1;
      return y<0?null:new TextureRegion(sheet("party-panels.png"),68,y,20,9);
   }
}
