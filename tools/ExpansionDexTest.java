package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.esotericsoftware.kryonet.Server;
import com.pkmngen.leaks.LeakTracer;
import java.util.Arrays;
import java.util.List;

/** Native integration of every new dex entry and representative gameplay/save paths. */
public final class ExpansionDexTest {
    private static Throwable failure;
    private static boolean complete;
    public static void main(String[] args) {
        Game.leakTracer = LeakTracer.NoOp.INSTANCE;
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false); config.setWindowedMode(640, 576);
        config.setForegroundFPS(240); config.useVsync(false);
        new Lwjgl3Application(new TestGame(), config);
        if (failure != null || !complete) {
            if (failure != null) failure.printStackTrace();
            System.exit(1);
        }
        System.out.println("EXPANSION PASS: every new species, native sprites, canonical stats/moves, capture calculations, level/evolution/egg, real save/load and audio");
        System.exit(0);
    }
    private static class TestGame extends Game {
        List<String> names;
        JsonValue data;
        int index;
        TestGame() { super(new String[0], 2); }
        @Override public void create() {
            try {
                super.create();
                actionStack.clear(); server = new Server(); Network.register(server);
                map = new PkmnMap("dex-fixture");
                for (int y = -320; y <= 320; y += 16) for (int x = -320; x <= 320; x += 16) {
                    Vector2 pos = new Vector2(x, y); map.tiles.put(pos, new Tile("green1", pos.cpy(), true));
                }
                map.bottomLeft = new Vector2(-320, -320); map.topRight = new Vector2(320, 320);
                map.minimap = new Pixmap(80, 80, Pixmap.Format.RGBA8888);
                map.edges.add(new Vector2());
                player.position.set(0, 0); player.spawnLoc.set(0, 0); player.name = "DexTest";
                start(); actionStack.clear();
                names = ExpansionDex.names();
                data = new JsonReader().parse(Gdx.files.internal("visual/dex/expansion-dex.json")).get("species");
                check(names.size() >= 500, "Expansion data missing");
            } catch (Throwable error) { fail(error); }
        }
        @Override public void render() {
            if (failure != null || complete) return;
            try {
                if (index < names.size()) {
                    String name = names.get(index++);
                    Pokemon pokemon = new Pokemon(name, 5, Pokemon.Generation.CRYSTAL, false);
                    pokemon.position.set(32, 0); pokemon.mapTiles = map.tiles;
                    JsonValue entry = data.get(name);
                    check(pokemon.dexNumber.equals(String.format(java.util.Locale.ROOT, "%03d", entry.getInt("dex"))), "Wrong dex number " + name);
                    check(pokemon.baseStats.get("hp") == entry.get("stats").getInt(0), "Wrong stats " + name);
                    check(pokemon.attacks[0] != null, "No attacks " + name);
                    for (String move : pokemon.attacks) check(move == null || Pokemon.attacksImplemented.contains(move), "Unimplemented move " + name + ": " + move);
                    check(pokemon.baseSpecie() != null, "Missing breeding ancestry " + name);
                    check(pokemon.sprite != null && pokemon.backSprite != null && pokemon.standingSprites.size() == 8, "Missing fallback sprites " + name);
                    check(pokemon.maxStats.get("catchRate") == entry.getInt("catchRate"), "Wrong catch rate " + name);
                    check(Battle.gen2CalcIfCaught(this, pokemon, "master ball") == -1, "Master ball " + name);
                    Battle.gen2CalcIfCaught(this, pokemon, "heavy ball");
                    Pokemon loaded = new Pokemon(new Network.PokemonData(pokemon));
                    check(loaded.specie.name.equals(name) && loaded.level == pokemon.level && loaded.exp == pokemon.exp
                        && Arrays.equals(loaded.attacks, pokemon.attacks) && loaded.gender.equals(pokemon.gender), "PokemonData round trip " + name);
                    check(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "GL error " + name);
                    if (index % 100 == 0) System.out.println("EXPANSION: verified " + index + "/" + names.size());
                    return;
                }
                lifecycle(); complete = true; Gdx.app.exit();
            } catch (Throwable error) { fail(error); }
        }
        void lifecycle() {
            Pokemon torchic = new Pokemon("torchic", 15, Pokemon.Generation.CRYSTAL, false);
            int beforeHp = torchic.maxStats.get("hp");
            torchic.gainLevel(1); torchic.exp = torchic.calcExpForLevel(torchic.level);
            check(torchic.maxStats.get("hp") > beforeHp, "Level-up did not increase HP");
            check("combusken".equals(Specie.gen2Evos.get("torchic").get("16")), "Canonical evolution unavailable");
            torchic.evolveTo("combusken");
            check(torchic.level == 16 && torchic.specie.name.equals("combusken"), "Original evolveTo failed");
            Pokemon egg = new Pokemon("sprigatito", 5, Pokemon.Generation.CRYSTAL, false, true);
            Pokemon loadedEgg = new Pokemon(new Network.PokemonData(egg));
            check(loadedEgg.isEgg && loadedEgg.specie.name.equals("sprigatito"), "Egg round trip failed");
            loadedEgg.hatch(); check(!loadedEgg.isEgg && loadedEgg.standingSprites.size() == 8, "Hatch failed");
            for (String name : new String[]{"torchic", "sprigatito", "fuecoco", "grookey", "meltan"}) {
                if (!ExpansionDex.contains(name)) continue;
                Pokemon pokemon = new Pokemon(name, 8, Pokemon.Generation.CRYSTAL, false);
                PlaySound sound = new PlaySound(pokemon, null);
                check(sound.waitFrames > 1 && sound.sound != null, "Cry missing " + name);
                sound.sound.dispose();
                PlayMusic music = new PlayMusic(pokemon, null);
                check(music.music != null, "Music cry missing " + name); music.music.dispose();
            }
            JsonValue values = data;
            int additions = 0;
            for (String routeName : new String[]{"volcano1", "snow1", "forest1", "mountain1", "desert1", "savanna2", "wooded_lake_water1"}) {
                Route route = new Route(routeName, 22);
                for (String name : route.allowedPokemon()) if (values.has(name)) {
                    JsonValue entry = values.get(name);
                    check(entry.getBoolean("spawnable") && !entry.getBoolean("legendary") && !entry.getBoolean("mythical"), "Invalid random spawn " + name);
                    additions++;
                }
            }
            check(additions > 30, "Biome expansion missing");
            player.pokemon.clear(); player.pokemon.add(torchic); player.pokemon.add(loadedEgg);
            player.pokemon.add(new Pokemon("fuecoco", 8, Pokemon.Generation.CRYSTAL, true));
            player.setCurrPokemon();
            Gdx.files.local("dex-fixture.sav").mkdirs();
            Gdx.files.local("dex-fixture.sav/.test").writeString("Isolated expansion test", false);
            saveGame();
            actionStack.clear(); Pixmap oldMinimap = map.minimap;
            map = new PkmnMap("dex-fixture"); player = new Player(); start(); map.loadFromFile(this); oldMinimap.dispose();
            check(player.pokemon.size() == 3 && player.pokemon.get(0).specie.name.equals("combusken")
                && player.pokemon.get(0).level == 16 && player.pokemon.get(1).specie.name.equals("sprigatito")
                && player.pokemon.get(2).specie.name.equals("fuecoco") && player.pokemon.get(2).isShiny, "Real save/load changed party");
            System.out.println("EXPANSION: all " + names.size() + " entries and " + additions + " biome additions passed; party combusken16/sprigatito5/fuecoco8 shiny saved and reloaded");
        }
        @Override public void dispose() {
            ExpansionDex.dispose(); PmdPokemonSprites.disposeShared(); super.dispose();
        }
        void fail(Throwable error) { failure = error; Gdx.app.exit(); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
