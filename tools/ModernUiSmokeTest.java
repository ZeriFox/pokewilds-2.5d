package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Native UI and battle integration; all navigation passes through InputProcessor. */
public final class ModernUiSmokeTest {
    public static void main(String[] args) throws Exception {
        PrintStream original = System.err;
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        System.setErr(new PrintStream(new OutputStream() {
            public void write(int value) { original.write(value); if (errors.size() < 1048576) errors.write(value); }
        }, true, "UTF-8"));
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(290000); } catch (InterruptedException done) { return; }
            original.println("UI FAIL: 290s watchdog"); System.exit(124);
        });
        watchdog.setDaemon(true); watchdog.start();
        int exit = 1;
        try {
            Game.leakTracer = LeakTracer.NoOp.INSTANCE;
            UiGame game = new UiGame();
            Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
            config.setWindowedMode(640, 576); config.setInitialVisible(false);
            config.setTitle("PokeWilds native modern UI integration");
            config.setForegroundFPS(60); config.setIdleFPS(60); config.useVsync(false);
            new Lwjgl3Application(game, config);
            if (!game.complete || !game.disposed) throw new IllegalStateException("Incomplete UI lifecycle");
            if (Pattern.compile("(?m)^\\s*(?:[\\w$]+\\.)*[\\w$]*(?:Exception|Error)(?:[:\\s]|$)|^\\s*at\\s+[\\w.$]+\\(")
                    .matcher(errors.toString("UTF-8")).find()) throw new IllegalStateException("Exception on stderr");
            System.out.println("UI PASS: original input, menus, crafting, NPC encounter, battle turn, flee, capture and save/load");
            exit = 0;
        } catch (Throwable error) { error.printStackTrace(original); }
        finally { watchdog.interrupt(); System.setErr(original); }
        System.exit(exit);
    }

    private static final class Step {
        String name; int age, timeout; Runnable enter, tick, exit; BooleanSupplier done;
        Step(String n, int max, Runnable a, Runnable b, BooleanSupplier c, Runnable d) {
            name=n; timeout=max; enter=a; tick=b; done=c; exit=d;
        }
    }

    private static final class UiGame extends Game {
        final ArrayDeque<Step> steps = new ArrayDeque<>();
        final Set<String> checkedScreens = new LinkedHashSet<>();
        final Set<String> screenshots = new LinkedHashSet<>();
        final Map<String,Integer> transitionSamples = new LinkedHashMap<>();
        Input nativeInput, controlledInput;
        Step current;
        int pressed=-1, lastPressed=-1, justPressed=-1, frames, encounter, beforeCraft, oldHp, oldTurn, oldOppHp;
        long priorWorld, priorBattle, priorTransition;
        boolean complete, disposed, fixture, trackBattle;
        Pokemon wild;
        Pokemon renamed;
        Tile storage;
        Player.Craft craft;
        PrintWriter report;
        int stageFrame, transitionRendered, battleRendered;

        UiGame() { super(new String[0], 4); }

        @Override public void create() {
            super.create();
            nativeInput=Gdx.input;
            controlledInput=(Input)Proxy.newProxyInstance(Input.class.getClassLoader(),new Class<?>[]{Input.class},(proxy,method,args)->{
                if (method.getName().equals("isKeyPressed")) return pressed>=0 && (Integer)args[0]==pressed;
                if (method.getName().equals("isKeyJustPressed")) return justPressed>=0 && (Integer)args[0]==justPressed;
                if (method.getName().equals("isButtonPressed")) return (Integer)args[0]==Input.Buttons.LEFT
                    ? pressed==DesktopControls.MOUSE_LEFT : (Integer)args[0]==Input.Buttons.RIGHT && pressed==DesktopControls.MOUSE_RIGHT;
                if (method.getName().equals("isButtonJustPressed")) return (Integer)args[0]==Input.Buttons.LEFT
                    ? justPressed==DesktopControls.MOUSE_LEFT : (Integer)args[0]==Input.Buttons.RIGHT && justPressed==DesktopControls.MOUSE_RIGHT;
                if (method.getName().equals("isTouched")) return false;
                try { return method.invoke(nativeInput,args); } catch(InvocationTargetException e) { throw e.getCause(); }
            });
            try { report=new PrintWriter(Files.newBufferedWriter(Path.of("ui-results.tsv"))); }
            catch(IOException e) { throw new IllegalStateException(e); }
            report.println("frame\tscreen\tmodern_draws\tcolors\tfile");
            plan();
        }

        void add(String n,int max,Runnable enter,Runnable tick,BooleanSupplier done,Runnable exit) {
            steps.add(new Step(n,max,enter,tick,done,exit));
        }
        void action(String n,Runnable r) { add(n,3,r,null,()->true,null); }
        void delay(int n) { add("wait "+n,n+5,null,null,()->current.age>=n,null); }
        void tap(int key) {
            add("key "+key,10,()->pressed=key,()->{if(current.age>=1)pressed=-1;},()->current.age>=4,()->pressed=-1);
        }
        void waitFor(String label,BooleanSupplier condition,int max,boolean dialogue) {
            add(label,max,null,()->{if(dialogue)advanceDialogue();},condition,()->pressed=-1);
        }
        <T extends Action> T active(Class<T> type) {
            for(Action a:actionStack) if(type.isInstance(a) && (!(a instanceof Menu)||!((Menu)a).disabled)) return type.cast(a);
            return null;
        }
        void screen(Class<? extends Action> type,String file) {
            waitFor("open "+type.getSimpleName(),()->active(type)!=null,400,false);
            delay(12); action("capture "+file,()->{require(active(type)!=null,"Screen closed: "+type); screenshot(file,type.getSimpleName());});
        }
        void world() { waitFor("return to world",()->playerCanMove && battle.drawAction==null,800,true); delay(16); }
        void playerEntry(int index) {
            waitFor("player entry "+index,()->active(DrawPlayerMenu.class)!=null && active(DrawPlayerMenu.class).currIndex==index,120,false);
        }
        void navigatePlayer(int index) {
            add("navigate player "+index,160,null,()->{
                DrawPlayerMenu m=active(DrawPlayerMenu.class);
                pressed=m==null?-1:pulse(m.currIndex<index?InputProcessor.keyboardDown:InputProcessor.keyboardUp);
            },()->active(DrawPlayerMenu.class)!=null && active(DrawPlayerMenu.class).currIndex==index,()->pressed=-1);
            delay(3);
        }
        int pulse(int key) { return current.age%8==0?key:-1; }
        void advanceDialogue() {
            // Never confirm another menu merely because text appeared previously.
            pressed=(active(DisplayText.class)!=null || active(GainExpAnimationGen2.ShowLevelUpStats.class)!=null)
                && current.age%8==0?InputProcessor.keyboardA:-1;
        }
        void bag(int index,String capture) {
            add("bag pocket "+index,160,null,()->{
                DrawItemMenuGen2 m=active(DrawItemMenuGen2.class);
                pressed=m!=null && DrawItemMenuGen2.boxIndex!=index?pulse(InputProcessor.keyboardRight):-1;
            },()->active(DrawItemMenuGen2.class)!=null && DrawItemMenuGen2.boxIndex==index,()->pressed=-1);
            if(capture!=null)screen(DrawItemMenuGen2.class,capture); else delay(8);
        }
        void battleMenu(String pos) {
            waitFor("battle command ready",()->active(DrawBattleMenuNormal.class)!=null,1500,true);
            add("battle command "+pos,160,null,()->{
                DrawBattleMenuNormal m=active(DrawBattleMenuNormal.class);
                if(m==null){pressed=-1;return;}
                int key=m.curr.charAt(0)!=pos.charAt(0)?(pos.charAt(0)=='t'?InputProcessor.keyboardUp:InputProcessor.keyboardDown)
                    :(pos.charAt(1)=='l'?InputProcessor.keyboardLeft:InputProcessor.keyboardRight);
                pressed=pulse(key);
            },()->active(DrawBattleMenuNormal.class)!=null && active(DrawBattleMenuNormal.class).curr.equals(pos),()->pressed=-1);
            delay(4);
        }

        void plan() {
            screen(DrawSetupMenu.class,"01-setup.png");
            tap(InputProcessor.keyboardDown); tap(InputProcessor.keyboardRight);
            screen(DrawSetupMenu.class,"02-setup-load.png");
            tap(InputProcessor.keyboardLeft);
            action("create isolated fixture",this::fixture);
            delay(75); action("controls",()->screenshot("03-controls.png","DrawControls"));
            action("close controls",()->{DrawControls c=active(DrawControls.class); if(c!=null)c.remove=true;});
            delay(30); action("world",()->screenshot("04-world-sprites.png",null));
            tap(InputProcessor.keyboardStart); screen(DrawPlayerMenu.class,"05-player-menu.png");
            navigatePlayer(0); tap(InputProcessor.keyboardA); screen(DrawPokemonMenu.class,"06-party.png");
            tap(InputProcessor.keyboardA); screen(DrawPokemonMenu.SelectedMenu.class,"07-pokemon-actions.png");
            add("select STATS",150,null,()->{
                DrawPokemonMenu.SelectedMenu m=active(DrawPokemonMenu.SelectedMenu.class);
                pressed=m!=null && !m.words.get(m.curr).equals("STATS")?pulse(InputProcessor.keyboardDown):-1;
            },()->{DrawPokemonMenu.SelectedMenu m=active(DrawPokemonMenu.SelectedMenu.class);return m!=null && m.words.get(m.curr).equals("STATS");},()->pressed=-1);
            delay(3); tap(InputProcessor.keyboardA); screen(DrawStatsScreen.class,"08-stats-health.png");
            tap(InputProcessor.keyboardRight); screen(DrawStatsScreen.class,"09-stats-moves.png");
            tap(InputProcessor.keyboardRight); screen(DrawStatsScreen.class,"10-stats-values.png");
            tap(InputProcessor.keyboardB); waitFor("party after stats",()->active(DrawPokemonMenu.class)!=null,180,false);
            tap(InputProcessor.keyboardB); waitFor("player after party",()->active(DrawPlayerMenu.class)!=null,180,false);
            navigatePlayer(1); tap(InputProcessor.keyboardA);
            for(int i=0;i<5;i++)bag(i,"11-bag-"+i+".png");
            // A known medicine exists in this disposable fixture.
            tap(InputProcessor.keyboardA); screen(DrawUseTossMenu.class,"12-item-actions.png");
            tap(InputProcessor.keyboardB); delay(8); tap(InputProcessor.keyboardB);
            waitFor("player after bag",()->active(DrawPlayerMenu.class)!=null,180,false);
            navigatePlayer(2); tap(InputProcessor.keyboardA); screen(DrawMiniMap.class,"13-map.png");
            tap(InputProcessor.keyboardB); waitFor("player after map",()->active(DrawPlayerMenu.class)!=null,180,false);
            navigatePlayer(3); tap(InputProcessor.keyboardA); screen(DrawItemMenu.class,"14-guide.png");
            tap(InputProcessor.keyboardA); screen(DrawItemMenu.DrawGuideText.class,"14-guide-page.png");
            tap(InputProcessor.keyboardDown); screen(DrawItemMenu.DrawGuideText.class,"14-guide-next-page.png");
            action("guide navigation",()->require(active(DrawItemMenu.DrawGuideText.class).index==1,"Guide page ignored down input"));
            tap(InputProcessor.keyboardB);waitFor("guide list after page",()->active(DrawItemMenu.class)!=null,180,false);
            tap(InputProcessor.keyboardB); waitFor("player after guide",()->active(DrawPlayerMenu.class)!=null,180,false);
            tap(InputProcessor.keyboardB); world();
            action("face campfire",()->{player.dirFacing="up";});
            tap(InputProcessor.keyboardA); screen(DrawCraftsMenu.class,"15-crafting.png");
            action("remember recipe",()->{DrawCraftsMenu m=active(DrawCraftsMenu.class);craft=m.crafts.get(m.currIndex+m.cursorPos);beforeCraft=player.hasItem(craft.name)?player.getItemAmount(craft.name):0;});
            tap(InputProcessor.keyboardA); screen(DrawCraftsMenu.SelectAmount.class,"16-craft-quantity.png");
            tap(InputProcessor.keyboardUp);
            action("quantity input",()->require(DrawCraftsMenu.SelectAmount.amount==2,"Craft amount did not follow input"));
            tap(InputProcessor.keyboardA); screen(DrawCraftsMenu.Selected.class,"17-craft-confirm.png");
            tap(InputProcessor.keyboardA);
            waitFor("craft completed",()->active(DrawCraftsMenu.class)!=null,800,true);
            action("craft rules",()->require(player.getItemAmount(craft.name)==beforeCraft+craft.amount*2,"Original recipe output incorrect"));
            tap(InputProcessor.keyboardB); world();
            action("NPC encounter 1",this::spawnEncounter);
            battleMenu("tl"); screen(DrawBattleMenuNormal.class,"18-battle-commands.png");
            tap(InputProcessor.keyboardA); screen(DrawAttacksMenu.class,"19-battle-attacks.png");
            add("select non lethal Leer",180,null,()->{
                int target=Arrays.asList(player.currPokemon.attacks).indexOf("leer"); require(target>=0,"Starter has no Leer for deterministic battle test");
                pressed=DrawAttacksMenu.curr==target?-1:pulse(InputProcessor.keyboardDown);
            },()->"leer".equals(player.currPokemon.attacks[DrawAttacksMenu.curr]),()->pressed=-1);
            delay(3); action("record turn",()->{oldTurn=battle.turnNumber;oldHp=player.currPokemon.currentStats.get("hp");});
            tap(InputProcessor.keyboardA);
            waitFor("original turn resolved",()->active(DrawBattleMenuNormal.class)!=null && battle.turnNumber>oldTurn,1500,true);
            action("turn and health",()->System.out.println("UI: original turn advanced "+oldTurn+"->"+battle.turnNumber+", friendly HP "+oldHp+"->"+player.currPokemon.currentStats.get("hp")));
            battleMenu("tl");tap(InputProcessor.keyboardA);
            waitFor("attack menu for damage",()->active(DrawAttacksMenu.class)!=null,180,false);
            add("select Low Kick",180,null,()->pressed="low kick".equals(player.currPokemon.attacks[DrawAttacksMenu.curr])?-1:pulse(InputProcessor.keyboardUp),
                ()->"low kick".equals(player.currPokemon.attacks[DrawAttacksMenu.curr]),()->pressed=-1);
            delay(3);action("record HP",()->{oldOppHp=wild.currentStats.get("hp");oldTurn=battle.turnNumber;});tap(InputProcessor.keyboardA);
            waitFor("damage turn resolved",()->active(DrawBattleMenuNormal.class)!=null && battle.turnNumber>oldTurn,1500,true);
            action("damage verified",()->{require(wild.currentStats.get("hp")<oldOppHp && wild.currentStats.get("hp")>0,"Expected original attack damage with surviving target");
                System.out.println("UI: original Low Kick HP "+oldOppHp+"->"+wild.currentStats.get("hp"));screenshot("battle-damage-result.png","DrawBattleMenuNormal");});
            battleMenu("tr"); tap(InputProcessor.keyboardA); screen(DrawPokemonMenu.class,"20-battle-party.png");
            tap(InputProcessor.keyboardB); battleMenu("bl"); tap(InputProcessor.keyboardA);bag(1,"21-battle-bag.png");
            tap(InputProcessor.keyboardB);battleMenu("br");tap(InputProcessor.keyboardA);world();
            action("flee verified",()->{require(player.pokemon.size()==1,"Flee changed party");require(!wild.inBattle,"Enemy still in battle"); screenshot("22-after-flee.png",null);});
            action("NPC encounter 2",this::spawnEncounter); battleMenu("bl");tap(InputProcessor.keyboardA);bag(1,null);
            add("choose Master Ball",160,null,()->{
                DrawItemMenuGen2 m=active(DrawItemMenuGen2.class);
                pressed=m!=null && !m.itemsList.get(m.currIndex+m.cursorPos).equalsIgnoreCase("master ball")?pulse(InputProcessor.keyboardDown):-1;
            },()->{DrawItemMenuGen2 m=active(DrawItemMenuGen2.class);return m!=null && m.itemsList.get(m.currIndex+m.cursorPos).equalsIgnoreCase("master ball");},()->pressed=-1);
            delay(4);tap(InputProcessor.keyboardA);screen(DrawUseTossMenu.class,"23-capture-command.png");tap(InputProcessor.keyboardA);
            waitFor("capture completed",()->playerCanMove && battle.drawAction==null && player.pokemon.size()==2,2400,true);
            delay(30);action("capture result",()->{require(!player.hasItem("master ball")||player.getItemAmount("master ball")==0,"Ball not consumed");require(player.pokemon.contains(wild),"Captured Pokemon missing");screenshot("24-after-capture.png",null);});
            action("save and reload UI fixture",this::saveReload);
            delay(60);action("save round trip screenshot",()->screenshot("25-loaded.png",null));
            supplementalScreens();
            action("finish",this::finish);
        }

        void supplementalScreens() {
            // Extend only this disposable, already saved fixture. No production
            // state or user save is edited to make a UI scenario reachable.
            action("prepare storage fixture",()->{
                Vector2 pos=player.position.cpy().add(0,16);
                storage=new Tile("sand1","chest1",pos,true);storage.routeBelongsTo=new Route("",2);
                String[] species={"machop","caterpie","pidgey","pikachu","bulbasaur","squirtle","charmander","eevee"};
                for(String speciesName:species)storage.routeBelongsTo.storedPokemon.add(new Pokemon(speciesName,8));
                map.tiles.put(pos,storage);player.dirFacing="up";
            });
            tap(InputProcessor.keyboardA);screen(DrawPokemonMenu.class,"26-storage-party.png");
            tap(InputProcessor.keyboardRight);screen(DrawPokemonMenu.class,"27-storage-six-rows.png");
            action("storage contents",()->require(DrawPokemonMenu.allPokemon==storage.routeBelongsTo.storedPokemon && DrawPokemonMenu.allPokemon.size()==8,"Storage list not selected"));
            add("scroll eight stored Pokemon",160,null,()->pressed=pulse(InputProcessor.keyboardDown),
                ()->DrawPokemonMenu.currIndex+DrawPokemonMenu.scrollIndex==7,()->pressed=-1);
            delay(12);action("storage scrolled",()->{require(DrawPokemonMenu.scrollIndex>0,"Storage did not scroll");screenshot("28-storage-scrolled.png","DrawPokemonMenu");});
            action("nickname target",()->renamed=DrawPokemonMenu.allPokemon.get(7));
            tap(InputProcessor.keyboardA);screen(DrawPokemonMenu.SelectedMenu.class,"29-storage-actions.png");
            add("select nickname",160,null,()->pressed=pulse(InputProcessor.keyboardDown),
                ()->{DrawPokemonMenu.SelectedMenu m=active(DrawPokemonMenu.SelectedMenu.class);return m!=null && m.words.get(m.curr).equals("NICKNAME");},()->pressed=-1);
            delay(3);tap(InputProcessor.keyboardA);screen(Pokemon.SetNickname.class,"30-nickname.png");
            // The original nickname editor prepopulates the species name.
            for(int i=0;i<"eevee".length();i++)tap(Input.Keys.BACKSPACE);
            for(int key:new int[]{Input.Keys.N,Input.Keys.O,Input.Keys.V,Input.Keys.A})tap(key);
            delay(8);action("nickname text",()->screenshot("31-nickname-typed.png","SetNickname"));
            tap(Input.Keys.ENTER);screen(DrawYesNoMenu.class,"32-nickname-confirm.png");tap(InputProcessor.keyboardA);
            waitFor("nickname committed",()->active(DrawPokemonMenu.class)!=null,240,true);
            action("nickname rules",()->require(renamed.nickname.equals("nova"),"Nickname keyboard input was not committed: "+renamed.nickname));
            tap(InputProcessor.keyboardB);world();
            action("long original dialogue",()->{
                playerCanMove=false;
                insertAction(new DisplayText(this,"Explore the island with your partner. Gather supplies, build a campfire, and discover new habitats. Your journey continues beyond the shore.",null,false,true,new SetField(this,"playerCanMove",true,null)));
            });
            waitFor("dialogue first page",()->active(DisplayText.class)!=null && active(DisplayText.class).spritesBeingDrawn.size()>=37,500,false);
            action("dialogue first page screenshot",()->screenshot("33-dialogue-first-page.png","DisplayText"));
            tap(InputProcessor.keyboardA);delay(80);action("dialogue scroll screenshot",()->screenshot("34-dialogue-scrolled.png","DisplayText"));
            world();
        }

        void fixture() {
            actionStack.removeIf(a->a instanceof DrawSetupMenu);
            Gdx.input.setInputProcessor(null);
            server=new Server(); Network.register(server);
            map=new PkmnMap("ui-fixture");
            // Reproducible disposable fixture, using the game's original RNGs.
            Game.rand.setSeed(73911L);map.rand.setSeed(73911L);
            for(int y=-320;y<=320;y+=16)for(int x=-320;x<=320;x+=16){Vector2 p=new Vector2(x,y);map.tiles.put(p,new Tile(Math.abs(y)<=32?"sand1":"green1",p.cpy(),true));}
            Vector2 fire=new Vector2(0,16);map.tiles.put(fire,new Tile("sand1","campfire1",fire.cpy(),true));
            map.bottomLeft=new Vector2(-320,-320);map.topRight=new Vector2(320,320);
            map.minimap=new Pixmap(80,80,Pixmap.Format.RGBA8888);map.minimap.setColor(.3f,.6f,.3f,1);map.minimap.fill();
            player.position.set(0,0);player.spawnLoc.set(0,0);player.name="UiTest";
            cam.position.set(0,8,0);start();player.setCurrPokemon();
            for(Player.Craft c:Player.crafts)for(Player.Craft req:c.requirements)player.setItemAmount(req.name,100);
            player.setItemAmount("master ball",1);player.setItemAmount("moomoo milk",2);
            insertAction(new DrawControls());fixture=true;
            System.out.println("UI: isolated original game fixture ready; starter moves="+Arrays.toString(player.currPokemon.attacks));
        }

        void spawnEncounter() {
            if(wild!=null){wild.aggroPlayer=false;wild.removeDrawActions(this);if(wild.standingAction!=null)actionStack.remove(wild.standingAction);map.pokemon.values().removeIf(p->p==wild);}
            wild=new Pokemon(encounter==0 ? "caterpie" : "sprigatito",4);wild.position.set(player.position.cpy().add(32,0));wild.mapTiles=map.tiles;
            wild.aggroPlayer=true;wild.dirFacing="left";insertAction(wild.new Standing());
            encounter++;trackBattle=true;stageFrame=0;
            System.out.println("UI: NPC "+encounter+" placed two tiles away; original aggressive AI starts encounter");
        }

        @Override public void render() {
            Gdx.input=controlledInput;
            justPressed=pressed!=lastPressed?pressed:-1;lastPressed=pressed;
            super.render(); frames++;
            int error=Gdx.gl.glGetError();require(error==GL20.GL_NO_ERROR,"GL error "+error);
            if(fixture)monitorPresentation();
            if(current==null){current=steps.poll();if(current==null)return;System.out.println("UI STEP "+current.name);if(current.enter!=null)current.enter.run();}
            if(current.tick!=null)current.tick.run();
            if(current.done.getAsBoolean()){if(current.exit!=null)current.exit.run();current=null;}
            else if(++current.age>current.timeout)throw new IllegalStateException("Timeout: "+current.name+"; active="+actionNames());
        }

        void monitorPresentation() {
            Object johto=field("johtoRenderer"),br=field("johtoBattleRenderer");
            long wf=number(johto,"getRenderedFrames"),bf=number(br,"getBattleFrames"),tf=number(br,"getTransitionFrames");
            // Model rendering must remain disabled in this sprite presentation.
            require(number(johto,"getPlayerModelFrames")==0 && number(johto,"getPokemonModelFrames")==0
                && number(johto,"getLoadedSpeciesCount")==0,"3D actor models active or loaded");
            if(trackBattle){
                String phase=String.valueOf(call(br,"getPhase"));
                GainExpAnimationGen2.ShowLevelUpStats levelUp=active(GainExpAnimationGen2.ShowLevelUpStats.class);
                if(levelUp!=null && levelUp.timer>=30 && !screenshots.contains("capture-level-up.png"))
                    screenshot("capture-level-up.png","ShowLevelUpStats");
                if(active(CatchPokemonWobblesThenCatch.class)!=null) {
                    int count=transitionSamples.getOrDefault("capture-animation",0)+1;
                    transitionSamples.put("capture-animation",count);
                    if(count==20)screenshot("capture-animation.png",null);
                }
                DepleteEnemyHealth enemyHp=active(DepleteEnemyHealth.class);
                if(enemyHp!=null && !enemyHp.firstStep && battle.drawAction.drawEnemyHealthAction.currHealth>enemyHp.targetSize
                        && !screenshots.contains("battle-enemy-hp-animation.png"))
                    screenshot("battle-enemy-hp-animation.png",null);
                DepleteFriendlyHealth friendlyHp=active(DepleteFriendlyHealth.class);
                if(friendlyHp!=null && !friendlyHp.firstStep && battle.drawAction.drawFriendlyHealthAction.currHealth>friendlyHp.targetSize
                        && !screenshots.contains("battle-friendly-hp-animation.png"))
                    screenshot("battle-friendly-hp-animation.png",null);
                if(bf>priorBattle)battleRendered++;
                if(tf>priorTransition){
                    transitionRendered++;
                    String key=encounter+"-"+phase;
                    int count=transitionSamples.getOrDefault(key,0)+1;transitionSamples.put(key,count);
                    if(count==1||count==12||count==30)screenshot("transition-"+key+"-"+count+".png",null);
                }
                // getIntroAction constructs DrawBattle long before it becomes
                // active. Inspect the actual stack instead of that early field.
                if(active(DrawBattle.class)!=null && active(DrawPokemonMenu.class)==null && active(DrawItemMenuGen2.class)==null
                        && active(DrawStatsScreen.class)==null && active(DrawUseTossMenu.class)==null){
                    require(bf>priorBattle || tf>priorTransition,"Battle frame fell back to stale/world renderer; phase="+phase);
                }
                if(bf>priorBattle && wf>priorWorld)System.out.println("UI: world and battle both rendered at transition boundary frame="+frames+" phase="+phase);
            }
            priorWorld=wf;priorBattle=bf;priorTransition=tf;
        }

        void screenshot(String filename,String screen) {
            long modern=0;
            if(screen!=null){
                Object ui=field("modernUi");modern=((Number)call(ui,"getScreenDrawCount",new Class<?>[]{String.class},screen)).longValue();
                require(modern>0,"Modern presentation never rendered "+screen);checkedScreens.add(screen);
            }
            byte[] rgba=ScreenUtils.getFrameBufferPixels(0,0,640,576,false);
            BufferedImage image=new BufferedImage(640,576,BufferedImage.TYPE_INT_RGB);Set<Integer> colors=new HashSet<>();
            for(int i=0;i<rgba.length;i+=4){int rgb=(rgba[i]&255)<<16|(rgba[i+1]&255)<<8|rgba[i+2]&255;colors.add(rgb);image.setRGB(i/4%640,575-i/4/640,rgb);}
            if(!filename.startsWith("transition"))require(colors.size()>12,"Almost empty framebuffer: "+filename+" colors="+colors.size());
            try{require(ImageIO.write(image,"png",new File(filename)),"PNG unavailable");}catch(IOException e){throw new IllegalStateException(e);}
            screenshots.add(filename);report.println(frames+"\t"+(screen==null?"world/battle":screen)+"\t"+modern+"\t"+colors.size()+"\t"+filename);report.flush();
        }

        void saveReload() {
            require(player.pokemon.size()==2,"Party before save");
            Gdx.files.local("ui-fixture.sav").mkdirs();Gdx.files.local("ui-fixture.sav/.smoke-harness").writeString("Isolated UI smoke fixture.\n",false);
            saveGame();Vector2 saved=player.position.cpy();int tiles=map.tiles.size();Pixmap old=map.minimap;
            actionStack.clear();insertAction(new InputProcessor());map=new PkmnMap("ui-fixture");player=new Player();start();map.loadFromFile(this);old.dispose();
            require(map.tiles.size()==tiles && player.position.equals(saved),"UI fixture world failed save/load");
            require(player.pokemon.size()==2 && player.pokemon.get(1).specie.name.equals("sprigatito"),"Captured Gen IX party failed save/load");
            trackBattle=false;
        }
        void finish() {
            require(PmdPokemonSprites.get().isAvailable(), "PMD catalog was not packaged");
            require(PmdBattleSprites.getActorDraws()>100, "PMD battle actors were not rendered");
            require(PmdBattleSprites.getPortraitDraws()>20, "PMD party portraits were not rendered");
            System.out.println("PMD: battle="+PmdBattleSprites.getActorDraws()+", portraits="+PmdBattleSprites.getPortraitDraws()
                +", sheets="+PmdPokemonSprites.get().getLoadedTextureCount()+", textureBytes="+PmdPokemonSprites.get().getLoadedTextureBytes());
            require(transitionRendered>60 && battleRendered>100,"Modern battle renderer was not exercised");
            long snapshotFrames=number(field("johtoBattleRenderer"),"getSnapshotFrames");
            require(snapshotFrames>60,"Transitions never drew the preserved 2.5D world snapshot");
            require(checkedScreens.size()>=13,"Insufficient modern UI screens "+checkedScreens);
            require(screenshots.contains("battle-enemy-hp-animation.png") && screenshots.contains("battle-friendly-hp-animation.png"),"Interpolated HP animations were not captured");
            screenshot("35-final-world.png",null);
            System.out.println("UI: frames="+frames+", screens="+checkedScreens+", battleFrames="+battleRendered+", transitionFrames="+transitionRendered+", snapshotFrames="+snapshotFrames+", PNGs="+screenshots.size());
            System.out.println("UI: playerModelFrames="+number(field("johtoRenderer"),"getPlayerModelFrames")
                +", pokemonModelFrames="+number(field("johtoRenderer"),"getPokemonModelFrames")
                +", loadedModelSpecies="+number(field("johtoRenderer"),"getLoadedSpeciesCount"));
            complete=true;Gdx.app.exit();
        }
        String actionNames(){List<String> names=new ArrayList<>();for(Action a:actionStack)names.add(a.getClass().getSimpleName());return names.toString();}
        Object field(String name){try{Field f=Game.class.getDeclaredField(name);f.setAccessible(true);Object o=f.get(this);require(o!=null,"Missing renderer "+name);return o;}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
        long number(Object target,String method){return ((Number)call(target,method)).longValue();}
        Object call(Object target,String name){return call(target,name,new Class<?>[0]);}
        Object call(Object target,String name,Class<?>[] types,Object...args){try{return target.getClass().getMethod(name,types).invoke(target,args);}catch(ReflectiveOperationException e){throw new IllegalStateException(name,e);}}
        void require(boolean valid,String message){if(!valid)throw new IllegalStateException(message);}
        @Override public void dispose(){try{super.dispose();}finally{if(report!=null)report.close();disposed=true;}}
    }
}
