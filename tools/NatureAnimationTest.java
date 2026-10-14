package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Fresh unsaved worlds, real renderer/shaders, fixed presentation times. */
public final class NatureAnimationTest {
   private static final int WIDTH=960,HEIGHT=640;
   private static int checks;
   private static Throwable failure;
   private static void require(boolean condition,String message) {
      checks++;if(!condition)throw new IllegalStateException(message);
   }
   private static Field field(Class<?> type,String name)throws Exception {
      Field f=type.getDeclaredField(name);f.setAccessible(true);return f;
   }
   public static void main(String[] args) {
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      Probe probe=new Probe();
      Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
      cfg.setWindowedMode(WIDTH,HEIGHT);cfg.setInitialVisible(false);cfg.useVsync(false);
      cfg.setForegroundFPS(60);cfg.setTitle("Nature animation verification");
      try {
         new Lwjgl3Application(probe,cfg);
         if(failure!=null)throw new IllegalStateException("Native nature verification failed",failure);
         require(probe.complete&&probe.disposed,"Native lifecycle incomplete");
         System.out.println("NATURE ANIMATION PASS: checks="+checks+", scenes="+probe.scenes+", captures="+probe.captures
            +", animated open ocean/coast/vegetation/crowns, rooted trunks, directional and local light/shadows, shore seams, deterministic frames, reduced motion, unchanged tiles/RNG");
         System.exit(0);
      }catch(Throwable error){error.printStackTrace();System.exit(1);}
   }
   private static final class Probe extends Game {
      int frames,scenes,captures;boolean complete,disposed;
      final List<Field> tileFlags=new ArrayList<>();
      Probe(){super(new String[0],4);}
      JohtoRenderer renderer()throws Exception{return(JohtoRenderer)field(Game.class,"johtoRenderer").get(this);}
      @Override public void render() {
         if(failure!=null)return;
         try {
            super.render();frames++;
            if(frames==15){initialize();materialCoverage();windContract();shoreRestrictions();natureScenes();openOcean();crownScenes();lightingScene();complete=true;Gdx.app.exit();}
         }catch(Throwable error){failure=error;Gdx.app.exit();}
      }
      void initialize() {
         Gdx.input.setInputProcessor(null);actionStack.removeIf(a->a instanceof DrawSetupMenu);
         server=new Server();Network.register(server);map=new PkmnMap("nature-animation-fixture");
         map.bottomLeft=new Vector2(-256,-256);map.topRight=new Vector2(256,256);
         map.minimap=new Pixmap(64,64,Pixmap.Format.RGBA8888);
         world("green1","forest",false);player.name="Nature";player.character="gold";
         start();player.setCurrPokemon();actionStack.removeIf(a->a instanceof CycleDayNight);
         actionStackCopy.clear();player.acceptInput=false;
         System.setProperty("pokewilds.ambientOverlays","off");
         for(Field f:Tile.class.getDeclaredFields())if(f.getType()==boolean.class&&!Modifier.isStatic(f.getModifiers())) {
            f.setAccessible(true);tileFlags.add(f);
         }
      }
      Tile put(String lower,String upper,int x,int y,String biome) {
         Tile t=new Tile(lower,upper,new Vector2(x,y),true);t.biome=biome;map.tiles.put(t.position,t);return t;
      }
      void world(String ground,String biome,boolean indoor) {
         Map<Vector2,Tile> tiles=new HashMap<>();if(!indoor)map.overworldTiles=tiles;
         map.tiles=tiles;map.refreshCache=true;map.onscreenTiles.clear();map.onscreenPokemon.clear();map.onscreenBurrowed.clear();
         map.onscreenFossils.clear();map.pokemon.clear();
         for(int y=-256;y<=256;y+=16)for(int x=-256;x<=256;x+=16)put(ground,"",x,y,biome);
         player.position.set(0,0);player.spawnLoc.set(0,0);player.dirFacing="down";
         cam.position.set(8,8,0);cam.zoom=1.3f;map.timeOfDay="day";mapBatch.setColor(Color.WHITE);
      }
      void materialCoverage() {
         BwAssets art=BwAssets.get();int profiles=0;
         for(BiomeProfiles.Profile p:BiomeProfiles.all()) {
            for(String role:new String[]{"ground","path","rock","ash"})
               require(art.namedAnimated(p.terrain(role),0)!=null,"Missing terrain "+p.id+"/"+role);
            for(String role:new String[]{"front","top","ramp"})
               require(art.named(p.cliff(role))!=null,"Missing elevation material "+p.id+"/"+role);
            for(String role:new String[]{"tree","bush","grass","rock"})
               require(art.named(p.decor(role))!=null,"Missing regional decoration "+p.id+"/"+role);
            for(String role:new String[]{"water","shallow","lava","cooledLava"})
               require(art.namedAnimated(p.string("fluid",role,""),0)!=null,"Missing fluid "+p.id+"/"+role);
            profiles++;
         }
         require(profiles>=14,"World profile coverage regressed");
         System.out.println("NATURE materials: "+profiles+" complete biome profiles");
      }
      void windContract() {
         for(float x:new float[]{-1024,-16,-.001f,0,16,1024})for(float y:new float[]{-256,0,256})
            for(float time:new float[]{0,3.2f,4.5f,1200}) {
               require(WorldNatureMotion.wind(x,y,time,0,1.2f,false)==0,"A vegetation root moved");
               require(WorldNatureMotion.wind(x,y,time,1,1.2f,true)==0,"Reduced motion still bends vegetation");
               float a=WorldNatureMotion.wind(x,y,time,1,1.2f,false);
               require(Float.isFinite(a)&&Math.abs(a)<=1.201f,"Unbounded wind amplitude");
               require(Math.abs(a-WorldNatureMotion.wind(x+.001f,y,time,1,1.2f,false))<.01f,"Wind jumps at a tile boundary");
               require(Math.abs(a-WorldNatureMotion.wind(x,y,time+.001f,1,1.2f,false))<.01f,"Wind clock jumps");
            }
         require(!WorldNatureMotion.flexible("desert_cactus")&&!WorldNatureMotion.flexible("volcanic_charred_tree")
            &&!WorldNatureMotion.flexible("rock")&&!WorldNatureMotion.flexible("stump")
            &&!WorldNatureMotion.flexible("bush_dry"),"Rigid nature props bend");
         require(WorldNatureMotion.flexible("grass_dry_tuft"),"Dry grass cannot move in the wind");
         for(String identity:new String[]{"tree","tree_oak","tree_pine","tree_snow","forest_tree_willow"})
            require(WorldNatureMotion.treeFlexible(identity),"Living crown excluded from wind: "+identity);
         for(String identity:new String[]{"tree_dead","volcanic_charred_tree","desert_cactus","stump","rock","unknown_custom_tree"})
            require(!WorldNatureMotion.treeFlexible(identity),"Unreviewed or rigid tree bends: "+identity);
         for(float time:new float[]{0,3.2f,4.5f,1200})for(float fraction:new float[]{0,.2f,.4f,.45f,.5f,.8f,1}) {
            float dx=WorldNatureMotion.crownWind(32,-48,time,fraction,2,false);
            require(Float.isFinite(dx)&&Math.abs(dx)<=2.001f,"Unbounded crown wind");
            if(fraction<=.45f)require(dx==0,"Crown signal moves the rooted trunk");
            require(WorldNatureMotion.crownWind(32,-48,time,fraction,2,true)==0,"Reduced crown signal still moves");
            require(Math.abs(dx-WorldNatureMotion.crownWind(32,-48,time+.001f,fraction,2,false))<.02f,"Crown wind clock jumps");
         }
      }
      void plants(String ground,String biome,String tree) {
         world(ground,biome,false);
         for(int x:new int[]{-64,0,64})put(ground,tree,x,48,biome);
         for(int x:new int[]{-64,-32,32,64})put("grass4","",x,-16,biome);
         put(ground,"bush2_color",-48,16,biome);put(ground,"flower1",48,16,biome);
      }
      void shoreRestrictions() {
         Map<Vector2,Tile> tiles=new HashMap<>();
         for(int y=-32;y<=32;y+=16)for(int x=-32;x<=32;x+=16) {
            Tile t=new Tile("water1",new Vector2(x,y),true);t.biome="beach";tiles.put(t.position,t);
         }
         WorldNatureMotion motion=new WorldNatureMotion();WorldElevation elevation=new WorldElevation();
         Tile source=tiles.get(new Vector2(0,0));
         Tile sand=new Tile("sand4",new Vector2(16,0),true);sand.biome="beach";tiles.put(sand.position,sand);
         elevation.update(tiles);motion.prepare(tiles,source,elevation);
         require(motion.hasShore()&&motion.distance(16,8)==0,"Actual sand/water boundary lacks surf");
         for(String upper:new String[]{"ledges3_N","ledge_grass_ramp"}) {
            Tile obstruction=new Tile(upper.equals("ledge_grass_ramp")?upper:"sand4",upper.equals("ledge_grass_ramp")?"":upper,new Vector2(16,0),true);
            obstruction.biome="beach";tiles.put(obstruction.position,obstruction);
            elevation=new WorldElevation();elevation.update(tiles);motion.prepare(tiles,source,elevation);
            require(!motion.hasShore(),"Surf climbs a ledge or ramp: "+upper);
         }
      }
      void natureScenes()throws Exception {
         plants("green1","forest","tree1");compare("01-woodland-wind",true,true);
         plants("green1","savanna","tree_savanna1");compare("02-savanna-wind",true,true);
         plants("snow1","snow","tree4");compare("03-snow-vegetation",true,true);
         world("sand4","beach",false);
         for(int y=-256;y<32;y+=16)for(int x=-256;x<=256;x+=16)put("water1","",x,y,"beach");
         player.position.set(0,64);coastSeams("straight coast");compare("04-shore-surf",true,true);
         world("green1","wooded_lake",false);
         for(int y=-256;y<=256;y+=16)for(int x=-48;x<=16;x+=16)put("water1","",x,y,"wooded_lake");
         put("green1","tree6",64,48,"wooded_lake");player.position.set(64,0);
         coastSeams("river");compare("05-wetland-river",true,true);
         world("sand4","beach",false);
         for(int y=-80;y<=64;y+=16)for(int x=-96;x<=80;x+=16)
            if(x<16||y<16)put("water1","",x,y,"beach");
         player.position.set(32,32);coastSeams("bay corners");compare("06-bay-corners",true,true);
         world("mountain3","mountain",false);
         for(int y=16;y<=112;y+=16)for(int x=-64;x<=64;x+=16) {
            String edge=(y==16?"N":"")+(y==112?"S":"")+(x==-64?"E":"")+(x==64?"W":"");
            put("mountain3",edge.isEmpty()?"":"ledges3_"+edge,x,y,"mountain");
         }
         for(int y=-96;y<=96;y+=16)for(int x=0;x<=16;x+=16) {
            Tile t=put("water1",y==16?"ledges3_N":"",x,y,"mountain");
            if(y==16){map.waterfallify(t,map.tiles,true);require(t.isWaterfall,"Missing real waterfall");}
         }
         player.position.set(-32,0);cam.position.set(8,24,0);compare("07-waterfall-flow",true,true);
         world("volcano1","volcano",false);
         for(int y=-256;y<=256;y+=16)for(int x=-256;x<=256;x+=16)if(x<-16||x>16)put("lava1","",x,y,"volcano");
         put("volcano1","tree1",64,64,"volcano");compare("08-lava-flow",true,true);
         lavaMaterial();
         world("desert6","desert",false);put("desert6","cactus1",-32,32,"desert");
         put("desert6","rock1",48,32,"desert");compare("09-rigid-desert",false,true);
         world("house5_floor1","interior",true);put("house5_floor1","house_couch1",48,32,"interior");
         put("house5_floor1","pot1",-48,32,"interior");compare("10-interior-still",false,true);
         world("cave1_floor1","cave",true);put("cave1_floor1","rock1",48,32,"cave");
         compare("11-cave-still",false,true);
      }
      void openOcean()throws Exception {
         world("water1","beach",false);
         compare("12-open-ocean",true,true);
         byte[] a=draw(null,3.2f,false),near=draw(null,3.22f,false);
         int active=0,total=0;
         // Broad independent screen areas away from the trainer. These times
         // share one atlas frame, so regional change must come from the shader.
         for(int row=0;row<3;row++)for(int col=0;col<3;col++) {
            if(row==1&&col==1)continue;
            int changed=changedRegion(a,near,80+col*280,60+row*190,240,150);
            require(changed>100,"Open water is static outside the shore/player area: "+row+","+col+"="+changed);
            active++;total+=changed;
         }
         double variance=luminanceDeviation(a,80,60,240,150);
         require(active==8&&total>1600&&variance>4,"Open sea lacks distributed wave relief: changed="+total+", deviation="+variance);
         int water=0;
         for(float[] v:meshVertices("geometry",-1)) {
            require(v[7]==WorldNatureMotion.MAX_SHORE_DISTANCE,"Open-ocean fixture accidentally samples a shore");water++;
         }
         require(water>100,"Open-ocean probe did not render actual fluid geometry");
         System.out.println("NATURE ocean: same-atlas-frame changedPixels="+total+", activeRegions="+active+", luminanceDeviation="+variance+", all shoreDistances=16");
      }
      void lavaMaterial()throws Exception {
         byte[] pixels=draw(null,3.2f,false);int molten=0,crust=0;
         for(int y=80;y<560;y++)for(int x=24;x<300;x++) {
            int i=(y*WIDTH+x)*4,r=pixels[i]&255,g=pixels[i+1]&255,b=pixels[i+2]&255;
            if(r>180&&r>b*2&&g>50)molten++;
            if(r<115&&g<100&&b<115)crust++;
         }
         require(molten>15000&&crust>15000,"Lava lacks visibly distinct magma and dark crust: "+molten+"/"+crust);
         List<float[]> vertices=meshVertices("geometry",3.125f);
         require(vertices.size()>100,"Dedicated molten material is not used by actual lava geometry");
         for(float[] v:vertices) {
            require(Math.abs(v[4]-v[0]/64)<.001f&&Math.abs(v[5]-v[2]/64)<.001f,"Lava material restarts at a tile boundary");
         }
      }
      void crownScenes()throws Exception {
         world("green1","forest",false);put("green1","tree1",-32,32,"forest");
         put("green1","tree2",48,32,"forest");compare("13-living-crowns",true,true);
         draw(null,3.2f,false);List<float[]> a=meshVertices("geometry",5);
         draw(null,4.5f,false);List<float[]> b=meshVertices("geometry",5);
         require(a.size()==b.size()&&a.size()>24,"Living crowns lack subdivided geometry");
         float low=Float.POSITIVE_INFINITY,high=Float.NEGATIVE_INFINITY;
         for(float[] v:a){low=Math.min(low,v[1]);high=Math.max(high,v[1]);}
         int fixed=0,moved=0;
         for(int i=0;i<a.size();i++) {
            float[] before=a.get(i),after=b.get(i);float fraction=(before[1]-low)/(high-low);
            for(int component=1;component<8;component++)require(Float.floatToIntBits(before[component])==Float.floatToIntBits(after[component]),"Wind changed crown depth/UV/height/tint");
            float displacement=Math.abs(before[0]-after[0]);
            require(displacement<4.01f,"Crown wind is unbounded");
            if(fraction<=.4f){require(displacement==0,"Wind detached a tree trunk from its collision tile");fixed++;}
            else if(displacement>.001f)moved++;
         }
         require(fixed>6&&moved>12,"Actual tree geometry did not keep roots fixed while moving crowns");
         draw(null,3.2f,true);List<float[]> stillA=meshVertices("geometry",5);
         draw(null,4.5f,true);List<float[]> stillB=meshVertices("geometry",5);
         require(equalVertices(stillA,stillB),"Reduced motion still moves crown vertices");
         System.out.println("NATURE crowns: fixed lower vertices="+fixed+", moving crown vertices="+moved+", UV/depth/height unchanged");
         world("desert6","desert",false);put("desert6","tree1",-32,32,"desert");
         require(!WorldNatureMotion.treeFlexible(WorldMaterials.treeKey(map.tiles.get(new Vector2(-32,32)))),"Rigid desert tree selected for wind");
         compare("14-rigid-tree",false,true);
      }
      void lightingScene()throws Exception {
         world("green1","forest",false);put("green1","tree1",-48,32,"forest");
         Tile torch=put("green1","",32,32,"forest");map.onscreenTiles.add(torch);
         byte[] day=draw("15-lighting-day",3.2f,true);List<float[]> dayShadows=meshVertices("shadows",Float.NaN);
         map.timeOfDay="night";mapBatch.setColor(PkmnMap.nightColor);
         byte[] night=draw("15-lighting-night",3.2f,true);List<float[]> nightShadows=meshVertices("shadows",Float.NaN);
         require(meanLuminance(day)>meanLuminance(night)*1.15,"Directional lighting erased the original night cycle");
         require(!equalVertices(dayShadows,nightShadows),"Tree/actor shadows do not respond to day/night lighting");
         torch.isTorch=true;byte[] lit=draw("15-lighting-torch",3.2f,true);
         PerspectiveCamera camera=(PerspectiveCamera)field(JohtoRenderer.class,"camera").get(renderer());
         Vector3 near=camera.project(new Vector3(40,.2f,-24),0,0,WIDTH,HEIGHT);
         Vector3 far=camera.project(new Vector3(-80,.2f,-24),0,0,WIDTH,HEIGHT);
         int nearChanged=changedRegion(night,lit,(int)near.x-8,(int)near.y-8,16,16);
         int farChanged=changedRegion(night,lit,(int)far.x-8,(int)far.y-8,16,16);
         require(nearChanged>150,"Local torch produces no visible ground lighting");
         require(farChanged==0,"Local torch illuminates unrelated distant terrain");
         require(Arrays.equals(lit,draw(null,11f,true)),"Reduced motion still flickers local lighting");
         torch.isTorch=false;require(Arrays.equals(night,draw(null,3.2f,true)),"Torch removal left stale light/shadow state");
         scenes++;System.out.println("NATURE lighting: dayLuma="+meanLuminance(day)+", nightLuma="+meanLuminance(night)
            +", nearTorchPixels="+nearChanged+", farTorchPixels="+farChanged+", shadows respond; reduced motion stable");
      }
      List<float[]> meshVertices(String name,float style)throws Exception {
         WorldMeshBatch batch=(WorldMeshBatch)field(JohtoRenderer.class,name).get(renderer());
         Map<?,?> groups=(Map<?,?>)field(WorldMeshBatch.class,"groups").get(batch);List<float[]> result=new ArrayList<>();
         for(Object buffer:groups.values()) {
            com.badlogic.gdx.utils.FloatArray v=(com.badlogic.gdx.utils.FloatArray)field(buffer.getClass(),"vertices").get(buffer);
            for(int i=0;i<v.size;i+=8)if(Float.isNaN(style)||v.get(i+6)==style) {
               float[] vertex=new float[8];for(int c=0;c<8;c++)vertex[c]=v.get(i+c);result.add(vertex);
            }
         }
         return result;
      }
      boolean equalVertices(List<float[]> a,List<float[]> b) {
         if(a.size()!=b.size())return false;
         for(int i=0;i<a.size();i++)if(!Arrays.equals(a.get(i),b.get(i)))return false;return true;
      }
      int changedRegion(byte[] a,byte[] b,int left,int bottom,int width,int height) {
         require(left>=0&&bottom>=0&&left+width<=WIDTH&&bottom+height<=HEIGHT,"Pixel probe outside view");
         int count=0;for(int y=bottom;y<bottom+height;y++)for(int x=left;x<left+width;x++) {
            int i=(y*WIDTH+x)*4;if(a[i]!=b[i]||a[i+1]!=b[i+1]||a[i+2]!=b[i+2])count++;
         }return count;
      }
      double meanLuminance(byte[] pixels) {
         double sum=0;for(int i=0;i<pixels.length;i+=4)sum+=luma(pixels,i);return sum/(WIDTH*HEIGHT);
      }
      double luminanceDeviation(byte[] pixels,int left,int bottom,int width,int height) {
         double sum=0,squared=0;int count=0;
         for(int y=bottom;y<bottom+height;y++)for(int x=left;x<left+width;x++) {
            double value=luma(pixels,(y*WIDTH+x)*4);sum+=value;squared+=value*value;count++;
         }return Math.sqrt(Math.max(0,squared/count-(sum/count)*(sum/count)));
      }
      double luma(byte[] pixels,int index) {
         return .2126*(pixels[index]&255)+.7152*(pixels[index+1]&255)+.0722*(pixels[index+2]&255);
      }
      void coastSeams(String name)throws Exception {
         WorldElevation elevation=new WorldElevation();elevation.update(map.tiles);
         WorldNatureMotion first=new WorldNatureMotion(),second=new WorldNatureMotion();
         int samples=0,shoreTiles=0;
         for(Tile t:map.tiles.values()) {
            if(!WorldNatureMotion.water(t)&&!WorldNatureMotion.sand(t))continue;
            first.prepare(map.tiles,t,elevation);if(first.hasShore())shoreTiles++;
            for(int[] direction:new int[][]{{16,0},{0,16}}) {
               Tile next=map.tiles.get(new Vector2(t.position.x+direction[0],t.position.y+direction[1]));
               if(next==null||WorldNatureMotion.water(next)!=WorldNatureMotion.water(t)
                  ||WorldNatureMotion.sand(next)!=WorldNatureMotion.sand(t))continue;
               second.prepare(map.tiles,next,elevation);
               for(int step=0;step<=4;step++) {
                  float x=t.position.x+(direction[0]==16?16:step*4),y=t.position.y+(direction[1]==16?16:step*4);
                  require(Math.abs(first.distance(x,y)-second.distance(x,y))<.001f,"Shore phase seam in "+name+" at "+x+","+y);
                  samples++;
               }
            }
         }
         require(shoreTiles>10&&samples>100,"Shore fixture lacked useful boundaries: "+name);
         System.out.println("NATURE coast "+name+": "+samples+" shared-edge samples, "+shoreTiles+" actual shore tiles");
      }
      Map<Tile,String> state()throws Exception {
         Map<Tile,String> result=new HashMap<>();
         for(Tile t:map.tiles.values()) {
            StringBuilder s=new StringBuilder().append(t.position).append('|').append(t.name).append('|').append(t.nameUpper)
               .append('|').append(t.biome).append('|').append(t.ledgeDir).append('|').append(t.hasItem).append('|').append(t.hasItemAmount)
               .append('|').append(t.items).append('|').append(t.doorTiles).append('|').append(t.squishedTiles)
               .append('|').append(System.identityHashCode(t.routeBelongsTo));
            for(Field f:tileFlags)s.append(f.getBoolean(t)?'1':'0');result.put(t,s.toString());
         }
         return result;
      }
      byte[] draw(String name,float clock,boolean reduced)throws Exception {
         JohtoRenderer r=renderer();modernUi.desktop.preferences.reducedMotion=reduced;
         float delta=Math.min(.1f,Math.max(0,Gdx.graphics.getDeltaTime()));field(JohtoRenderer.class,"seconds").setFloat(r,clock-delta);
         ((Map<?,?>)field(JohtoRenderer.class,"motion").get(r)).clear();
         Game.rand.setSeed(9917);map.rand.setSeed(9923);
         Map<Tile,String> before=state();long fingerprint=ModernWorldGenerator.fingerprint(map.tiles);
         Gdx.gl.glViewport(0,0,WIDTH,HEIGHT);require(r.render(this),"Modern renderer skipped "+name);
         require(before.equals(state())&&fingerprint==ModernWorldGenerator.fingerprint(map.tiles),"Nature changed saved terrain/flags "+name);
         require(Game.rand.nextLong()==new Random(9917).nextLong(),"Nature consumed gameplay RNG");
         require(map.rand.nextLong()==new Random(9923).nextLong(),"Nature consumed map RNG");
         require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"OpenGL error in "+name);
         byte[] pixels=ScreenUtils.getFrameBufferPixels(0,0,WIDTH,HEIGHT,false);
         if(name!=null)capture(name);
         return pixels;
      }
      void compare(String name,boolean animated,boolean requireReducedExact)throws Exception {
         byte[] a=draw(name+"-a",3.2f,false),near=draw(null,3.22f,false),b=draw(name+"-b",4.5f,false);
         int changed=changed(a,b);double nearDelta=meanDelta(a,near),farDelta=meanDelta(a,b);
         require(animated?changed>20:changed==0,"Incorrect nature motion in "+name+": "+changed+" pixels");
         if(name.startsWith("04-")||name.startsWith("05-")||name.startsWith("06-")) {
            // These two times use the same water-atlas frame. Movement here
            // therefore comes from the continuous wave/shore shader itself.
            require(changed(a,near)>20,"Water changes only when the old atlas frame advances: "+name);
            fluidGeometry(!name.startsWith("05-"));
         }
         if(animated)require(nearDelta<=farDelta*2+.03,"Nature jumps between adjacent times in "+name);
         byte[] repeat=draw(null,3.2f,false);require(Arrays.equals(a,repeat),"Same nature time/state renders differently in "+name);
         byte[] reducedA=draw(name+"-reduced",3.2f,true),reducedB=draw(null,4.5f,true);
         if(requireReducedExact)require(Arrays.equals(reducedA,reducedB),"Reduced motion still animates "+name+": "+changed(reducedA,reducedB));
         else require(changed(reducedA,reducedB)<changed/8+1,"Reduced motion leaves broad lava motion");
         byte[] restored=draw(null,3.2f,false);require(Arrays.equals(a,restored),"Reduced motion did not reset cleanly in "+name);
         scenes++;System.out.println("NATURE scene="+name+" changedPixels="+changed+" nearMeanDelta="+nearDelta
            +" farMeanDelta="+farDelta+" reducedChanged="+changed(reducedA,reducedB)+" repeat=identical tiles/RNG=unchanged");
      }
      void fluidGeometry(boolean sandy)throws Exception {
         WorldMeshBatch batch=(WorldMeshBatch)field(JohtoRenderer.class,"geometry").get(renderer());
         Map<?,?> groups=(Map<?,?>)field(WorldMeshBatch.class,"groups").get(batch);
         Map<String,Float> heights=new HashMap<>();int water=0,sand=0,shared=0;
         for(Object buffer:groups.values()) {
            com.badlogic.gdx.utils.FloatArray vertices=(com.badlogic.gdx.utils.FloatArray)field(buffer.getClass(),"vertices").get(buffer);
            for(int i=0;i<vertices.size;i+=8) {
               float style=vertices.get(i+6);if(style>=-.5f)continue;
               if(style< -1.5f)sand++;else water++;
               if(style> -1.5f)require(Math.abs(vertices.get(i+4)-vertices.get(i)/80)<.001f
                  &&Math.abs(vertices.get(i+5)-vertices.get(i+2)/80)<.001f,"Water wave texture restarts at a tile edge");
               float distance=vertices.get(i+7);
               require(Float.isFinite(distance)&&distance>=0&&distance<=WorldNatureMotion.MAX_SHORE_DISTANCE,"Invalid GPU shore distance");
               String coordinate=vertices.get(i)+":"+vertices.get(i+2);
               Float height=heights.putIfAbsent(coordinate,vertices.get(i+1));
               if(height!=null){require(Math.abs(height-vertices.get(i+1))<.001f,"Gap in actual water/beach mesh at "+coordinate);shared++;}
            }
         }
         require(water>24&&(!sandy||sand>24)&&shared>24,"Wave/beach shader not used by real geometry");
         System.out.println("NATURE actual fluid mesh: waterVertices="+water+", beachVertices="+sand+", sharedVertices="+shared+" with no height gap");
      }
      static int changed(byte[] a,byte[] b) {
         int n=0;for(int i=0;i<a.length;i+=4)if(a[i]!=b[i]||a[i+1]!=b[i+1]||a[i+2]!=b[i+2])n++;return n;
      }
      static double meanDelta(byte[] a,byte[] b) {
         long sum=0;for(int i=0;i<a.length;i+=4)for(int c=0;c<3;c++)sum+=Math.abs((a[i+c]&255)-(b[i+c]&255));
         return sum/(double)(WIDTH*HEIGHT*3);
      }
      void capture(String name)throws Exception {
         Pixmap pixels=ScreenUtils.getFrameBufferPixmap(0,0,WIDTH,HEIGHT);PixmapIO.PNG writer=new PixmapIO.PNG();writer.setFlipY(true);
         try{writer.write(Gdx.files.local(name+".png"),pixels);captures++;}finally{writer.dispose();pixels.dispose();}
      }
      @Override public void dispose(){try{super.dispose();}finally{disposed=true;}}
   }
}
