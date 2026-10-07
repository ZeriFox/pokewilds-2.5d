package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Reads/writes only a disposable preexisting-save copy, never the original. */
public final class SaveCompatibilityTest {
    public static void main(String[] args) {
        Game.leakTracer=LeakTracer.NoOp.INSTANCE;
        Fixture game=new Fixture();
        Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
        cfg.setInitialVisible(false);cfg.setWindowedMode(960,720);cfg.setForegroundFPS(120);cfg.useVsync(false);
        new Lwjgl3Application(game,cfg);
        if(!game.done||!game.disposed)throw new IllegalStateException("Incomplete compatibility fixture");
        System.out.println("SAVE COMPATIBILITY PASS: copied save, data roundtrip and 120 real rendered frames");
        System.exit(0);
    }
    static final class Fixture extends Game {
        int frames;boolean done,disposed;Input quiet;
        final Map<String,Object> result=new LinkedHashMap<>();
        Fixture(){super(new String[0],4);}
        @Override public void create(){
            super.create();final Input original=Gdx.input;
            quiet=(Input)Proxy.newProxyInstance(Input.class.getClassLoader(),new Class<?>[]{Input.class},(p,m,a)->{
                switch(m.getName()){
                    case "isKeyPressed":case "isKeyJustPressed":case "isButtonPressed":case "isButtonJustPressed":case "isTouched":return false;
                    default:try{return m.invoke(original,a);}catch(InvocationTargetException e){throw e.getCause();}
                }
            });Gdx.input=quiet;Gdx.input.setInputProcessor(null);Game.gamepad=null;
            actionStack.clear();insertAction(new InputProcessor());server=new Server();Network.register(server);
            map=new PkmnMap("compatibility");start();map.loadFromFile(this);
            if(map.overworldTiles.isEmpty()||player.pokemon.isEmpty())throw new IllegalStateException("Existing save was not loaded");
            Map<String,Object> before=snapshot();saveGame();Pixmap old=map.minimap;
            actionStack.clear();insertAction(new InputProcessor());map=new PkmnMap("compatibility");player=new Player();players.clear();start();map.loadFromFile(this);old.dispose();
            Map<String,Object> after=snapshot();
            if(!before.equals(after)){
                ArrayList<String> differences=new ArrayList<>();for(String key:before.keySet())if(!Objects.equals(before.get(key),after.get(key)))differences.add(key);
                throw new IllegalStateException("Existing save roundtrip changed categories: "+differences);
            }
            result.put("persistentData",before);result.put("roundtripPreserved",true);
        }
        Map<String,Object> snapshot(){
            Map<String,Object> out=new TreeMap<>();
            out.put("playerAndGameHash",hash(canonical(com.pkmngen.game.util.Json.gson.toJsonTree(new Network.GameSaveData(this)))));
            out.put("exteriorHash",tileHash(map.overworldTiles));out.put("exteriorTiles",map.overworldTiles.size());
            ArrayList<String> rooms=new ArrayList<>();int interiorCount=0;
            for(Map<Vector2,Tile> room:map.interiorTiles){rooms.add(room==null?"null":tileHash(room));if(room!=null)interiorCount+=room.size();}
            out.put("interiorsHash",hash(rooms.toString()));out.put("interiorTiles",interiorCount);
            TreeMap<String,String> wild=new TreeMap<>();
            for(Map.Entry<Vector2,Pokemon> entry:map.pokemon.entrySet())wild.put(entry.getKey().toString(),canonical(com.pkmngen.game.util.Json.gson.toJsonTree(new Network.PokemonData(entry.getValue()))));
            out.put("wildPokemonHash",hash(wild.toString()));out.put("wildPokemonCount",wild.size());
            out.put("partyCount",player.pokemon.size());out.put("inventoryKinds",player.getItemsDict().size());
            out.put("activeInterior",map.tiles!=map.overworldTiles);return out;
        }
        String tileHash(Map<Vector2,Tile> tiles){
            TreeMap<String,String> data=new TreeMap<>();
            for(Tile tile:tiles.values()){
                JsonObject value=com.pkmngen.game.util.Json.gson.toJsonTree(new Network.TileData(tile)).getAsJsonObject();
                // Save files key routes by Object.toString (identity hash). Compare
                // the actual serialized route data, not process-local object IDs.
                value.remove("routeBelongsTo");
                if(tile.routeBelongsTo!=null){
                    JsonObject route=com.pkmngen.game.util.Json.gson.toJsonTree(new Network.RouteData(tile.routeBelongsTo)).getAsJsonObject();
                    route.remove("classId");value.add("routeData",route);
                }
                data.put(tile.position.toString(),canonical(value));
            }
            return hash(data.toString());
        }
        @Override public void render(){
            Gdx.input=quiet;super.render();if(Gdx.gl.glGetError()!=GL20.GL_NO_ERROR)throw new IllegalStateException("Compatibility rendering GL error");
            if(++frames==120){result.put("renderedFrames",frames);Gdx.files.local("compatibility-result.json").writeString(com.pkmngen.game.util.Json.gson.toJson(result),false,"UTF-8");done=true;Gdx.app.exit();}
        }
        @Override public void dispose(){super.dispose();disposed=true;}
    }
    static String canonical(JsonElement value){
        if(value.isJsonObject()){
            TreeMap<String,String> entries=new TreeMap<>();for(Map.Entry<String,JsonElement> entry:value.getAsJsonObject().entrySet())entries.put(entry.getKey(),canonical(entry.getValue()));return entries.toString();
        }
        if(value.isJsonArray()){ArrayList<String> entries=new ArrayList<>();for(JsonElement entry:value.getAsJsonArray())entries.add(canonical(entry));return entries.toString();}
        return value.toString();
    }
    static String hash(String text){
        try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
        catch(Exception e){throw new RuntimeException(e);}
    }
}
