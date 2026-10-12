package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.ScreenUtils;
import com.pkmngen.leaks.LeakTracer;
import java.lang.reflect.*;
import java.util.*;

/** Native screenshots + real event adapter/polled input, not just rendering new panels. */
public final class DesktopMenuFlowTest {
   static int checks,captures;
   static void check(boolean ok,String message){checks++;if(!ok)throw new IllegalStateException(message);}
   public static void main(String[] args) {
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;Probe probe=new Probe();
      Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration();c.setWindowedMode(960,540);
      c.setInitialVisible(false);c.useVsync(false);c.setForegroundFPS(60);
      new Lwjgl3Application(probe,c);
      check(probe.complete,"UI flow incomplete");
      System.out.println("DESKTOP MENU FLOW PASS: "+checks+" assertions; "+captures+" captures; explicit text focus, release barriers, settings persistence, 19 B/W-style identities and costume accents, pointer bag and reorder cancel");
      System.exit(0);
   }
   private static final class Probe extends Game {
      final Set<Integer> keys=new HashSet<>(),just=new HashSet<>(),buttons=new HashSet<>();
      Input real,fake;int px,py,frame;boolean complete;
      final InputProcessor processor=new InputProcessor();
      DrawSetupMenu setup;Action active;
      Probe(){super(new String[0],4);}
      @Override public void create() {
         super.create();real=Gdx.input;
         fake=(Input)Proxy.newProxyInstance(Input.class.getClassLoader(),new Class<?>[]{Input.class},(p,m,a)->{
            switch(m.getName()) {
               case "isKeyPressed":return keys.contains((Integer)a[0]);case "isKeyJustPressed":return just.contains((Integer)a[0]);
               case "isButtonPressed":case "isButtonJustPressed":return buttons.contains((Integer)a[0]);
               case "isTouched":return !buttons.isEmpty();case "getX":return px;case "getY":return py;
               default:try{return m.invoke(real,a);}catch(InvocationTargetException e){throw e.getCause();}
            }
         });
         Gdx.input=fake;Game.gamepad=null;InputProcessor.acceptInput=true;actionStack.clear();
         check(!Gdx.files.local(DesktopInterfaceSettings.FILE).exists(),"Test working directory is not isolated");
         modernUi.desktop.preferences.bindings=DesktopInterfaceSettings.defaults();modernUi.desktop.preferences.apply();
         setup=new DrawSetupMenu(this,null);actionStack.add(setup);active=setup;setup.firstStep(this);
      }
      @Override public void render() {
         if(frame++!=3)return;
         try {
            Gdx.input=fake;DesktopControls.focusChanged(true);modernUi.applyViewport(this,960,540);step();capture("01-title.png");
            press(Keys.ENTER);check(modernUi.desktop.screen==DesktopMenuController.Screen.TITLE&&!StartupCinematic.active(modernUi.desktop),"First Enter did not skip only the cinematic");release();
            press(Keys.ENTER);check(modernUi.desktop.screen==DesktopMenuController.Screen.HOME,"Title did not open home after skipping the cinematic");release();capture("02-home.png");
            modernUi.desktop.homeIndex=1;press(Keys.ENTER);check(modernUi.desktop.screen==DesktopMenuController.Screen.HOME&&!modernUi.desktop.message.isEmpty(),"Empty Continue not guarded");release();
            modernUi.desktop.homeIndex=0;press(Keys.ENTER);release();check(modernUi.desktop.screen==DesktopMenuController.Screen.SETUP,"New setup not opened");
            for(int i=1;i<=6;i++){press(Keys.S);typed('s');check(DrawSetupMenu.currIndex==i,"S navigation stopped at row "+i+" actual="+DrawSetupMenu.currIndex+" screen="+modernUi.desktop.screen+" down="+InputProcessor.downPressed+" just="+InputProcessor.downJustPressed+" binding="+InputProcessor.keyboardDown);check(setup.name.isEmpty()&&setup.mapName.isEmpty(),"Navigation typed into a field");release();}
            DrawSetupMenu.currIndex=2;press(Keys.ENTER);check(modernUi.desktop.text.active(),"Confirm did not explicitly focus save name");release();
            for(char c:"wasd".toCharArray()){type(c);check(DrawSetupMenu.currIndex==2,"Typing moved selection");check(!InputProcessor.upPressed&&!InputProcessor.downPressed&&!InputProcessor.leftPressed&&!InputProcessor.rightPressed,"Typing leaked movement");}
            press(Keys.LEFT);release();type('X');check(modernUi.desktop.text.draft.toString().equals("wasXd"),"Caret insertion wrong");
            press(Keys.BACKSPACE);typed('\b');release();check(modernUi.desktop.text.draft.toString().equals("wasd"),"Backspace deleted twice");
            capture("03-editing-save-name.png");press(Keys.ENTER);step();check(!modernUi.desktop.text.active()&&join(setup.mapName).equals("wasd"),"Commit or held Enter incorrect");release();
            press(Keys.S);check(DrawSetupMenu.currIndex==3,"S did not resume without arrow workaround");release();
            DrawSetupMenu.currIndex=2;press(Keys.ENTER);release();type('x');press(Keys.ESCAPE);release();check(join(setup.mapName).equals("wasd"),"Cancel committed draft");
            // A navigation key held while focus opens cannot become a repeated letter.
            DrawSetupMenu.currIndex=1;press(Keys.S);check(DrawSetupMenu.currIndex==2,"Held-S fixture");
            keyDown(Keys.ENTER);step();typed('s');check(modernUi.desktop.text.draft.toString().equals("wasd"),"Held navigation key leaked into text");release();
            press(Keys.ESCAPE);release();
            DrawSetupMenu.currIndex=4;press(Keys.ENTER);release();type('m');DesktopControls.focusChanged(false);
            check(!modernUi.desktop.text.active()&&setup.name.isEmpty(),"Focus loss wrote or retained an edit");keyDown(Keys.ENTER);typed('s');step();check(!modernUi.desktop.text.active()&&setup.name.isEmpty(),"Unfocused input opened/edited a field");
            DesktopControls.focusChanged(true);release();
            setup.localHostJoinIndex=2;setup.newLoadIndex=0;DrawSetupMenu.currIndex=2;press(Keys.S);check(DrawSetupMenu.currIndex==3,"Join address captured S");release();
            DrawSetupMenu.currIndex=2;press(Keys.ENTER);release();for(char c:"256.0.0.1".toCharArray())type(c);press(Keys.ENTER);release();
            check(modernUi.desktop.text.active()&&setup.serverIp.isEmpty(),"Invalid address committed");press(Keys.ESCAPE);release();
            setup.localHostJoinIndex=0;DrawSetupMenu.currIndex=0;step();capture("04-setup-navigation.png");
            settings();trainers();
            DrawSetupMenu.currIndex=0;press(Keys.ENTER);release();check(setup.localHostJoinIndex==1,"Confirm did not cycle a non-text choice");
            setup.localHostJoinIndex=0;DrawSetupMenu.currIndex=6;InputProcessor.aJustPressed=true;
            check(!modernUi.desktop.beforeAction(this,setup),"Go did not delegate the original game-launch action");InputProcessor.aJustPressed=false;
            bag();popup();
            check(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"OpenGL error in menu flow");complete=true;Gdx.app.exit();
         } catch(Throwable e){e.printStackTrace();Gdx.app.exit();throw new RuntimeException(e);}
      }
      void settings() throws Exception {
         press(Keys.F1);release();check(modernUi.desktop.optionsOpen(),"F1 did not open settings");capture("05-controls.png");
         press(Keys.ENTER);release();check(modernUi.desktop.capture==0,"Binding capture did not open");
         press(Keys.S);release();check(modernUi.desktop.capture==0&&modernUi.desktop.message.contains("Already used"),"Duplicate binding accepted");capture("06-binding-conflict.png");
         press(Keys.ESCAPE);release();check(modernUi.desktop.capture<0,"Esc did not cancel capture");
         press(Keys.ENTER);release();press(Keys.I);release();check(modernUi.desktop.draft.bindings[0]==Keys.I&&InputProcessor.keyboardUp==Keys.W,"Draft leaked into live input");
         modernUi.desktop.optionIndex=13;press(Keys.ENTER);release();check(!modernUi.desktop.optionsOpen()&&InputProcessor.keyboardUp==Keys.I,"Save did not apply bindings");
         DesktopInterfaceSettings load=new DesktopInterfaceSettings();load.load();check(load.bindings[0]==Keys.I,"Bindings did not persist");
         press(Keys.I);check(DrawSetupMenu.currIndex!=0,"Custom navigation key inactive");release();
         press(Keys.F1);release();modernUi.desktop.optionIndex=12;press(Keys.ENTER);release();modernUi.desktop.optionIndex=13;press(Keys.ENTER);release();check(InputProcessor.keyboardUp==Keys.W,"Reset/save failed");
         check(!modernUi.desktop.preferences.assign(4,Keys.DOWN).isEmpty(),"Arrow alias could become a second conflicting action");
         check(!modernUi.desktop.preferences.assign(0,Keys.ENTER).isEmpty(),"Enter could become movement and confirm simultaneously");
         int old=InputProcessor.keyboardUp;press(Keys.F1);release();modernUi.desktop.draft.bindings[0]=Keys.I;press(Keys.ESCAPE);release();check(InputProcessor.keyboardUp==old,"Discard changed live binding");
      }
      void trainers() {
         DrawSetupMenu.currIndex=5;press(Keys.ENTER);release();check(modernUi.desktop.screen==DesktopMenuController.Screen.TRAINERS,"Trainer picker not opened");capture("07-trainers-bw-style.png");
         check(modernUi.desktop.trainerCount()==19,"Built-in identities must appear once, without duplicate classic choices");
         Set<String> choices=new HashSet<>();
         for(String id:TrainerCatalog.CAST) {
            check(TrainerCatalog.complete(id),"Incomplete selectable trainer: "+id);
            TextureRegion r=BwAssets.get().detailedTrainer(id,"down",0,false);
            check(r!=null&&r.getRegionWidth()==32&&r.getRegionHeight()==32,"Invalid whole frame "+id);
            choices.add(modernUi.desktop.trainerId(choices.size()));
            for(String dir:new String[]{"down","up","left","right"})for(float t:new float[]{0,.15f,.3f,.45f})check(BwAssets.get().trainer(id,dir,t,true)!=null,"Missing direction/frame");
         }
         check(choices.size()==19&&choices.containsAll(Arrays.asList(TrainerCatalog.CAST)),"Missing or duplicate trainer choices");
         modernUi.desktop.trainerIndex=modernUi.desktop.trainerChoices.indexOf("lyra");modernUi.desktop.trainerPage=1;step();capture("08-lyra-picker.png");press(Keys.ENTER);release();
         check(player.character.equals("lyra")&&!modernUi.desktop.preferences.classicCast,"B/W-style Lyra not selected/saved");
         TextureRegion selected=BwAssets.get().trainer(player,0,false);
         check(selected==BwAssets.get().detailedTrainer("lyra","down",0,false),"Selected appearance not used in world API");
         modernUi.desktop.preferences.classicCast=true;
         check(BwAssets.get().trainer(player,0,false)==selected,"Old preferences restored classic artwork");
         press(Keys.ENTER);release();check(modernUi.desktop.trainerId(modernUi.desktop.trainerIndex).equals("lyra"),"Picker reopened on a different identity");
         int color=modernUi.desktop.trainerColor;press(Keys.V);release();check(modernUi.desktop.trainerColor!=color,"B/W-style costume accents could not be changed");
         TextureRegion preview=BwAssets.get().trainerPreview("lyra","down",0,false,setup.colors.get(modernUi.desktop.trainerColor));
         check(preview!=selected,"Costume preview did not change");press(Keys.ENTER);release();
         check(BwAssets.get().trainer(player,"down",0,false)==preview,"Applied costume differs from picker preview");
         press(Keys.ENTER);release();
         modernUi.desktop.trainerIndex=modernUi.desktop.trainerChoices.indexOf("hilda");press(Keys.ENTER);release();
         check(player.character.equals("hilda")&&!modernUi.desktop.preferences.classicCast,"Original B/W Hilda could not be selected");
      }
      void bag() {
         actionStack.clear();modernUi.desktop.text.cancel();DrawPlayerMenu parent=new DrawPlayerMenu(this,null);parent.disabled=true;
         for(int i=0;i<8;i++)player.setItemAmount("Test item "+i,10+i);
         DrawItemMenuGen2.boxIndex=0;DrawItemMenuGen2 bag=new DrawItemMenuGen2(this,parent);bag.firstStep(this);bag.currIndex=bag.cursorPos=0;active=bag;actionStack.add(bag);release();
         Map<String,Integer> before=new LinkedHashMap<>(player.getItemsDict());
         capture("09-inventory.png");
         click(ModernInventoryUi.bagRow(2),Buttons.LEFT);check(bag.cursorPos==2&&actionStack.contains(bag),"Click did not just select another row");release();
         click(new ModernUi.Box(104,30,20,20),Buttons.LEFT);check(actionStack.contains(bag)&&before.equals(player.getItemsDict()),"Description click activated an item");release();
         String selected=bag.itemsList.get(bag.currIndex+bag.cursorPos);
         String previous=DrawItemMenuGen2.itemDescriptions.put(selected.toLowerCase(Locale.ROOT),"A deliberately long description that contains many lines. Browse the complete explanation without changing the selected item or activating its action. This second sentence verifies that information is accessible rather than silently discarded.");
         uiBatch.begin();try{modernUi.render(this,bag);}finally{uiBatch.end();}
         Gdx.input.getInputProcessor().scrolled(0,1);step();check(bag.cursorPos==2&&modernUi.desktop.detailScroll==1,"Description wheel changed selected item or failed to scroll");
         press(Keys.V);release();check(modernUi.desktop.detailScroll>1,"Description keyboard paging unavailable");
         if(previous==null)DrawItemMenuGen2.itemDescriptions.remove(selected.toLowerCase(Locale.ROOT));else DrawItemMenuGen2.itemDescriptions.put(selected.toLowerCase(Locale.ROOT),previous);
         point(ModernInventoryUi.bagRow(2));Gdx.input.getInputProcessor().scrolled(0,1);step();check(bag.cursorPos==3,"List wheel did not navigate");
         press(Keys.ENTER);release();check(bag.isSorting,"Original reorder command lost");
         click(new ModernUi.Box(104,30,20,20),Buttons.RIGHT);release();check(!bag.isSorting&&actionStack.contains(bag),"Cancel reorder closed the bag");
         click(new ModernUi.Box(7+30,108,27,8),Buttons.LEFT);release();check(DrawItemMenuGen2.boxIndex==1&&bag.cursorPos==0,"Pocket click did not update correct list");
         check(before.equals(player.getItemsDict()),"Browsing modified inventory");capture("10-empty-pocket.png");
      }
      void popup() {
         actionStack.clear();Action yes=new Action(),no=new Action();DrawYesNoMenu popup=new DrawYesNoMenu(null,yes,no);actionStack.add(popup);active=popup;release();
         click(new ModernUi.Box(2,2,10,10),Buttons.LEFT);release();check(actionStack.contains(popup),"Popup margin confirmed a destructive choice");
         click(ModernUi.popupRow(2,0,1),Buttons.LEFT);release();check(popup.curr==1&&actionStack.contains(popup)&&!actionStack.contains(yes),"Clicking NO executed YES or skipped explicit selection");
         click(ModernUi.popupRow(2,1,1),Buttons.LEFT);check(!actionStack.contains(popup)&&actionStack.contains(no)&&!actionStack.contains(yes),"Selected NO did not route the original confirmation action");
      }
      void step() {
         processor.step(this);
         uiBatch.begin();try{modernUi.step(this,active);}finally{uiBatch.end();}just.clear();
      }
      void keyDown(int k){keys.add(k);just.add(k);Gdx.input.getInputProcessor().keyDown(k);}
      void press(int k){keyDown(k);step();}
      void typed(char c){Gdx.input.getInputProcessor().keyTyped(c);}
      void type(char c){int k=Keys.valueOf(String.valueOf(c).toUpperCase(Locale.ROOT));keyDown(k);typed(c);step();release();}
      void release(){for(int k:new ArrayList<>(keys))Gdx.input.getInputProcessor().keyUp(k);keys.clear();just.clear();buttons.clear();step();}
      void point(ModernUi.Box b) {
         ModernUi.Box view=modernUi.viewportBounds();px=(int)((b.x+b.width/2-view.x)*Gdx.graphics.getWidth()/view.width);
         py=(int)(Gdx.graphics.getHeight()-(b.y+b.height/2-view.y)*Gdx.graphics.getHeight()/view.height);
         }
      void click(ModernUi.Box b,int button) {
         point(b);buttons.add(button);Gdx.input.getInputProcessor().touchDown(px,py,0,button);step();
      }
      void capture(String name) {
         uiBatch.begin();try{modernUi.render(this,active);}finally{uiBatch.end();}
         Pixmap p=ScreenUtils.getFrameBufferPixmap(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
         PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);try{png.write(Gdx.files.local(name),p);}catch(Exception e){throw new RuntimeException(e);}finally{png.dispose();p.dispose();}
         captures++;
      }
      static String join(List<Character> list){StringBuilder b=new StringBuilder();for(char c:list)b.append(c);return b.toString();}
      @Override public void dispose(){if(real!=null)Gdx.input=real;super.dispose();}
   }
}
