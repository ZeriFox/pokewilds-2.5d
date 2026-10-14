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
      int[] projectedPixels;boolean[] stablePixels;
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
         float activeYaw=johtoBattleRenderer.cameraYaw(this);
         animation.step(this);stepsThisRender++;uiBatch.flush();
         require(activeYaw==johtoBattleRenderer.cameraYaw(this),"Move step reset the active arena camera");
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
                  int matched=verifyTarget(before,after,path,scale,left,bottom,activeYaw);
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
      /** Independent CPU oracle: original PNG texels rasterized through the documented
       * 2.5D camera, then compared with the real GPU action after each window resize.
       * It deliberately never calls projectScenePoint/projectEffect/drawFrame. */
      int verifyTarget(Pixmap before,Pixmap after,String path,float scale,float left,float bottom,float yaw){
         Pixmap png=new Pixmap(Gdx.files.internal(path));int minX=160,maxX=-1,matched=0;
         int width=after.getWidth(),height=after.getHeight();
         if(projectedPixels==null||projectedPixels.length!=width*height){projectedPixels=new int[width*height];stablePixels=new boolean[width*height];}
         Arrays.fill(projectedPixels,0);Arrays.fill(stablePixels,false);
         try{
            for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)if((png.getPixel(x,y)&255)>0){minX=Math.min(minX,x);maxX=Math.max(maxX,x);}
            if(maxX>=minX)require(Math.min(maxX+1,enemy.sprite.getX()+enemy.sprite.getWidth())-Math.max(minX,enemy.sprite.getX())>10,
               "Authored effect does not overlap the actual enemy target");
            // The presentation contract uses twenty vertical strips on the original
            // 160x144 canvas; a software barycentric raster oracle checks actual PNG
            // registration, including triangle interpolation and nearest sampling.
            for(int column=0;column<20;column++){
               double x0=column*8,x1=x0+8;
               double[][] p={cameraPoint(x0,0,yaw,scale,left,bottom),cameraPoint(x0,144,yaw,scale,left,bottom),
                  cameraPoint(x1,144,yaw,scale,left,bottom),cameraPoint(x1,0,yaw,scale,left,bottom)};
               raster(png,p[0],p[1],p[2],x0,144,x0,0,x1,0,width,height,bottom+48*scale);
               raster(png,p[2],p[3],p[0],x1,0,x1,144,x0,144,width,height,bottom+48*scale);
            }
            for(int y=(int)Math.ceil(bottom+49*scale);y<height-2;y++)for(int x=2;x<width-2;x++){
               float worldX=(x+.5f-left)/scale,worldY=(y+.5f-bottom)/scale;
               // Only the two fixed top cards/shadows are redrawn by presentation.
               // Their opaque interiors are checked separately rather than exempted.
               boolean health=(worldX>=3&&worldX<=78||worldX>=83&&worldX<=158)&&worldY>=121&&worldY<=142;
               if(health){
                  if((worldX>=5&&worldX<=75||worldX>=85&&worldX<=155)&&worldY>=124&&worldY<=139)
                     require(before.getPixel(x,y)==after.getPixel(x,y),"Move changed opaque health HUD at "+x+","+y);
                  continue;
               }
               if(before.getPixel(x,y)!=after.getPixel(x,y)){
                  // One framebuffer pixel covers GPU edge rules and libGDX's
                  // trigonometric lookup-table rounding, never an expanded arena.
                  boolean supported=false;
                  for(int dy=-1;dy<=1&&!supported;dy++)for(int dx=-1;dx<=1;dx++)
                     if((projectedPixels[(y+dy)*width+x+dx]&255)>0){supported=true;break;}
                  require(supported,"Tackle effect outside projected PNG alpha mask: "+path+" "+x+","+y+" yaw="+yaw);
               }
               int index=y*width+x,rgba=projectedPixels[index];
               if(stablePixels[index]&&(rgba&255)==255&&before.getPixel(x,y)!=rgba){
                  require(after.getPixel(x,y)==rgba,"Projected Tackle PNG texel missed target after resize: "+path+" "+x+","+y+" yaw="+yaw);
                  matched++;
               }
            }
         }finally{png.dispose();}
         return matched;
      }
      static double[] cameraPoint(double x,double y,double yaw,double scale,double left,double bottom){
         double ground=48+40*Math.max(0,Math.min(1,(x-40)/84));
         double depth=(ground-70)/(.55-(ground-70)/260),wx=(x-80)*(1+depth/260)*.82,wz=depth*.82;
         double rx=Math.cos(yaw)*wx-Math.sin(yaw)*wz,rz=Math.sin(yaw)*wx+Math.cos(yaw)*wz;
         double perspective=300/(300+rz);
         return new double[]{left+(80+rx*perspective)*scale,bottom+(74+.32*rz*perspective+(y-ground)*perspective*.68)*scale};
      }
      void raster(Pixmap png,double[] a,double[] b,double[] c,double au,double av,double bu,double bv,double cu,double cv,int width,int height,double clipBottom){
         double denominator=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1]);
         if(Math.abs(denominator)<1e-9)return;
         int x0=Math.max(0,(int)Math.floor(Math.min(a[0],Math.min(b[0],c[0])))),x1=Math.min(width-1,(int)Math.ceil(Math.max(a[0],Math.max(b[0],c[0]))));
         int y0=Math.max((int)Math.ceil(clipBottom),(int)Math.floor(Math.min(a[1],Math.min(b[1],c[1])))),y1=Math.min(height-1,(int)Math.ceil(Math.max(a[1],Math.max(b[1],c[1]))));
         for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){
            double alpha=((b[1]-c[1])*(x+.5-c[0])+(c[0]-b[0])*(y+.5-c[1]))/denominator;
            double beta=((c[1]-a[1])*(x+.5-c[0])+(a[0]-c[0])*(y+.5-c[1]))/denominator,gamma=1-alpha-beta;
            if(alpha<0||beta<0||gamma<0)continue;
            double u=alpha*au+beta*bu+gamma*cu,v=alpha*av+beta*bv+gamma*cv;
            int px=Math.max(0,Math.min(159,(int)Math.floor(u))),py=Math.max(0,Math.min(143,(int)Math.floor(v))),rgba=png.getPixel(px,py);
            if((rgba&255)==0)continue;
            int index=y*width+x;projectedPixels[index]=rgba;
            // Exact-color checks are for texel interiors, away from nearest-sample
            // boundaries. The alpha-mask containment check still covers all edges.
            stablePixels[index]=u-Math.floor(u)>.2&&u-Math.floor(u)<.8&&v-Math.floor(v)>.2&&v-Math.floor(v)<.8;
         }
      }
      void save(Pixmap image,String filename){PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);try{png.write(Gdx.files.local(filename),image);}catch(java.io.IOException ex){throw new RuntimeException(ex);}finally{png.dispose();}}
   }
}
