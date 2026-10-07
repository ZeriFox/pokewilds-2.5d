package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Locale;

/** Native regression: the exact delivered Game and actual move actions. */
public final class ActiveMoveResizeTest {
   static final int[][] SIZES={{1280,720},{1024,768},{1600,600}};
   static void require(boolean yes,String message){if(!yes)throw new IllegalStateException(message);}
   static int[] gl(int key,int n){IntBuffer b=BufferUtils.newIntBuffer(n);Gdx.gl.glGetIntegerv(key,b);int[] a=new int[n];for(int i=0;i<n;i++)a[i]=b.get(i);return a;}
   public static void main(String[] args){
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;Sample game=new Sample();
      Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
      config.setWindowedMode(1280,720);config.setInitialVisible(false);config.setDecorated(false);
      config.setForegroundFPS(0);config.useVsync(false);config.setTitle("Active move resize regression");
      try{new Lwjgl3Application(game,config);require(game.complete,"Incomplete resize lifecycle");
         System.out.println("ACTIVE MOVE RESIZE PASS: Surf/Tackle, four in-flight window resizes, same actions/targets, contiguous clocks, true PNG target, HUD/GL/projection, original completion and disposed FBO");
         System.exit(0);
      }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
   }
   static final class Next extends Action {
      int steps;
      @Override public String getCamera(){return "gui";}
      @Override public Layer getLayer(){return Layer.gui_140;}
      @Override public void step(Game game){steps++;game.actionStack.remove(this);}
   }
   static final class Sample extends Game {
      final String[] moves={"surf","tackle"};
      final int[][] resizeFrames={{32,90},{31,39}};
      int moveIndex,sizeIndex,frames,resizeCallbacks,renderCalls,stepsThisRender,pendingFrames;
      int[] useful=new int[3],snapshots=new int[3],targetPixels=new int[3];
      boolean complete,starting=true,pendingResize,completing;
      Battle.LoadAndPlayAnimation animation,originalAction;
      MoveEffectPresentation originalPresentation;
      Pokemon own,enemy;
      Next next;
      Sample(){super(new String[0],4);}
      @Override public void create(){
         super.create();actionStack.clear();Gdx.input.setInputProcessor(null);Game.battleAnims=true;
         server=new Server();Network.register(server);map=new PkmnMap("active-resize-fixture");
         for(int y=-64;y<=64;y+=16)for(int x=-64;x<=64;x+=16){Vector2 p=new Vector2(x,y);map.tiles.put(p,new Tile("green1",p.cpy(),true));}
         map.bottomLeft=new Vector2(-64,-64);map.topRight=new Vector2(64,64);map.minimap=new Pixmap(16,16,Pixmap.Format.RGBA8888);
         player.position.set(0,0);player.spawnLoc.set(0,0);player.name="ActiveResize";start();player.setCurrPokemon();actionStack.clear();
         own=new Pokemon("machop",10);enemy=new Pokemon("pikachu",10);player.currPokemon=own;battle.oppPokemon=enemy;
         battle.drawAction=new DrawBattle(this);battle.drawAction.drawEnemyHealthAction=new DrawEnemyHealthGen2(this);
         battle.drawAction.drawFriendlyHealthAction=new DrawFriendlyHealthGen2(this);
         battle.drawAction.drawFriendlyHealthAction.currHealth=48;
         battle.drawAction.drawFriendlyHealthAction.currHealthRemaining=own.currentStats.get("hp");
         actionStack.add(battle.drawAction);player.dontDrawMapDuringBattle=true;beginMove();
      }
      void beginMove(){
         own.backSprite.setPosition(16,48);enemy.sprite.setPosition(96,88);
         DrawBattle.shouldDrawOwnPokemon=DrawBattle.shouldDrawOppPokemon=true;
         DrawBattle.hideOwnPokemon=DrawBattle.hideOppPokemon=false;
         DrawFriendlyHealth.shouldDraw=DrawEnemyHealth.shouldDraw=true;
         next=new Next();animation=new Battle.LoadAndPlayAnimation(this,moves[moveIndex],enemy,next);
         originalAction=animation;originalPresentation=null;actionStack.add(animation);
         frames=sizeIndex=0;useful=new int[3];snapshots=new int[3];targetPixels=new int[3];starting=true;pendingResize=false;
         Gdx.graphics.setWindowedMode(SIZES[0][0],SIZES[0][1]);
      }
      @Override public void resize(int width,int height){
         int before=animation==null?-1:animation.frameNum;Battle.LoadAndPlayAnimation same=animation;
         super.resize(width,height);resizeCallbacks++;
         require(same==animation&&(animation==null||animation.frameNum==before),"Resize callback advanced/replaced original action");
      }
      @Override public void render(){
         if(complete)return;renderCalls++;stepsThisRender=0;
         require(renderCalls<650,"Resize test stalled");
         if(completing){
            next.step(this);require(next.steps==1&&!actionStack.contains(next),"Next action not live once on following frame");completing=false;
            if(++moveIndex==moves.length){complete=true;Gdx.app.exit();}else beginMove();
            return;
         }
         int w=Gdx.graphics.getBackBufferWidth(),h=Gdx.graphics.getBackBufferHeight();
         if(starting){if(w!=1280||h!=720)return;starting=false;}
         if(pendingResize){
            require(++pendingFrames<10,"Window resize did not settle");
            if(w==SIZES[sizeIndex][0]&&h==SIZES[sizeIndex][1]){
               pendingResize=false;System.out.println("RESIZE APPLIED "+moves[moveIndex]+" action="+System.identityHashCode(animation)+" frame="+animation.frameNum+" size="+w+"x"+h);
            }
         }
         require(animation==originalAction&&animation.target==enemy&&player.currPokemon==own&&battle.oppPokemon==enemy,"Resize changed actor/action identity");
         require(actionStack.contains(animation),"Active action disappeared before original completion");
         Matrix4 originalProjection=new Matrix4(uiBatch.getProjectionMatrix());
         uiBatch.setTransformMatrix(new Matrix4());Gdx.gl.glViewport(0,0,w,h);
         Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);ScreenUtils.clear(.1f,.1f,.1f,1);
         johtoBattleRenderer.prepareFrame(this,false);uiBatch.begin();uiBatch.setColor(Color.WHITE);
         johtoBattleRenderer.drawBackground(this);own.backSprite.draw(uiBatch);enemy.sprite.draw(uiBatch);
         if(DrawFriendlyHealth.shouldDraw)johtoBattleRenderer.drawFriendlyHealth(this,battle.drawAction.drawFriendlyHealthAction);
         if(DrawEnemyHealth.shouldDraw)johtoBattleRenderer.drawEnemyHealth(this,battle.drawAction.drawEnemyHealthAction);
         // Deliberate fixture sentinel tests command-area integrity; not product UI.
         modernUi.rect(this,-320,-288,800,335,new Color(.8f,.04f,.6f,1));uiBatch.flush();
         Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);Gdx.gl.glScissor(0,0,w,h);
         Pixmap before=ScreenUtils.getFrameBufferPixmap(0,0,w,h);
         int[] viewport=gl(GL20.GL_VIEWPORT,4),fbo=gl(GL20.GL_FRAMEBUFFER_BINDING,1),box=gl(GL20.GL_SCISSOR_BOX,4),depthMask=gl(GL20.GL_DEPTH_WRITEMASK,1);
         ShaderProgram shader=uiBatch.getShader();Matrix4 transform=new Matrix4(uiBatch.getTransformMatrix());
         int frame=animation.frameNum;String path="attacks/"+animation.name+"/output/frame-"+String.format(Locale.ROOT,"%03d",frame)+".png";
         boolean exists=Gdx.files.internal(path).exists();float ownW=own.backSprite.getWidth(),enemyW=enemy.sprite.getWidth();
         animation.step(this);stepsThisRender++;uiBatch.flush();
         require(stepsThisRender==1&&animation.frameNum==frame+(exists?1:0),"Original move clock advanced more/less than once: "+frame);
         require(Arrays.equals(viewport,gl(GL20.GL_VIEWPORT,4))&&Arrays.equals(fbo,gl(GL20.GL_FRAMEBUFFER_BINDING,1)),"Viewport/FBO leaked");
         require(Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST)&&Arrays.equals(box,gl(GL20.GL_SCISSOR_BOX,4)),"Scissor leaked");
         require(Arrays.equals(depthMask,gl(GL20.GL_DEPTH_WRITEMASK,1))&&shader==uiBatch.getShader(),"Depth mask/shader leaked");
         require(Arrays.equals(transform.val,uiBatch.getTransformMatrix().val),"Move transform leaked");
         require(ownW==own.backSprite.getWidth()&&enemyW==enemy.sprite.getWidth(),"Resize changed source actor geometry");
         require(enemy.sprite.getX()==96&&enemy.sprite.getY()==88&&own.backSprite.getY()==48,"Resize moved original actor anchor");
         String metadata=animation.metadata.getOrDefault(frame,"");
         float expectedOwnX=16;
         if(metadata.contains("player_translate_x:"))expectedOwnX+=Integer.parseInt(metadata.split("player_translate_x:")[1].split(" ")[0]);
         require(own.backSprite.getX()==expectedOwnX,"Authored Tackle translation not preserved");
         require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"GL error after active resize");
         Pixmap after=ScreenUtils.getFrameBufferPixmap(0,0,w,h);
         float scale=Math.min(w/160f,h/144f),left=(w-160*scale)*.5f,bottom=(h-144*scale)*.5f;
         int changedOutside=0,changedInside=0;
         for(int y=(int)Math.ceil(bottom+49*scale);y<h-2;y+=3)for(int x=2;x<w-2;x+=3)
            if(before.getPixel(x,y)!=after.getPixel(x,y)){if(x<left||x>=w-left)changedOutside++;else changedInside++;}
         for(int y=3;y<bottom+46*scale;y+=11)for(int x=3;x<w-3;x+=31)
            require(before.getPixel(x,y)==after.getPixel(x,y),"Move distorted command HUD at "+x+","+y);
         MoveEffectPresentation presentation=animation.presentation;
         require(presentation!=null,"Actual move did not create presentation");
         if(originalPresentation==null)originalPresentation=presentation;
         require(presentation==originalPresentation,"Resize replaced presentation/action instance");
         if(exists&&!pendingResize){
            if(moveIndex==0&&metadata.contains("row_copy:")){
               require(presentation.getSnapshotWidth()==w&&presentation.getSnapshotHeight()==h,"Active Surf kept stale pre-resize FBO");snapshots[sizeIndex]++;
               if(changedOutside>0){
                  useful[sizeIndex]++;if(useful[sizeIndex]==1)save(after,"surf-resize-"+sizeIndex+"-frame-"+frame+"-"+w+"x"+h+".png");
               }
            }else if(moveIndex==1){
               require(changedOutside==0,"Tackle leaked into expanded field after resize");
               if(changedInside>0){
                  int matched=verifyTarget(before,after,path,scale,left,bottom);
                  targetPixels[sizeIndex]+=matched;
                  if(matched>0){useful[sizeIndex]++;if(useful[sizeIndex]==1)save(after,"tackle-resize-"+sizeIndex+"-frame-"+frame+"-"+w+"x"+h+".png");}
               }
            }
         }
         before.dispose();after.dispose();uiBatch.end();johtoBattleRenderer.finishFrame(this);
         require(Arrays.equals(originalProjection.val,uiBatch.getProjectionMatrix().val),"Battle projection not restored after resize");
         if(exists){
            frames++;require(frames==frame,"Original clock not contiguous");
            if(sizeIndex<2&&frame==resizeFrames[moveIndex][sizeIndex]){
               sizeIndex++;pendingFrames=0;pendingResize=true;int clock=animation.frameNum;
               Gdx.graphics.setWindowedMode(SIZES[sizeIndex][0],SIZES[sizeIndex][1]);
               require(animation==originalAction&&animation.frameNum==clock,"Request itself advanced action");
            }
            return;
         }
         require(sizeIndex==2&&!pendingResize,"Move finished before both resizes");
         require(!actionStack.contains(animation)&&actionStack.contains(next)&&next.steps==0,"Original completion/next action damaged");
         int nextCount=0;for(Action action:actionStack)if(action==next)nextCount++;require(nextCount==1,"Original next action duplicated");
         require(presentation.getSnapshotWidth()==0&&presentation.getSnapshotHeight()==0,"Completed resized move leaked FBO");
         for(int i=1;i<=2;i++)require(useful[i]>0,"No actual effect after resize "+i+" for "+moves[moveIndex]);
         if(moveIndex==0){for(int n:snapshots)require(n>0,"Missing pre/post resize Surf capture");require(presentation.getCaptureCount()>=3,"Surf FBO not recreated twice");}
         if(moveIndex==1)for(int i=1;i<=2;i++)require(targetPixels[i]>20,"Local effect missed actual enemy after resize");
         System.out.println("RESIZED MOVE "+moves[moveIndex]+" frames="+frames+" callbacks="+resizeCallbacks+" useful="+Arrays.toString(useful)+" captures="+presentation.getCaptureCount()+" targetPixels="+Arrays.toString(targetPixels)+" disposed=true next=queued-once");
         completing=true;
      }
      int verifyTarget(Pixmap before,Pixmap after,String path,float scale,float left,float bottom){
         Pixmap png=new Pixmap(Gdx.files.internal(path));int minX=160,minY=144,maxX=-1,maxY=-1,matched=0;
         try{
            for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)if((png.getPixel(x,y)&255)>0){minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
            if(maxX>=minX)require(Math.min(maxX+1,enemy.sprite.getX()+enemy.sprite.getWidth())-Math.max(minX,enemy.sprite.getX())>10,
               "Authored effect does not overlap the actual enemy target");
            int x0=(int)Math.floor(left+minX*scale)-1,x1=(int)Math.ceil(left+(maxX+1)*scale)+1;
            int y0=(int)Math.floor(bottom+(143-maxY)*scale)-1,y1=(int)Math.ceil(bottom+(144-minY)*scale)+1;
            for(int y=(int)Math.ceil(bottom+49*scale);y<after.getHeight()-2;y+=3)for(int x=2;x<after.getWidth()-2;x+=3)
               if(before.getPixel(x,y)!=after.getPixel(x,y)){
                  float worldX=(x+.5f-left)/scale,worldY=(y+.5f-bottom)/scale;
                  // MoveEffectPresentation intentionally redraws health panels;
                  // their translucent shadows are not authored attack pixels.
                  boolean health=worldX>=3&&worldX<=88&&worldY>=111&&worldY<=140
                     ||worldX>=81&&worldX<=159&&worldY>=45&&worldY<=83;
                  if(!health)require(maxX>=minX&&x>=x0&&x<=x1&&y>=y0&&y<=y1,"Tackle effect outside actual PNG target bounds: "+path+" "+x+","+y);
               }
            for(int y=minY;y<=maxY;y++)for(int x=minX;x<=maxX;x++){
               int rgba=png.getPixel(x,y);if((rgba&255)!=255)continue;
               int sx=(int)(left+(x+.5f)*scale),sy=(int)(bottom+(143-y+.5f)*scale);
               if(before.getPixel(sx,sy)!=rgba){require(after.getPixel(sx,sy)==rgba,"Tackle PNG no longer registered to enemy after resize");matched++;}
            }
         }finally{png.dispose();}
         return matched;
      }
      void save(Pixmap image,String filename){PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);try{png.write(Gdx.files.local(filename),image);}catch(java.io.IOException ex){throw new RuntimeException(ex);}finally{png.dispose();}}
   }
}
