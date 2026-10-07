package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.math.Vector2;
import com.pkmngen.leaks.LeakTracer;
import java.util.List;

/** Runs the original night trigger and identity/reveal mechanism against the built JAR. */
public final class GhostDiagnosticTest {
   static Throwable failure;
   static void require(boolean value,String reason){if(!value)throw new IllegalStateException(reason);}
   public static void main(String[] args){
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
      config.setInitialVisible(false);config.setWindowedMode(320,288);
      new Lwjgl3Application(new Sample(),config);
      if(failure!=null){failure.printStackTrace();System.exit(1);}
      System.out.println("GHOST DIAGNOSTIC PASS: original day/night biome trigger matrix, campfire/movement exclusions, real concealed species and exact reveal identity");
      System.exit(0);
   }
   static final class Sample extends Game {
      Sample(){super(new String[0],2);}
      @Override public void create(){
         try{
            super.create();actionStack.clear();Game.rand.setSeed(90217);
            map=new PkmnMap("ghost-diagnostic");map.tiles=map.overworldTiles;map.currRoute=new Route("forest1",10);
            for(int y=-64;y<=64;y+=16)for(int x=-64;x<=64;x+=16){Tile tile=new Tile("green1",new Vector2(x,y),true);map.tiles.put(tile.position,tile);}
            player.position.set(0,0);player.isSleeping=false;player.nearCacturne=false;
            for(String biome:List.of("graveyard","deep_forest","wooded_lake","desert","grassland"))
               for(String time:List.of("day","night")){
                  boolean expected=time.equals("night")&&!biome.equals("desert")&&!biome.equals("grassland");
                  trigger(biome,time,false,true,expected);
               }
            trigger("graveyard","night",true,true,false);
            trigger("deep_forest","night",false,false,false);
            require(Gdx.files.internal("ghost_sheet1.png").exists()&&Gdx.files.internal("ghost_spawn1.png").exists(),"Intentional legacy spirit sheets are missing");
            Pokemon ordinary=new Pokemon("gastly",21);
            require(!ordinary.isGhost&&PmdPokemonSprites.get().frame(ordinary,"down","Idle",0)!=null,"Ordinary Gastly was mistaken for a concealed ghost or lost PMD art");
            DrawGhost scripted=new DrawGhost(this,new Vector2(48,32),true);
            require(scripted.pokemon.isGhost&&scripted.pokemon.specie!=null,"Scripted ghost did not conceal a real species");
            require(List.of("litwick","lampent","chandelure","mimikyu","misdreavus","sableye","gastly","haunter","gengar").contains(scripted.pokemon.specie.name),"Scripted ghost changed its authored species pool");
            require(PmdPokemonSprites.get().frame(scripted.pokemon,"down","Idle",0)==null,"Concealed identity leaked via PMD ordinary actor lookup");
            System.out.println("GHOST identity="+scripted.pokemon.specie.name+" isGhost="+scripted.pokemon.isGhost+" requested=ghost_sheet1.png fallback=false layer="+scripted.getLayer());
            Pokemon exact=scripted.pokemon;String identity=exact.specie.name;
            require(exact.revealGhost()==exact&&!exact.isGhost&&exact.specie.name.equals(identity),"Reveal replaced or lost the real Pokémon identity");
            require(exact.sprite==exact.specie.sprite||exact.sprite==exact.specie.spriteShiny,"Reveal did not restore the exact species sprite");
         }catch(Throwable error){failure=error;}
         Gdx.app.exit();
      }
      void trigger(String biome,String time,boolean campfire,boolean canMove,boolean expected){
         actionStack.clear();map.currBiome=biome;map.timeOfDay=time;player.isNearCampfire=campfire;playerCanMove=canMove;
         CycleDayNight cycle=new CycleDayNight(this);cycle.countDownToGhost=1;cycle.rand.setSeed(314);CycleDayNight.dayTimer=10000;
         mapBatch.begin();uiBatch.begin();try{cycle.step(this);}finally{uiBatch.end();mapBatch.end();}
         long ghosts=actionStack.stream().filter(a->a instanceof SpawnGhost).count();
         require(ghosts==(expected?1:0),"Unexpected ghost trigger biome="+biome+" time="+time+" campfire="+campfire+" movable="+canMove);
         System.out.println("GHOST trigger biome="+biome+" time="+time+" campfire="+campfire+" movable="+canMove+" spawns="+ghosts);
      }
      @Override public void render(){}
   }
}
