package com.pkmngen.game;

import com.badlogic.gdx.graphics.Color;
import java.util.*;

/** Bag, guide, crafting and confirmation views; never mutates their actions. */
final class ModernInventoryUi {
   boolean covers(Action a) {
      return a instanceof DrawItemMenuGen2 || a instanceof DrawItemMenu || a instanceof DrawItemMenu.DrawGuideText
         || a instanceof DrawItemMenu.Intro || a instanceof DrawMiniMap.Intro || a instanceof DrawCraftsMenu
         || a instanceof DrawCraftsMenu.SelectAmount || a instanceof DrawCraftsMenu.Selected
         || a instanceof DrawUseTossMenu || a instanceof DrawUseTossMenu.SelectAmount || a instanceof DrawYesNoMenu
         || a instanceof SelectAmount || a instanceof SelectAmount.Selected;
   }
   void render(ModernUi ui,Game game,Action a) {
      if(a instanceof DrawItemMenuGen2) {
         DrawItemMenuGen2 m=(DrawItemMenuGen2)a;
         String[] categories={"ITEMS","BALLS","MATERIALS","KEY ITEMS","MEDICINE"};
         ModernPartyUi.frlgScreen(ui,game,"BAG",categories[Math.floorMod(m.boxIndex,5)]);
         for(int i=0;i<5;i++) {
            ui.rect(game,7+i*30,108,27,8,i==m.boxIndex?ModernUi.ACCENT:ModernUi.LINE);
            ui.fitText(game,new String[]{"Items","Balls","Materials","Key","Medicine"}[i],9+i*30,114,5,23,i==m.boxIndex?ModernUi.PAPER:ModernUi.INK);
         }
         ui.panel(game,6,23,89,80);
         if(m.drawItems || !m.disabled) {
            for(int i=0;i<5 && m.itemsList!=null && m.currIndex+i<m.itemsList.size();i++) {
               ModernUi.Box row=bagRow(i);String name=m.itemsList.get(m.currIndex+i);
               boolean count=game.player!=null&&game.player.hasItem(name);
               ModernPartyUi.frlgRow(ui,game,(m.isSorting&&m.sortingIndex==m.currIndex+i?"* ":"")+name,
                  row.x,row.y,row.width,row.height,i==m.cursorPos,count?23:0);
               if(count)ui.fitText(game,"x"+game.player.getItemAmount(name),row.x+row.width-23,row.y+8.5f,5,21,ModernUi.INK);
            }
         } else ui.fitText(game,"Changing pocket...",12,62,5.5f,76,ModernUi.MUTED);
         if(m.itemsList!=null && m.itemsList.size()>5) {
            float track=66,thumb=Math.max(5,track*5/m.itemsList.size());
            float ratio=m.itemsList.size()==5?0:(float)m.currIndex/(m.itemsList.size()-5);
            ui.rect(game,93,27,1.2f,track,ModernUi.LINE);
            ui.rect(game,93,27+(track-thumb)*(1-Math.max(0,Math.min(1,ratio))),1.2f,thumb,ModernUi.ACCENT);
         }
         String selected=at(m.itemsList,m.currIndex+m.cursorPos);
         ui.panel(game,98,23,56,80);
         if(m.bagIcons[Math.floorMod(m.boxIndex,5)]!=null)
            game.uiBatch.draw(m.bagIcons[Math.floorMod(m.boxIndex,5)],112,84,25,15);
         ui.wrapped(game,selected,103,79,5.6f,46,2,ModernUi.INK);
         if(game.player!=null&&game.player.hasItem(selected))ui.fitText(game,"Owned: "+game.player.getItemAmount(selected),103,60,5,46,ModernUi.ACCENT);
         String description=DrawItemMenuGen2.itemDescriptions.get(selected.toLowerCase(Locale.ROOT));
         if(description==null)description=selected.equalsIgnoreCase("Cancel")?"Return to your adventure.":"Select this item to view available actions.";
         String identity=m.boxIndex+":"+selected;
         if(!identity.equals(ui.desktop.detailIdentity)){ui.desktop.detailIdentity=identity;ui.desktop.detailScroll=0;}
         List<String> lines=detailLines(ui,description,46,4.8f);
         ui.desktop.detailScroll=Math.max(0,Math.min(Math.max(0,lines.size()-3),ui.desktop.detailScroll));
         for(int i=0;i<3&&i+ui.desktop.detailScroll<lines.size();i++)ui.text(game,lines.get(i+ui.desktop.detailScroll),103,49-i*7,4.8f,ModernUi.MUTED);
         if(lines.size()>3)ui.fitText(game,DesktopControls.label(InputProcessor.keyboardL)+"/"+DesktopControls.label(InputProcessor.keyboardR)+" more  "+(ui.desktop.detailScroll+1)+"/"+lines.size(),103,28,3.7f,46,ModernUi.ACCENT);
         ui.fitText(game,m.isSorting?"Choose where to move this item.":"Click once to select; again to open.",8,18,4.5f,145,ModernUi.MUTED);
         ui.footer(game,m.isSorting?DesktopControls.confirm()+" Move here   "+DesktopControls.back()+" Cancel reorder":DesktopControls.horizontal()+" Pocket   "+DesktopControls.vertical()+" Select   "+DesktopControls.menu()+" Reorder   "+DesktopControls.back()+" Back");
      } else if(a instanceof DrawItemMenu) {
         DrawItemMenu m=(DrawItemMenu)a;
         ModernPartyUi.frlgScreen(ui,game,m.isGuideMenu?"FIELD GUIDE":"BAG",m.isGuideMenu?"Explore, build, discover":"Your supplies");
         list(ui,game,m.itemsList,m.currIndex,m.cursorPos,110,17,true,m.isSorting,m.sortingIndex);
         ui.footer(game,DesktopControls.vertical()+" Browse   "+DesktopControls.confirm()+" Open   "+DesktopControls.back()+" Back");
      } else if(a instanceof DrawItemMenu.DrawGuideText) {
         DrawItemMenu.DrawGuideText m=(DrawItemMenu.DrawGuideText)a;
         ModernPartyUi.frlgScreen(ui,game,"FIELD GUIDE",at(m.entries,m.index));
         ui.panel(game,5,16,150,100);
         StringBuilder content=new StringBuilder(); for(char c:m.text) content.append(c);
         ui.wrapped(game,DesktopControls.hints(content.toString()),11,108,5.9f,138,12,ModernUi.INK);
         ui.footer(game,DesktopControls.vertical()+" Topic   "+DesktopControls.back()+" Back");
      } else if(a instanceof DrawCraftsMenu) {
         DrawCraftsMenu m=(DrawCraftsMenu)a;
         ModernPartyUi.frlgScreen(ui,game,"CRAFTING","Make something for your adventure");
         list(ui,game,m.craftsList,m.currIndex,m.cursorPos,110,14,false,false,0);
         ui.panel(game,6,16,148,35); ui.text(game,"MATERIALS",11,45,5,ModernUi.MUTED);
         for(int i=0;i<m.craftReqs.size() && i<3;i++) {
            boolean enough=i>=m.craftReqColors.size() || m.craftReqColors.get(i).a>.9f;
            ui.fitText(game,m.craftReqs.get(i).replaceAll(" +"," "),11,36-i*7,5.8f,137,enough?ModernUi.ACCENT:ModernUi.RED);
         }
         ui.footer(game,DesktopControls.vertical()+" Recipe   "+DesktopControls.confirm()+" Select   "+DesktopControls.back()+" Back");
      } else if(a instanceof DrawItemMenu.Intro) {
         ui.render(game,((DrawItemMenu.Intro)a).prevMenu);
      } else if(a instanceof DrawMiniMap.Intro) {
         ui.render(game,((DrawMiniMap.Intro)a).prevMenu);
      } else {
         Action parent=null;
         Object value=ModernUi.field(a,"prevAction"); if(value instanceof Action) parent=(Action)value;
         if(parent==null) {value=ModernUi.field(a,"prevMenu"); if(value instanceof Action) parent=(Action)value;}
         if(parent!=a) ui.render(game,parent);
         if(a instanceof DrawUseTossMenu) {
            DrawUseTossMenu m=(DrawUseTossMenu)a; ui.popup(game,m.itemName,m.words,m.curr);
         } else if(a instanceof DrawYesNoMenu) {
            DrawYesNoMenu m=(DrawYesNoMenu)a; ui.popup(game,"CONFIRM",m.words,m.curr);
         } else if(a instanceof DrawCraftsMenu.Selected) {
            DrawCraftsMenu.Selected m=(DrawCraftsMenu.Selected)a; ui.popup(game,"READY TO CRAFT",m.words,m.curr);
         } else if(a instanceof SelectAmount.Selected) {
            SelectAmount.Selected m=(SelectAmount.Selected)a; ui.popup(game,"CONFIRM",m.words,m.curr);
         } else {
            Object amount=ModernUi.field(a,"amount");
            ui.panel(game,73,46,81,44); ui.text(game,"QUANTITY",80,83,5.5f,ModernUi.MUTED);
            ui.text(game,"x "+(amount==null?1:amount),87,72,13,ModernUi.INK);
            ui.fitText(game,DesktopControls.movement()+" adjust / "+ModernUi.confirmKey()+" select",78,52,4.4f,70,ModernUi.MUTED);
         }
      }
   }
   static List<String> detailLines(ModernUi ui,String text,float width,float size) {
      List<String> result=new ArrayList<>();String line="";
      for(String word:text.trim().split("\\s+")) {
         if(!line.isEmpty() && ui.width(line+" "+word,size)>width){result.add(line);line="";}
         while(ui.width(word,size)>width && word.length()>1) {
            int cut=1;while(cut<word.length()&&ui.width(word.substring(0,cut+1),size)<=width)cut++;
            if(!line.isEmpty()){result.add(line);line="";}result.add(word.substring(0,cut));word=word.substring(cut);
         }
         line+=line.isEmpty()?word:" "+word;
      }
      if(!line.isEmpty())result.add(line);return result;
   }
   static ModernUi.Box bagRow(int index) {return new ModernUi.Box(9,84-index*14,82,13);}
   private void list(ModernUi ui,Game game,List<String> entries,int offset,int cursor,float top,float rowHeight,boolean counts,boolean sorting,int sortingIndex) {
      if(entries==null) return;
      int visible=rowHeight==14?4:5;
      ui.panel(game,6,top-visible*rowHeight-2,148,visible*rowHeight+4);
      for(int i=0;i<visible && i+offset<entries.size();i++) {
         String name=entries.get(i+offset); float y=top-(i+1)*rowHeight;
         boolean hasCount=counts && game.player!=null && game.player.hasItem(name);
         // Allocate two columns before text layout; quantities never paint over item names.
         ModernPartyUi.frlgRow(ui,game,(sorting && i+offset==sortingIndex?"* ":"")+name,9,y,142,rowHeight-1,i==cursor,hasCount?24:0);
         if(hasCount) {
            ui.fitText(game,"x"+game.player.getItemAmount(name),129,y+rowHeight-4,5.8f,19,ModernUi.INK);
         }
      }
      if(entries.size()>visible) ui.bar(game,153,top-visible*rowHeight,1.5f,visible*rowHeight,(offset+cursor+1f)/entries.size(),ModernUi.ACCENT);
   }
   private static String at(List<String> list,int index) { return list==null || list.isEmpty()?"":list.get(Math.max(0,Math.min(list.size()-1,index))); }
}
