package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/** Both renderers consume the same frozen world state and clock, in the same GL context. */
public final class WorldRendererParityTest {
   public static void main(String[] args) {
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      Probe probe=new Probe();
      Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
      cfg.setWindowedMode(640,576);cfg.setInitialVisible(false);cfg.useVsync(false);cfg.setForegroundFPS(60);
      cfg.setTitle("Pre/post world renderer pixel parity");
      try {
         new Lwjgl3Application(probe,cfg);
         if(!probe.complete)throw new IllegalStateException("Parity probe incomplete");
         System.out.println("WORLD RENDERER PARITY PASS: six scenes, zero differing RGBA bytes against pinned Java baseline");
         System.exit(0);
      } catch(Throwable ex) {ex.printStackTrace();System.exit(1);}
   }
   private static final class Probe extends Game {
      int frame;
      boolean complete;
      ReferenceJohtoRenderer reference;
      HashMap<Vector2,Tile> exterior,interior;
      Probe(){super(new String[0],4);}
      void put(String ground,String upper,int x,int y,String biome) {
         Tile tile=new Tile(ground,upper,new Vector2(x,y),true);tile.biome=biome;map.tiles.put(tile.position,tile);
      }
      void fixture() {
         actionStack.removeIf(a->a instanceof DrawSetupMenu);
         Gdx.input.setInputProcessor(null);server=new Server();Network.register(server);
         map=new PkmnMap("mesh-parity-fixture");Game.rand.setSeed(77129);map.rand.setSeed(77129);
         for(int y=-320;y<=320;y+=16)for(int x=-320;x<=320;x+=16)
            put(x>80?"volcano1":x<-80?"water1":y>32?"mountain3":"green1","",x,y,x>80?"volcano":"grassland");
         for(int x=-64;x<=64;x+=16)put("mountain3",x==0||x==16?"":"ledges3_N",x,32,"mountain");
         put("ledge_grass_ramp","",0,32,"mountain");put("ledge_grass_ramp","",16,32,"mountain");
         for(int x=96;x<=240;x+=16)for(int y=-96;y<=96;y+=16) {
            boolean path=y==0||x==160;
            put(path?"volcano2":Math.abs(y)==32?"lava1":"volcano1",!path&&y==64&&x%48==0?"rock_volcano1":"",x,y,"volcano");
         }
         put("green1","tree1",-48,-16,"grassland");put("green1","tree2",48,0,"grassland");
         put("mountain3","tree4",-64,64,"mountain");put("green1","rock1",-32,16,"grassland");put("green1","flower1",32,-16,"grassland");
         map.bottomLeft=new Vector2(-320,-320);map.topRight=new Vector2(320,320);
         map.minimap=new Pixmap(80,80,Pixmap.Format.RGBA8888);map.minimap.setColor(.3f,.6f,.3f,1);map.minimap.fill();
         player.position.set(0,0);player.spawnLoc.set(0,0);player.name="MeshParity";cam.position.set(8,8,0);
         start();player.setCurrPokemon();actionStack.removeIf(a->a instanceof CycleDayNight);
         exterior=map.tiles;interior=new HashMap<>();
         for(int y=-80;y<=112;y+=16)for(int x=-96;x<=96;x+=16) {
            Tile t=new Tile(y==112||x==-96||x==96?"interiorwall1":"house5_floor1",new Vector2(x,y),true);interior.put(t.position,t);
         }
         Tile couch=new Tile("house5_floor1","house_couch1",new Vector2(-32,32),true);interior.put(couch.position,couch);
         Tile plant=new Tile("house5_floor1","house_plant1",new Vector2(48,48),true);interior.put(plant.position,plant);
         reference=new ReferenceJohtoRenderer();
      }
      @Override public void render() {
         super.render();frame++;
         if(frame==20)fixture();
         if(frame==45) {
            try {
               compare("01-day-relief",exterior,0,false);
               compare("02-night-relief",exterior,0,true);
               compare("03-volcano-day",exterior,160,false);
               compare("04-volcano-night",exterior,160,true);
               compare("05-interior",interior,0,false);
               compare("06-exterior-return",exterior,0,false);
               complete=true;Gdx.app.exit();
            } catch(Exception ex) {throw new RuntimeException(ex);}
         }
         if(frame>90)throw new IllegalStateException("Parity fixture timeout");
      }
      void compare(String name,HashMap<Vector2,Tile> tiles,float x,boolean night) throws Exception {
         map.tiles=tiles;map.refreshCache=true;map.timeOfDay=night?"night":"day";
         mapBatch.setColor(night?PkmnMap.nightColor:Color.WHITE);
         player.position.set(x,0);cam.position.set(x+8,8,0);
         Field field=Game.class.getDeclaredField("johtoRenderer");field.setAccessible(true);
         JohtoRenderer current=(JohtoRenderer)field.get(this);
         freeze(current);freeze(reference);
         Gdx.gl.glViewport(0,0,640,576);
         if(!reference.render(this))throw new IllegalStateException("Reference skipped "+name);
         byte[] before=ScreenUtils.getFrameBufferPixels(0,0,640,576,false);capture(name+"-before.png");
         Gdx.gl.glViewport(0,0,640,576);
         if(!current.render(this))throw new IllegalStateException("Candidate skipped "+name);
         byte[] after=ScreenUtils.getFrameBufferPixels(0,0,640,576,false);capture(name+"-after.png");
         if(!Arrays.equals(before,after)) {
            int different=0;for(int i=0;i<before.length;i++)if(before[i]!=after[i])different++;
            throw new IllegalStateException(name+": "+different+" differing RGBA bytes");
         }
         if(Gdx.gl.glGetError()!=GL20.GL_NO_ERROR)throw new IllegalStateException("GL error in "+name);
         System.out.println("PARITY "+name+" rgbaSha256="+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(after))+" differingBytes=0");
      }
      void freeze(Object renderer) throws Exception {
         Field time=renderer.getClass().getDeclaredField("seconds");time.setAccessible(true);time.setFloat(renderer,2f);
         Field movement=renderer.getClass().getDeclaredField("motion");movement.setAccessible(true);((Map<?,?>)movement.get(renderer)).clear();
      }
      void capture(String name) throws Exception {
         Pixmap pixels=ScreenUtils.getFrameBufferPixmap(0,0,640,576);
         PixmapIO.PNG writer=new PixmapIO.PNG();writer.setFlipY(true);
         try {writer.write(Gdx.files.local(name),pixels);}finally{writer.dispose();pixels.dispose();}
      }
      @Override public void dispose() {
         try {if(reference!=null)reference.dispose();}finally{super.dispose();}
      }
   }
}
