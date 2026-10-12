package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Exact shipped JAR, real GL: semantics, sparse accents, UI/focus protection and RNG. */
public final class WorldPresentationLayersTest {
   private static final int WIDTH=960,HEIGHT=540;
   private static int checks;
   private static void require(boolean ok,String message) { checks++;if(!ok)throw new IllegalStateException(message); }
   private static Field field(Object object,String name) throws Exception {
      Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f;
   }
   public static void main(String[] args) {
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      Probe game=new Probe();
      Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
      cfg.setInitialVisible(false);cfg.setWindowedMode(WIDTH,HEIGHT);cfg.useVsync(false);cfg.setForegroundFPS(60);
      try {
         new Lwjgl3Application(game,cfg);
         require(game.complete,"Presentation layer probe did not finish");
         System.out.println("WORLD PRESENTATION LAYERS PASS: "+checks+" checks; semantic roles, whole rectangular sprites, flat volcanic cracks, seven on/off scenes, UI/focus budgets, deterministic RNG-free accents, indoor reset");
         System.exit(0);
      } catch(Throwable e) {e.printStackTrace();System.exit(1);}
   }
   private static final class Probe extends Game {
      int frame;boolean complete;
      Probe(){super(new String[0],4);}
      @Override public void render() {
         super.render();frame++;
         if(frame==15) {
            try { initialize();semantics();scenes();complete=true;Gdx.app.exit(); }
            catch(Exception e){throw new RuntimeException(e);}
         }
      }
      void initialize() {
         Gdx.input.setInputProcessor(null);actionStack.removeIf(a->a instanceof DrawSetupMenu);
         server=new Server();Network.register(server);map=new PkmnMap("material-overlay-fixture");
         map.bottomLeft=new Vector2(-256,-256);map.topRight=new Vector2(256,256);
         map.minimap=new Pixmap(64,64,Pixmap.Format.RGBA8888);
         world("green1","grassland",false);player.name="Layers";player.character="gold";
         start();player.setCurrPokemon();actionStack.removeIf(a->a instanceof CycleDayNight);
         actionStackCopy.clear();player.acceptInput=false;
      }
      Tile put(String ground,String upper,int x,int y,String biome) {
         Tile t=new Tile(ground,upper,new Vector2(x,y),true);t.biome=biome;map.tiles.put(t.position,t);return t;
      }
      void world(String ground,String biome,boolean indoor) {
         Map<Vector2,Tile> tiles=new HashMap<>();
         if(!indoor)map.overworldTiles=tiles;
         map.tiles=tiles;map.refreshCache=true;map.onscreenPokemon.clear();map.onscreenFossils.clear();map.onscreenBurrowed.clear();
         map.pokemon.clear();
         for(int y=-256;y<=256;y+=16)for(int x=-256;x<=256;x+=16)put(ground,"",x,y,biome);
         player.position.set(0,0);player.spawnLoc.set(0,0);cam.position.set(8,8,0);
      }
      void semantics() throws Exception {
         Tile t=new Tile("house5_floor1",new Vector2(0,0),true);
         require(WorldMaterials.objectName(t)==null,"Floor became a prop");
         require(WorldMaterials.kind(t,null)==WorldMaterials.Kind.NONE,"Pure floor not distinguished");
         t.name="cave_rock_floor1";t.nameUpper="";t.isSolid=false;
         require(WorldMaterials.terrainName(t).equals("cave_floor")&&WorldMaterials.objectName(t)==null,"Rock floor became an upright rock");
         t.name="cave_rock_floor1_cracked";
         require(WorldMaterials.kind(t,WorldMaterials.objectName(t))==WorldMaterials.Kind.GROUND_DETAIL,"Floor puzzle crack was hidden");
         t.name="house_floor_onpress";
         require(WorldMaterials.kind(t,WorldMaterials.objectName(t))==WorldMaterials.Kind.GROUND_DETAIL,"Floor pressure plate was hidden");
         t.name="wood_floor";t.nameUpper="house_couch1";t.isSolid=true;
         require(WorldMaterials.kind(t,WorldMaterials.objectName(t))==WorldMaterials.Kind.FURNITURE,"Furniture became a wall");
         t.name="bridge1";t.nameUpper="rock1";
         require(WorldMaterials.kind(t,WorldMaterials.objectName(t))==WorldMaterials.Kind.ROCK,"Bridge swallowed its upper rock");
         JohtoRenderer renderer=renderer();
         require(renderer.render(this),"Semantic fixture did not initialize the world renderer");
         Method building=JohtoRenderer.class.getDeclaredMethod("building",Game.class,Tile.class,float.class,float.class,String.class);
         building.setAccessible(true);
         require(!(Boolean)building.invoke(renderer,this,t,0f,0f,"bridge1 rock1"),"Actual building dispatch swallowed the rock");
         t.name="desert6";t.biome="desert";
         require(WorldMaterials.objectName(t).equals("desert_rock"),"Wrong desert rock material");
         TextureRegion rock=BwAssets.get().object(t),whole=BwAssets.get().named("desert_rock");
         require(rock==whole&&rock.getRegionWidth()==32&&rock.getRegionHeight()==32,"Rock PNG sampled as terrain fragments");
         t.name="volcano1";t.nameUpper="volcano_cracked";t.biome="volcano";
         require(WorldMaterials.objectName(t).equals("volcanic_cracked"),"Volcanic crack material missing");
         require(WorldMaterials.kind(t,"volcanic_cracked")==WorldMaterials.Kind.GROUND_DETAIL,"Volcanic crack is upright");
         // The private tile dispatch requires the live neighbours/elevation collected
         // by render(). Put the sample in that world and prepare its actual context
         // before isolating its emitted geometry; an assets-only setup is incomplete.
         map.tiles.put(t.position,t);map.refreshCache=true;
         require(renderer.render(this),"Crack fixture did not prepare its world context");
         // Inspect the real tile dispatch: this detail is emitted at one world height.
         for(String group:new String[]{"geometry","translucent","shadows","emotes"})((WorldMeshBatch)field(renderer,group).get(renderer)).clear();
         field(renderer,"surfaceLift").setFloat(renderer,0);
         Method drawTile=JohtoRenderer.class.getDeclaredMethod("tile",Game.class,Tile.class);drawTile.setAccessible(true);
         drawTile.invoke(renderer,this,t);
         WorldMeshBatch owner=(WorldMeshBatch)field(renderer,"geometry").get(renderer);
         Map<?,?> groups=(Map<?,?>)field(owner,"groups").get(owner);
         Object buffer=groups.get(BwAssets.get().named("volcanic_cracked").getTexture());
         com.badlogic.gdx.utils.FloatArray vertices=(com.badlogic.gdx.utils.FloatArray)field(buffer,"vertices").get(buffer);
         require(vertices.size>=48,"Crack detail not emitted");
         float y=vertices.get(vertices.size-47);
         for(int i=vertices.size-48;i<vertices.size;i+=8)require(vertices.get(i+1)==y,"Crack still has a vertical billboard edge");
         for(String key:new String[]{"window","door_locked","roof","fence_wood","pressure_plate"})
            require(WorldMaterials.kind(t,key)!=WorldMaterials.Kind.ROCK,"Structural/decal role misclassified: "+key);
         t.nameUpper="tree1_nosprite";require(WorldMaterials.kind(t,null)==WorldMaterials.Kind.HIDDEN,"Hidden collision marker rendered");
         Tile regional=new Tile("green1","tree1",new Vector2(0,0),true);
         for(String[] sample:new String[][]{{"deep_forest","tree_oak"},{"wooded_lake","forest_tree_willow"},
            {"savanna","tree_dry"},{"mountain","tree_pine"},{"snow","tree_snow"}}) {
            regional.biome=sample[0];
            require(WorldMaterials.treeKey(regional).equals(sample[1]),"Regional tree ignored: "+sample[0]);
            require(BwAssets.get().named(sample[1])!=null,"Regional tree missing: "+sample[1]);
         }
         Tile cave=new Tile("cave1_left1",new Vector2(0,0),true);
         require(WorldMaterials.caveWall(cave)&&WorldMaterials.wallCap(cave).equals("cave_floor"),"Cave cap leaks outdoor mountain material");
         for(String biome:new String[]{"snow","savanna"})
            require(BiomeProfiles.named(biome).decor("grass").equals("grass_dry_tuft"),"Bright green tufts in "+biome);
         for(BiomeProfiles.Profile p:BiomeProfiles.all())for(boolean night:new boolean[]{false,true}) {
            require(WorldAmbientOverlay.effect(p.id,night,false,false,true)==WorldAmbientOverlay.Effect.NONE,"Solid prop emits weather");
            require(WorldAmbientOverlay.effect("interior",night,true,true,false)==WorldAmbientOverlay.Effect.NONE,"Indoor particle leak");
            require(WorldAmbientOverlay.effect("cave",night,true,true,false)==WorldAmbientOverlay.Effect.NONE,"Cave particle leak");
         }
         require(WorldAmbientOverlay.effect("volcano",false,false,false,false)==WorldAmbientOverlay.Effect.NONE,"Embers originate on dry ground");
         require(WorldAmbientOverlay.effect("volcano",true,false,true,false)==WorldAmbientOverlay.Effect.EMBER,"Actual lava lacks embers");
         require(WorldAmbientOverlay.effect("forest",false,false,false,false)==WorldAmbientOverlay.Effect.NONE,"Daytime fireflies");
         require(WorldAmbientOverlay.effect("forest",true,false,false,false)==WorldAmbientOverlay.Effect.FIREFLY,"Night fireflies missing");
         for(int x=-100;x<=100;x++)require(WorldAmbientOverlay.seed(x,-x)==WorldAmbientOverlay.seed(x,-x),"Unstable coordinate hash");
      }
      JohtoRenderer renderer() throws Exception {
         Field f=Game.class.getDeclaredField("johtoRenderer");f.setAccessible(true);return(JohtoRenderer)f.get(this);
      }
      WorldAmbientOverlay overlay() throws Exception {return(WorldAmbientOverlay)field(renderer(),"ambientOverlays").get(renderer());}
      void scenes() throws Exception {
         world("desert6","desert",false);put("desert6","rock1",-48,16,"desert");put("desert6","cactus1",48,48,"desert");
         compare("01-desert-dust",false,true);
         world("lava1","volcano",false);for(int y=-256;y<=256;y+=16)for(int x=-16;x<=16;x+=16)put("volcano1","",x,y,"volcano");
         compare("02-volcanic-embers",true,true);
         world("green12","graveyard",false);put("green12","gravestone1",-48,32,"graveyard");put("green12","tree1",64,64,"graveyard");
         compare("03-graveyard-mist",false,true);
         world("green1","deep_forest",false);put("green1","tree1",-48,32,"deep_forest");put("green1","tree2",64,48,"deep_forest");
         compare("04-forest-fireflies",true,true);
         world("snow1","snow",false);put("snow1","tree4",-48,48,"snow");compare("05-sparse-snow",false,true);
         world("water1","beach",false);for(int y=-256;y<=256;y+=16)for(int x=-16;x<=16;x+=16)put("sand4","",x,y,"beach");
         compare("06-water-glints",false,true);
         world("house5_floor1","interior",true);put("house5_floor1","house_couch1",48,32,"interior");
         compare("07-interior-clean",false,false);
         // Repeated transitions clear old particle meshes; disabling is immediate.
         require(overlay().particleCount()+overlay().mistCount()==0,"Previous biome leaked inside");
      }
      byte[] draw(String name,boolean enabled,float clock) throws Exception {
         System.setProperty("pokewilds.ambientOverlays",enabled?"on":"off");
         JohtoRenderer r=renderer();field(r,"seconds").setFloat(r,clock);
         ((Map<?,?>)field(r,"motion").get(r)).clear();
         Game.rand.setSeed(2917);map.rand.setSeed(3191);
         long state=ModernWorldGenerator.fingerprint(map.tiles);
         Gdx.gl.glViewport(0,0,WIDTH,HEIGHT);require(r.render(this),"Renderer skipped scene "+name);
         require(state==ModernWorldGenerator.fingerprint(map.tiles),"Presentation mutated tiles");
         require(Game.rand.nextLong()==new Random(2917).nextLong(),"Overlay consumed gameplay RNG");
         require(map.rand.nextLong()==new Random(3191).nextLong(),"Overlay consumed map RNG");
         modernUi.applyViewport(this,WIDTH,HEIGHT);((ModernBatch)uiBatch).suppressed=false;
         uiBatch.begin();modernUi.panel(this,8,6,144,16);
         modernUi.text(this,"WASD   Move    LMB   Action    RMB   Back",12,17,4,ModernUi.INK);uiBatch.end();
         byte[] pixels=ScreenUtils.getFrameBufferPixels(0,0,WIDTH,HEIGHT,false);
         if(name!=null) {
            Pixmap pix=ScreenUtils.getFrameBufferPixmap(0,0,WIDTH,HEIGHT);PixmapIO.PNG writer=new PixmapIO.PNG();writer.setFlipY(true);
            try{writer.write(Gdx.files.local(name+".png"),pix);}finally{writer.dispose();pix.dispose();}
         }
         require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"GL state error after overlays/UI");
         return pixels;
      }
      void compare(String name,boolean night,boolean expected) throws Exception {
         map.timeOfDay=night?"night":"day";mapBatch.setColor(night?PkmnMap.nightColor:Color.WHITE);
         byte[] before=draw(name+"-off",false,3f),after=draw(name+"-on",true,3f);
         int count=0,maxDelta=0;
         for(int i=0;i<before.length;i+=4) {
            boolean different=false;
            for(int c=0;c<3;c++){int d=Math.abs((before[i+c]&255)-(after[i+c]&255));different|=d>0;maxDelta=Math.max(maxDelta,d);}
            if(different)count++;
         }
         require(!expected||count>4,"No visible ambient accent in "+name+" particles="+overlay().particleCount()+" mist="+overlay().mistCount());
         require(expected||Arrays.equals(before,after),"Disabled indoor scene changed");
         require(count<WIDTH*HEIGHT*.06f,"Overlay covers too much of the screen in "+name+": "+count);
         require(overlay().particleCount()<=24&&overlay().mistCount()<=6,"Particle budget exceeded");
         // Exact pixels inside the opaque panel of the real ModernUi implementation.
         for(int y=28;y<75;y++)for(int x=215;x<745;x++)for(int c=0;c<4;c++)
            require(before[(y*WIDTH+x)*4+c]==after[(y*WIDTH+x)*4+c],"Overlay altered UI pixels");
         PerspectiveCamera camera=(PerspectiveCamera)field(renderer(),"camera").get(renderer());
         Vector3 p=camera.project(new Vector3(8,10,-7),0,0,WIDTH,HEIGHT);
         for(int y=(int)p.y-12;y<(int)p.y+12;y++)for(int x=(int)p.x-12;x<(int)p.x+12;x++)for(int c=0;c<4;c++)
            require(before[(y*WIDTH+x)*4+c]==after[(y*WIDTH+x)*4+c],"Accent obscured the central actor focus");
         byte[] repeat=draw(null,true,3f);require(Arrays.equals(after,repeat),"Same state and clock changed ambient image");
         if(expected) {
            modernUi.desktop.preferences.reducedMotion=true;
            try {
               draw(null,true,2f);String still=ambientGeometry();
               draw(null,true,11f);
               require(still.equals(ambientGeometry()),"Reduced motion still animates ambient geometry: "+name);
            } finally {modernUi.desktop.preferences.reducedMotion=false;}
         }
         System.out.println("LAYERS "+name+" changedPixels="+count+"/"+(WIDTH*HEIGHT)+" maxChannelDelta="+maxDelta
            +" particles="+overlay().particleCount()+" mist="+overlay().mistCount()+" UI=identical focus=identical RNG=unchanged");
      }
      String ambientGeometry() throws Exception {
         Object mesh=field(overlay(),"mesh").get(overlay());
         Map<?,?> groups=(Map<?,?>)field(mesh,"groups").get(mesh);
         StringBuilder result=new StringBuilder();
         for(Object buffer:groups.values()) {
            com.badlogic.gdx.utils.FloatArray v=(com.badlogic.gdx.utils.FloatArray)field(buffer,"vertices").get(buffer);
            result.append(Arrays.toString(v.toArray()));
         }
         return result.toString();
      }
   }
}
