package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.FloatArray;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Actual packaged world rendering: sealed rounded relief, readable rock and stairs. */
public final class ReliefPresentationTest {
   private static Throwable failure;
   private static int checks;
   private static final boolean BEFORE=Boolean.getBoolean("relief.before");
   private static void require(boolean pass,String reason) {
      checks++;if(!pass)throw new IllegalStateException(reason);
   }
   static Field field(Class<?> owner,String name)throws Exception {
      Field result=owner.getDeclaredField(name);result.setAccessible(true);return result;
   }
   public static void main(String[] args) {
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      Sample sample=new Sample();
      Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
      cfg.setInitialVisible(false);cfg.setWindowedMode(960,720);cfg.useVsync(false);
      cfg.setForegroundFPS(120);cfg.setIdleFPS(120);cfg.setTitle("Relief presentation verification");
      new Lwjgl3Application(sample,cfg);
      if(failure!=null){failure.printStackTrace();System.exit(1);}
      require(sample.complete&&sample.disposed,"Incomplete native lifecycle");
      System.out.println("RELIEF PRESENTATION PASS: checks="+checks+", scenes="+sample.scenes
         +", captures="+sample.captures+", before="+BEFORE
         +", four stair directions, L-shaped convex/concave terrace, sealed corner, day/night rock pixels, unchanged tiles/RNG");
      System.exit(0);
   }
   static final class Sample extends Game {
      final String[] biomes={"mountain","desert","snow","forest"};
      int frames,scenes,captures;boolean ready,complete,disposed;
      Tile ramp,flat,corner;int direction;
      long fingerprint;
      Sample(){super(new String[0],6);}
      JohtoRenderer renderer()throws Exception{return (JohtoRenderer)field(Game.class,"johtoRenderer").get(this);}
      @Override public void render() {
         if(failure!=null)return;
         try {
            frames++;
            if(!ready) {
               super.render();if(frames==20){boot();ready=true;}
               return;
            }
            if(scenes==8){if(!BEFORE){verifyAssetLifecycle();verifyLayeredFloorMaterial();}complete=true;Gdx.app.exit();return;}
            configure(scenes);JohtoRenderer renderer=renderer();
            long seed=929831L+scenes;Game.rand.setSeed(seed);map.rand.setSeed(seed+1);
            require(renderer.render(this),"World composition rejected relief scene "+scenes);
            require(Game.rand.nextLong()==new Random(seed).nextLong(),"Renderer consumed gameplay RNG");
            require(map.rand.nextLong()==new Random(seed+1).nextLong(),"Renderer consumed map RNG");
            require(fingerprint==ModernWorldGenerator.fingerprint(map.tiles),"Renderer mutated saved terrain");
            verify(renderer);capture(String.format("%02d-%s-%s.png",scenes+1,biomes[scenes/2],scenes%2==0?"day":"night"));
            require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"OpenGL error in relief scene "+scenes);
            require(((WorldBatch)mapBatch).getSubmittedDraws()==0,"Legacy ground pixels entered relief fixture");
            scenes++;
         }catch(Throwable error){failure=error;Gdx.app.exit();}
      }
      void boot() {
         actionStack.removeIf(action->action instanceof DrawSetupMenu);Gdx.input.setInputProcessor(null);
         server=new Server();Network.register(server);map=new PkmnMap("unsaved-relief-fixture");
         configure(0);map.minimap=new Pixmap(80,80,Pixmap.Format.RGBA8888);map.minimap.setColor(.4f,.5f,.4f,1);map.minimap.fill();
         start();player.setCurrPokemon();player.acceptInput=false;
         actionStack.clear();actionStackCopy.clear();map.pokemon.clear();map.onscreenPokemon.clear();
         map.onscreenBurrowed.clear();map.onscreenFossils.clear();
      }
      int[] rotate(int x,int y,int turn) {
         for(int n=0;n<turn;n++){int previous=x;x=-y;y=previous;}
         return new int[]{x,y};
      }
      boolean high(int x,int y,int turn) {
         int[] p=rotate(x,y,(4-turn)%4);
         return p[0]>=-5&&p[0]<=5&&p[1]>=-5&&p[1]<=5&&!(p[0]>1&&p[1]>1);
      }
      void configure(int scene) {
         String biome=biomes[scene/2];direction=scene/2;map.tiles=new java.util.LinkedHashMap<>();
         map.overworldTiles=map.tiles;map.timeOfDay=scene%2==0?"day":"night";map.currBiome=biome;
         String ground=biome.equals("mountain")?"mountain3":biome.equals("desert")?"sand1":biome.equals("snow")?"snow1":"green1";
         ramp=flat=corner=null;
         for(int y=-16;y<=16;y++)for(int x=-16;x<=16;x++) {
            String upper="",lower=ground;
            if(high(x,y,direction)) {
               String edges="";
               if(!high(x,y-1,direction))edges+="N";if(!high(x,y+1,direction))edges+="S";
               if(!high(x-1,y,direction))edges+="E";if(!high(x+1,y,direction))edges+="W";
               if(!edges.isEmpty())upper="ledges3_"+edges;
            }
            int[] canonical=rotate(x,y,(4-direction)%4);
            boolean entrance=canonical[1]==-5&&Math.abs(canonical[0])<=1;
            boolean flatConnector=x==-7&&y==-7;
            if(entrance||flatConnector){lower="ledge_grass_ramp";upper="";}
            Tile tile=new Tile(lower,upper,new Vector2(x*16,y*16),true);tile.biome=biome;map.tiles.put(tile.position.cpy(),tile);
            if(entrance&&canonical[0]==0)ramp=tile;
            if(flatConnector)flat=tile;
            if(upper.contains("N")&&upper.contains("E")&&(corner==null||x+y<corner.position.x/16+corner.position.y/16))corner=tile;
         }
         require(ramp!=null&&flat!=null&&corner!=null,"Incomplete convex/concave/stairs fixture");
         map.bottomLeft=new Vector2(-256,-256);map.topRight=new Vector2(256,256);
         player.position.set(0,-144);player.spawnLoc.set(player.position);cam.position.set(8,-24,0);cam.zoom=2.6f;
         map.refreshCache=true;mapBatch.setColor(scene%2==0?Color.WHITE:new Color(.46f,.51f,.68f,1));
         fingerprint=ModernWorldGenerator.fingerprint(map.tiles);
      }
      void verify(JohtoRenderer renderer)throws Exception {
         WorldElevation elevation=renderer.getElevation();
         require(elevation.isSloped(ramp),"Actual stair entrance is flat, direction "+direction);
         require(!elevation.isSloped(flat),"Flat connector invented a cliff");
         float x=ramp.position.x,y=ramp.position.y;
         // Follow the original traversable centerline through low side, ramp and plateau.
         int[] inward=rotate(0,1,direction);float previous=elevation.height(x+8-inward[0]*24,y+8-inward[1]*24),min=previous,max=previous;
         for(float step=-23.5f;step<=24;step+=.5f) {
            float current=elevation.height(x+8+inward[0]*step,y+8+inward[1]*step);
            require(Math.abs(current-previous)<1,"Stair-to-ground height seam in direction "+direction);
            min=Math.min(min,current);max=Math.max(max,current);previous=current;
         }
         require(max-min>6,"Stair centerline does not connect two actual elevations");
         List<float[]> triangles=triangles(renderer);
         verifySealedCorner(renderer,elevation,triangles);
         verifyConcaveJoin(elevation,triangles);
         rockPixels(renderer,elevation);
         stairPixels(renderer,elevation);
         // This checks the plane the walking actor actually reads, separately from stair art.
         require(elevation.height(player.position.x+8,player.position.y+8)==0,"Exterior actor anchor gained an artificial rise");
         require(renderer.getLoadedSpeciesCount()==0,"Relief fixture unexpectedly loaded 3D models");
      }
      List<float[]> triangles(JohtoRenderer renderer)throws Exception {
         WorldMeshBatch batch=(WorldMeshBatch)field(JohtoRenderer.class,"geometry").get(renderer);
         Map<?,?> groups=(Map<?,?>)field(WorldMeshBatch.class,"groups").get(batch);List<float[]> result=new ArrayList<>();
         for(Object buffer:groups.values()) {
            FloatArray vertices=(FloatArray)field(buffer.getClass(),"vertices").get(buffer);
            require(vertices.size%24==0,"Opaque relief contains an incomplete triangle");
            for(int i=0;i<vertices.size;i+=24)result.add(new float[]{vertices.get(i),vertices.get(i+1),vertices.get(i+2),
               vertices.get(i+8),vertices.get(i+9),vertices.get(i+10),vertices.get(i+16),vertices.get(i+17),vertices.get(i+18)});
         }
         return result;
      }
      void verifySealedCorner(JohtoRenderer renderer,WorldElevation elevation,List<float[]> triangles) {
         float x=corner.position.x,y=corner.position.y,top=elevation.tileHeight(corner);int holes=0;
         Set<Integer> orientations=new HashSet<>();
         Vector3 a=new Vector3(),b=new Vector3(),c=new Vector3(),normal=new Vector3(),hit=new Vector3();
         for(float[] t:triangles) {
            a.set(t[0],t[1],t[2]);b.set(t[3],t[4],t[5]);c.set(t[6],t[7],t[8]);
            normal.set(b).sub(a).crs(new Vector3(c).sub(a)).nor();
            float mx=(a.x+b.x+c.x)/3,my=-(a.z+b.z+c.z)/3;
            if(mx>=x-.01f&&mx<x+6&&my>=y-.01f&&my<y+6&&Math.abs(normal.y)<.1f&&Math.abs(a.y-b.y)+Math.abs(b.y-c.y)>3)
               orientations.add(Math.round(MathUtils.atan2(normal.z,normal.x)*MathUtils.radiansToDegrees/5));
         }
         for(float dy=.23f;dy<5;dy+=.7f)for(float dx=.31f;dx<5;dx+=.7f) {
            Ray ray=new Ray(new Vector3(x+dx,top+20,-y-dy),new Vector3(0,-1,0));boolean covered=false;
            for(float[] t:triangles) {
               a.set(t[0],t[1],t[2]);b.set(t[3],t[4],t[5]);c.set(t[6],t[7],t[8]);
               if(Intersector.intersectRayTriangle(ray,a,b,c,hit)&&hit.y>=-.01f){covered=true;break;}
            }
            if(!covered)holes++;
         }
         System.out.println("RELIEF corner scene="+scenes+" holes="+holes+" faceOrientations="+orientations.size()+" terraceHeight="+top);
         if(!BEFORE) {
            require(holes==0,"Rounded corner left an uncovered wedge: "+holes);
            require(orientations.size()>=4,"Cliff corner remains a single straight chamfer: "+orientations.size());
         }
         require(top>6,"Convex corner lost upper terrain");
      }
      void verifyConcaveJoin(WorldElevation elevation,List<float[]> triangles) {
         Vector3 a=new Vector3(),b=new Vector3(),c=new Vector3(),hit=new Vector3();
         float min=Float.MAX_VALUE,max=-Float.MAX_VALUE;
         for(float dy=-3.7f;dy<4;dy+=1)for(float dx=-3.7f;dx<4;dx+=1) {
            // Cell centers rotate around (8,8), so the inside corner of the
            // L-shaped footprint follows that same logical lattice rotation.
            float x=24+dx,y=24+dy;
            for(int turn=0;turn<direction;turn++){float previous=x;x=-y;y=previous;}
            x+=8;y+=8;float expected=elevation.height(x,y),top=-Float.MAX_VALUE;
            min=Math.min(min,expected);max=Math.max(max,expected);
            Ray ray=new Ray(new Vector3(x,expected+20,-y),new Vector3(0,-1,0));
            for(float[] t:triangles) {
               a.set(t[0],t[1],t[2]);b.set(t[3],t[4],t[5]);c.set(t[6],t[7],t[8]);
               if(Intersector.intersectRayTriangle(ray,a,b,c,hit))top=Math.max(top,hit.y);
            }
            require(top>=-.01f,"Concave terrace join contains an uncovered seam");
            require(Math.abs(top-expected)<.5f,"Concave rounding moved the actual high/low contour");
         }
         require(max-min>6,"Concave join fixture does not straddle two actual elevations");
         float vx=24,vy=24;
         for(int turn=0;turn<direction;turn++){float previous=vx;vx=-vy;vy=previous;}
         vx+=8;vy+=8;
         int[] lowDirection=rotate(1,1,direction);
         // Two rays straddle the joint: an exact triangle-edge ray can be
         // rejected by floating-point barycentric tests in one orientation.
         // The old diverging end points leave both of these rays uncovered.
         for(float fraction:new float[]{.35f,.50f,.70f})for(float offset:new float[]{-.12f,.12f}) {
            float height=MathUtils.lerp(min,max,fraction),nearest=Float.MAX_VALUE;
            Ray ray=new Ray(new Vector3(vx+lowDirection[0]*6-lowDirection[1]*offset,height,
                  -vy-lowDirection[1]*6-lowDirection[0]*offset),
               new Vector3(-lowDirection[0],0,lowDirection[1]).nor());
            for(float[] t:triangles) {
               a.set(t[0],t[1],t[2]);b.set(t[3],t[4],t[5]);c.set(t[6],t[7],t[8]);
               if(Intersector.intersectRayTriangle(ray,a,b,c,hit))nearest=Math.min(nearest,hit.dst(ray.origin));
            }
            require(nearest<12,"Concave vertical face has a lateral slit at "+fraction+", offset="+offset+", nearest="+nearest+", direction="+direction);
         }
      }
      void rockPixels(JohtoRenderer renderer,WorldElevation elevation)throws Exception {
         PerspectiveCamera camera=(PerspectiveCamera)field(JohtoRenderer.class,"camera").get(renderer);
         Pixmap pixels=com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(0,0,960,720);
         Set<Integer> colors=new HashSet<>();int samples=0,bright=0,min=255,max=0;
         try {
            for(Tile tile:map.tiles.values()) {
               if(tile.nameUpper==null||!tile.nameUpper.contains("N")||tile.nameUpper.contains("E")||tile.nameUpper.contains("W"))continue;
               float x=tile.position.x,y=tile.position.y,high=elevation.tileHeight(tile),low=elevation.height(x+8,y-.02f);
               if(high-low<6)continue;
               for(float dx=2;dx<=14;dx+=2)for(float f=.18f;f<.85f;f+=.11f) {
                  Vector3 point=new Vector3(x+dx,MathUtils.lerp(low,high,f),-y);camera.project(point,0,0,960,720);
                  if(point.x<2||point.x>957||point.y<2||point.y>717)continue;
                  int pixel=pixels.getPixel(Math.round(point.x),Math.round(point.y));
                  int r=(pixel>>>24)&255,g=(pixel>>>16)&255,b=(pixel>>>8)&255,value=(r+g+b)/3;
                  min=Math.min(min,value);max=Math.max(max,value);colors.add(pixel);if(value>=18)bright++;samples++;
               }
            }
         }finally{pixels.dispose();}
         require(samples>=100,"Insufficient projected visible cliff face samples: "+samples);
         require(bright>samples*.95f,"Rock face became a black wall, readable="+bright+"/"+samples);
         System.out.println("RELIEF rock scene="+scenes+" colors="+colors.size()+" luminanceRange="+min+".."+max+" readable="+bright+"/"+samples);
         if(!BEFORE)require(colors.size()>16&&max-min>10,"Projected cliff art lacks rock texture contrast: colors="+colors.size()+", range="+(max-min));
      }
      void stairPixels(JohtoRenderer renderer,WorldElevation elevation)throws Exception {
         PerspectiveCamera camera=(PerspectiveCamera)field(JohtoRenderer.class,"camera").get(renderer);
         java.lang.reflect.Method draw=JohtoRenderer.class.getDeclaredMethod("draw",Game.class);draw.setAccessible(true);
         Vector3 originalPosition=camera.position.cpy(),originalDirection=camera.direction.cpy(),originalUp=camera.up.cpy();
         try {
            for(Tile tile:new Tile[]{ramp,flat}) {
               if(tile==ramp) {
                  // View a north-facing entrance from its low side. The regular
                  // overworld shot remains untouched; an occluded stair cannot be
                  // evaluated as if the top surface were a visible stair texture.
                  Vector3 originalTarget=new Vector3(cam.position.x,elevation.height(player.position.x+8,player.position.y+8),-cam.position.y);
                  Vector3 target=new Vector3(tile.position.x+8,elevation.height(tile.position.x+8,tile.position.y+8),-tile.position.y-8);
                  camera.position.set(originalPosition).sub(originalTarget).rotate(Vector3.Y,direction*90).add(target);
                  camera.lookAt(target);camera.update();draw.invoke(renderer,this);
               } else {
                  camera.position.set(originalPosition);camera.direction.set(originalDirection);camera.up.set(originalUp);camera.update();draw.invoke(renderer,this);
               }
               Pixmap pixels=com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(0,0,960,720);
               int[] forward=tile==ramp?rotate(0,1,direction):new int[]{0,1};
               Set<Integer> colors=new HashSet<>();int min=255,max=0,samples=0;
               try {
                  for(float across=-4;across<=4;across+=2)for(float along=-7;along<=7;along+=.5f) {
                     float x=tile.position.x+8+forward[0]*along-forward[1]*across;
                     float y=tile.position.y+8+forward[1]*along+forward[0]*across;
                     Vector3 point=new Vector3(x,elevation.height(x,y)+.2f,-y);camera.project(point,0,0,960,720);
                     if(point.x<1||point.x>958||point.y<1||point.y>718)continue;
                     int pixel=pixels.getPixel(Math.round(point.x),Math.round(point.y));
                     int value=(((pixel>>>24)&255)+((pixel>>>16)&255)+((pixel>>>8)&255))/3;
                     min=Math.min(min,value);max=Math.max(max,value);colors.add(pixel);samples++;
                  }
               }finally{pixels.dispose();}
               require(samples>=100,"Stair surface is outside fixture viewport");
               System.out.println("RELIEF stairs scene="+scenes+" sloped="+(tile==ramp)+" colors="+colors.size()+" contrast="+(max-min));
               if(!BEFORE)require(colors.size()>=8&&max-min>=12,"Stair tread/riser contrast disappeared: "+(tile==ramp?"sloped":"flat")
                  +", colors="+colors.size()+", contrast="+(max-min));
               if(tile==ramp&&scenes%2==0)capture("stairs-direction-"+direction+".png");
            }
         }finally {
            camera.position.set(originalPosition);camera.direction.set(originalDirection);camera.up.set(originalUp);camera.update();draw.invoke(renderer,this);
         }
      }
      void verifyAssetLifecycle()throws Exception {
         // Reflection keeps the identical fixture compilable against the previous JAR.
         Class<?> type=Class.forName("com.pkmngen.game.TerrainReliefAssets");
         Object assets=type.getMethod("get").invoke(null);
         java.lang.reflect.Method face=type.getMethod("face",Tile.class,BiomeProfiles.Profile.class);
         java.lang.reflect.Method stair=type.getMethod("stair",Tile.class,BiomeProfiles.Profile.class);
         BiomeProfiles.Profile profile=BiomeProfiles.visualForTile(corner);
         TextureRegion original=(TextureRegion)face.invoke(assets,corner,profile);
         TextureRegion steps=(TextureRegion)stair.invoke(assets,ramp,profile);
         require(original!=null&&steps!=null,"Dedicated cliff/stair material missing from packaged resources");
         for(int n=0;n<32;n++) {
            require(face.invoke(assets,corner,profile)==original,"Cliff material creates an unbounded per-frame texture");
            require(stair.invoke(assets,ramp,profile)==steps,"Stair material creates an unbounded per-frame texture");
         }
         require(original.getTexture().getUWrap()==Texture.TextureWrap.Repeat,"Long mountain faces cannot repeat dedicated rock art");
         require(original.getTexture().getMinFilter()==Texture.TextureFilter.Nearest,"Dedicated pixel-art face was blurred by sampling");
         Map<?,?> textures=(Map<?,?>)field(type,"textures").get(assets);
         Tile mountain=new Tile("mountain3","",new Vector2(0,0),true);mountain.biome="mountain";
         TextureRegion rock=(TextureRegion)face.invoke(assets,mountain,BiomeProfiles.named("mountain"));
         TextureRegion earth=(TextureRegion)type.getMethod("earth").invoke(assets);
         TextureRegion lava=(TextureRegion)type.getMethod("lava").invoke(assets);
         TextureRegion water=(TextureRegion)type.getMethod("water").invoke(assets);
         require(rock!=earth&&earth!=steps&&lava!=rock,"Earth, mountain and lava materials lost their distinct identity");
         require(lava.getTexture().getUWrap()==Texture.TextureWrap.Repeat,"Lava cannot flow continuously across tiles");
         require(water!=lava&&water.getTexture().getUWrap()==Texture.TextureWrap.Repeat,"Water lacks its own continuous wave material");
         TextureRegion cap=(TextureRegion)type.getMethod("cap").invoke(assets);
         require(cap!=rock&&cap!=earth&&cap!=water&&cap!=lava&&cap!=steps,"Weathered rock cap lacks its dedicated material");
         require(cap.getTexture().getUWrap()==Texture.TextureWrap.Repeat,"Rock shoulders cannot continue across adjacent tiles");
         require(textures.size()==6,"Terrain cache grew beyond the six shared materials");
         for(String fluid:new String[]{"water1","lava1"}) {
            Tile tile=new Tile(fluid,"",new Vector2(0,0),true);tile.biome="mountain";
            require(face.invoke(assets,tile,profile)==null,"Dry rock replacement leaked onto a fluid bank");
         }
         Texture disposed=original.getTexture();type.getMethod("disposeShared").invoke(null);
         require(disposed.getTextureObjectHandle()==0,"Shared relief disposal leaked the GPU texture");
         Object reloaded=type.getMethod("get").invoke(null);
         TextureRegion replacement=(TextureRegion)face.invoke(reloaded,corner,profile);
         require(reloaded!=assets&&replacement.getTexture()!=disposed&&replacement.getTexture().getTextureObjectHandle()!=0,
            "Relief assets cannot recover after disposal");
         require(renderer().render(this),"World composition failed after relief material recreation");
         require(fingerprint==ModernWorldGenerator.fingerprint(map.tiles),"Material recreation mutated saved terrain");
         require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"Relief material recreation left a GL error");
         System.out.println("RELIEF materials: six cached shared textures, distinct earth/rock/cap/lava/water/stairs, dry-only face, Repeat+Nearest, disposal/recreation PASS");
      }
      void verifyLayeredFloorMaterial()throws Exception {
         JohtoRenderer renderer=renderer();
         WorldMeshBatch batch=(WorldMeshBatch)field(JohtoRenderer.class,"geometry").get(renderer);
         Map<?,?> groups=(Map<?,?>)field(WorldMeshBatch.class,"groups").get(batch);
         java.lang.reflect.Method surface=JohtoRenderer.class.getDeclaredMethod("terrainSurface",Game.class,Tile.class,TextureRegion.class);
         surface.setAccessible(true);
         TextureRegion cap=TerrainReliefAssets.get().cap();
         // The real fixture has a raised interior at this coordinate; an exterior
         // sample would miss the cap override entirely and provide a false pass.
         Tile equivalent=new Tile("green1","house5_floor1",new Vector2(0,0),true);equivalent.biome="mountain";
         TextureRegion expected=BwAssets.get().terrain(equivalent,0);
         require(expected!=null&&expected.getTexture()!=cap.getTexture(),"Layered floor reference lacks its own texture");
         for(String base:new String[]{"mountain3","rock1"}) {
            Tile tile=new Tile(base,"house5_floor1",new Vector2(0,0),true);tile.biome="mountain";
            require(renderer.getElevation().tileHeight(tile)>3,"Layered floor regression is not on raised terrain");
            TextureRegion floor=BwAssets.get().terrain(tile,0);
            require(floor!=null&&floor.getTexture()==expected.getTexture(),"Floor resolution differs from equivalent green base");
            int floorBefore=bufferSize(groups,expected.getTexture()),capBefore=bufferSize(groups,cap.getTexture());
            surface.invoke(renderer,this,tile,floor);
            require(bufferSize(groups,expected.getTexture())==floorBefore+48,"Raised "+base+" floor did not submit its actual six surface vertices");
            require(bufferSize(groups,cap.getTexture())==capBefore,"Raised "+base+" floor was covered with geological cap");
         }
         require(fingerprint==ModernWorldGenerator.fingerprint(map.tiles),"Layered floor probe mutated the fixture map");
         System.out.println("RELIEF layered floors: raised mountain3/rock1 + house5_floor1 keep original wood-floor mesh texture, PASS");
      }
      int bufferSize(Map<?,?> groups,Texture texture)throws Exception {
         Object buffer=groups.get(texture);
         return buffer==null?0:((FloatArray)field(buffer.getClass(),"vertices").get(buffer)).size;
      }
      void capture(String name) {
         Pixmap pixels=com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(0,0,960,720);PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);
         try{png.write(Gdx.files.local(name),pixels);captures++;}
         catch(java.io.IOException error){throw new IllegalStateException(error);}
         finally{png.dispose();pixels.dispose();}
      }
      @Override public void dispose(){try{super.dispose();}finally{disposed=true;}}
   }
}
