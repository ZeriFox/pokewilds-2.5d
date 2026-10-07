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
         System.out.println("MOVE EFFECT PRESENTATION PASS: real Surf/Tackle/Rain Dance/Thunder; full viewport rows; local bounds; HUD; GL restoration; original frame progression; real Dig PMD shrink; local Water Gun/Teleport/Double Team row operations");System.exit(0);
      }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
   }
   static int[] gl(int name,int count){IntBuffer b=BufferUtils.newIntBuffer(count);Gdx.gl.glGetIntegerv(name,b);int[] a=new int[count];for(int i=0;i<count;i++)a[i]=b.get(i);return a;}
   static final class Sample extends Game {
      final String[] moves={"surf","tackle","rain dance","thunder","dig","water gun","teleport","double team"};
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
         boolean useful=index==0&&properties.contains("row_copy:")&&outside>0||index==1&&inside>0||index==2&&outside>0||index==3&&properties.contains("inverse_colors")&&outside>0||index==4&&properties.contains("shrink:")||index>=5&&(properties.contains("row_copy:")||properties.contains("row_displace:")||properties.contains("row_split:"));
         if(!saved&&useful){save(after,moves[index].replace(' ','-')+"-real-frame-"+frame+".png");saved=true;}
         before.dispose();after.dispose();uiBatch.end();johtoBattleRenderer.finishFrame(this);
         require(Arrays.equals(projection.val,uiBatch.getProjectionMatrix().val),"Battle projection not restored");
         if(exists){frames++;return;}
         require(!actionStack.contains(animation),"Completed real animation not removed");
         require(frames>20&&saved,"Real move yielded no useful frames: "+moves[index]);
         if(index==0){require(wideChanges>0&&view.getRowFrames()>0,"Surf row copies did not reach expanded field");require(snapshotWidth==W&&snapshotHeight==H,"Surf snapshot cropped to legacy canvas");}
         if(index==1){require(wideChanges==0&&localChanges>0,"Tackle changed pixels outside local canvas");require(view.getLocalFrames()==frames&&view.getScreenFrames()==0,"Tackle scope changed");}
         if(index==2)require(wideChanges>0&&view.getScreenFrames()==frames,"Weather did not reach expanded field");
         if(index==3)require(hadInverse&&view.getColorFrames()>0&&wideChanges>0,"Real Thunder global flash metadata not applied");
         if(index==4)require(view.getShrinkFrames()>0,"Dig never rendered PMD shrink metadata");
         if(index>=5)require(view.getRowFrames()>0&&wideChanges==0,"Target-only row effect leaked into expanded margins: "+moves[index]);
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
         identity.drawMetadata(this,animation,"screenshot:0,0,160,144 row_copy:60,60");
         com.badlogic.gdx.graphics.g2d.Sprite empty=new com.badlogic.gdx.graphics.g2d.Sprite(
            com.pkmngen.game.util.TextureCache.get(Gdx.files.internal("attacks/surf_player_gsc/output/frame-001.png")));
         Pixmap transparent=new Pixmap(Gdx.files.internal("attacks/surf_player_gsc/output/frame-001.png"));
         for(int y=0;y<transparent.getHeight();y++)for(int x=0;x<transparent.getWidth();x++)
            require((transparent.getPixel(x,y)&255)==0,"Identity test needs the actual transparent intro frame");
         transparent.dispose();identity.drawFrame(this,empty,"");uiBatch.flush();
         Pixmap identityShot=ScreenUtils.getFrameBufferPixmap(0,0,W,H);
         int comparisons=0;
         // Actor animation is not advanced; compare asymmetric arena margins away from panels.
         for(int y=H/2;y<H-2;y+=11)for(int x:new int[]{3,21,W-22,W-4}) {
            require(baseline.getPixel(x,y)==identityShot.getPixel(x,y),"Snapshot orientation/identity changed arena pixel "+x+","+y);
            comparisons++;
         }
         identityShot.dispose();identity.dispose();
         // Compare expanded actual Surf image against normal Sprite geometry, including UV orientation.
         johtoBattleRenderer.drawBackground(this);uiBatch.flush();
         com.badlogic.gdx.graphics.g2d.Sprite original=new com.badlogic.gdx.graphics.g2d.Sprite(
            com.pkmngen.game.util.TextureCache.get(Gdx.files.internal("attacks/surf_player_gsc/output/frame-013.png")));
         com.badlogic.gdx.graphics.g2d.Sprite expected=new com.badlogic.gdx.graphics.g2d.Sprite(original);
         float width=144f*W/H;
         expected.setBounds((160-width)*.5f,0,width,144);expected.draw(uiBatch);uiBatch.flush();
         Pixmap expectedShot=ScreenUtils.getFrameBufferPixmap(0,0,W,H);
         johtoBattleRenderer.drawBackground(this);
         MoveEffectPresentation global=new MoveEffectPresentation("surf_player_gsc");global.drawFrame(this,original,"");uiBatch.flush();
         Pixmap actualShot=ScreenUtils.getFrameBufferPixmap(0,0,W,H);
         for(int y=(int)(49*H/144f);y<H-2;y+=3)for(int x:new int[]{3,21,W-22,W-4})
            require(expectedShot.getPixel(x,y)==actualShot.getPixel(x,y),"Actual screen PNG was inverted at "+x+","+y);
         expectedShot.dispose();actualShot.dispose();global.dispose();
         // Restore the same frame for the real action and its before/after comparison.
         johtoBattleRenderer.drawBackground(this);own.backSprite.draw(uiBatch);enemy.sprite.draw(uiBatch);
         if(DrawFriendlyHealth.shouldDraw)johtoBattleRenderer.drawFriendlyHealth(this,battle.drawAction.drawFriendlyHealthAction);
         if(DrawEnemyHealth.shouldDraw)johtoBattleRenderer.drawEnemyHealth(this,battle.drawAction.drawEnemyHealthAction);
         modernUi.rect(this,-320,-288,800,335,new Color(.8f,.04f,.6f,1));uiBatch.flush();
         System.out.println("Snapshot and real PNG orientation PASS ("+comparisons+" arena samples)");
      }
      void save(Pixmap shot,String filename){PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);try{png.write(Gdx.files.local(filename),shot);}catch(java.io.IOException failure){throw new RuntimeException(failure);}finally{png.dispose();}}
   }
}
