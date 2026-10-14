package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.lang.reflect.Field;
import java.util.*;

/** Real throw + three wobble success/failure actions in an isolated unsaved arena. */
public final class BattleCaptureOrbitTest {
   static Throwable failure;static int checks,frames,cases;
   static void check(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException(why);}
   static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
   public static void main(String[] args){
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;Probe probe=new Probe();
      Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();cfg.setInitialVisible(false);cfg.setWindowedMode(960,540);cfg.useVsync(false);cfg.setForegroundFPS(0);
      new Lwjgl3Application(probe,cfg);if(failure!=null){failure.printStackTrace();System.exit(1);}
      check(probe.complete,"Incomplete capture fixture");
      System.out.println("BATTLE CAPTURE ORBIT PASS: checks="+checks+", cases="+cases+", actionFrames="+frames+", four ball identities, all camera quadrants, original throw/wobble schedules, success/failure outcomes, RNG unchanged");System.exit(0);
   }
   static final class Probe extends Game {
      boolean ready,complete;int boot;Pokemon own;
      Probe(){super(new String[0],4);}
      void setup(){
         Gdx.input.setInputProcessor(null);actionStack.clear();server=new Server();Network.register(server);
         map=new PkmnMap("capture-orbit-fixture");map.bottomLeft=new Vector2(-64,-64);map.topRight=new Vector2(64,64);map.minimap=new Pixmap(16,16,Pixmap.Format.RGBA8888);
         for(int y=-64;y<=64;y+=16)for(int x=-64;x<=64;x+=16){Vector2 p=new Vector2(x,y);map.tiles.put(p,new Tile("green1",p.cpy(),true));}
         player.position.set(0,0);player.spawnLoc.set(0,0);player.name="Capture";start();player.setCurrPokemon();actionStack.clear();
         own=new Pokemon("machop",10);own.backSprite.setPosition(16,48);player.currPokemon=own;
         battle.drawAction=new DrawBattle(this);player.dontDrawMapDuringBattle=true;player.battleSprite.setPosition(160,48);Game.catchExpEnabled=false;
         map.currBiome="forest";ready=true;
      }
      @Override public void render(){try{
         if(!ready){super.render();if(++boot<15)return;setup();}
         String[] balls={"poke ball","great ball","ultra ball","dusk ball"};
         for(int angle=0;angle<4;angle++)for(boolean success:new boolean[]{false,true}){
            player.pokemon.clear();player.pokemon.add(own);
            Pokemon enemy=new Pokemon("pikachu",10);enemy.sprite.setPosition(96,88);battle.oppPokemon=enemy;
            CaptureBallVisual.select(this,balls[angle]);check(CaptureBallVisual.selected(this).equals(balls[angle]),"Wrong selected capture identity");
            DrawBattle.shouldDrawOppPokemon=true;DrawBattle.shouldDrawOwnPokemon=true;
            float clock=angle*12;
            runAction(new ThrowPokeball(this,null),clock,"throw-"+angle+"-"+success);
            check(player.pokemon.size()==1,"Throw changed party before capture result");
            Action wobble=success?new CatchPokemonWobblesThenCatch(this,balls[angle],null):new CatchPokemonWobbles3Times(this,null);
            runAction(wobble,clock+2,"wobble-"+angle+"-"+success);
            check(player.pokemon.contains(enemy)==success,"Capture outcome changed for "+balls[angle]+" success="+success);
            check(player.pokemon.size()==(success?2:1),"Party mutation mismatch");
            if(success)check(enemy.previousOwner==player,"Captured owner missing");
            else check(DrawBattle.shouldDrawOppPokemon,"Failed capture did not restore opponent");
            cases++;
         }
         complete=true;Gdx.app.exit();
      }catch(Throwable e){failure=e;Gdx.app.exit();}}
      void runAction(Action action,float clock,String label)throws Exception {
         actionStack.clear();actionStack.add(battle.drawAction);actionStack.add(action);action.firstStep(this);
         @SuppressWarnings("unchecked") ArrayList<Integer> schedule=(ArrayList<Integer>)field(action.getClass(),"repeats").get(action);
         int expected=1;for(int count:schedule)expected+=count+1;
         Game.rand.setSeed(1977);long mapBefore=ModernWorldGenerator.fingerprint(map.tiles);
         int steps=0;
         while(actionStack.contains(action)&&steps<1000){
            johtoBattleRenderer.prepareFrame(this,false);
            field(JohtoBattleRenderer.class,"scenerySeconds").setFloat(johtoBattleRenderer,clock+steps/60f);
            float yaw=johtoBattleRenderer.cameraYaw(this);
            uiBatch.begin();johtoBattleRenderer.drawBackground(this);own.backSprite.draw(uiBatch);
            if(DrawBattle.shouldDrawOppPokemon)battle.oppPokemon.sprite.draw(uiBatch);
            action.step(this);uiBatch.end();johtoBattleRenderer.finishFrame(this);
            check(yaw==johtoBattleRenderer.cameraYaw(this),"Capture action reset camera");
            check(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"Capture GL error");
            if(steps==25&&label.startsWith("wobble")&&label.endsWith("true")){
               Pixmap pixels=ScreenUtils.getFrameBufferPixmap(0,0,960,540);PixmapIO.writePNG(Gdx.files.local(label+".png"),pixels,-1,true);pixels.dispose();
            }
            steps++;frames++;
         }
         check(steps==expected,"Authored action timing changed "+label+": "+steps+" != "+expected);
         check(Game.rand.nextLong()==new Random(1977).nextLong(),"Rendering consumed capture RNG "+label);
         check(mapBefore==ModernWorldGenerator.fingerprint(map.tiles),"Capture animation changed terrain");
      }
   }
}
