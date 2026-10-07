package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.*;
import javax.imageio.ImageIO;

/** Actual Game.render + original field-menu activation and movement, isolated save directory. */
public final class FieldSceneTest {
   public static void main(String[] args) {
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      Sample game=new Sample();
      Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
      cfg.setWindowedMode(640,576);cfg.setInitialVisible(false);cfg.setForegroundFPS(60);cfg.setIdleFPS(60);cfg.useVsync(false);
      try {new Lwjgl3Application(game,cfg);if(!game.complete)throw new IllegalStateException("Incomplete field test");
         System.out.println("FIELD PASS: exclusive modern world; POWER/BUILD/DIG/SURF/FLY/RIDE activation; real BUILD placement/collision/resources, DIG excavation/refill/resources, cancellation without consumption; Surf/Ride movement and FLY takeoff/key movement/RMB landing; CUT/HEADBUTT/plant clocks and next actions; original couch enter/exit, bed sleep/wake, kiln enter/cancel, filtered-empty fishing and rod-restricted catch into battle; furniture geometry/projection and previews/light/pose snapshots");System.exit(0);
      }catch(Throwable ex){ex.printStackTrace();System.exit(1);}
   }
   private static final class Sample extends Game {
      int frame;boolean ready,complete;long previous,previousCompositor;Pokemon electric,water,bird;
      boolean leftMouse,rightMouse,rightKey;
      Input fixtureInput;
      CutTreeAnim cut;HeadbuttTreeAnim headbutt;PlantTree plant;
      Tile actionTile;long effectsBefore;int cutNext,headbuttNext,plantNext;
      int lifecycle=-1,lifecycleStart,furnitureSettledFrames;boolean fishingSeen;Pokemon opponentBeforeFishing;
      ArrayList<String> seaPoolBefore;Random randomBeforeFishing;
      String caughtSpecies;boolean castScreenshot;int fishingBattleMenuFrames;
      Map<String,Integer> constructionCosts,constructionBefore,cancelInventory;
      Tile constructionTarget;int grassBefore;Vector2 flightStart;
      Sample(){super(new String[0],4);}
      @Override public void insertAction(Action action){
         // The production launcher passes an exact Game. This harness subclasses
         // it; retain the original reflected Game signature rather than Sample.
         if(action instanceof CallMethod){CallMethod call=(CallMethod)action;
            for(int i=0;i<call.params.length;i++)if(call.params[i]==this&&call.paramTypes[i]==Sample.class)call.paramTypes[i]=Game.class;}
         super.insertAction(action);
      }
      void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
      JohtoRenderer renderer(){try{java.lang.reflect.Field f=Game.class.getDeclaredField("johtoRenderer");f.setAccessible(true);return (JohtoRenderer)f.get(this);}catch(Exception e){throw new IllegalStateException(e);}}
      void fixture(){
         Input nativeInput=Gdx.input;
         fixtureInput=(Input)Proxy.newProxyInstance(Input.class.getClassLoader(),new Class<?>[]{Input.class},(p,m,a)->{
            if(m.getName().startsWith("isButton"))return (Integer)a[0]==Input.Buttons.LEFT?leftMouse:(Integer)a[0]==Input.Buttons.RIGHT&&rightMouse;
            if(m.getName().startsWith("isKey"))return a!=null&&a.length>0&&(Integer)a[0]==InputProcessor.keyboardRight&&rightKey;
            if(m.getName().equals("isTouched"))return false;
            return m.invoke(nativeInput,a);
         });
         Gdx.input=fixtureInput;
         actionStack.removeIf(a->a instanceof DrawSetupMenu);server=new Server();Network.register(server);
         map=new PkmnMap("field-fixture");Game.rand.setSeed(77129);map.rand.setSeed(77129);
         for(int y=-320;y<=320;y+=16)for(int x=-320;x<=320;x+=16){
            Tile tile=new Tile(x>=16?"water1":"green1",new Vector2(x,y),true);tile.biome="grassland";map.tiles.put(tile.position,tile);
         }
         map.bottomLeft=new Vector2(-320,-320);map.topRight=new Vector2(320,320);
         map.minimap=new Pixmap(80,80,Pixmap.Format.RGBA8888);map.minimap.setColor(.3f,.6f,.3f,1);map.minimap.fill();
         player.position.set(0,0);player.spawnLoc.set(0,0);player.dirFacing="left";player.name="Field";cam.position.set(8,8,0);
         start();player.setCurrPokemon();actionStack.removeIf(a->a instanceof CycleDayNight);
         electric=new Pokemon("pikachu",20);water=new Pokemon("lapras",20);bird=new Pokemon("pidgeot",20);
         electric.hms.add("FLASH");player.pokemon.add(electric);player.pokemon.add(water);player.pokemon.add(bird);
         ready=true;
      }
      void use(String move,Pokemon pokemon){
         DrawPokemonMenu menu=new DrawPokemonMenu(this,(Menu)null);
         DrawPokemonMenu.allPokemon=player.pokemon;DrawPokemonMenu.currIndex=player.pokemon.indexOf(pokemon);DrawPokemonMenu.scrollIndex=0;
         DrawPokemonMenu.SelectedMenu selected=new DrawPokemonMenu.SelectedMenu(menu,pokemon);
         Action next=selected.getAction(this,move,menu);
         require(next!=null && move.equals(player.currFieldMove),"Original menu activation failed: "+move);
         // Activation may restore the parent menu pending its normal dialogue outro.
         // This renderer fixture skips dialogue only, then drives the original movement.
         actionStack.removeIf(a->a instanceof Menu);
         System.out.println("FIELD activated "+move+" with "+pokemon.specie.name);
      }
      void stop(){
         if(player.hmPokemon!=null){
            if(!player.currFieldMove.isEmpty() && !player.currFieldMove.equals("FLY"))player.swapSprites(player.hmPokemon);
            player.hmPokemon.removeDrawActions(this);
         }
         actionStack.removeIf(a->a.getClass().getName().contains("$Follow")||a.getClass().getName().contains("$Flying")||a instanceof PlayerMoving);
         player.hmPokemon=null;player.currFieldMove="";player.flyingAction=null;player.acceptInput=true;
         playerCanMove=true;
         player.isFishing=false;player.isSleeping=false;player.isSitting=false;player.isCrafting=false;
         actionStack.removeIf(a->a instanceof PlayerStanding);
         insertAction(new PlayerStanding(this));
      }
      @Override public void render(){
         // LWJGL restores its own Input on every window context switch.
         if(fixtureInput!=null)Gdx.input=fixtureInput;
         Map<Action,Integer> clocks=new IdentityHashMap<>();
         if(ready)for(Action action:actionStack) {
            if(action instanceof CutTreeAnim)clocks.put(action,((CutTreeAnim)action).timer);
            else if(action instanceof HeadbuttTreeAnim)clocks.put(action,((HeadbuttTreeAnim)action).index);
            else if(action instanceof PlantTree)clocks.put(action,((PlantTree)action).timer);
            else if(action instanceof Player.CaughtFishAnim)clocks.put(action,((Player.CaughtFishAnim)action).timer);
         }
         super.render();frame++;
         for(Map.Entry<Action,Integer> clock:clocks.entrySet()) {
            Action action=clock.getKey();int now=action instanceof CutTreeAnim?((CutTreeAnim)action).timer:
               action instanceof HeadbuttTreeAnim?((HeadbuttTreeAnim)action).index:
               action instanceof PlantTree?((PlantTree)action).timer:((Player.CaughtFishAnim)action).timer;
            require(now==clock.getValue()+1,"Field action advanced other than once: "+action.getClass().getSimpleName());
         }
         require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"GL error at "+frame);
         if(frame==20)fixture();
         if(!ready)return;
         WorldBatch batch=(WorldBatch)mapBatch;
         require(batch.getSubmittedDraws()==0,"Legacy world submitted pixels at "+frame);
         if(frame>22){long now=renderer().getRenderedFrames();long compositor=johtoBattleRenderer.getBattleFrames()+johtoBattleRenderer.getTransitionFrames()+johtoBattleRenderer.getSnapshotFrames();
            require(now>previous||player.dontDrawMapDuringBattle&&compositor>previousCompositor,"World fallback at "+frame+" move="+player.currFieldMove);previous=now;previousCompositor=compositor;}
         if(frame==50)use("POWER",electric);
         if(frame==80)screenshot("01-power.png");
         if(frame==85){map.timeOfDay="night";mapBatch.setColor(PkmnMap.nightColor);}
         if(frame==110)screenshot("02-flash-night.png");
         if(frame==115){map.timeOfDay="day";mapBatch.setColor(Color.WHITE);use("BUILD",electric);}
         if(frame==140){screenshot("03-build.png");for(String key:player.buildTileRequirements.get(player.currBuildTile.name).keySet())player.setItemAmount(key,0);}
         if(frame==145){require(batch.getTargetTint().r>batch.getTargetTint().g,"Invalid build indicator not captured");screenshot("04-build-invalid.png");}
         if(frame==150)use("DIG",electric);
         if(frame==175)screenshot("05-dig.png");
         if(frame==180){stop();player.position.set(0,0);player.dirFacing="right";use("SURF",water);insertAction(new PlayerMoving(this,player,false,new SetField(player,"acceptInput",true,new PlayerStanding(this))));}
         if(frame==210){require(player.position.x==16,"Surf did not enter real water tile: "+player.position);screenshot("06-surf-enter.png");insertAction(new PlayerMoving(this,player,false,new PlayerStanding(this)));}
         if(frame==240){require(player.position.x==32,"Surf movement failed: "+player.position);screenshot("07-surf-move.png");}
         if(frame==245){stop();player.position.set(-16,0);player.dirFacing="down";cam.position.set(-8,8,0);use("FLY",bird);insertAction(player.new Flying(bird,true,null));}
         if(frame==300)screenshot("08-fly.png");
         if(frame==330){stop();player.position.set(0,0);player.dirFacing="right";cam.position.set(8,8,0);player.isFishing=true;}
         if(frame==350)screenshot("09-fishing.png");
         if(frame==355){player.isFishing=false;player.isSitting=true;}
         if(frame==375)screenshot("10-sitting.png");
         if(frame==380){player.isSitting=false;player.isSleeping=true;}
         if(frame==400)screenshot("11-sleeping.png");
         if(frame==405){player.isSleeping=false;player.isCrafting=true;}
         if(frame==425)screenshot("12-crafting.png");
         if(frame==430){
            stop();player.position.set(-32,-16);player.dirFacing="up";cam.position.set(-24,0,0);
            actionTile=putTarget("tree2");require(actionTile.isCuttable,"CUT fixture not cuttable");
            use("CUT",electric);effectsBefore=renderer().getFieldEffectFrames();leftMouse=true;
         }
         if(frame==431)leftMouse=false;
         if(frame==433){cut=find(CutTreeAnim.class);require(cut!=null,"Mouse confirm did not activate original CUT");
            cut.append(new RunCode(()->cutNext++,null));}
         if(frame==456)screenshot("13-cut-split.png");
         if(frame==488){
            require(cut.timer==50&&!actionStack.contains(cut)&&cutNext==1,"CUT did not finish original timer/next action");
            require(map.tiles.get(actionTile.position)!=actionTile&&map.tiles.get(actionTile.position).nameUpper.isEmpty(),"CUT did not remove its target");
            require(playerCanMove&&renderer().getFieldEffectFrames()>effectsBefore+15,"CUT lost modern feedback or next-action liveness");
            screenshot("14-cut-completed.png");
         }
         if(frame==490){actionTile=putTarget("tree2");require(actionTile.isHeadbuttable,"HEADBUTT fixture not headbuttable");
            use("HEADBUTT",electric);effectsBefore=renderer().getFieldEffectFrames();leftMouse=true;}
         if(frame==491)leftMouse=false;
         if(frame==493){headbutt=find(HeadbuttTreeAnim.class);require(headbutt!=null,"Mouse confirm did not activate original HEADBUTT");
            // This legacy action declares its own nextAction; preserve and extend that actual chain.
            headbutt.nextAction.append(new RunCode(()->headbuttNext++,null));}
         if(frame==515)screenshot("15-headbutt-shake.png");
         if(frame==555){
            require(headbutt.index==60&&!actionStack.contains(headbutt)&&headbuttNext==1,"HEADBUTT did not finish original timer/next action");
            require(map.tiles.get(actionTile.position)==actionTile&&actionTile.overSprite==headbutt.originalSprite,"HEADBUTT changed tree identity or left deformed art");
            require(playerCanMove&&renderer().getFieldEffectFrames()>effectsBefore+50,"HEADBUTT lost feedback or return to movement");
         }
         if(frame==560){stop();putTarget("");player.currPlanting="red apricorn";player.setItemAmount("red apricorn",2);
            effectsBefore=renderer().getFieldEffectFrames();leftMouse=true;}
         if(frame==561)leftMouse=false;
         if(frame>561&&frame<620&&plant==null){plant=find(PlantTree.class);if(plant!=null)plant.append(new RunCode(()->plantNext++,null));}
         if(frame==582)screenshot("16-plant-growth.png");
         if(frame==620){
            require(plant!=null&&plant.timer==25&&!actionStack.contains(plant)&&plantNext==1,"Plant action did not complete once");
            require(map.tiles.get(player.facingPos()).nameUpper.equals("tree_planted2")&&player.getItemAmount("red apricorn")==1,
               "Planting did not persist sprout and consume exactly one item");
            require(renderer().getFieldEffectFrames()>effectsBefore+20,"Plant growth was invisible");
            screenshot("17-plant-completed.png");rightMouse=true;
         }
         if(frame==646){require(player.currPlanting==null,"Held RMB did not cancel planting through PlayerStanding");rightMouse=false;}
         if(frame==650){use("RIDE",bird);player.dirFacing="left";
            insertAction(new PlayerMoving(this,player,false,new PlayerStanding(this)));}
         if(frame==678){require(player.position.x==-48&&player.hmPokemon==bird,"RIDE movement or mount identity lost");screenshot("18-riding.png");rightMouse=true;}
         if(frame==705){require(player.currFieldMove.isEmpty()&&player.hmPokemon==null,"Held RMB did not dismount through original action");rightMouse=false;}
         if(frame==710){screenshot("19-dismounted.png");constructionTarget=putTarget("");use("BUILD",electric);
            selectConstruction("chest1");constructionCosts=new HashMap<>(player.buildTileRequirements.get("chest1"));
            for(String item:constructionCosts.keySet())player.setItemAmount(item,constructionCosts.get(item)+4);
            constructionBefore=new HashMap<>(player.getItemsDict());phase(8);}
         if(lifecycle>=0)lifecycle();
      }
      void phase(int value){lifecycle=value;lifecycleStart=frame;furnitureSettledFrames=0;leftMouse=false;rightMouse=false;rightKey=false;}
      void lifecycle(){
         int age=frame-lifecycleStart;
         require(age<(lifecycle==7?1500:500),"Field lifecycle timed out at phase "+lifecycle);
         if(lifecycle==8){
            leftMouse=age==3;
            if(age==40){Tile built=map.tiles.get(constructionTarget.position);
               require(built.nameUpper.equals("chest1")&&built.isSolid,"BUILD did not install the original blocking chest");
               for(String item:constructionCosts.keySet())require(player.getItemAmount(item)==constructionBefore.get(item)-constructionCosts.get(item)
                  &&built.items().get(item).equals(constructionCosts.get(item)),"BUILD consumed incorrect resources: "+item);
               require(playerCanMove,"BUILD completion did not restore movement");screenshot("26-build-completed.png");
               player.dirFacing="down";constructionTarget=putTarget("");cancelInventory=new HashMap<>(player.getItemsDict());phase(9);rightMouse=true;}
         }else if(lifecycle==9){
            rightMouse=age<65;
            if(age==65){require(player.currFieldMove.isEmpty()&&player.hmPokemon==null&&playerCanMove,"BUILD cancel did not finish original companion return");
               require(player.getItemsDict().equals(cancelInventory)&&constructionTarget.nameUpper.isEmpty()&&!constructionTarget.isSolid,
                  "BUILD cancellation consumed material or placed a collision tile");
               use("DIG",electric);grassBefore=player.getItemAmount("grass");phase(10);}
         }else if(lifecycle==10){
            leftMouse=age==3||age==40;
            if(age==30){require(constructionTarget.nameUpper.contains("hole")&&player.getItemAmount("grass")==grassBefore+1
                  &&playerCanMove&&player.currFieldMove.equals("DIG"),"DIG did not excavate, award terrain once and restore control");
               screenshot("27-dig-hole.png");selectConstruction("sand1");player.setItemAmount("soft sand",3);}
            if(age==65){require(constructionTarget.name.contains("sand")&&constructionTarget.nameUpper.isEmpty()&&!constructionTarget.isSolid,
                  "DIG terrain placement did not fill the hole and refresh passability");
               require(player.getItemAmount("soft sand")==2&&player.getItemAmount("grass")==grassBefore+1&&playerCanMove,
                  "DIG terrain placement consumed wrong material or failed to finish");
               screenshot("28-dig-refilled.png");cancelInventory=new HashMap<>(player.getItemsDict());phase(11);rightMouse=true;}
         }else if(lifecycle==11){
            rightMouse=age<65;
            if(age==65){require(player.currFieldMove.isEmpty()&&player.hmPokemon==null&&playerCanMove,"DIG cancellation did not complete");
               require(player.getItemsDict().equals(cancelInventory)&&constructionTarget.name.contains("sand")&&constructionTarget.nameUpper.isEmpty(),
                  "DIG cancellation changed materials or terrain");
               use("FLY",bird);flightStart=player.position.cpy();insertAction(player.new Flying(bird,true,null));phase(12);}
         }else if(lifecycle==12){
            rightKey=age==110;
            rightMouse=age==125;
            if(age==122){require(player.flyingAction!=null&&player.position.equals(flightStart.cpy().add(16,0)),
                  "Original FLY right-key movement did not advance exactly one tile");screenshot("29-fly-moved.png");}
            if(age==220){require(player.flyingAction==null&&find(Player.Flying.class)==null&&find(PlayerStanding.class)!=null
                  &&player.currFieldMove.isEmpty()&&player.hmPokemon==null,"RMB FLY landing did not finish into original PlayerStanding");
               screenshot("30-fly-landed.png");putTarget("house_couch1");phase(0);leftMouse=true;}
         }else if(lifecycle==0){
            leftMouse=age==0;
            if(age==42){require(player.isSitting&&find(Player.Sitting.class)!=null,"Couch interaction did not enter original Sitting");
               furnitureAnchor(false);
               screenshot("20-couch-enter.png");phase(1);rightMouse=true;}
         }else if(lifecycle==1){
            rightMouse=age==0;
            if(age==40){require(!player.isSitting&&playerCanMove&&find(Player.Sitting.class)==null,"Couch cancel failed to restore movement");
               putTarget("house_bed1");phase(2);leftMouse=true;}
         }else if(lifecycle==2){
            leftMouse=age==0||age%12==0&&(find(DisplayText.class)!=null||find(DrawYesNoMenu.class)!=null);
            if(player.isSleeping&&++furnitureSettledFrames>=2){require(player.sleepingDir!=null,"Bed sleep lost its original bed target");
               furnitureAnchor(true);screenshot("21-bed-sleep.png");phase(3);rightMouse=true;}
         }else if(lifecycle==3){
            rightMouse=age==0;
            if(age==12){require(!player.isSleeping&&player.sleepingDir==null&&playerCanMove,"Bed wake did not restore movement");
               putTarget("ball_kiln1");phase(4);leftMouse=true;}
         }else if(lifecycle==4){
            leftMouse=age==0;
            if(age>25&&find(DrawCraftsMenu.class)!=null){require(player.isCrafting&&!playerCanMove,"Kiln menu did not enter crafting state");
               screenshot("22-kiln-menu.png");phase(5);rightMouse=true;}
         }else if(lifecycle==5){
            rightMouse=age==0;
            if(age>6&&!player.isCrafting){require(playerCanMove&&find(DrawCraftsMenu.class)==null,"Kiln cancellation did not finish its next action");
               Tile sea=new Tile("water2",player.facingPos(),true,new Route("ocean1",10));map.tiles.put(sea.position.cpy(),sea);
               player.currRod="old rod";opponentBeforeFishing=battle.oppPokemon;seaPoolBefore=Route.allowedPokemon.get("sea1");
               Route.allowedPokemon.put("sea1",new ArrayList<>(List.of("milotic")));
               randomBeforeFishing=Game.rand;Game.rand=new Random(54321){@Override public int nextInt(int bound){return bound==2?0:super.nextInt(bound);}};
               phase(6);leftMouse=true;}
         }else if(lifecycle==6){
            fishingSeen|=player.isFishing;
            leftMouse=age==0||age%12==0&&find(DisplayText.class)!=null;
            if(age>130&&fishingSeen&&!player.isFishing&&playerCanMove){
               require(battle.oppPokemon==opponentBeforeFishing,"Empty filtered rod pool constructed an invalid fish");
               screenshot("23-fishing-no-eligible-catch.png");
               if(seaPoolBefore==null)Route.allowedPokemon.remove("sea1");else Route.allowedPokemon.put("sea1",seaPoolBefore);
               Tile pond=new Tile("water1",player.facingPos(),true,new Route("river1",10));map.tiles.put(pond.position.cpy(),pond);
               fishingSeen=false;phase(7);
            }
         }else if(lifecycle==7){
            // Let the previous confirmation release before the next deliberate cast.
            leftMouse=age==3;
            fishingSeen|=player.isFishing;
            if(age==30){require(battle.oppPokemon!=null&&battle.oppPokemon!=opponentBeforeFishing,"Real fishing did not create an encounter");
               caughtSpecies=battle.oppPokemon.specie.name;
               require(List.of("magikarp","poliwag","goldeen").contains(caughtSpecies)&&battle.oppPokemon.level==10,"Old rod restriction was ignored: "+caughtSpecies);}
            // The original rod sound finishes asynchronously before isFishing is set.
            if(age>30&&player.isFishing&&!castScreenshot){screenshot("24-old-rod-cast.png");castScreenshot=true;}
            if(age>35&&find(DisplayText.class)!=null)leftMouse=age%12==0;
            if(find(DrawBattleMenuNormal.class)!=null){leftMouse=false;fishingBattleMenuFrames++;}
            if(player.dontDrawMapDuringBattle&&johtoBattleRenderer.getBattleFrames()>30&&fishingBattleMenuFrames>=3){
               require(fishingSeen&&!player.isFishing&&battle.oppPokemon.specie.name.equals(caughtSpecies),"Caught fish did not finish into the original battle");
               screenshot("25-fishing-battle.png");Game.rand=randomBeforeFishing;finish();
            }
         }
      }
      void finish(){
            WorldBatch batch=(WorldBatch)mapBatch;require(batch.getSuppressedDraws()>1000,"Suppression not exercised");
            require(renderer().getLoadedSpeciesCount()==0,"3D models unexpectedly loaded");
            System.out.println("FIELD modernFrames="+renderer().getRenderedFrames()+" effectFrames="+renderer().getFieldEffectFrames()+" discardedLegacyDraws="+batch.getSuppressedDraws()+" submitted="+batch.getSubmittedDraws()+" caughtSpecies="+caughtSpecies);complete=true;Gdx.app.exit();}
      void selectConstruction(String name){
         for(int i=0;i<player.buildTiles.size();i++)if(player.buildTiles.get(i).name.equals(name)){
            player.buildTileIndex=i;player.currBuildTile=player.buildTiles.get(i);return;}
         throw new IllegalStateException("Original construction list omitted "+name);
      }
      /** Assert the submitted mesh and its projection, not just flags or a colourful frame. */
      void furnitureAnchor(boolean bed){
         BwAssets assets=BwAssets.get();
         float[] person=geometryQuad(assets.trainer(player,0,false),true);
         float x=(person[0]+person[8])*.5f,y=-(person[2]+person[10])*.5f;
         if(!bed){
            Player.Sitting sitting=find(Player.Sitting.class);
            require(Math.abs(x-(sitting.sittingOn.position.x+sitting.offsetX+8))<.02f
               &&Math.abs(y-(sitting.sittingOn.position.y+sitting.offsetY+7))<.02f,
               "Submitted seated trainer stayed on the adjacent walking cell");
         }else{
            float[] furniture=geometryQuad(assets.named("bed"),false);
            require(Math.abs(x-(furniture[0]+furniture[8])*.5f)<.02f,
               "Sleeping trainer is not horizontally centred over the rendered bed");
            PerspectiveCamera camera=(PerspectiveCamera)field(renderer(),"camera");
            Vector3 bedBottom=camera.project(new Vector3((furniture[0]+furniture[8])*.5f,furniture[1],furniture[2]));
            Vector3 bedTop=camera.project(new Vector3((furniture[16]+furniture[32])*.5f,furniture[17],furniture[18]));
            Vector3 headBottom=camera.project(new Vector3(x,person[1],person[2]));
            float pillowFraction=(headBottom.y-bedBottom.y)/(bedTop.y-bedBottom.y);
            require(pillowFraction>.55f&&pillowFraction<.85f,
               "Sleeping head is outside the rendered pillow plane: "+pillowFraction);
            require(Math.abs(headBottom.x-(bedBottom.x+bedTop.x)*.5f)<3,
               "Sleeping head projects outside bed centre");
            System.out.println("FIELD bed projected pillow fraction="+pillowFraction);
         }
      }
      float[] geometryQuad(TextureRegion region,boolean actor){
         @SuppressWarnings("unchecked") Map<Texture,Object> groups=(Map<Texture,Object>)field(renderer(),"geometry");
         Object mesh=groups.get(region.getTexture());require(mesh!=null,"No submitted furniture/trainer mesh");
         FloatArray vertices=(FloatArray)field(mesh,"vertices");
         for(int offset=0;offset+47<vertices.size;offset+=48){
            float[] data=vertices.items;boolean isActor=data[offset+6]>3.5f;
            if(isActor!=actor)continue;
            if(!actor&&(Math.abs(data[offset+4]-region.getU())>.00001f
               ||Math.abs(data[offset+5]-region.getV2())>.00001f
               ||Math.abs(data[offset+12]-region.getU2())>.00001f))continue;
            return Arrays.copyOfRange(data,offset,offset+48);
         }
         throw new IllegalStateException("Expected rendered furniture/trainer quad absent");
      }
      Object field(Object owner,String name){try{java.lang.reflect.Field value=owner.getClass().getDeclaredField(name);
         value.setAccessible(true);return value.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
      Tile putTarget(String upper){
         Tile tile=new Tile("green1",upper,player.facingPos(),true);tile.biome="grassland";
         tile.items=new HashMap<>();map.tiles.put(tile.position.cpy(),tile);map.refreshCache=true;return tile;
      }
      <T extends Action>T find(Class<T> type){for(Action action:actionStack)if(type.isInstance(action))return type.cast(action);return null;}
      void screenshot(String name){
         byte[] rgba=ScreenUtils.getFrameBufferPixels(0,0,640,576,false);BufferedImage image=new BufferedImage(640,576,BufferedImage.TYPE_INT_RGB);Set<Integer>colors=new HashSet<>();
         for(int i=0;i<rgba.length;i+=4){int rgb=(rgba[i]&255)<<16|(rgba[i+1]&255)<<8|rgba[i+2]&255;colors.add(rgb);image.setRGB(i/4%640,575-i/4/640,rgb);}
         require(colors.size()>12,"Empty frame "+name);
         try{ImageIO.write(image,"png",new File(name));}catch(Exception e){throw new IllegalStateException(e);}
      }
   }
}
