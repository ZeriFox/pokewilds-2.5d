package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.audio.Music;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import com.badlogic.gdx.utils.BufferUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Real native Game.render pipeline and original MAP action, using an unsaved island. */
public final class MapPresentationTest {
   private static Throwable failure;
   private static int checks,captures;
   private static final int GRASS=0x36b55cff,WATER=0x286fc8ff,SAND=0xe4c46bff,LAVA=0xe85c36ff;
   private static void check(boolean value,String reason){checks++;if(!value)throw new IllegalStateException(reason);}
   public static void main(String[] args) {
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;Sample sample=new Sample();
      Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
      cfg.setInitialVisible(false);cfg.setWindowedMode(960,720);cfg.useVsync(false);
      cfg.setForegroundFPS(120);cfg.setIdleFPS(120);cfg.setTitle("World map native regression");
      new Lwjgl3Application(sample,cfg);
      if(failure!=null){failure.printStackTrace();System.exit(1);}
      check(sample.complete&&sample.disposed,"Incomplete map lifecycle");
      System.out.println("MAP PRESENTATION PASS: checks="+checks+", captures="+captures+", frames="+sample.frames
         +", actual MAP menu keyboard/pointer entry, land/water/sand/lava pixels, original pan/close/reopen, wide/portrait resize, warp cancellation, unchanged world/RNG; unsaved fixture only");
      System.exit(0);
   }
   static final class Sample extends Game {
      final Set<Integer> keys=new HashSet<>(),just=new HashSet<>(),buttons=new HashSet<>();
      final InputProcessor processor=new InputProcessor();Input real,fake;
      int px,py,frames;boolean complete,disposed,ready;DrawPlayerMenu parent;DrawMiniMap mapMenu;
      long terrainFingerprint,minimapFingerprint;float panX,panY,grassX,grassY;Vector2 position;
      Sample(){super(new String[0],5);}
      @Override public void create() {
         super.create();real=Gdx.input;
         fake=(Input)Proxy.newProxyInstance(Input.class.getClassLoader(),new Class<?>[]{Input.class},(proxy,method,args)->{
            switch(method.getName()) {
               case "isKeyPressed":return keys.contains((Integer)args[0]);
               case "isKeyJustPressed":return just.contains((Integer)args[0]);
               case "isButtonPressed":case "isButtonJustPressed":return buttons.contains((Integer)args[0]);
               case "isTouched":return !buttons.isEmpty();
               case "getX":return px;case "getY":return py;
               default:try{return method.invoke(real,args);}catch(InvocationTargetException ex){throw ex.getCause();}
            }
         });
         Gdx.input=fake;Game.gamepad=null;DesktopControls.focusChanged(true);InputProcessor.acceptInput=true;
         modernUi.desktop.preferences.bindings=DesktopInterfaceSettings.defaults();modernUi.desktop.preferences.apply();
         boot();ready=true;
      }
      void boot() {
         actionStack.clear();server=new Server();Network.register(server);map=new PkmnMap("unsaved-map-fixture");
         map.timeOfDay="day";map.currBiome="grassland";
         for(int y=-320;y<=304;y+=16)for(int x=-320;x<=304;x+=16) {
            Tile tile=new Tile("cave1_floor1","",new Vector2(x,y),true);tile.biome="cave";map.tiles.put(tile.position,tile);
         }
         map.overworldTiles=map.tiles;map.bottomLeft=new Vector2(-320,-320);map.topRight=new Vector2(320,320);
         map.minimap=new Pixmap(80,80,Pixmap.Format.RGBA8888);
         for(int y=0;y<80;y++)for(int x=0;x<80;x++)map.minimap.drawPixel(x,y,x<40?(y<40?GRASS:WATER):(y<40?SAND:LAVA));
         player.position.set(0,0);player.spawnLoc.set(-240,-240);player.name="Map fixture";cam.position.set(8,8,0);
         start();player.setCurrPokemon();playerCanMove=false;player.canMove=false;player.acceptInput=false;
         // Keep gameplay updates out of this presentation fixture; actions under test remain original.
         actionStack.clear();actionStackCopy.clear();insertAction(processor);map.pokemon.clear();map.onscreenPokemon.clear();
         DrawMiniMap.warpTiles.clear();parent=new DrawPlayerMenu(this,null);parent.currIndex=0;
         parent.newPos=parent.arrowCoords.get(0);insertAction(parent);
         currMusic=(Music)Proxy.newProxyInstance(Music.class.getClassLoader(),new Class<?>[]{Music.class},(proxy,method,args)->{
            if(method.getName().equals("getVolume"))return 0f;
            if(method.getReturnType()==boolean.class)return false;
            if(method.getReturnType()==float.class)return 0f;
            return null;
         });
         terrainFingerprint=ModernWorldGenerator.fingerprint(map.tiles);minimapFingerprint=pixmapFingerprint(map.minimap);
         position=player.position.cpy();mapBatch.setColor(Color.WHITE);
      }
      @Override public void render() {
         if(failure!=null||complete)return;
         try {
            Gdx.input=fake;frames++;events();
            // Native composition must not consume either simulation random stream.
            long seed=830013L+frames;Game.rand.setSeed(seed);map.rand.setSeed(seed+1);
            super.render();just.clear();
            check(Game.rand.nextLong()==new Random(seed).nextLong(),"Map flow consumed gameplay RNG on frame "+frames);
            check(map.rand.nextLong()==new Random(seed+1).nextLong(),"Map flow consumed map RNG on frame "+frames);
            check(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"OpenGL error on map frame "+frames);
            check(ModernWorldGenerator.fingerprint(map.tiles)==terrainFingerprint,"Map UI mutated world tiles");
            check(pixmapFingerprint(map.minimap)==minimapFingerprint,"Map UI rewrote the saved minimap");
            check(player.position.equals(position),"Browsing map moved/teleported player");
            assertions();
            if(frames>320)throw new IllegalStateException("Map fixture timeout");
         }catch(Throwable ex){failure=ex;Gdx.app.exit();}
      }
      void events() {
         switch(frames) {
            case 1:DesktopControls.focusChanged(true);break;
            case 4:keyDown(Keys.S);break;
            case 5:release();break;
            case 6:keyDown(Keys.S);break;
            case 7:release();break;
            case 8:click(new ModernUi.Box(82,64,67,14),Buttons.LEFT);break;
            case 9:release();break;
            case 25:keyDown(Keys.D);break;
            case 30:release();break;
            case 33:keyDown(Keys.W);break;
            case 38:release();break;
            case 40:click(new ModernUi.Box(80,65,1,1),Buttons.LEFT);break;
            case 41:px+=50;py-=20;break;
            case 42:click(new ModernUi.Box(40,55,10,10),Buttons.RIGHT);break;
            case 43:release();break;
            case 65:click(new ModernUi.Box(82,64,67,14),Buttons.LEFT);break;
            case 66:release();break;
            case 83:Gdx.graphics.setWindowedMode(1280,720);break;
            case 91:Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);Gdx.gl.glScissor(11,13,Gdx.graphics.getWidth()-17,Gdx.graphics.getHeight()-21);break;
            case 96:Gdx.graphics.setWindowedMode(640,960);break;
            case 112:keyDown(Keys.ESCAPE);break;
            case 113:release();break;
            case 135:click(new ModernUi.Box(82,64,67,14),Buttons.LEFT);break;
            case 136:release();break;
            case 151:prepareWarp();break;
            case 155:click(new ModernUi.Box(80,65,1,1),Buttons.LEFT);break;
            case 156:release();break;
            case 267:click(new ModernUi.Box(80,65,1,1),Buttons.RIGHT);break;
            case 268:release();break;
         }
      }
      void assertions() throws Exception {
         if(frames==7)check(parent.currIndex==2,"WASD did not select MAP in the actual player menu");
         if(frames==21) {
            mapMenu=activeMap();check(mapMenu!=null,"MAP button did not install original DrawMiniMap");
            check(!actionStack.contains(parent),"Player menu remained over map");
            captureAndCheck("01-map-open.png");panX=mapMenu.currX;panY=mapMenu.currY;
         }
         if(frames==31) {
            check(mapMenu.currX<panX-4,"D did not pan original map coordinates");
            check(mapMenu.currY==panY,"Horizontal pan changed vertical coordinate");
            float oldX=grassX;captureAndCheck("02-map-pan-east.png");check(grassX<oldX-25,"Map coordinates panned east but displayed terrain stayed fixed");panX=mapMenu.currX;panY=mapMenu.currY;
         }
         if(frames==39) {
            check(mapMenu.currY<panY-4&&mapMenu.currX==panX,"W did not pan north independently");
            float oldY=grassY;captureAndCheck("03-map-pan-north.png");check(grassY<oldY-25,"Map coordinates panned north but displayed terrain stayed fixed");panX=mapMenu.currX;panY=mapMenu.currY;
         }
         if(frames==41) {
            check(mapMenu.currX>panX&&mapMenu.currY>panY,"Mouse dragging did not pan original map action");
            float oldX=grassX;captureAndCheck("04-map-drag.png");check(grassX>oldX+5,"Mouse drag coordinates changed but displayed map did not move");
         }
         if(frames==44){check(activeMap()==null,"Right mouse did not close MAP");check(mapMenu.texture.getTextureObjectHandle()==0&&mapMenu.warpTileTexture.getTextureObjectHandle()==0,"Original map textures were not disposed on close");}
         if(frames==62)check(actionStack.contains(parent)&&!parent.disabled,"Original previous menu was not restored");
         if(frames==79) {
            DrawMiniMap reopened=activeMap();check(reopened!=null&&reopened!=mapMenu,"Pointer MAP button did not reopen a fresh map");
            mapMenu=reopened;captureAndCheck("05-map-reopened.png");
         }
         if(frames==91) {
            IntBuffer box=BufferUtils.newIntBuffer(4);Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX,box);
            check(Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST)&&box.get(0)==11&&box.get(1)==13
               &&box.get(2)==Gdx.graphics.getWidth()-17&&box.get(3)==Gdx.graphics.getHeight()-21,"Map clipping leaked OpenGL scissor state");
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
         }
         if(frames==92) {
            check(Gdx.graphics.getWidth()==1280&&Gdx.graphics.getHeight()==720,"Wide resize did not take effect");
            captureAndCheck("06-map-wide.png");
         }
         if(frames==106) {
            check(Gdx.graphics.getWidth()==640&&Gdx.graphics.getHeight()==960,"Portrait resize did not take effect");
            captureAndCheck("07-map-portrait.png");
         }
         if(frames==114)check(activeMap()==null,"Escape did not close MAP");
         if(frames==132)check(actionStack.contains(parent)&&!parent.disabled,"Escape did not restore previous menu");
         if(frames==149){mapMenu=activeMap();check(mapMenu!=null,"Map did not reopen after portrait close");captureAndCheck("08-map-portrait-reopened.png");}
         if(frames==154)check(mapMenu.nearestWarpTile!=null&&mapMenu.teleportPokemon!=null,"Original warp search/teleport eligibility lost");
         if(frames==157) {
            check(mapMenu.disabled,"Teleport confirm did not delegate the original action");
            check(actionStack.stream().anyMatch(a->a instanceof DisplayText),"Original teleport question not shown");
         }
         if(frames==265) {
            check(actionStack.stream().anyMatch(a->a instanceof DrawYesNoMenu),"Original teleport confirmation was not reached");
            captureAndCheck("09-map-warp-question.png");
         }
         if(frames==290) {
            check(!mapMenu.disabled&&!mapMenu.goAway,"Cancelling warp did not restore original map interaction");
            captureAndCheck("10-map-warp-cancelled.png");
            check(Gdx.files.local(".").list((file,name)->name.endsWith(".sav")).length==0,"Map test unexpectedly wrote a save");
            complete=true;Gdx.app.exit();
         }
      }
      void prepareWarp() {
         Tile tile=new Tile("cave1_floor1","warp_tile1",new Vector2(0,0),true);tile.hasItem="Map test beacon";
         DrawMiniMap.warpTiles.put(tile.position.cpy(),tile);
         mapMenu.teleportPokemon=player.currPokemon;mapMenu.cursorPos.set(0,0);
         mapMenu.currX=cam.position.x-mapMenu.playerPos.x;mapMenu.currY=cam.position.y-mapMenu.playerPos.y;
      }
      DrawMiniMap activeMap(){for(Action a:actionStack)if(a instanceof DrawMiniMap)return(DrawMiniMap)a;return null;}
      void keyDown(int key){keys.add(key);just.add(key);Gdx.input.getInputProcessor().keyDown(key);}
      void release(){for(int key:new ArrayList<>(keys))Gdx.input.getInputProcessor().keyUp(key);keys.clear();just.clear();buttons.clear();}
      void point(ModernUi.Box box) {
         ModernUi.Box view=modernUi.viewportBounds();
         px=Math.round((box.x+box.width*.5f-view.x)*Gdx.graphics.getWidth()/view.width);
         py=Math.round(Gdx.graphics.getHeight()-(box.y+box.height*.5f-view.y)*Gdx.graphics.getHeight()/view.height);
      }
      void click(ModernUi.Box box,int button){point(box);buttons.add(button);Gdx.input.getInputProcessor().touchDown(px,py,0,button);}
      void captureAndCheck(String name)throws Exception {
         Pixmap pixmap=ScreenUtils.getFrameBufferPixmap(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
         PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);
         try {
            png.write(Gdx.files.local(name),pixmap);captures++;
            int[] colors={GRASS,WATER,SAND,LAVA},counts=new int[4];long sumX=0,sumY=0;
            for(int y=0;y<pixmap.getHeight();y++)for(int x=0;x<pixmap.getWidth();x++) {
               int pixel=pixmap.getPixel(x,y);
               for(int i=0;i<colors.length;i++)if(distance(pixel,colors[i])<10){counts[i]++;if(i==0){sumX+=x;sumY+=y;}}
            }
            for(int i=0;i<colors.length;i++)check(counts[i]>150,"Invisible/incomplete map: "+name+" semantic color "+i+" count="+counts[i]);
            grassX=sumX/(float)counts[0];grassY=sumY/(float)counts[0];
            // Independent registration from map coordinates, not screen counters or the helper's mapping method.
            // The original confirmation deliberately covers both map markers.
            if(!name.contains("warp-question")) {
               markerPixels(pixmap,40,40,0xffe292ff,"Player marker",name);
               // Beacon labels use the existing bottom dialogue and cover the camp here.
               if(!name.contains("warp-cancelled"))markerPixels(pixmap,10,10,0x88d9ebff,"Camp marker",name);
            }
            System.out.println("MAP pixels "+name+" grass="+counts[0]+" water="+counts[1]+" sand="+counts[2]+" lava="+counts[3]);
         }finally{png.dispose();pixmap.dispose();}
      }
      void markerPixels(Pixmap pixmap,float mapX,float mapY,int color,String label,String name) {
         ModernUi.Box view=modernUi.viewportBounds();float scale=Math.min(140f/80,89f/80);
         float originX=6+(148-80*scale)*.5f+(mapMenu.currX+32+mapMenu.currOffset.x)*scale;
         float originY=20+(97-80*scale)*.5f+(mapMenu.currY+32+mapMenu.currOffset.y)*scale;
         float x=originX+mapX*scale,y=originY+mapY*scale;int found=0;
         for(int py=Math.max(0,(int)((y-3-view.y)*pixmap.getHeight()/view.height));py<Math.min(pixmap.getHeight(),(y+3-view.y)*pixmap.getHeight()/view.height);py++)
            for(int px=Math.max(0,(int)((x-3-view.x)*pixmap.getWidth()/view.width));px<Math.min(pixmap.getWidth(),(x+3-view.x)*pixmap.getWidth()/view.width);px++)
               if(distance(pixmap.getPixel(px,py),color)<10)found++;
         check(found>30,label+" missing or registered to wrong world location: "+name+" count="+found+" logical="+x+","+y);
      }
      static int distance(int a,int b){return Math.abs((a>>>24)-(b>>>24))+Math.abs((a>>>16&255)-(b>>>16&255))+Math.abs((a>>>8&255)-(b>>>8&255));}
      static long pixmapFingerprint(Pixmap pixmap){long hash=0xcbf29ce484222325L;for(int y=0;y<pixmap.getHeight();y++)for(int x=0;x<pixmap.getWidth();x++)hash=(hash^pixmap.getPixel(x,y))*0x100000001b3L;return hash;}
      @Override public void dispose(){if(real!=null)Gdx.input=real;DrawMiniMap.warpTiles.clear();super.dispose();disposed=true;}
   }
}
