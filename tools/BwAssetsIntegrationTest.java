package com.pkmngen.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.FloatArray;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Real GL loader/UV checks in an isolated working directory, not a full playthrough. */
public final class BwAssetsIntegrationTest extends ApplicationAdapter {
   private static Throwable failure;
   private static int checks, builtinRegions, animationFrames;
   private static final int[] COLORS={0xff0000ff,0x00ff00ff,0x0000ffff,0xffff00ff,
      0xff00ffff,0x00ffffff,0xff8000ff,0x8000ffff};
   private SpriteBatch batch;
   private FileHandle override;

   public static void main(String[] args) {
      Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
      config.setInitialVisible(false);config.setWindowedMode(512,256);config.disableAudio(true);
      new Lwjgl3Application(new BwAssetsIntegrationTest(),config);
      if(failure!=null){failure.printStackTrace();System.exit(1);}
      System.out.println("BW ASSETS PASS: "+checks+" checks; "+builtinRegions+" complete built-in regions; "
         +animationFrames+" animation frames; custom 64px and rectangular GPU sampling; normalized/pixel anchors; actual renderer trainer crops/feet/offset geometry; strict invalid overrides; disposal/recovery");
      System.exit(0);
   }
   @Override public void create() {
      try {
         override=Gdx.files.local("visual/custom/world-overrides.json");
         check(!override.exists(),"Test directory already contains an override");
         batch=new SpriteBatch();
         baseline();custom();invalid();
      } catch(Throwable error){failure=error;}
      finally {
         BwAssets.disposeShared();if(batch!=null)batch.dispose();
         if(override!=null && override.exists())override.delete();
         Gdx.app.exit();
      }
   }
   private void baseline() {
      BwAssets assets=BwAssets.get();
      JsonValue atlas=new JsonReader().parse(Gdx.files.internal("visual/stardew/world-atlas.json"));
      check(atlas.getInt("schemaVersion")==2,"Versioned active material contract");
      check(atlas.get("coordinateContract").getString("anchorUnit").equals("source-pixels"),"Explicit source-pixel anchors");
      int[] positions={-65,-16,-1,0,16,65};
      for(JsonValue e=atlas.get("regions").child;e!=null;e=e.next) {
         builtinRegions++;
         TextureRegion full=assets.named(e.name);int w=e.getInt("width"),h=e.getInt("height");
         check(full!=null && full.getRegionWidth()==w && full.getRegionHeight()==h,"Full crop "+e.name);
         near(e.getFloat("anchorX",w/2f),assets.anchorX(e.name),"Pixel anchor "+e.name);
         near(e.getFloat("anchorX",w/2f)/w,assets.layout(full).anchorX(.5f),"Normalized anchor "+e.name);
         int sw=e.getInt("sampleWidth",Math.min(16,w)),sh=e.getInt("sampleHeight",Math.min(16,h));
         int cols=Math.max(1,w/sw),rows=Math.max(1,h/sh);
         for(int x:positions)for(int y:positions) {
            TextureRegion cell=assets.cell(e.name,x,y);
            int col=Math.floorMod((int)Math.floor(x/16f),cols),row=Math.floorMod((int)Math.floor(-y/16f),rows);
            check(cell.getRegionX()==e.getInt("x")+col*sw && cell.getRegionY()==e.getInt("y")+row*sh
               && cell.getRegionWidth()==sw && cell.getRegionHeight()==sh,"Declared source sampling "+e.name);
         }
      }
      check(builtinRegions>=214 && builtinRegions==atlas.get("regions").size,"Complete active atlas including retained materials");
      int sequences=0;
      for(JsonValue e=atlas.get("animations").child;e!=null;e=e.next) {
         sequences++;String[] frames=e.get("frames").asStringArray();float fps=e.getFloat("fps");
         for(int i=0;i<frames.length;i++){animationFrames++;check(assets.namedAnimated(e.name,(i+.25f)/fps)==assets.named(frames[i]),"Animation "+e.name);}
      }
      check(sequences==6,"All six animations retained");
      TextureRegion trainer=assets.trainer("gold","down",0,false);
      check(trainer!=null && !assets.layout(trainer).customized,"Trainer default layout preserved");
      rendererGeometry(assets,false);
      clear();batch.begin();
      // Use universally present semantic aliases for the GPU baseline preview.
      String[] names={"grass_light","sand","basalt","graveyard_ground"};
      for(int i=0;i<names.length;i++) {
         TextureRegion region=assets.named(names[i]);check(region!=null,"Baseline material "+names[i]);
         batch.draw(region,16+i*120,32,96,96);
      }
      batch.end();capture("builtin-materials.png");
      int handle=trainer.getTexture().getTextureObjectHandle();BwAssets.disposeShared();
      check(!Gdx.gl.glIsTexture(handle),"Owned trainer texture disposed");
   }
   private void custom() {
      Pixmap image=new Pixmap(128,192,Pixmap.Format.RGBA8888);
      for(int i=0;i<COLORS.length;i++){image.setColor(COLORS[i]);image.fillRectangle((i%2)*64,(i/2)*48,64,48);}
      FileHandle file=Gdx.files.local("visual/custom/test-pattern.png");file.parent().mkdirs();PixmapIO.writePNG(file,image);image.dispose();
      override.writeString(valid(),false,"UTF-8");
      BwAssets assets=BwAssets.get();
      TextureRegion single=assets.cell("grass",0,0);
      check(single.getRegionWidth()==64 && single.getRegionHeight()==48,"Whole rectangular custom sample");
      check(assets.cell("grass",-2048,4096)==single,"Single sample repetition");
      TextureRegion sixtyFour=assets.cell("grass_light",0,0);
      check(sixtyFour.getRegionWidth()==64 && sixtyFour.getRegionHeight()==64,"64x64 custom sample");
      TextureRegion tree=assets.named("tree");BwAssets.SpriteLayout layout=assets.layout(tree);
      near(.25f,layout.anchorX(0),"Custom anchor X");near(.75f,layout.anchorY(0),"Custom anchor Y");
      near(24,assets.anchorX("tree"),"Custom anchor API pixels X");near(120,assets.anchorY("tree"),"Custom anchor API pixels Y");
      near(24,assets.worldWidth(tree,1),"Custom world width");near(40,assets.worldHeight(tree,1),"Custom world height");
      near(3,layout.offsetX,"Offset X");near(-2,layout.offsetY,"Offset Y");near(5,layout.elevation,"Elevation");
      TextureRegion aspect=assets.named("qa_aspect");
      near(100,assets.worldWidth(aspect,1),"Aspect width from explicit height");
      check(assets.namedAnimated("lava_bright",10)==assets.named("lava_bright"),"Animation alias static override");
      check(assets.namedAnimated("water",0)!=assets.namedAnimated("water",.25f),"Other animations retained");
      check(tree.getTexture()==single.getTexture(),"Custom texture cached once");
      rendererGeometry(assets,true);
      clear();batch.begin();batch.draw(single,16,16,64,64);batch.draw(sixtyFour,16,112,64,64);
      for(int i=0;i<8;i++)batch.draw(assets.cell("path",(i%2)*16,-(i/2)*16),128+(i%4)*64,16+(i/4)*64,64,64);
      batch.end();
      Pixmap pixels=ScreenUtils.getFrameBufferPixmap(0,0,512,256);
      check(pixels.getPixel(40,40)==COLORS[1],"GPU complete single sample");
      check(pixels.getPixel(40,160)==COLORS[0] && pixels.getPixel(40,120)==COLORS[2],"GPU 64x64 sample retains both source bands");
      for(int i=0;i<8;i++)check(pixels.getPixel(150+(i%4)*64,38+(i/4)*64)==COLORS[i],"GPU rectangular mosaic cell "+i);
      PixmapIO.writePNG(Gdx.files.local("custom-sampling.png"),pixels);pixels.dispose();
      BwAssets.disposeShared();
   }
   private String valid() {
      return "{\"texture\":\"visual/custom/test-pattern.png\",\"regions\":{"
         +"\"grass\":{\"x\":64,\"width\":64,\"height\":48,\"sampleWidth\":64,\"sampleHeight\":48},"
         +"\"grass_light\":{\"width\":64,\"height\":64,\"sampleWidth\":64,\"sampleHeight\":64},"
         +"\"path\":{\"width\":128,\"height\":192,\"sampleWidth\":64,\"sampleHeight\":48},"
         +"\"tree\":{\"width\":96,\"height\":160,\"sampleWidth\":96,\"sampleHeight\":160,\"anchorX\":0.25,\"anchorY\":0.75,\"worldWidth\":24,\"worldHeight\":40,\"offsetX\":3,\"offsetY\":-2,\"elevation\":5},"
         +"\"qa_aspect\":{\"width\":64,\"height\":32,\"sampleWidth\":64,\"sampleHeight\":32,\"worldHeight\":50},"
         +"\"trainer_male_walk_down_1\":{\"width\":64,\"height\":64,\"sampleWidth\":64,\"sampleHeight\":64},"
         +"\"lava_bright\":{\"width\":64,\"height\":64,\"sampleWidth\":64,\"sampleHeight\":64}}}";
   }
   private void invalid() {
      String region="{\"texture\":\"visual/custom/test-pattern.png\",\"width\":64,\"height\":64";
      reject(region+",\"sampleWidth\":48}","No silent custom truncation");
      reject(region+",\"anchorX\":44}","Pixel anchors forbidden in custom format");
      reject(region+",\"sampleWidth\":1.5}","Fractional samples rejected");
      reject(region+",\"x\":100}","Out-of-bounds crop rejected");
      reject("{\"texture\":\"../escape.png\",\"width\":16,\"height\":16}","Relative traversal rejected");
      rejectContract("{\"schemaVersion\":99,\"regions\":{}}","unsupported material schema");
      rejectContract("{\"schemaVersion\":2,\"coordinateContract\":{\"anchorUnit\":\"source-pixels\"},\"regions\":{}}","anchorUnit must be normalized");
      override.writeString(valid(),false,"UTF-8");
      check(BwAssets.get().named("tree").getRegionWidth()==96,"Recovery after invalid override");
   }
   private void rejectContract(String document,String message) {
      BwAssets.disposeShared();override.writeString(document,false,"UTF-8");
      try {BwAssets.get();throw new IllegalStateException("Invalid material contract accepted");}
      catch(IllegalArgumentException expected){check(expected.getMessage().contains(message),"Contextual contract error");}
      finally{BwAssets.disposeShared();}
   }
   private void reject(String region,String reason) {
      override.writeString("{\"regions\":{\"invalid_probe\":"+region+"}}",false,"UTF-8");
      try{BwAssets.get();throw new AssertionError(reason);}
      catch(IllegalArgumentException expected){check(expected.getMessage().contains("invalid_probe"),"Contextual error "+reason);}
      finally{BwAssets.disposeShared();}
   }
   private void rendererGeometry(BwAssets assets,boolean custom) {
      JohtoRenderer renderer=new JohtoRenderer();
      try {
         field(renderer,"assets").set(renderer,assets);
         field(renderer,"surfaceLift").setFloat(renderer,14f);
         Method trainer=JohtoRenderer.class.getDeclaredMethod("trainerImage",TextureRegion.class,int.class,
            float.class,float.class,float.class,float.class,float.class);trainer.setAccessible(true);
         Method clear=JohtoRenderer.class.getDeclaredMethod("clearGeometry");clear.setAccessible(true);
         TextureRegion frame=assets.trainer("gold","down",0,false);
         check(frame.getRegionHeight()==(custom?64:32),"Trainer source resolution");
         for(int rows:new int[]{32,25,26,14}) {
            clear.invoke(renderer);float scale=rows==25?.78f:.85f,feet=rows==32?2.55f:0f;
            trainer.invoke(renderer,frame,rows,100f,200f,1f,rows==32?3f:0f,scale);
            FloatArray vertices=vertices(renderer,"geometry",frame.getTexture());
            quadSize(vertices,32f*scale,rows*scale,"Trainer "+(custom?64:32)+" crop "+rows);
            float sin=(float)Math.sin(Math.toRadians(50)),cos=(float)Math.cos(Math.toRadians(50));
            near(100,vertices.get(0)+16*scale,"Trainer feet X");
            near(15,vertices.get(1)+feet*cos,"Trainer feet elevation");
            near(-200,vertices.get(2)-feet*sin,"Trainer feet Y");
            near(-200,field(renderer,"actorPlaneZ").getFloat(renderer),"Trainer depth plane");
            near(rows*(custom?2f:1f),Math.abs(vertices.get(5)-vertices.get(21))*frame.getTexture().getHeight(),"Proportional trainer UV crop");
            for(int i=0;i<vertices.size;i+=8) {
               near(4,vertices.get(i+6),"Trainer depth style");
               check(Float.isFinite(vertices.get(i+7)),"Trainer projected depth finite");
            }
         }
         if(custom) {
            clear.invoke(renderer);TextureRegion tree=assets.named("tree");
            Method upright=JohtoRenderer.class.getDeclaredMethod("upright",TextureRegion.class,float.class,
               float.class,float.class,float.class,float.class,float.class,float.class);upright.setAccessible(true);
            upright.invoke(renderer,tree,100f,200f,1f,30f,50f,Color.WHITE_FLOAT_BITS,4f);
            FloatArray vertices=vertices(renderer,"geometry",tree.getTexture());
            quadSize(vertices,24,40,"Custom prop dimensions");
            float sin=(float)Math.sin(Math.toRadians(50)),cos=(float)Math.cos(Math.toRadians(50));
            near(118,vertices.get(0)+6,"Custom prop anchor and offset X");
            near(20,vertices.get(1)+30*cos,"Custom prop anchor and elevation");
            near(-198,vertices.get(2)-30*sin,"Custom prop anchor and offset Y");
            near(-198,field(renderer,"actorPlaneZ").getFloat(renderer),"Custom prop feet depth plane");
            clear.invoke(renderer);
            trainer.invoke(renderer,tree,25,100f,200f,1f,0f,.85f);
            vertices=vertices(renderer,"geometry",tree.getTexture());
            quadSize(vertices,24,31.25f,"Custom trainer crop with explicit dimensions");
            near(103,vertices.get(0)+6,"Custom trainer anchor and offset X");
            near(20,vertices.get(1)+21.25f*cos,"Custom trainer cropped anchor height");
            near(-198,vertices.get(2)-21.25f*sin,"Custom trainer cropped anchor Y");
            Method shadow=JohtoRenderer.class.getDeclaredMethod("assetShadow",TextureRegion.class,float.class,float.class,float.class,float.class);
            shadow.setAccessible(true);shadow.invoke(renderer,tree,100f,200f,4f,2f);
            FloatArray shade=vertices(renderer,"shadows",null);
            near(103,shade.get(0),"Shadow follows offset X");near(-198,shade.get(2),"Shadow follows offset Y");
         }
      } catch(ReflectiveOperationException error){throw new IllegalStateException("Renderer integration test failed",error);}
      finally{renderer.dispose();}
   }
   private static Field field(Object owner,String name) throws ReflectiveOperationException {
      Field result=owner.getClass().getDeclaredField(name);result.setAccessible(true);return result;
   }
   private static FloatArray vertices(JohtoRenderer renderer,String group,Object texture) throws ReflectiveOperationException {
      Map<?,?> geometry=(Map<?,?>)field(renderer,group).get(renderer);Object mesh=geometry.get(texture);
      check(mesh!=null,"Renderer produced "+group+" mesh");
      return (FloatArray)field(mesh,"vertices").get(mesh);
   }
   private static void quadSize(FloatArray vertices,float width,float height,String reason) {
      check(vertices.size==48,reason+" six 8-float vertices");
      near(width,vertices.get(8)-vertices.get(0),reason+" world width");
      float dy=vertices.get(17)-vertices.get(1),dz=vertices.get(18)-vertices.get(2);
      near(height,(float)Math.sqrt(dy*dy+dz*dz),reason+" world height");
   }
   private void clear(){Gdx.gl.glClearColor(0,0,0,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);}
   private void capture(String name){Pixmap p=ScreenUtils.getFrameBufferPixmap(0,0,512,256);PixmapIO.writePNG(Gdx.files.local(name),p);p.dispose();}
   // Renderer trigonometry uses libGDX's lookup table; allow less than 1/500
   // world unit when comparing geometric invariants to Math.sin/cos.
   private static void near(float a,float b,String reason){check(Math.abs(a-b)<.002f,reason+": "+a+" != "+b);}
   private static void check(boolean ok,String reason){checks++;if(!ok)throw new IllegalStateException(reason);}
}
