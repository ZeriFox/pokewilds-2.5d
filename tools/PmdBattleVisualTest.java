package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.game.util.SpriteProxy;
import com.pkmngen.game.util.ProxyBatch;
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
      int phase, captures, facingChecks;
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
         verifyTrainerTrimming();
         verifyCustomizedTrainer();
         verifyBattleDirections();
         verifyMissingShiny();
      }

      void verifyTrainerTrimming() {
         Pixmap firstPixels=new Pixmap(64,48,Pixmap.Format.RGBA8888),secondPixels=new Pixmap(64,48,Pixmap.Format.RGBA8888);
         firstPixels.setColor(Color.WHITE);firstPixels.fillRectangle(12,9,14,19);
         secondPixels.setColor(Color.WHITE);secondPixels.fillRectangle(22,14,23,7);
         Texture first=new Texture(firstPixels),second=new Texture(secondPixels);
         try {
            java.lang.reflect.Method trim=PmdBattleSprites.class.getDeclaredMethod("trimTrainer",TextureRegion.class);trim.setAccessible(true);
            TextureRegion firstSource=new TextureRegion(first,8,6,40,28),secondSource=new TextureRegion(second,8,6,40,28);
            TextureRegion firstTrim=(TextureRegion)trim.invoke(null,firstSource),secondTrim=(TextureRegion)trim.invoke(null,secondSource);
            assertCrop(firstTrim,first,12,9,14,19,"First rectangular custom avatar");
            assertCrop(secondTrim,second,22,14,23,7,"Same-coordinate avatar from a different texture");
            require(trim.invoke(null,firstSource)==firstTrim&&trim.invoke(null,secondSource)==secondTrim,
               "Trainer trim cache did not preserve independent texture identities");
            TextureRegion smaller=(TextureRegion)trim.invoke(null,new TextureRegion(first,8,6,14,14));
            assertCrop(smaller,first,12,9,10,11,"Same texture coordinates with a different crop size");
            TextureRegion empty=new TextureRegion(first,40,36,8,8);
            require(trim.invoke(null,empty)==empty,"Transparent custom avatar region did not preserve its original bounds");
            require(firstSource.getRegionWidth()==40&&firstSource.getRegionHeight()==28,
               "Trainer alpha trimming mutated the shared source region");
            System.out.println("PMD CUSTOM TRAINER PASS: actual source alpha, rectangular crops, independent texture/size cache and immutable source");
         }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
         finally{first.dispose();second.dispose();firstPixels.dispose();secondPixels.dispose();}
      }
      void assertCrop(TextureRegion region,Texture texture,int x,int y,int width,int height,String reason) {
         require(region.getTexture()==texture&&region.getRegionX()==x&&region.getRegionY()==y
            &&region.getRegionWidth()==width&&region.getRegionHeight()==height,reason+" read wrong alpha bounds or cache identity");
      }

      void verifyCustomizedTrainer() {
         String name=player.name,character=player.character;ProxyBatch original=uiBatch;
         Sprite before=new Sprite(player.battleSprite);CaptureBatch capture=new CaptureBatch();
         try {
            CharacterAppearance appearance=new CharacterAppearance();
            appearance.styles[0]=2;appearance.styles[1]=1;appearance.styles[3]=2;
            appearance.colors[0]=2;appearance.colors[1]=5;appearance.colors[3]=6;
            CharacterAppearance.save("Battle canary","gold",appearance);
            player.name="Battle canary";player.character="gold";
            TextureRegion expected=BwAssets.get().trainer(player,johtoBattleRenderer.actorFacing(this,true),0,false);
            java.lang.reflect.Method trim=PmdBattleSprites.class.getDeclaredMethod("trimTrainer",TextureRegion.class);trim.setAccessible(true);
            require(trim.invoke(null,expected)==expected,"Composed trainer did not preserve the complete normalized frame");
            uiBatch=capture;capture.reset();capture.begin();player.battleSprite.setBounds(16,48,48,48);
            require(PmdBattleSprites.drawKnown(player.battleSprite,capture),"Customized entry trainer fell back to old art");capture.end();
            assertFrame(capture,expected,"customized battle-entry trainer");
            System.out.println("PMD WARDROBE TRAINER PASS: released CPU Pixmap, complete normalized frame, actual battle-entry draw route and texture/UV identity");
         }catch(Exception e){throw new IllegalStateException(e);}
         finally{uiBatch=original;player.name=name;player.character=character;player.battleSprite.set(before);capture.dispose();}
      }

      void verifyBattleDirections() {
         ProxyBatch original=uiBatch;CaptureBatch capture=new CaptureBatch();uiBatch=capture;
         Pokemon originalOwn=player.currPokemon;
         try {
            Field time=PmdBattleSprites.class.getDeclaredField("seconds");time.setAccessible(true);
            for(String species:new String[]{"machop","pikachu","gyarados"}) {
               Pokemon pokemon=new Pokemon(species,20);
               for(boolean friendly:new boolean[]{true,false}) {
                  String direction=friendly?"up-right":"down-left";
                  Sprite proxy=new Sprite(friendly?pokemon.backSprite:pokemon.sprite);
                  float sourceSize=friendly?48:pokemon.specie.sprite.getWidth();proxy.setBounds(20,40,sourceSize,sourceSize);
                  float minX=0,minY=0,maxX=0,maxY=0,firstScale=-1;
                  String[] animations={"Idle","Walk","Attack","Hurt","Sleep"};
                  for(String animation:animations) {
                     PmdPokemonSprites.AnimationBounds b=PmdPokemonSprites.get().bounds(pokemon,direction,animation);
                     require(b!=null,"Missing diagonal bounds for "+species+"/"+direction+"/"+animation);
                     minX=Math.min(minX,b.minX);minY=Math.min(minY,b.minY);maxX=Math.max(maxX,b.maxX);maxY=Math.max(maxY,b.maxY);
                  }
                  for(String animation:animations)for(int tick:new int[]{0,7,19,43,79,119}) {
                     float seconds=tick/60f;time.setFloat(null,seconds);
                     PmdPokemonSprites.Frame expected=PmdPokemonSprites.get().frame(pokemon,direction,animation,seconds);
                     require(expected!=null,"Missing diagonal frame");capture.reset();capture.begin();
                     require(PmdBattleSprites.draw(this,pokemon,proxy,friendly,animation),"Modern battle actor was not drawn");capture.end();
                     assertFrame(capture,expected.region,species+"/"+direction+"/"+animation);
                     float width=capture.vertices[10]-capture.vertices[0],height=capture.vertices[6]-capture.vertices[1];
                     float scale=width/expected.width;
                     require(Math.abs(scale-height/expected.height)<.0001f,"Battle pose was stretched");
                     if(firstScale<0)firstScale=scale;
                     require(Math.abs(firstScale-scale)<.0001f,"Pose changed scale across the animation envelope");
                     require((maxX-minX)*scale<=56.01f&&(maxY-minY)*scale<=46.01f,"Diagonal animation exceeded battle slot size");
                     require(minX*scale>=-33.01f&&maxX*scale<=33.01f&&minY*scale>=-6.01f&&maxY*scale<=44.01f,
                        "Diagonal animation exceeded ground-anchored slot margins");
                     // The pose origin is stable; the opaque idle feet, rather than
                     // a transparent canvas origin, now meet the arena contact line.
                     PmdPokemonSprites.AnimationBounds idle=PmdPokemonSprites.get().bounds(pokemon,direction,"Idle");
                     float contactLift=-Math.min(0,idle.minY)*scale;
                     float actualOrigin=capture.vertices[1]+expected.anchorY*scale;
                     com.badlogic.gdx.math.Vector3 anchor=johtoBattleRenderer.projectActor(this,proxy.getX()+sourceSize/2,proxy.getY(),new com.badlogic.gdx.math.Vector3());
                     require(Math.abs(capture.vertices[0]+expected.anchorX*scale-anchor.x)<.001f
                        &&Math.abs(actualOrigin-anchor.y-contactLift)<.001f,"PMD contact origin moved across poses");
                     require(Math.abs(actualOrigin+Math.min(0,idle.minY)*scale-anchor.y)<.001f,
                        "Opaque idle contact missed the ground plane");
                     facingChecks++;
                  }
                  if(friendly) {
                     player.currPokemon=pokemon;time.setFloat(null,0);capture.reset();capture.begin();
                     require(PmdBattleSprites.sendOut(this,16,48,4),"Send-out fell back to classic art");capture.end();
                     assertFrame(capture,PmdPokemonSprites.get().frame(pokemon,"up-right","Idle",0).region,species+"/send-out");facingChecks++;
                  }
               }
            }
            System.out.println("PMD DIAGONAL PASS: "+facingChecks+" actual texture/UV draws; NE/SW animation envelopes, stable uniform scale, ground anchors and send-out");
         }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
         finally{player.currPokemon=originalOwn;uiBatch=original;capture.dispose();}
      }

      @SuppressWarnings("unchecked")
      void verifyMissingShiny(){
         ProxyBatch previous=uiBatch;CaptureBatch capture=new CaptureBatch();uiBatch=capture;
         java.util.Map<String,Object> entries=null;Object removed=null;
         try {
            Field catalog=PmdPokemonSprites.class.getDeclaredField("entries");catalog.setAccessible(true);
            entries=(java.util.Map<String,Object>)catalog.get(PmdPokemonSprites.get());removed=entries.remove("pikachu#shiny");
            Pokemon shiny=new Pokemon("pikachu",20);shiny.isShiny=true;
            Sprite source=new Sprite(shiny.specie.spriteShiny);source.setPosition(96,88);
            float[] original=source.getVertices().clone();
            require(PmdPokemonSprites.get().frame(shiny,"down-left","Idle",0)==null,"Absent exact shiny fixture still resolved PMD art");
            capture.reset();capture.begin();require(PmdBattleSprites.draw(this,shiny,source,false,"Idle"),"Missing PMD shiny escaped the orbit renderer");capture.end();
            require(capture.texture==source.getTexture(),"Missing PMD shiny borrowed an unrelated palette");
            require(Arrays.equals(original,source.getVertices()),"Fallback shiny mutated the gameplay sprite");
            com.badlogic.gdx.math.Vector3 anchor=johtoBattleRenderer.projectActor(this,source.getX()+source.getWidth()*.5f,88,new com.badlogic.gdx.math.Vector3());
            require(Math.abs((capture.vertices[0]+capture.vertices[10])*.5f-anchor.x)<.001f,"Shiny fallback detached from orbit");
            System.out.println("PMD SHINY FALLBACK PASS: absent exact sheet retains original art, projected anchor, sparkle draw route and immutable sprite");
         }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
         finally{if(entries!=null&&removed!=null)entries.put("pikachu#shiny",removed);uiBatch=previous;capture.dispose();}
      }

      void assertFrame(CaptureBatch capture,TextureRegion expected,String label) {
         require(capture.draws==1&&capture.texture==expected.getTexture(),"Wrong PMD texture/draw count "+label);
         float minU=Float.POSITIVE_INFINITY,minV=minU,maxU=Float.NEGATIVE_INFINITY,maxV=maxU;
         for(int i=0;i<20;i+=5){minU=Math.min(minU,capture.vertices[i+3]);maxU=Math.max(maxU,capture.vertices[i+3]);
            minV=Math.min(minV,capture.vertices[i+4]);maxV=Math.max(maxV,capture.vertices[i+4]);}
         require(Math.abs(minU-expected.getU())<.00001f&&Math.abs(maxU-expected.getU2())<.00001f
            &&Math.abs(minV-expected.getV())<.00001f&&Math.abs(maxV-expected.getV2())<.00001f,"Wrong rendered diagonal PMD row "+label);
      }
      void require(boolean result,String message){if(!result)throw new IllegalStateException(message);}

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
            case 18: {
               String name=player.name,character=player.character;Sprite before=new Sprite(player.battleSprite);
               try{player.name="Battle canary";player.character="gold";player.battleSprite.setBounds(16,48,48,48);
                  background();player.battleSprite.draw(uiBatch);enemy.sprite.draw(uiBatch);finish("19-customized-entry-trainer.png");}
               finally{player.name=name;player.character=character;player.battleSprite.set(before);}break;
            }
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
   static final class CaptureBatch extends ProxyBatch {
      int draws;Texture texture;float[] vertices;
      void reset(){draws=0;texture=null;vertices=null;}
      @Override public void draw(Texture texture,float[] vertices,int offset,int count) {
         this.texture=texture;this.vertices=Arrays.copyOfRange(vertices,offset,offset+count);draws++;
         super.draw(texture,vertices,offset,count);
      }
   }
}
