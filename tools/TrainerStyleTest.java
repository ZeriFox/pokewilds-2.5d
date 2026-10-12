package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.lang.reflect.Field;
import java.util.*;

/** Native packaged-art proof: distinct identities, real selector and projected actor texels. */
public final class TrainerStyleTest {
   private static final int WIDTH=960,HEIGHT=760;
   private static final String MOD="verification_bw_mod";
   private static final String INCOMPLETE_MOD="verification_missing_back";
   private static final String[] CAST={"leaf","gold","kris","brendan","may","hilbert","hilda","rosa","calem",
      "serena","chase","elaine","gloria","mark","mint","lunick","summer","kellyn","lyra"};
   private static final String[] DIRS={"down","up","left","right"};
   private static final float[] TIMES={0,.126f,.251f};
   private static int checks;
   private static Throwable failure;
   private static void require(boolean condition,String message){checks++;if(!condition)throw new IllegalStateException(message);}
   private static Object read(Object object,String name)throws Exception {
      Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
   }
   private static Field field(Class<?> type,String name)throws Exception {
      Field f=type.getDeclaredField(name);f.setAccessible(true);return f;
   }
   public static void main(String[] args) {
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      Probe game=new Probe();Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
      cfg.setWindowedMode(WIDTH,HEIGHT);cfg.setInitialVisible(false);cfg.useVsync(false);cfg.setForegroundFPS(60);
      cfg.setTitle("Trainer style verification");
      try {
         new Lwjgl3Application(game,cfg);
         if(failure!=null)throw new IllegalStateException("Native trainer verification failed",failure);
         require(game.complete&&game.disposed,"Incomplete native lifecycle");
         System.out.println("TRAINER STYLE PASS: checks="+checks+", identities="+CAST.length+", walkFrames="+game.walkFrames
            +", worldPoses="+game.worldPoses+", captures="+game.captures
            +", unique cast and selector, legacy preference, walk/run clocks, rooted complete sprites, mod art and costume, cache/dispose");
         System.exit(0);
      }catch(Throwable error){error.printStackTrace();System.exit(1);}
   }
   private static final class Probe extends Game {
      int frame,captures,walkFrames,worldPoses;boolean complete,disposed;
      final Map<Texture,Pixmap> pixelCache=new IdentityHashMap<>();
      SpriteBatch contactBatch;BitmapFont contactFont;
      Probe(){super(new String[0],4);}
      @Override public void create() {
         try {
            // Both fixture mod and old preference live only in the runner's isolated directory.
            makeMod(MOD,true);
            makeMod(INCOMPLETE_MOD,true);
            require(Gdx.files.local("mods/player/"+INCOMPLETE_MOD+"/back.png").delete(),"Missing-back negative fixture was not prepared");
            DesktopInterfaceSettings old=new DesktopInterfaceSettings();old.bindings=DesktopInterfaceSettings.defaults();
            old.classicCast=true;old.openingScene=false;old.save();
            super.create();
         }catch(Throwable error){failure=error;Gdx.app.exit();}
      }
      @Override public void render() {
         if(failure!=null)return;
         try {
            super.render();frame++;
            if(frame==10){selectorContract();modernUi.desktop.openTrainers();modernUi.desktop.trainerPage=0;}
            if(frame>=11&&frame<=12) {
               capture("0"+(frame-5)+"-selector-page-"+(frame-10));
               modernUi.desktop.trainerPage=1;
            }
            if(frame==15) {
               assetContract();costumeContract();contacts();initializeWorld();worldContract();modsAndLifecycle();
               complete=true;Gdx.app.exit();
            }
         }catch(Throwable error){failure=error;Gdx.app.exit();}
      }
      void selectorContract()throws Exception {
         DesktopMenuController c=modernUi.desktop;
         require(c.preferences.classicCast,"The legacy saved preference fixture was not loaded");
         require(c.setup!=null,"Startup selector was never attached");
         require(c.trainerCount()==20,"Expected 19 built-ins plus exactly one installed mod, got "+c.trainerCount());
         Set<String> seen=new HashSet<>();
         for(int i=0;i<c.trainerCount();i++)require(seen.add(c.trainerId(i)),"Duplicate trainer card: "+c.trainerId(i));
         require(seen.containsAll(Arrays.asList(CAST))&&seen.contains(MOD),"Selector lost a built-in identity or installed mod");
         require(!seen.contains(INCOMPLETE_MOD)&&!TrainerCatalog.complete(INCOMPLETE_MOD),"Mod missing the legacy battle back pose is selectable");
         c.trainerIndex=c.trainerChoices.indexOf(MOD);c.trainerColor=c.setup.colors.indexOf(Color.MAGENTA);
         require(c.trainerIndex>=0&&c.trainerColor>=0,"Valid mod or costume color fixture missing");
         java.lang.reflect.Method apply=DesktopMenuController.class.getDeclaredMethod("applyTrainer",Game.class);apply.setAccessible(true);apply.invoke(c,this);
         require(player.character.equals(MOD)&&player.battleSprite!=null&&c.screen==DesktopMenuController.Screen.SETUP,
            "Applying a complete mod did not finish the real legacy player pose loading");
         require(player.color.equals(Color.MAGENTA),"Applied mod lost selected costume color");
         for(String id:CAST) {
            player.character=id;c.setup.playerTypeIndex=c.setup.playerTypes.indexOf(id);
            require(c.setup.playerTypeIndex>=0,"Legacy setup lost "+id);
            c.openTrainers();require(id.equals(c.trainerId(c.trainerIndex)),"Reopening selector changes saved identity "+id);
         }
         player.character="gold";c.setup.playerTypeIndex=c.setup.playerTypes.indexOf("gold");
         c.setup.avatarColorIndex=0;c.trainerColor=0;player.setColor(c.setup.colors.get(0));
         System.out.println("TRAINER selector: 19 unique built-ins plus applied complete mod, missing-back mod excluded, legacy preference true");
      }
      Pixmap pixels(Texture texture) {
         Pixmap p=pixelCache.get(texture);if(p!=null)return p;
         // Read owned GL pixels: generated costume textures may no longer retain
         // their constructor Pixmap, so consuming TextureData would use freed memory.
         FrameBuffer target=new FrameBuffer(Pixmap.Format.RGBA8888,texture.getWidth(),texture.getHeight(),false);
         SpriteBatch batch=new SpriteBatch();Pixmap raw=null;
         try {
            target.begin();Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.disableBlending();batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,texture.getWidth(),texture.getHeight()));
            batch.begin();batch.draw(texture,0,0);batch.end();
            raw=ScreenUtils.getFrameBufferPixmap(0,0,texture.getWidth(),texture.getHeight());
            p=new Pixmap(texture.getWidth(),texture.getHeight(),Pixmap.Format.RGBA8888);p.setBlending(Pixmap.Blending.None);
            for(int y=0;y<p.getHeight();y++)for(int x=0;x<p.getWidth();x++)p.drawPixel(x,y,raw.getPixel(x,p.getHeight()-1-y));
         }finally{target.end();if(raw!=null)raw.dispose();batch.dispose();target.dispose();Gdx.gl.glViewport(0,0,WIDTH,HEIGHT);}
         pixelCache.put(texture,p);return p;
      }
      int[] image(TextureRegion region) {
         require(region!=null,"Missing trainer image");Pixmap p=pixels(region.getTexture());
         int[] result=new int[region.getRegionWidth()*region.getRegionHeight()];
         for(int y=0;y<region.getRegionHeight();y++)for(int x=0;x<region.getRegionWidth();x++) {
            int color=p.getPixel(region.getRegionX()+x,region.getRegionY()+y);
            result[y*region.getRegionWidth()+x]=(color&255)==0?0:color;
         }
         return result;
      }
      int[] bounds(int[] data) {
         int minX=32,minY=32,maxX=-1,maxY=-1,opaque=0;Set<Integer> colors=new HashSet<>();
         for(int y=0;y<32;y++)for(int x=0;x<32;x++)if((data[y*32+x]&255)>0) {
            minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);opaque++;colors.add(data[y*32+x]);
         }
         return new int[]{minX,minY,maxX,maxY,opaque,colors.size()};
      }
      void assetContract() {
         BwAssets art=BwAssets.get();Set<Integer> identities=new HashSet<>();
         for(String id:CAST) {
            player.character=id;player.currState="";player.color=new Color(0xf83808ff);
            int[] idle=image(art.trainer(id,"down",0,false));
            require(identities.add(Arrays.hashCode(idle)),"Two cast identities share the same visible front sprite: "+id);
            Set<TextureRegion> regions=Collections.newSetFromMap(new IdentityHashMap<>());
            for(String dir:DIRS) {
               int idleFoot=-1;Set<Integer> poses=new HashSet<>();
               for(float clock:TIMES) {
                  TextureRegion r=art.trainer(id,dir,clock,true);require(r.getRegionWidth()==32&&r.getRegionHeight()==32,"Unexpected frame size "+id);
                  require(r.getTexture().getMinFilter()==Texture.TextureFilter.Nearest,"Pixel art is blurred "+id);
                  require(r==art.trainer(id,dir,clock,true),"Frame allocation is repeated every request "+id);
                  regions.add(r);int[] pix=image(r),b=bounds(pix);
                  require(b[0]>=1&&b[2]<=30&&b[1]>=0&&b[3]<=30,"Sprite clipped at a sheet boundary "+id+"/"+dir+Arrays.toString(b));
                  require(b[4]>=140&&b[5]>=8&&b[3]-b[1]>=22,"Insufficient complete detailed silhouette "+id+"/"+dir+Arrays.toString(b));
                  require(b[3]>=28,"Foot anchor is too high "+id+"/"+dir);
                  if(idleFoot<0)idleFoot=b[3];else require(Math.abs(b[3]-idleFoot)<=1,"Feet bounce between walking frames "+id+"/"+dir);
                  require(poses.add(Arrays.hashCode(pix)),"Walking frame repeated instead of both steps "+id+"/"+dir);walkFrames++;
                  modernUi.desktop.preferences.classicCast=false;int[] modern=image(art.trainer(id,dir,clock,true));
                  modernUi.desktop.preferences.classicCast=true;require(Arrays.equals(pix,modern),"Old preference changes built-in style "+id);
               }
               TextureRegion stationary=art.trainer(id,dir,0,false);
               require(stationary==art.trainer(id,dir,200,false),"Stationary character keeps stepping "+id+"/"+dir);
               int expected=(id.equals("hilbert")||id.equals("hilda"))?0:1;
               if(expected==0)require(Arrays.equals(image(stationary),image(art.named("trainer_"+(id.equals("hilda")?"female":"male")+"_walk_"+dir+"_0"))),"Genuine BW idle uses stepping frame "+id+"/"+dir);
               else {
                  require(Arrays.equals(image(stationary),image(art.trainer(id,dir,.126f,true))),"Redrawn idle does not use neutral pose "+id+"/"+dir);
                  require(!Arrays.equals(image(stationary),image(art.classicTrainer(id,dir,0,false,null))),"Built-in still uses enlarged classic sprite "+id);
               }
            }
            require(regions.size()==12,"Directional walking frames are not separately cached "+id);
            // Read the real running state used by the world API; no alternate renderer here.
            PlayerRunning running=new PlayerRunning(this,player,false);actionStack.add(running);
            try {
               TextureRegion walk=art.trainer(id,"down",.09f,true),run=art.trainer(player,"down",.09f,true);
               require(!Arrays.equals(image(walk),image(run)),"Running still uses walk cadence "+id);
               require(Arrays.equals(image(art.trainer(player,"down",0,false)),image(art.trainer(id,"down",0,false))),"Run state changes stationary pose "+id);
            }finally{actionStack.remove(running);player.currState="";}
         }
         require(walkFrames==228,"Incomplete walking coverage");
         System.out.println("TRAINER assets: 19 distinct identities, 228 directional walking frames, real running cadence, modern art with old preferences");
      }
      void costumeContract() {
         BwAssets art=BwAssets.get();
         for(String id:CAST) {
            if(id.equals("hilbert")||id.equals("hilda"))continue;
            Pixmap mask=new Pixmap(Gdx.files.internal("visual/trainers-bw/"+id+"-mask.png"));
            try {
               require(mask.getWidth()==96&&mask.getHeight()==128,"Costume mask dimensions do not match complete sheet "+id);
               player.character=id;player.color=new Color(0xf83808ff);
               int totalChanged=0;
               for(int d=0;d<4;d++)for(int f=0;f<3;f++) {
                  String dir=DIRS[d];float time=TIMES[f];
                  int[] original=image(art.trainer(id,dir,time,true));
                  require(Arrays.equals(original,image(art.trainer(player,dir,time,true))),"Default costume modifies authored colors "+id);
                  player.color=Color.CYAN.cpy();TextureRegion cyan=art.trainer(player,dir,time,true);int[] changed=image(cyan);
                  require(cyan==art.trainer(player,dir,time,true),"Costume cache misses identical frame "+id);
                  for(int y=0;y<32;y++)for(int x=0;x<32;x++) {
                     int at=y*32+x;boolean selected=(mask.getPixel(f*32+x,d*32+y)&255)>0;
                     require((original[at]&255)==(changed[at]&255),"Costume changes silhouette alpha "+id);
                     if(!selected)require(original[at]==changed[at],"Costume tints protected skin/hair/accessory pixels "+id);
                     if(original[at]!=changed[at])totalChanged++;
                  }
                  player.color=new Color(0xf83808ff);
               }
               require(totalChanged>=12,"Costume selector has no useful visible effect "+id+": "+totalChanged);
            }finally{mask.dispose();}
         }
         System.out.println("TRAINER costumes: all 17 redrawn identities, all directions/steps; mask-external pixels and alpha unchanged");
      }
      void contacts() {
         contactBatch=new SpriteBatch();contactFont=new BitmapFont();contactBatch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,WIDTH,HEIGHT));
         for(int d=0;d<4;d++)contact(DIRS[d],false,"0"+(d+1)+"-cast-walk-"+DIRS[d]);
         contact("down",true,"05-cast-running");
      }
      void contact(String direction,boolean run,String name) {
         Gdx.gl.glViewport(0,0,WIDTH,HEIGHT);Gdx.gl.glClearColor(.075f,.105f,.14f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT);
         contactBatch.begin();contactFont.setColor(.78f,.9f,.94f,1);
         contactFont.draw(contactBatch,"PLAYABLE CAST  /  "+direction.toUpperCase(Locale.ROOT)+"  /  "+(run?"RUN":"WALK"),20,HEIGHT-20);
         for(int i=0;i<CAST.length;i++) {
            String id=CAST[i];float x=18+(i%4)*237,y=HEIGHT-152-(i/4)*142;
            contactFont.draw(contactBatch,Game.capitalize(id),x+8,y+82);
            player.character=id;PlayerRunning running=run?new PlayerRunning(this,player,false):null;
            if(run)actionStack.add(running);
            try {for(int f=0;f<3;f++) {
               float clock=run?(f/12f+.001f):TIMES[f];
               TextureRegion r=run?BwAssets.get().trainer(player,direction,clock,true):BwAssets.get().trainer(id,direction,clock,true);
               contactBatch.draw(r,x+f*70,y,64,64);
            }}finally{if(run)actionStack.remove(running);player.currState="";}
         }
         contactBatch.end();capture(name);
      }
      void initializeWorld() {
         Gdx.input.setInputProcessor(null);actionStack.removeIf(a->a instanceof DrawSetupMenu);
         server=new Server();Network.register(server);map=new PkmnMap("trainer-style-fixture");
         map.bottomLeft=new Vector2(-256,-256);map.topRight=new Vector2(256,256);map.minimap=new Pixmap(64,64,Pixmap.Format.RGBA8888);
         for(int y=-256;y<=256;y+=16)for(int x=-256;x<=256;x+=16) {
            Tile tile=new Tile("mountain3",new Vector2(x,y),true);tile.biome="mountain";map.tiles.put(tile.position,tile);
         }
         map.overworldTiles=map.tiles;map.timeOfDay="day";map.refreshCache=true;
         player.name="Trainer proof";player.character="gold";player.position.set(0,0);player.spawnLoc.set(0,0);player.dirFacing="down";
         cam.position.set(8,8,0);cam.zoom=1.1f;start();player.setCurrPokemon();actionStack.removeIf(a->a instanceof CycleDayNight);
         actionStackCopy.clear();player.acceptInput=false;mapBatch.setColor(Color.WHITE);
         System.setProperty("pokewilds.ambientOverlays","off");modernUi.desktop.preferences.reducedMotion=true;
      }
      JohtoRenderer renderer()throws Exception{return(JohtoRenderer)field(Game.class,"johtoRenderer").get(this);}
      byte[] drawWorld()throws Exception {
         JohtoRenderer r=renderer();field(JohtoRenderer.class,"seconds").setFloat(r,0);
         ((Map<?,?>)read(r,"motion")).clear();Gdx.gl.glViewport(0,0,WIDTH,HEIGHT);
         require(r.render(this),"World renderer declined fixture");require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"OpenGL error in trainer world");
         return ScreenUtils.getFrameBufferPixels(0,0,WIDTH,HEIGHT,false);
      }
      void worldContract()throws Exception {
         long terrain=ModernWorldGenerator.fingerprint(map.tiles);Vector2 position=new Vector2(player.position);
         Set<Integer> fronts=new HashSet<>();
         for(String id:CAST)for(String dir:DIRS) {
            player.character=id;player.dirFacing=dir;Game.rand.setSeed(721);map.rand.setSeed(722);
            byte[] frame=drawWorld();TextureRegion r=BwAssets.get().trainer(player,0,false);
            WorldMeshBatch mesh=(WorldMeshBatch)read(renderer(),"geometry");Object buffer=((Map<?,?>)read(mesh,"groups")).get(r.getTexture());
            require(buffer!=null,"Selected trainer texture never reaches world geometry "+id+"/"+dir);
            FloatArray vertices=(FloatArray)read(buffer,"vertices");require(vertices.size==48,"Unexpected actor geometry for "+id);
            int[] result=projectedTexels(frame,vertices,(PerspectiveCamera)read(renderer(),"camera"),pixels(r.getTexture()));
            require(result[0]>=140&&result[2]>=22,"World probe has too few actual trainer texels "+id+"/"+dir);
            require(result[1]>=result[0]*.95f&&result[3]==0,"World terrain clips trainer "+id+"/"+dir+" visible="+result[1]+"/"+result[0]+" missingRows="+result[3]);
            require(terrain==ModernWorldGenerator.fingerprint(map.tiles)&&position.equals(player.position),"Changing sprite changed world state "+id);
            require(Game.rand.nextLong()==new Random(721).nextLong()&&map.rand.nextLong()==new Random(722).nextLong(),"Trainer rendering consumed gameplay RNG");
            if(dir.equals("down"))require(fronts.add(Arrays.hashCode(frame)),"Different cast members look identical in the actual world "+id);
            if(dir.equals("down")&&(id.equals("gold")||id.equals("hilda")||id.equals("lyra")))capture("world-"+id);
            if(worldPoses==0) {
               byte[] sliced=frame.clone();PerspectiveCamera camera=(PerspectiveCamera)read(renderer(),"camera");
               Vector3 a=point(vertices,.5f,.36f),b=point(vertices,.5f,.60f);camera.project(a,0,0,WIDTH,HEIGHT);camera.project(b,0,0,WIDTH,HEIGHT);
               for(int y=(int)Math.min(a.y,b.y);y<=(int)Math.max(a.y,b.y);y++)for(int x=0;x<WIDTH;x++) {
                  int k=(y*WIDTH+x)*4;sliced[k]=(byte)255;sliced[k+1]=0;sliced[k+2]=(byte)255;
               }
               int[] negative=projectedTexels(sliced,vertices,camera,pixels(r.getTexture()));
               require(negative[3]>0&&negative[1]<negative[0]*.95f,"Pixel probe missed a simulated terrain cut through the body");
            }
            worldPoses++;
         }
         require(worldPoses==76,"Incomplete actual world pose coverage");
         System.out.println("TRAINER world: 76 complete stationary direction/identity poses, projected texture pixels, negative body-slice control, unchanged map/position/RNG");
      }
      Vector3 point(FloatArray v,float x,float y) {
         return new Vector3(v.get(0)+(v.get(8)-v.get(0))*x+(v.get(32)-v.get(0))*y,
            v.get(1)+(v.get(9)-v.get(1))*x+(v.get(33)-v.get(1))*y,
            v.get(2)+(v.get(10)-v.get(2))*x+(v.get(34)-v.get(2))*y);
      }
      int[] projectedTexels(byte[] screen,FloatArray v,PerspectiveCamera camera,Pixmap texture) {
         float u0=v.get(4),u1=v.get(12),v0=v.get(37),v1=v.get(5);
         int left=(int)Math.floor(u0*texture.getWidth()),right=(int)Math.ceil(u1*texture.getWidth());
         int top=(int)Math.floor(v0*texture.getHeight()),bottom=(int)Math.ceil(v1*texture.getHeight());
         int expected=0,visible=0,rows=0,missing=0,first=-1,last=-1;
         boolean[] opaque=new boolean[bottom-top],seen=new boolean[bottom-top];
         for(int y=top;y<bottom;y++) {
            int rowExpected=0,rowVisible=0;
            for(int x=left;x<right;x++) {
               int pixel=texture.getPixel(x,y);if((pixel&255)<240)continue;
               float fx=((x+.5f)/texture.getWidth()-u0)/(u1-u0),fy=1-((y+.5f)/texture.getHeight()-v0)/(v1-v0);
               if(fx<0||fx>1||fy<0||fy>1)continue;Vector3 p=point(v,fx,fy);camera.project(p,0,0,WIDTH,HEIGHT);boolean match=false;
               for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                  int sx=(int)p.x+dx,sy=(int)p.y+dy;if(sx<0||sy<0||sx>=WIDTH||sy>=HEIGHT)continue;int at=(sy*WIDTH+sx)*4;
                  if(Math.abs((screen[at]&255)-(pixel>>>24&255))<=3&&Math.abs((screen[at+1]&255)-(pixel>>>16&255))<=3
                     &&Math.abs((screen[at+2]&255)-(pixel>>>8&255))<=3)match=true;
               }
               expected++;rowExpected++;if(match){visible++;rowVisible++;}
            }
            if(rowExpected>0){rows++;opaque[y-top]=true;seen[y-top]=rowVisible>=rowExpected*.5f;if(seen[y-top]){if(first<0)first=y-top;last=y-top;}}
         }
         for(int y=first;y>=0&&y<=last;y++)if(opaque[y]&&!seen[y])missing++;
         return new int[]{expected,visible,rows,missing};
      }
      void makeMod(String id,boolean completePoses) {
         FileHandle directory=Gdx.files.local("mods/player/"+id);directory.mkdirs();
         if(completePoses)for(String pose:new String[]{"running","fishing","sleepingbag","sitting","back"})
            directory.child(pose+".png").writeBytes(Gdx.files.internal("player/gold-"+pose+".png").readBytes(),false);
         Pixmap source=new Pixmap(128,16,Pixmap.Format.RGBA8888);
         try {
            source.setColor(0xf83808ff);
            for(int x=0;x<128;x+=16)source.fillRectangle(x+4,2,8,13);
            for(int x=0;x<128;x+=16){source.drawPixel(x+6,5,0xfffbecff);source.drawPixel(x+8,5,0xff8800ff);}
            PixmapIO.writePNG(directory.child("walking.png"),source);
         }finally{source.dispose();}
      }
      void modsAndLifecycle() {
         BwAssets art=BwAssets.get();player.character=MOD;player.color=Color.MAGENTA.cpy();player.currState="";
         TextureRegion mod=art.trainer(player,"down",0,false);int[] tinted=image(mod);
         require(Arrays.stream(tinted).filter(p->p==0xff00ffff).count()>200,"Installed mod lost its costume palette tint");
         require(Arrays.stream(tinted).filter(p->p==0xff8800ff).count()==4,"Costume tint recolors unrelated mod pixels");
         require(mod==art.trainer(player,"down",0,false),"Mod frame was not cached");
         player.color=Color.CYAN.cpy();TextureRegion recolored=art.trainer(player,"down",0,false);
         require(!Arrays.equals(tinted,image(recolored)),"Costume change reuses stale mod pixels");
         TextureRegion before=art.trainer("leaf","down",0,false);int[] expected=image(before);Texture old=before.getTexture();
         releasePixels();BwAssets.disposeShared();require(old.getTextureObjectHandle()==0,"Disposed cast left owned texture alive");
         BwAssets fresh=BwAssets.get();TextureRegion after=fresh.trainer("leaf","down",0,false);
         require(after.getTexture()!=old&&Arrays.equals(expected,image(after)),"Dispose/reload changes trainer art or reuses deleted GL texture");
         // An explicit installed override for an existing cast identity must still win.
         makeMod("gold",false);releasePixels();BwAssets.disposeShared();fresh=BwAssets.get();
         player.character="gold";player.color=Color.MAGENTA.cpy();
         int[] replacement=image(fresh.trainer(player,"down",0,false));
         require(Arrays.stream(replacement).filter(p->p==0xff00ffff).count()>200,"Built-in modern catalog hides an installed player override");
         require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"GL error after trainer reload");
         System.out.println("TRAINER mods: complete custom identity, original costume mask, built-in override priority, cache and disposed/recreated textures");
      }
      void capture(String name) {
         Pixmap p=ScreenUtils.getFrameBufferPixmap(0,0,WIDTH,HEIGHT);PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);
         try{png.write(Gdx.files.local(name+".png"),p);captures++;}catch(java.io.IOException e){throw new IllegalStateException(e);}
         finally{png.dispose();p.dispose();}
      }
      void releasePixels(){for(Pixmap p:pixelCache.values())p.dispose();pixelCache.clear();}
      @Override public void dispose(){try{releasePixels();if(contactBatch!=null)contactBatch.dispose();if(contactFont!=null)contactFont.dispose();super.dispose();}finally{disposed=true;}}
   }
}
