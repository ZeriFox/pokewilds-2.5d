package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.nio.file.*;
import java.util.*;

/** Real game rules and world save round-trip, with disposable fixture data only. */
public final class CreativeModeTest {
   static int checks,captures;static Throwable failure;static boolean complete;
   static void check(boolean value,String message){checks++;if(!value)throw new IllegalStateException(message);}
   public static void main(String[] args) {
      Game.leakTracer=LeakTracer.NoOp.INSTANCE;
      Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();config.setInitialVisible(false);config.setWindowedMode(960,768);config.useVsync(false);
      new Lwjgl3Application(new Probe(),config);
      if(failure!=null){failure.printStackTrace();System.exit(1);}check(complete,"Fixture incomplete");
      System.out.println("CREATIVE MODE PASS: checks="+checks+", captures="+captures+", virtual materials/craft/build, original inventory, catalog/levels/shiny/full party, network guards, real save/load, real settings controller");System.exit(0);
   }
   static final class Probe extends Game {
      int frames;Probe(){super(new String[0],4);}
      @Override public void render(){if(failure!=null)return;try{super.render();if(++frames==15){run();complete=true;Gdx.app.exit();}}catch(Throwable error){failure=error;Gdx.app.exit();}}
      void run()throws Exception {
         actionStack.clear();server=new Server();Network.register(server);map=new PkmnMap("creative-fixture");
         for(int y=-160;y<=160;y+=16)for(int x=-160;x<=160;x+=16){Vector2 pos=new Vector2(x,y);map.tiles.put(pos,new Tile("green1",pos.cpy(),true));}
         map.bottomLeft=new Vector2(-160,-160);map.topRight=new Vector2(160,160);map.minimap=new Pixmap(40,40,Pixmap.Format.RGBA8888);map.edges.add(new Vector2());
         player.name="CreativeTest";player.position.set(0,0);player.spawnLoc.set(0,0);start();actionStack.clear();CreativeMode.newWorld(this);
         player.setItemAmount("log",3);player.setItemAmount("grass",2);player.setItemAmount("rare candy",4);
         Map<String,Integer> original=new LinkedHashMap<>(player.getStoredItemsDict());
         check(!CreativeMode.active(player)&&player.getItemAmount("log")==3,"Default mode changed inventory");
         Player.Craft recipe=new Player.Craft("creative test output",1);recipe.requirements.add(new Player.Craft("log",4));
         check(!player.hasCraftRequirements(recipe,1),"Adventure craft accepts missing ingredients");
         CreativeMode.setEnabled(this,true);check(CreativeMode.active(player),"Creative toggle failed");
         for(Map<String,Integer> ingredients:player.buildTileRequirements.values())for(Map.Entry<String,Integer> ingredient:ingredients.entrySet())
            check(player.hasItem(ingredient.getKey())&&player.getItemAmount(ingredient.getKey())>=ingredient.getValue(),"Build resource missing: "+ingredient.getKey());
         check(player.hasCraftRequirements(recipe,99),"Infinite resources cannot craft");
         player.craftItem(recipe,99);check(player.getItemAmount(recipe.name)==99&&player.getStoredItemsDict().get("log")==3,"Craft modified real material stack");
         player.currFieldMove="BUILD";player.dirFacing="down";player.updateBuildTiles(this);
         boolean selected=false;for(int i=0;i<player.buildTiles.size();i++)if(player.buildTiles.get(i).name.equals("chest1")){player.buildTileIndex=i;player.currBuildTile=player.buildTiles.get(i);selected=true;break;}
         check(selected,"Fixture has no chest construction");InputProcessor.blockHeldInputs();InputProcessor.aJustPressed=true;playerCanMove=true;
         new PlayerStanding(this).localStep(this);
         Tile chest=map.tiles.get(new Vector2(0,-16));check(chest.nameUpper.equals("chest1")&&chest.isSolid,"Real BUILD action rejected creative resources");
         for(Map.Entry<String,Integer> cost:player.buildTileRequirements.get("chest1").entrySet())check(chest.items().getOrDefault(cost.getKey(),0)>=cost.getValue(),"Built chest lost recoverable materials: "+cost+" vs "+chest.items());
         check(player.getStoredItemsDict().get("log")==3,"Real BUILD consumed owned logs");
         InputProcessor.blockHeldInputs();actionStack.clear();player.currFieldMove="";playerCanMove=true;
         // Execute the exact inventory debits used by PlayerStanding placement, repeatedly.
         for(int i=0;i<250;i++){player.setItemAmount("log",player.getItemAmount("log")-5000);player.removeItem("log");player.getItemsDict().remove("grass");}
         check(player.getItemAmount("log")==CreativeMode.SUPPLY&&player.getStoredItemsDict().get("grass")==2,"Supply ran out or destroyed owned resources");
         Map<String,Integer> bag=new LinkedHashMap<>(player.getItemsDict());player.getItemsDict().clear();player.setPlayerItems(bag);
         check(player.getStoredItemsDict().get("log")==3&&!player.getStoredItemsDict().containsKey("mystic soil"),"Bag sorting persisted virtual stacks");
         Network.PlayerData packet=new Network.PlayerData(player);check(packet.itemsDict.equals(player.getStoredItemsDict())&&!packet.itemsDict.containsValue(CreativeMode.SUPPLY),"Virtual stacks leaked into save/network payload");
         check(CreativeMode.catalog().size()>800&&CreativeMode.catalog().contains("pikachu")&&CreativeMode.catalog().contains("fuecoco"),"Catalog excludes old or new species");
         check(CreativeMode.search("FUECO").equals(Collections.singletonList("fuecoco")),"Search ignores query");
         int count=player.pokemon.size();CreativeMode.grant(this,"pikachu",0,false);check(player.pokemon.size()==count,"Invalid level was accepted");
         CreativeMode.grant(this,"pikachu",1,false);CreativeMode.grant(this,"fuecoco",100,true);
         Pokemon created=player.pokemon.get(player.pokemon.size()-1);check(created.level==100&&created.isShiny&&created.previousOwner==player,"Catalog parameters lost");
         while(player.pokemon.size()<6)CreativeMode.grant(this,"eevee",20,false);
         List<Pokemon> full=new ArrayList<>(player.pokemon);String message=CreativeMode.grant(this,"mew",50,false);
         check(player.pokemon.equals(full)&&message.contains("Party full"),"Full party was replaced or overflowed");
         type=Type.CLIENT;check(!CreativeMode.active(player)&&player.getItemAmount("log")==3,"Client received creative resources");
         check(CreativeMode.grant(this,"mew",50,false).startsWith("Enable"),"Client can grant Pokemon");
         type=Type.SERVER;check(!CreativeMode.active(player),"Hosted game receives local creative rules");type=null;
         CreativeMode.setEnabled(this,false);check(player.getItemAmount("log")==3&&!player.hasItem("mystic soil"),"Disabling changed original inventory");
         player.removeItem(recipe.name);check(original.equals(player.getStoredItemsDict()),"Original inventory not restored exactly");
         creationSelection();controller();CreativeMode.setEnabled(this,true);player.setCurrPokemon();
         Gdx.files.local("creative-fixture.sav").mkdirs();Gdx.files.local("creative-fixture.sav/.test").writeString("Disposable creative fixture",false);saveGame();
         check(Gdx.files.local("creative-fixture.sav/creative-mode.json").readString().contains("true"),"Save omitted creative state");
         actionStack.clear();Pixmap previous=map.minimap;map=new PkmnMap("creative-fixture");player=new Player();start();map.loadFromFile(this);previous.dispose();
         check(CreativeMode.active(player)&&player.getItemAmount("log")==CreativeMode.SUPPLY,"Creative state lost on actual world reload");
         check(player.getStoredItemsDict().equals(original)&&player.pokemon.size()==6&&player.pokemon.get(2).isShiny&&player.pokemon.get(2).level==100,"World reload corrupted inventory/created Pokemon");
         check(map.tiles.get(new Vector2(0,-16)).nameUpper.equals("chest1"),"Creative construction disappeared on reload");
         CreativeMode.newWorld(this);check(!CreativeMode.active(player),"New adventure reused old creative state");
         CreativeMode.reload(this);check(CreativeMode.active(player),"Saved state cannot be reloaded");
         Gdx.files.local("creative-fixture.sav/creative-mode.json").writeString("{malformed",false);CreativeMode.reload(this);
         check(!CreativeMode.active(player)&&!CreativeMode.warning.isEmpty(),"Invalid mode file enables creative");
         check(original.equals(player.getStoredItemsDict()),"Invalid sidecar changed player inventory");
      }
      void creationSelection() {
         DesktopMenuController c=modernUi.desktop;
         DrawSetupMenu setup=new DrawSetupMenu(this,null);c.attach(setup);c.screen=DesktopMenuController.Screen.SETUP;
         check(!c.creativeWorldDraft&&!CreativeMode.active(player),"New-world selection inherited the active world mode");
         check(c.rows().size()==8&&c.rows().get(6).field==DesktopMenuController.Field.WORLD_MODE,"New Local setup has no explicit game mode");
         for(int i=0;i<8;i++) {
            ModernUi.Box b=DesktopMenuController.setupBox(i,8);check(b.y>22&&b.top()<115,"Setup game-mode row overlaps header or help");
            if(i>0)check(b.top()<DesktopMenuController.setupBox(i-1,8).y,"Setup rows overlap");
         }
         actionStack.clear();actionStack.add(setup);DrawSetupMenu.currIndex=7;InputProcessor.blockHeldInputs();InputProcessor.rightJustPressed=true;
         check(c.beforeAction(this,setup)&&c.creativeWorldDraft&&!CreativeMode.active(player),"Choosing Creative changed the current adventure before creation");InputProcessor.blockHeldInputs();
         setup.localHostJoinIndex=1;check(c.rows().stream().noneMatch(r->r.field==DesktopMenuController.Field.WORLD_MODE),"Hosted setup offers local Creative");
         CreativeMode.newWorld(this,setup);check(!CreativeMode.active(player),"Host setup draft enabled Creative");
         setup.localHostJoinIndex=2;check(c.rows().stream().noneMatch(r->r.field==DesktopMenuController.Field.WORLD_MODE),"Join setup offers local Creative");
         setup.localHostJoinIndex=0;setup.newLoadIndex=1;
         check(c.rows().stream().noneMatch(r->r.field==DesktopMenuController.Field.WORLD_MODE),"Load setup offers a replacement mode instead of saved state");
         CreativeMode.newWorld(this,setup);check(!CreativeMode.active(player),"Load draft enabled Creative");
         setup.newLoadIndex=0;CreativeMode.newWorld(this,setup);check(CreativeMode.active(player),"Selected Creative did not become active at creation");
         type=Type.SERVER;CreativeMode.newWorld(this,setup);check(!CreativeMode.active(player),"Networking bypassed local-only creation guard");type=null;
         DrawSetupMenu stale=new DrawSetupMenu(this,null);CreativeMode.newWorld(this,stale);check(!CreativeMode.active(player),"An unattached setup reused another draft");
         c.attach(stale);check(!c.creativeWorldDraft,"New setup reused the previous draft");CreativeMode.newWorld(this,stale);check(!CreativeMode.active(player),"Default new world no longer starts in Adventure");
         actionStack.clear();InputProcessor.blockHeldInputs();
      }
      void controller()throws Exception {
         DrawPlayerMenu menu=new DrawPlayerMenu(this,null);actionStack.clear();actionStack.add(menu);
         DesktopMenuController c=modernUi.desktop;c.openOptions(menu);c.optionIndex=16;c.enter=true;c.beforeAction(this,menu);
         check(c.creative!=null,"F1 settings has no creative entry");
         c.creative.focus=0;c.enter=true;c.beforeAction(this,menu);check(CreativeMode.active(player),"UI toggle not connected to game rules");
         c.creative.query="fue";c.creative.filter();c.creative.focus=4;
         check(c.creative.matches.equals(Collections.singletonList("fuecoco")),"Catalog UI filter does not select species");
         c.preferences.reducedMotion=true;modernUi.applyViewport(this,960,768);uiBatch.begin();modernUi.render(this,menu);uiBatch.end();
         Pixmap shot=new Pixmap(960,768,Pixmap.Format.RGBA8888);shot.getPixels().put(ScreenUtils.getFrameBufferPixels(0,0,960,768,true));PixmapIO.writePNG(Gdx.files.local("creative-catalog.png"),shot);shot.dispose();captures++;
         c.creative.focus=1;c.enter=true;c.beforeAction(this,menu);check(c.creative.editing,"Search never receives typing focus");
         check(c.filterHeld(this,15)==0,"Search key presses leak into movement");
         c.creative.key(com.badlogic.gdx.Input.Keys.ENTER);check(!c.creative.editing&&c.creative.focus==4,"Search cannot return to catalog");
         c.back=true;c.beforeAction(this,menu);check(c.creative==null&&c.optionsOpen(),"Creative Back closes wrong menu");c.closeOptions(false);
         DrawSetupMenu setup=new DrawSetupMenu(this,null);c.openOptions(setup);c.optionIndex=16;c.enter=true;c.beforeAction(this,setup);
         check(c.creative==null&&!c.message.isEmpty(),"Title screen allows editing an absent adventure");c.closeOptions(false);actionStack.clear();
      }
   }
}
