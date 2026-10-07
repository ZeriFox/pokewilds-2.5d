package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.game.util.SpriteProxy;
import com.pkmngen.leaks.LeakTracer;
import java.lang.reflect.Field;
import java.util.Arrays;

/** Native isolated visual phases for PMD send-out, faint, shiny reveal and color effects. */
public final class PmdBattleVisualTest {
   public static void main(String[] args) {
      Game.leakTracer = LeakTracer.NoOp.INSTANCE;
      TestGame game = new TestGame();
      Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
      config.setWindowedMode(640, 576); config.setInitialVisible(false);
      config.setTitle("PMD battle transition visual test"); config.setForegroundFPS(60); config.useVsync(false);
      new Lwjgl3Application(game, config);
      if (!game.complete) throw new IllegalStateException("Visual test incomplete");
      System.out.println("PMD BATTLE VISUAL PASS captures=" + game.captures + " originalActions=EnemyFaint,FriendlyFaint,ThrowOutPokemonCrystal,EvolutionAnim,EggHatchAnim,PokemonFrame");
   }

   static final class TestGame extends Game {
      boolean complete;
      int phase, captures;
      Pokemon own, enemy;
      TestGame() { super(new String[0], 4); }

      @Override public void create() {
         super.create();
         actionStack.clear(); Gdx.input.setInputProcessor(null);
         server = new Server(); Network.register(server);
         map = new PkmnMap("pmd-visual-fixture");
         for (int y = -64; y <= 64; y += 16) for (int x = -64; x <= 64; x += 16) {
            Vector2 position = new Vector2(x,y); map.tiles.put(position, new Tile("green1", position.cpy(), true));
         }
         map.bottomLeft = new Vector2(-64,-64); map.topRight = new Vector2(64,64);
         map.minimap = new Pixmap(16,16,Pixmap.Format.RGBA8888);
         player.position.set(0,0); player.spawnLoc.set(0,0); player.name = "VisualTest";
         start(); player.setCurrPokemon(); actionStack.clear();
         own = new Pokemon("machop", 10); player.currPokemon = own;
         enemy = new Pokemon("pikachu", 10); battle.oppPokemon = enemy;
         own.backSprite.setPosition(16,48); enemy.sprite.setPosition(96,88);
         battle.drawAction = new DrawBattle(this);
         currMusic = com.pkmngen.game.util.audio.AudioLoader.loadMusic("sounds/evolve_fanfare1.ogg");
      }

      @Override public void render() {
         try {
            Field time = PmdBattleSprites.class.getDeclaredField("seconds"); time.setAccessible(true); time.setFloat(null,0f);
         } catch (ReflectiveOperationException ex) { throw new RuntimeException(ex); }
         uiBatch.setProjectionMatrix(uiBatch.getProjectionMatrix().setToOrtho2D(0,0,160,144));
         Gdx.gl.glViewport(0,0,640,576); Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
         PmdPokemonSprites.get().beginFrame();
         switch (phase++) {
            case 0:
               background(); own.backSprite.draw(uiBatch); enemy.sprite.draw(uiBatch); finish("01-battle-actors.png"); break;
            case 1:
               background(); PmdBattleSprites.sendOut(this,16,48,7); finish("02-sendout-quarter.png"); break;
            case 2:
               background(); PmdBattleSprites.sendOut(this,16,48,6); finish("03-sendout-half.png"); break;
            case 3:
               background(); PmdBattleSprites.sendOut(this,16,48,4); finish("04-sendout-full.png"); break;
            case 4: {
               ThrowOutPokemonCrystal action = new ThrowOutPokemonCrystal(this,null); action.firstStep(this);
               for (int n = 0; n < 48; n++) { background(); action.step(this); uiBatch.end(); }
               background(); action.step(this); finish("05-original-sendout-action.png"); break;
            }
            case 5: {
               FriendlyFaint action = new FriendlyFaint(this,null);
               for (int n = 0; n < 29; n++) { background(); action.step(this); uiBatch.end(); }
               background(); action.step(this); finish("06-friendly-faint.png"); break;
            }
            case 6: {
               EnemyFaint action = new EnemyFaint(this,null);
               for (int n = 0; n < 29; n++) { background(); action.step(this); uiBatch.end(); }
               background(); action.step(this); finish("07-enemy-faint.png"); break;
            }
            case 7:
               enemy.isShiny = false; background(); new PokemonFrame(enemy,null).step(this); finish("08-reveal-normal.png"); break;
            case 8:
               enemy.isShiny = true; background(); new PokemonFrame(enemy,null).step(this); finish("09-reveal-shiny.png"); break;
            case 9: {
               EvolutionAnim action = new EvolutionAnim(enemy,"raichu",null); action.firstStep(this);
               EvolutionAnim.drawSprite = true; EvolutionAnim.drawPostEvoTop = false;
               background(); action.step(this); finish("10-evolution-shiny-before.png"); break;
            }
            case 10: {
               EvolutionAnim action = new EvolutionAnim(enemy,"raichu",null); action.firstStep(this);
               EvolutionAnim.drawSprite = true; EvolutionAnim.drawPostEvoTop = true;
               background(); action.step(this); finish("11-evolution-shiny-after.png"); break;
            }
            case 11: {
               EggHatchAnim action = new EggHatchAnim(enemy,null); EggHatchAnim.drawSprite = true;
               EggHatchAnim.drawPostHatchTop = false; EggHatchAnim.drawPostHatchBottom = false; EggHatchAnim.isDone = false;
               background(); action.step(this); finish("12-hatch-shiny-reveal.png"); break;
            }
            case 12:
               enemy.isShiny = false; background(); enemy.sprite.draw(uiBatch); finish("13-effect-normal.png"); break;
            case 13:
               enemy.sprite.lightenColors2 = true; background(); enemy.sprite.draw(uiBatch); finish("14-effect-lighten.png"); enemy.sprite.lightenColors2 = false; break;
            case 14:
               enemy.sprite.setAlpha(.3f); background(); enemy.sprite.draw(uiBatch); finish("15-effect-alpha.png"); enemy.sprite.setAlpha(1f); break;
            case 15:
               background(); enemy.sprite.setScale(.5f); enemy.sprite.draw(uiBatch); finish("16-effect-scale.png"); enemy.sprite.setScale(1f); break;
            case 16:
               enemy=new Pokemon("gastly",21);battle.oppPokemon=enemy;enemy.spookify();enemy.sprite.setPosition(96,88);
               background();
               if(!PmdBattleSprites.draw(this,enemy,enemy.sprite,false,"Idle"))throw new IllegalStateException("Mystery ghost used old art");
               finish("17-night-mystery.png");
               if(!enemy.isGhost)throw new IllegalStateException("Presentation revealed hidden species");break;
            case 17:
               enemy.revealGhost();enemy.sprite.setPosition(96,88);background();enemy.sprite.draw(uiBatch);finish("18-night-revealed.png");break;
            default:
               verifyDifferent("08-reveal-normal.png","09-reveal-shiny.png","Shiny reveal rendered normal appearance");
               verifyDifferent("13-effect-normal.png","14-effect-lighten.png","Lighten battle effect was ignored");
               verifyDifferent("13-effect-normal.png","15-effect-alpha.png","Sprite alpha was ignored");
               verifyDifferent("17-night-mystery.png","18-night-revealed.png","Silph Scope did not reveal the actual species");
               complete = true; Gdx.app.exit();
         }
         if (Gdx.gl.glGetError() != GL20.GL_NO_ERROR) throw new IllegalStateException("OpenGL error in visual phase " + phase);
      }

      void background() {
         ScreenUtils.clear(.1f,.1f,.1f,1);
         uiBatch.setColor(Color.WHITE); uiBatch.begin(); johtoBattleRenderer.drawBackground(this);
      }
      void finish(String file) {
         uiBatch.end();
         Pixmap pixels = ScreenUtils.getFrameBufferPixmap(0,0,640,576);
         PixmapIO.PNG png = new PixmapIO.PNG(); png.setFlipY(true);
         try { png.write(Gdx.files.local(file), pixels); }
         catch (java.io.IOException ex) { throw new RuntimeException(ex); }
         finally { png.dispose(); pixels.dispose(); }
         captures++; System.out.println("PMD VISUAL " + file);
      }
      void verifyDifferent(String a, String b, String reason) {
         if (Arrays.equals(Gdx.files.local(a).readBytes(),Gdx.files.local(b).readBytes())) throw new IllegalStateException(reason);
      }
   }
}
