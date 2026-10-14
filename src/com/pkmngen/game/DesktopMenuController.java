package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.Input.Buttons;
import java.util.*;

/** Desktop UI state only. Launching/loading and all inventory operations stay in original actions. */
final class DesktopMenuController {
   enum Screen { TITLE, HOME, SETUP, TRAINERS, WARDROBE }
   enum Field { MODE, ADVENTURE, SAVE, SIZE, NAME, TRAINER, FILE, ADDRESS, GO }
   static final class Row {
      final Field field;final int legacyIndex;
      Row(Field field,int index){this.field=field;legacyIndex=index;}
   }
   final List<String> trainerChoices=new ArrayList<>(Arrays.asList(TrainerCatalog.CAST));
   final DesktopInterfaceSettings preferences=new DesktopInterfaceSettings();
   final MenuTextSession text=new MenuTextSession();
   Screen screen=Screen.TITLE;
   DrawSetupMenu setup;
   Action optionsOwner;
   DesktopInterfaceSettings draft;
   int homeIndex,trainerIndex,trainerPage,trainerColor,optionIndex,capture=-1,rawPrevious;
   float clock,screenAge;
   String message="";
   CharacterAppearance appearanceDraft;
   CreativeModeUi creative;
   String wardrobeId="gold",wardrobeName="";
   int wardrobeSlot,wardrobeDirection;
   boolean wardrobeFromOptions;
   final Map<String,CharacterAppearance> selectedAppearances=new HashMap<>();
   boolean enter,back,settingsRequest,tab,editConfirm,editCancel,opened;
   float wheel;
   int detailScroll;
   String detailIdentity="";
   private float pointerX=-1000,pointerY=-1000;
   private int pointerButton=-1;
   private boolean captureWaiting;
   private long frame=Long.MIN_VALUE;
   private final Set<Integer> typingBarrier=new HashSet<>();
   private final Events events=new Events();
   private com.badlogic.gdx.InputProcessor delegate;

   DesktopMenuController(){preferences.load();}
   boolean optionsOpen(){return draft!=null;}
   boolean isTyping(Game game){return creative!=null&&creative.editing || text.active()&&setup!=null&&game.actionStack.contains(setup);}
   boolean menuActive(Game game) {
      for(Action a:game.actionStack)if(a instanceof Menu || a instanceof DrawSetupMenu || a instanceof DrawControls)return true;
      return false;
   }
   void tick() {
      if(frame==Gdx.graphics.getFrameId())return;frame=Gdx.graphics.getFrameId();
      float dt=Math.max(0,Math.min(.1f,Gdx.graphics.getDeltaTime()));clock+=dt;screenAge+=dt;
   }
   void sync(Game game) {
      tick();
      CreativeMode.sync(game);
      if(text.active()&&(setup==null||!game.actionStack.contains(setup))) {text.cancel();InputProcessor.blockHeldInputs();}
      if(optionsOwner!=null&&!game.actionStack.contains(optionsOwner)){closeOptions(false);}
      typingBarrier.removeIf(k->!Gdx.input.isKeyPressed(k));
      if(captureWaiting&&!anyHeld())captureWaiting=false;
      com.badlogic.gdx.InputProcessor installed=Gdx.input.getInputProcessor();
      if(installed!=events){delegate=installed;Gdx.input.setInputProcessor(events);}
   }
   void blur() {text.cancel();typingBarrier.clear();capture=-1;captureWaiting=false;appearanceDraft=null;if(creative!=null)creative.editing=false;if(screen==Screen.WARDROBE)screen=Screen.TRAINERS;clearEvents();rawPrevious=0;}
   private void clearEvents(){enter=back=settingsRequest=tab=editConfirm=editCancel=false;pointerButton=-1;wheel=0;}
   private boolean settingsAllowed(Game game) {
      for(Action a:game.actionStack)if(a instanceof DrawSetupMenu || a instanceof DrawPlayerMenu&&!((DrawPlayerMenu)a).disabled)return true;
      return false;
   }
   private boolean anyHeld() {
      if(Gdx.input.isButtonPressed(Buttons.LEFT)||Gdx.input.isButtonPressed(Buttons.RIGHT))return true;
      for(int k=1;k<=Keys.MAX_KEYCODE;k++)if(Gdx.input.isKeyPressed(k))return true;return false;
   }
   int filterHeld(Game game,int held) {
      int pressed=held&~rawPrevious;rawPrevious=held;
      if(optionsOpen()&&capture>=0)return 0;
      if(creative!=null&&creative.editing)return 0;
      if(isTyping(game)) {
         if((pressed&(16|64))!=0)editConfirm=true;
         if((pressed&32)!=0)editCancel=true;
         return 0;
      }
      return held;
   }
   void attach(DrawSetupMenu value) {
      if(setup==value)return;
      text.cancel();appearanceDraft=null;selectedAppearances.clear();setup=value;
      if(Game.staticGame!=null && setup.playerTypes.contains(Game.staticGame.player.character))setup.playerTypeIndex=setup.playerTypes.indexOf(Game.staticGame.player.character);
      if(!setup.playerTypes.contains("lyra")&&TrainerCatalog.complete("lyra"))setup.playerTypes.add("lyra");
      trainerChoices.clear();
      for(String id:TrainerCatalog.CAST)if(TrainerCatalog.complete(id))trainerChoices.add(id);
      // Preserve installed player mods instead of replacing their selection with a fixed cast.
      for(String id:setup.playerTypes)if(!trainerChoices.contains(id)&&TrainerCatalog.complete(id))trainerChoices.add(id);
      screen=opened||!preferences.openingScene?Screen.HOME:Screen.TITLE;opened=true;screenAge=0;
   }
   void change(Screen next){screen=next;screenAge=0;message="";clearEvents();InputProcessor.blockHeldInputs();}
   void beginText(Row row) {
      if(row.field==Field.NAME)text.begin(setup.name,MenuTextSession.Kind.NAME);
      else if(row.field==Field.SAVE)text.begin(setup.mapName,MenuTextSession.Kind.SAVE);
      else if(row.field==Field.ADDRESS)text.begin(setup.serverIp,MenuTextSession.Kind.ADDRESS);
      else return;
      typingBarrier.clear();for(int k=1;k<=Keys.MAX_KEYCODE;k++)if(Gdx.input.isKeyPressed(k))typingBarrier.add(k);
      clearEvents();InputProcessor.blockHeldInputs();
   }
   List<Row> rows() {
      List<Row> r=new ArrayList<>();r.add(new Row(Field.MODE,0));
      if(setup.localHostJoinIndex==2) {
         r.add(new Row(Field.ADDRESS,2));r.add(new Row(Field.NAME,3));r.add(new Row(Field.TRAINER,4));r.add(new Row(Field.GO,5));
      } else if(setup.newLoadIndex==1) {
         r.add(new Row(Field.ADVENTURE,1));r.add(new Row(Field.FILE,2));r.add(new Row(Field.GO,3));
      } else {
         r.add(new Row(Field.ADVENTURE,1));r.add(new Row(Field.SAVE,2));r.add(new Row(Field.SIZE,3));
         if(setup.localHostJoinIndex==0){r.add(new Row(Field.NAME,4));r.add(new Row(Field.TRAINER,5));r.add(new Row(Field.GO,6));}
         else r.add(new Row(Field.GO,4));
      }return r;
   }
   int selected(List<Row> rows){for(int i=0;i<rows.size();i++)if(rows.get(i).legacyIndex==DrawSetupMenu.currIndex)return i;return 0;}
   static ModernUi.Box setupBox(int i){return new ModernUi.Box(7,102-i*13.2f,146,11.8f);}
   static ModernUi.Box homeBox(int i){return new ModernUi.Box(27,69-i*13,106,11.5f);}
   static ModernUi.Box trainerBox(int i){return new ModernUi.Box(7+(i%5)*29.5f,75-(i/5)*30,27,27);}
   static ModernUi.Box optionBox(int i){return new ModernUi.Box(7,110-i*6.2f,146,5.9f);}
   static ModernUi.Box customizeBox(){return new ModernUi.Box(9,12,142,10);}
   static ModernUi.Box wardrobeBox(int i){return new ModernUi.Box(65,103-i*12,87,10);}
   static ModernUi.Box wardrobeSwatch(int i){ModernUi.Box row=wardrobeBox(i);return new ModernUi.Box(row.x+77,row.y,10,10);}
   boolean hit(ModernUi.Box box){return pointerX>=box.x&&pointerX<box.x+box.width&&pointerY>=box.y&&pointerY<box.top();}
   private void locatePointer(Game game) {
      ModernUi.Box viewport=game.modernUi.viewportBounds();
      pointerX=viewport.x+Gdx.input.getX()*viewport.width/Math.max(1,Gdx.graphics.getWidth());
      pointerY=viewport.y+(Gdx.graphics.getHeight()-Gdx.input.getY())*viewport.height/Math.max(1,Gdx.graphics.getHeight());
   }
   /** true skips only the old UI action; false delegates its original operation once. */
   boolean beforeAction(Game game,Action action) {
      if(action instanceof DrawSetupMenu)attach((DrawSetupMenu)action);
      boolean ownOptions=action instanceof DrawSetupMenu || action instanceof DrawPlayerMenu&&!((DrawPlayerMenu)action).disabled;
      if(settingsRequest&&ownOptions&&!text.active()){openOptions(action);settingsRequest=false;}
      if(optionsOpen()&&action==optionsOwner){updateOptions();clearEvents();return true;}
      if(action instanceof DrawSetupMenu){boolean handled=updateSetup(game);clearEvents();return handled;}
      if(action instanceof DrawItemMenuGen2)pointerBag(game,(DrawItemMenuGen2)action);
      if(action instanceof Menu&&!((Menu)action).disabled&&pointerButton==Buttons.LEFT) {
         if(action instanceof DrawUseTossMenu){DrawUseTossMenu m=(DrawUseTossMenu)action;m.curr=popupSelection(m.words,m.curr);m.newPos=m.getCoords.get(m.curr);}
         else if(action instanceof DrawYesNoMenu){DrawYesNoMenu m=(DrawYesNoMenu)action;m.curr=popupSelection(m.words,m.curr);m.newPos=m.getCoords.get(m.curr);}
         else if(action instanceof DrawCraftsMenu.Selected){DrawCraftsMenu.Selected m=(DrawCraftsMenu.Selected)action;m.curr=popupSelection(m.words,m.curr);m.newPos=m.getCoords.get(m.curr);}
         else if(action instanceof SelectAmount.Selected){SelectAmount.Selected m=(SelectAmount.Selected)action;m.curr=popupSelection(m.words,m.curr);m.newPos=m.getCoords.get(m.curr);}
      }
      if(action instanceof DrawPlayerMenu&&!((DrawPlayerMenu)action).disabled&&pointerButton==Buttons.LEFT) {
         boolean found=false;DrawPlayerMenu m=(DrawPlayerMenu)action;
         for(int i=0;i<m.entries.length;i++)if(hit(new ModernUi.Box(82,96-i*16,67,14))) {
            m.currIndex=i;m.newPos=m.arrowCoords.get(i);InputProcessor.aJustPressed=true;found=true;break;
         }
         if(!found)InputProcessor.aJustPressed=false;pointerButton=-1;
      }
      return false;
   }
   void afterAction(Game game,Action action) {
      if(action instanceof DrawSetupMenu&&!game.actionStack.contains(action)){text.cancel();InputProcessor.blockHeldInputs();}
      if(action instanceof Menu){enter=back=tab=false;pointerButton=-1;}
   }
   private boolean updateSetup(Game game) {
      boolean confirm=enter||InputProcessor.startJustPressed||InputProcessor.aJustPressed&&pointerButton!=Buttons.LEFT;
      boolean cancel=back||InputProcessor.bJustPressed;
      int vertical=InputProcessor.downJustPressed?1:InputProcessor.upJustPressed?-1:0;
      int horizontal=InputProcessor.rightJustPressed?1:InputProcessor.leftJustPressed?-1:0;
      if(text.active()) {
         if(editCancel||back){text.cancel();InputProcessor.blockHeldInputs();}
         else if(editConfirm||enter||tab) {
            if(text.commit()) {if(tab){List<Row> r=rows();DrawSetupMenu.currIndex=r.get((selected(r)+1)%r.size()).legacyIndex;}InputProcessor.blockHeldInputs();}
         }return true;
      }
      if(screen==Screen.TITLE){if(confirm||cancel||InputProcessor.aJustPressed) {
         if(StartupCinematic.active(this)){screenAge=StartupCinematic.DURATION;clearEvents();InputProcessor.blockHeldInputs();}
         else change(Screen.HOME);
      }return true;}
      if(screen==Screen.HOME) {
         if(vertical!=0)homeIndex=Math.floorMod(homeIndex+vertical,4);
         if(pointerButton==Buttons.LEFT)for(int i=0;i<4;i++)if(hit(homeBox(i))){homeIndex=i;confirm=true;}
         if(cancel){change(Screen.TITLE);return true;}
         if(confirm) {
            if(homeIndex==3){openOptions(setup);return true;}
            if(homeIndex==1&&setup.fileNames.isEmpty()){message="No saved adventures yet. Choose New adventure.";return true;}
            setup.localHostJoinIndex=homeIndex==2?2:0;setup.newLoadIndex=homeIndex==1?1:0;
            DrawSetupMenu.currIndex=homeIndex==1?2:0;setup.playerColorIndex=0;change(Screen.SETUP);
         }return true;
      }
      if(screen==Screen.TRAINERS) {
         if(pointerButton==Buttons.LEFT&&hit(customizeBox())||confirm&&(Gdx.input.isKeyPressed(Keys.SHIFT_LEFT)||Gdx.input.isKeyPressed(Keys.SHIFT_RIGHT))) {
            openWardrobe(false);return true;
         }
         int total=trainerCount();
         if(TrainerCatalog.costumeColors(trainerId(trainerIndex))&&(InputProcessor.lJustPressed||InputProcessor.rJustPressed))
            trainerColor=Math.floorMod(trainerColor+(InputProcessor.rJustPressed?1:-1),setup.colors.size());
         if(vertical!=0||horizontal!=0)trainerIndex=Math.floorMod(trainerIndex+vertical*5+horizontal,total);
         if(wheel!=0)trainerIndex=Math.floorMod(trainerIndex+(wheel>0?10:-10),total);
         trainerPage=trainerIndex/10;
         if(pointerButton==Buttons.LEFT)for(int i=0;i<10&&trainerPage*10+i<total;i++)if(hit(trainerBox(i))){trainerIndex=trainerPage*10+i;confirm=true;}
         if(tab){trainerIndex=(trainerIndex+10)%total;trainerPage=trainerIndex/10;}
         if(cancel){change(Screen.SETUP);return true;}
         if(confirm)applyTrainer(game);
         return true;
      }
      if(screen==Screen.WARDROBE){updateWardrobe();return true;}
      if(cancel){change(Screen.HOME);return true;}
      List<Row> r=rows();int selected=selected(r);
      if(vertical!=0){selected=Math.floorMod(selected+vertical,r.size());DrawSetupMenu.currIndex=r.get(selected).legacyIndex;}
      if(pointerButton==Buttons.LEFT)for(int i=0;i<r.size();i++)if(hit(setupBox(i))){selected=i;DrawSetupMenu.currIndex=r.get(i).legacyIndex;confirm=true;}
      Row row=r.get(selected);
      if(confirm && horizontal==0 && (row.field==Field.MODE||row.field==Field.ADVENTURE||row.field==Field.SIZE||row.field==Field.FILE))horizontal=1;
      if(horizontal!=0) {
         switch(row.field) {
            case MODE: setup.localHostJoinIndex=Math.floorMod(setup.localHostJoinIndex+horizontal,3);setup.newLoadIndex=0;DrawSetupMenu.currIndex=0;break;
            case ADVENTURE: setup.newLoadIndex=1-setup.newLoadIndex;break;
            case SIZE: setup.sizeIndex=Math.max(0,Math.min(5,setup.sizeIndex+horizontal));break;
            case FILE: if(!setup.fileNames.isEmpty())setup.fileIndex=Math.floorMod(setup.fileIndex+horizontal,setup.fileNames.size());break;
            case TRAINER: openTrainers();break;
            default: break;
         }
      }
      if(confirm) {
         if(row.field==Field.NAME||row.field==Field.SAVE||row.field==Field.ADDRESS)beginText(row);
         else if(row.field==Field.TRAINER)openTrainers();
         else if(row.field==Field.GO) {
            if(setup.newLoadIndex==1&&setup.fileNames.isEmpty()){message="No save selected. Choose a new adventure.";return true;}
            if(setup.newLoadIndex==0) {
               CharacterAppearance chosen=selectedAppearances.get(game.player.character);
               if(chosen!=null)try{CharacterAppearance.save(appearanceName(),game.player.character,chosen);}
               catch(java.io.IOException error){message="Could not save the selected appearance for this name.";return true;}
            }
            setup.offset=setup.localHostJoinIndex==2?-1:0;
            setup.offset2=setup.newLoadIndex==1?-3:0;
            setup.offset3=setup.localHostJoinIndex==1&&setup.newLoadIndex==0?-2:0;
            setup.playerColorIndex=0;
            // The original Go branch still owns saves, generation, networking and warnings.
            InputProcessor.upJustPressed=InputProcessor.downJustPressed=InputProcessor.leftJustPressed=InputProcessor.rightJustPressed=false;
            InputProcessor.aJustPressed=true;InputProcessor.bJustPressed=false;return false;
         }
      }
      return true;
   }
   void openTrainers(){trainerIndex=Math.max(0,trainerChoices.indexOf(Game.staticGame.player.character));
      trainerColor=setup.avatarColorIndex;trainerPage=trainerIndex/10;change(Screen.TRAINERS);}
   int trainerCount(){return trainerChoices.size();}
   String trainerId(int i){return trainerChoices.get(Math.max(0,Math.min(trainerChoices.size()-1,i)));}
   String appearanceName(){StringBuilder s=new StringBuilder();if(setup!=null)for(char c:setup.name)s.append(c);return s.toString();}
   CharacterAppearance previewAppearance(String id){CharacterAppearance selected=selectedAppearances.get(id);return selected==null?CharacterAppearance.get(appearanceName(),id):selected.copy();}
   void openWardrobe(boolean fromOptions) {
      Game game=Game.staticGame;String id=fromOptions?game.player.character:trainerId(trainerIndex);
      if(!TrainerCatalog.builtin(id)||TrainerCatalog.hasMod(id)||BwAssets.get().hasTrainerOverride(id)){message="Installed trainer artwork keeps its own clothing and color controls.";return;}
      boolean setupContext=!fromOptions||optionsOwner instanceof DrawSetupMenu;
      wardrobeFromOptions=fromOptions;wardrobeId=id;wardrobeName=setupContext?appearanceName():game.player.name;
      appearanceDraft=setupContext?previewAppearance(id):CharacterAppearance.get(wardrobeName,id);wardrobeSlot=0;wardrobeDirection=0;
      if(!fromOptions)change(Screen.WARDROBE);else{clearEvents();InputProcessor.blockHeldInputs();}
      message=CharacterAppearance.warning;
   }
   private void closeWardrobe(){appearanceDraft=null;if(!wardrobeFromOptions)change(Screen.TRAINERS);else{clearEvents();InputProcessor.blockHeldInputs();}}
   private void updateWardrobe() {
      if(appearanceDraft==null){closeWardrobe();return;}
      if(back||InputProcessor.bJustPressed){closeWardrobe();return;}
      if(tab){wardrobeDirection=(wardrobeDirection+1)%4;tab=false;}
      int vertical=InputProcessor.downJustPressed?1:InputProcessor.upJustPressed?-1:0;
      if(vertical!=0)wardrobeSlot=Math.floorMod(wardrobeSlot+vertical,7);
      boolean confirm=enter||InputProcessor.startJustPressed||InputProcessor.aJustPressed&&pointerButton!=Buttons.LEFT;
      boolean colorClick=false;
      if(pointerButton==Buttons.LEFT)for(int i=0;i<7;i++)if(hit(wardrobeBox(i))){wardrobeSlot=i;colorClick=i<5&&hit(wardrobeSwatch(i));confirm=!colorClick;}
      if(wardrobeSlot<5) {
         int horizontal=InputProcessor.rightJustPressed?1:InputProcessor.leftJustPressed?-1:0;
         if(confirm)horizontal=1;
         if(horizontal!=0)appearanceDraft.styles[wardrobeSlot]=Math.floorMod(appearanceDraft.styles[wardrobeSlot]+horizontal,CharacterAppearance.STYLES[wardrobeSlot].length);
         if(colorClick||InputProcessor.lJustPressed||InputProcessor.rJustPressed) {
            // Color picks are attached to actual replacement parts, never to the whole trainer.
            if(appearanceDraft.styles[wardrobeSlot]==0)appearanceDraft.styles[wardrobeSlot]=1;
            appearanceDraft.colors[wardrobeSlot]=Math.floorMod(appearanceDraft.colors[wardrobeSlot]+(colorClick||InputProcessor.rJustPressed?1:-1),CharacterAppearance.COLORS.length);
         }
      }else if(confirm&&wardrobeSlot==5)appearanceDraft.reset();
      else if(confirm) {
         try {
            CharacterAppearance.save(wardrobeName,wardrobeId,appearanceDraft);
            if(!wardrobeFromOptions||optionsOwner instanceof DrawSetupMenu)selectedAppearances.put(wardrobeId,appearanceDraft.copy());
            if(wardrobeFromOptions){closeWardrobe();message="Appearance saved for this trainer.";}
            else{closeWardrobe();applyTrainer(Game.staticGame);}
         }catch(java.io.IOException error){message="Could not save appearance; the previous profile is unchanged.";}
      }
   }
   private void applyTrainer(Game game) {
      String id=trainerId(trainerIndex);if(!TrainerCatalog.complete(id)){message="This trainer is missing a required pose.";return;}
      if(!setup.playerTypes.contains(id))setup.playerTypes.add(id);setup.playerTypeIndex=setup.playerTypes.indexOf(id);
      setup.avatarColorIndex=trainerColor;game.player.character=id;game.player.setColor(setup.colors.get(setup.avatarColorIndex));
      setup.avatarSprites.clear();setup.avatarSprites.add(game.player.standingSprites.get("down"));setup.avatarSprites.add(game.player.movingSprites.get("down"));setup.avatarSprites.add(game.player.altMovingSprites.get("down"));
      // Kept only for older preference readers; built-in appearance is now per identity.
      preferences.classicCast=TrainerCatalog.hasMod(id);
      try{preferences.save();change(Screen.SETUP);}catch(java.io.IOException e){message="Trainer applied this session; settings could not be saved.";}
   }
   void openOptions(Action owner){appearanceDraft=null;creative=null;optionsOwner=owner;draft=preferences.copy();optionIndex=0;capture=-1;message="";InputProcessor.blockHeldInputs();clearEvents();}
   void closeOptions(boolean save) {
      if(save&&draft!=null) {
         try{draft.save();preferences.bindings=draft.bindings.clone();preferences.reducedMotion=draft.reducedMotion;
            preferences.openingScene=draft.openingScene;preferences.ambient=draft.ambient;preferences.apply();}
         catch(java.io.IOException e){message="Could not save settings. Your previous settings are intact.";return;}
      }
      creative=null;appearanceDraft=null;draft=null;optionsOwner=null;capture=-1;clearEvents();InputProcessor.blockHeldInputs();
   }
   private void updateOptions() {
      if(creative!=null) {
         if(creative.update(Game.staticGame,this,enter||InputProcessor.startJustPressed||InputProcessor.aJustPressed&&pointerButton!=Buttons.LEFT,pointerButton==Buttons.LEFT)){creative=null;InputProcessor.blockHeldInputs();}
         return;
      }
      if(appearanceDraft!=null&&wardrobeFromOptions){updateWardrobe();return;}
      if(capture>=0){if(back){capture=-1;message="Binding unchanged.";InputProcessor.blockHeldInputs();}return;}
      int delta=InputProcessor.downJustPressed?1:InputProcessor.upJustPressed?-1:0;
      if(delta!=0)optionIndex=Math.floorMod(optionIndex+delta,17);
      boolean confirm=enter||InputProcessor.startJustPressed||InputProcessor.aJustPressed&&pointerButton!=Buttons.LEFT;
      if(pointerButton==Buttons.LEFT)for(int i=0;i<17;i++)if(hit(optionBox(i))){optionIndex=i;confirm=true;}
      if(back||InputProcessor.bJustPressed){closeOptions(false);return;}
      if(!confirm&&!InputProcessor.leftJustPressed&&!InputProcessor.rightJustPressed)return;
      if(optionIndex<9){capture=optionIndex;captureWaiting=true;message="Press a key or mouse button. Esc cancels.";InputProcessor.blockHeldInputs();}
      else if(optionIndex==9)draft.reducedMotion=!draft.reducedMotion;
      else if(optionIndex==10)draft.openingScene=!draft.openingScene;
      else if(optionIndex==11)draft.ambient=!draft.ambient;
      else if(optionIndex==12){draft.bindings=DesktopInterfaceSettings.defaults();message="Default controls restored in this draft. Save to apply.";}
      else if(optionIndex==15)openWardrobe(true);
      else if(optionIndex==16) {
         if(optionsOwner instanceof DrawSetupMenu||!CreativeMode.local(Game.staticGame))message="Open a Local adventure, then Menu > F1 > Creative sandbox.";
         else {creative=new CreativeModeUi();InputProcessor.blockHeldInputs();}
      }
      else closeOptions(optionIndex==13);
   }
   private void captured(int code) {
      if(capture<0||captureWaiting)return;
      message=draft.assign(capture,code);
      if(message.isEmpty()){capture=-1;message="Binding ready. Save settings to apply.";InputProcessor.blockHeldInputs();}
   }
   private int popupSelection(List<String> words,int current) {
      pointerButton=-1;
      int first=Math.max(0,current-5),visible=Math.min(6,words.size());
      for(int i=0;i<visible;i++)if(hit(ModernUi.popupRow(words.size(),current,i))) {
         // Do not execute YES/USE while the player is clicking another choice.
         InputProcessor.aJustPressed=first+i==current;
         InputProcessor.upJustPressed=InputProcessor.downJustPressed=false;
         return first+i;
      }
      InputProcessor.aJustPressed=false;return current;
   }
   private void pointerBag(Game game,DrawItemMenuGen2 bag) {
      if(bag.disabled||bag.itemsList==null)return;
      if(bag.isSorting&&InputProcessor.bJustPressed){bag.isSorting=false;InputProcessor.bJustPressed=false;InputProcessor.blockHeldInputs();return;}
      if(InputProcessor.lJustPressed||InputProcessor.rJustPressed)detailScroll+=InputProcessor.rJustPressed?3:-3;
      if(wheel!=0 && hit(new ModernUi.Box(98,23,56,80))){detailScroll+=wheel>0?1:-1;wheel=0;}
      if(wheel!=0) {
         if(wheel>0)InputProcessor.downJustPressed=true;else InputProcessor.upJustPressed=true;wheel=0;
      }
      if(pointerButton!=Buttons.LEFT)return;
      pointerButton=-1;
      for(int i=0;i<5;i++)if(hit(new ModernUi.Box(7+i*30,108,27,8))) {
         if(i!=DrawItemMenuGen2.boxIndex&&!bag.isSorting) {
            DrawItemMenuGen2.boxIndex=i;bag.currIndex=bag.cursorPos=0;bag.firstStep(game);
         }
         InputProcessor.aJustPressed=false;InputProcessor.blockHeldInputs();return;
      }
      for(int i=0;i<5&&bag.currIndex+i<bag.itemsList.size();i++)if(hit(ModernInventoryUi.bagRow(i))) {
         // First click selects; a second deliberate click activates the selected item.
         boolean activate=bag.cursorPos==i;bag.cursorPos=i;bag.newPos=bag.arrowCoords.get(i);InputProcessor.aJustPressed=activate;
         return;
      }
      // Clicking a description or margin never uses/tosses the highlighted item.
      InputProcessor.aJustPressed=false;
   }
   private final class Events extends InputAdapter {
      private Game game(){return Game.staticGame;}
      @Override public boolean keyDown(int key) {
         if(!InputProcessor.isWindowFocused())return true;
         Game g=game();if(g==null||g.modernUi==null)return delegate!=null&&delegate.keyDown(key);
         if(creative!=null&&creative.key(key))return true;
         if(key==Keys.F1&&!text.active()&&settingsAllowed(g)){settingsRequest=true;return true;}
         if(optionsOpen()&&capture>=0){if(key==Keys.ESCAPE)back=true;else captured(key);return true;}
         if(text.active()) {
            if(typingBarrier.contains(key))return true;
            if(key==Keys.ENTER||key==Keys.NUMPAD_ENTER)editConfirm=true;
            else if(key==Keys.ESCAPE)editCancel=true;
            else if(key==Keys.TAB)tab=true;
            else text.key(key);return true;
         }
         if(menuActive(g)) {
            if(key==Keys.ESCAPE){back=true;return true;}
            if(key==Keys.ENTER||key==Keys.NUMPAD_ENTER){enter=true;return true;}
            if(key==Keys.TAB){tab=true;return true;}
         }
         return delegate!=null&&delegate.keyDown(key);
      }
      @Override public boolean keyTyped(char c) {
         if(!InputProcessor.isWindowFocused())return true;
         Game g=game();if(g!=null&&g.modernUi!=null) {
            if(creative!=null&&creative.editing)return creative.typed(c);
            if(text.active()) {
               int code=Keys.valueOf(String.valueOf(c).toUpperCase(Locale.ROOT));
               if(typingBarrier.contains(code))return true;return text.typed(c);
            }
            // The original setup keyTyped listener must not edit merely highlighted rows.
            if(optionsOpen()||setup!=null&&g.actionStack.contains(setup))return true;
         }return delegate!=null&&delegate.keyTyped(c);
      }
      @Override public boolean keyUp(int k){typingBarrier.remove(k);return delegate!=null&&delegate.keyUp(k);}
      @Override public boolean touchDown(int x,int y,int pointer,int button) {
         if(!InputProcessor.isWindowFocused())return true;
         Game g=game();if(g!=null&&menuActive(g)) {
            locatePointer(g);pointerButton=button;
            if(capture>=0){captured(button==Buttons.LEFT?DesktopControls.MOUSE_LEFT:button==Buttons.RIGHT?DesktopControls.MOUSE_RIGHT:0);return true;}
            if(text.active()){if(button==Buttons.RIGHT)editCancel=true;else if(button==Buttons.LEFT)editConfirm=true;return true;}
         }return delegate!=null&&delegate.touchDown(x,y,pointer,button);
      }
      @Override public boolean touchUp(int x,int y,int pointer,int button){return delegate!=null&&delegate.touchUp(x,y,pointer,button);}
      @Override public boolean touchDragged(int x,int y,int pointer){return delegate!=null&&delegate.touchDragged(x,y,pointer);}
      @Override public boolean mouseMoved(int x,int y){return delegate!=null&&delegate.mouseMoved(x,y);}
      @Override public boolean scrolled(float x,float y){Game g=game();if(g!=null&&menuActive(g)&&!text.active()&&!optionsOpen()&&InputProcessor.isWindowFocused()){locatePointer(g);wheel=Math.max(-3,Math.min(3,wheel+y));return true;}return delegate!=null&&delegate.scrolled(x,y);}
   }
   void dispose(){if(Gdx.input.getInputProcessor()==events)Gdx.input.setInputProcessor(delegate);delegate=null;}
}
