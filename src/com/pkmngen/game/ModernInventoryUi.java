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
         ui.fullScreen(game,"BAG",categories[Math.floorMod(m.boxIndex,5)]);
         for(int i=0;i<5;i++) {
            ui.rect(game,7+i*30,108,27,8,i==m.boxIndex?ModernUi.ACCENT:ModernUi.LINE);
            ui.fitText(game,new String[]{"Items","Balls","Materials","Key","Medicine"}[i],9+i*30,114,5,23,i==m.boxIndex?ModernUi.PAPER:ModernUi.INK);
         }
         list(ui,game,m.itemsList,m.currIndex,m.cursorPos,102,12.2f,true,m.isSorting,m.sortingIndex);
         String selected=at(m.itemsList,m.currIndex+m.cursorPos);
         ui.panel(game,6,16,148,27);
         String description=DrawItemMenuGen2.itemDescriptions.get(selected.toLowerCase(Locale.ROOT));
         if(description==null) description=selected.equalsIgnoreCase("Cancel")?"Return to your adventure.":"Choose an item to use or drop.";
         ui.wrapped(game,description,11,37,5.5f,138,3,ModernUi.MUTED);
         ui.footer(game,m.isSorting?"Select a destination     X  Cancel":"Left / Right  Pocket     Z  Select     X  Back");
      } else if(a instanceof DrawItemMenu) {
         DrawItemMenu m=(DrawItemMenu)a;
         ui.fullScreen(game,m.isGuideMenu?"FIELD GUIDE":"BAG",m.isGuideMenu?"Explore, build, discover":"Your supplies");
         list(ui,game,m.itemsList,m.currIndex,m.cursorPos,110,17,true,m.isSorting,m.sortingIndex);
         ui.footer(game,"Up / Down  Browse     Z  Open     X  Back");
      } else if(a instanceof DrawItemMenu.DrawGuideText) {
         DrawItemMenu.DrawGuideText m=(DrawItemMenu.DrawGuideText)a;
         ui.fullScreen(game,"FIELD GUIDE",at(m.entries,m.index));
         ui.panel(game,5,16,150,100);
         StringBuilder content=new StringBuilder(); for(char c:m.text) content.append(c);
         ui.wrapped(game,content.toString(),11,108,5.9f,138,12,ModernUi.INK);
         ui.footer(game,"Up / Down  Topic     X  Back");
      } else if(a instanceof DrawCraftsMenu) {
         DrawCraftsMenu m=(DrawCraftsMenu)a;
         ui.fullScreen(game,"CRAFTING","Make something for your adventure");
         list(ui,game,m.craftsList,m.currIndex,m.cursorPos,110,14,false,false,0);
         ui.panel(game,6,16,148,35); ui.text(game,"MATERIALS",11,45,5,ModernUi.MUTED);
         for(int i=0;i<m.craftReqs.size() && i<3;i++) {
            boolean enough=i>=m.craftReqColors.size() || m.craftReqColors.get(i).a>.9f;
            ui.fitText(game,m.craftReqs.get(i).replaceAll(" +"," "),11,36-i*7,5.8f,137,enough?ModernUi.ACCENT:ModernUi.RED);
         }
         ui.footer(game,"Up / Down  Recipe     Z  Select     X  Back");
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
            ui.fitText(game,"Arrows adjust / "+ModernUi.confirmKey()+" select",78,52,4.4f,70,ModernUi.MUTED);
         }
      }
   }
   private void list(ModernUi ui,Game game,List<String> entries,int offset,int cursor,float top,float rowHeight,boolean counts,boolean sorting,int sortingIndex) {
      if(entries==null) return;
      int visible=rowHeight==14?4:5;
      ui.panel(game,6,top-visible*rowHeight-2,148,visible*rowHeight+4);
      for(int i=0;i<visible && i+offset<entries.size();i++) {
         String name=entries.get(i+offset); float y=top-(i+1)*rowHeight;
         ui.row(game,(sorting && i+offset==sortingIndex?"* ":"")+name,9,y,142,rowHeight-1,i==cursor);
         if(counts && game.player!=null && game.player.hasItem(name)) {
            // Reserve the right edge for quantities, covering unusually long original names.
            Color bg=i==cursor?ModernUi.ACCENT:ModernUi.PAPER;
            ui.rect(game,126,y,24,rowHeight-1,bg);
            ui.fitText(game,"x"+game.player.getItemAmount(name),129,y+rowHeight-4,5.8f,19,i==cursor?ModernUi.PAPER:ModernUi.MUTED);
         }
      }
      if(entries.size()>visible) ui.bar(game,153,top-visible*rowHeight,1.5f,visible*rowHeight,(offset+cursor+1f)/entries.size(),ModernUi.ACCENT);
   }
   private static String at(List<String> list,int index) { return list==null || list.isEmpty()?"":list.get(Math.max(0,Math.min(list.size()-1,index))); }
}
