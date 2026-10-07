package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Integration fixture; all normal frames execute the delivered Game.render. */
public final class VerticalSliceTest {
    private static final int WIDTH=1280, HEIGHT=720;
    private static void require(boolean value,String message) { if(!value)throw new IllegalStateException(message); }
    public static void main(String[] args) {
        Game.leakTracer=LeakTracer.NoOp.INSTANCE;
        Fixture game=new Fixture();
        Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
        cfg.setInitialVisible(false);cfg.setWindowedMode(WIDTH,HEIGHT);cfg.setForegroundFPS(120);cfg.useVsync(false);
        try {
            new Lwjgl3Application(game,cfg);
            require(game.complete&&game.disposed,"Incomplete native fixture lifecycle");
            System.out.println("VERTICAL SLICE PASS: ramp traversal, furnished room, original building travel/collision, inventory, original Surf animation, repeated battle/flee, save/load and render invariants");
            System.exit(0);
        } catch(Throwable error) { error.printStackTrace();System.exit(1); }
    }

    private static final class CountingRandom extends Random {
        long calls;
        CountingRandom(long seed){super(seed);}
        @Override protected int next(int bits){calls++;return super.next(bits);}
    }
    private static final class Step {
        final String name;final int limit;final Runnable enter,tick,exit;final BooleanSupplier done;int age;
        Step(String n,int l,Runnable a,Runnable b,BooleanSupplier c,Runnable d){name=n;limit=l;enter=a;tick=b;done=c;exit=d;}
    }
    private static final class Probe extends Action {
        int steps;
        @Override public void step(Game game){steps++;}
    }
    private static final class Fixture extends Game {
        final ArrayDeque<Step> plan=new ArrayDeque<>();
        final Map<String,List<Long>> times=new LinkedHashMap<>();
        final ArrayList<Map<String,Object>> cycles=new ArrayList<>();
        final CountingRandom simulationRandom=new CountingRandom(781921),mapRandom=new CountingRandom(99871);
        final Probe probe=new Probe();
        JohtoRenderer world;
        Input nativeInput,controlled;
        Step current;
        int frame,pressed=-1,last=-1,just=-1,captures;
        long previousWorld,previousBattle,previousTransition,readOnlyChecks,allocationBytes;
        Battle.LoadAndPlayAnimation animation;
        float lastHeight=Float.NaN,highestWalked;
        boolean ready,complete,disposed,animationDone,trackWalk,surfCaptured;
        PrintWriter frameLog;
        com.sun.management.ThreadMXBean allocationBean;
        final Vector2 door=new Vector2(160,0);
        Fixture(){super(new String[0],4);}

        @Override public void create() {
            super.create();
            require("johto".equals(System.getProperty("pokewilds.visual")),"Modern mode required");
            nativeInput=Gdx.input;
            controlled=(Input)Proxy.newProxyInstance(Input.class.getClassLoader(),new Class<?>[]{Input.class},(p,m,a)->{
                switch(m.getName()) {
                    case "isKeyPressed": return pressed>=0&&(Integer)a[0]==pressed;
                    case "isKeyJustPressed": return just>=0&&(Integer)a[0]==just;
                    case "isButtonPressed": return (Integer)a[0]==Input.Buttons.LEFT?pressed==DesktopControls.MOUSE_LEFT:(Integer)a[0]==Input.Buttons.RIGHT&&pressed==DesktopControls.MOUSE_RIGHT;
                    case "isButtonJustPressed": return (Integer)a[0]==Input.Buttons.LEFT?just==DesktopControls.MOUSE_LEFT:(Integer)a[0]==Input.Buttons.RIGHT&&just==DesktopControls.MOUSE_RIGHT;
                    case "isTouched": return false;
                    default: try{return m.invoke(nativeInput,a);}catch(InvocationTargetException e){throw e.getCause();}
                }
            });
            Gdx.input=controlled;Gdx.input.setInputProcessor(null);Game.gamepad=null;
            actionStack.clear();insertAction(new InputProcessor());server=new Server();Network.register(server);
            world=(JohtoRenderer)field(this,Game.class,"johtoRenderer");
            Game.rand=simulationRandom;map=new PkmnMap("vertical-slice");map.rand=mapRandom;
            Route route=new Route("mountain1",10);
            for(int y=-18;y<=18;y++)for(int x=-18;x<=22;x++) {
                String upper=ring(x,y,5),name="mountain3";
                if(y==-5&&Math.abs(x)<=1){name="ledge_grass_ramp";upper="";}
                if(x==3&&y>=-7&&y<=-3)name=y==-5?"waterfall_N":"water1";
                Tile tile=new Tile(name,upper,new Vector2(x*16,y*16),true,route);
                tile.biome="mountain";map.tiles.put(tile.position.cpy(),tile);
            }
            Tile entrance=new Tile("house5_floor1","house1_door1",door.cpy(),true,route);
            map.tiles.put(door.cpy(),entrance);
            HashMap<Vector2,Tile> inside=map.getInteriorLayer(0);map.interiorTilesIndex=0;
            for(int y=0;y<=7;y++)for(int x=6;x<=14;x++) {
                boolean edge=x==6||x==14||y==0||y==7;
                String upper=edge?"house5_wall1":"";
                if(x==10&&y==0)upper="house1_door1";
                if(x==10&&y==7)upper="house_window1";
                Tile tile=new Tile("house5_floor1",upper,new Vector2(x*16,y*16),true,route);
                tile.biome="interior";inside.put(tile.position.cpy(),tile);
            }
            String[] furniture={"house_stool1","house_table1_default","house_bed1","house_couch1","house_shelf1","house_wardrobe1","house_vanity1","house_plant1"};
            int[][] positions={{8,3},{10,3},{7,5},{12,3},{9,6},{12,6},{13,5},{7,3}};
            for(int i=0;i<furniture.length;i++) {
                Tile t=new Tile("house5_floor1",furniture[i],new Vector2(positions[i][0]*16,positions[i][1]*16),true,route);
                t.biome="interior";inside.put(t.position.cpy(),t);
            }
            map.bottomLeft=new Vector2(-288,-288);map.topRight=new Vector2(352,288);
            map.minimap=new Pixmap(80,80,Pixmap.Format.RGBA8888);map.minimap.setColor(.4f,.5f,.4f,1);map.minimap.fill();
            player.position.set(0,-112);player.spawnLoc.set(player.position);player.name="Slice";cam.position.set(8,-72,0);
            start();player.setCurrPokemon();actionStack.removeIf(a->a instanceof CycleDayNight);
            map.timeOfDay="day";mapBatch.setColor(Color.WHITE);insertAction(probe);
            player.setItemAmount("log",12);player.setItemAmount("moomoo milk",4);player.setItemAmount("pokeball",8);
            try{frameLog=new PrintWriter("frame-times.csv","UTF-8");}catch(Exception e){throw new RuntimeException(e);}
            frameLog.println("frame,phase,cpu_nanoseconds,thread_allocated_bytes,world_frames,battle_frames,transition_frames");
            Object bean=ManagementFactory.getThreadMXBean();
            if(bean instanceof com.sun.management.ThreadMXBean) {
                allocationBean=(com.sun.management.ThreadMXBean)bean;
                if(allocationBean.isThreadAllocatedMemorySupported())allocationBean.setThreadAllocatedMemoryEnabled(true);else allocationBean=null;
            }
            ready=true;buildPlan();
        }

        void add(String n,int limit,Runnable enter,Runnable tick,BooleanSupplier done,Runnable exit){plan.add(new Step(n,limit,enter,tick,done,exit));}
        void action(String n,Runnable run){add(n,3,run,null,()->true,null);}
        void delay(String n,int frames){add(n,frames+10,null,null,()->current.age>=frames,null);}
        void tap(int key){add("tap",10,()->pressed=key,()->{if(current.age>0)pressed=-1;},()->current.age>=4,()->pressed=-1);}
        void waitFor(String n,BooleanSupplier done,int limit,boolean dialogue){add(n,limit,null,()->{if(dialogue)pressed=active(DisplayText.class)!=null&&current.age%8==0?InputProcessor.keyboardA:-1;},done,()->pressed=-1);}
        <T extends Action>T active(Class<T> cls){for(Action a:actionStack)if(cls.isInstance(a)&&(!(a instanceof Menu)||!((Menu)a).disabled))return cls.cast(a);return null;}
        void buildPlan() {
            delay("outdoor-warmup",50);
            action("terrain-contract",()->{
                WorldElevation e=world.getElevation();require(Math.abs(e.height(8,0)-14)<.01,"Fixture plateau lost height");
                require(world.getWaterfallFrames()>0,"Connected waterfall never rendered");
                require(map.tiles.get(new Vector2(48,-64)).isWater&&map.tiles.get(new Vector2(48,-96)).isWater,"Waterfall lacks connected water");
                require(e.height(56,-64)>e.height(56,-96)+10,"Waterfall has no real drop");screenshot("01-mountain-ramp-waterfall.png");
            });
            add("walk-ramp",240,()->{trackWalk=true;pressed=InputProcessor.keyboardUp;},null,()->player.position.y>=-48,()->{pressed=-1;trackWalk=false;require(highestWalked>13.9f,"Player never reached raised terrace");});
            delay("on-plateau",30);action("render-read-only",()->{pureRendering();screenshot("02-on-plateau.png");});
            action("enter-house",()->travel(true));
            waitFor("house-travel",()->map.tiles!=map.overworldTiles&&active(EnterBuilding.class)==null&&active(PlayerMoving.class)==null,200,false);
            delay("interior-warmup",30);action("furniture-contract",()->{interiorContract();screenshot("03-furnished-interior.png");});
            action("prepare-wall-collision",()->{player.position.set(112,16);cam.position.set(160,48,0);player.dirFacing="left";});
            add("wall-collision",65,()->pressed=InputProcessor.keyboardLeft,null,()->current.age>=45,()->{pressed=-1;require(player.position.equals(new Vector2(112,16)),"Original wall collision allowed passage: "+player.position);});
            delay("front-wall-idle",12);action("front-wall-visibility",()->{actorVisibility();pureRendering();});
            action("inventory-open",()->{playerCanMove=false;insertAction(new DrawItemMenuGen2(this,null));});
            delay("inventory",35);action("inventory-capture",()->{require(modernUi.getScreenDrawCount("DrawItemMenuGen2")>0,"Modern inventory not drawn");screenshot("05-inventory.png");});
            tap(InputProcessor.keyboardB);waitFor("inventory-close",()->active(DrawItemMenuGen2.class)==null,150,false);
            action("save-round-trip",this::saveRoundTrip);delay("loaded-interior",30);action("loaded-capture",()->screenshot("06-interior-reloaded.png"));
            action("exit-house",()->travel(false));
            waitFor("outside-travel",()->map.tiles==map.overworldTiles&&active(EnterBuilding.class)==null&&active(PlayerMoving.class)==null,200,false);
            for(int i=0;i<3;i++) {
                final int cycle=i;
                delay("world-before-battle-"+i,30);action("battle-start-"+i,this::beginBattle);
                waitFor("battle-intro-"+i,()->active(DrawBattleMenuNormal.class)!=null,1300,true);
                delay("battle-warm-"+i,30);action("battle-scene-"+i,()->{if(cycle==0)screenshot("07-battle.png");playSurf();});
                waitFor("authored-surf-"+i,()->animationDone,800,false);
                action("effect-assert-"+i,()->{require(animation.presentation!=null&&animation.presentation.getScreenFrames()>0,"Original Surf never reached full-viewport effect path");DrawBattleMenuNormal menu=activeMenu();menu.disabled=false;menu.curr="br";});
                tap(InputProcessor.keyboardA);
                waitFor("battle-flee-"+i,()->battle.drawAction==null&&playerCanMove,1500,true);
                delay("world-after-battle-"+i,45);action("cache-cycle-"+i,()->{recordCycle(cycle);if(cycle==0)screenshot("09-return-to-world.png");});
            }
            action("finish",this::finish);
        }
        DrawBattleMenuNormal activeMenu(){for(Action a:actionStack)if(a instanceof DrawBattleMenuNormal)return(DrawBattleMenuNormal)a;throw new IllegalStateException("Battle menu lost");}
        void beginBattle(){
            playerCanMove=false;pressed=-1;battle.oppPokemon=new Pokemon("pikachu",3);battle.oppPokemon.position.set(player.position.cpy().add(32,0));
            battle.oppPokemon.mapTiles=map.tiles;musicController.startBattle="wild";insertAction(Battle.getIntroAction(this));
        }
        void playSurf(){
            activeMenu().disabled=true;animationDone=false;Game.battleAnims=true;
            animation=new Battle.LoadAndPlayAnimation(this,"surf",battle.oppPokemon,new RunCode(()->animationDone=true,null));
            insertAction(animation);
        }
        void travel(boolean enter){
            pressed=-1;player.position.set(door);player.dirFacing=enter?"up":"down";cam.position.set(160,48,0);playerCanMove=false;
            actionStack.removeIf(a->a instanceof PlayerStanding||a instanceof PlayerMoving);
            Action after=new SetField(this,"playerCanMove",true,new PlayerMoving(this,player,false,new PlayerStanding(this)));
            insertAction(new EnterBuilding(this,enter?"enter":"exit",enter?map.interiorTiles.get(0):map.overworldTiles,after));
        }
        void interiorContract(){
            Set<String> families=new HashSet<>();
            for(Tile t:map.tiles.values()) {
                String object=BwAssets.objectName(t);
                if(t.nameUpper.startsWith("house_")&&!t.nameUpper.contains("window")) {
                    require(object!=null&&!object.equals("wall"),"Furniture misclassified as a wall: "+t.nameUpper);families.add(object);
                }
            }
            require(families.containsAll(Arrays.asList("chair","table","bed","couch","shelf","wardrobe","desk","pot")),"Incomplete furnished room: "+families);
            require(!map.tiles.get(door).isSolid,"Door opening blocks passage");
            for(Vector2 corner:Arrays.asList(new Vector2(96,0),new Vector2(224,0),new Vector2(96,112),new Vector2(224,112)))require(map.tiles.get(corner).isSolid,"Open room corner "+corner);
            require("window".equals(BwAssets.objectName(map.tiles.get(new Vector2(160,112)))),"Window is not a structural window");
        }
        void pureRendering(){
            long fingerprint=ModernWorldGenerator.fingerprint(map.tiles),rng=simulationRandom.calls,mapRng=mapRandom.calls;
            int ticks=probe.steps;Vector2 position=player.position.cpy();ArrayList<Action> actions=new ArrayList<>(actionStack);
            for(int i=0;i<3;i++)require(world.render(this),"Direct world render unavailable");
            require(fingerprint==ModernWorldGenerator.fingerprint(map.tiles)&&position.equals(player.position)&&actions.equals(actionStack),"Drawing changed persistent world/action state");
            require(probe.steps==ticks&&rng==simulationRandom.calls&&mapRng==mapRandom.calls,"Drawing advanced actions or simulation RNG");readOnlyChecks++;
        }
        void actorVisibility(){
            Vector2 present=player.position.cpy();Map<Vector2,Tile> actualTiles=map.tiles;
            Pixmap actual=null,reference=null,background=null;
            try{
                require(world.render(this),"Interior rendering unavailable");
                actual=ScreenUtils.getFrameBufferPixmap(0,0,WIDTH,HEIGHT);writeImage(actual,"04-front-wall-player.png");
                PerspectiveCamera camera=(PerspectiveCamera)field(world,JohtoRenderer.class,"camera");
                Vector3 contact=camera.project(new Vector3(present.x+8,.15f,-present.y-7),0,0,WIDTH,HEIGHT);
                // Test-only counterfactual: remove just the foreground wall to
                // establish the full actor silhouette and prove occlusion cause.
                Map<Vector2,Tile> unobstructed=new HashMap<>(actualTiles);
                for(Tile tile:actualTiles.values())if(tile.position.y==0&&"wall".equals(BwAssets.objectName(tile))){
                    Tile floor=new Tile(tile.name,"",tile.position.cpy(),true,tile.routeBelongsTo);floor.biome=tile.biome;
                    unobstructed.put(floor.position.cpy(),floor);
                }
                map.tiles=unobstructed;require(world.render(this),"Reference interior unavailable");
                reference=ScreenUtils.getFrameBufferPixmap(0,0,WIDTH,HEIGHT);writeImage(reference,"04-reference-without-front-wall.png");
                player.position.set(10000,10000);require(world.render(this),"Reference background unavailable");
                background=ScreenUtils.getFrameBufferPixmap(0,0,WIDTH,HEIGHT);
                int left=Math.max(0,(int)contact.x-80),right=Math.min(WIDTH,(int)contact.x+80);
                int bottom=Math.max(0,(int)contact.y-16),top=Math.min(HEIGHT,(int)contact.y+180);
                ArrayList<int[]> mask=new ArrayList<>();int minY=HEIGHT,maxY=0;
                for(int y=bottom;y<top;y++)for(int x=left;x<right;x++){
                    int contrast=colorDistance(reference.getPixel(x,y),background.getPixel(x,y));
                    // Strong foreground contrast excludes the soft ground shadow.
                    if(contrast>36){mask.add(new int[]{x,y,contrast});minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
                }
                require(mask.size()>100&&maxY-minY>30,"Could not identify a full reference actor silhouette");
                int[] expected=new int[3],visible=new int[3];
                for(int[] pixel:mask){
                    float fraction=(float)(pixel[1]-minY)/(maxY-minY+1);
                    int band=fraction<.25f?0:fraction<.60f?1:2;expected[band]++;
                    int difference=colorDistance(actual.getPixel(pixel[0],pixel[1]),reference.getPixel(pixel[0],pixel[1]));
                    if(difference<=Math.max(15,pixel[2]*.4f))visible[band]++;
                }
                System.out.println("VERTICAL OCCLUSION feet="+visible[0]+"/"+expected[0]+" torso="+visible[1]+"/"+expected[1]+" head="+visible[2]+"/"+expected[2]);
                require(expected[0]>25&&expected[1]>25,"Reference lacks feet or torso pixels");
                require(visible[0]>=expected[0]*.70f&&visible[1]>=expected[1]*.70f,
                    "Foreground wall hides feet/torso: feet="+visible[0]+"/"+expected[0]+" torso="+visible[1]+"/"+expected[1]);
            }finally{
                player.position.set(present);map.tiles=actualTiles;
                if(actual!=null)actual.dispose();if(reference!=null)reference.dispose();if(background!=null)background.dispose();
                require(world.render(this),"Could not restore actor fixture");
            }
        }
        int colorDistance(int a,int b){int result=0;for(int shift:new int[]{8,16,24})result=Math.max(result,Math.abs((a>>>shift&255)-(b>>>shift&255)));return result;}
        Map<String,String> savedIdentity(Map<Vector2,Tile> tiles){
            TreeMap<String,String> result=new TreeMap<>();
            for(Tile t:tiles.values())result.put(t.position.toString(),t.name+"|"+t.nameUpper+"|"+t.isSolid+"|"+t.biome);
            return result;
        }
        void saveRoundTrip(){
            Map<String,String> exterior=savedIdentity(map.overworldTiles),interior=savedIdentity(map.interiorTiles.get(0));
            Vector2 before=player.position.cpy();Map<String,Integer> inventory=new HashMap<>(player.getItemsDict());String species=player.currPokemon.specie.name;
            Gdx.files.local("vertical-slice.sav").mkdirs();
            Gdx.files.local("vertical-slice.sav/.fixture").writeString("Isolated native integration fixture; no private save data.\n",false);
            saveGame();Pixmap old=map.minimap;
            actionStack.clear();insertAction(new InputProcessor());map=new PkmnMap("vertical-slice");player=new Player();start();map.loadFromFile(this);old.dispose();
            require(exterior.equals(savedIdentity(map.overworldTiles)),"Exterior changed after save/load");
            require(interior.equals(savedIdentity(map.interiorTiles.get(0))),"Furniture/structure changed after save/load");
            require(before.equals(player.position)&&inventory.equals(player.getItemsDict())&&species.equals(player.currPokemon.specie.name),"Player/inventory changed after save/load");
            actionStack.removeIf(a->a instanceof CycleDayNight);map.timeOfDay="day";mapBatch.setColor(Color.WHITE);map.rand=mapRandom;insertAction(probe);interiorContract();
            System.out.println("VERTICAL SAVE PASS: original serializer preserved exterior, room, collision, furniture, inventory and player");
        }
        @Override public void render(){
            if(complete)return;
            Gdx.input=controlled;just=pressed!=last?pressed:-1;last=pressed;
            long beforeAllocation=allocated(),started=System.nanoTime();super.render();long elapsed=System.nanoTime()-started;
            long allocated=Math.max(0,allocated()-beforeAllocation);allocationBytes+=allocated;frame++;
            require(Gdx.gl.glGetError()==GL20.GL_NO_ERROR,"GL error at frame "+frame);
            if(!ready)return;
            long wf=world.getRenderedFrames(),bf=johtoBattleRenderer.getBattleFrames(),tf=johtoBattleRenderer.getTransitionFrames();
            require(((WorldBatch)mapBatch).getSubmittedDraws()==0,"Legacy world submitted pixels at frame "+frame);
            require(wf>previousWorld||bf>previousBattle||tf>previousTransition,"No modern scene at frame "+frame);
            previousWorld=wf;previousBattle=bf;previousTransition=tf;
            String phase=current==null?"setup":current.name;
            times.computeIfAbsent(phase,key->new ArrayList<>()).add(elapsed);
            frameLog.println(frame+","+phase+","+elapsed+","+allocated+","+wf+","+bf+","+tf);
            if(trackWalk){float h=world.getElevation().height(player.position.x+8,player.position.y+8);highestWalked=Math.max(highestWalked,h);if(!Float.isNaN(lastHeight))require(Math.abs(h-lastHeight)<4,"Discontinuous foot elevation while walking: "+lastHeight+"->"+h);lastHeight=h;}
            // The first authored Surf frame is transparent; capture its visible
            // wave instead of mistaking a valid draw call for visual evidence.
            if(phase.equals("authored-surf-0")&&!surfCaptured&&animation.frameNum>=101&&animation.presentation!=null&&animation.presentation.getScreenFrames()>0){
                require(animation.currFrame!=null&&animation.currFrame.getTexture()!=null,"Authored Surf frame unavailable");
                screenshot("08-authored-surf.png");surfCaptured=true;
                System.out.println("VERTICAL SURF CAPTURE: authored frame="+(animation.frameNum-1));
            }
            if(current==null){current=plan.poll();require(current!=null,"Plan ended without completion");System.out.println("VERTICAL STEP "+current.name);if(current.enter!=null)current.enter.run();}
            if(current.tick!=null)current.tick.run();
            if(current.done.getAsBoolean()){if(current.exit!=null)current.exit.run();current=null;}
            else if(++current.age>current.limit)throw new IllegalStateException("Timeout "+current.name+" position="+player.position+" actions="+actionStack);
        }
        long allocated(){return allocationBean==null?0:allocationBean.getThreadAllocatedBytes(Thread.currentThread().getId());}
        void recordCycle(int cycle){
            Map<String,Object> values=new LinkedHashMap<>();values.put("cycle",cycle+1);values.put("managedTextures",Texture.getNumManagedTextures());
            values.put("managedShaders",ShaderProgram.getNumManagedShaderPrograms());
            values.put("pmdSheets",PmdPokemonSprites.get().getLoadedTextureCount());values.put("pmdTextureBytes",PmdPokemonSprites.get().getLoadedTextureBytes());
            values.put("heapUsedBytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());cycles.add(values);System.out.println("VERTICAL CACHE "+values);
        }
        void finish(){
            require(readOnlyChecks>=2&&world.getLoadedSpeciesCount()==0&&surfCaptured,"Missing render/screenshot check or unexpected 3D models");
            require(cycles.size()==3,"Incomplete repeated battles");
            for(String metric:Arrays.asList("managedTextures","managedShaders","pmdSheets","pmdTextureBytes"))require(cycles.get(1).get(metric).equals(cycles.get(2).get(metric)),"Warm repeated battles grew "+metric+": "+cycles);
            screenshot("10-final-world.png");Map<String,Object> result=new LinkedHashMap<>();
            result.put("platform",System.getProperty("os.name")+" "+System.getProperty("os.version")+" "+System.getProperty("os.arch"));
            result.put("java",System.getProperty("java.runtime.version"));result.put("glVendor",Gdx.gl.glGetString(GL20.GL_VENDOR));result.put("glRenderer",Gdx.gl.glGetString(GL20.GL_RENDERER));
            result.put("viewport",Arrays.asList(WIDTH,HEIGHT));result.put("fixedSeeds",Arrays.asList(781921,99871));result.put("frames",frame);result.put("cpuThreadAllocatedBytes",allocationBean==null?"unavailable":allocationBytes);
            result.put("cacheCycles",cycles);result.put("readOnlyRenderChecks",readOnlyChecks);result.put("baselineComparison","none; diagnostic CPU timings may include other host/GPU work; no FPS promise");
            Map<String,Object> timing=new LinkedHashMap<>();
            for(Map.Entry<String,List<Long>> entry:times.entrySet()) {
                ArrayList<Long> values=new ArrayList<>(entry.getValue());Collections.sort(values);double total=0;for(long value:values)total+=value;
                Map<String,Object> stats=new LinkedHashMap<>();stats.put("frames",values.size());stats.put("meanCpuMs",total/values.size()/1e6);stats.put("p95CpuMs",values.get(Math.min(values.size()-1,(int)Math.ceil(values.size()*.95)-1))/1e6);stats.put("maxCpuMs",values.get(values.size()-1)/1e6);timing.put(entry.getKey(),stats);
            }
            result.put("frameTiming",timing);
            Gdx.files.local("vertical-slice-metrics.json").writeString(com.pkmngen.game.util.Json.gson.toJson(result),false,"UTF-8");frameLog.flush();complete=true;Gdx.app.exit();
        }
        void screenshot(String name){Pixmap p=ScreenUtils.getFrameBufferPixmap(0,0,WIDTH,HEIGHT);Set<Integer> colors=new HashSet<>();for(int y=0;y<HEIGHT;y+=4)for(int x=0;x<WIDTH;x+=4)colors.add(p.getPixel(x,y));require(colors.size()>12,"Blank framebuffer "+name);writeImage(p,name);p.dispose();captures++;}
        void writeImage(Pixmap p,String name){PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);try{png.write(Gdx.files.local(name),p);}catch(Exception e){throw new RuntimeException(e);}finally{png.dispose();}}
        @Override public void dispose(){if(frameLog!=null)frameLog.close();super.dispose();disposed=true;}
    }
    private static Object field(Object target,Class<?> type,String name){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new RuntimeException(e);}}
    private static String ring(int x,int y,int radius){if(Math.max(Math.abs(x),Math.abs(y))!=radius)return "";return "ledges3_"+(y==-radius?"N":"")+(y==radius?"S":"")+(x==-radius?"E":"")+(x==radius?"W":"");}
}
