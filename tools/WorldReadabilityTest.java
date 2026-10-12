package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

/** Real built-JAR test. The A/B switch affects presentation only, never map state. */
public final class WorldReadabilityTest {
   static final int W=960,H=540;
   static int checks;
   static void check(boolean condition,String message){checks++;if(!condition)throw new IllegalStateException(message);}
   static Field field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f;}
   public static void main(String[] args){
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      Probe game=new Probe();Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration();
      c.setInitialVisible(false);c.setWindowedMode(W,H);c.useVsync(false);c.setForegroundFPS(60);
      try{new Lwjgl3Application(game,c);check(game.complete,"Incomplete native readability test");
         System.out.println("READABILITY PASS: "+checks+" assertions; natural seams, nonflat ramp cues, consistent prop depth, complete PMD locomotion envelopes, presentation-only state, native entrance captures");System.exit(0);
      }catch(Throwable e){e.printStackTrace();System.exit(1);}
   }
   private static final class Probe extends Game {
      int frames;boolean complete;Pokemon pokemon;
      Probe(){super(new String[0],4);}
      @Override public void render(){
         super.render();frames++;
         try{
            if(frames==15)initialize();
            if(frames==24){runChecks();complete=true;Gdx.app.exit();}
         }catch(Exception e){throw new RuntimeException(e);}
      }
      Tile put(String ground,String upper,int x,int y,String biome){
         Tile t=new Tile(ground,upper,new Vector2(x,y),true);t.biome=biome;map.tiles.put(t.position,t);return t;
      }
      void initialize(){
         actionStack.removeIf(a->a instanceof DrawSetupMenu);Gdx.input.setInputProcessor(null);
         server=new Server();Network.register(server);map=new PkmnMap("readability-fixture");
         for(int y=-256;y<=256;y+=16)for(int x=-256;x<=256;x+=16){
            String ground=x>64?"sand4":y==-16?"path1":"green1";
            put(ground,"",x,y,x>64?"beach":"forest");
         }
         for(int x=-96;x<48;x+=32){put("green1","tree1",x,48,"forest");put("green1","bush1",x,80,"forest");}
         put("green1","tree1",16,0,"forest");put("green1","rock1",-48,0,"forest");
         map.bottomLeft=new Vector2(-256,-256);map.topRight=new Vector2(256,256);
         map.minimap=new Pixmap(64,64,Pixmap.Format.RGBA8888);
         player.position.set(0,-32);player.spawnLoc.set(0,-32);player.name="Readability";cam.position.set(8,-24,0);
         start();player.setCurrPokemon();actionStack.removeIf(a->a instanceof CycleDayNight);
         pokemon=new Pokemon("pikachu",5);pokemon.position.set(16,-16);pokemon.mapTiles=map.tiles;
         pokemon.aggroPlayer=false;pokemon.dirFacing="down";insertAction(pokemon.new Standing());
      }
      JohtoRenderer renderer()throws Exception{Field f=Game.class.getDeclaredField("johtoRenderer");f.setAccessible(true);return(JohtoRenderer)f.get(this);}
      byte[] capture(String name,boolean enabled)throws Exception{
         System.setProperty("pokewilds.readability",enabled?"on":"off");System.setProperty("pokewilds.ambientOverlays","off");
         JohtoRenderer r=renderer();field(r,"seconds").setFloat(r,2f);((Map<?,?>)field(r,"motion").get(r)).clear();
         Game.rand.setSeed(78123);map.rand.setSeed(291);long fingerprint=ModernWorldGenerator.fingerprint(map.tiles);
         Gdx.gl.glViewport(0,0,W,H);check(r.render(this),"World render skipped");
         check(fingerprint==ModernWorldGenerator.fingerprint(map.tiles),"Tiles mutated by readability");
         check(Game.rand.nextLong()==new Random(78123).nextLong(),"Game RNG consumed");
         check(map.rand.nextLong()==new Random(291).nextLong(),"Map RNG consumed");
         byte[] result=ScreenUtils.getFrameBufferPixels(0,0,W,H,false);save(name);
         check(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"GL error");return result;
      }
      void save(String name){
         Pixmap p=ScreenUtils.getFrameBufferPixmap(0,0,W,H);PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);
         try{png.write(Gdx.files.local(name+".png"),p);}catch(java.io.IOException e){throw new RuntimeException(e);}finally{png.dispose();p.dispose();}
      }
      void runChecks()throws Exception{
         Tile grass=map.tiles.get(new Vector2(0,0)),sand=map.tiles.get(new Vector2(96,0));
         check(WorldReadability.feather(grass,sand),"Natural dry seam not eligible");
         check(!WorldReadability.feather(sand,grass),"Both sides own seam");
         for(String floor:new String[]{"water1","lava1","house5_floor1","ledge_grass_ramp"}){
            Tile t=new Tile(floor,new Vector2(-999,-999),true);
            check(WorldReadability.materialRank(t)<0,"Protected boundary softened: "+floor);
         }
         for(String species:new String[]{"pikachu","machop","milotic"}){
            PmdPokemonSprites sprites=PmdPokemonSprites.get();PmdPokemonSprites.AnimationBounds all=sprites.overworldBounds(species);
            check(all!=null&&all.width>0&&all.height>0,"Missing locomotion envelope "+species);
            check(all==sprites.overworldBounds(species),"Envelope not cached");
            for(String anim:new String[]{"Idle","Walk"})for(String dir:new String[]{"down","down-right","right","up-right","up","up-left","left","down-left"}){
               PmdPokemonSprites.AnimationBounds b=sprites.bounds(species,dir,anim);if(b==null)continue;
               check(all.minX<=b.minX&&all.minY<=b.minY&&all.maxX>=b.maxX&&all.maxY>=b.maxY,"Frame cut by locomotion envelope");
               float scale=Math.min(.85f,30f/Math.max(all.width,all.height));
               check((b.minY-Math.min(0,all.minY))*scale>=-.001f,"Ground penetration after dry foot correction");
            }
         }
         byte[] before=capture("01-forest-before-depth-and-seams",false),after=capture("02-forest-readability",true);
         check(!Arrays.equals(before,after),"No rendered difference for depth/seams");
         check(renderer().getFeatheredEdges()>0,"No real seam geometry emitted");
         byte[] repeat=capture("03-forest-repeat",true);check(Arrays.equals(after,repeat),"Non-deterministic world presentation");
         inspectPropDepth();
         // Closed raised terrace with a three-wide ramp, not an invented tile-height offset.
         for(int y=-112;y<=112;y+=16)for(int x=-112;x<=112;x+=16){
            int xx=x/16,yy=y/16;String upper="";
            if(Math.max(Math.abs(xx),Math.abs(yy))==5){if(yy==-5)upper+="N";if(yy==5)upper+="S";if(xx==-5)upper+="E";if(xx==5)upper+="W";upper="ledges3_"+upper;}
            String ground="mountain3";if(yy==-5&&Math.abs(xx)<=1){upper="";ground="ledge_grass_ramp";}
            put(ground,upper,x,y,"mountain");
         }
         map.refreshCache=true;player.position.set(0,-96);cam.position.set(8,-88,0);
         capture("04-ramp-readable",true);check(renderer().getMarkedRamps()>0,"No ramp cues emitted");
         check(renderer().getElevation().isSloped(map.tiles.get(new Vector2(0,-80))),"Fixture must really slope");
         // Presentation calls never shorten the original battle action or schedule a second battle.
         BattleIntro intro=new BattleIntro(null);int count=intro.frames.size();actionStack.add(intro);
         johtoBattleRenderer.prepareFrame(this,true);
         for(int i=0;i<3;i++){
            capture("05-entry-world-"+i,true);
            uiBatch.begin();johtoBattleRenderer.drawIntro(this,intro,count-i*count/3);uiBatch.end();save("06-entry-phase-"+i);
         }
         check(intro.frames.size()==count,"Presenter advanced original battle action");
         uiBatch.begin();johtoBattleRenderer.drawWipe(this,intro,count);uiBatch.end();save("07-wipe-start");
         uiBatch.begin();johtoBattleRenderer.drawWipe(this,intro,1);uiBatch.end();save("08-wipe-closed");
         johtoBattleRenderer.finishFrame(this);actionStack.remove(intro);
         for(float x:new float[]{-1,0,.2f,.5f,.8f,1,2})check(WorldReadability.ease(x)>=0&&WorldReadability.ease(x)<=1,"Easing outside bounds");
         System.setProperty("pokewilds.readability","on");System.clearProperty("pokewilds.ambientOverlays");
      }
      void inspectPropDepth()throws Exception{
         JohtoRenderer r=renderer();TextureRegion tree=BwAssets.get().named("tree");
         WorldMeshBatch geometry=(WorldMeshBatch)field(r,"geometry").get(r);geometry.clear();
         Method upright=JohtoRenderer.class.getDeclaredMethod("upright",TextureRegion.class,float.class,float.class,float.class,float.class,float.class,float.class);upright.setAccessible(true);
         upright.invoke(r,tree,0f,32f,.2f,24f,48f,Color.WHITE_FLOAT_BITS);
         Map<?,?> groups=(Map<?,?>)field(geometry,"groups").get(geometry);Object buffer=groups.get(tree.getTexture());
         FloatArray vertices=(FloatArray)field(buffer,"vertices").get(buffer);
         check(vertices.size==48,"Whole tree quad changed topology");
         for(int i=0;i<vertices.size;i+=8){check(vertices.get(i+6)==4,"Prop and Pokemon use different depth conventions");check(Float.isFinite(vertices.get(i+7)),"Invalid depth");}
      }
   }
}
