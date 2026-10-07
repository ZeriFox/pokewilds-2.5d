package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.*;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import javax.imageio.ImageIO;

/** Geometry, framebuffer and original walking actions independently verify apparent relief. */
public final class ReliefVisualTest {
   static Throwable failure;
   static void require(boolean value,String why){if(!value)throw new IllegalStateException(why);}
   public static void main(String[] args) {
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      Sample game=new Sample();
      Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration();
      c.setInitialVisible(false);c.setWindowedMode(640,576);c.setForegroundFPS(120);c.useVsync(false);
      new Lwjgl3Application(game,c);
      if(failure!=null){failure.printStackTrace();System.exit(1);}
      require(game.complete,"Incomplete relief fixture");
      System.out.println("RELIEF VISUAL PASS: saved caldera terrain volume; closed/open plateau; original walking action; continuous camera and slope; actual rendered surfaces");
      System.exit(0);
   }
   static final class Sample extends Game {
      final ArrayList<String> defects=new ArrayList<>();
      int frame,phase,phaseFrame,moves,walkFrames,captures,walkDx,walkDy;
      long previous,fingerprint;
      boolean complete,walking,moveDone,middleCaptured;
      Vector2 walkStart;
      float previousHeight,previousCamera,maxHeightDelta,maxCameraDelta,startHeight,endHeight,minWalkHeight,maxWalkHeight;
      Sample(){super(new String[0],4);}
      JohtoRenderer renderer(){try{Field f=Game.class.getDeclaredField("johtoRenderer");f.setAccessible(true);return(JohtoRenderer)f.get(this);}catch(Exception e){throw new IllegalStateException(e);}}
      PerspectiveCamera camera(){try{Field f=JohtoRenderer.class.getDeclaredField("camera");f.setAccessible(true);return(PerspectiveCamera)f.get(renderer());}catch(Exception e){throw new IllegalStateException(e);}}
      void check(boolean ok,String reason){if(!ok){defects.add(reason);System.out.println("RELIEF DEFECT: "+reason);}}
      @Override public void create() {
         try {
            super.create();server=new Server();Network.register(server);actionStack.clear();
            map=new PkmnMap("smoke-world");start();map.loadFromFile(this);
            player.position.set(-48,-160);cam.position.set(-40,-152,0);player.acceptInput=false;
            actionStack.removeIf(a->a instanceof CycleDayNight||a instanceof DrawSetupMenu);
            map.timeOfDay="day";mapBatch.setColor(Color.WHITE);map.refreshCache=true;
         } catch(Throwable t){failure=t;Gdx.app.exit();}
      }
      @Override public void render() {
         if(failure!=null)return;
         try {
            super.render();frame++;phaseFrame++;
            require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"GL error at frame "+frame);
            long count=renderer().getRenderedFrames();require(count>previous,"Modern frame omitted "+frame);previous=count;
            if(phase==0 && phaseFrame==25){savedCaldera();screenshot("00-saved-caldera.png");
               actionStack.removeIf(a->a instanceof PlayerMoving||a instanceof PlayerStanding);
               player.position.set(32,-176);cam.position.set(48,-176,0);player.dirFacing="up";map.refreshCache=true;
               fingerprint=ModernWorldGenerator.fingerprint(map.tiles);phase=5;phaseFrame=0;
            }
            else if(phase==5 && phaseFrame==10){screenshot("00a-saved-ramp-entry.png");beginWalk(0,1);phase=6;phaseFrame=0;}
            else if(phase==6){walk("00b-saved");if(!walking){fixture(false);phase=1;phaseFrame=0;}}
            else if(phase==1 && phaseFrame==20){plateau("closed");screenshot("01-closed-ramp-entry.png");beginWalk(0,1);phase=2;phaseFrame=0;}
            else if(phase==2){walk("02-closed");if(!walking){fixture(true);phase=3;phaseFrame=0;}}
            else if(phase==3 && phaseFrame==20){plateau("open");screenshot("03-open-ramp-entry.png");beginWalk(0,1);phase=4;phaseFrame=0;}
            else if(phase==4){walk("04-open");if(!walking){
               player.position.set(96,96);cam.position.set(112,96,0);player.dirFacing="left";phase=7;phaseFrame=0;
            }}
            else if(phase==7&&phaseFrame==15){screenshot("05-opening-entry.png");beginWalk(-1,0);phase=8;phaseFrame=0;}
            else if(phase==8){walk("06-opening");if(!walking){
               require(((WorldBatch)mapBatch).getSubmittedDraws()==0,"Legacy world submitted draws");
               System.out.println("RELIEF counts: frames="+frame+" screenshots="+captures+" originalWalkFrames="+walkFrames);
               require(defects.isEmpty(),String.join("; ",defects));complete=true;Gdx.app.exit();
            }}
            require(frame<550,"Relief test timeout");
         }catch(Throwable t){failure=t;Gdx.app.exit();}
      }
      void savedCaldera()throws Exception {
         WorldElevation e=renderer().getElevation();int ledges=0,raised=0,ground=0,elevatedGround=0,ramps=0,slopes=0;
         ArrayList<Tile> tiles=new ArrayList<>(map.tiles.values());tiles.sort(Comparator.comparingDouble((Tile t)->t.position.y).thenComparingDouble(t->t.position.x));
         try(PrintWriter out=new PrintWriter("saved-caldera-heights.csv")) {
            out.println("x,y,ground,upper,ledgeDir,tileHeight,lowHeight,heightAtCenter");
            for(Tile t:tiles) {
               if(Math.abs(t.position.x+48)>128||Math.abs(t.position.y+160)>128)continue;
               out.println(t.position.x+","+t.position.y+","+t.name+","+t.nameUpper+","+t.ledgeDir+","+e.tileHeight(t)+","+e.lowHeight(t)+","+e.height(t.position.x+8,t.position.y+8));
               if(!BiomeProfiles.visualForTile(t).id.equals("volcano"))continue;
               if(t.nameUpper.startsWith("ledges3")&&t.ledgeDir!=null){ledges++;if(e.raised(t))raised++;}
               if(ModernWorldGenerator.isRamp(t)){ramps++;if(e.raised(t))slopes++;}
               else if(!t.isLedge&&!t.isLava&&!t.isWater&&t.nameUpper.isEmpty()){ground++;if(Math.abs(e.tileHeight(t))>=4)elevatedGround++;}
            }
         }
         System.out.println("RELIEF saved: supportedLedges="+raised+"/"+ledges+" elevatedGround="+elevatedGround+"/"+ground+" ramps="+slopes+"/"+ramps);
         check(ledges>10&&ground>20&&ramps>0,"Saved regression fixture has no caldera terrain evidence");
         check(raised>=ledges*.50f,"Saved caldera mostly has wall strips without a raised side: "+raised+"/"+ledges);
         check(elevatedGround>=12,"Saved caldera lacks a substantial real upper ground surface: "+elevatedGround);
         check(slopes>0,"Saved caldera's visible ramps are flat");
      }
      Tile put(String name,String upper,int x,int y) {
         Tile t=new Tile(name,upper,new Vector2(x,y),true);t.biome="volcano";map.tiles.put(t.position,t);return t;
      }
      void fixture(boolean opening) {
         actionStack.removeIf(a->a instanceof PlayerMoving||a instanceof PlayerStanding);
         map.overworldTiles=new HashMap<>();map.tiles=map.overworldTiles;map.onscreenPokemon.clear();map.pokemon.clear();
         for(int y=-128;y<=192;y+=16)for(int x=-160;x<=160;x+=16){
            String sides="";
            if(y>=0&&y<=112&&x>=-64&&x<=64){
               if(y==0)sides+="N";if(y==112)sides+="S";if(x==-64)sides+="E";if(x==64)sides+="W";
            }
            String name="volcano1",upper=sides.isEmpty()?"":"ledges3volcano_"+sides;
            if(y==0&&Math.abs(x)<=16){name="ledge_grass_ramp";upper="";}
            // One-cell opening beside a corner used to leave a long half-height strip.
            if(opening&&x==64&&y==96)upper="";
            put(name,upper,x,y);
         }
         player.position.set(0,-32);player.dirFacing="up";cam.position.set(16,-32,0);map.refreshCache=true;
         map.timeOfDay="day";mapBatch.setColor(Color.WHITE);fingerprint=ModernWorldGenerator.fingerprint(map.tiles);
      }
      void plateau(String name) {
         WorldElevation e=renderer().getElevation();float low=e.height(8,-24),high=e.height(8,72);
         System.out.println("RELIEF "+name+": lower="+low+" upper="+high);
         check(high-low>=8,name+" plateau does not lift its interior above the approach: "+low+" -> "+high);
         if(name.equals("open"))check(Math.abs(e.height(40,104)-high)<.01f,"Opening beside corner did not rejoin the full upper plane");
         for(int x=-16;x<=16;x+=16) {
            float last=e.height(x+8,-.01f),max=0;
            for(float y=0;y<=16;y+=.25f){float h=e.height(x+8,y);max=Math.max(max,Math.abs(h-last));last=h;}
            check(max<1.5f,name+" ramp has a discontinuous seam/step "+max);
            check(e.height(x+8,15.75f)-e.height(x+8,.25f)>=6,name+" ramp has no perceptible slope at x="+x);
         }
         // A real upward surface must be in the submitted mesh, not only returned by elevation.
         for(float y:new float[]{32,48,64}) {
            float actual=surfaceAt(8,y+8),expected=e.height(8,y+8);
            check(Float.isFinite(actual)&&Math.abs(actual-expected)<.3f,name+" rendered plateau does not match walkable height at "+y+": "+actual+" / "+expected);
         }
         checkUnsupportedWalls(name);
      }
      void checkUnsupportedWalls(String name) {
         int unsupported=0;WorldElevation e=renderer().getElevation();
         try {
            Field groups=JohtoRenderer.class.getDeclaredField("geometry");groups.setAccessible(true);
            for(Object mesh:((Map<?,?>)groups.get(renderer())).values()) {
               Field f=mesh.getClass().getDeclaredField("vertices");f.setAccessible(true);FloatArray a=(FloatArray)f.get(mesh);
               for(int i=0;i+23<a.size;i+=24) {
                  if(a.items[i+6]>3.5f)continue;
                  Vector3 v1=new Vector3(a.items[i],a.items[i+1],a.items[i+2]);
                  Vector3 v2=new Vector3(a.items[i+8],a.items[i+9],a.items[i+10]);
                  Vector3 v3=new Vector3(a.items[i+16],a.items[i+17],a.items[i+18]);
                  if(Math.abs(v2.cpy().sub(v1).crs(v3.cpy().sub(v1)).nor().y)>.1f)continue;
                  float high=Math.max(v1.y,Math.max(v2.y,v3.y)),supported=Float.NEGATIVE_INFINITY;
                  for(Vector3 v:new Vector3[]{v1,v2,v3})for(float dx:new float[]{-.1f,.1f})for(float dy:new float[]{-.1f,.1f})
                     supported=Math.max(supported,e.height(v.x+dx,-v.z+dy));
                  if(high>supported+.3f)unsupported++;
               }
            }
         }catch(Exception error){throw new IllegalStateException(error);}
         check(unsupported==0,name+" has "+unsupported+" vertical triangles rising above both actual adjacent surfaces");
         System.out.println("RELIEF unsupported walls "+name+"="+unsupported);
      }
      float surfaceAt(float x,float y) {
         float top=Float.NEGATIVE_INFINITY;
         try {
            Field groupsField=JohtoRenderer.class.getDeclaredField("geometry");groupsField.setAccessible(true);
            for(Object mesh:((Map<?,?>)groupsField.get(renderer())).values()) {
               Field f=mesh.getClass().getDeclaredField("vertices");f.setAccessible(true);FloatArray a=(FloatArray)f.get(mesh);
               for(int i=0;i+23<a.size;i+=24) {
                  if(a.items[i+6]>3.5f)continue;
                  Vector3 v1=new Vector3(a.items[i],a.items[i+1],a.items[i+2]);
                  Vector3 v2=new Vector3(a.items[i+8],a.items[i+9],a.items[i+10]);
                  Vector3 v3=new Vector3(a.items[i+16],a.items[i+17],a.items[i+18]),hit=new Vector3();
                  Vector3 normal=v2.cpy().sub(v1).crs(v3.cpy().sub(v1)).nor();
                  if(Math.abs(normal.y)<.1f)continue;
                  if(Intersector.intersectRayTriangle(new Ray(new Vector3(x,200,-y),new Vector3(0,-1,0)),v1,v2,v3,hit))top=Math.max(top,hit.y);
               }
            }
         }catch(Exception e){throw new IllegalStateException(e);}
         return top;
      }
      void beginWalk(int dx,int dy) {
         walkDx=dx;walkDy=dy;walkStart=player.position.cpy();middleCaptured=false;
         moves=0;walking=true;moveDone=true;maxHeightDelta=0;maxCameraDelta=0;
         startHeight=previousHeight=renderer().getElevation().height(player.position.x+8,player.position.y+8);previousCamera=camera().position.y;
         minWalkHeight=maxWalkHeight=startHeight;
      }
      void walk(String label)throws Exception {
         float h=renderer().getElevation().height(player.position.x+8,player.position.y+8),cy=camera().position.y;
         maxHeightDelta=Math.max(maxHeightDelta,Math.abs(h-previousHeight));maxCameraDelta=Math.max(maxCameraDelta,Math.abs(cy-previousCamera));
         minWalkHeight=Math.min(minWalkHeight,h);maxWalkHeight=Math.max(maxWalkHeight,h);
         previousHeight=h;previousCamera=cy;walkFrames++;
         if(!middleCaptured&&player.position.dst(walkStart)>=32){screenshot(label+"-ramp-middle.png");middleCaptured=true;}
         if(moveDone) {
            if(moves==5) {
               endHeight=h;screenshot(label+"-plateau-top.png");
               check(maxWalkHeight-minWalkHeight>=8,label+" actual walking action traversed no significant relief: "+minWalkHeight+".."+maxWalkHeight);
               check(maxHeightDelta<2,label+" feet teleport between height levels: "+maxHeightDelta);
               check(maxCameraDelta<2,label+" camera abruptly jumps vertically: "+maxCameraDelta);
               check(fingerprint==ModernWorldGenerator.fingerprint(map.tiles),label+" renderer/walking changed terrain data");
               System.out.println("RELIEF traversal "+label+": height="+startHeight+"->"+endHeight+" range="+minWalkHeight+".."+maxWalkHeight+" maxFootDelta="+maxHeightDelta+" maxCameraDelta="+maxCameraDelta+" tiles="+moves);
               walking=false;return;
            }
            Tile target=map.tiles.get(new Vector2(player.position.x+walkDx*16,player.position.y+walkDy*16));require(target!=null&&ModernWorldGenerator.isWalkable(target),"Fixture walking route is blocked: "+player.position);
            moveDone=false;moves++;playerCanMove=true;
            insertAction(new PlayerMoving(this,player,moves%2==0,new RunCode(()->moveDone=true,null)));
         }
      }
      void screenshot(String name)throws Exception {
         byte[] p=ScreenUtils.getFrameBufferPixels(0,0,640,576,false);BufferedImage image=new BufferedImage(640,576,BufferedImage.TYPE_INT_RGB);
         for(int y=0;y<576;y++)for(int x=0;x<640;x++){int i=(y*640+x)*4;image.setRGB(x,575-y,(p[i]&255)<<16|(p[i+1]&255)<<8|p[i+2]&255);}
         ImageIO.write(image,"png",new File(name));captures++;System.out.println("RELIEF image="+name);
         if(name.equals("01-closed-ramp-entry.png")||name.equals("03-open-ramp-entry.png"))legibility(image,name);
      }
      double luminance(BufferedImage image,float x,float y,float h) {
         Vector3 p=camera().project(new Vector3(x,h,-y),0,0,640,576);
         int px=Math.round(p.x),py=575-Math.round(p.y);
         if(px<1||px>=639||py<1||py>=575)return 0;
         double sum=0;
         for(int yy=py-1;yy<=py+1;yy++)for(int xx=px-1;xx<=px+1;xx++){
            int rgb=image.getRGB(xx,yy);sum+=.2126*(rgb>>16&255)+.7152*(rgb>>8&255)+.0722*(rgb&255);
         }
         return sum/9;
      }
      void legibility(BufferedImage image,String name) {
         WorldElevation e=renderer().getElevation();double top=0,face=0;
         float low=e.height(-24,-24),high=e.height(-24,40);
         for(float x=-40;x<=-24;x+=2){top+=luminance(image,x,32,high+.05f);face+=luminance(image,x,0,(low+high)*.5f);}
         top/=9;face/=9;
         int bands=0;boolean bright=false;double minimum=255,maximum=0;ArrayList<Double> samples=new ArrayList<>();
         for(float y=.5f;y<15.6f;y+=.15f){
            double luma=0;for(float x=-12;x<=-4;x+=4)luma+=luminance(image,x,y,e.height(x,y)+.12f);luma/=3;
            minimum=Math.min(minimum,luma);maximum=Math.max(maximum,luma);
            samples.add(luma);
         }
         ArrayList<Double> sorted=new ArrayList<>(samples);java.util.Collections.sort(sorted);
         double baseline=sorted.get(sorted.size()/3),threshold=baseline+18;
         for(double luma:samples){boolean next=luma>=threshold;if(next&&!bright)bands++;bright=next;}
         System.out.println("RELIEF pixels "+name+": topLuma="+top+" faceLuma="+face+" treadBands="+bands+" rampRange="+minimum+".."+maximum);
         if(high-low>=8){
            check(top-face>=10,"Upper surface does not visually separate from cliff face: "+(top-face));
            check(bands>=3,"Inclined ramp lacks three separately visible crosswise treads: "+bands);
         }
      }
   }
}
