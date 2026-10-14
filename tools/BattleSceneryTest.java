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
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
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
            +", four aspects, shared floor/actor orbit, directional sprites, fixed UI, reduced motion, cache/disposal, batch state, gameplay/RNG unchanged");
         System.exit(0);
      }catch(Throwable e){e.printStackTrace();System.exit(1);}
   }
   static final class Probe extends Game {
      final int[][] sizes={{960,540},{640,576},{420,840},{1440,400}};
      final String[] biomes={"forest","beach","ocean","snow","desert","volcano","graveyard","cave"};
      int boot,sizeIndex,settle,scenes,captures;boolean requested,complete,disposed,ready;
      Pokemon own,enemy;ShaderProgram custom;DrawBattleMenuNormal menu;
      DrawFriendlyHealthGen2 friendlyHealth;DrawEnemyHealthGen2 enemyHealth;
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
         menu=new DrawBattleMenuNormal(this,null);actionStack.add(menu);
         battle.drawAction=new DrawBattle(this);
         friendlyHealth=new DrawFriendlyHealthGen2(this);friendlyHealth.firstStep(this);enemyHealth=new DrawEnemyHealthGen2(this);
         custom=SpriteBatch.createDefaultShader();require(custom.isCompiled(),"Control shader failed");ready=true;
         modernUi.desktop.clock=2f;
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
            largeActors();
            actorOcclusion();
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
         if(actors){own.backSprite.draw(uiBatch);enemy.sprite.draw(uiBatch);
            johtoBattleRenderer.drawFriendlyHealth(this,friendlyHealth);johtoBattleRenderer.drawEnemyHealth(this,enemyHealth);
            johtoBattleRenderer.drawCommandMenu(this,"tl",false);}
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
         // Settle the independently animated UI selection before comparing
         // fixed scenery clocks. Reduced motion snaps that selection to its
         // target; otherwise an initial .32 reveal is compared to settled 1.
         draw(0,true,true,null);
         byte[] a=draw(3.2f,false,true,sizeIndex==0||biome.equals("forest")?label+"-a":null);
         Texture cached=(Texture)field(JohtoBattleRenderer.class,"arena").get(johtoBattleRenderer);
         byte[] near=draw(3.22f,false,true,null),b=draw(24f,false,true,sizeIndex==0||biome.equals("forest")?label+"-b":null);
         require(cached==field(JohtoBattleRenderer.class,"arena").get(johtoBattleRenderer),"Per-frame arena texture allocation");
         int changed=changed(a,b,0,0,width(),height());require(changed>width()*height()/200,"Static panorama "+label+": "+changed);
         require(changed(a,near,0,0,width(),height())>20,"Shader lacks continuous movement "+label);
         require(changed(a,b,0,height()*4/5,Math.max(1,width()/8),height()/10)>5,
            "Surrounding panorama stayed fixed while only arena center rotated "+label);
         require(Arrays.equals(a,draw(3.2f,false,true,null)),"Same clock changed pixels "+label);
         byte[] stillA=draw(3.2f,true,true,sizeIndex==0||biome.equals("forest")?label+"-still":null),stillB=draw(80f,true,true,null);
         require(Arrays.equals(stillA,stillB),"Reduced motion still animates "+label);
         require(Arrays.equals(a,draw(3.2f,false,true,null)),"Motion toggle retained stale shader state "+label);
         fixedRect(a,b,6,6,148,32,"command UI "+label);
         cameraContract();
         scenes++;System.out.println("BATTLE SCENERY scene="+label+" changedPixels="+changed+" cache=retained projectedActors=coherent UI=fixed reduced=exact");
      }
      void cameraContract()throws Exception {
         Vector3 neutral=BattleScenery.project(40,48,0,new Vector3());
         require(neutral.x>30&&neutral.x<60&&neutral.y>48,"Neutral actor placement outside stage");
         java.util.Set<String> facings=new java.util.HashSet<>();
         for(int step=0;step<=720;step++) {
            float yaw=step*MathUtils.PI2/720f;
            Vector3 a=BattleScenery.project(40,48,yaw,new Vector3());
            Vector3 b=BattleScenery.project(124,88,yaw,new Vector3());
            require(a.y>48&&a.x>8&&a.x<152&&b.x>8&&b.x<152&&b.y>48,"Actor leaves safe stage at "+step+": "+a+" / "+b);
            Vector3 periodic=BattleScenery.project(40,48,yaw+MathUtils.PI2,new Vector3());
            require(a.dst(periodic)<.05f,"Orbit seam changes actor ground anchor");
            Vector3 effect=BattleScenery.projectEffect(40,48,yaw,new Vector3());
            require(effect.dst(a)<.001f,"Friendly particle ground detached from actor");
            effect=BattleScenery.projectEffect(124,88,yaw,new Vector3());
            require(effect.dst(b)<.001f,"Enemy particle ground detached from actor");
            facings.add(BattleScenery.facing(true,yaw));
         }
         require(facings.size()==8,"Full orbit did not expose all eight sprite directions");
         require(Math.abs(BattleScenery.orbit(BattleScenery.ORBIT_PERIOD)-MathUtils.PI2)<.0001f,"Orbit does not complete 360 degrees");
         require(BattleScenery.project(40,48,MathUtils.PI,new Vector3()).x>80,"Half orbit did not swap actor sides");
         float active=johtoBattleRenderer.cameraYaw(this);require(active!=0,"Menu orbit disabled");
         menu.disabled=true;require(johtoBattleRenderer.cameraYaw(this)==active,"Disabled menu resets camera");menu.disabled=false;
         actionStack.remove(menu);require(johtoBattleRenderer.cameraYaw(this)==active,"Active turn resets camera");actionStack.add(menu);
      }
      void largeActors()throws Exception {
         Pokemon savedOwn=own,savedEnemy=enemy;map.currBiome="forest";
         own=new Pokemon("exeggutor",25);enemy=new Pokemon("exeggutor",30);player.currPokemon=own;battle.oppPokemon=enemy;
         own.backSprite.setPosition(16,48);enemy.sprite.setPosition(96,88);
         friendlyHealth=new DrawFriendlyHealthGen2(this);friendlyHealth.firstStep(this);enemyHealth=new DrawEnemyHealthGen2(this);
         draw(5.2374f,false,true,sizeIndex+"-forest-exeggutor-orbit-max");
         draw(24.8723f,false,true,sizeIndex+"-forest-exeggutor-orbit-min");
         own=savedOwn;enemy=savedEnemy;player.currPokemon=own;battle.oppPokemon=enemy;
         friendlyHealth=new DrawFriendlyHealthGen2(this);friendlyHealth.firstStep(this);enemyHealth=new DrawEnemyHealthGen2(this);
      }
      void actorOcclusion()throws Exception {
         float x=enemy.sprite.getX(),y=enemy.sprite.getY();
         enemy.sprite.setPosition(14,52);
         byte[][] orders=new byte[2][];
         for(int order=0;order<2;order++){
            draw(0,false,false,null);johtoBattleRenderer.prepareFrame(this,false);
            field(JohtoBattleRenderer.class,"scenerySeconds").setFloat(johtoBattleRenderer,0);
            uiBatch.begin();johtoBattleRenderer.drawBackground(this);
            boolean depth=Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST);
            if(order==0){own.backSprite.draw(uiBatch);enemy.sprite.draw(uiBatch);}
            else {enemy.sprite.draw(uiBatch);own.backSprite.draw(uiBatch);}
            require(depth==Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST),"Actor depth state leaked to UI");
            uiBatch.end();johtoBattleRenderer.finishFrame(this);
            orders[order]=ScreenUtils.getFrameBufferPixels(0,0,width(),height(),false);
         }
         // Fractional floor shadows are intentionally blended; opaque sprite bodies
         // above y=68 must be identical regardless of legacy GUI action order.
         float scale=Math.min(width()/160f,height()/144f),bottom=(height()-144*scale)/2;
         int y0=(int)Math.ceil(bottom+68*scale),top=(int)Math.floor(bottom+120*scale);
         require(changed(orders[0],orders[1],0,y0,width(),top-y0)==0,"Overlapping actors depend on legacy draw order");
         enemy.sprite.setPosition(x,y);
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
         byte[] previousFrame=draw(24f,false,true,null);
         actionStack.remove(menu);
         byte[] cut=draw(24f,false,true,null);
         require(changed(previousFrame,cut,0,0,width(),height())==0,"Turn changed camera or introduced a dissolve");
         byte[] moving=draw(25f,false,true,null);
         require(!Arrays.equals(cut,moving),"Camera stopped while turn executes");
         actionStack.add(menu);draw(24f,true,true,null);
         require(johtoBattleRenderer.cameraYaw(this)==0,"Reduced motion retained orbit");
         BattleScenery scenery=(BattleScenery)field(JohtoBattleRenderer.class,"scenery").get(johtoBattleRenderer);
         ShaderProgram before=(ShaderProgram)field(BattleScenery.class,"shader").get(scenery);
         scenery.dispose();scenery.dispose();require(field(BattleScenery.class,"shader").get(scenery)==null,"Disposed shader retained");
         draw(3.2f,false,true,null);require(field(BattleScenery.class,"shader").get(scenery)!=before,"Disposed shader reused");
      }
      int width(){return Gdx.graphics.getBackBufferWidth();}int height(){return Gdx.graphics.getBackBufferHeight();}
      @Override public void dispose(){if(custom!=null)custom.dispose();super.dispose();disposed=true;}
   }
}
