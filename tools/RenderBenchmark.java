package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.*;

/** Identical small workload for two complete JARs; no production class overrides. */
public final class RenderBenchmark {
    public static void main(String[] args) {
        Game.leakTracer=LeakTracer.NoOp.INSTANCE;
        Workload workload=new Workload();
        Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);config.setWindowedMode(1280,720);
        config.setForegroundFPS(120);config.useVsync(false);
        new Lwjgl3Application(workload,config);
        if(!workload.done||!workload.disposed)throw new IllegalStateException("Incomplete benchmark");
        System.out.println("RENDER BENCHMARK PASS: 120 warmup + 600 measured identical fixture frames");
        System.exit(0);
    }

    static final class Workload extends Game {
        static final int WARM=120,SAMPLES=600;
        final ArrayList<Long> timings=new ArrayList<>(),allocations=new ArrayList<>();
        int frames;boolean done,disposed;
        Input quiet;
        com.sun.management.ThreadMXBean bean;
        Workload(){super(new String[0],4);}
        @Override public void create(){
            super.create();
            final Input original=Gdx.input;
            quiet=(Input)Proxy.newProxyInstance(Input.class.getClassLoader(),new Class<?>[]{Input.class},(p,m,a)->{
                switch(m.getName()){
                    case "isKeyPressed":case "isKeyJustPressed":case "isButtonPressed":case "isButtonJustPressed":case "isTouched":return false;
                    default:try{return m.invoke(original,a);}catch(InvocationTargetException e){throw e.getCause();}
                }
            });
            Gdx.input=quiet;Gdx.input.setInputProcessor(null);Game.gamepad=null;
            actionStack.clear();insertAction(new InputProcessor());server=new Server();Network.register(server);
            Game.rand=new Random(78311);map=new PkmnMap("render-benchmark");map.rand=new Random(31783);
            Route route=new Route("mountain1",10);
            for(int y=-30;y<=30;y++)for(int x=-30;x<=30;x++){
                String lower="mountain3",upper="";
                if(Math.max(Math.abs(x),Math.abs(y))==8)upper="ledges3_"+(y==-8?"N":"")+(y==8?"S":"")+(x==-8?"E":"")+(x==8?"W":"");
                if(y==-8&&Math.abs(x)<=1){lower="ledge_grass_ramp";upper="";}
                if(x==4&&y>=-10&&y<=-6)lower=y==-8?"waterfall_N":"water1";
                if(x%5==0&&y%4==0&&Math.abs(x)>2&&Math.abs(y)>2&&upper.isEmpty())upper="tree1";
                Tile tile=new Tile(lower,upper,new Vector2(x*16,y*16),true,route);tile.biome="mountain";
                map.tiles.put(tile.position.cpy(),tile);
            }
            map.bottomLeft=new Vector2(-480,-480);map.topRight=new Vector2(480,480);
            map.minimap=new Pixmap(120,120,Pixmap.Format.RGBA8888);map.minimap.setColor(.4f,.5f,.4f,1);map.minimap.fill();
            player.position.set(0,-96);player.spawnLoc.set(player.position);cam.position.set(8,-56,0);
            start();player.setCurrPokemon();actionStack.removeIf(a->a instanceof CycleDayNight);
            map.timeOfDay="day";mapBatch.setColor(Color.WHITE);
            Object candidate=ManagementFactory.getThreadMXBean();
            if(candidate instanceof com.sun.management.ThreadMXBean){bean=(com.sun.management.ThreadMXBean)candidate;if(bean.isThreadAllocatedMemorySupported())bean.setThreadAllocatedMemoryEnabled(true);else bean=null;}
        }
        long allocation(){return bean==null?0:bean.getThreadAllocatedBytes(Thread.currentThread().getId());}
        @Override public void render(){
            Gdx.input=quiet;
            long allocated=allocation(),start=System.nanoTime();super.render();long elapsed=System.nanoTime()-start;
            if(Gdx.gl.glGetError()!=GL20.GL_NO_ERROR)throw new IllegalStateException("Benchmark GL error");
            if(++frames<=WARM)return;
            timings.add(elapsed);allocations.add(Math.max(0,allocation()-allocated));
            if(timings.size()==SAMPLES){
                Map<String,Object> metrics=new LinkedHashMap<>();
                metrics.put("java",System.getProperty("java.runtime.version"));metrics.put("glVendor",Gdx.gl.glGetString(GL20.GL_VENDOR));
                metrics.put("glRenderer",Gdx.gl.glGetString(GL20.GL_RENDERER));metrics.put("glVersion",Gdx.gl.glGetString(GL20.GL_VERSION));
                metrics.put("viewport",Arrays.asList(1280,720));metrics.put("warmupFrames",WARM);metrics.put("measuredFrames",SAMPLES);
                metrics.put("fixtureSeeds",Arrays.asList(78311,31783));metrics.put("tiles",map.tiles.size());metrics.put("playerPosition",player.position.toString());
                metrics.put("cpuRenderMs",stats(timings,1e6));metrics.put("threadAllocationBytes",bean==null?"unavailable":stats(allocations,1));
                metrics.put("managedTextures",Texture.getNumManagedTextures());metrics.put("managedShaders",ShaderProgram.getNumManagedShaderPrograms());
                metrics.put("managedTextureRgba8BaseLevelEstimateBytes",textureEstimate());
                metrics.put("heapUsedBytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
                metrics.put("limits","Single stationary mountain/forest/waterfall scene. CPU render wall time excludes frame pacing; includes driver stalls. Texture estimate sums managed width*height*4 (RGBA8 equivalent base level), excludes mipmaps/unmanaged/driver overhead; not measured VRAM. No GPU timer, campaign workload, or machine-wide allocation claim.");
                Gdx.files.local("metrics.json").writeString(com.pkmngen.game.util.Json.gson.toJson(metrics),false,"UTF-8");
                Pixmap pixels=ScreenUtils.getFrameBufferPixmap(0,0,1280,720);
                try{PixmapIO.writePNG(Gdx.files.local("frame.png"),pixels,-1,true);}finally{pixels.dispose();}
                done=true;Gdx.app.exit();
            }
        }
        Map<String,Object> stats(List<Long> input,double divisor){
            ArrayList<Long> values=new ArrayList<>(input);Collections.sort(values);double sum=0;for(long value:values)sum+=value;
            Map<String,Object> result=new LinkedHashMap<>();result.put("mean",sum/values.size()/divisor);
            result.put("median",values.get(values.size()/2)/divisor);result.put("p95",values.get((int)Math.ceil(values.size()*.95)-1)/divisor);result.put("max",values.get(values.size()-1)/divisor);return result;
        }
        long textureEstimate(){
            try{
                java.lang.reflect.Field field=Texture.class.getDeclaredField("managedTextures");field.setAccessible(true);
                long bytes=0;int count=0;
                for(Object values:((Map<?,?>)field.get(null)).values())for(Object value:(Iterable<?>)values){
                    Texture texture=(Texture)value;bytes+=(long)texture.getWidth()*texture.getHeight()*4;count++;
                }
                if(count!=Texture.getNumManagedTextures())throw new IllegalStateException("Texture inventory differs from managed texture count");
                return bytes;
            }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot inspect managed texture dimensions",e);}
        }
        @Override public void dispose(){super.dispose();disposed=true;}
    }
}
