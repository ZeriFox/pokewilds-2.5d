package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.game.util.SpriteProxy;
import com.pkmngen.leaks.LeakTracer;
import java.util.Arrays;

/** Exercises the exact built JAR, never overlays production classes. */
public final class PresentationReworkTest {
   private static final int WIDTH = Integer.getInteger("presentation.width",1280);
   private static final int HEIGHT = Integer.getInteger("presentation.height",720);
   public static void main(String[] args) {
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      Sample game=new Sample();
      Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
      config.setWindowedMode(WIDTH,HEIGHT);config.setInitialVisible(false);
      config.setDecorated(false);
      config.setForegroundFPS(30);config.useVsync(false);
      try {
         new Lwjgl3Application(game,config);
         require(game.complete,"Incomplete native presentation test");
         System.out.println("PRESENTATION REWORK PASS: full-viewport effects, targeted particle bounds, immutable effect source, native Stardew regions, diagonal PMD actors");
         System.exit(0);
      } catch(Throwable error) { error.printStackTrace();System.exit(1); }
   }
   private static void require(boolean value,String message) {if(!value)throw new IllegalStateException(message);}
   private static final class Sample extends Game {
      int phase;
      boolean complete;
      SpriteProxy effect;
      Pokemon own,enemy;
      final Matrix4 normalProjection=new Matrix4().setToOrtho2D(0,0,160,144);
      Sample(){super(new String[0],4);}
      @Override public void create() {
         super.create();actionStack.clear();Gdx.input.setInputProcessor(null);
         System.out.println("Requested viewport="+WIDTH+"x"+HEIGHT+" actual backbuffer="+Gdx.graphics.getBackBufferWidth()+"x"+Gdx.graphics.getBackBufferHeight());
         server=new Server();Network.register(server);
         map=new PkmnMap("presentation-rework-fixture");
         for(int y=-64;y<=64;y+=16)for(int x=-64;x<=64;x+=16){
            Vector2 pos=new Vector2(x,y);map.tiles.put(pos,new Tile("green1",pos.cpy(),true));
         }
         map.bottomLeft=new Vector2(-64,-64);map.topRight=new Vector2(64,64);
         map.minimap=new Pixmap(16,16,Pixmap.Format.RGBA8888);
         player.position.set(0,0);player.spawnLoc.set(0,0);player.name="PresentationTest";
         start();player.setCurrPokemon();actionStack.clear();
         own=new Pokemon("machop",10);player.currPokemon=own;
         enemy=new Pokemon("pikachu",10);battle.oppPokemon=enemy;
         own.backSprite.setPosition(16,48);enemy.sprite.setPosition(96,88);
         battle.drawAction=new DrawBattle(this);actionStack.add(battle.drawAction);
         player.dontDrawMapDuringBattle=true;
         Pixmap pixel=new Pixmap(8,8,Pixmap.Format.RGBA8888);pixel.setColor(Color.WHITE);pixel.fill();
         effect=new SpriteProxy(new Texture(pixel),0,0,8,8);pixel.dispose();
         effect.setColor(.12f,.82f,.25f,1);
         BwAssets assets=BwAssets.get();
         for(String key:new String[]{"grass","desert_ground","desert_rock","volcanic_rock","wall","wall_cap","chair","bed","ui_frlg_row","ui_frlg_leader"})
            require(assets.named(key)!=null,"Missing new region "+key);
         TextureRegion wall=assets.named("wall"),rock=assets.named("desert_rock");
         require(wall.getRegionHeight()==45,"Opaque wall face must exclude the sheet's three-pixel shadow fringe");
         require(rock.getRegionWidth()>16||rock.getRegionHeight()>16,"Rock silhouette was reduced to one tile");
         require(PmdPokemonSprites.get().frame(own,"up-right","Idle",0)!=null,"Missing friendly diagonal pose");
         require(PmdPokemonSprites.get().frame(enemy,"down-left","Idle",0)!=null,"Missing opponent diagonal pose");
      }
      void begin() {
         uiBatch.setProjectionMatrix(normalProjection);
         Gdx.gl.glViewport(0,0,WIDTH,HEIGHT);Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
         ScreenUtils.clear(.06f,.08f,.1f,1);
         johtoBattleRenderer.prepareFrame(this,false);
         uiBatch.setColor(Color.WHITE);uiBatch.begin();johtoBattleRenderer.drawBackground(this);
      }
      void finish(String name) {
         uiBatch.end();johtoBattleRenderer.finishFrame(this);
         require(Arrays.equals(normalProjection.val,uiBatch.getProjectionMatrix().val),"Battle projection leaked into world UI");
         Pixmap shot=ScreenUtils.getFrameBufferPixmap(0,0,WIDTH,HEIGHT);
         PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);
         try{png.write(Gdx.files.local(name),shot);}catch(java.io.IOException e){throw new RuntimeException(e);}
         finally{png.dispose();shot.dispose();}
         require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"OpenGL error");
      }
      @Override public void render() {
         switch(phase++) {
            case 0:
               begin();own.backSprite.draw(uiBatch);enemy.sprite.draw(uiBatch);finish("battle-depth-wide.png");break;
            case 1: {
               begin();effect.setBounds(0,0,160,144);
               float[] before=effect.getVertices().clone();long count=johtoBattleRenderer.getFullScreenEffectDraws();
               effect.draw(uiBatch);finish("effect-full-width.png");
               require(johtoBattleRenderer.getFullScreenEffectDraws()==count+1,"Large effect not expanded");
               require(Arrays.equals(before,effect.getVertices()),"Authored effect vertices mutated");
               Pixmap shot=ScreenUtils.getFrameBufferPixmap(0,0,WIDTH,HEIGHT);
               for(int x:new int[]{4,WIDTH/2,WIDTH-5}){
                  Color c=new Color(shot.getPixel(x,HEIGHT/2));
                  require(c.g>.7f&&c.r<.2f&&c.b<.35f,"Effect erased at viewport x="+x+" rgba="+c);
               }
               shot.dispose();break;
            }
            case 2: {
               begin();effect.setBounds(100,94,16,16);
               long count=johtoBattleRenderer.getFullScreenEffectDraws();
               float[] before=effect.getVertices().clone();effect.draw(uiBatch);finish("effect-targeted.png");
               require(johtoBattleRenderer.getFullScreenEffectDraws()==count,"Targeted effect was stretched");
               require(Arrays.equals(before,effect.getVertices()),"Targeted source mutated");break;
            }
            default:complete=true;Gdx.app.exit();
         }
      }
   }
}
