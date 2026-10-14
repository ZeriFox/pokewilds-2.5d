package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.lang.reflect.Field;
import java.util.*;

/** Real GL, isolated unsaved maps: jump heights, obstruction footprints, interiors and field actors. */
public final class WorldReadabilityTest {
   private static int checks;
   private static Throwable failure;
   private static void require(boolean pass,String message){checks++;if(!pass)throw new IllegalStateException(message);}
   private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
   public static void main(String[] args){
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;Probe probe=new Probe();
      Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
      cfg.setInitialVisible(false);cfg.setWindowedMode(960,640);cfg.useVsync(false);cfg.setForegroundFPS(60);
      new Lwjgl3Application(probe,cfg);
      if(failure!=null){failure.printStackTrace();System.exit(1);}
      require(probe.complete,"Incomplete world readability fixture");
      System.out.println("WORLD READABILITY PASS: "+checks+" checks; directional jump heights, all CUT tiers, true blocked footprints, layered interior floors, companion placement/action pose, shiny cue and immutable simulation");
      System.exit(0);
   }
   private static final class Probe extends Game {
      int frames;boolean complete;
      Probe(){super(new String[0],4);}
      @Override public void render(){
         try {super.render();frames++;if(frames==15){fixture();verify();complete=true;Gdx.app.exit();}}
         catch(Throwable t){failure=t;Gdx.app.exit();}
      }
      JohtoRenderer renderer()throws Exception{return (JohtoRenderer)field(Game.class,"johtoRenderer").get(this);}
      Tile put(String name,String upper,int x,int y){Tile tile=new Tile(name,upper,new Vector2(x,y),true);tile.biome="forest";map.tiles.put(tile.position,tile);return tile;}
      void fixture(){
         Gdx.input.setInputProcessor(null);actionStack.removeIf(a->a instanceof DrawSetupMenu);
         server=new Server();Network.register(server);map=new PkmnMap("world-readability-fixture");
         map.bottomLeft=new Vector2(-256,-192);map.topRight=new Vector2(256,192);
         map.minimap=new Pixmap(64,64,Pixmap.Format.RGBA8888);
         world(false);player.name="Readability";player.position.set(0,0);player.spawnLoc.set(0,0);
         start();player.setCurrPokemon();actionStack.removeIf(a->a instanceof CycleDayNight);
         actionStackCopy.clear();player.acceptInput=false;mapBatch.setColor(Color.WHITE);map.timeOfDay="day";
      }
      void world(boolean inside){
         Map<Vector2,Tile> tiles=new HashMap<>();map.tiles=tiles;if(!inside)map.overworldTiles=tiles;
         for(int y=-192;y<=192;y+=16)for(int x=-256;x<=256;x+=16)put(inside?"house5_floor1":"green1","",x,y);
         map.onscreenPokemon.clear();map.onscreenFossils.clear();map.onscreenBurrowed.clear();map.refreshCache=true;
         player.position.set(0,0);cam.position.set(8,24,0);cam.zoom=1.8f;
      }
      void verify()throws Exception{
         JohtoRenderer renderer=renderer();
         traversal();world(false);
         for(String direction:new String[]{"down","up","left","right"}) {
            Map<Vector2,Tile> tiles=new HashMap<>();
            for(int y=-3;y<=3;y++)for(int x=-3;x<=3;x++){
               Tile t=new Tile("green1",new Vector2(x*16,y*16),true);
               if((direction.equals("down")||direction.equals("up"))?y==0:x==0){t.isLedge=true;t.ledgeDir=direction;t.name="ledge1_"+direction;}
               tiles.put(t.position,t);
            }
            WorldElevation height=new WorldElevation();height.update(tiles);
            float dx=direction.equals("left")?-1:direction.equals("right")?1:0,dy=dx!=0?0:direction.equals("up")?1:-1;
            float high=height.height(8+dx*7.99f,8+dy*7.99f),low=height.height(8+dx*8.01f,8+dy*8.01f);
            require(Math.abs(high-low-6f)<.01f,"Invisible old jump ledge "+direction+": "+high+" / "+low);
         }
         for(int x=-80;x<=80;x+=16)put("ledge1_down","",x,64);
         for(int y=-48;y<=32;y+=16)put("ledge1_right","",112,y);
         Tile ordinary=put("green1","tree2",-64,-16),mature=put("tree_large1","",32,-16);
         put("tree_large1_noSprite","",48,-16);put("tree_large1_noSprite","",32,0);put("tree_large1_noSprite","",48,0);
         Tile locked=put("green1","tree1",-112,0),savanna=put("green1","tree_savanna1",80,16);
         require(WorldReadability.cutTier(ordinary)==1&&WorldReadability.cutTier(mature)==2&&WorldReadability.cutTier(savanna)==2,"CUT tiers do not match game rules");
         require(!WorldMaterials.treeKey(ordinary).equals(WorldMaterials.treeKey(mature)),"Ordinary and mature trees still share artwork");
         require(WorldReadability.cutTier(locked)==0,"Uncuttable tree falsely advertised as cuttable");
         require(renderer.render(this),"Forest failed to render");
         require(renderer.getJumpCueFrames()>10&&renderer.getObstacleFootprintFrames()>4,"Missing ledge/footprint geometry");
         capture("01-jump-ledges-and-cut-tiers.png");
         long before=ModernWorldGenerator.fingerprint(map.tiles);
         Vector2 spot=new Vector2(),scratch=new Vector2();
         require(WorldReadability.companionTarget(map.tiles,8,7,"up",spot,scratch),"No open field actor position");
         require(spot.dst2(8,7)>14*14&&WorldReadability.open(map.tiles,spot.x,spot.y,scratch),"Actor overlaps trainer or obstacle");
         Pokemon pokemon=new Pokemon("bulbasaur",12);pokemon.isShiny=true;
         pokemon.position.set(4,5);Vector2 saved=pokemon.position.cpy();
         player.hmPokemon=pokemon;player.currFieldMove="CUT";player.dirFacing="up";
         CutTreeAnim cut=new CutTreeAnim(this,ordinary,null);cut.timer=8;actionStack.add(cut);actionStackCopy.add(cut);
         long active=renderer.getActiveCompanionFrames();
         for(int i=0;i<8;i++){field(JohtoRenderer.class,"seconds").setFloat(renderer,.25f+i*.035f);renderer.render(this);}
         Vector2 displayed=(Vector2)field(JohtoRenderer.class,"companionPosition").get(renderer);
         require(displayed.dst2(8,7)>14*14,"Ability actor attached to player");
         require(renderer.getActiveCompanionFrames()>active,"CUT actor never changes to its action pose");
         require(renderer.getShinyFrames()>0,"Shiny field actor lacks sparkle effect");
         require(pokemon.position.equals(saved)&&cut.timer==8&&before==ModernWorldGenerator.fingerprint(map.tiles),"Renderer changed gameplay or action clocks");
         capture("02-animated-shiny-field-companion.png");
         actionStack.remove(cut);actionStackCopy.remove(cut);player.hmPokemon=null;player.currFieldMove="";
         world(true);
         Tile layered=put("green1","house5_floor1",0,16);
         require(WorldMaterials.terrainName(layered).equals("wood_floor"),"Upper-layer interior floor lost its texture");
         Tile window=put("house5_floor1","house_window1",-48,48);
         require(WorldMaterials.kind(window,WorldMaterials.objectName(window))==WorldMaterials.Kind.WINDOW,"Interior window missing wall geometry");
         for(int x=-96;x<=96;x+=16){put("house5_wall1","",x,64);if(x<-32||x>32)put("house5_wall1","",x,-32);}
         for(int y=-32;y<=64;y+=16){put("house5_wall1","",-96,y);put("house5_wall1","",96,y);}
         put("house5_floor1","solid",-64,0);put("house5_floor1","house_shelf1",64,32);
         put("house5_floor1","house_bed1",32,32);put("house5_floor1","building1_fossilreviver1",-48,32);
         require(renderer.render(this),"Interior failed to render");capture("03-textured-interior.png");
         require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"GL error after world fixtures");
      }
      void traversal(){
         List<Action> previous=new ArrayList<>(actionStack);
         try{
            for(String direction:new String[]{"down","up","left","right"})for(boolean allowed:new boolean[]{true,false}) {
               world(false);actionStack.clear();actionStackCopy.clear();player.acceptInput=true;playerCanMove=true;
               Tile ledge=put("green1","",0,0);ledge.name="ledge1_"+direction;ledge.isLedge=true;ledge.ledgeDir=direction;
               float dx=direction.equals("left")?-1:direction.equals("right")?1:0;
               float dy=dx!=0?0:direction.equals("up")?1:-1;
               // Up ledges use the original one-cell jump from the high cell;
               // the other directions use the original two-cell jump across it.
               Vector2 start=direction.equals("up")&&allowed?new Vector2():new Vector2(dx*16*(allowed?-1:1),dy*16*(allowed?-1:1));
               start.set(Math.round(start.x),Math.round(start.y));
               player.position.set(start);cam.position.set(start.x+8,start.y+8,0);
               String facing=allowed?direction:Player.oppDirs.get(direction);player.dirFacing=facing;
               InputProcessor.upPressed=facing.equals("up");InputProcessor.downPressed=facing.equals("down");
               InputProcessor.leftPressed=facing.equals("left");InputProcessor.rightPressed=facing.equals("right");
               InputProcessor.aJustPressed=false;InputProcessor.bPressed=false;InputProcessor.startJustPressed=false;
               PlayerStanding.moveTimer=4;PlayerStanding standing=new PlayerStanding(this);
               standing.currTile=map.tiles.get(player.position);standing.localStep(this);
               Action selected=null;for(Action action:actionStack)if(action instanceof PlayerLedgeJump||action instanceof PlayerLedgeJumpFast||action instanceof PlayerBump)selected=action;
               require(selected!=null,"No original movement action for "+direction+" allowed="+allowed);
               if(allowed) {
                  require(!(selected instanceof PlayerBump),"Allowed jump is blocked: "+direction);
                  if(selected instanceof PlayerLedgeJump)for(int tick=0;tick<39;tick++)((PlayerLedgeJump)selected).localStep(this);
                  else for(int tick=0;tick<20;tick++)((PlayerLedgeJumpFast)selected).localStep(this);
                  require(player.position.equals(new Vector2(dx*16,dy*16)),"Jump did not land on original target: "+direction+" "+player.position);
               } else {
                  require(selected instanceof PlayerBump,"Reverse ledge became traversable: "+direction);
                  for(int tick=0;tick<3;tick++)((PlayerBump)selected).localStep(this);
                  require(player.position.equals(start),"Blocked approach moved the player");
               }
            }
         } finally {
            InputProcessor.upPressed=InputProcessor.downPressed=InputProcessor.leftPressed=InputProcessor.rightPressed=false;
            PlayerStanding.moveTimer=0;player.acceptInput=false;actionStack.clear();actionStack.addAll(previous);actionStackCopy.clear();
         }
      }
      void capture(String name){
         Pixmap pixels=ScreenUtils.getFrameBufferPixmap(0,0,960,640);PixmapIO.PNG writer=new PixmapIO.PNG();writer.setFlipY(true);
         try{writer.write(Gdx.files.local(name),pixels);}catch(java.io.IOException e){throw new RuntimeException(e);}finally{writer.dispose();pixels.dispose();}
      }
   }
}
