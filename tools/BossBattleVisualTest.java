package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;

/** Executes the actual special draw/intro/effect actions on a wide native GL framebuffer. */
public final class BossBattleVisualTest {
   public static void main(String[] args){
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;TestGame game=new TestGame();
      Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
      config.setWindowedMode(1280,720);config.setInitialVisible(false);config.setForegroundFPS(60);config.useVsync(false);
      new Lwjgl3Application(game,config);
      if(!game.complete)throw new IllegalStateException("Boss visual test incomplete");
      System.out.println("BOSS VISUAL PASS captures="+game.captures+" mewtwoIntroFrames=641 gengarIntroFrames=391 regigigasSpecialFrames=201 originalRocksAndRipple=PASS cleanup=PASS");
   }
   static final class TestGame extends Game {
      int phase,captures;boolean complete;
      TestGame(){super(new String[0],4);}
      @Override public void create(){
         super.create();actionStack.clear();Gdx.input.setInputProcessor(null);
         server=new Server();Network.register(server);
         map=new PkmnMap("boss-visual-fixture");
         for(int y=-64;y<=64;y+=16)for(int x=-64;x<=64;x+=16){Vector2 pos=new Vector2(x,y);map.tiles.put(pos,new Tile("green1",pos.cpy(),true));}
         map.bottomLeft=new Vector2(-64,-64);map.topRight=new Vector2(64,64);map.minimap=new Pixmap(16,16,Pixmap.Format.RGBA8888);
         player.position.set(0,0);player.spawnLoc.set(0,0);player.name="BossTest";start();player.setCurrPokemon();actionStack.clear();
         player.currPokemon=new Pokemon("machop",15);player.currPokemon.backSprite.setPosition(16,48);
         freshMegaLoad();
      }
      void freshMegaLoad(){
         if(Specie.species.containsKey("mgengar"))throw new IllegalStateException("Fresh-process test already has a Mega definition");
         Pokemon normal=new Pokemon("gengar",70);int normalHp=normal.baseStats.get("hp");
         Network.PokemonDataBase data=new Network.PokemonDataBase(normal);data.name="mgengar";data.isShiny=true;
         Pokemon loaded=new Pokemon(data);
         if(!loaded.specie.name.equals("mgengar")||!loaded.dexNumber.equals(normal.dexNumber)||loaded.baseStats.get("hp")!=300
            ||!loaded.isShiny||loaded.hasEvo()||normal.baseStats.get("hp")!=normalHp||loaded.learnSet==normal.learnSet
            ||PmdPokemonSprites.get().frame(loaded,"down","Idle",0)==null)throw new IllegalStateException("Fresh Mega save load failed");
         new PlayMusic(loaded,null).music.dispose();
         Network.PokemonData roundTrip=new Network.PokemonData(loaded);Specie.species.remove("mgengar");
         Pokemon again=new Pokemon(roundTrip);
         if(!again.specie.name.equals("mgengar")||again.baseStats.get("hp")!=300||!again.isShiny)throw new IllegalStateException("Mega round trip failed");
         System.out.println("MEGA FRESH LOAD PASS noBossConstructor=true identity=mgengar cryDex="+again.dexNumber+" ordinaryGengarUnchanged=true");
      }
      @Override public void render(){
         switch(phase++){
            case 0:mewtwo();break;
            case 1:gengar();break;
            case 2:regigigas();break;
            default:complete=true;Gdx.app.exit();
         }
      }
      void reset(Pokemon enemy){
         actionStack.clear();uiBatch.setShader(null);uiBatch.setColor(Color.WHITE);
         battle.oppPokemon=enemy;enemy.sprite.setPosition(96,88);player.battleSprite.setPosition(-80,49);
         player.currPokemon.backSprite.setPosition(16,48);DrawBattle.shouldDrawOppPokemon=true;DrawBattle.hideOppPokemon=false;
         player.dontDrawMapDuringBattle=true;
      }
      void begin(){
         Gdx.gl.glViewport(0,0,1280,720);Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);ScreenUtils.clear(1,0,1,1);
         johtoBattleRenderer.prepareFrame(this,false);uiBatch.setColor(Color.WHITE);uiBatch.begin();
      }
      void end(String name){
         uiBatch.end();johtoBattleRenderer.finishFrame(this);
         if(Gdx.gl.glGetError()!=GL20.GL_NO_ERROR)throw new IllegalStateException("GL error "+name);
         if(name==null)return;
         Pixmap pixels=ScreenUtils.getFrameBufferPixmap(0,0,1280,720);
         for(int y=0;y<720;y+=2)for(int x=0;x<1280;x+=2)if(pixels.getPixel(x,y)>>>8==0xff00ff)throw new IllegalStateException("Uncovered viewport "+name);
         PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);
         try{png.write(Gdx.files.local(name),pixels);}catch(java.io.IOException ex){throw new RuntimeException(ex);}finally{png.dispose();pixels.dispose();}
         captures++;System.out.println("BOSS CAPTURE "+name);
      }
      void mewtwo(){
         SpecialMewtwo1 enemy=new SpecialMewtwo1(70,map.tiles.values().iterator().next());reset(enemy);
         SpecialBattleMewtwo encounter=new SpecialBattleMewtwo(this,enemy);
         SpecialBattleMewtwo.DrawBattle1 draw=encounter.new DrawBattle1(this);
         SpecialBattleMewtwo.DrawBreathingSprite breathing=new SpecialBattleMewtwo.DrawBreathingSprite(enemy);
         SpecialBattleMewtwo.RocksEffect2 backRocks=new SpecialBattleMewtwo.RocksEffect2();
         SpecialBattleMewtwo.IntroAnim intro=new SpecialBattleMewtwo.IntroAnim(this,null);
         actionStack.add(draw);actionStack.add(breathing);actionStack.add(intro);actionStack.add(backRocks);
         for(int frame=0;frame<641;frame++){
            begin();intro.step(this);backRocks.step(this);draw.step(this);breathing.step(this);
            end(frame==145||frame==493||frame==558||frame==640?"mewtwo-intro-"+frame+".png":null);
         }
         if(intro.timer!=641||!intro.moves_relative.isEmpty()||!SpecialBattleMewtwo.DrawBreathingSprite.shouldBreathe)
            throw new IllegalStateException("Mewtwo original intro state did not advance");
         SpecialBattleMewtwo.RocksEffect1 rocks=new SpecialBattleMewtwo.RocksEffect1();
         SpecialBattleMewtwo.RippleEffect1 ripple=encounter.new RippleEffect1();
         player.battleSprite.setX(-80);
         for(int frame=0;frame<60;frame++){
            begin();backRocks.step(this);draw.step(this);breathing.step(this);player.currPokemon.backSprite.draw(uiBatch);rocks.step(this);ripple.step(this);
            end(frame==10||frame==30||frame==59?"mewtwo-effects-"+frame+".png":null);
         }
         draw.cleanup(this);begin();breathing.step(this);backRocks.step(this);rocks.step(this);ripple.step(this);end(null);
         if(actionStack.contains(breathing)||actionStack.contains(backRocks))throw new IllegalStateException("Mewtwo effect cleanup failed");
      }
      void gengar(){
         SpecialMegaGengar1 enemy=new SpecialMegaGengar1(70);reset(enemy);
         if(PmdPokemonSprites.get().frame(enemy,"down","Idle",0)==null)throw new IllegalStateException("Exact Mega Gengar PMD missing");
         SpecialBattleMegaGengar encounter=new SpecialBattleMegaGengar(this);
         SpecialBattleMegaGengar.DrawBattle1 draw=encounter.new DrawBattle1(this);
         SpecialBattleMegaGengar.DrawBreathingSprite breathing=new SpecialBattleMegaGengar.DrawBreathingSprite(enemy);
         SpecialBattleMegaGengar.IntroAnim intro=new SpecialBattleMegaGengar.IntroAnim(this,null);
         actionStack.add(draw);actionStack.add(breathing);actionStack.add(intro);
         for(int frame=0;frame<391;frame++){
            begin();intro.step(this);breathing.step(this);draw.step(this);
            end(frame==145||frame==264||frame==390?"gengar-intro-"+frame+".png":null);
         }
         if(intro.timer!=247||!intro.moves_relative.isEmpty()||!SpecialBattleMegaGengar.DrawBreathingSprite.shouldBreathe)
            throw new IllegalStateException("Gengar original intro state did not advance");
         ThrowOutPokemon send=new ThrowOutPokemon(this,null);
         player.battleSprite.setX(-80);
         for(int frame=0;frame<110;frame++){
            begin();breathing.step(this);draw.step(this);send.step(this);end(frame==80||frame==109?"gengar-sendout-"+frame+".png":null);
         }
         if(!send.doneYet)throw new IllegalStateException("Gengar sendout did not complete");
         draw.cleanup(this);begin();breathing.step(this);end(null);
         if(actionStack.contains(breathing))throw new IllegalStateException("Gengar cleanup failed");
      }
      void regigigas(){
         reset(new Pokemon("regigigas",70));RegigigasBattle.Draw draw=new RegigigasBattle.Draw(this,null);draw.firstStep(this);
         actionStack.add(draw);draw.shouldFadeAlpha=true;draw.alsoDoShockwave=true;
         for(int frame=0;frame<80;frame++){
            begin();draw.step(this);player.currPokemon.backSprite.draw(uiBatch);end(frame==5||frame==40||frame==79?"regigigas-intro-"+frame+".png":null);
         }
         RegigigasBattle.SpecialAttack attack=new RegigigasBattle.SpecialAttack(null);actionStack.add(attack);
         for(int frame=0;frame<201;frame++){
            begin();attack.step(this);draw.step(this);player.currPokemon.backSprite.draw(uiBatch);
            end(frame==97||frame==116||frame==200?"regigigas-special-"+frame+".png":null);
         }
         if(attack.timer!=201||!RegigigasBattle.Draw.canMove||!RegigigasBattle.Draw.shouldBreathe)
            throw new IllegalStateException("Regigigas original special attack state did not advance");
         draw.cleanup(this);if(battle.drawAction!=null||player.dontDrawMapDuringBattle)throw new IllegalStateException("Regigigas cleanup failed");
      }
   }
}
