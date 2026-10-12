package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.math.Vector2;
import com.pkmngen.leaks.LeakTracer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Native Tile/collision/elevation/save-format regression for authored generation. */
public final class AuthoredTerrainTest {
    private static Throwable failure;
    private static int assertions;
    public static void main(String[] args) {
        Game.leakTracer = LeakTracer.NoOp.INSTANCE;
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false); config.setWindowedMode(320, 288); config.setForegroundFPS(30);
        new Lwjgl3Application(new TestGame(), config);
        if (failure != null) { failure.printStackTrace(); System.exit(1); }
        System.out.println("AUTHORED PASS: " + assertions + " checks; 12 seeds, reversed insertion, >50% woodland coverage, protected events and reserved actors, real three-wide slopes, collision/TileData consistency");
        System.exit(0);
    }
    private static final class TestGame extends Game {
        TestGame() { super(new String[0], 2); }
        @Override public void create() {
            try {
                super.create();
                for (int seed = 0; seed < 12; seed++) run(seed);
                checkTerraceProtection();
                checkRejectedTerrain();
            } catch (Throwable error) { failure = error; }
            Gdx.app.exit();
        }
        @Override public void render() {}
    }
    private static void run(int seed) {
        Map<Vector2, Tile> first = fixture(false, false), second = fixture(true, false);
        Set<Vector2> reserved = Collections.singleton(new Vector2(25 * 16, 11 * 16));
        Tile event = first.get(new Vector2(0, 0)), actor = first.get(reserved.iterator().next());
        String eventBefore = state(event), actorBefore = state(actor);
        Map<Vector2, Route> routes = new HashMap<>();
        Map<Vector2, String> biomes = new HashMap<>();
        Map<Vector2, String> encounterTrees = new HashMap<>();
        for (Tile tile : first.values()) {
            routes.put(tile.position, tile.routeBelongsTo); biomes.put(tile.position, tile.biome);
            if (!tile.routeBelongsTo.storedPokemon.isEmpty()) encounterTrees.put(tile.position, state(tile));
        }
        int beforeComponents = ModernWorldGenerator.dryComponents(first);
        AuthoredTerrain.Report a = AuthoredTerrain.apply(first, seed, reserved);
        AuthoredTerrain.Report b = AuthoredTerrain.apply(second, seed, reserved);
        check(ModernWorldGenerator.fingerprint(first) == ModernWorldGenerator.fingerprint(second), "insertion-order determinism " + seed);
        check(a.modules == b.modules && a.cellsRebuilt == b.cellsRebuilt, "deterministic reports " + seed);
        check(a.cellsRebuilt > first.size() / 2 && a.modules >= 20, "insufficient woodland coverage: " + a);
        check(a.terraces >= 1 && a.ramps == a.terraces * 6, "three-wide stairs missing: " + a);
        check(first.size() == 129 * 129, "footprint changed");
        check(ModernWorldGenerator.dryComponents(first) <= beforeComponents, "dry components disconnected");
        check(state(event).equals(eventBefore) && state(actor).equals(actorBefore), "event or reserved actor cell changed");
        WorldElevation elevation = new WorldElevation(); elevation.update(first);
        int ramps = 0, sloped = 0;
        for (Tile tile : first.values()) {
            check(tile.routeBelongsTo == routes.get(tile.position), "encounter route replaced");
            check(tile.biome.equals(biomes.get(tile.position)), "habitat replaced");
            if (encounterTrees.containsKey(tile.position)) check(state(tile).equals(encounterTrees.get(tile.position)), "headbutt encounter lost its original tree");
            if (ModernWorldGenerator.isRamp(tile)) {
                ramps++; if (elevation.isSloped(tile)) sloped++;
                check(ModernWorldGenerator.isWalkable(tile), "stairs collision blocked");
                float low = elevation.height(tile.position.x + 8, tile.position.y + .1f);
                float high = elevation.height(tile.position.x + 8, tile.position.y + 15.9f);
                check(Math.abs(high - low) > 4, "stairs are only a flat decal");
            }
            Network.TileData data = new Network.TileData(tile, 100);
            Tile loaded = Tile.get(data, tile.routeBelongsTo);
            check(loaded.name.equals(tile.name) && loaded.nameUpper.equals(tile.nameUpper)
                && loaded.biome.equals(tile.biome) && loaded.isSolid == tile.isSolid
                && loaded.isLedge == tile.isLedge && loaded.isGrass == tile.isGrass,
                "ordinary TileData cannot reproduce authored topology");
        }
        check(ramps == a.ramps && sloped == ramps, "rendered and structural ramps disagree");
        System.out.println("seed=" + seed + " coverage=" + (100 * a.cellsRebuilt / first.size()) + "% " + a);
        // A mountainside fixture contains old ledges as well as open land. Only
        // complete contour interiors may be replaced; border contours stay safe.
        Map<Vector2, Tile> mountains = fixture(false, true);
        int mountainBefore = ModernWorldGenerator.dryComponents(mountains);
        AuthoredTerrain.Report mountain = AuthoredTerrain.apply(mountains, seed);
        check(mountain.modules > 0, "no authored mountain terrain");
        check(ModernWorldGenerator.dryComponents(mountains) <= mountainBefore, "mountain access severed");
    }
    private static Map<Vector2, Tile> fixture(boolean reverse, boolean mountains) {
        List<Vector2> positions = new ArrayList<>();
        for (int y = -64; y <= 64; y++) for (int x = -64; x <= 64; x++) positions.add(new Vector2(x * 16, y * 16));
        if (reverse) Collections.reverse(positions);
        Map<Vector2, Tile> tiles = new LinkedHashMap<>();
        Route route = new Route(mountains ? "mountain1" : "forest1", 20);
        Route headbutt = new Route("", 20);
        headbutt.storedPokemon.add(new Pokemon("pineco", 20));
        for (Vector2 position : positions) {
            int x = Math.round(position.x / 16), y = Math.round(position.y / 16);
            String name = mountains ? "mountain3" : Math.floorMod(x + y, 5) == 0 ? "grass4" : "green1";
            String upper = Math.floorMod(x * 7 + y * 13, 5) == 0 ? "tree2" : "";
            if (upper.isEmpty() && Math.floorMod(x * 3 + y, 11) == 0) upper = "bush2_color";
            if (mountains && y == 0) upper = "ledges3_N";
            if (x == 0 && y == 0) upper = "pokeball1";
            Route treeRoute = x > 24 && upper.equals("tree2") && Math.floorMod(y, 3) == 0 ? headbutt : route;
            Tile tile = new Tile(name, upper, position, true, treeRoute);
            tile.biome = mountains ? "mountain" : "forest";
            if (x == 0 && y == 0) tile.hasItem = "rare candy";
            tiles.put(position, tile);
        }
        return tiles;
    }
    private static void checkRejectedTerrain() {
        for (String biome : new String[]{"deep_forest", "desert", "graveyard"}) {
            Map<Vector2, Tile> map = fixture(false, false);
            for (Tile tile : map.values()) tile.biome = biome;
            long before = ModernWorldGenerator.fingerprint(map);
            AuthoredTerrain.Report report = AuthoredTerrain.apply(map, 13);
            check(report.modules == 0 && ModernWorldGenerator.fingerprint(map) == before, "special biome modified: " + biome);
        }
        Map<Vector2, Tile> map = fixture(false, false);
        for (Tile tile : map.values()) { tile.name = "water2"; tile.init(); }
        long before = ModernWorldGenerator.fingerprint(map);
        check(AuthoredTerrain.apply(map, 13).modules == 0 && ModernWorldGenerator.fingerprint(map) == before, "water overwritten");
    }
    private static void checkTerraceProtection() {
        Map<Vector2, Tile> original = fixture(false, false);
        AuthoredTerrain.Report report = AuthoredTerrain.apply(original, 0);
        AuthoredTerrain.Placement chosen = report.placements.stream()
            .filter(value -> value.module.equals("double-stair-terrace")).findFirst().orElseThrow();
        for (boolean center : new boolean[]{false, true}) {
            Map<Vector2, Tile> map = fixture(false, false);
            int local = center ? 8 : 1;
            int x = chosen.x + local, y = chosen.y + local;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                Tile tile = map.get(new Vector2((x + dx) * 16, (y + dy) * 16));
                tile.init("green1", "", tile.position, true, new Route("forest1", 20)); tile.biome = "forest";
            }
            Tile event = map.get(new Vector2(x * 16, y * 16));
            event.init("green1", "pokeball1", event.position, true, event.routeBelongsTo); event.hasItem = "rare candy";
            String before = state(event);
            AuthoredTerrain.Report result = AuthoredTerrain.apply(map, 0);
            AuthoredTerrain.Placement found = result.placements.stream()
                .filter(value -> value.x == chosen.x && value.y == chosen.y).findFirst().orElseThrow();
            check(state(event).equals(before), "terrace selection changed protected item");
            check(found.module.contains("terrace") != center, "terrace protection must distinguish low corner from raised center");
        }
    }
    private static String state(Tile tile) {
        return tile.name + "/" + tile.nameUpper + "/" + tile.biome + "/" + tile.hasItem + "/" + tile.isSolid;
    }
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new IllegalStateException(message);
    }
}
