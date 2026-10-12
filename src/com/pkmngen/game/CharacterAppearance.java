package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local appearance profiles. Inventory, player identity and game saves are untouched. */
final class CharacterAppearance {
   static final String FILE="appearance-profiles.json";
   static final String[] SLOTS={"Outfit","Backpack","Shoes","Headwear","Hair"};
   static final String[][] STYLES={{"Original","Jacket","Raincoat"},{"Original","Daypack","Hiking pack"},
      {"Original","Sneakers","Boots"},{"Original","Cap","Beanie"},{"Original","Spiky","Side part","Ponytail","Bob"}};
   static final String[] COLOR_NAMES={"Original","Scarlet","Ocean","Teal","Violet","Gold","Rose","Ivory","Charcoal","Chestnut"};
   static final int[] COLORS={0,0xdb493dff,0x448ed1ff,0x36a695ff,0x8a60bfff,0xd5a743ff,0xcf6fa3ff,0xd7d9d2ff,0x4b515eff,0x86563bff};
   final int[] styles=new int[5],colors=new int[5];
   private static final Map<String,CharacterAppearance> profiles=new LinkedHashMap<>();
   private static String loadedPath;
   static String warning="";
   CharacterAppearance copy(){CharacterAppearance a=new CharacterAppearance();System.arraycopy(styles,0,a.styles,0,5);System.arraycopy(colors,0,a.colors,0,5);return a;}
   boolean active(){for(int style:styles)if(style!=0)return true;return false;}
   String key(){return Arrays.toString(styles)+Arrays.toString(colors);}
   void reset(){Arrays.fill(styles,0);Arrays.fill(colors,0);}
   private static String profileKey(String name,String id){return (name==null?"":name.trim())+"|"+id.toLowerCase(Locale.ROOT);}
   static CharacterAppearance get(String name,String id) {
      load();CharacterAppearance a=profiles.get(profileKey(name,id));
      if(a==null)a=profiles.get(profileKey("",id));return a==null?new CharacterAppearance():a.copy();
   }
   static CharacterAppearance player(Player player) {
      if(player==null||Game.staticGame==null||player!=Game.staticGame.player)return new CharacterAppearance();
      return get(player.name,player.character);
   }
   static void reload(){loadedPath=null;profiles.clear();load();}
   private static void load() {
      String path=Gdx.files.local(FILE).file().getAbsolutePath();if(path.equals(loadedPath))return;
      loadedPath=path;profiles.clear();warning="";
      if(!Gdx.files.local(FILE).exists())return;
      try {
         if(Files.isSymbolicLink(Paths.get(path)))throw new IllegalArgumentException("Linked appearance profile");
         JsonValue root=new JsonReader().parse(Gdx.files.local(FILE));
         if(root.getInt("schema",0)!=1)throw new IllegalArgumentException("Unsupported appearance profile");
         JsonValue entries=root.get("profiles");if(entries==null||!entries.isObject())throw new IllegalArgumentException("Missing appearance profiles");
         Map<String,CharacterAppearance> parsed=new LinkedHashMap<>();
         for(JsonValue entry=entries.child;entry!=null;entry=entry.next) {
            CharacterAppearance a=new CharacterAppearance();int[] style=entry.get("styles").asIntArray(),color=entry.get("colors").asIntArray();
            if(style.length!=5||color.length!=5)throw new IllegalArgumentException("Incomplete clothing profile");
            for(int i=0;i<5;i++){if(style[i]<0||style[i]>=STYLES[i].length||color[i]<0||color[i]>=COLORS.length)throw new IllegalArgumentException("Unknown clothing part");a.styles[i]=style[i];a.colors[i]=color[i];}
            parsed.put(entry.name,a);
         }
         profiles.putAll(parsed);
      }catch(RuntimeException e){warning="Appearance profiles could not be read; original trainers are available.";}
   }
   static void save(String name,String id,CharacterAppearance appearance)throws java.io.IOException {
      load();Map<String,CharacterAppearance> next=new LinkedHashMap<>(profiles);
      next.put(profileKey(name,id),appearance.copy());
      // The unnamed template preserves a selection made before entering a name.
      if(name==null||name.trim().isEmpty())next.put(profileKey("",id),appearance.copy());
      JsonValue root=new JsonValue(JsonValue.ValueType.object),entries=new JsonValue(JsonValue.ValueType.object);
      root.addChild("schema",new JsonValue(1));root.addChild("profiles",entries);
      for(Map.Entry<String,CharacterAppearance> p:next.entrySet()) {
         JsonValue value=new JsonValue(JsonValue.ValueType.object),styles=new JsonValue(JsonValue.ValueType.array),colors=new JsonValue(JsonValue.ValueType.array);
         for(int s:p.getValue().styles)styles.addChild(new JsonValue(s));for(int c:p.getValue().colors)colors.addChild(new JsonValue(c));
         value.addChild("styles",styles);value.addChild("colors",colors);entries.addChild(p.getKey(),value);
      }
      Path path=Gdx.files.local(FILE).file().toPath().toAbsolutePath(),temp=path.resolveSibling(FILE+".tmp"),backup=path.resolveSibling(FILE+".bak");
      if(Files.isSymbolicLink(path)||Files.isSymbolicLink(temp)||Files.isSymbolicLink(backup))throw new java.io.IOException("Linked appearance profiles are not overwritten");
      try {
         Files.writeString(temp,root.toJson(JsonWriter.OutputType.json)+"\n",StandardCharsets.UTF_8);
         if(Files.exists(path))Files.copy(path,backup,StandardCopyOption.REPLACE_EXISTING);
         try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
         catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
      }finally{Files.deleteIfExists(temp);}
      profiles.clear();profiles.putAll(next);warning="";
   }
   static Pixmap original(String id,boolean running) {
      if(!id.equals("hilbert")&&!id.equals("hilda"))return new Pixmap(Gdx.files.internal("visual/trainers-bw/"+id+".png"));
      Pixmap atlas=new Pixmap(Gdx.files.internal("visual/unova/world-atlas.png")),result=new Pixmap(96,128,Pixmap.Format.RGBA8888);
      result.setBlending(Pixmap.Blending.None);
      try{for(int d=0;d<4;d++)for(int f=0;f<3;f++) {
         String dir=new String[]{"down","up","left","right"}[d];int actual=f==0?1:f==1?0:2;
         TextureRegion r=BwAssets.get().named("trainer_"+(id.equals("hilda")?"female":"male")+"_"+(running?"run":"walk")+"_"+dir+"_"+actual);
         result.drawPixmap(atlas,r.getRegionX(),r.getRegionY(),32,32,f*32,d*32,32,32);
      }}finally{atlas.dispose();}return result;
   }
   /** Generated component cutouts replace their occupied clothing zones, with face pixels preserved. */
   Pixmap compose(Pixmap original) {
      Pixmap out=new Pixmap(96,128,Pixmap.Format.RGBA8888),parts=new Pixmap(Gdx.files.internal("visual/wardrobe/parts.png"));
      out.setBlending(Pixmap.Blending.None);out.drawPixmap(original,0,0);
      try {
         // Hair under headwear; pack behind clothing in front, over it from behind.
         for(int d=0;d<4;d++)for(int f=0;f<3;f++) {
            int[] faceBounds=skinBounds(original,f,d);
            int[] order={4,3,0,1,2};
            for(int slot:order)if(styles[slot]>0) {
               int component=slot<4?slot*2+styles[slot]-1:8+styles[slot]-1;
               int bob=f==1?0:-1,dx=slot==1&&f!=1?(f==0?-1:1):0;
               // Replacing a slot removes its old silhouette, rather than covering it with a second object.
               for(int y=0;y<32;y++)for(int x=0;x<32;x++)if(zone(slot,d,x,y))out.drawPixel(f*32+x,d*32+y,0);
               for(int y=0;y<32;y++)for(int x=0;x<32;x++) {
                  int color=parts.getPixel(component*32+x,d*32+y);if((color&255)==0)continue;
                  int targetX=x+dx,targetY=y+(slot==2?0:bob);
                  // Alternate the two contacts in every facing direction; the
                  // grounded shoe keeps its baseline while the swing foot lifts.
                  if(slot==2&&f!=1&&((f==0&&x<16)||(f==2&&x>=16)))targetY--;
                  if(targetX<1||targetX>30||targetY<0||targetY>30)continue;
                  if(slot==4&&inside(faceBounds,targetX,targetY))continue;
                  out.drawPixel(f*32+targetX,d*32+targetY,tint(color,COLORS[colors[slot]]));
               }
               // Original face is invariant under hair/clothing changes.
               if(slot==4)for(int y=8;y<20;y++)for(int x=4;x<28;x++)if(inside(faceBounds,x,y)) {
                  int color=original.getPixel(f*32+x,d*32+y);if((color&255)>0)out.drawPixel(f*32+x,d*32+y,color);
               }
               if(slot==4&&styles[3]==0)for(int y=0;y<=11;y++)for(int x=0;x<32;x++) {
                  int color=original.getPixel(f*32+x,d*32+y);if((color&255)>0)out.drawPixel(f*32+x,d*32+y,color);
               }
            }
            // Collars and hair may overlap, but skin/eyes belong to the selected
            // identity and must remain connected after every clothing layer.
            for(int y=8;y<21;y++)for(int x=4;x<28;x++)if(inside(faceBounds,x,y)) {
               int color=original.getPixel(f*32+x,d*32+y);if((color&255)>0)out.drawPixel(f*32+x,d*32+y,color);
            }
         }
      }finally{parts.dispose();}return out;
   }
   private static int[] skinBounds(Pixmap original,int frame,int direction) {
      int left=32,top=32,right=-1,bottom=-1;if(direction==1)return new int[]{left,top,right,bottom};
      for(int y=8;y<21;y++)for(int x=7;x<26;x++) {
         int color=original.getPixel(frame*32+x,direction*32+y),r=color>>>24,g=color>>>16&255,b=color>>>8&255;
         if((color&255)>200&&r>150&&g>100&&b>45&&r-g>=8&&r-g<100&&g-b>=8&&g-b<100) {
            left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
         }
      }
      return new int[]{left,top,right,bottom};
   }
   private static boolean inside(int[] bounds,int x,int y){return x>=bounds[0]&&x<=bounds[2]&&y>=bounds[1]&&y<=bounds[3];}
   private static boolean zone(int slot,int d,int x,int y) {
      if(slot==0)return x>=6&&x<=26&&y>=18&&y<=24;
      // A pack lies on clothing, and a hat lies on hair: their transparent holes
      // must reveal the layer below, not erase that layer into detached pieces.
      if(slot==1||slot==3)return false;
      if(slot==2)return x>=5&&x<=27&&y>=28;
      return y<=17||y<=22&&(x<=10||x>=22);
   }
   private static int tint(int color,int chosen) {
      if(chosen==0)return color;
      int r=color>>>24,g=color>>>16&255,b=color>>>8&255,max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));
      if(max-min<22||max<30)return color;
      float shade=.28f+.72f*max/255f;
      return Math.round((chosen>>>24)*shade)<<24|Math.round((chosen>>>16&255)*shade)<<16|Math.round((chosen>>>8&255)*shade)<<8|color&255;
   }
   /** Real run anticipation: torso leans in travel direction while contact feet stay fixed. */
   static Pixmap running(Pixmap source) {
      Pixmap result=new Pixmap(96,128,Pixmap.Format.RGBA8888);result.setBlending(Pixmap.Blending.None);
      for(int d=0;d<4;d++)for(int f=0;f<3;f++)for(int y=0;y<32;y++)for(int x=0;x<32;x++) {
         int color=source.getPixel(f*32+x,d*32+y);if((color&255)==0)continue;
         // Keep the waist joint under the raised torso; a hard shift cutoff
         // otherwise leaves an entirely transparent row between body and legs.
         if(y==24)result.drawPixel(f*32+x,d*32+y,color);
         int lean=y<25&&f!=1?(d==2?-1:d==3?1:0):0;
         int bob=y<25&&f!=1?-1:0;int tx=x+lean,ty=y+bob;
         if(tx>=1&&tx<=30&&ty>=0&&ty<=30)result.drawPixel(f*32+tx,d*32+ty,color);
      }
      return result;
   }
}
