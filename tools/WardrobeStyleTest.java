package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.lang.reflect.*;
import java.util.*;

/** Actual GL compositions, profile persistence, controller draft/apply and existing-game options. */
public final class WardrobeStyleTest {
   static final int W=960,H=760;static int checks,captures;static Throwable failure;
   static void check(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException(why);}
   static Field field(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
   public static void main(String[] args){Game.leakTracer=LeakTracer.NoOp.INSTANCE;Probe p=new Probe();
      Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();cfg.setWindowedMode(W,H);cfg.setInitialVisible(false);cfg.useVsync(false);
      new Lwjgl3Application(p,cfg);if(failure!=null){failure.printStackTrace();System.exit(1);}check(p.complete,"Incomplete wardrobe proof");
      System.out.println("WARDROBE STYLE PASS: checks="+checks+", identities=19, directions=4, captures="+captures+", separateparts/colors, realcontrollerdraft/apply/cancel, existing-gameentry, profileatomicbackup/reload, runningposes");System.exit(0);}
   static final class Probe extends Game {
      int frame;boolean complete;DrawSetupMenu setup;SpriteBatch proofBatch;BitmapFont font;
      Probe(){super(new String[0],4);}
      @Override public void render(){if(failure!=null)return;try{super.render();if(++frame==15){run();complete=true;Gdx.app.exit();}}catch(Throwable e){failure=e;Gdx.app.exit();}}
      CharacterAppearance sample(int variant){CharacterAppearance a=new CharacterAppearance();for(int i=0;i<5;i++){a.styles[i]=variant;a.colors[i]=(i+variant)%9+1;}return a;}
      void run()throws Exception {
         DesktopMenuController c=modernUi.desktop;setup=c.setup;c.preferences.reducedMotion=true;
         modernUi.applyViewport(this,W,H);setup.name.clear();for(char letter:"Wardrobe".toCharArray())setup.name.add(letter);
         c.trainerIndex=c.trainerChoices.indexOf("gold");c.openWardrobe(false);
         check(c.screen==DesktopMenuController.Screen.WARDROBE&&c.appearanceDraft!=null,"Picker cannot open clothing editor");
         String before=CharacterAppearance.get("Wardrobe","gold").key();
         c.appearanceDraft.styles[0]=2;c.back=true;c.beforeAction(this,setup);
         check(c.screen==DesktopMenuController.Screen.TRAINERS&&CharacterAppearance.get("Wardrobe","gold").key().equals(before),"Cancel wrote a profile");
         c.openWardrobe(false);for(int i=0;i<5;i++){c.appearanceDraft.styles[i]=1;c.appearanceDraft.colors[i]=i+1;}
         captureMenu("01-wardrobe-draft",setup);
         c.wardrobeSlot=6;c.enter=true;c.beforeAction(this,setup);
         check(c.appearanceDraft==null&&c.screen==DesktopMenuController.Screen.SETUP,"Apply did not close editor and select trainer");
         CharacterAppearance.reload();CharacterAppearance saved=CharacterAppearance.get("Wardrobe","gold");
         check(saved.styles[0]==1&&saved.colors[4]==5,"Saved appearance did not survive reload");
         player.name="Wardrobe";player.character="gold";
         TextureRegion preview=BwAssets.get().trainerPreview("gold","down",0,false,player.color,saved);
         check(preview==BwAssets.get().trainer(player,"down",0,false),"World and preview compose different appearances");
         CharacterAppearance independent=saved.copy();independent.colors[1]=9;
         int[] bagBefore=rgba(BwAssets.get().trainerPreview("gold","up",0,false,player.color,saved));
         int[] bagAfter=rgba(BwAssets.get().trainerPreview("gold","up",0,false,player.color,independent));
         check(!Arrays.equals(bagBefore,bagAfter),"Backpack palette has no distinct output in the same pose");
         for(int i=0;i<1024;i++)if(bagBefore[i]!=bagAfter[i])check((bagBefore[i]&255)==(bagAfter[i]&255)&&i/32>=4&&i/32<=16,"Backpack color changed head, shoes or silhouette");
         CharacterAppearance bagOnly=new CharacterAppearance();bagOnly.styles[1]=1;
         check(!Arrays.equals(rgba(BwAssets.get().trainerPreview("gold","down",0,false,Color.RED,bagOnly)),rgba(BwAssets.get().trainerPreview("gold","down",0,false,Color.BLUE,bagOnly))),"Original clothing accents stop working when only the backpack changes");
         setupFlow(c,saved);
         contacts();world();existingGame();profileSafety();
      }
      void setupFlow(DesktopMenuController c,CharacterAppearance saved)throws Exception {
         setup.name.clear();for(char letter:"Renamed".toCharArray())setup.name.add(letter);
         check(c.previewAppearance("gold").key().equals(saved.key()),"Editing the name loses the selected appearance");
         c.openOptions(setup);c.optionIndex=15;c.enter=true;c.beforeAction(this,setup);
         check(c.wardrobeName.equals("Renamed")&&c.appearanceDraft.key().equals(saved.key()),"F1 wardrobe during setup uses the stale player name or look");
         c.appearanceDraft.colors[2]=8;c.wardrobeSlot=6;c.enter=true;c.beforeAction(this,setup);
         check(c.optionsOpen()&&c.selectedAppearances.get("gold").colors[2]==8,"F1 wardrobe did not update the pending setup look");c.closeOptions(false);
         c.change(DesktopMenuController.Screen.SETUP);setup.newLoadIndex=0;setup.localHostJoinIndex=0;
         for(DesktopMenuController.Row row:c.rows())if(row.field==DesktopMenuController.Field.GO)DrawSetupMenu.currIndex=row.legacyIndex;
         c.enter=true;check(!c.beforeAction(this,setup),"Customized Go no longer delegates original launch branch");
         InputProcessor.blockHeldInputs();CharacterAppearance.reload();
         check(CharacterAppearance.get("Renamed","gold").colors[2]==8,"Go replaced the new F1 look with an earlier profile after rename");
         player.name="Renamed";
         @SuppressWarnings("unchecked") Set<String> overrides=(Set<String>)field(BwAssets.class,"customRegions").get(BwAssets.get());
         overrides.add("trainer_gold_walk_down_1");c.trainerIndex=c.trainerChoices.indexOf("gold");c.openWardrobe(false);
         check(c.appearanceDraft==null&&!c.message.isEmpty(),"Wardrobe offers ineffective customization over installed atlas artwork");
         overrides.remove("trainer_gold_walk_down_1");
         c.openWardrobe(false);int style=c.appearanceDraft.styles[0],color=c.appearanceDraft.colors[0];
         ModernUi.Box swatch=DesktopMenuController.wardrobeSwatch(0);field(DesktopMenuController.class,"pointerX").setFloat(c,swatch.x+2);field(DesktopMenuController.class,"pointerY").setFloat(c,swatch.y+2);field(DesktopMenuController.class,"pointerButton").setInt(c,0);
         c.beforeAction(this,setup);check(c.appearanceDraft.styles[0]==style&&c.appearanceDraft.colors[0]!=color,"Clicking a color swatch changes the clothing style instead");
         c.back=true;c.beforeAction(this,setup);
      }
      void contacts(){proofBatch=new SpriteBatch();font=new BitmapFont();proofBatch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,W,H));
         for(int d=0;d<4;d++){String direction=new String[]{"down","up","left","right"}[d];
            Gdx.gl.glViewport(0,0,W,H);Gdx.gl.glClearColor(.08f,.11f,.15f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT);
            proofBatch.begin();font.draw(proofBatch,"WARDROBE  /  "+direction.toUpperCase(Locale.ROOT)+"  /  original - adventure - rain",16,H-18);
            for(int i=0;i<TrainerCatalog.CAST.length;i++) {
               String id=TrainerCatalog.CAST[i];int x=15+i%4*237,y=H-150-i/4*142;font.draw(proofBatch,Game.capitalize(id),x+4,y+84);
               for(int style=0;style<3;style++){
                  CharacterAppearance a=style==0?new CharacterAppearance():sample(style);
                  TextureRegion r=BwAssets.get().trainerPreview(id,direction,.126f,true,null,a);
                  check(r!=null&&r.getRegionWidth()==32&&r.getRegionHeight()==32,"Invalid composed sheet "+id);
                  proofBatch.draw(r,x+style*70,y,64,64);
               }
            }
            proofBatch.end();capture("0"+(d+2)+"-cast-parts-"+direction);
         }
         // Independent substitutions are structural, not a single full-sprite tint.
         for(String id:TrainerCatalog.CAST)for(int slot=0;slot<5;slot++) {
            CharacterAppearance a=new CharacterAppearance();a.styles[slot]=1;a.colors[slot]=3;int[] first=rgba(BwAssets.get().trainerPreview(id,"up",0,false,null,a));
            a.styles[slot]=2;int[] second=rgba(BwAssets.get().trainerPreview(id,"up",0,false,null,a));
            check(!Arrays.equals(first,second),"Two clothing styles collapse into a recolor: "+id+"/"+slot);
         }
         for(String id:TrainerCatalog.CAST)for(String dir:new String[]{"down","up","left","right"})for(int style=1;style<=2;style++)for(float time:new float[]{0,.126f,.251f}) {
            int[] pixels=rgba(BwAssets.get().trainerPreview(id,dir,time,true,null,sample(style)));continuous(pixels,id+"/"+dir+"/"+style+"/"+time);
         }
         for(String id:TrainerCatalog.CAST)for(String dir:new String[]{"left","right"}) {
            int[] left=rgba(BwAssets.get().trainerPreview(id,dir,0,true,null,sample(1))),right=rgba(BwAssets.get().trainerPreview(id,dir,.251f,true,null,sample(1)));
            check(!Arrays.equals(Arrays.copyOfRange(left,32,192),Arrays.copyOfRange(right,32,192)),"Sideways shoes never change their foot contact "+id+"/"+dir);
         }
      }
      void continuous(int[] pixels,String label){int lo=32,hi=-1;boolean[] occupied=new boolean[32];for(int y=0;y<32;y++)for(int x=0;x<32;x++)if((pixels[y*32+x]&255)>127){occupied[y]=true;lo=Math.min(lo,y);hi=Math.max(hi,y);}check(hi-lo>=20,"Unexpectedly tiny composed trainer "+label);for(int y=lo;y<=hi;y++)check(occupied[y],"Detached clothing/body at raster row "+y+" "+label);}
      int[] rgba(TextureRegion r){FrameBuffer fb=new FrameBuffer(Pixmap.Format.RGBA8888,32,32,false);SpriteBatch b=new SpriteBatch();Pixmap p=null;
         try{fb.begin();Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);b.disableBlending();b.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,32,32));b.begin();b.draw(r,0,0);b.end();p=ScreenUtils.getFrameBufferPixmap(0,0,32,32);int[] values=new int[1024];for(int y=0;y<32;y++)for(int x=0;x<32;x++)values[y*32+x]=p.getPixel(x,y);return values;}
         finally{fb.end();if(p!=null)p.dispose();b.dispose();fb.dispose();Gdx.gl.glViewport(0,0,W,H);}}
      void world()throws Exception {
         actionStack.removeIf(a->a instanceof DrawSetupMenu);server=new Server();Network.register(server);map=new PkmnMap("wardrobe-fixture");
         map.bottomLeft=new Vector2(-256,-256);map.topRight=new Vector2(256,256);map.minimap=new Pixmap(64,64,Pixmap.Format.RGBA8888);
         for(int y=-256;y<=256;y+=16)for(int x=-256;x<=256;x+=16){Tile t=new Tile("mountain3",new Vector2(x,y),true);t.biome="mountain";map.tiles.put(t.position,t);}
         map.overworldTiles=map.tiles;map.timeOfDay="day";player.position.set(0,0);player.spawnLoc.set(0,0);cam.position.set(8,8,0);cam.zoom=1.1f;
         start();player.setCurrPokemon();actionStack.removeIf(a->a instanceof CycleDayNight);actionStackCopy.clear();mapBatch.setColor(Color.WHITE);
         long state=ModernWorldGenerator.fingerprint(map.tiles);JohtoRenderer renderer=(JohtoRenderer)field(Game.class,"johtoRenderer").get(this);
         for(String id:new String[]{"gold","hilda","gloria"}){
            player.character=id;CharacterAppearance.save(player.name,id,sample(2));player.dirFacing="down";
            Gdx.gl.glViewport(0,0,W,H);check(renderer.render(this),"Customized world renderer failed");capture("world-"+id);
            check(state==ModernWorldGenerator.fingerprint(map.tiles)&&player.position.equals(Vector2.Zero),"Appearance mutated the world");
            TextureRegion walk=BwAssets.get().trainer(player,"right",0,true);PlayerRunning running=new PlayerRunning(this,player,false);actionStack.add(running);
            TextureRegion run=BwAssets.get().trainer(player,"right",0,true);int[] runPixels=rgba(run);check(!Arrays.equals(rgba(walk),runPixels),"Running changes cadence only, not pose "+id);continuous(runPixels,id+"/running");actionStack.remove(running);
         }
      }
      void existingGame()throws Exception {
         DesktopMenuController c=modernUi.desktop;DrawPlayerMenu parent=new DrawPlayerMenu(this,null);actionStack.add(parent);
         String identity=player.character;c.openOptions(parent);c.optionIndex=15;c.enter=true;c.beforeAction(this,parent);
         check(c.appearanceDraft!=null&&c.wardrobeFromOptions&&c.wardrobeId.equals(identity),"Existing game cannot enter wardrobe from options");
         captureMenu("09-existing-game-wardrobe",parent);c.appearanceDraft.styles[2]=1;c.appearanceDraft.colors[2]=7;c.wardrobeSlot=6;c.enter=true;c.beforeAction(this,parent);
         check(player.character.equals(identity)&&c.appearanceDraft==null&&c.optionsOpen(),"Applying clothing changes identity or closes the existing game");
         check(CharacterAppearance.get(player.name,identity).colors[2]==7,"Existing game profile was not saved");
         captureMenu("10-existing-game-options",parent);c.closeOptions(false);
      }
      void profileSafety()throws Exception {
         String valid=Gdx.files.local(CharacterAppearance.FILE).readString("UTF-8");
         Gdx.files.local(CharacterAppearance.FILE).writeString("{broken",false,"UTF-8");CharacterAppearance.reload();
         check(!CharacterAppearance.get("Wardrobe","gold").active()&&!CharacterAppearance.warning.isEmpty(),"Invalid profile does not fall back safely");
         DesktopMenuController c=modernUi.desktop;c.closeOptions(false);c.trainerIndex=c.trainerChoices.indexOf("gold");c.openWardrobe(false);check(!c.message.isEmpty(),"Opening the wardrobe hides the invalid-file warning");c.blur();
         CharacterAppearance.save("Wardrobe","gold",sample(1));
         check(Gdx.files.local(CharacterAppearance.FILE+".bak").readString("UTF-8").equals("{broken"),"Invalid existing profile overwritten without recoverable backup");
         Gdx.files.local(CharacterAppearance.FILE).writeString(valid,false,"UTF-8");CharacterAppearance.reload();
         check(CharacterAppearance.get("Wardrobe","gold").active(),"Restored profile no longer loads");
         check(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"GL error in wardrobe verification");
      }
      void captureMenu(String name,Action action){uiBatch.begin();try{modernUi.render(this,action);}finally{uiBatch.end();}capture(name);}
      void capture(String name){Pixmap p=ScreenUtils.getFrameBufferPixmap(0,0,W,H);PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);try{png.write(Gdx.files.local(name+".png"),p);captures++;}catch(Exception e){throw new RuntimeException(e);}finally{p.dispose();png.dispose();}}
      @Override public void dispose(){if(proofBatch!=null)proofBatch.dispose();if(font!=null)font.dispose();super.dispose();}
   }
}
