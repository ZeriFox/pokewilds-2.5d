package com.pkmngen.game;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.*;

/** Pixel checks and contact sheets use the real runtime atlas loader and OpenGL. */
public final class UserSpriteAssetsTest extends ApplicationAdapter {
   static final int W=960,H=720;static int checks;static Throwable failure;
   SpriteBatch batch;BitmapFont font;boolean completed;
   static void check(boolean value,String why){checks++;if(!value)throw new IllegalStateException(why);}
   public static void main(String[] args){Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();cfg.setInitialVisible(false);cfg.setWindowedMode(W,H);cfg.useVsync(false);
      UserSpriteAssetsTest app=new UserSpriteAssetsTest();new Lwjgl3Application(app,cfg);
      if(failure!=null){failure.printStackTrace();System.exit(1);}check(app.completed,"Incomplete runtime atlas test");System.out.println("USER SPRITES PASS: checks="+checks+", captures=6, 64 berry identities x3 growth stages x2 frames, independently audited item identities, supplied ball/item atlas, alpha and dispose/reload");}
   @Override public void create(){try{batch=new SpriteBatch();font=new BitmapFont();batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,W,H));run();completed=true;}catch(Throwable e){failure=e;}finally{Gdx.app.exit();}}
   void clear(){Gdx.gl.glViewport(0,0,W,H);Gdx.gl.glClearColor(.11f,.15f,.20f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,W,H));}
   void capture(String name){Pixmap p=ScreenUtils.getFrameBufferPixmap(0,0,W,H);PixmapIO.writePNG(Gdx.files.local(name),p,-1,true);p.dispose();}
   void run(){UserSpriteAssets art=UserSpriteAssets.get();
      String[] names={"poke ball","great ball","ultra ball","master ball","potion","revive","rare candy","max repel","escape rope","fire stone","water stone","shiny stone","dusk stone","dawn stone","cheri berry","chesto berry","pecha berry","rawst berry","aspear berry","oran berry","lum berry","sitrus berry","metal coat","dragon scale","magnet","charcoal","soft sand","hard stone","miracle seed","poison barb","nevermeltice","light clay"};
      clear();batch.begin();for(int i=0;i<names.length;i++){TextureRegion r=art.item(names[i]);check(r!=null,"Missing supplied item "+names[i]);int x=20+i%8*118,y=610-i/8*160;batch.draw(r,x+22,y,64,64);font.draw(batch,names[i],x,y-8);}batch.end();capture("items-gallery.png");
      Texture source=new Texture(Gdx.files.internal("visual/supplied/items-bw.png"));
      clear();batch.begin();for(int j=0;j<80;j++){int i=330+j,x=10+j%10*94,y=620-j/10*83;batch.draw(new TextureRegion(source,i%26*32,i/26*32,32,32),x+16,y,48,48);font.draw(batch,"Cell "+i,x,y-4);}batch.end();capture("items-index-330-409.png");
      // These cells were independently named from supplied-sheet inspection and
      // reference-image comparisons. They catch the original off-by-one and TM
      // compaction errors, which alpha/uniqueness checks alone could not catch.
      String[] exact={"metal coat","dragon scale","magnet","charcoal","soft sand","hard stone","miracle seed","poison barb","nevermeltice","light clay","binding band","dream ball","pretty wing","old rod","good rod","super rod","red apricorn","black apricorn","ragecandybar"};
      int[] cells={215,217,224,231,219,220,221,227,228,250,417,449,444,346,347,348,386,392,404};
      for(int i=0;i<exact.length;i++)check(Arrays.equals(pixels(art.item(exact[i])),pixels(new TextureRegion(source,cells[i]%26*32,cells[i]/26*32,32,32))),"Wrong semantic item identity: "+exact[i]);
      source.dispose();
      String[] later={"red apricorn","blue apricorn","yellow apricorn","green apricorn","pink apricorn","white apricorn","black apricorn","binding band","old rod","good rod","super rod","ragecandybar","pretty wing","ice stone","beast ball","dream ball"};
      clear();batch.begin();for(int i=0;i<later.length;i++){TextureRegion r=art.item(later[i]);check(r!=null,"Missing audited current-game item "+later[i]);int x=20+i%8*118,y=530-i/8*220;batch.draw(r,x+22,y,64,64);font.draw(batch,later[i],x,y-8);}batch.end();capture("items-current-gallery.png");
      clear();batch.begin();for(int i=0;i<64;i++){TextureRegion r=art.berry(i,2,0);int x=12+i%16*59,y=550-i/16*150;batch.draw(r,x+6,y,44,68);font.draw(batch,"Berry "+(i+1),x,y-8);}batch.end();capture("berries-gallery.png");
      Set<Integer> berryHashes=new HashSet<>();
      for(int i=0;i<64;i++)for(int stage=0;stage<3;stage++)for(int frame=0;frame<2;frame++){
         TextureRegion r=art.berry(i,stage,frame*.5f);int[] pixels=pixels(r);int opaque=0;for(int p:pixels)if((p&255)>0)opaque++;
         check(opaque>8,"Empty berry plant "+i+"/"+stage);check(opaque<22*34*.85,"Sheet background/grid retained "+i+"/"+stage);
         check((pixels[0]&255)==0,"Opaque berry top-left");if(stage==2&&frame==0)berryHashes.add(Arrays.hashCode(pixels));
      }
      check(berryHashes.size()>=60,"Species berry art unexpectedly aliases");
      String[] balls={"poke","great","ultra","master","safari","net","dive","nest","repeat","timer","luxury","premier","dusk","heal","quick","cherish","fast","level","lure","heavy","love","friend","moon","sport","dream","beast"};
      clear();batch.begin();for(int i=0;i<balls.length;i++){int x=20+i%9*104,y=580-i/9*180;batch.draw(art.ball(balls[i]+" ball",0),x+12,y,64,64);font.draw(batch,balls[i],x,y-12);}batch.end();capture("balls-gallery.png");
      Set<Integer> ballHashes=new HashSet<>();for(String name:balls){int[] pixels=pixels(art.ball(name+" ball",0));check((pixels[0]&255)==0,"Opaque ball corner "+name);ballHashes.add(Arrays.hashCode(pixels));}check(ballHashes.size()==balls.length,"Distinct balls alias");
      clear();batch.begin();for(int species=0;species<8;species++)for(int stage=0;stage<3;stage++)for(int frame=0;frame<2;frame++){int x=15+species*116,y=530-stage*180;batch.draw(art.berry(species,stage,frame*.5f),x+frame*46,y,44,68);if(frame==0)font.draw(batch,"#"+(species+1)+" stage"+stage,x,y-10);}batch.end();capture("berry-growth.png");
      check(art.item("nonexistent invented item")==null,"Unknown identity must not borrow unrelated icon");
      for(String custom:new String[]{"dark energy","psi energy","life force","soft wool","silky thread","berry seed"})check(art.item(custom)==null,"Custom material borrowed an unrelated canonical icon: "+custom);
      UserSpriteAssets.disposeShared();check(UserSpriteAssets.get().ball("poke ball",0)!=null,"Asset reload after dispose");check(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"GL error");
   }
   int[] pixels(TextureRegion r){int w=r.getRegionWidth(),h=r.getRegionHeight();FrameBuffer fb=new FrameBuffer(Pixmap.Format.RGBA8888,w,h,false);Pixmap p=null;
      try{fb.begin();Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,w,h));batch.disableBlending();batch.begin();batch.setColor(Color.WHITE);batch.draw(r,0,0);batch.end();batch.enableBlending();p=ScreenUtils.getFrameBufferPixmap(0,0,w,h);int[] rgba=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)rgba[y*w+x]=p.getPixel(x,y);return rgba;}
      finally{if(p!=null)p.dispose();fb.end();fb.dispose();Gdx.gl.glViewport(0,0,W,H);}}
   @Override public void dispose(){if(batch!=null)batch.dispose();if(font!=null)font.dispose();UserSpriteAssets.disposeShared();}
}
