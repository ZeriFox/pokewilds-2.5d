package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.*;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.lang.reflect.Field;
import java.util.*;

/** Isolated native mounted-pose/art/geometry proof; no personal save is opened. */
public final class MountedPoseTest {
   static final int WIDTH=1520,HEIGHT=640;
   static final String[] MODES={"RIDE","SURF","FLY"},DIRS={"down","up","left","right"};
   static int checks,captures,poses,worlds;static Throwable failure;
   static void require(boolean test,String text){checks++;if(!test)throw new IllegalStateException(text);}
   static Field field(Object object,String name)throws Exception{for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
   static Object read(Object object,String name)throws Exception{return field(object,name).get(object);}
   static void write(Object object,String name,Object value)throws Exception{field(object,name).set(object,value);}
   public static void main(String[] args){Game.leakTracer=LeakTracer.NoOp.INSTANCE;Probe game=new Probe();
      Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();cfg.setWindowedMode(WIDTH,HEIGHT);cfg.setInitialVisible(false);cfg.useVsync(false);cfg.setForegroundFPS(60);
      try{new Lwjgl3Application(game,cfg);if(failure!=null)throw new IllegalStateException("Mounted pose fixture failed",failure);
         require(game.complete&&game.disposed,"Incomplete native lifecycle");
         System.out.println("MOUNTED POSE PASS: checks="+checks+", poses="+poses+", identities=19, modes=3, directions=4, worldScenes="+worlds+", captures="+captures);
         System.exit(0);}catch(Throwable error){error.printStackTrace();System.exit(1);}}
   static final class Probe extends Game {
      int frame;boolean complete,disposed;SpriteBatch contact;BitmapFont font;
      final Map<Texture,Pixmap> pixelCache=new IdentityHashMap<>();
      Probe(){super(new String[0],4);}
      @Override public void create(){try{super.create();contact=new SpriteBatch();font=new BitmapFont();}catch(Throwable e){failure=e;Gdx.app.exit();}}
      @Override public void render(){if(failure!=null)return;try{super.render();frame++;
         if(frame==12){artContract();wardrobeAndMods();initializeWorld();worldContract();lifecycle();complete=true;Gdx.app.exit();}
      }catch(Throwable e){failure=e;Gdx.app.exit();}}
      Pixmap pixels(Texture texture){Pixmap found=pixelCache.get(texture);if(found!=null)return found;
         FrameBuffer fbo=new FrameBuffer(Pixmap.Format.RGBA8888,texture.getWidth(),texture.getHeight(),false);SpriteBatch batch=new SpriteBatch();Pixmap raw=null;
         try{fbo.begin();Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);batch.disableBlending();
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,texture.getWidth(),texture.getHeight()));batch.begin();batch.draw(texture,0,0);batch.end();
            raw=ScreenUtils.getFrameBufferPixmap(0,0,texture.getWidth(),texture.getHeight());found=new Pixmap(texture.getWidth(),texture.getHeight(),Pixmap.Format.RGBA8888);
            found.setBlending(Pixmap.Blending.None);for(int y=0;y<found.getHeight();y++)for(int x=0;x<found.getWidth();x++)found.drawPixel(x,y,raw.getPixel(x,found.getHeight()-1-y));
         }finally{fbo.end();if(raw!=null)raw.dispose();batch.dispose();fbo.dispose();Gdx.gl.glViewport(0,0,WIDTH,HEIGHT);}
         pixelCache.put(texture,found);return found;}
      int[] image(TextureRegion region){Pixmap pixels=pixels(region.getTexture());Pixmap copy=MountedTrainerSprites.copyRegion(pixels,region);
         int[] values=new int[1024];try{require(copy.getWidth()==32&&copy.getHeight()==32,"Mounted frame is not complete 32px art");
            for(int y=0;y<32;y++)for(int x=0;x<32;x++){int c=copy.getPixel(x,y);values[y*32+x]=(c&255)==0?0:c;}}
         finally{copy.dispose();}return values;}
      void artContract(){BwAssets art=BwAssets.get();
         for(String mode:MODES){Set<Integer> frontIdentities=new HashSet<>();
            for(String id:TrainerCatalog.CAST)for(String dir:DIRS){player.character=id;player.dirFacing=dir;player.color=new Color(0xf83808ff);
               TextureRegion idle=art.trainer(player,dir,0,false),pose=art.mountedTrainer(player,mode,0);
               int[] original=image(idle),mounted=image(pose);
               require(pose==art.mountedTrainer(player,mode,.251f),"Mounted pose follows walking clock "+id+"/"+mode+"/"+dir);
               require(pose==art.mountedTrainer(player,mode,200f),"Time creates a fresh rider gait "+id);
               for(int y=0;y<=18;y++)for(int x=0;x<32;x++)if(original[y*32+x]!=0)require(mounted[y*32+x]==original[y*32+x],"Identity head/hair/hat changed "+id);
               int lower=0,different=0,bottom=0;for(int y=19;y<32;y++)for(int x=0;x<32;x++){if(mounted[y*32+x]!=0)lower++;if(mounted[y*32+x]!=original[y*32+x])different++;if(y>=27&&mounted[y*32+x]!=0)bottom++;}
               require(lower>=45&&different>=35&&bottom>=3,"Pose has cropped or reused standing legs "+id+"/"+mode+"/"+dir+" lower="+lower+" difference="+different+" feet="+bottom);
               if(dir.equals("down"))require(frontIdentities.add(Arrays.hashCode(mounted)),"Cast identity collapsed "+id+"/"+mode);poses++;
            }
            gallery(mode);
         }
         require(poses==228,"Incomplete cast/direction/mode coverage");
         for(String dir:DIRS){player.character="gold";player.dirFacing=dir;Set<Integer> hashes=new HashSet<>();for(String mode:MODES)require(hashes.add(Arrays.hashCode(image(art.mountedTrainer(player,mode,0)))),"Mounted mode lacks a distinct silhouette "+dir);}
         require(MountedTrainerSprites.sway("RIDE",.5f,false)==0,"Stationary RIDE rider bounces like walking");
         require(Math.abs(MountedTrainerSprites.sway("SURF",.5f,true))<=.15f&&Math.abs(MountedTrainerSprites.sway("FLY",.5f,true))<=.15f,"Rider sway is too large");
      }
      void gallery(String mode){Gdx.gl.glClearColor(.08f,.11f,.14f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);contact.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,WIDTH,HEIGHT));
         contact.begin();font.setColor(Color.WHITE);font.draw(contact,mode+" / dedicated mounted body - full cast and four directions",16,HEIGHT-12);
         for(int i=0;i<TrainerCatalog.CAST.length;i++){String id=TrainerCatalog.CAST[i];player.character=id;for(int d=0;d<4;d++){player.dirFacing=DIRS[d];TextureRegion sprite=BwAssets.get().mountedTrainer(player,mode,0);
            int x=i*80,y=HEIGHT-160-d*145;contact.draw(sprite,x+2,y,76,76);font.getData().setScale(.75f);font.draw(contact,id,x+2,y-4);font.getData().setScale(1);}}
         contact.end();capture("cast-"+mode.toLowerCase(Locale.ROOT));}
      void wardrobeAndMods()throws Exception{BwAssets art=BwAssets.get();player.name="Mounted fixture";player.character="gold";player.dirFacing="down";
         int[] before=image(art.mountedTrainer(player,"RIDE",0));CharacterAppearance custom=new CharacterAppearance();Arrays.fill(custom.styles,1);Arrays.fill(custom.colors,2);
         CharacterAppearance.save(player.name,player.character,custom);TextureRegion changed=art.mountedTrainer(player,"RIDE",0);int[] after=image(changed);
         require(!Arrays.equals(before,after),"Mounted cache hides wardrobe change");int[] idle=image(art.trainer(player,0,false));
         for(int y=0;y<=18;y++)for(int x=0;x<32;x++)if(idle[y*32+x]!=0)require(after[y*32+x]==idle[y*32+x],"Mounted pose loses custom hair or headwear");
         CharacterAppearance.save(player.name,player.character,new CharacterAppearance());
         FileHandle path=Gdx.files.local("mods/player/verification_mounted_mod/walking.png");path.parent().mkdirs();Pixmap sheet=new Pixmap(128,16,Pixmap.Format.RGBA8888);
         try{sheet.setColor(0xf83808ff);for(int x=0;x<128;x+=16)sheet.fillRectangle(x+4,2,8,13);for(int x=0;x<128;x+=16)sheet.drawPixel(x+6,5,0xffff00ff);PixmapIO.writePNG(path,sheet);}finally{sheet.dispose();}
         player.character="verification_mounted_mod";player.color=Color.MAGENTA.cpy();int[] mod=image(art.mountedTrainer(player,"RIDE",0));int[] modIdle=image(art.trainer(player,0,false));
         for(int y=0;y<=18;y++)for(int x=0;x<32;x++)if(modIdle[y*32+x]!=0)require(mod[y*32+x]==modIdle[y*32+x],"Mod identity pixels lost");
         player.color=Color.CYAN.cpy();require(!Arrays.equals(mod,image(art.mountedTrainer(player,"RIDE",0))),"Mounted cache hides mod costume change");
         require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"GL error composing wardrobe/mods");player.character="gold";player.color=new Color(0xf83808ff);}
      void initializeWorld(){Gdx.input.setInputProcessor(null);actionStack.removeIf(a->a instanceof DrawSetupMenu);server=new Server();Network.register(server);map=new PkmnMap("mounted-unsaved-fixture");
         map.bottomLeft=new Vector2(-256,-256);map.topRight=new Vector2(256,256);map.minimap=new Pixmap(64,64,Pixmap.Format.RGBA8888);
         for(int y=-256;y<=256;y+=16)for(int x=-256;x<=256;x+=16){Tile tile=new Tile("green1",new Vector2(x,y),true);tile.biome="grassland";map.tiles.put(tile.position,tile);}
         map.overworldTiles=map.tiles;map.timeOfDay="day";map.refreshCache=true;player.position.set(0,0);player.spawnLoc.set(0,0);player.dirFacing="down";
         cam.position.set(8,8,0);cam.zoom=.8f;start();actionStack.removeIf(a->a instanceof CycleDayNight);actionStackCopy.clear();player.acceptInput=false;mapBatch.setColor(Color.WHITE);
         System.setProperty("pokewilds.ambientOverlays","off");modernUi.desktop.preferences.reducedMotion=true;}
      void worldContract()throws Exception{JohtoRenderer r=(JohtoRenderer)read(this,"johtoRenderer");long terrain=ModernWorldGenerator.fingerprint(map.tiles);
         for(String mode:MODES)for(String dir:DIRS){player.currFieldMove=mode;player.hmPokemon=new Pokemon(mode.equals("SURF")?"lapras":mode.equals("FLY")?"pidgeot":"arcanine",20);
            player.dirFacing=dir;player.character="gold";Vector2 playerBefore=player.position.cpy(),pokemonBefore=player.hmPokemon.position.cpy();
            Game.rand.setSeed(101);map.rand.setSeed(102);write(r,"seconds",0f);((Map<?,?>)read(r,"motion")).clear();
            Gdx.gl.glViewport(0,0,WIDTH,HEIGHT);require(r.render(this),"Mounted world render declined");TextureRegion actor=BwAssets.get().mountedTrainer(player,mode,0);
            WorldMeshBatch mesh=(WorldMeshBatch)read(r,"geometry");Object buffer=((Map<?,?>)read(mesh,"groups")).get(actor.getTexture());require(buffer!=null,"Dedicated rider art absent from world "+mode+"/"+dir);
            FloatArray vertices=(FloatArray)read(buffer,"vertices");require(vertices.size==48,"Mounted rider quad incomplete");
            require(Math.abs(Math.abs(vertices.get(5)-vertices.get(37))-1f)<.001f,"Rider sprite still uses a cropped UV rectangle");
            int[] visible=visibleTexels(ScreenUtils.getFrameBufferPixels(0,0,WIDTH,HEIGHT,false),vertices,(PerspectiveCamera)read(r,"camera"),pixels(actor.getTexture()),(float[])read(r,"lights"));
            require(visible[0]>80&&visible[1]>=visible[0]*.90f,"Mount clips rider head "+mode+"/"+dir+" visible="+Arrays.toString(visible));
            require(visible[2]>35&&visible[3]>=visible[2]*.70f,"Mount hides rider body/bent legs "+mode+"/"+dir+" visible="+Arrays.toString(visible));
            require(Game.rand.nextLong()==new Random(101).nextLong()&&map.rand.nextLong()==new Random(102).nextLong(),"Mounted presentation consumes gameplay RNG");
            require(playerBefore.equals(player.position)&&pokemonBefore.equals(player.hmPokemon.position)&&terrain==ModernWorldGenerator.fingerprint(map.tiles),"Mounted render mutated gameplay");
            require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"GL error rendering mounted world");capture("world-"+mode.toLowerCase(Locale.ROOT)+"-"+dir);worlds++;
         }player.currFieldMove="";player.hmPokemon=null;}
      int[] visibleTexels(byte[] screen,FloatArray vertices,PerspectiveCamera camera,Pixmap texture,float[] lights) {
         int[] count=new int[4];
         for(int y=0;y<32;y++)for(int x=0;x<32;x++) {
            int pixel=texture.getPixel(x,y);if((pixel&255)<240)continue;
            float fx=(x+.5f)/32f,fy=1-(y+.5f)/32f;
            Vector3 point=new Vector3(vertices.get(0)+(vertices.get(8)-vertices.get(0))*fx+(vertices.get(32)-vertices.get(0))*fy,
               vertices.get(1)+(vertices.get(9)-vertices.get(1))*fx+(vertices.get(33)-vertices.get(1))*fy,
               vertices.get(2)+(vertices.get(10)-vertices.get(2))*fx+(vertices.get(34)-vertices.get(2))*fy);
            // Fire/Flash companions legitimately illuminate the rider. Compare
            // to that presented material colour rather than unlit source RGB.
            float glow=0;for(int i=0;i<lights.length;i+=4){float distance=Vector2.dst(point.x,point.z,lights[i],lights[i+1]);float f=MathUtils.clamp(1-distance/Math.max(lights[i+2],1),0,1);glow=Math.max(glow,f*f*lights[i+3]);}
            glow=MathUtils.clamp(glow,0,1);int red=Math.min(255,Math.round((pixel>>>24&255)*(1+.14f*glow))),green=Math.min(255,Math.round((pixel>>>16&255)*(1+.03f*glow))),blue=pixel>>>8&255;
            camera.project(point,0,0,WIDTH,HEIGHT);boolean visible=false;
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
               int sx=(int)point.x+dx,sy=(int)point.y+dy;if(sx<0||sx>=WIDTH||sy<0||sy>=HEIGHT)continue;int at=(sy*WIDTH+sx)*4;
               if(Math.abs((screen[at]&255)-red)<=3&&Math.abs((screen[at+1]&255)-green)<=3&&Math.abs((screen[at+2]&255)-blue)<=3)visible=true;
            }
            int start=y<=18?0:2;count[start]++;if(visible)count[start+1]++;
         }
         return count;
      }
      void lifecycle(){BwAssets art=BwAssets.get();player.character="gold";player.dirFacing="down";TextureRegion before=art.mountedTrainer(player,"RIDE",0);int[] expected=image(before);Texture texture=before.getTexture();
         clearPixels();BwAssets.disposeShared();require(texture.getTextureObjectHandle()==0,"Mounted cache retains disposed texture");TextureRegion fresh=BwAssets.get().mountedTrainer(player,"RIDE",0);
         require(fresh.getTexture()!=texture&&Arrays.equals(expected,image(fresh)),"Mounted art changes after asset recreation");}
      void capture(String name){Pixmap pixels=ScreenUtils.getFrameBufferPixmap(0,0,WIDTH,HEIGHT);PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);
         try{png.write(Gdx.files.local(name+".png"),pixels);captures++;}catch(java.io.IOException e){throw new IllegalStateException(e);}finally{png.dispose();pixels.dispose();}}
      void clearPixels(){for(Pixmap p:pixelCache.values())p.dispose();pixelCache.clear();}
      @Override public void dispose(){try{clearPixels();if(contact!=null)contact.dispose();if(font!=null)font.dispose();super.dispose();}finally{disposed=true;}}
   }
}
