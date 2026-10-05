package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import java.lang.reflect.Field;
import java.util.*;

/** Presentation only. Original actions retain ownership of input, timing and rules. */
public final class ModernUi {
   public static final Color INK = Color.valueOf("203e4c"), MUTED = Color.valueOf("667c80"),
      PAPER = Color.valueOf("fffbed"), ACCENT = Color.valueOf("287e78"), GOLD = Color.valueOf("d7a955"),
      RED = Color.valueOf("c85252"), LINE = Color.valueOf("cfddd4"), BACK = Color.valueOf("e7eee2");
   private final Texture pixel;
   private final BitmapFont font;
   private final GlyphLayout layout = new GlyphLayout();
   private final ModernPartyUi party = new ModernPartyUi();
   private final ModernInventoryUi inventory = new ModernInventoryUi();
   private final Map<String,Long> counts = new LinkedHashMap<>();
   private final Map<String,Character> letters = new HashMap<>();
   private long drawCount;
   private String lastScreenId = "";

   public ModernUi() {
      Pixmap p = new Pixmap(1,1,Pixmap.Format.RGBA8888);
      p.setColor(Color.WHITE); p.fill(); pixel = new Texture(p); p.dispose();
      FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts.ttf"));
      FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
      parameter.size = 32;
      parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS + "éÉàèìòù♀♂×→←↑↓…";
      parameter.minFilter = Texture.TextureFilter.Linear; parameter.magFilter = Texture.TextureFilter.Linear;
      font = generator.generateFont(parameter); generator.dispose(); font.setUseIntegerPositions(false);
   }
   public static ModernUi get(Game game) { return game.modernUi; }
   public long getDrawCount() { return drawCount; }
   public String getLastScreenId() { return lastScreenId; }
   public long getScreenDrawCount(String name) { return counts.getOrDefault(name,0L); }
   public Map<String,Long> getScreenCounts() { return Collections.unmodifiableMap(counts); }
   public void mark(Action action) {
      lastScreenId = action.getClass().getSimpleName(); drawCount++;
      counts.merge(lastScreenId,1L,Long::sum);
   }
   public boolean covers(Action action) {
      return party.covers(action) || inventory.covers(action) || action instanceof DisplayText
         || action instanceof DisplayTextIntro || action instanceof DrawText || action instanceof DrawWhiteScreen
         || action instanceof DrawBuildRequirements || action instanceof DrawItemPickup
         || action instanceof ItemPickupNotify || action instanceof RequirementNotify
         || action instanceof DisplayText.ScrollTextUp || action instanceof DisplayTextIntro.ScrollTextUp;
   }
   public void step(Game game, Action action) {
      boolean replace = covers(action);
      ModernBatch batch = (ModernBatch)game.uiBatch;
      batch.suppressed = replace;
      try { action.step(game); } finally { batch.suppressed = false; }
      if(replace) render(game,action);
   }
   /** Rendering a parent never executes it again; nested menus already do that themselves. */
   public void render(Game game, Action action) {
      if(action == null) return;
      float saved = game.uiBatch.getPackedColor();
      game.uiBatch.setColor(Color.WHITE);
      if(party.covers(action)) party.render(this,game,action);
      else if(inventory.covers(action)) inventory.render(this,game,action);
      else if(game.johtoBattleRenderer != null && game.johtoBattleRenderer.renderMenu(game,action)) { }
      else if(action instanceof DisplayText) dialogue(game,(DisplayText)action);
      else if(action instanceof DisplayTextIntro) introText(game,(DisplayTextIntro)action);
      else if(action instanceof DrawText) {
         String text=((DrawText)action).text;
         if(text!=null) { panel(game,3,3,154,35); wrapped(game,text,9,32,6.5f,140,3,INK); }
      } else if(action instanceof DrawBuildRequirements) {
         DrawBuildRequirements m=(DrawBuildRequirements)action;
         if(game.player!=null && game.playerCanMove && (game.player.currFieldMove.equals("BUILD")
            || game.player.currFieldMove.equals("DIG") && !game.player.currBuildTile.name.contains("hole"))) {
            panel(game,3,124,71,18); fitText(game,m.namesMap.getOrDefault(m.currBuilding,"Build"),8,136,7,61,INK);
            float h=14+m.words.size()*8; panel(game,83,141-h,74,h);
            for(int i=0;i<m.words.size();i++) fitText(game,m.words.get(i).replaceAll(" +"," "),88,135-i*8,5.5f,64,
               i<m.wordColors.size() && m.wordColors.get(i).a<.9f?RED:INK);
         }
      } else if(action instanceof DrawItemPickup) {
         DrawItemPickup m=(DrawItemPickup)action;
         float h=15+m.items.size()*9; panel(game,67,141-h,90,h); text(game,"COLLECTED",73,136,5,ACCENT);
         int i=0; for(Map.Entry<String,Integer> entry:m.items.entrySet())
            fitText(game,entry.getKey()+" x"+entry.getValue(),73,126-i++*9,6,79,INK);
      } else if(action instanceof ItemPickupNotify) {
         ItemPickupNotify m=(ItemPickupNotify)action;
         if(m.signCounter>0) toast(game,"Picked up "+m.itemName+" x"+m.quantity,m.bgSprite.getY());
      } else if(action instanceof RequirementNotify) {
         RequirementNotify m=(RequirementNotify)action;
         if(m.signCounter>0) toast(game,"Requires: "+m.text,m.bgSprite.getY());
      } else if(action instanceof DrawWhiteScreen) {
         // Menu transitions retain their original duration, using the shared paper instead of a white flash.
         rect(game,-200,-200,560,544,BACK);
         rect(game,62,70,36,2,LINE); rect(game,62,70,12,2,ACCENT);
      } else if(action instanceof DisplayText.ScrollTextUp || action instanceof DisplayTextIntro.ScrollTextUp) {
         Object owner = enclosing(action);
         if(owner instanceof DisplayText) dialogue(game,(DisplayText)owner);
         else if(owner instanceof DisplayTextIntro) introText(game,(DisplayTextIntro)owner);
      }
      mark(action); game.uiBatch.setPackedColor(saved);
   }
   public void renderAction(Game game, Action action) { render(game, action); }
   private void toast(Game game,String message,float y) {
      panel(game,4,y+122,152,20); rect(game,5,y+123,2,18,GOLD);
      fitText(game,message,11,y+136,6,139,INK);
   }
   public void mapFrame(Game game) {
      for(Action a:game.actionStack) if(a instanceof DrawMiniMap) {
         header(game,"WORLD MAP","Procedural island");
         footer(game,"Arrows  Pan     Z  Select     X  Back"); mark(a); break;
      }
   }
   public void rect(Game game,float x,float y,float w,float h,Color color) {
      float saved=game.uiBatch.getPackedColor(); game.uiBatch.setColor(color);
      game.uiBatch.draw(pixel,x,y,w,h); game.uiBatch.setPackedColor(saved);
   }
   public void panel(Game game,float x,float y,float w,float h) {
      rect(game,x+1,y-1,w,h,new Color(INK.r,INK.g,INK.b,0.18f));
      rect(game,x,y,w,h,LINE); rect(game,x+0.65f,y+0.65f,w-1.3f,h-1.3f,PAPER);
   }
   public void text(Game game,String value,float x,float top,float size,Color color) {
      if(value==null || value.isEmpty()) return;
      font.getData().setScale(size/32f); font.setColor(color);
      font.draw(game.uiBatch,normalize(value),x,top);
   }
   public float width(String value,float size) {
      font.getData().setScale(size/32f); layout.setText(font,normalize(value)); return layout.width;
   }
   public void fitText(Game game,String value,float x,float top,float size,float maxWidth,Color color) {
      float actual=width(value,size); text(game,value,x,top,actual>maxWidth?size*maxWidth/actual:size,color);
   }
   public void wrapped(Game game,String value,float x,float top,float size,float maxWidth,int lines,Color color) {
      if(value==null) return;
      StringBuilder line=new StringBuilder(); int row=0;
      for(String word:normalize(value).split("\\s+")) {
         String next=line.length()==0?word:line+" "+word;
         if(width(next,size)>maxWidth && line.length()>0) {
            text(game,line.toString(),x,top-row*(size+2),size,color); line.setLength(0); row++;
            if(row>=lines) return;
         }
         if(line.length()>0) line.append(' '); line.append(word);
      }
      if(row<lines) text(game,line.toString(),x,top-row*(size+2),size,color);
   }
   public void bar(Game game,float x,float y,float w,float h,float ratio,Color color) {
      rect(game,x,y,w,h,LINE); rect(game,x+0.5f,y+0.5f,Math.max(0,(w-1)*Math.min(1,Math.max(0,ratio))),h-1,color);
   }
   public void row(Game game,String value,float x,float y,float w,float h,boolean selected) {
      if(selected) { rect(game,x,y,w,h,ACCENT); rect(game,x,y,2,h,GOLD); }
      else rect(game,x,y,w,h,new Color(1f,1f,1f,.35f));
      fitText(game,value,x+5,y+h-(h-7)/2,7,w-10,selected?PAPER:INK);
   }
   public void header(Game game,String title,String subtitle) {
      rect(game,-200,121,560,223,INK); rect(game,0,121,160,1.2f,GOLD);
      text(game,title,8,140,9,PAPER); fitText(game,subtitle,8,128.5f,4.7f,144,LINE);
   }
   public void fullScreen(Game game,String title,String subtitle) {
      rect(game,-200,-200,560,544,BACK); header(game,title,subtitle);
   }
   public void footer(Game game,String value) {
      String hints=value.replace("Z ",confirmKey()+" ").replace("X ",backKey()+" ");
      rect(game,-200,-200,560,212,INK); fitText(game,hints,7,8.5f,4.7f,146,PAPER);
   }
   public static String confirmKey() { return com.badlogic.gdx.Input.Keys.toString(InputProcessor.keyboardA); }
   public static String backKey() { return com.badlogic.gdx.Input.Keys.toString(InputProcessor.keyboardB); }
   public void eventBackdrop(Game game,String title,Action action) {
      rect(game,-200,-200,560,544,BACK);
      panel(game,8,43,144,88);
      text(game,title,12,140,6.5f,ACCENT);
      rect(game,8,130,144,1,GOLD);
      mark(action);
   }
   public void popup(Game game,String title,List<String> words,int selected) {
      int start=Math.max(0,selected-5); int visible=Math.min(6,words.size());
      float h=17+visible*14, y=45;
      if(y+h>137) y=137-h;
      panel(game,68,y,87,h); text(game,title,74,y+h-5,5,MUTED);
      for(int i=0;i<visible;i++) row(game,words.get(start+i),72,y+h-17-(i+1)*14,79,13,start+i==selected);
   }
   private void dialogue(Game game,DisplayText text) {
      panel(game,2,2,156,38); rect(game,3,37,154,2,ACCENT);
      drawRevealed(game,text.spritesBeingDrawn,6,38,text.braillify||DisplayText.unownText);
      if(text.spritesNotDrawn.isEmpty() && (text.timer/18)%2==0) fitText(game,confirmKey(),145,10,5,10,ACCENT);
   }
   private void introText(Game game,DisplayTextIntro text) {
      panel(game,2,2,156,38); drawRevealed(game,text.spritesBeingDrawn,0,144,false);
   }
   private void drawRevealed(Game game,List<Sprite> sprites,float minY,float maxY,boolean preserveGlyph) {
      if(letters.isEmpty()) for(Map.Entry<Character,? extends Sprite> entry:game.textDict.entrySet()) {
         if(entry.getKey()!=null) letters.put(key(entry.getValue()),entry.getKey());
      }
      Map<Float,List<Sprite>> rows=new TreeMap<>(Collections.reverseOrder());
      for(Sprite s:sprites) {
         if(s.getY()<minY || s.getY()>maxY) continue;
         if(preserveGlyph) s.draw(game.uiBatch);
         else rows.computeIfAbsent(s.getY(),unused->new ArrayList<>()).add(s);
      }
      for(Map.Entry<Float,List<Sprite>> row:rows.entrySet()) {
         row.getValue().sort(Comparator.comparingDouble(Sprite::getX));
         StringBuilder line=new StringBuilder();
         for(Sprite glyph:row.getValue()) line.append(letters.getOrDefault(key(glyph),'?'));
         fitText(game,line.toString().stripTrailing(),8,row.getKey()+8.5f,8.5f,142,INK);
      }
   }
   private static String key(Sprite s) { return System.identityHashCode(s.getTexture())+":"+s.getRegionX()+":"+s.getRegionY()+":"+s.getRegionWidth()+":"+s.getRegionHeight(); }
   public static String normalize(String s) {
      return s==null?"":s.replace('ì','\'').replace('×','x').replace("↑↓","Up/Down").replace("←→","Left/Right").replace("→",">").replace("←","<")
         .replace("↑","Up").replace("↓","Down").replace("♀","F").replace("♂","M").replace("…","...")
         .replace('–','-').replace('—','-').replace('’','\'').replace('‘','\'');
   }
   static Object field(Object owner,String name) {
      if(owner==null) return null;
      for(Class<?> c=owner.getClass();c!=null;c=c.getSuperclass()) try { Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(owner); } catch(NoSuchFieldException ignored) {} catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}
      return null;
   }
   static Object enclosing(Object object) {
      for(Field f:object.getClass().getDeclaredFields()) if(f.isSynthetic() && f.getName().startsWith("this$")) try {f.setAccessible(true);return f.get(object);} catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
      return null;
   }
   public void dispose() { font.dispose(); pixel.dispose(); }
}
