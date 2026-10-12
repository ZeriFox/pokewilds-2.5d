package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.*;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.lang.reflect.*;
import java.util.*;

/** Fresh, unsaved native terrain fixture and real-shader sprite occlusion probes. */
public final class TerrainReadabilityTest {
   private static Throwable failure;
   private static int checks;
   private static void require(boolean pass,String why) {
      checks++;
      if(!pass)throw new IllegalStateException(why);
   }
   public static void main(String[] args) {
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      Sample sample=new Sample();
      Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
      cfg.setWindowedMode(800,640);cfg.setInitialVisible(false);cfg.setForegroundFPS(120);
      cfg.setIdleFPS(120);cfg.useVsync(false);cfg.setTitle("Terrain readability verification");
      new Lwjgl3Application(sample,cfg);
      if(failure!=null){failure.printStackTrace();System.exit(1);}
      require(sample.complete&&sample.disposed,"Incomplete native lifecycle");
      System.out.println("TERRAIN READABILITY PASS: checks="+checks+", screenshots="+sample.captures
         +", frames="+sample.frames+", flat/inclined stairs, material boundaries, PMD/tree overlap, B/W trainer");
      System.exit(0);
   }
   static Field field(Class<?> type,String name)throws Exception {
      Field f=type.getDeclaredField(name);f.setAccessible(true);return f;
   }
   static Method method(String name,Class<?>... types)throws Exception {
      Method m=JohtoRenderer.class.getDeclaredMethod(name,types);m.setAccessible(true);return m;
   }
   static final class Sample extends Game {
      int frames,captures;
      boolean ready,complete,disposed;
      long lastRendered,fingerprint;
      Tile flatRamp,slopeRamp,treeTile;
      AuthoredTerrain.Placement grove;
      final List<Pokemon> specimens=new ArrayList<>();
      Sample(){super(new String[0],4);}
      JohtoRenderer renderer()throws Exception{return (JohtoRenderer)field(Game.class,"johtoRenderer").get(this);}
      Tile put(String lower,String upper,int x,int y,String biome) {
         Tile t=new Tile(lower,upper,new Vector2(x,y),true);t.biome=biome;map.tiles.put(t.position,t);return t;
      }
      void fixture() {
         actionStack.removeIf(a->a instanceof DrawSetupMenu);
         Gdx.input.setInputProcessor(null);server=new Server();Network.register(server);
         map=new PkmnMap("terrain-readability-fixture");Game.rand.setSeed(812733);map.rand.setSeed(812733);
         for(int y=-224;y<=256;y+=16)for(int x=-256;x<=256;x+=16) {
            String biome=x<-80?"desert":x>112?"mountain":"forest";
            String lower=x<-144?"water1":x<-80?"sand1":x>112?"mountain3":"green1";
            put(lower,"",x,y,biome);
         }
         // A closed upper terrace has a real three-cell entrance, not wall-shaped props.
         for(int y=48;y<=144;y+=16)for(int x=-48;x<=64;x+=16) {
            String edges="";
            if(y==48)edges+="N";if(y==144)edges+="S";if(x==-48)edges+="E";if(x==64)edges+="W";
            put("green1",edges.isEmpty()?"":"ledges3_"+edges,x,y,"forest");
         }
         for(int x=-16;x<=16;x+=16) {
            Tile ramp=put("ledge_grass_ramp","",x,48,"forest");if(x==0)slopeRamp=ramp;
         }
         flatRamp=put("ledge_grass_ramp","",-32,-16,"forest");
         treeTile=put("green1","tree1",32,-16,"forest");
         for(int x=-64;x<=80;x+=48)for(int y=-112;y<=-48;y+=32)
            put("green1","tree1",x,y,"forest");
         put("green1","rock1",96,16,"forest");
         put("green1","flower1",-16,-16,"forest");
         map.bottomLeft=new Vector2(-256,-224);map.topRight=new Vector2(256,256);
         map.minimap=new Pixmap(80,80,Pixmap.Format.RGBA8888);
         map.minimap.setColor(.3f,.6f,.3f,1);map.minimap.fill();
         player.position.set(0,0);player.spawnLoc.set(0,0);player.name="Readability";cam.position.set(8,8,0);cam.zoom=1.5f;
         start();player.setCurrPokemon();player.acceptInput=false;
         actionStack.removeIf(a->a instanceof CycleDayNight);
         map.timeOfDay="day";mapBatch.setColor(Color.WHITE);
         specimen("machop",32,-32);specimen("raichu",0,-64);specimen("snorlax",48,-80);
         fingerprint=ModernWorldGenerator.fingerprint(map.tiles);ready=true;
      }
      void specimen(String name,int x,int y) {
         Pokemon p=new Pokemon(name,8);p.position.set(x,y);p.mapTiles=map.tiles;p.dirFacing="down";p.aggroPlayer=false;
         specimens.add(p);insertAction(p.new Standing());
      }
      void freeze() {
         for(Pokemon p:specimens) {
            if(p.standingAction instanceof Pokemon.Standing)((Pokemon.Standing)p.standingAction).moveTimer=10000;
            p.aggroPlayer=false;
         }
      }
      @Override public void render() {
         if(failure!=null)return;
         try {
            super.render();frames++;
            require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"OpenGL error at "+frames);
            if(frames==20)fixture();
            if(!ready)return;
            freeze();
            if(frames>26) {
               long rendered=renderer().getRenderedFrames();require(rendered>lastRendered,"Modern scene missing frame "+frames);lastRendered=rendered;
            }
            if(frames==50) {
               verifyTerrain();capture("01-stairs-and-material-boundaries.png");
               player.position.set(0,-80);cam.position.set(8,-72,0);cam.zoom=1;map.refreshCache=true;
            }
            if(frames==72)capture("02-pmd-dense-woodland.png");
            if(frames==73)occlusionProbe(true);
            if(frames==74)occlusionProbe(false);
            if(frames==76) {
               require(fingerprint==ModernWorldGenerator.fingerprint(map.tiles),"Presentation changed saved tile structure");
               require(((WorldBatch)mapBatch).getSubmittedDraws()==0,"Legacy world pixels submitted");
               require(PmdPokemonSprites.get().getLoadedTextureCount()>=3,"Three live PMD species were not rendered");
               require(renderer().getLoadedSpeciesCount()==0,"Unexpected 3D models");
               authoredFixture();
            }
            if(frames==94) {
               trainerVisible("terrace-final-frame");
               capture("05-authored-terrace.png");
               center(grove);map.refreshCache=true;
            }
            if(frames==110) {
               trainerVisible("woodland-final-frame");
               capture("06-authored-woodland.png");
               reverseTextureOrder();
               require(fingerprint==ModernWorldGenerator.fingerprint(map.tiles),"Renderer changed the authored terrain");
               complete=true;Gdx.app.exit();
            }
            require(frames<140,"Readability fixture timed out");
         } catch(Throwable t){failure=t;Gdx.app.exit();}
      }
      void authoredFixture() {
         actionStack.removeIf(a->a.getClass().getName().startsWith("com.pkmngen.game.Pokemon$"));
         specimens.clear();map.pokemon.clear();map.onscreenPokemon.clear();map.onscreenBurrowed.clear();
         map.overworldTiles=new HashMap<>();map.tiles=map.overworldTiles;
         Route route=new Route("forest1",20);
         for(int y=-512;y<=512;y+=16)for(int x=-512;x<=512;x+=16) {
            Tile t=put("green1","",x,y,"forest");t.routeBelongsTo=route;
         }
         AuthoredTerrain.Report report=AuthoredTerrain.apply(map.tiles,811L);
         require(report.modules>=4&&report.terraces>0,"Authored visual fixture lacks placed terrain modules: "+report);
         AuthoredTerrain.Placement terrace=null;
         for(AuthoredTerrain.Placement placement:report.placements) {
            if(placement.module.equals("double-stair-terrace")&&terrace==null)terrace=placement;
            if(placement.module.equals("woodland-bend")&&grove==null)grove=placement;
         }
         require(terrace!=null&&grove!=null,"Missing terrace/grove preview modules");
         map.bottomLeft.set(-512,-512);map.topRight.set(512,512);center(terrace);
         map.refreshCache=true;fingerprint=ModernWorldGenerator.fingerprint(map.tiles);
         System.out.println("READABILITY actual authored preview: "+report);
      }
      void center(AuthoredTerrain.Placement placement) {
         float x=(placement.x+placement.size/2)*16,y=(placement.y+placement.size/2)*16;
         player.position.set(x,y);cam.position.set(x+8,y+8,0);cam.zoom=placement.size>9?2.4f:1.6f;
      }
      void trainerVisible(String stage)throws Exception {
         JohtoRenderer r=renderer();PerspectiveCamera camera=(PerspectiveCamera)field(JohtoRenderer.class,"camera").get(r);
         TextureRegion trainer=BwAssets.get().trainer(player,"down",0,false);
         WorldMeshBatch batch=(WorldMeshBatch)field(JohtoRenderer.class,"geometry").get(r);
         Object buffer=((Map<?,?>)field(WorldMeshBatch.class,"groups").get(batch)).get(trainer.getTexture());
         require(buffer!=null,"Trainer has no geometry: "+stage);
         FloatArray vertices=(FloatArray)field(buffer.getClass(),"vertices").get(buffer);
         require(vertices.size==48,"Expected exactly one trainer quad: "+stage);
         Pixmap atlas=texturePixels(trainer.getTexture());
         Pixmap screen=ScreenUtils.getFrameBufferPixmap(0,0,800,640);
         int expected=0,visible=0;
         try {
            float u0=vertices.get(4),u1=vertices.get(12),v0=vertices.get(37),v1=vertices.get(5);
            int x0=Math.round(u0*atlas.getWidth()),x1=Math.round(u1*atlas.getWidth());
            int y0=Math.round(v0*atlas.getHeight()),y1=Math.round(v1*atlas.getHeight());
            for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++) {
               int pixel=atlas.getPixel(x,y);
               // Sample the real frame's opaque outline/clothing, allowing tint/fog.
               // The authored clearing below is bright dirt with no foreground prop.
               if((pixel&255)<240||((pixel>>>24)&255)>90||((pixel>>>16)&255)>90||((pixel>>>8)&255)>90)continue;
               float fx=(x+.5f-x0)/(x1-x0),fy=1-(y+.5f-y0)/(y1-y0);
               Vector3 point=new Vector3(vertices.get(0)+(vertices.get(8)-vertices.get(0))*fx+(vertices.get(32)-vertices.get(0))*fy,
                  vertices.get(1)+(vertices.get(9)-vertices.get(1))*fx+(vertices.get(33)-vertices.get(1))*fy,
                  vertices.get(2)+(vertices.get(10)-vertices.get(2))*fx+(vertices.get(34)-vertices.get(2))*fy);
               camera.project(point,0,0,800,640);expected++;
               boolean found=false;
               for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                  int actual=screen.getPixel(Math.round(point.x)+dx,Math.round(point.y)+dy);
                  if(((actual>>>24)&255)<145&&((actual>>>16)&255)<145&&((actual>>>8)&255)<145)found=true;
               }
               if(found)visible++;
            }
         }finally{screen.dispose();atlas.dispose();}
         require(expected>=40,"Insufficient opaque trainer samples: "+stage);
         if(visible<expected*.7f)capture("failure-trainer-"+stage+".png");
         require(visible>=expected*.7f,"Trainer disappeared after composition: "+stage+", visible="+visible+"/"+expected);
         System.out.println("READABILITY visible trainer "+stage+": "+visible+"/"+expected+" projected opaque samples");
      }
      Pixmap texturePixels(Texture texture) {
         // Trainer identities and costume variants own separate uploaded sheets.
         // UV coordinates refer to that exact texture, not the old common atlas.
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
      @SuppressWarnings("unchecked")
      void reverseTextureOrder()throws Exception {
         JohtoRenderer r=renderer();WorldMeshBatch batch=(WorldMeshBatch)field(JohtoRenderer.class,"geometry").get(r);
         Field groupsField=field(WorldMeshBatch.class,"groups");
         Map<Texture,WorldMeshBatch.Buffer> original=(Map<Texture,WorldMeshBatch.Buffer>)groupsField.get(batch);
         List<Texture> textures=new ArrayList<>(original.keySet());java.util.Collections.reverse(textures);
         Map<Texture,WorldMeshBatch.Buffer> reversed=new LinkedHashMap<>();
         for(Texture texture:textures)reversed.put(texture,original.get(texture));
         try {
            groupsField.set(batch,reversed);method("draw",Game.class).invoke(r,this);
            trainerVisible("woodland-reversed-texture-order");
         }finally{groupsField.set(batch,original);}
      }
      void verifyTerrain()throws Exception {
         JohtoRenderer r=renderer();WorldElevation e=r.getElevation();
         require(!e.isSloped(flatRamp),"Flat stairs fixture accidentally gained elevation");
         require(e.isSloped(slopeRamp),"Terrace entrance is flat");
         require(e.height(8,100)-e.height(8,24)>6,"Terrace lacks an upper walking surface");
         TextureRegion steps=BwAssets.get().named(BiomeProfiles.visualForTile(flatRamp).cliff("ramp"));
         int texturedVertices=0;
         WorldMeshBatch batch=(WorldMeshBatch)field(JohtoRenderer.class,"geometry").get(r);
         Map<?,?> groups=(Map<?,?>)field(WorldMeshBatch.class,"groups").get(batch);
         Object buffer=groups.get(steps.getTexture());
         require(buffer!=null,"No stair texture was submitted");
         FloatArray vertices=(FloatArray)field(buffer.getClass(),"vertices").get(buffer);
         for(int i=0;i+7<vertices.size;i+=8) {
            float x=vertices.get(i),y=-vertices.get(i+2),u=vertices.get(i+4),v=vertices.get(i+5);
            if(x>=flatRamp.position.x-.01f&&x<=flatRamp.position.x+16.01f
               &&y>=flatRamp.position.y-.01f&&y<=flatRamp.position.y+16.01f
               &&u>=steps.getU()-.00001f&&u<=steps.getU2()+.00001f
               &&v>=steps.getV()-.00001f&&v<=steps.getV2()+.00001f)texturedVertices++;
         }
         require(texturedVertices>=6,"Flat traversable stairs disappeared into plain ground: "+texturedVertices+" steps vertices");
         require(!modernUi.desktop.preferences.classicCast,"Fresh default unexpectedly selects upscaled classic trainer");
         TextureRegion trainer=BwAssets.get().trainer(player,"down",0,false);
         require(trainer==BwAssets.get().detailedTrainer(player.character,"down",0,false),"Default avatar is not detailed B/W sprite");
         System.out.println("READABILITY terrain: flatStepsVertices="+texturedVertices+", terraceRise="+(e.height(8,100)-e.height(8,24)));
      }
      @SuppressWarnings("unchecked")
      void occlusionProbe(boolean actorInFront)throws Exception {
         JohtoRenderer r=renderer();BwAssets assets=BwAssets.get();
         Map<String,TextureRegion> regions=(Map<String,TextureRegion>)field(BwAssets.class,"regions").get(assets);
         TextureRegion original=regions.get("tree");
         Texture green=solid(24,48,Color.GREEN),magenta=solid(24,32,Color.MAGENTA);
         try {
            for(String name:new String[]{"geometry","translucent","shadows","emotes"})
               ((WorldMeshBatch)field(JohtoRenderer.class,name).get(r)).clear();
            field(JohtoRenderer.class,"surfaceLift").setFloat(r,0);
            // Use the real tree path, then the exact billboard path used by PMD sprites.
            regions.put("tree",new TextureRegion(green));
            float gx=player.position.x+8,gy=player.position.y+8;
            method("tree",Tile.class,float.class,float.class,String.class).invoke(r,treeTile,gx,gy,"green1 tree1");
            float actorY=gy+(actorInFront?-6:6);
            method("anchored",TextureRegion.class,float.class,float.class,float.class,float.class,float.class,float.class)
               .invoke(r,new TextureRegion(magenta),gx,actorY,.15f,12f,0f,1f);
            WorldMeshBatch geometry=(WorldMeshBatch)field(JohtoRenderer.class,"geometry").get(r);
            Map<?,?> groups=(Map<?,?>)field(WorldMeshBatch.class,"groups").get(geometry);
            Object treeBuffer=groups.get(green);
            FloatArray treeVertices=(FloatArray)field(treeBuffer.getClass(),"vertices").get(treeBuffer);
            for(int i=0;i<treeVertices.size;i+=8)require(treeVertices.get(i+6)>3.5f,"Tree and PMD use incompatible depth planes");
            geometry.upload();
            for(String name:new String[]{"translucent","shadows","emotes"})
               ((WorldMeshBatch)field(JohtoRenderer.class,name).get(r)).upload();
            method("draw",Game.class).invoke(r,this);
            PerspectiveCamera camera=(PerspectiveCamera)field(JohtoRenderer.class,"camera").get(r);
            Pixmap pixels=ScreenUtils.getFrameBufferPixmap(0,0,800,640);
            int sampled=0;
            try {
               for(float height:new float[]{12,18,24})for(float dx:new float[]{-5,0,5}) {
                  Vector3 point=new Vector3(gx+dx,.15f+height*MathUtils.cosDeg(50),-actorY-height*MathUtils.sinDeg(50));
                  camera.project(point,0,0,800,640);
                  int pixel=pixels.getPixel(Math.round(point.x),Math.round(point.y));
                  int red=(pixel>>>24)&255,greenValue=(pixel>>>16)&255,blue=(pixel>>>8)&255;
                  require(actorInFront?red>greenValue+50&&blue>greenValue+50:greenValue>red+50&&greenValue>blue+50,
                     "Sprite sliced at overlapping height "+height+", front="+actorInFront+", rgb="+Integer.toHexString(pixel));
                  sampled++;
               }
            } finally {pixels.dispose();}
            capture(actorInFront?"03-occlusion-pokemon-front.png":"04-occlusion-tree-front.png");
            System.out.println("READABILITY GPU occlusion front="+actorInFront+", samples="+sampled);
         } finally {regions.put("tree",original);green.dispose();magenta.dispose();}
      }
      Texture solid(int width,int height,Color color) {
         Pixmap p=new Pixmap(width,height,Pixmap.Format.RGBA8888);p.setColor(color);p.fill();
         Texture t=new Texture(p);p.dispose();return t;
      }
      void capture(String filename) {
         Pixmap p=ScreenUtils.getFrameBufferPixmap(0,0,800,640);
         PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);
         try {png.write(Gdx.files.local(filename),p);captures++;}
         catch(java.io.IOException ex){throw new IllegalStateException(ex);}
         finally{png.dispose();p.dispose();}
      }
      @Override public void dispose(){try{super.dispose();}finally{disposed=true;}}
   }
}
