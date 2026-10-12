package com.pkmngen.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.*;

/** Title scene and menus share the controller's exact hit boxes. No simulated gameplay or RNG. */
final class ModernStartupUi {
   private static final Color SKY=Color.valueOf("172f40"),SKY_LIGHT=Color.valueOf("3f6b72"),SHORE=Color.valueOf("234c4b"),SOFT=Color.valueOf("dfece0");
   void render(ModernUi ui,Game game,DrawSetupMenu setup) {
      DesktopMenuController c=ui.desktop;c.attach(setup);c.tick();
      if(c.optionsOpen()){options(ui,game,c);return;}
      switch(c.screen) {
         case TITLE: scenery(ui,game,c);title(ui,game,c);break;
         case HOME: scenery(ui,game,c);home(ui,game,c);break;
         case TRAINERS: trainers(ui,game,c);break;
         default: setup(ui,game,c);
      }
   }
   private void scenery(ModernUi ui,Game game,DesktopMenuController c) {
      ModernUi.Box view=ui.viewportBounds();float time=c.preferences.reducedMotion?0:c.clock;
      ui.fill(game,SKY);
      for(int i=0;i<24;i++){float f=i/23f;Color color=new Color(SKY).lerp(SKY_LIGHT,1-f);ui.rect(game,view.x,32+i*5,view.width,5,color);}
      // Three quiet parallax bands; these are title artwork, never a fake playable world.
      ui.rect(game,view.x,view.y,view.width,64-view.y,SHORE);
      ui.rect(game,view.x,55,view.width,9,Color.valueOf("315b55"));
      for(int i=0;i<5;i++) {
         float x=view.x+(i*83+time*1.7f)%(view.width+48)-40;
         ui.rect(game,x,89+(i%3)*9,32,1.5f,new Color(.86f,.94f,.90f,.09f));
         ui.rect(game,x+7,91+(i%3)*9,18,1,new Color(.86f,.94f,.90f,.06f));
      }
      TextureRegion tree=BwAssets.get().named("tree");
      for(int i=0;i<7;i++){float x=view.x+i*(view.width/6)-10;region(ui,game,tree,x,34+(i%2)*3,25,43);}
      ui.rect(game,view.x,26,view.width,6,Color.valueOf("adad78"));
      ui.rect(game,view.x,25,view.width,1,ModernUi.GOLD);
      TextureRegion trainer=BwAssets.get().trainer("hilbert","right",time*.35f,true);
      region(ui,game,trainer,56,29,24,30);
      PmdPokemonSprites.Frame p=PmdPokemonSprites.get().frame("pikachu","left","Idle",time);
      if(p!=null)region(ui,game,p.region,90,30,22,23);
   }
   private void title(ModernUi ui,Game game,DesktopMenuController c) {
      float lift=c.preferences.reducedMotion?0:(float)Math.max(0,1-c.screenAge/.6f)*5;
      ui.fitText(game,"POKéWILDS",13,127+lift,21,134,ModernUi.PAPER);
      ui.fitText(game,"A NEW HORIZON",36,101,7,90,ModernUi.GOLD);
      ui.fitText(game,"Explore  /  Befriend  /  Build",34,88,5,99,ModernUi.LINE);
      ui.panel(game,27,13,106,12);
      ui.fitText(game,"Enter or "+DesktopControls.confirm()+"  -  Begin",35,21,6,91,ModernUi.INK);
      ui.footer(game,"Opening scene is skippable  /  F1 Settings");
   }
   private void home(ModernUi ui,Game game,DesktopMenuController c) {
      ui.fitText(game,"POKéWILDS",26,134,16,110,ModernUi.PAPER);
      ui.fitText(game,"Your next adventure starts here",28,113,5.8f,106,ModernUi.LINE);
      String[] labels={"New adventure","Continue","Online adventure","Controls & display"};
      ui.panel(game,23,25,114,58);
      for(int i=0;i<labels.length;i++) {
         ModernUi.Box b=DesktopMenuController.homeBox(i);
         ui.row(game,labels[i],b.x,b.y,b.width,b.height,c.homeIndex==i);
         if(i==1&&c.setup.fileNames.isEmpty())ui.fitText(game,"No saves",b.x+73,b.y+7,4,29,ModernUi.MUTED);
      }
      if(!c.message.isEmpty())ui.fitText(game,c.message,6,20,4.4f,148,ModernUi.PAPER);
      ui.footer(game,DesktopControls.movement()+" Navigate   "+DesktopControls.confirm()+" Select   Esc Back");
   }
   private void setup(ModernUi ui,Game game,DesktopMenuController c) {
      ui.fullScreen(game,"ADVENTURE SETUP","Select a field first; confirm only when you want to edit it.");
      List<DesktopMenuController.Row> rows=c.rows();int selected=c.selected(rows);
      for(int i=0;i<rows.size();i++) {
         DesktopMenuController.Row r=rows.get(i);ModernUi.Box b=DesktopMenuController.setupBox(i);
         boolean active=i==selected;ui.panel(game,b);
         if(active){ui.rect(game,b.x+1,b.y+1,b.width-2,b.height-2,SOFT);ui.rect(game,b.x,b.y,2,b.height,ModernUi.ACCENT);}
         String label=label(r.field),value=value(game,c,r.field);
         if(r.field==DesktopMenuController.Field.GO){ui.row(game,value,b.x,b.y,b.width,b.height,active);continue;}
         ui.fitText(game,label,b.x+5,b.y+8.4f,5.3f,44,ModernUi.MUTED);
         if(c.text.active()&&active) {
            value=c.text.draft.substring(0,c.text.cursor)+"|"+c.text.draft.substring(c.text.cursor);
            ui.rect(game,b.x+50,b.y+1,b.width-52,b.height-2,ModernUi.PAPER);
         }
         ui.fitText(game,value,b.x+53,b.y+8.4f,6,86,ModernUi.INK);
      }
      String hint=c.text.active()?"Typing: Enter / LMB Save   Esc / RMB Cancel   Tab Next":"WASD or arrows Navigate   LMB / Enter Edit   F1 Settings";
      if(!c.message.isEmpty())ui.fitText(game,c.message,9,17,4.4f,142,ModernUi.RED);
      if(c.text.active()&&!c.text.error.isEmpty())ui.fitText(game,c.text.error,9,17,4.4f,142,ModernUi.RED);
      ui.footer(game,hint.replace("WASD",DesktopControls.movement()));
   }
   private static String label(DesktopMenuController.Field f) {
      switch(f){case MODE:return "Play mode";case ADVENTURE:return "Adventure";case SAVE:return "Save name";
         case SIZE:return "World size";case NAME:return "Your name";case TRAINER:return "Trainer";case FILE:return "Save file";
         case ADDRESS:return "Server IP";default:return "";}
   }
   private String value(Game game,DesktopMenuController c,DesktopMenuController.Field f) {
      DrawSetupMenu m=c.setup;
      switch(f) {
         case MODE:return new String[]{"< Local >","< Host >","< Join >"}[Math.floorMod(m.localHostJoinIndex,3)];
         case ADVENTURE:return m.newLoadIndex==0?"< New island >":"< Load saved island >";
         case SAVE:return letters(m.mapName).isEmpty()?"default   [confirm to edit]":letters(m.mapName);
         case SIZE:return "< "+new String[]{game.debugInputEnabled?"Test island":"Default (Small)","Small","Medium","Large","Extra large","Vast"}[Math.max(0,Math.min(5,m.sizeIndex))]+" >";
         case NAME:return m.name.isEmpty()?"Your name   [confirm to edit]":letters(m.name);
         case ADDRESS:return m.serverIp.isEmpty()?"127.0.0.1   [confirm to edit]":letters(m.serverIp);
         case FILE:return m.fileNames.isEmpty()?"No saves available":"< "+m.fileNames.get(Math.floorMod(m.fileIndex,m.fileNames.size()))+" >";
         case TRAINER:return Game.capitalize(game.player.character)+" / "+(TrainerCatalog.hasMod(game.player.character)?"Custom":"B/W style")+"  [choose]";
         default:return m.localHostJoinIndex==2?"Join adventure":m.newLoadIndex==1?"Continue adventure":"Begin adventure";
      }
   }
   private static String letters(List<Character> list){StringBuilder s=new StringBuilder();for(char c:list)s.append(c);return s.toString();}
   private void trainers(ModernUi ui,Game game,DesktopMenuController c) {
      ui.fullScreen(game,"CHOOSE YOUR TRAINER","19 individual B/W-style trainers + your installed player mods");
      int total=c.trainerCount();
      for(int i=0;i<10&&c.trainerPage*10+i<total;i++) {
         int at=c.trainerPage*10+i;ModernUi.Box b=DesktopMenuController.trainerBox(i);
         ui.panel(game,b);if(at==c.trainerIndex)ui.rect(game,b.x,b.y,b.width,1.5f,ModernUi.ACCENT);
         String id=c.trainerId(at);
         boolean animate=at==c.trainerIndex&&!c.preferences.reducedMotion;
         TextureRegion image=BwAssets.get().trainerPreview(id,"down",c.clock,animate,c.setup.colors.get(c.trainerColor));
         region(ui,game,image,b.x+5,b.y+6,17,19);
         ui.fitText(game,Game.capitalize(id),b.x+2,b.y+4.8f,4.5f,b.width-4,ModernUi.INK);
      }
      ui.fitText(game,"PAGE "+(c.trainerPage+1)+" / "+((total+9)/10)+"  -  Tab or mouse wheel",8,115,5.3f,144,ModernUi.MUTED);
      boolean mod=TrainerCatalog.hasMod(c.trainerId(c.trainerIndex));
      boolean costume=TrainerCatalog.costumeColors(c.trainerId(c.trainerIndex));
      ui.fitText(game,Game.capitalize(c.trainerId(c.trainerIndex))+"  /  "+(mod?"Installed custom artwork":"B/W style"),9,37,6,143,ModernUi.ACCENT);
      ui.fitText(game,costume?DesktopControls.label(InputProcessor.keyboardL)+" / "+DesktopControls.label(InputProcessor.keyboardR)+" Costume accents":"Original outfit colors  /  Four-direction animation",9,26,4.8f,128,ModernUi.MUTED);
      if(costume)ui.rect(game,142,20,8,8,c.setup.colors.get(c.trainerColor));
      if(!c.message.isEmpty())ui.fitText(game,c.message,8,17,4.3f,145,ModernUi.RED);
      ui.footer(game,DesktopControls.movement()+" Choose   LMB / Enter Apply   Esc Back");
   }
   void options(ModernUi ui,Game game,DesktopMenuController c) {
      ui.fullScreen(game,"CONTROLS & DISPLAY","Changes are applied only with Save settings; Esc discards them.");
      for(int i=0;i<15;i++) {
         ModernUi.Box b=DesktopMenuController.optionBox(i);boolean active=i==c.optionIndex;
         ui.rect(game,b.x,b.y,b.width,b.height,active?ModernUi.ACCENT:ModernUi.PAPER);
         String label=i<9?DesktopInterfaceSettings.ACTIONS[i]:new String[]{"Reduced menu motion","Opening scene","Ambient accents","Reset control draft","Save settings","Discard / back"}[i-9];
         String val=i<9?DesktopControls.label(c.draft.bindings[i]):i==9?(c.draft.reducedMotion?"ON":"OFF"):i==10?(c.draft.openingScene?"ON":"OFF"):i==11?(c.draft.ambient?"ON":"OFF"):"";
         ui.fitText(game,label,b.x+3,b.y+4.8f,4.8f,99,active?ModernUi.PAPER:ModernUi.INK);
         ui.fitText(game,val,b.x+111,b.y+4.8f,4.8f,30,active?ModernUi.PAPER:ModernUi.ACCENT);
      }
      if(!c.message.isEmpty())ui.fitText(game,c.message,9,16,4.2f,142,c.capture>=0?ModernUi.ACCENT:ModernUi.RED);
      if(c.capture>=0) {
         ui.panel(game,15,57,130,29);ui.fitText(game,"BIND: "+DesktopInterfaceSettings.ACTIONS[c.capture],21,79,6.2f,118,ModernUi.ACCENT);
         ui.fitText(game,"Press a key or mouse button. Esc cancels.",21,65,5.1f,118,ModernUi.INK);
      }
      ui.footer(game,DesktopControls.vertical()+" Navigate   LMB / Enter Change   Esc Discard");
   }
   static void region(ModernUi ui,Game game,TextureRegion region,float x,float y,float w,float h) {
      if(region==null)return;float scale=Math.min(w/region.getRegionWidth(),h/region.getRegionHeight());
      float width=region.getRegionWidth()*scale,height=region.getRegionHeight()*scale;
      game.uiBatch.draw(region,x+(w-width)/2,y+(h-height)/2,width,height);
   }
}
