package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.ScreenUtils;
import com.pkmngen.leaks.LeakTracer;
import java.util.*;

/** Exercises shipped opening artwork, motion preferences and concealed ghost alpha in native GL. */
public final class CinematicOpeningTest {
   static int checks,captures;static Throwable failure;
   static void check(boolean value,String message){checks++;if(!value)throw new IllegalStateException(message);}
   public static void main(String[] args){
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
      config.setInitialVisible(false);config.setWindowedMode(960,540);config.useVsync(false);
      new Lwjgl3Application(new Probe(),config);
      if(failure!=null){failure.printStackTrace();System.exit(1);}
      System.out.println("CINEMATIC OPENING PASS: checks="+checks+", captures="+captures+", full viewport, skippable opening, motion preferences, four visible spirit frames, dispose/reload");
      System.exit(0);
   }
   static final class Probe extends Game {
      int ticks;Probe(){super(new String[0],4);}
      @Override public void render(){if(ticks++!=2)return;try{
         actionStack.clear();modernUi.applyViewport(this,960,540);
         DrawSetupMenu setup=new DrawSetupMenu(this,null);modernUi.desktop.attach(setup);
         DesktopMenuController c=modernUi.desktop;c.screen=DesktopMenuController.Screen.TITLE;
         c.preferences.openingScene=true;c.preferences.reducedMotion=false;
         for(float time:new float[]{.8f,3.5f,5.9f}) {
            c.screenAge=time;c.clock=time;Gdx.gl.glClearColor(1,0,1,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            uiBatch.begin();check(StartupCinematic.render(modernUi,this,c),"Opening unexpectedly skipped");uiBatch.end();
            capture("opening-"+(int)(time*10)+".png",true);
         }
         c.preferences.reducedMotion=true;check(!StartupCinematic.active(c),"Reduced motion did not bypass camera movement");
         c.preferences.reducedMotion=false;c.preferences.openingScene=false;check(!StartupCinematic.active(c),"Disabled opening still active");
         c.preferences.openingScene=true;c.screenAge=StartupCinematic.DURATION;check(!StartupCinematic.active(c),"Opening cannot finish");
         c.screenAge=.5f;c.screen=DesktopMenuController.Screen.HOME;check(!StartupCinematic.active(c),"Opening appeared on home");
         Gdx.gl.glClearColor(1,0,1,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
         uiBatch.begin();modernUi.renderStartup(this,setup);uiBatch.end();capture("menu-home.png",true);
         MenuMotion motion=new MenuMotion();motion.screen("one",0);
         check(motion.reveal(0,false)==0&&motion.reveal(.5f,false)==1,"Entrance easing duration");
         check(motion.reveal(0,true)==1,"Reduced-motion entrance not immediate");
         float previous=motion.selection(1,1,true,0,false);
         for(int i=1;i<8;i++){float next=motion.selection(1,1,true,i*.04f,false);check(next>=previous&&next<=1,"Selection not monotone");previous=next;}
         check(motion.selection(1,1,false,1,true)==0,"Reduced selection did not snap");
         check(motion.selection(1,1,true,1,true)==1,"Reduced selection target incorrect");
         check(GhostVisualAssets.frameIndex(Float.NaN)==0&&GhostVisualAssets.frameIndex(-1)==0,"Invalid spirit clock");
         Set<Integer> hashes=new HashSet<>();
         for(int index=0;index<4;index++){
            TextureRegion region=GhostVisualAssets.get().frame(index*.3f+.01f);
            check(GhostVisualAssets.frameIndex(index*.3f+.01f)==index,"Ghost frame inaccessible");
            TextureData data=region.getTexture().getTextureData();if(!data.isPrepared())data.prepare();Pixmap source=data.consumePixmap();
            int opaque=0,transparent=0,hash=1;
            for(int y=0;y<region.getRegionHeight();y++)for(int x=0;x<region.getRegionWidth();x++){
               int rgba=source.getPixel(region.getRegionX()+x,region.getRegionY()+y);hash=31*hash+rgba;
               if((rgba&255)>128)opaque++;if((rgba&255)==0)transparent++;
            }
            if(data.disposePixmap())source.dispose();
            check(opaque>20000&&transparent>20000,"Spirit missing visible body or transparent margins");hashes.add(hash);
            Gdx.gl.glClearColor(.03f,.04f,.1f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);uiBatch.begin();
            uiBatch.setColor(Color.WHITE);uiBatch.draw(region,55,25,50,67);uiBatch.end();capture("spirit-"+index+".png",false);
         }
         check(hashes.size()==4,"Spirit frames are duplicates");
         GhostVisualAssets.disposeShared();check(GhostVisualAssets.get().frame(0)!=null,"Spirit texture did not recover after dispose");
         StartupCinematic.disposeShared();c.screen=DesktopMenuController.Screen.TITLE;c.screenAge=1;
         uiBatch.begin();check(StartupCinematic.render(modernUi,this,c),"Opening did not recover after dispose");uiBatch.end();
         check(actionStack.isEmpty(),"Opening changed gameplay actions");
         check(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"GL error during cinematic/ghost rendering");
      }catch(Throwable error){failure=error;}finally{Gdx.app.exit();}}
      void capture(String path,boolean coverage){
         Pixmap pixels=ScreenUtils.getFrameBufferPixmap(0,0,960,540);
         if(coverage){int empty=0;for(int y=0;y<540;y+=7)for(int x=0;x<960;x+=7)if(pixels.getPixel(x,y)==0xff00ffff)empty++;check(empty==0,"Uncovered viewport pixels");}
         PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);
         try{png.write(Gdx.files.local(path),pixels);}catch(java.io.IOException error){throw new RuntimeException(error);}
         finally{png.dispose();pixels.dispose();}captures++;
      }
   }
}
