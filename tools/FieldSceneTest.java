package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.*;
import javax.imageio.ImageIO;

/** Actual Game.render + original field-menu activation and movement, isolated save directory. */
public final class FieldSceneTest {
   public static void main(String[] args) {
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      Sample game=new Sample();
      Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
      cfg.setWindowedMode(640,576);cfg.setInitialVisible(false);cfg.setForegroundFPS(60);cfg.setIdleFPS(60);cfg.useVsync(false);
      try {new Lwjgl3Application(game,cfg);if(!game.complete)throw new IllegalStateException("Incomplete field test");
         System.out.println("FIELD PASS: exclusive modern world, original POWER/BUILD/DIG/SURF/FLY activation, real Surf movement, previews, light, poses");System.exit(0);
      }catch(Throwable ex){ex.printStackTrace();System.exit(1);}
   }
   private static final class Sample extends Game {
      int frame;boolean ready,complete;long previous;Pokemon electric,water,bird;
      Sample(){super(new String[0],4);}
      void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
      JohtoRenderer renderer(){try{java.lang.reflect.Field f=Game.class.getDeclaredField("johtoRenderer");f.setAccessible(true);return (JohtoRenderer)f.get(this);}catch(Exception e){throw new IllegalStateException(e);}}
      void fixture(){
         Input nativeInput=Gdx.input;
         Gdx.input=(Input)Proxy.newProxyInstance(Input.class.getClassLoader(),new Class<?>[]{Input.class},(p,m,a)->{
            if(m.getName().startsWith("isKey")||m.getName().startsWith("isButton")||m.getName().equals("isTouched"))return false;
            return m.invoke(nativeInput,a);
         });
         actionStack.removeIf(a->a instanceof DrawSetupMenu);server=new Server();Network.register(server);
         map=new PkmnMap("field-fixture");Game.rand.setSeed(77129);map.rand.setSeed(77129);
         for(int y=-320;y<=320;y+=16)for(int x=-320;x<=320;x+=16){
            Tile tile=new Tile(x>=16?"water1":"green1",new Vector2(x,y),true);tile.biome="grassland";map.tiles.put(tile.position,tile);
         }
         map.bottomLeft=new Vector2(-320,-320);map.topRight=new Vector2(320,320);
         map.minimap=new Pixmap(80,80,Pixmap.Format.RGBA8888);map.minimap.setColor(.3f,.6f,.3f,1);map.minimap.fill();
         player.position.set(0,0);player.spawnLoc.set(0,0);player.dirFacing="left";player.name="Field";cam.position.set(8,8,0);
         start();player.setCurrPokemon();actionStack.removeIf(a->a instanceof CycleDayNight);
         electric=new Pokemon("pikachu",20);water=new Pokemon("lapras",20);bird=new Pokemon("pidgeot",20);
         electric.hms.add("FLASH");player.pokemon.add(electric);player.pokemon.add(water);player.pokemon.add(bird);
         ready=true;
      }
      void use(String move,Pokemon pokemon){
         DrawPokemonMenu menu=new DrawPokemonMenu(this,(Menu)null);
         DrawPokemonMenu.allPokemon=player.pokemon;DrawPokemonMenu.currIndex=player.pokemon.indexOf(pokemon);DrawPokemonMenu.scrollIndex=0;
         DrawPokemonMenu.SelectedMenu selected=new DrawPokemonMenu.SelectedMenu(menu,pokemon);
         Action next=selected.getAction(this,move,menu);
         require(next!=null && move.equals(player.currFieldMove),"Original menu activation failed: "+move);
         // Activation may restore the parent menu pending its normal dialogue outro.
         // This renderer fixture skips dialogue only, then drives the original movement.
         actionStack.removeIf(a->a instanceof Menu);
         System.out.println("FIELD activated "+move+" with "+pokemon.specie.name);
      }
      void stop(){
         if(player.hmPokemon!=null){
            if(!player.currFieldMove.isEmpty() && !player.currFieldMove.equals("FLY"))player.swapSprites(player.hmPokemon);
            player.hmPokemon.removeDrawActions(this);
         }
         actionStack.removeIf(a->a.getClass().getName().contains("$Follow")||a.getClass().getName().contains("$Flying")||a instanceof PlayerMoving);
         player.hmPokemon=null;player.currFieldMove="";player.flyingAction=null;player.acceptInput=true;
         player.isFishing=false;player.isSleeping=false;player.isSitting=false;player.isCrafting=false;
         if(actionStack.stream().noneMatch(a->a instanceof PlayerStanding))insertAction(new PlayerStanding(this));
      }
      @Override public void render(){
         super.render();frame++;
         require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"GL error at "+frame);
         if(frame==20)fixture();
         if(!ready)return;
         WorldBatch batch=(WorldBatch)mapBatch;
         require(batch.getSubmittedDraws()==0,"Legacy world submitted pixels at "+frame);
         if(frame>22){long now=renderer().getRenderedFrames();require(now>previous,"World fallback at "+frame+" move="+player.currFieldMove);previous=now;}
         if(frame==50)use("POWER",electric);
         if(frame==80)screenshot("01-power.png");
         if(frame==85){map.timeOfDay="night";mapBatch.setColor(PkmnMap.nightColor);}
         if(frame==110)screenshot("02-flash-night.png");
         if(frame==115){map.timeOfDay="day";mapBatch.setColor(Color.WHITE);use("BUILD",electric);}
         if(frame==140){screenshot("03-build.png");for(String key:player.buildTileRequirements.get(player.currBuildTile.name).keySet())player.setItemAmount(key,0);}
         if(frame==145){require(batch.getTargetTint().r>batch.getTargetTint().g,"Invalid build indicator not captured");screenshot("04-build-invalid.png");}
         if(frame==150)use("DIG",electric);
         if(frame==175)screenshot("05-dig.png");
         if(frame==180){stop();player.position.set(0,0);player.dirFacing="right";use("SURF",water);insertAction(new PlayerMoving(this,player,false,new SetField(player,"acceptInput",true,new PlayerStanding(this))));}
         if(frame==210){require(player.position.x==16,"Surf did not enter real water tile: "+player.position);screenshot("06-surf-enter.png");insertAction(new PlayerMoving(this,player,false,new PlayerStanding(this)));}
         if(frame==240){require(player.position.x==32,"Surf movement failed: "+player.position);screenshot("07-surf-move.png");}
         if(frame==245){stop();player.position.set(-16,0);player.dirFacing="down";cam.position.set(-8,8,0);use("FLY",bird);insertAction(player.new Flying(bird,true,null));}
         if(frame==300)screenshot("08-fly.png");
         if(frame==330){stop();player.position.set(0,0);player.dirFacing="right";cam.position.set(8,8,0);player.isFishing=true;}
         if(frame==350)screenshot("09-fishing.png");
         if(frame==355){player.isFishing=false;player.isSitting=true;}
         if(frame==375)screenshot("10-sitting.png");
         if(frame==380){player.isSitting=false;player.isSleeping=true;}
         if(frame==400)screenshot("11-sleeping.png");
         if(frame==405){player.isSleeping=false;player.isCrafting=true;}
         if(frame==425){screenshot("12-crafting.png");require(batch.getSuppressedDraws()>1000,"Suppression not exercised");
            require(renderer().getLoadedSpeciesCount()==0,"3D models unexpectedly loaded");
            System.out.println("FIELD modernFrames="+renderer().getRenderedFrames()+" discardedLegacyDraws="+batch.getSuppressedDraws()+" submitted="+batch.getSubmittedDraws());complete=true;Gdx.app.exit();}
      }
      void screenshot(String name){
         byte[] rgba=ScreenUtils.getFrameBufferPixels(0,0,640,576,false);BufferedImage image=new BufferedImage(640,576,BufferedImage.TYPE_INT_RGB);Set<Integer>colors=new HashSet<>();
         for(int i=0;i<rgba.length;i+=4){int rgb=(rgba[i]&255)<<16|(rgba[i+1]&255)<<8|rgba[i+2]&255;colors.add(rgb);image.setRGB(i/4%640,575-i/4/640,rgb);}
         require(colors.size()>12,"Empty frame "+name);
         try{ImageIO.write(image,"png",new File(name));}catch(Exception e){throw new IllegalStateException(e);}
      }
   }
}
