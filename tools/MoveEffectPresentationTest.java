package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.nio.IntBuffer;
import java.util.Arrays;

/** Native actual animation action + original move data from the delivered JAR. */
public final class MoveEffectPresentationTest {
   static final int W=Integer.getInteger("effects.width",1280),H=Integer.getInteger("effects.height",720);
   static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
   public static void main(String[] args) {
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      Sample game=new Sample();
      Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
      config.setWindowedMode(W,H);config.setInitialVisible(false);config.setDecorated(false);
      config.setForegroundFPS(0);config.useVsync(false);
      try {new Lwjgl3Application(game,config);require(game.complete,"Incomplete real move animation test");
         System.out.println("MOVE EFFECT PRESENTATION PASS: real Surf/Tackle/Rain Dance/Thunder; actor-only metadata; stable panorama; modern particles; local bounds; HUD; GL restoration; original frame progression; real Dig PMD shrink");System.exit(0);
      }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
   }
   static int[] gl(int name,int count){IntBuffer b=BufferUtils.newIntBuffer(count);Gdx.gl.glGetIntegerv(name,b);int[] a=new int[count];for(int i=0;i<count;i++)a[i]=b.get(i);return a;}
   static final class Sample extends Game {
      final String[] moves={"surf","tackle","rain dance","thunder","dig","water gun","teleport","double team","flamethrower","razor leaf","ice beam","shadow ball","rock throw","sludge"};
      final Matrix4 projection=new Matrix4().setToOrtho2D(0,0,160,144);
      int index,frames,wideChanges,localChanges,snapshotWidth,snapshotHeight;
      boolean complete,saved,hadInverse,identityChecked,quittingFixture;
      MoveEffectPresentation shutdownPresentation;
      Battle.LoadAndPlayAnimation animation;
      Pokemon own,enemy;
      Sample(){super(new String[0],4);}
      @Override public void create() {
         super.create();actionStack.clear();Gdx.input.setInputProcessor(null);Game.battleAnims=true;
         require(Gdx.graphics.getBackBufferWidth()==W&&Gdx.graphics.getBackBufferHeight()==H,"Requested native viewport unavailable");
         server=new Server();Network.register(server);map=new PkmnMap("move-effect-fixture");
         for(int y=-64;y<=64;y+=16)for(int x=-64;x<=64;x+=16){Vector2 p=new Vector2(x,y);map.tiles.put(p,new Tile("green1",p.cpy(),true));}
         map.bottomLeft=new Vector2(-64,-64);map.topRight=new Vector2(64,64);map.minimap=new Pixmap(16,16,Pixmap.Format.RGBA8888);
         player.position.set(0,0);player.spawnLoc.set(0,0);player.name="MoveEffectsTest";start();player.setCurrPokemon();actionStack.clear();
         own=new Pokemon("machop",10);enemy=new Pokemon("pikachu",10);player.currPokemon=own;battle.oppPokemon=enemy;
         own.backSprite.setPosition(16,48);enemy.sprite.setPosition(96,88);
         DrawBattle.shouldDrawOwnPokemon=DrawBattle.shouldDrawOppPokemon=true;DrawBattle.hideOwnPokemon=DrawBattle.hideOppPokemon=false;
         battle.drawAction=new DrawBattle(this);battle.drawAction.drawEnemyHealthAction=new DrawEnemyHealthGen2(this);
         battle.drawAction.drawFriendlyHealthAction=new DrawFriendlyHealthGen2(this);
         battle.drawAction.drawFriendlyHealthAction.currHealth=48;
         battle.drawAction.drawFriendlyHealthAction.currHealthRemaining=own.currentStats.get("hp");
         actionStack.add(battle.drawAction);player.dontDrawMapDuringBattle=true;
         require(MoveEffectPresentation.scopeFor("surf_enemy_gsc")==MoveEffectPresentation.Scope.SCREEN,"Enemy Surf scope");
         require(MoveEffectPresentation.scopeFor("thunder_player_gsc")==MoveEffectPresentation.Scope.LOCAL,"Local Thunder scope");
         next();
      }
      void next(){
         if(index==moves.length){quittingFixture=true;animation=new Battle.LoadAndPlayAnimation(this,"surf",enemy,null);actionStack.add(animation);return;}
         animation=new Battle.LoadAndPlayAnimation(this,moves[index],enemy,null);actionStack.add(animation);
         frames=wideChanges=localChanges=snapshotWidth=snapshotHeight=0;saved=hadInverse=false;
         own.backSprite.setPosition(16,48);enemy.sprite.setPosition(96,88);
      }
      @Override public void render() {
         if(complete)return;
         if(quittingFixture) {
            uiBatch.setProjectionMatrix(projection);uiBatch.setTransformMatrix(new Matrix4());
            johtoBattleRenderer.prepareFrame(this,false);uiBatch.begin();johtoBattleRenderer.drawBackground(this);
            animation.step(this);uiBatch.end();johtoBattleRenderer.finishFrame(this);
            require(animation.frameNum<60,"Mid-move exit fixture never created its real snapshot");
            if(animation.presentation!=null&&animation.presentation.getSnapshotWidth()>0) {
               shutdownPresentation=animation.presentation;complete=true;Gdx.app.exit();
            }
            return;
         }
         uiBatch.setProjectionMatrix(projection);uiBatch.setTransformMatrix(new Matrix4());
         Gdx.gl.glViewport(0,0,W,H);Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
         ScreenUtils.clear(.1f,.1f,.1f,1);johtoBattleRenderer.prepareFrame(this,false);
         uiBatch.begin();uiBatch.setColor(Color.WHITE);johtoBattleRenderer.drawBackground(this);
         own.backSprite.draw(uiBatch);enemy.sprite.draw(uiBatch);
         if(DrawFriendlyHealth.shouldDraw)johtoBattleRenderer.drawFriendlyHealth(this,battle.drawAction.drawFriendlyHealthAction);
         if(DrawEnemyHealth.shouldDraw)johtoBattleRenderer.drawEnemyHealth(this,battle.drawAction.drawEnemyHealthAction);
         modernUi.rect(this,-320,-288,800,335,new Color(.8f,.04f,.6f,1));
         uiBatch.flush();Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);Gdx.gl.glScissor(0,0,W,H);
         Pixmap before=ScreenUtils.getFrameBufferPixmap(0,0,W,H);
         if(!identityChecked){verifyOrientation(before);identityChecked=true;}
         int[] viewport=gl(GL20.GL_VIEWPORT,4),framebuffer=gl(GL20.GL_FRAMEBUFFER_BINDING,1);
         boolean scissor=Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
         int[] scissorBox=gl(GL20.GL_SCISSOR_BOX,4),depthMask=gl(GL20.GL_DEPTH_WRITEMASK,1);
         com.badlogic.gdx.graphics.glutils.ShaderProgram shader=uiBatch.getShader();
         float ownWidth=own.backSprite.getWidth(),enemyWidth=enemy.sprite.getWidth();
         int frame=animation.frameNum;
         boolean exists=Gdx.files.internal("attacks/"+animation.name+"/output/frame-"+String.format(java.util.Locale.ROOT,"%03d",frame)+".png").exists();
         animation.step(this);uiBatch.flush();
         require(animation.frameNum==frame+(exists?1:0),"Original action frame progression changed: "+animation.name+":"+frame);
         require(Arrays.equals(viewport,gl(GL20.GL_VIEWPORT,4)),"Viewport leaked from move snapshot");
         require(Arrays.equals(framebuffer,gl(GL20.GL_FRAMEBUFFER_BINDING,1)),"Framebuffer leaked from move snapshot");
         require(scissor==Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST),"Scissor enable leaked from move snapshot");
         require(Arrays.equals(scissorBox,gl(GL20.GL_SCISSOR_BOX,4)),"Scissor rectangle leaked");
         require(Arrays.equals(depthMask,gl(GL20.GL_DEPTH_WRITEMASK,1)),"Depth write mask leaked");
         require(shader==uiBatch.getShader(),"Move shader leaked");
         require(ownWidth==own.backSprite.getWidth()&&enemyWidth==enemy.sprite.getWidth(),"PMD effect changed authored actor dimensions");
         require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"OpenGL error in "+animation.name+":"+frame);
         Pixmap after=ScreenUtils.getFrameBufferPixmap(0,0,W,H);
         float scale=H/144f;int centerLeft=(int)((W-160*scale)/2),centerRight=W-centerLeft;
         int outside=0,inside=0;
         for(int y=(int)(49*scale);y<H-2;y+=7)for(int x=2;x<W-2;x+=7)
            if(before.getPixel(x,y)!=after.getPixel(x,y)){if(x<centerLeft||x>=centerRight)outside++;else inside++;}
         if(index>=5&&outside>0) {
            System.out.println("LOCAL MARGIN DIAGNOSTIC "+moves[index]+" frame="+frame+" changed="+outside);
            if(!Gdx.files.local("local-margin-before.png").exists()) {
               save(before,"local-margin-before.png");save(after,"local-margin-after.png");
               int printed=0;
               for(int y=(int)(49*scale);y<H-2&&printed<12;y+=7)for(int x=2;x<W-2&&printed<12;x+=7)
                  if((x<centerLeft||x>=centerRight)&&before.getPixel(x,y)!=after.getPixel(x,y)){
                     System.out.println("MARGIN "+x+","+y+" before="+Integer.toHexString(before.getPixel(x,y))+" after="+Integer.toHexString(after.getPixel(x,y)));printed++;
                  }
            }
         }
         wideChanges+=outside;localChanges+=inside;
         for(int x:new int[]{3,W/2,W-4})require(before.getPixel(x,(int)(20*scale))==after.getPixel(x,(int)(20*scale)),"Move distorted command/text UI");
         MoveEffectPresentation view=animation.presentation;
         if(view!=null){snapshotWidth=Math.max(snapshotWidth,view.getSnapshotWidth());snapshotHeight=Math.max(snapshotHeight,view.getSnapshotHeight());}
         String properties=animation.metadata.getOrDefault(frame,"");
         if(properties.contains("inverse_colors"))hadInverse=true;
         boolean useful=index==0&&properties.contains("row_copy:")&&outside>0||index==1&&inside>0||index==2&&outside>0||index==3&&properties.contains("inverse_colors")&&inside>0||index==4&&properties.contains("shrink:")||index>=5&&index<8&&(properties.contains("row_copy:")||properties.contains("row_displace:")||properties.contains("row_split:"))||index>=8&&inside>0&&frame>ModernMoveEffects.duration(animation.name)*.38f;
         if(!saved&&useful){save(after,moves[index].replace(' ','-')+"-real-frame-"+frame+".png");saved=true;}
         before.dispose();after.dispose();uiBatch.end();johtoBattleRenderer.finishFrame(this);
         require(Arrays.equals(projection.val,uiBatch.getProjectionMatrix().val),"Battle projection not restored");
         if(exists){frames++;return;}
         require(!actionStack.contains(animation),"Completed real animation not removed");
         require(frames>20&&saved,"Real move yielded no useful frames: "+moves[index]);
         if(index==0){require(wideChanges>0&&view.getRowFrames()>0,"Surf row copies did not reach expanded field");require(snapshotWidth==W&&snapshotHeight==H,"Surf snapshot cropped to legacy canvas");}
         if(index==1){require(wideChanges==0&&localChanges>0,"Tackle changed pixels outside local canvas");require(view.getLocalFrames()==frames&&view.getScreenFrames()==0,"Tackle scope changed");}
         if(index==2)require(wideChanges>0&&view.getScreenFrames()==frames,"Weather did not reach expanded field");
         if(index==3)require(hadInverse&&view.getColorFrames()>0&&wideChanges==0&&view.getModernFrames()==frames,"Thunder must pulse actors without inverting the panorama");
         if(index==4)require(view.getShrinkFrames()>0,"Dig never rendered PMD shrink metadata");
         if(index>=5&&index<8)require(view.getRowFrames()>0&&wideChanges==0&&view.getModernFrames()==frames,"Target-only modern effect leaked into expanded margins: "+moves[index]);
         if(index>=8)require(localChanges>0&&wideChanges==0&&view.getModernFrames()==frames,"Missing modern particle family or local effect escaped: "+moves[index]);
         require(view.getSnapshotWidth()==0,"Completed move leaked FBO");
         System.out.println("MOVE "+moves[index]+" frames="+frames+" expandedPixelChanges="+wideChanges+" rows="+view.getRowFrames()+" colors="+view.getColorFrames()+" snapshot="+snapshotWidth+"x"+snapshotHeight);
         index++;next();
      }
      @Override public void dispose() {
         super.dispose();
         if(complete){require(shutdownPresentation!=null&&shutdownPresentation.getSnapshotWidth()==0,"Window closure leaked an active move framebuffer");
            System.out.println("Mid-move window closure disposed active Surf snapshot");}
      }
      void verifyOrientation(Pixmap baseline) {
         MoveEffectPresentation identity=new MoveEffectPresentation("surf_player_gsc");
         identity.drawMetadata(this,animation,"screenshot:0,0,160,144 row_copy:60,60 inverse_colors");
         uiBatch.flush();Pixmap capturedShot;
         int previousFramebuffer=gl(GL20.GL_FRAMEBUFFER_BINDING,1)[0];
         try {
            java.lang.reflect.Field snapshotField=MoveEffectPresentation.class.getDeclaredField("snapshot");snapshotField.setAccessible(true);
            com.badlogic.gdx.graphics.glutils.FrameBuffer captured=(com.badlogic.gdx.graphics.glutils.FrameBuffer)snapshotField.get(identity);
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,captured.getFramebufferHandle());
            capturedShot=ScreenUtils.getFrameBufferPixmap(0,0,W,H);
         } catch(ReflectiveOperationException failure) {throw new RuntimeException(failure);}
         finally {Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,previousFramebuffer);}
         Pixmap after=ScreenUtils.getFrameBufferPixmap(0,0,W,H);
         int actorPixels=0,stableBackground=0,transparent=0;
         float scale=Math.min(W/160f,H/144f),left=(W-160*scale)*.5f,bottom=(H-144*scale)*.5f;
         for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int alpha=capturedShot.getPixel(x,y)&255;
            if(alpha>0){
               actorPixels++;
               require(x>left+80*scale,"Actor snapshot captured friendly Pokemon or panorama");
               require(y>bottom+75*scale&&y<bottom+130*scale,"Actor snapshot inverted or captured non-actor pixels");
            }else transparent++;
            // This independent mask allows only the documented <=1.2 logical
            // pixel horizontal afterimage, plus rasterization border texels.
            boolean masked=false;
            for(int dx=-(int)Math.ceil(2*scale);dx<=(int)Math.ceil(2*scale);dx++){
               int sx=x+dx;if(sx>=0&&sx<W&&(capturedShot.getPixel(sx,y)&255)>0){masked=true;break;}
            }
            if(!masked){require(baseline.getPixel(x,y)==after.getPixel(x,y),"Legacy metadata changed scenery/HUD at "+x+","+y);stableBackground++;}
         }
         require(actorPixels>50&&transparent>W*H*.9,"Target buffer is missing actor or contains environment: actor="+actorPixels+" transparent="+transparent);
         require(stableBackground>W*H*.9,"Insufficient unaffected scenery coverage");
         capturedShot.dispose();after.dispose();identity.dispose();
         MoveEffectPresentation explicit=new MoveEffectPresentation("water_gun_player_gsc");
         require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"Before explicit target test GL error");
         explicit.drawMetadata(this,animation,"row_displace:1 displace_target:player");uiBatch.flush();
         require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"Explicit target capture GL error");
         int binding=gl(GL20.GL_FRAMEBUFFER_BINDING,1)[0];Pixmap playerOnly;
         try{
            java.lang.reflect.Field f=MoveEffectPresentation.class.getDeclaredField("snapshot");f.setAccessible(true);
            com.badlogic.gdx.graphics.glutils.FrameBuffer fb=(com.badlogic.gdx.graphics.glutils.FrameBuffer)f.get(explicit);
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,fb.getFramebufferHandle());playerOnly=ScreenUtils.getFrameBufferPixmap(0,0,W,H);
         }catch(ReflectiveOperationException failure){throw new RuntimeException(failure);}
         finally{Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,binding);}
         int friendlyPixels=0;
         for(int y=0;y<H;y++)for(int x=0;x<W;x++)if((playerOnly.getPixel(x,y)&255)>0){
            require(x<left+80*scale,"Explicit player metadata selected enemy instead");friendlyPixels++;
         }
         require(friendlyPixels>50,"Explicit metadata lost friendly target");playerOnly.dispose();explicit.dispose();
         require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"Explicit target dispose GL error");
         // Restore exactly the frame used by the actual-action progression test.
         johtoBattleRenderer.drawBackground(this);own.backSprite.draw(uiBatch);enemy.sprite.draw(uiBatch);
         if(DrawFriendlyHealth.shouldDraw)johtoBattleRenderer.drawFriendlyHealth(this,battle.drawAction.drawFriendlyHealthAction);
         if(DrawEnemyHealth.shouldDraw)johtoBattleRenderer.drawEnemyHealth(this,battle.drawAction.drawEnemyHealthAction);
         modernUi.rect(this,-320,-288,800,335,new Color(.8f,.04f,.6f,1));uiBatch.flush();
         require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"Restored scene GL error");
         System.out.println("Actor-only snapshot PASS: visible="+actorPixels+" transparent="+transparent+" unchangedScenery="+stableBackground);
      }
      void save(Pixmap shot,String filename){PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);try{png.write(Gdx.files.local(filename),shot);}catch(java.io.IOException failure){throw new RuntimeException(failure);}finally{png.dispose();}}
   }
}
