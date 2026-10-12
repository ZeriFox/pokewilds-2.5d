package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import javax.imageio.ImageIO;

/** Isolated real Game.render fixtures: biome art, derived relief and original ghost action lifetime. */
public final class BiomeSceneTest {
   static void require(boolean value,String reason){if(!value)throw new IllegalStateException(reason);}
   public static void main(String[] args) {
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      Sample game=new Sample();
      Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
      config.setInitialVisible(false);config.setWindowedMode(640,576);config.setForegroundFPS(120);config.useVsync(false);
      try {
         new Lwjgl3Application(game,config);
         require(game.complete,"Biome scenes did not finish");
         System.out.println("BIOME SCENE PASS: dedicated desert art; volcanic/graveyard day/night; closed plateau and continuous ramp; waterfall; walkable high/low tides and localized puddles with visible sand bed; real SpawnGhost/DrawGhost/DespawnGhost lifecycle with zero post-despawn draws");
         System.exit(0);
      } catch(Throwable error){error.printStackTrace();System.exit(1);}
   }
   static final class Sample extends Game {
      int frame,phase=-1,phaseFrame,spawnFrames,drawFrames,despawnFrames;
      long previousRendered,ghostBefore,ghostAfter,waterfallBefore;
      boolean ready,complete;
      SpawnGhost spawn;
      Tile shallowProbe;
      Sample(){super(new String[0],4);}
      JohtoRenderer renderer(){try{Field f=Game.class.getDeclaredField("johtoRenderer");f.setAccessible(true);return(JohtoRenderer)f.get(this);}catch(Exception e){throw new IllegalStateException(e);}}
      Tile put(String ground,String upper,int x,int y,String biome) {
         Tile tile=new Tile(ground,upper,new Vector2(x,y),true);tile.biome=biome;map.tiles.put(tile.position,tile);return tile;
      }
      void empty(String ground,String biome) {
         map.overworldTiles=new HashMap<>();map.tiles=map.overworldTiles;
         for(int y=-192;y<=192;y+=16)for(int x=-192;x<=192;x+=16)put(ground,"",x,y,biome);
         map.refreshCache=true;map.onscreenPokemon.clear();map.pokemon.clear();
         player.position.set(0,0);player.spawnLoc.set(0,0);cam.position.set(8,8,0);
      }
      void time(String value){map.timeOfDay=value;mapBatch.setColor(value.equals("night")?PkmnMap.nightColor:Color.WHITE);}
      void initialize() {
         actionStack.removeIf(a->a instanceof DrawSetupMenu);Gdx.input.setInputProcessor(null);
         server=new Server();Network.register(server);map=new PkmnMap("biome-scene-fixture");
         Game.rand.setSeed(712903);map.rand.setSeed(712903);
         map.bottomLeft=new Vector2(-192,-192);map.topRight=new Vector2(192,192);
         map.minimap=new Pixmap(64,64,Pixmap.Format.RGBA8888);map.minimap.setColor(.3f,.5f,.4f,1);map.minimap.fill();
         empty("desert6","desert");player.name="Biome QA";player.character="gold";
         start();player.setCurrPokemon();player.acceptInput=false;
         actionStack.removeIf(a->a instanceof CycleDayNight);
         ready=true;nextPhase();
      }
      void plateau(String ground,String biome) {
         for(int y=16;y<=112;y+=16)for(int x=-48;x<=48;x+=16) {
            String upper="";
            if(y==16)upper="N";if(y==112)upper="S";
            if(x==-48)upper+="E";if(x==48)upper+="W";
            put(ground,upper.isEmpty()?"":"ledges3_"+upper,x,y,biome);
         }
         put("ledge_grass_ramp","",0,16,biome);
      }
      void nextPhase() {
         phase++;phaseFrame=0;
         if(phase==0) {
            empty("desert6","desert");plateau("desert6","desert");
            put("desert6","rock1",-48,-16,"desert");put("desert6","rock1",32,48,"desert");
            put("desert6","cactus1",48,-16,"desert");
            Tile rock=map.tiles.get(new Vector2(-48,-16));
            require(BwAssets.objectName(rock).equals("desert_rock"),"Desert rock used a generic prop");
            require(BiomeProfiles.forTile(rock).cliff("front").equals("desert_cliff"),"Desert cliff used a generic face");
            time("day");
         } else if(phase==1)time("night");
         else if(phase==2) {
            empty("volcano1","volcano");plateau("volcano2","volcano");
            for(int y=-80;y<=0;y+=16)for(int x=-64;x<=64;x+=16)if(x<-16||x>16)put("lava1","",x,y,"volcano");
            put("volcano1","rock_volcano1",-48,64,"volcano");put("volcano1","tree1",48,0,"volcano");time("day");
         } else if(phase==3)time("night");
         else if(phase==4) {
            empty("green12","graveyard");
            for(int y=16;y<=64;y+=32)for(int x=-64;x<=64;x+=32)put("green12","gravestone1",x,y,"graveyard");
            put("green12","tree1",-64,-16,"graveyard");put("green12","tree1",64,80,"graveyard");
            put("grass_graveyard1","",-48,-32,"graveyard");put("grass_graveyard1","",48,-16,"graveyard");time("day");
         } else if(phase==5)time("night");
         else if(phase==6) {
            empty("green1","mountain");plateau("mountain3","mountain");time("day");
         } else if(phase==7) {
            empty("green1","mountain");plateau("mountain3","mountain");
            for(int y=-64;y<=96;y+=16)for(int x=0;x<=16;x+=16) {
               Tile tile=put("water1",y==16?"ledges3_N":"",x,y,"mountain");
               if(y==16){map.waterfallify(tile,map.tiles,true);require(tile.isWaterfall&&tile.isLedge,"Real waterfallify fixture lacks both flags");}
            }
            put("mountain3","rock1",-32,48,"mountain");
            player.position.set(-32,0);cam.position.set(8,24,0);waterfallBefore=waterfallFrames();time("day");
         } else if(phase==8) {
            empty("sand4","beach");time("day");
            for(int y=-96;y<=112;y+=16)for(int x=-96;x<=16;x+=16)
               put(x<=-48?"water1":"sand4_tidalwater","",x,y,"beach");
            put("sand4_tidalwater","tree6",16,64,"beach");
            shallowProbe=map.tiles.get(new Vector2(-16,-32));
            require(shallowProbe.isTidal&&!shallowProbe.isWater,"Tile.init did not preserve walkable high tide");
            require(map.tiles.get(new Vector2(-64,-32)).isWater,"True water comparison cell lost isWater");
            require(BwAssets.terrainName(shallowProbe).equals("sand"),"High tide replaced its sand bed with opaque water");
         } else if(phase==9) {
            for(Tile tile:map.tiles.values())if(tile.name.contains("_tidalwater")) {
               tile.name=tile.name.replace("_tidalwater","_tidaloff");tile.init();
            }
            map.refreshCache=true;
            require(!shallowProbe.isTidal&&!shallowProbe.isWater,"Tile.init did not lower the tide");
            require(BwAssets.terrainName(shallowProbe).equals("sand"),"Low tide is not the same sand bed");
         } else if(phase==10) {
            empty("sand4","beach");time("day");
            for(int y=-96;y<=112;y+=16)for(int x=-96;x<=-48;x+=16)put("water1","",x,y,"beach");
            shallowProbe=put("sand4_puddle1","",-16,-32,"beach");
            put("sand4_puddle1","",16,32,"beach");put("sand4","tree6",48,64,"beach");
            require(!shallowProbe.isTidal&&!shallowProbe.isWater,"Dry-ground puddle became deep or tidal water");
            require(BwAssets.terrainName(shallowProbe).equals("sand"),"Puddle removed its visible dry bed");
         } else if(phase==11) {
            empty("green12","graveyard");time("night");
            ghostBefore=renderer().getGhostFrames();spawn=new SpawnGhost(this,new Vector2(48,32),true);insertAction(spawn);
         }
      }
      @Override public void render() {
         super.render();frame++;
         require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"Biome scene GL error frame "+frame);
         if(frame==20)initialize();
         if(!ready)return;
         if(frame>22){long now=renderer().getRenderedFrames();require(now>previousRendered,"Modern renderer dropped frame "+frame);previousRendered=now;}
         if(frame<=22)return;
         phaseFrame++;
         if(phase<11) {
            require(renderer().getGhostFrames()==0,"Ghost appeared with no scripted ghost action");
            if(phase==6 && phaseFrame==10){screenshot("06a-ramp-entry.png");player.position.set(0,20);cam.position.set(8,28,0);}
            if(phase==6 && phaseFrame==30){screenshot("06b-ramp-middle.png");player.position.set(0,64);cam.position.set(8,72,0);}
            if(phaseFrame==(phase==6?60:30)) {
               if(phase<=1)require(renderer().getFogDensity()==0,"Desert inherited graveyard fog");
               if(phase==4||phase==5)require(renderer().getFogDensity()>.01f,"Graveyard lost fog");
               if(phase==6)checkHeight();
               if(phase==7)require(waterfallFrames()>waterfallBefore,"Generated waterfall was swallowed by the earlier cliff dispatch");
               if(phase>=8)checkShallowLayers();
               String[] names={"desert-day","desert-night","volcano-day","volcano-night","graveyard-day","graveyard-night","plateau-ramp","waterfall","tidal-high","tidal-low","puddles"};
               screenshot(String.format("%02d-%s.png",phase<8?phase:phase+4,names[phase]));nextPhase();
            }
         } else {
            boolean spawning=false,drawing=false,despawning=false;
            for(Action action:actionStack) {
               if(action instanceof SpawnGhost)spawning=true;
               if(action instanceof DrawGhost)drawing=true;
               if(action instanceof DespawnGhost)despawning=true;
            }
            if(spawning)spawnFrames++;
            if(drawing)drawFrames++;
            if(despawning)despawnFrames++;
            if(spawnFrames==145)screenshot("08-ghost-spawning.png");
            if(drawFrames==20 && drawing){screenshot("09-ghost-chase.png");time("day");}
            if(despawnFrames==20)screenshot("10-ghost-despawning.png");
            if(!spawning&&!drawing&&!despawning) {
               if(ghostAfter==0){ghostAfter=renderer().getGhostFrames();screenshot("11-ghost-gone.png");}
               require(renderer().getGhostFrames()==ghostAfter,"Ghost continued drawing after its action disappeared");
               if(phaseFrame>300) {
                  require(spawnFrames>=165&&spawnFrames<=172,"Spawn animation duration changed: "+spawnFrames);
                  require(drawFrames==20&&despawnFrames>=79&&despawnFrames<=81,"Ghost action lifetime changed: "+drawFrames+"/"+despawnFrames);
                  require(ghostAfter>ghostBefore+200,"Modern ghost was never rendered through its lifecycle");
                  require(((WorldBatch)mapBatch).getSubmittedDraws()==0,"Legacy map draws leaked into biome scenes");
                  System.out.println("BIOME SCENE: worldFrames="+renderer().getRenderedFrames()+" spawn="+spawnFrames+" chase="+drawFrames+" despawn="+despawnFrames+" ghostDraws="+(ghostAfter-ghostBefore));
                  complete=true;Gdx.app.exit();
               }
            }
         }
         require(frame<850,"Biome/ghost fixture timed out");
      }
      void checkShallowLayers() {
         // Inspect the actual submitted translucent geometry, not a parallel renderer.
         // An opaque water replacement cannot satisfy both the dry-bed mapping above
         // and this independently measured translucent quad over the chosen tile.
         int overlays=0;
         try {
            Field groupsField=JohtoRenderer.class.getDeclaredField("translucent");groupsField.setAccessible(true);
            WorldMeshBatch owner=(WorldMeshBatch)groupsField.get(renderer());
            Field buffers=WorldMeshBatch.class.getDeclaredField("groups");buffers.setAccessible(true);
            Map<?,?> groups=(Map<?,?>)buffers.get(owner);
            for(Object mesh:groups.values()) {
               Field verticesField=mesh.getClass().getDeclaredField("vertices");verticesField.setAccessible(true);
               com.badlogic.gdx.utils.FloatArray array=(com.badlogic.gdx.utils.FloatArray)verticesField.get(mesh);
               for(int i=0;i+47<array.size;i+=48) {
                  float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,minY=Float.POSITIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
                  boolean shallow=true;
                  for(int v=0;v<6;v++) {
                     int offset=i+v*8;float x=array.items[offset],y=-array.items[offset+2],height=array.items[offset+1];
                     minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                     int alpha=Float.floatToRawIntBits(array.items[offset+3])>>>24;
                     if(Math.abs(height-.045f)>.001f||alpha<=0||alpha>=200)shallow=false;
                  }
                  if(!shallow||minX<shallowProbe.position.x||maxX>shallowProbe.position.x+16||minY<shallowProbe.position.y||maxY>shallowProbe.position.y+16)continue;
                  overlays++;
                  float expected=phase==10?10:16;
                  require(Math.abs(maxX-minX-expected)<.01f&&Math.abs(maxY-minY-expected)<.01f,"Puddle/tide overlay footprint incorrect");
               }
            }
         } catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
         require(overlays==(phase==9?0:1),"Live tide/puddle produced incorrect number of shallow overlays: "+overlays+" phase="+phase);
         System.out.println("BIOME SCENE shallow phase="+phase+" tile="+shallowProbe.name+" isTidal="+shallowProbe.isTidal+" isWater="+shallowProbe.isWater+" overlays="+overlays);
      }
      long waterfallFrames() {
         try{return ((Number)JohtoRenderer.class.getMethod("getWaterfallFrames").invoke(renderer())).longValue();}
         catch(ReflectiveOperationException error){throw new IllegalStateException("Native waterfall coverage counter unavailable",error);}
      }
      void checkHeight() {
         WorldElevation height=renderer().getElevation();
         float low=height.height(8,8),high=height.height(8,56),rise=BiomeProfiles.named("mountain").number("height","cliffHeight",0);
         require(high>low&&Math.abs(high-low-rise)<.001f,"Closed plateau was flattened or floated: low="+low+" high="+high+" rise="+rise);
         require(height.getConflicts()==0,"Consistent closed plateau has elevation conflicts");
         float previous=low;
         for(int y=16;y<=31;y++){float next=height.height(8,y);require(next>=previous-.001f&&next<=high+.001f,"Ramp discontinuity at "+y);previous=next;}
         require(height.height(8,17)>low&&height.height(8,31)<high,"Ramp did not interpolate between both surfaces");
         System.out.println("BIOME SCENE: plateau low="+low+" high="+high+" ramp17="+height.height(8,17)+" ramp31="+height.height(8,31));
      }
      void screenshot(String name) {
         int width=Gdx.graphics.getBackBufferWidth(),height=Gdx.graphics.getBackBufferHeight();
         byte[] pixels=ScreenUtils.getFrameBufferPixels(0,0,width,height,false);
         BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);Set<Integer> colors=new HashSet<>();
         for(int y=0;y<height;y++)for(int x=0;x<width;x++){int i=(y*width+x)*4;int rgb=(pixels[i]&255)<<16|(pixels[i+1]&255)<<8|(pixels[i+2]&255);colors.add(rgb);image.setRGB(x,height-1-y,rgb);}
         try{ImageIO.write(image,"png",new File(name));}catch(Exception e){throw new IllegalStateException(e);}
         require(colors.size()>20,"Biome frame blank "+name);
         if(name.equals("06b-ramp-middle.png")||name.equals("06-plateau-ramp.png")||name.equals("07-waterfall.png"))
            checkActorContinuity(image,name);
         System.out.println("BIOME SCENE image="+name+" fog="+renderer().getFogDensity()+" colors="+colors.size());
      }
      void checkActorContinuity(BufferedImage image,String name) {
         // Probe texels of the actual submitted trainer frame. The old fixed
         // palette crop also counted bright stair treads below his feet, while
         // dropping the dark face row; both the old and new JAR failed on an
         // identical complete silhouette. Terrain colors cannot define his bounds.
         try {
            JohtoRenderer r=renderer();
            PerspectiveCamera camera=(PerspectiveCamera)read(r,"camera");
            TextureRegion trainer=BwAssets.get().trainer(player,"down",0,false);
            WorldMeshBatch mesh=(WorldMeshBatch)read(r,"geometry");
            Object buffer=((Map<?,?>)read(mesh,"groups")).get(trainer.getTexture());
            require(buffer!=null,"Trainer texture absent from ramp geometry "+name);
            FloatArray vertices=(FloatArray)read(buffer,"vertices");
            require(vertices.size==48,"Expected one trainer quad in ramp fixture "+name);
            Pixmap atlas=texturePixels(trainer.getTexture());
            try {
               int[] result=trainerSamples(image,vertices,camera,atlas);
               require(result[0]>=150&&result[2]>=20,"Insufficient real trainer texels "+name);
               require(result[1]>=result[0]*.95f&&result[3]==0,
                  "Terrain obscured trainer texels: "+result[1]+"/"+result[0]+", missing rows="+result[3]+" in "+name);
               // Negative control: a band through the projected body must fail.
               BufferedImage covered=new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_RGB);
               covered.setData(image.getData());
               int a=projectedRow(vertices,camera,image,.36f),b=projectedRow(vertices,camera,image,.60f);
               for(int py=Math.min(a,b);py<=Math.max(a,b);py++)for(int px=0;px<image.getWidth();px++)
                  covered.setRGB(px,py,0xff00ff);
               int[] sliced=trainerSamples(covered,vertices,camera,atlas);
               require(sliced[3]>0&&sliced[1]<sliced[0]*.95f,"Trainer probe did not detect a simulated depth slice");
               System.out.println("BIOME SCENE actor continuity="+name+" matchedTexels="+result[1]+"/"+result[0]
                  +" opaqueRows="+result[2]+" missingRows="+result[3]+" negativeSliceRows="+sliced[3]);
            }finally{atlas.dispose();}
         }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
      }
      Pixmap texturePixels(Texture texture) {
         // A selected character may own a recolored uploaded texture. Read the
         // actual GL image, never assume an atlas path or consume a freed Pixmap.
         int width=texture.getWidth(),height=texture.getHeight();
         FrameBuffer target=new FrameBuffer(Pixmap.Format.RGBA8888,width,height,false);
         SpriteBatch batch=new SpriteBatch();Pixmap raw=null,result=null;
         try {
            target.begin();Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.disableBlending();batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,width,height));
            batch.begin();batch.draw(texture,0,0);batch.end();
            raw=ScreenUtils.getFrameBufferPixmap(0,0,width,height);
            result=new Pixmap(width,height,Pixmap.Format.RGBA8888);result.setBlending(Pixmap.Blending.None);
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)result.drawPixel(x,y,raw.getPixel(x,height-1-y));
            return result;
         }catch(RuntimeException error){if(result!=null)result.dispose();throw error;}
         finally{target.end();if(raw!=null)raw.dispose();batch.dispose();target.dispose();
            Gdx.gl.glViewport(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());}
      }
      Object read(Object object,String name)throws ReflectiveOperationException {
         Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
      }
      int projectedRow(FloatArray vertices,PerspectiveCamera camera,BufferedImage image,float fraction) {
         Vector3 point=new Vector3(vertices.get(0),
            vertices.get(1)+(vertices.get(33)-vertices.get(1))*fraction,
            vertices.get(2)+(vertices.get(34)-vertices.get(2))*fraction);
         camera.project(point,0,0,image.getWidth(),image.getHeight());
         return image.getHeight()-1-(int)point.y;
      }
      int[] trainerSamples(BufferedImage image,FloatArray vertices,PerspectiveCamera camera,Pixmap atlas) {
         float u0=vertices.get(4),u1=vertices.get(12),v0=vertices.get(37),v1=vertices.get(5);
         int left=(int)Math.floor(u0*atlas.getWidth()),right=(int)Math.ceil(u1*atlas.getWidth());
         int top=(int)Math.floor(v0*atlas.getHeight()),bottom=(int)Math.ceil(v1*atlas.getHeight());
         int expected=0,visible=0,rows=0,missingRows=0;
         boolean[] opaqueRows=new boolean[bottom-top],visibleRows=new boolean[bottom-top];
         int firstVisible=-1,lastVisible=-1;
         Vector3 point=new Vector3();
         for(int y=top;y<bottom;y++) {
            int rowExpected=0,rowVisible=0;
            for(int x=left;x<right;x++) {
               int pixel=atlas.getPixel(x,y);if((pixel&255)<240)continue;
               float fx=((x+.5f)/atlas.getWidth()-u0)/(u1-u0);
               float fy=1-((y+.5f)/atlas.getHeight()-v0)/(v1-v0);
               if(fx<0||fx>1||fy<0||fy>1)continue;
               point.set(vertices.get(0)+(vertices.get(8)-vertices.get(0))*fx+(vertices.get(32)-vertices.get(0))*fy,
                  vertices.get(1)+(vertices.get(9)-vertices.get(1))*fx+(vertices.get(33)-vertices.get(1))*fy,
                  vertices.get(2)+(vertices.get(10)-vertices.get(2))*fx+(vertices.get(34)-vertices.get(2))*fy);
               camera.project(point,0,0,image.getWidth(),image.getHeight());
               int px=(int)point.x,py=image.getHeight()-1-(int)point.y;
               boolean matched=false;
               for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                  int sx=px+dx,sy=py+dy;if(sx<0||sy<0||sx>=image.getWidth()||sy>=image.getHeight())continue;
                  int actual=image.getRGB(sx,sy);
                  // Mountain daylight has no tint/fog; allow only quantization.
                  if(Math.abs((actual>>16&255)-(pixel>>>24&255))<=3
                     &&Math.abs((actual>>8&255)-(pixel>>>16&255))<=3
                     &&Math.abs((actual&255)-(pixel>>>8&255))<=3)matched=true;
               }
               expected++;rowExpected++;if(matched){visible++;rowVisible++;}
            }
            if(rowExpected>0) {
               rows++;opaqueRows[y-top]=true;visibleRows[y-top]=rowVisible>=rowExpected*.5f;
               if(visibleRows[y-top]){if(firstVisible<0)firstVisible=y-top;lastVisible=y-top;}
            }
         }
         // The authored 3px feet anchor may bury the terminal sole pixels in
         // the ground (identical in both baseline/current builds). Detect cuts
         // inside the visible body, while the 95% total coverage still rejects
         // a missing head or substantial loss at either end of the frame.
         for(int row=firstVisible;row>=0&&row<=lastVisible;row++)if(opaqueRows[row]&&!visibleRows[row])missingRows++;
         return new int[]{expected,visible,rows,missingRows};
      }
   }
}
