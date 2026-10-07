package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.pkmngen.leaks.LeakTracer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

/** Actual GL projection, tutorial column gutters and menu stress cases at common desktop scales. */
public final class UiLayoutTest {
   private static void require(boolean value,String message) { if(!value) throw new IllegalStateException(message); }
   public static void main(String[] args) {
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      TestGame game=new TestGame();
      Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
      config.setInitialVisible(false); config.setWindowedMode(640,576); config.setForegroundFPS(120); config.useVsync(false);
      config.setDecorated(false);
      new Lwjgl3Application(game,config);
      require(game.complete,"UI layout run incomplete");
      System.out.println("UI LAYOUT PASS: "+game.captures+" screens; 100/125/150%, widescreen, portrait, ultrawide and desktop-full-size; uniform scale and clear tutorial gutters");
      System.exit(0);
   }
   private static final class TestGame extends Game {
      final int[][] sizes={{640,576},{800,720},{960,864},{1280,720},{1920,1080},{1024,768},{420,840},{1440,400},{0,0}};
      int sizeIndex,scene,wait,frames,captures;
      boolean complete;
      DrawControls controls;
      DrawStatsScreen summary;
      DrawItemMenuGen2 bag;
      DrawSetupMenu setup;
      DrawItemMenu.DrawGuideText guide;
      TestGame() { super(new String[0],4); }
      @Override public void create() {
         super.create(); actionStack.clear();
         sizes[sizes.length-1][0]=Gdx.graphics.getDisplayMode().width; sizes[sizes.length-1][1]=Gdx.graphics.getDisplayMode().height;
         defaults(); controls=new DrawControls(); controls.alpha=1;
         Pokemon pokemon=new Pokemon("gyarados",88); pokemon.previousOwner=player;
         pokemon.nickname="A VERY LONG POKEMON SUMMARY TITLE";
         pokemon.exp=pokemon.calcExpForLevel(pokemon.level)+1000;
         DrawPokemonMenu.allPokemon=new ArrayList<>(); DrawPokemonMenu.allPokemon.add(pokemon);
         summary=new DrawStatsScreen(this,pokemon,null);
         String longItem="Extremely long item name with separate quantity";
         player.setItemAmount(longItem,999999);
         bag=new DrawItemMenuGen2(this,null); bag.firstStep(this);
         bag.itemsList=new ArrayList<>(Arrays.asList(longItem,"Cancel")); bag.cursorPos=0;bag.currIndex=0;
         DrawItemMenuGen2.itemDescriptions.put(longItem.toLowerCase(Locale.ROOT),"Long descriptions stay inside the card, including VeryLongUnbrokenResourceNamesThatMustNotOverflow.");
         setup=new DrawSetupMenu(this,null);
         guide=new DrawItemMenu.DrawGuideText(0,null);guide.firstStep(this);
         require(DesktopControls.hints("Press Z; pressing Z; HOLD X; holding\nX.").equals("Press LMB; pressing LMB; HOLD RMB; holding RMB."),"Authored hint migration incorrect");
         require(DesktopControls.hints("Porygon-Z / Xerneas / XYZ / Z-Move").equals("Porygon-Z / Xerneas / XYZ / Z-Move"),"Names were rewritten");
         DisplayText customText=new DisplayText(this,"Press Z",null,null,null);
         require(customText.spritesNotDrawn.size()==8,"Player-authored dialogue was rewritten");
         nextSize();
      }
      void defaults() {
         InputProcessor.keyboardUp=Keys.W;InputProcessor.keyboardDown=Keys.S;
         InputProcessor.keyboardLeft=Keys.A;InputProcessor.keyboardRight=Keys.D;
         InputProcessor.keyboardA=DesktopControls.MOUSE_LEFT;InputProcessor.keyboardB=DesktopControls.MOUSE_RIGHT;
         InputProcessor.keyboardStart=Keys.ENTER;
      }
      void nextSize() {
         Gdx.graphics.setWindowedMode(sizes[sizeIndex][0],sizes[sizeIndex][1]);
         wait=15;scene=0;
      }
      @Override public void render() {
         if(complete) return;
         if(wait-- >0) return;
         defaults();
         if(Boolean.getBoolean("ui.sourceMode")) modernUi.applyViewport(this,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
         checkProjection();
         ScreenUtils.clear(.9f,0,.9f,1);
         uiBatch.begin();
         if(scene<=2) {
            controls.displayControls=scene!=2;
            if(scene==1) {
               InputProcessor.keyboardUp=Keys.PAGE_UP;InputProcessor.keyboardDown=Keys.PAGE_DOWN;
               InputProcessor.keyboardLeft=Keys.CAPS_LOCK;InputProcessor.keyboardRight=Keys.SCROLL_LOCK;
               InputProcessor.keyboardA=Keys.FORWARD_DEL;InputProcessor.keyboardB=Keys.BACKSPACE;
               InputProcessor.keyboardStart=Keys.NUMPAD_ENTER;
            }
            controls.currTrainerTip="TRAINER TIPS! Stand still while holding X to stop using a Field Move. Custom controls are reflected in every authored hint.";
            modernUi.render(this,controls);
         } else if(scene<=5) { summary.currIndex=scene-3;modernUi.render(this,summary); }
         else if(scene==6) modernUi.render(this,bag);
         else if(scene==7) modernUi.render(this,setup);
         else modernUi.render(this,guide);
         uiBatch.end();
         require(Gdx.gl.glGetError()==0,"GL error while rendering scene "+scene);
         if(++frames%5==0) {
            BufferedImage image=capture();
            if(scene<=1) checkControlGutter(image);
            checkScreenEdges(image);
            String name="ui-"+sizeIndex+"-"+scene+"-"+Gdx.graphics.getWidth()+"x"+Gdx.graphics.getHeight()+".png";
            try { ImageIO.write(image,"png",new File(name)); } catch(Exception e) {throw new IllegalStateException(e);}
            captures++;scene++;
            if(scene>=9) {
               System.out.println("UI LAYOUT: requested="+sizes[sizeIndex][0]+"x"+sizes[sizeIndex][1]
                  +" actual="+Gdx.graphics.getWidth()+"x"+Gdx.graphics.getHeight()+" nine screens passed");
               sizeIndex++;
               if(sizeIndex>=sizes.length) {complete=true;Gdx.app.exit();return;}
               nextSize();
            }
         }
      }
      void checkProjection() {
         Vector3 bottom=new Vector3(0,0,0).prj(uiBatch.getProjectionMatrix());
         Vector3 top=new Vector3(160,144,0).prj(uiBatch.getProjectionMatrix());
         require(bottom.x>=-1.00001f && bottom.y>=-1.00001f && top.x<=1.00001f && top.y<=1.00001f,"UI cropped by resize "+Gdx.graphics.getWidth()+"x"+Gdx.graphics.getHeight());
         float sx=(top.x-bottom.x)*Gdx.graphics.getWidth()/320f,sy=(top.y-bottom.y)*Gdx.graphics.getHeight()/288f;
         require(Math.abs(sx-sy)<.001f,"UI stretched non-uniformly");
      }
      void checkControlGutter(BufferedImage image) {
         // The allocated gap must remain paper throughout the actual rendered control table.
         ModernUi.Box box=modernUi.viewportBounds();
         for(float x=90.8f;x<93.5f;x+=.3f) for(float y=41;y<102;y+=.5f) {
            int px=Math.round((x-box.x)/box.width*image.getWidth()),py=image.getHeight()-1-Math.round((y-box.y)/box.height*image.getHeight());
            Color color=ModernUi.PAPER;int rgb=image.getRGB(px,py);
            require(Math.abs((rgb>>16&255)-Math.round(color.r*255))<=2 && Math.abs((rgb>>8&255)-Math.round(color.g*255))<=2 && Math.abs((rgb&255)-Math.round(color.b*255))<=2,"Tutorial label overlaps key column at "+x+","+y);
         }
      }
      void checkScreenEdges(BufferedImage image) {
         for(int x:new int[]{0,image.getWidth()-1}) for(int y:new int[]{0,image.getHeight()-1}) {
            int rgb=image.getRGB(x,y)&0xffffff;
            require(rgb!=0xe600e6 && rgb!=0xe500e5,"Unpainted fullscreen margin");
         }
      }
      BufferedImage capture() {
         int w=Gdx.graphics.getBackBufferWidth(),h=Gdx.graphics.getBackBufferHeight();
         byte[] data=ScreenUtils.getFrameBufferPixels(0,0,w,h,false);
         BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
         for(int y=0;y<h;y++)for(int x=0;x<w;x++) {int p=(y*w+x)*4;image.setRGB(x,h-1-y,(data[p]&255)<<16|(data[p+1]&255)<<8|(data[p+2]&255));}
         return image;
      }
   }
}
