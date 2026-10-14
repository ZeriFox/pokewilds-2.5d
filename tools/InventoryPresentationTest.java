package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.util.*;

/** Real bag/party/summary menus plus shiny disclosure and reduced-motion pixel checks. */
public final class InventoryPresentationTest {
   static int checks,captures;static Throwable failure;static boolean complete;
   static void check(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException(why);}
   public static void main(String[] args){Game.leakTracer=LeakTracer.NoOp.INSTANCE;Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration();c.setInitialVisible(false);c.setWindowedMode(960,864);c.useVsync(false);new Lwjgl3Application(new Probe(),c);if(failure!=null){failure.printStackTrace();System.exit(1);}check(complete,"Incomplete UI probe");System.out.println("INVENTORY PRESENTATION PASS: checks="+checks+", captures="+captures+", item icons/party/status/summary, shiny normal/ghost/egg concealment, reduced motion and inventory unchanged");}
   static class Probe extends Game {
      int boot;Probe(){super(new String[0],4);}
      @Override public void render(){try{super.render();if(++boot==15){run();complete=true;Gdx.app.exit();}}catch(Throwable t){failure=t;Gdx.app.exit();}}
      void run(){
         actionStack.clear();server=new Server();Network.register(server);map=new PkmnMap("inventory-visual-fixture");map.bottomLeft=new Vector2(-64,-64);map.topRight=new Vector2(64,64);map.minimap=new Pixmap(16,16,Pixmap.Format.RGBA8888);
         for(int y=-64;y<=64;y+=16)for(int x=-64;x<=64;x+=16){Vector2 p=new Vector2(x,y);map.tiles.put(p,new Tile("green1",p,true));}
         player.name="VisualTest";player.position.set(0,0);player.spawnLoc.set(0,0);start();actionStack.clear();actionStackCopy.clear();CreativeMode.newWorld(this);modernUi.desktop.preferences.reducedMotion=true;modernUi.applyViewport(this,960,864);
         for(String item:new String[]{"poke ball","great ball","ultra ball","dream ball","master ball","rare candy","max repel","oran berry","pecha berry","log","hard stone"})player.setItemAmount(item,12);
         Map<String,Integer> before=new LinkedHashMap<>(player.getStoredItemsDict());
         DrawPlayerMenu parent=new DrawPlayerMenu(this,null);
         for(int pocket:new int[]{0,1,2}){DrawItemMenuGen2.boxIndex=pocket;DrawItemMenu.lastCurrIndex=0;DrawItemMenu.lastCursorPos=0;DrawItemMenuGen2 menu=new DrawItemMenuGen2(this,parent);menu.firstStep(this);menu.drawItems=true;menu.disabled=false;screen(menu,"bag-pocket-"+pocket);}
         Pokemon shiny=new Pokemon("pikachu",20,Pokemon.Generation.CRYSTAL,true);player.pokemon.add(shiny);shiny.status="burn";
         DrawPokemonMenu party=new DrawPokemonMenu(this,parent);party.firstStep(this);screen(party,"party-status");
         DrawPokemonMenu.allPokemon=player.pokemon;DrawStatsScreen summary=new DrawStatsScreen(this,shiny,party);screen(summary,"summary-shiny");
         check(player.getStoredItemsDict().equals(before),"Drawing new inventory assets changes owned items");
         Pokemon normal=new Pokemon("eevee",10,Pokemon.Generation.CRYSTAL,false);byte[] empty=glitter(normal,0);
         byte[] bright=glitter(shiny,0);check(!Arrays.equals(empty,bright),"Shiny has no visible sparkle");
         check(Arrays.equals(bright,glitter(shiny,8)),"Reduced motion sparkle animates");
         shiny.isGhost=true;check(Arrays.equals(empty,glitter(shiny,0)),"Hidden ghost reveals shiny identity");shiny.isGhost=false;
         shiny.isEgg=true;check(Arrays.equals(empty,glitter(shiny,0)),"Egg reveals shiny identity");shiny.isEgg=false;
         modernUi.desktop.preferences.reducedMotion=false;check(!Arrays.equals(glitter(shiny,.1f),glitter(shiny,1.1f)),"Shiny effect has no animation");
         check(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"GL state error");
      }
      void screen(Action action,String name){ScreenUtils.clear(.11f,.15f,.2f,1);uiBatch.begin();modernUi.render(this,action);uiBatch.end();Pixmap image=ScreenUtils.getFrameBufferPixmap(0,0,960,864);PixmapIO.writePNG(Gdx.files.local(name+".png"),image,-1,true);image.dispose();captures++;}
      byte[] glitter(Pokemon p,float time){ScreenUtils.clear(0,0,0,1);uiBatch.begin();ShinyVisuals.drawBattle(this,p,80,40,45,55,time);uiBatch.end();return ScreenUtils.getFrameBufferPixels(0,0,960,864,false);}
   }
}
