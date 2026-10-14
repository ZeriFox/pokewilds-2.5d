package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import java.util.*;

/** A real Pokemon catalog exposed from the existing F1 settings menu. */
final class CreativeModeUi {
   int focus,index,level=20;
   boolean shiny,editing;
   String query="",message="";
   List<String> matches=CreativeMode.search("");
   static ModernUi.Box box(int i){
      if(i==0)return new ModernUi.Box(7,109,146,11);
      if(i==1)return new ModernUi.Box(7,95,146,11);
      if(i==2)return new ModernUi.Box(7,81,88,11);
      if(i==3)return new ModernUi.Box(99,81,54,11);
      if(i==4)return new ModernUi.Box(7,25,146,53);
      return new ModernUi.Box(i==5?7:108,12,i==5?97:45,10);
   }
   static ModernUi.Box pokemonBox(int row){return new ModernUi.Box(9,69-row*8.2f,142,7.8f);}
   void filter(){matches=CreativeMode.search(query);index=0;}
   boolean key(int key) {
      if(!editing)return false;
      if(key==Keys.ESCAPE||key==Keys.ENTER||key==Keys.NUMPAD_ENTER||key==Keys.TAB){editing=false;focus=4;InputProcessor.blockHeldInputs();}
      else if(key==Keys.BACKSPACE&&!query.isEmpty()){query=query.substring(0,query.length()-1);filter();}
      return true;
   }
   boolean typed(char c){if(editing&&query.length()<32&&(Character.isLetterOrDigit(c)||c==' '||c=='-'||c=='_')){query+=Character.toLowerCase(c);filter();}return editing;}
   boolean update(Game game,DesktopMenuController c,boolean confirm,boolean click) {
      if(editing){if(c.back||InputProcessor.bJustPressed){editing=false;focus=4;}return false;}
      if(c.back||InputProcessor.bJustPressed)return true;
      int vertical=InputProcessor.downJustPressed?1:InputProcessor.upJustPressed?-1:0;
      int horizontal=InputProcessor.rightJustPressed?1:InputProcessor.leftJustPressed?-1:0;
      if(c.tab){focus=(focus+1)%7;InputProcessor.blockHeldInputs();}
      else if(vertical!=0){if(focus==4&&!matches.isEmpty())index=Math.floorMod(index+vertical,matches.size());else focus=Math.floorMod(focus+vertical,7);}
      if(focus==4&&horizontal!=0&&!matches.isEmpty())index=Math.floorMod(index+horizontal*6,matches.size());
      if(click) {
         for(int i=0;i<7;i++)if(c.hit(box(i))){focus=i;confirm=i!=4;break;}
         if(focus==4)for(int row=0;row<6;row++)if(c.hit(pokemonBox(row))){index=Math.min(matches.size()-1,index/6*6+row);confirm=false;break;}
      }
      if(focus==2&&(horizontal!=0||confirm)){int step=Gdx.input.isKeyPressed(Keys.SHIFT_LEFT)||Gdx.input.isKeyPressed(Keys.SHIFT_RIGHT)?10:1;level=Math.max(1,Math.min(100,level+(horizontal==0?1:horizontal)*step));}
      else if(focus==0&&confirm)message=CreativeMode.setEnabled(game,!CreativeMode.active(game.player));
      else if(focus==1&&confirm){editing=true;InputProcessor.blockHeldInputs();}
      else if(focus==3&&(confirm||horizontal!=0))shiny=!shiny;
      else if(focus==4&&confirm)focus=5;
      else if(focus==5&&confirm){message=matches.isEmpty()?"No matching Pokemon.":CreativeMode.grant(game,matches.get(Math.max(0,index)),level,shiny);}
      else if(focus==6&&confirm)return true;
      return false;
   }
   void render(ModernUi ui,Game game) {
      boolean active=CreativeMode.active(game.player);
      String note=message.isEmpty()?CreativeMode.warning:message;
      ui.fullScreen(game,"CREATIVE SANDBOX",note.isEmpty()?(active?"Unlimited crafting / building materials. Save from the adventure menu.":"Enable to create Pokemon and use unlimited materials. Local adventures only."):note);
      row(ui,game,0,"Creative mode",active?"ON":"OFF");row(ui,game,1,"Search",query+(editing?"|":""));
      row(ui,game,2,"Level  <  "+level+"  >","");row(ui,game,3,"Shiny",shiny?"YES":"NO");
      ModernUi.Box panel=box(4);ui.panel(game,panel.x,panel.y,panel.width,panel.height);
      if(matches.isEmpty())ui.fitText(game,"No species matches this search.",12,58,6,136,ModernUi.MUTED);
      for(int row=0;row<6&&index/6*6+row<matches.size();row++) {
         int current=index/6*6+row;ModernUi.Box b=pokemonBox(row);
         ui.row(game,"",b.x,b.y,b.width,b.height,current==index&&focus==4);
         String name=matches.get(current);ui.fitText(game,Game.capitalize(name.replace('_',' ')),b.x+3,b.y+5.6f,5.5f,111,current==index&&focus==4?ModernUi.PAPER:ModernUi.INK);
         ui.fitText(game,"#"+Pokemon.nameToIndex(name),b.x+119,b.y+5.6f,4.5f,20,current==index&&focus==4?ModernUi.PAPER:ModernUi.MUTED);
      }
      row(ui,game,5,"Add to party ("+game.player.pokemon.size()+"/6)","");row(ui,game,6,"Back","");
      ui.footer(game,"Tab: control   WASD: select / level   LMB / Enter: apply   Esc: back");
   }
   private void row(ModernUi ui,Game game,int i,String label,String value) {
      ModernUi.Box b=box(i);boolean selected=focus==i;ui.row(game,"",b.x,b.y,b.width,b.height,selected);
      ui.fitText(game,label,b.x+3,b.y+b.height-3,5.2f,value.isEmpty()?b.width-6:b.width*.55f,selected?ModernUi.PAPER:ModernUi.INK);
      if(!value.isEmpty())ui.fitText(game,value,b.x+b.width*.58f,b.y+b.height-3,5.2f,b.width*.39f,selected?ModernUi.PAPER:ModernUi.ACCENT);
   }
}
