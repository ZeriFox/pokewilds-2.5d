package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;

/** Real encounter actions, full-window coverage and a stationary reduced-motion alternative. */
public final class BattleEntryTest {
   public static void main(String[] args) {
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      TestGame game=new TestGame();
      Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
      config.setInitialVisible(false);config.setWindowedMode(640,576);
      config.setForegroundFPS(120);config.useVsync(false);
      new Lwjgl3Application(game,config);
      require(game.complete,"Incomplete battle entry regression");
      System.out.println("BATTLE ENTRY PASS sizes=4 motionModes=2 originalIntroFrames=74 originalWipeFrames=70"
         +" coveredFrames="+game.checkedFrames+" captures="+game.captures+" reducedMotion=stationaryFade");
      System.exit(0);
   }

   private static void require(boolean condition,String reason) {
      if(!condition)throw new IllegalStateException(reason);
   }

   static final class TestGame extends Game {
      final int[][] sizes={{640,576},{1280,720},{420,840},{1440,400}};
      int sizeIndex,wait=2,checkedFrames,captures;boolean requested,complete;
      JohtoRenderer world;
      TestGame(){super(new String[0],4);}
      @Override public void create() {
         super.create();actionStack.clear();Gdx.input.setInputProcessor(null);
         try {
            java.lang.reflect.Field field=Game.class.getDeclaredField("johtoRenderer");field.setAccessible(true);
            world=(JohtoRenderer)field.get(this);
         }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
         server=new Server();Network.register(server);
         map=new PkmnMap("battle-entry-fixture");map.currBiome="forest";
         for(int y=-320;y<=320;y+=16)for(int x=-320;x<=320;x+=16) {
            Vector2 position=new Vector2(x,y);map.tiles.put(position,new Tile("green1",position.cpy(),true));
         }
         map.bottomLeft=new Vector2(-320,-320);map.topRight=new Vector2(320,320);
         map.minimap=new Pixmap(80,80,Pixmap.Format.RGBA8888);
         player.position.set(0,0);player.spawnLoc.set(0,0);player.name="Entry fixture";
         start();player.setCurrPokemon();actionStack.clear();
         battle.oppPokemon=new Pokemon("charizard",25);player.currPokemon=new Pokemon("machop",20);
      }
      @Override public void render() {
         if(sizeIndex==sizes.length){complete=true;Gdx.app.exit();return;}
         if(!requested){Gdx.graphics.setWindowedMode(sizes[sizeIndex][0],sizes[sizeIndex][1]);requested=true;wait=2;return;}
         if(wait-->0)return;
         for(boolean reduced:new boolean[]{false,true})exercise(reduced);
         sizeIndex++;requested=false;
      }
      void exercise(boolean reduced) {
         modernUi.desktop.preferences.reducedMotion=reduced;
         actionStack.clear();player.dontDrawMapDuringBattle=false;
         clear();require(world.render(this),"No modern world snapshot");
         johtoBattleRenderer.prepareFrame(this,true);uiBatch.begin();uiBatch.end();johtoBattleRenderer.finishFrame(this);
         Action completed=new Action(){@Override public void step(Game game){}};
         BattleIntroAnim1 wipe=new BattleIntroAnim1(completed);
         BattleIntro intro=new BattleIntro(wipe);
         require(intro.frames.size()==74&&wipe.frames.size()==70,"Original action durations changed");
         actionStack.add(intro);
         String mode=reduced?"still":"motion";
         float lastDim=Float.POSITIVE_INFINITY;
         for(int frame=0;frame<74;frame++) {
            draw(intro);require(intro.frames.size()==73-frame,"Intro action skipped or repeated a frame");
            Pixmap pixels=read();covered(pixels);
            // The reduced-motion capture stays stationary. With the lens push,
            // a pixel can cross a bright leaf; measure the outer bands together
            // so actual full-window bright pulses still fail the regression.
            float luminance=reduced?luminance(pixels.getPixel(pixels.getWidth()/3,pixels.getHeight()/8)):outerLuminance(pixels);
            require(luminance<=lastDim+.012f,"Encounter intro flashed brighter at frame "+frame);
            lastDim=luminance;
            if(frame==0||frame==25||frame==65||frame==73)capture(pixels,"entry-"+sizeIndex+"-"+mode+"-intro-"+frame+".png");
            if(frame==73)solidInk(pixels);
            pixels.dispose();
         }
         require(actionStack.contains(wipe)&&!actionStack.contains(intro),"Intro did not hand off normally");
         Pixmap arena=null,partial=null;
         for(int frame=0;frame<70;frame++) {
            draw(wipe);require(wipe.frames.size()==69-frame,"Wipe action skipped or repeated a frame");
            Pixmap pixels=read();covered(pixels);
            if(frame==0)solidInk(pixels);
            if(frame==20&&reduced)partial=copy(pixels);
            if(frame==69)arena=copy(pixels);
            if(frame==0||frame==20||frame==40||frame==69)capture(pixels,"entry-"+sizeIndex+"-"+mode+"-arena-"+frame+".png");
            pixels.dispose();
         }
         require(actionStack.contains(wipe),"Wipe lost its original final handoff frame");
         draw(wipe);require(actionStack.contains(completed)&&!actionStack.contains(wipe),"Wipe handoff changed");
         if(reduced) {
            // A global fade yields the same interpolation factor at many
            // distant samples. A moving curtain, even if slow, fails this.
            Float reference=null;
            for(int y=0;y<arena.getHeight();y+=Math.max(1,arena.getHeight()/7))
               for(int x=0;x<arena.getWidth();x+=Math.max(1,arena.getWidth()/9)) {
                  int end=arena.getPixel(x,y),middle=partial.getPixel(x,y),ink=Color.rgba8888(ModernUi.INK);
                  for(int shift:new int[]{8,16,24}) {
                     int a=(end>>>shift)&255,b=(ink>>>shift)&255;
                     if(Math.abs(a-b)<60)continue;
                     float fraction=(((middle>>>shift)&255)-b)/(float)(a-b);
                     if(reference==null)reference=fraction;
                     else require(Math.abs(fraction-reference)<.04f,"Reduced-motion reveal moved instead of fading");
                  }
               }
            require(reference!=null&&reference>.05f&&reference<.95f,"Reduced-motion reveal sample missing");
            partial.dispose();
         }
         arena.dispose();
         require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"OpenGL error");
      }
      void draw(Action action) {
         clear();Matrix4 projection=new Matrix4(uiBatch.getProjectionMatrix());
         johtoBattleRenderer.prepareFrame(this,false);uiBatch.setColor(Color.WHITE);uiBatch.begin();
         action.step(this);uiBatch.end();johtoBattleRenderer.finishFrame(this);
         require(java.util.Arrays.equals(projection.val,uiBatch.getProjectionMatrix().val),"Transition changed menu projection");
      }
      void clear(){Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);ScreenUtils.clear(1,0,1,1);}
      Pixmap read(){return ScreenUtils.getFrameBufferPixmap(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());}
      Pixmap copy(Pixmap p){Pixmap c=new Pixmap(p.getWidth(),p.getHeight(),Pixmap.Format.RGBA8888);c.drawPixmap(p,0,0);return c;}
      void covered(Pixmap pixels) {
         for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++)
            if((pixels.getPixel(x,y)>>>8)==0xff00ff)throw new IllegalStateException("Uncovered encounter pixel at "+x+","+y);
         checkedFrames++;
      }
      void solidInk(Pixmap pixels) {
         int target=Color.rgba8888(ModernUi.INK);
         for(int y=0;y<pixels.getHeight();y+=7)for(int x=0;x<pixels.getWidth();x+=7)
            for(int shift:new int[]{8,16,24})if(Math.abs((pixels.getPixel(x,y)>>>shift&255)-(target>>>shift&255))>=3)
               throw new IllegalStateException("Transition boundary has a gap at "+x+","+y);
      }
      float luminance(int rgba){return (rgba>>>24&255)*.2126f/255+(rgba>>>16&255)*.7152f/255+(rgba>>>8&255)*.0722f/255;}
      float outerLuminance(Pixmap pixels) {
         float sum=0;int count=0;
         for(int y=0;y<pixels.getHeight();y+=4) {
            if(y>pixels.getHeight()/4&&y<pixels.getHeight()*3/4)continue;
            for(int x=0;x<pixels.getWidth();x+=4){sum+=luminance(pixels.getPixel(x,y));count++;}
         }
         return sum/Math.max(1,count);
      }
      void capture(Pixmap pixels,String path) {
         PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);
         try{png.write(Gdx.files.local(path),pixels);}catch(java.io.IOException e){throw new IllegalStateException(e);}finally{png.dispose();}
         captures++;
      }
   }
}
