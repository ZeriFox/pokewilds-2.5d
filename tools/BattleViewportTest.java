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

/** GPU regression: real resize, complete actors, original cleanup and every exit frame. */
public final class BattleViewportTest {
   public static void main(String[] args) {
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      TestGame game=new TestGame();
      Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
      config.setWindowedMode(640,576); config.setInitialVisible(false);
      config.setTitle("Battle viewport regression"); config.setForegroundFPS(60); config.useVsync(false);
      new Lwjgl3Application(game,config);
      if(!game.complete)throw new IllegalStateException("Incomplete battle viewport test");
      System.out.println("BATTLE VIEWPORT PASS sizes=5 biomes=6 zooms=5 exitFrames="+game.exitFrames
         +" captures="+game.captures+" panoramaMarginPixels="+game.checkedPixels+" missingWorldGap=opaque");
   }

   static final class TestGame extends Game {
      final int[][] sizes={{640,576},{1280,720},{420,840},{1440,400},{321,289}};
      final String[] biomes={"forest","cave","volcano","desert","snow","graveyard"};
      int sizeIndex,settle=2,captures,exitFrames; long checkedPixels; boolean requested,complete;
      Pokemon own,enemy;
      JohtoRenderer worldRenderer;
      Pixmap expectedArena;
      TestGame(){super(new String[0],4);}
      @Override public void create(){
         super.create(); actionStack.clear(); Gdx.input.setInputProcessor(null);
         try { java.lang.reflect.Field field=Game.class.getDeclaredField("johtoRenderer");field.setAccessible(true);worldRenderer=(JohtoRenderer)field.get(this); }
         catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}
         server=new Server(); Network.register(server);
         map=new PkmnMap("battle-viewport-fixture");
         for(int y=-320;y<=320;y+=16)for(int x=-320;x<=320;x+=16){
            Vector2 pos=new Vector2(x,y); map.tiles.put(pos,new Tile("green1",pos.cpy(),true));
         }
         map.bottomLeft=new Vector2(-320,-320); map.topRight=new Vector2(320,320);
         map.minimap=new Pixmap(80,80,Pixmap.Format.RGBA8888);
         player.position.set(0,0);player.spawnLoc.set(0,0);player.name="Viewport";
         start();player.setCurrPokemon();actionStack.clear();
         own=new Pokemon("machop",10);enemy=new Pokemon("charizard",10);
         player.currPokemon=own;battle.oppPokemon=enemy;
         own.backSprite.setPosition(16,48);enemy.sprite.setPosition(96,88);
      }
      @Override public void render(){
         if(sizeIndex==sizes.length){complete=true;Gdx.app.exit();return;}
         int width=sizes[sizeIndex][0],height=sizes[sizeIndex][1];
         if(!requested){Gdx.graphics.setWindowedMode(width,height);requested=true;settle=2;return;}
         if(settle-->0)return;
         if(Gdx.graphics.getBackBufferWidth()!=width||Gdx.graphics.getBackBufferHeight()!=height)
            throw new IllegalStateException("Resize not applied");
         cam.zoom=.6f+sizeIndex*.55f;
         for(String biome:biomes){
            map.currBiome=biome; actionStack.clear();player.dontDrawMapDuringBattle=false;
            clearPoison();if(!worldRenderer.render(this))throw new IllegalStateException("Modern world unavailable");
            johtoBattleRenderer.prepareFrame(this,true);uiBatch.begin();uiBatch.end();johtoBattleRenderer.finishFrame(this);
            DrawBattle draw=new DrawBattle(this);actionStack.add(draw);draw.firstStep(this);
            DrawBattle.shouldDrawOppPokemon=true;DrawBattle.hideOppPokemon=false;
            player.battleSprite.setPosition(160,49);
            drawScene(draw);inspect("arena-"+sizeIndex+"-"+biome+".png",true,true);
            if(biome.equals("forest")){
               player.battleSprite.setPosition(18,49);own.backSprite.setAlpha(0);
               drawScene(draw);inspect("trainer-"+sizeIndex+".png",true,true);own.backSprite.setAlpha(1);
            }
            // The trainer's original slide crosses the screen edge. Its pixels
            // and attack translations must never appear outside the actor canvas.
            player.battleSprite.setPosition(-20,49);enemy.sprite.setX(156);
            drawScene(draw);inspect(null,true,false);enemy.sprite.setX(96);
            player.battleSprite.setPosition(-96,49);
            if(biome.equals("forest"))exitBattle(draw);
            else draw.cleanup(this);
         }
         if(Gdx.gl.glGetError()!=GL20.GL_NO_ERROR)throw new IllegalStateException("OpenGL error");
         sizeIndex++;requested=false;
      }
      void drawScene(DrawBattle draw){
         clearPoison();Matrix4 original=new Matrix4(uiBatch.getProjectionMatrix());
         johtoBattleRenderer.prepareFrame(this,false);uiBatch.setColor(Color.WHITE);uiBatch.begin();
         johtoBattleRenderer.drawBackground(this);uiBatch.end();
         if(expectedArena!=null)expectedArena.dispose();
         expectedArena=ScreenUtils.getFrameBufferPixmap(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());
         uiBatch.begin();
         draw.step(this);own.backSprite.draw(uiBatch);
         johtoBattleRenderer.drawCommandMenu(this,"tl",false);
         uiBatch.end();johtoBattleRenderer.finishFrame(this);
         if(!java.util.Arrays.equals(original.val,uiBatch.getProjectionMatrix().val))
            throw new IllegalStateException("Battle changed the persistent UI projection");
      }
      void exitBattle(DrawBattle draw){
         actionStack.clear();actionStack.add(draw);
         BattleFadeOut fade=new BattleFadeOut(this,null);actionStack.add(fade);
         fade.firstStep(this);
         if(player.dontDrawMapDuringBattle||battle.drawAction!=null)throw new IllegalStateException("Original cleanup failed");
         for(int frame=0;frame<34;frame++){
            clearPoison();boolean world=worldRenderer.render(this);
            if(!world)throw new IllegalStateException("No modern world during battle return");
            johtoBattleRenderer.prepareFrame(this,world);uiBatch.setColor(Color.WHITE);uiBatch.begin();
            boolean active=actionStack.contains(fade);if(active)fade.step(this);
            uiBatch.end();johtoBattleRenderer.finishFrame(this);
            inspect(sizeIndex==1||frame==0||frame==33?"exit-"+sizeIndex+"-"+frame+".png":null,false,true);
            exitFrames++;
         }
         if(actionStack.contains(fade)||!johtoBattleRenderer.getPhase().equals("none"))
            throw new IllegalStateException("Fade did not complete");
         // One frame with neither a world nor an action must still be opaque.
         clearPoison();johtoBattleRenderer.prepareFrame(this,false);uiBatch.begin();uiBatch.end();johtoBattleRenderer.finishFrame(this);
         inspect("gap-"+sizeIndex+".png",false,true);
      }
      void clearPoison(){
         Gdx.gl.glViewport(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());
         Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);ScreenUtils.clear(1,0,1,1);
      }
      void inspect(String name,boolean canvas,boolean noPoison){
         int w=Gdx.graphics.getBackBufferWidth(),h=Gdx.graphics.getBackBufferHeight();
         Pixmap pixels=ScreenUtils.getFrameBufferPixmap(0,0,w,h);
         float scale=Math.min(w/160f,h/144f),left=(w-160*scale)/2,bottom=(h-144*scale)/2;
         for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int rgba=pixels.getPixel(x,y);
            if(noPoison&&(rgba>>>8)==0xFF00FF)throw new IllegalStateException("Uncovered framebuffer at "+x+","+y+" "+name);
            if(canvas&&(x+.5f<left-.5f||x+.5f>w-left+.5f||y+.5f<bottom-.5f||y+.5f>h-bottom+.5f)){
               checkedPixels++;
               int expected=expectedArena.getPixel(x,y);
               for(int shift:new int[]{8,16,24})if(Math.abs((rgba>>>shift&255)-(expected>>>shift&255))>3)
                  throw new IllegalStateException("Actor/effect leaked into panorama margin at "+x+","+y+" "+name);
            }
         }
         if(name!=null){
            PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);
            try{png.write(Gdx.files.local(name),pixels);}catch(java.io.IOException ex){throw new RuntimeException(ex);}finally{png.dispose();}
            captures++;System.out.println("BATTLE CAPTURE "+name);
         }
         pixels.dispose();
      }
   }
}
