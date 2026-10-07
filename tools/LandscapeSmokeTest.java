package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
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
import java.lang.reflect.Field;
import java.util.*;
import javax.imageio.ImageIO;

/** Isolated native landscape fixture, real map actions and original travel action. */
public final class LandscapeSmokeTest {
   public static void main(String[] args) {
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      Sample game=new Sample();
      Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
      cfg.setWindowedMode(640,576);cfg.setInitialVisible(false);cfg.setForegroundFPS(60);cfg.setIdleFPS(60);cfg.useVsync(false);
      cfg.setTitle("B/W landscape native verification");
      try {
         new Lwjgl3Application(game,cfg);
         if(!game.complete || !game.disposed) throw new IllegalStateException("Incomplete lifecycle");
         System.out.println("LANDSCAPE PASS: day/night, relief/ramp, caldera, interior, real EnterBuilding/exit, PMD/BW GPU rendering");
         System.exit(0);
      } catch(Throwable ex) { ex.printStackTrace();System.exit(1); }
   }

   private static final class Sample extends Game {
      int frame; boolean ready,complete,disposed;
      long lastRendered;
      HashMap<Vector2,Tile> interior;
      Pokemon samplePokemon;
      Sample(){super(new String[0],4);}
      @Override public void create(){super.create();}
      void put(String ground,String upper,int x,int y,String biome) {
         Tile tile=new Tile(ground,upper,new Vector2(x,y),true);
         tile.biome=biome;map.tiles.put(tile.position,tile);
      }
      void semanticFixture() {
         for(int y=112;y<=288;y+=16)for(int x=-272;x<=-96;x+=16)put("green1","",x,y,"grassland");
         put("cave1_floor1","rock1",-224,192,"cave");
         put("cave1_floor1","chest1",-208,192,"cave");
         put("cave1_floor1","building1_fossilreviver1",-192,192,"cave");
         put("sand1","pkmnmansion_ext",-224,224,"grassland");
         put("sand1","pkmnmansion_ext_windows",-208,224,"grassland");
         put("sand1","pkmnmansion_ext_locked",-192,224,"grassland");
         put("green1","house5_middle1",-160,224,"grassland");
         put("cave1_regi1","",-144,224,"cave");
         put("green1","warp_tile1",-224,160,"grassland");
         put("green1","onpress_above",-208,160,"grassland");
         put("desert6","desert4_cracked",-192,160,"desert");
         put("green1","grass_planted",-160,160,"grassland");
         put("green1","berrytree_cheri_empty",-144,160,"grassland");
         put("green1","berrytree_cheri_full",-128,160,"grassland");
         cameraAt(-176,192);
         System.out.println("LANDSCAPE semantic fixture: cave rock/chest/machine, mansion/window/locked door, upper house, Regigigas, floor cues, planting");
      }
      void fixture() {
         actionStack.removeIf(a->a instanceof DrawSetupMenu);
         Gdx.input.setInputProcessor(null);server=new Server();Network.register(server);
         map=new PkmnMap("landscape-fixture");Game.rand.setSeed(77129);map.rand.setSeed(77129);
         for(int y=-320;y<=320;y+=16)for(int x=-320;x<=320;x+=16) {
            String base=x>80?"volcano1":x<-80?"water1":y>32?"mountain3":"green1";
            put(base,"",x,y,x>80?"volcano":"grassland");
         }
         for(int x=-64;x<=64;x+=16)put("mountain3",x==0||x==16?"":"ledges3_N",x,32,"mountain");
         put("ledge_grass_ramp","",0,32,"mountain");put("ledge_grass_ramp","",16,32,"mountain");
         for(int x=96;x<=240;x+=16)for(int y=-96;y<=96;y+=16) {
            boolean path=y==0 || x==160;
            put(path?"volcano2":Math.abs(y)==32?"lava1":"volcano1",!path&&y==64&&x%48==0?"rock_volcano1":"",x,y,"volcano");
         }
         put("green1","tree1",-48,-16,"grassland");put("green1","tree2",48,0,"grassland");
         put("mountain3","tree4",-64,64,"mountain");put("green1","rock1",-32,16,"grassland");
         put("green1","flower1",32,-16,"grassland");
         map.bottomLeft=new Vector2(-320,-320);map.topRight=new Vector2(320,320);
         map.minimap=new Pixmap(80,80,Pixmap.Format.RGBA8888);map.minimap.setColor(.3f,.6f,.3f,1);map.minimap.fill();
         player.position.set(0,0);player.spawnLoc.set(0,0);player.name="Landscape";cam.position.set(8,8,0);
         start();player.setCurrPokemon();
         samplePokemon=new Pokemon("machop",5);samplePokemon.position.set(32,-32);samplePokemon.mapTiles=map.tiles;
         samplePokemon.dirFacing="left";samplePokemon.aggroPlayer=false;insertAction(samplePokemon.new Standing());
         actionStack.removeIf(a->a instanceof CycleDayNight);
         interior=new HashMap<>();
         for(int y=-80;y<=112;y+=16)for(int x=-96;x<=96;x+=16){
            Tile t=new Tile(y==112||x==-96||x==96?"interiorwall1":"house5_floor1",new Vector2(x,y),true);interior.put(t.position,t);
         }
         Tile couch=new Tile("house5_floor1","house_couch1",new Vector2(-32,32),true);interior.put(couch.position,couch);
         Tile plant=new Tile("house5_floor1","house_plant1",new Vector2(48,48),true);interior.put(plant.position,plant);
         while(map.interiorTiles.size()<=1)map.interiorTiles.add(new HashMap<>());
         map.interiorTiles.set(1,interior);
         ready=true;System.out.println("LANDSCAPE fixture="+map.tiles.size()+", rampWalkable="+ModernWorldGenerator.isWalkable(map.tiles.get(new Vector2(0,32))));
      }
      JohtoRenderer renderer(){try{Field f=Game.class.getDeclaredField("johtoRenderer");f.setAccessible(true);return(JohtoRenderer)f.get(this);}catch(Exception e){throw new IllegalStateException(e);}}
      void cameraAt(float x,float y){player.position.set(x,y);cam.position.set(x+8,y+8,0);map.refreshCache=true;}
      @Override public void render() {
         super.render();frame++;
         if(Gdx.gl.glGetError()!=GL20.GL_NO_ERROR)throw new IllegalStateException("GL error at "+frame);
         if(frame==20)fixture();
         if(frame==23){((Pokemon.Standing)samplePokemon.standingAction).moveTimer=10000;samplePokemon.aggroPlayer=false;}
         if(!ready)return;
         if(frame>35) {
            long count=renderer().getRenderedFrames();
            if(count<=lastRendered) throw new IllegalStateException("Landscape fallback at "+frame+" actions="+actionStack);
            lastRendered=count;
         }
         if(frame==75)screenshot("01-day-relief.png");
         if(frame==80){map.timeOfDay="night";mapBatch.setColor(PkmnMap.nightColor);}
         if(frame==120)screenshot("02-night-relief.png");
         if(frame==130){map.timeOfDay="day";mapBatch.setColor(Color.WHITE);cameraAt(160,0);}
         if(frame==170)screenshot("03-caldera-day.png");
         if(frame==180){map.timeOfDay="night";mapBatch.setColor(PkmnMap.nightColor);}
         if(frame==215)screenshot("04-caldera-night.png");
         if(frame==230){map.timeOfDay="day";mapBatch.setColor(Color.WHITE);cameraAt(0,0);map.interiorTilesIndex=1;insertAction(new EnterBuilding(this,"enter",interior,new PlayerStanding(this)));}
         if(frame==235)screenshot("05-enter-fade.png");
         if(frame==290){require(map.tiles==interior,"EnterBuilding failed map switch");screenshot("06-interior.png");}
         if(frame==305)insertAction(new EnterBuilding(this,"exit",map.overworldTiles,new PlayerStanding(this)));
         if(frame==310)screenshot("07-exit-fade.png");
         if(frame==355){require(map.tiles==map.overworldTiles,"Exit failed map switch");screenshot("08-exterior-return.png");}
         if(frame==365)semanticFixture();
         if(frame==400)screenshot("09-semantic-objects.png");
         if(frame==405)cameraAt(-176,144);
         if(frame==435){screenshot("10-planting-floor-cues.png");
            require(renderer().getLoadedSpeciesCount()==0,"Unexpected 3D assets");
            require(PmdPokemonSprites.get().getLoadedTextureCount()>0,"PMD world sprite was not loaded");
            System.out.println("LANDSCAPE frames="+renderer().getRenderedFrames()+", PMD sheets="+PmdPokemonSprites.get().getLoadedTextureCount());complete=true;Gdx.app.exit();}
         if(frame>480)throw new IllegalStateException("Landscape timeout");
      }
      void screenshot(String name){
         byte[] rgba=ScreenUtils.getFrameBufferPixels(0,0,640,576,false);BufferedImage image=new BufferedImage(640,576,BufferedImage.TYPE_INT_RGB);Set<Integer> colors=new HashSet<>();
         for(int i=0;i<rgba.length;i+=4){int rgb=(rgba[i]&255)<<16|(rgba[i+1]&255)<<8|rgba[i+2]&255;colors.add(rgb);image.setRGB(i/4%640,575-i/4/640,rgb);}
         require(colors.size()>20,"Empty framebuffer "+name);
         try{ImageIO.write(image,"png",new File(name));}catch(Exception e){throw new IllegalStateException(e);}
         System.out.println("LANDSCAPE image="+name+", colors="+colors.size()+", modernFrames="+renderer().getRenderedFrames());
      }
      void require(boolean pass,String message){if(!pass)throw new IllegalStateException(message);}
      @Override public void dispose(){try{super.dispose();}finally{disposed=true;}}
   }
}
