package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Random;

/** Native fixed-clock scenery checks. No personal saves or gameplay steps. */
public final class BattleSceneryTest {
   static int checks;static Throwable failure;
   static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException(message);}
   static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
   public static void main(String[] args){
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;Probe game=new Probe();
      Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
      cfg.setInitialVisible(false);cfg.setWindowedMode(960,540);cfg.useVsync(false);cfg.setForegroundFPS(60);
      try {
         new Lwjgl3Application(game,cfg);
         if(failure!=null)throw new IllegalStateException("Scenery verification failed",failure);
         require(game.complete&&game.disposed,"Incomplete native lifecycle");
         System.out.println("BATTLE SCENERY PASS: checks="+checks+", scenes="+game.scenes+", captures="+game.captures
            +", four aspects, real shader motion, fixed actor/stage/UI anchors, reduced motion, cache/disposal, batch state, gameplay/RNG unchanged");
         System.exit(0);
      }catch(Throwable e){e.printStackTrace();System.exit(1);}
   }
   static final class Probe extends Game {
      final int[][] sizes={{960,540},{640,576},{420,840},{1440,400}};
      final String[] biomes={"forest","beach","ocean","snow","desert","volcano","graveyard","cave"};
      int boot,sizeIndex,settle,scenes,captures;boolean requested,complete,disposed,ready;
      Pokemon own,enemy;ShaderProgram custom;
      Probe(){super(new String[0],4);}
      void initialize()throws Exception{
         Gdx.input.setInputProcessor(null);actionStack.clear();server=new Server();Network.register(server);
         map=new PkmnMap("battle-scenery-fixture");map.bottomLeft=new Vector2(-64,-64);map.topRight=new Vector2(64,64);
         map.minimap=new Pixmap(16,16,Pixmap.Format.RGBA8888);
         for(int y=-64;y<=64;y+=16)for(int x=-64;x<=64;x+=16){Vector2 pos=new Vector2(x,y);map.tiles.put(pos,new Tile("green1",pos.cpy(),true));}
         player.position.set(0,0);player.spawnLoc.set(0,0);player.name="Scenery";player.character="gold";
         start();player.setCurrPokemon();actionStack.clear();actionStackCopy.clear();
         own=new Pokemon("machop",10);enemy=new Pokemon("charizard",10);player.currPokemon=own;battle.oppPokemon=enemy;
         own.backSprite.setPosition(16,48);enemy.sprite.setPosition(96,88);player.battleSprite.setPosition(160,49);
         player.dontDrawMapDuringBattle=true;map.timeOfDay="day";
         custom=SpriteBatch.createDefaultShader();require(custom.isCompiled(),"Control shader failed");ready=true;
         for(String water:new String[]{"beach","ocean","ocean1","sea","sea1","water","water1","SEA1"})
            require(BattleScenery.kind(water)<=2,"Marine alias rendered as forest: "+water);
      }
      @Override public void render(){
         if(failure!=null)return;
         try {
            if(!ready){super.render();if(++boot<15)return;initialize();}
            if(sizeIndex==sizes.length){lifecycle();complete=true;Gdx.app.exit();return;}
            int[] size=sizes[sizeIndex];
            if(!requested){Gdx.graphics.setWindowedMode(size[0],size[1]);requested=true;settle=2;return;}
            if(settle-->0)return;
            require(Gdx.graphics.getBackBufferWidth()==size[0]&&Gdx.graphics.getBackBufferHeight()==size[1],"Resize not applied");
            for(String biome:biomes)compare(biome);
            sizeIndex++;requested=false;
         }catch(Throwable e){failure=e;Gdx.app.exit();}
      }
      byte[] draw(float time,boolean reduced,boolean actors,String capture)throws Exception {
         modernUi.desktop.preferences.reducedMotion=reduced;
         Gdx.gl.glViewport(0,0,width(),height());Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);ScreenUtils.clear(1,0,1,1);
         float[] ownBefore=own.backSprite.getVertices().clone(),enemyBefore=enemy.sprite.getVertices().clone();
         float[] playerBefore=player.battleSprite.getVertices().clone();long mapBefore=ModernWorldGenerator.fingerprint(map.tiles);
         int ownHp=own.currentStats.get("hp"),enemyHp=enemy.currentStats.get("hp");int actions=actionStack.size();
         Game.rand.setSeed(8081);map.rand.setSeed(8087);
         Matrix4 projection=new Matrix4(uiBatch.getProjectionMatrix()),transform=new Matrix4(uiBatch.getTransformMatrix());
         johtoBattleRenderer.prepareFrame(this,false);
         field(JohtoBattleRenderer.class,"scenerySeconds").setFloat(johtoBattleRenderer,time);
         field(PmdBattleSprites.class,"seconds").setFloat(null,2.0f);
         Color incoming=new Color(.8f,.7f,.6f,.9f);uiBatch.setShader(custom);uiBatch.setColor(incoming);uiBatch.begin();
         johtoBattleRenderer.drawBackground(this);
         require(uiBatch.getShader()==custom,"Scenery shader leaked into actors/UI");
         require(uiBatch.getColor().equals(incoming),"Scenery changed the caller color");uiBatch.setColor(Color.WHITE);
         if(actors){own.backSprite.draw(uiBatch);enemy.sprite.draw(uiBatch);johtoBattleRenderer.drawCommandMenu(this,"tl",false);}
         uiBatch.end();johtoBattleRenderer.finishFrame(this);uiBatch.setShader(null);
         require(Arrays.equals(projection.val,uiBatch.getProjectionMatrix().val)&&Arrays.equals(transform.val,uiBatch.getTransformMatrix().val),"Persistent camera/transform changed");
         require(Arrays.equals(ownBefore,own.backSprite.getVertices())&&Arrays.equals(enemyBefore,enemy.sprite.getVertices())&&Arrays.equals(playerBefore,player.battleSprite.getVertices()),"Scenery moved gameplay actor anchors");
         require(own.currentStats.get("hp")==ownHp&&enemy.currentStats.get("hp")==enemyHp&&actions==actionStack.size(),"Scenery stepped a gameplay action");
         require(ModernWorldGenerator.fingerprint(map.tiles)==mapBefore,"Scenery changed saved terrain");
         require(Game.rand.nextLong()==new Random(8081).nextLong()&&map.rand.nextLong()==new Random(8087).nextLong(),"Scenery used gameplay/map RNG");
         require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"OpenGL error");
         byte[] result=ScreenUtils.getFrameBufferPixels(0,0,width(),height(),false);
         int poison=0;for(int i=0;i<result.length;i+=4)if((result[i]&255)==255&&result[i+1]==0&&(result[i+2]&255)==255)poison++;
         require(poison==0,"Uncovered framebuffer: "+poison);
         if(capture!=null){Pixmap pixels=ScreenUtils.getFrameBufferPixmap(0,0,width(),height());PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);
            try{png.write(Gdx.files.local(capture+".png"),pixels);}finally{png.dispose();pixels.dispose();}captures++;}
         return result;
      }
      void compare(String biome)throws Exception {
         map.currBiome=biome;String label=sizeIndex+"-"+biome;
         byte[] a=draw(3.2f,false,true,sizeIndex==0?label+"-a":null);
         Texture cached=(Texture)field(JohtoBattleRenderer.class,"arena").get(johtoBattleRenderer);
         byte[] near=draw(3.22f,false,true,null),b=draw(6.8f,false,true,sizeIndex==0?label+"-b":null);
         require(cached==field(JohtoBattleRenderer.class,"arena").get(johtoBattleRenderer),"Per-frame arena texture allocation");
         int changed=changed(a,b,0,0,width(),height());require(changed>width()*height()/200,"Static panorama "+label+": "+changed);
         require(changed(a,near,0,0,width(),height())>20,"Shader lacks continuous movement "+label);
         require(Arrays.equals(a,draw(3.2f,false,true,null)),"Same clock changed pixels "+label);
         byte[] stillA=draw(3.2f,true,true,sizeIndex==0?label+"-still":null),stillB=draw(80f,true,true,null);
         require(Arrays.equals(stillA,stillB),"Reduced motion still animates "+label);
         require(Arrays.equals(a,draw(3.2f,false,true,null)),"Motion toggle retained stale shader state "+label);
         // Background plinths and the near plane remain pixel-identical even while the panorama moves.
         byte[] groundA=draw(3.2f,false,false,null),groundB=draw(6.8f,false,false,null);
         fixedRect(groundA,groundB,112,84,23,8,"enemy plinth "+label);
         fixedRect(groundA,groundB,15,45,49,10,"friendly plinth "+label);
         fixedRect(a,b,3,3,153,37,"command UI "+label);
         scenes++;System.out.println("BATTLE SCENERY scene="+label+" changedPixels="+changed+" cache=retained stage/UI=fixed reduced=exact");
      }
      void fixedRect(byte[] a,byte[] b,float x,float y,float w,float h,String label){
         float scale=Math.min(width()/160f,height()/144f),left=(width()-160*scale)/2,bottom=(height()-144*scale)/2;
         int count=changed(a,b,(int)Math.ceil(left+x*scale),(int)Math.ceil(bottom+y*scale),(int)Math.floor(w*scale)-1,(int)Math.floor(h*scale)-1);
         require(count==0,"Protected pixels moved "+label+": "+count);
      }
      int changed(byte[] a,byte[] b,int x,int y,int w,int h){
         int count=0;for(int row=y;row<y+h;row++)for(int col=x;col<x+w;col++){int i=(row*width()+col)*4;if(a[i]!=b[i]||a[i+1]!=b[i+1]||a[i+2]!=b[i+2])count++;}return count;
      }
      void lifecycle()throws Exception {
         BattleScenery scenery=(BattleScenery)field(JohtoBattleRenderer.class,"scenery").get(johtoBattleRenderer);
         ShaderProgram before=(ShaderProgram)field(BattleScenery.class,"shader").get(scenery);
         scenery.dispose();scenery.dispose();require(field(BattleScenery.class,"shader").get(scenery)==null,"Disposed shader retained");
         draw(3.2f,false,true,null);require(field(BattleScenery.class,"shader").get(scenery)!=before,"Disposed shader reused");
      }
      int width(){return Gdx.graphics.getBackBufferWidth();}int height(){return Gdx.graphics.getBackBufferHeight();}
      @Override public void dispose(){if(custom!=null)custom.dispose();super.dispose();disposed=true;}
   }
}
