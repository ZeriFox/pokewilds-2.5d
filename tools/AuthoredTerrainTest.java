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
        System.out.println("AUTHORED PASS: " + assertions + " checks; 12 woodland seeds, 9 biome catalogs x 4 seeds, reversed insertion, retained water/maze boundaries, protected events and actors, unlabelled habitats, real slopes, collision/TileData consistency");
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
                checkBiomeCatalogs();
                checkRetainedBoundaries();
                checkUnlabelledGround();
                checkSpecialWaterBoundaries();
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
        for (String biome : new String[]{"volcano"}) {
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

    private static void checkBiomeCatalogs() {
        String[][] biomes = {
            {"desert", "desert1", "desert4"}, {"beach", "beach1", "sand1"},
            {"oasis", "oasis1", "green1"}, {"savanna", "savanna2", "green_savanna1"},
            {"wooded_lake", "wooded_lake1", "green_woodedlake"}, {"snow", "snow1", "snow1"},
            {"mountain", "mountain1", "mountain3"}, {"deep_forest", "deep_forest", "green_deepforest"},
            {"graveyard", "graveyard1", "green12"}
        };
        for (String[] biome : biomes) for (int seed = 0; seed < 4; seed++) {
            Map<Vector2, Tile> map = biomeFixture(biome), reversed = new LinkedHashMap<>();
            List<Vector2> order = new ArrayList<>(map.keySet()); Collections.reverse(order);
            Map<Vector2, Tile> duplicate = biomeFixture(biome);
            for (Vector2 position : order) reversed.put(position, duplicate.get(position));
            Map<Vector2, String> protectedBefore = new HashMap<>(), profiles = new HashMap<>();
            Map<Vector2, Route> routes = new HashMap<>();
            for (Tile tile : map.values()) {
                profiles.put(tile.position, BiomeProfiles.forTile(tile).id);
                routes.put(tile.position, tile.routeBelongsTo);
                int x = Math.round(tile.position.x / 16), y = Math.round(tile.position.y / 16);
                if (tile.isWater || tile.isTidal || tile.hasItem != null || tile.routeBelongsTo.isDungeon
                    || !tile.routeBelongsTo.storedPokemon.isEmpty() || tile.name.equals("tree5")
                    || tile.nameUpper.equals("gravestone2") || tile.name.equals("desert2_trapinch_spawn")
                    || x == 13 && y == 13) protectedBefore.put(tile.position, state(tile));
            }
            Set<Vector2> reserved = Collections.singleton(new Vector2(13 * 16, 13 * 16));
            int components = ModernWorldGenerator.dryComponents(map);
            AuthoredTerrain.Report result = AuthoredTerrain.apply(map, seed, reserved);
            AuthoredTerrain.apply(reversed, seed, reserved);
            check(result.modules >= 4 && result.preparedCells >= 200, "missing biome assembly " + biome[0] + ": " + result);
            check(result.cellsRebuilt > 200, "insufficient biome room coverage " + biome[0] + ": " + result);
            check(ModernWorldGenerator.fingerprint(map) == ModernWorldGenerator.fingerprint(reversed), "biome insertion order " + biome[0]);
            check(ModernWorldGenerator.dryComponents(map) <= components, "biome route disconnected " + biome[0]);
            WorldElevation elevation = new WorldElevation(); elevation.update(map);
            for (Tile tile : map.values()) {
                check(tile.routeBelongsTo == routes.get(tile.position), "biome route replaced " + biome[0]);
                check(BiomeProfiles.forTile(tile).id.equals(profiles.get(tile.position)), "material/habitat identity changed " + biome[0] + " " + tile.name);
                if (protectedBefore.containsKey(tile.position)) check(protectedBefore.get(tile.position).equals(state(tile)), "protected biome content changed " + biome[0]);
                if (ModernWorldGenerator.isRamp(tile)) check(elevation.isSloped(tile) && ModernWorldGenerator.isWalkable(tile), "biome ramp is blocked or flat " + biome[0]);
                Network.TileData data = new Network.TileData(tile, 100);
                Tile loaded = Tile.get(data, tile.routeBelongsTo);
                check(loaded.name.equals(tile.name) && loaded.nameUpper.equals(tile.nameUpper)
                    && loaded.isSolid == tile.isSolid && loaded.isGrass == tile.isGrass && loaded.isLedge == tile.isLedge,
                    "biome save mismatch " + biome[0]);
            }
            if (seed == 0) System.out.println("BIOME " + biome[0] + " " + result);
        }
    }

    private static Map<Vector2, Tile> biomeFixture(String[] biome) {
        Map<Vector2, Tile> map = new LinkedHashMap<>();
        Route route = new Route(biome[1], 20), dungeon = new Route("pkmnmansion1", 20), encounter = new Route("", 20);
        encounter.storedPokemon.add(new Pokemon("pineco", 20));
        for (int y = -20; y <= 20; y++) for (int x = -20; x <= 20; x++) {
            Vector2 pos = new Vector2(x * 16, y * 16);
            String ground = biome[2], upper = "";
            Route cellRoute = route;
            if (y < -16) ground = "water2";
            if (y == -16) ground = "sand4_tidalwater";
            if (biome[0].equals("deep_forest") && y == 0 && Math.abs(x) > 3) ground = "tree5";
            if (biome[0].equals("graveyard") && Math.floorMod(x, 12) == 0 && Math.floorMod(y, 12) == 0) upper = "gravestone2";
            if (x == -10 && y == 10) upper = "pokeball1";
            if (x == 10 && y == 10) cellRoute = dungeon;
            if (x == -10 && y == -10) { upper = "tree2"; cellRoute = encounter; }
            if (biome[0].equals("desert") && x == 10 && y == -10) ground = "desert2_trapinch_spawn";
            Tile tile = new Tile(ground, upper, pos, true, cellRoute); tile.biome = biome[0];
            if (x == -10 && y == 10) tile.hasItem = "rare candy";
            map.put(pos, tile);
        }
        return map;
    }

    private static void checkRetainedBoundaries() {
        for (boolean maze : new boolean[]{false, true}) {
            Map<Vector2, Tile> map = new LinkedHashMap<>(); Map<Vector2, String> immutable = new HashMap<>();
            Route route = new Route(maze ? "deep_forest" : "beach1", 20);
            for (int y = -15; y <= 15; y++) for (int x = -15; x <= 15; x++) {
                String ground = maze ? Math.abs(y) <= 1 ? "green_deepforest" : "tree5" : x == y ? "water2" : "sand1";
                Tile tile = new Tile(ground, "", new Vector2(x * 16, y * 16), true, route);
                tile.biome = maze ? "deep_forest" : "beach"; map.put(tile.position, tile);
                if (tile.isSolid || tile.isWater) immutable.put(tile.position, state(tile));
            }
            int before = ModernWorldGenerator.dryComponents(map);
            AuthoredTerrain.Report result = AuthoredTerrain.apply(map, 19);
            check(result.modules > 0, "no accepted narrow corridor/bank pieces, maze=" + maze);
            check(result.changedCells >= result.modules * 8, "pocket counted without substantial changes");
            check(ModernWorldGenerator.dryComponents(map) == before, "diagonal water or maze topology changed");
            for (Map.Entry<Vector2, String> entry : immutable.entrySet()) check(state(map.get(entry.getKey())).equals(entry.getValue()), "water/maze wall rewritten");
        }
        Map<Vector2, Tile> islands = new LinkedHashMap<>(); Route beach = new Route("beach1", 20);
        for (int y = -10; y <= 10; y++) for (int x = -10; x <= 10; x++) {
            boolean land = Math.abs(y) <= 1 && (x >= -5 && x <= -4 || x >= 4 && x <= 5);
            Tile tile = new Tile(land ? "sand1" : "water2", "", new Vector2(x * 16, y * 16), true, beach);
            tile.biome = "beach"; islands.put(tile.position, tile);
        }
        int before = ModernWorldGenerator.dryComponents(islands);
        AuthoredTerrain.Report result = AuthoredTerrain.apply(islands, 19);
        check(result.modules == 0 && ModernWorldGenerator.dryComponents(islands) == before, "tiny separated islands were connected or counted as modules");
    }

    private static void checkUnlabelledGround() {
        for (String ground : new String[]{"desert4", "sand1", "snow1", "mountain3", "grass_graveyard1"}) {
            Map<Vector2, Tile> map = new LinkedHashMap<>(); Map<Vector2, String> profiles = new HashMap<>();
            for (int y = -12; y <= 12; y++) for (int x = -12; x <= 12; x++) {
                Tile tile = new Tile(ground, "", new Vector2(x * 16, y * 16), true, null);
                tile.biome = ""; map.put(tile.position, tile); profiles.put(tile.position, BiomeProfiles.forTile(tile).id);
            }
            AuthoredTerrain.apply(map, 12);
            for (Tile tile : map.values()) check(tile.routeBelongsTo == null && tile.biome.isEmpty()
                && BiomeProfiles.forTile(tile).id.equals(profiles.get(tile.position)), "unlabelled ground lost habitat: " + ground + " -> " + tile.name);
        }
    }

    /** Pocket boundaries must not bypass the special-cell approach halo. The
     * later shoreline pass must also retain already exposed special water. */
    private static void checkSpecialWaterBoundaries() {
        Vector2 center = new Vector2(0, 0);
        for (boolean stored : new boolean[]{false, true}) {
            Map<Vector2, Tile> map = specialWaterFixture(stored, true, 12);
            Map<Vector2, String> halo = new HashMap<>();
            for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
                Vector2 pos = new Vector2(x * 16, y * 16); halo.put(pos, state(map.get(pos)));
            }
            Set<Vector2> reserved = stored ? Collections.emptySet() : Collections.singleton(center);
            AuthoredTerrain.apply(map, 0, reserved);
            for (Map.Entry<Vector2, String> entry : halo.entrySet())
                check(state(map.get(entry.getKey())).equals(entry.getValue()), "special water lost pocket approach halo, stored=" + stored);

            map = specialWaterFixture(stored, true, 12);
            Tile water = map.get(center); Route originalRoute = water.routeBelongsTo;
            Pokemon encounter = stored ? originalRoute.storedPokemon.get(0) : new Pokemon("milotic", 20);
            Map<Vector2, Pokemon> early = new HashMap<>();
            if (!stored) early.put(center, encounter);
            ModernWorldGenerator.Report report = ModernWorldGenerator.apply(map, early);
            check(water.isWater && water.name.equals("water2") && water.routeBelongsTo == originalRoute
                && report.shoreCellsSmoothed == 0, "complete pipeline replaced protected pond");
            check(stored ? originalRoute.storedPokemon.size() == 1 && originalRoute.storedPokemon.get(0) == encounter
                : early.get(center) == encounter, "complete pipeline lost protected pond encounter");
            for (Map.Entry<Vector2, String> entry : halo.entrySet())
                check(state(map.get(entry.getKey())).equals(entry.getValue()), "complete pipeline changed pond approach");
        }
        for (String mode : new String[]{"normal", "reserved", "stored", "dungeon"}) {
            Map<Vector2, Tile> map = specialWaterFixture(mode.equals("stored"), false, 1);
            Tile water = map.get(center); Route originalRoute = water.routeBelongsTo;
            if (mode.equals("dungeon")) originalRoute.isDungeon = true;
            Map<Vector2, Pokemon> early = new HashMap<>();
            Pokemon encounter = mode.equals("stored") ? originalRoute.storedPokemon.get(0) : new Pokemon("milotic", 20);
            if (mode.equals("reserved")) early.put(center, encounter);
            ModernWorldGenerator.Report report = ModernWorldGenerator.apply(map, early);
            if (mode.equals("normal")) {
                check(report.shoreCellsSmoothed == 1 && water.name.equals("sand3") && !water.isWater,
                    "ordinary isolated inland water no longer smooths");
            } else {
                check(report.shoreCellsSmoothed == 0 && water.name.equals("water2") && water.isWater
                    && water.routeBelongsTo == originalRoute, "exposed special water was smoothed: " + mode);
                if (mode.equals("stored")) check(originalRoute.storedPokemon.size() == 1 && originalRoute.storedPokemon.get(0) == encounter, "stored water encounter replaced");
                if (mode.equals("reserved")) check(early.get(center) == encounter, "reserved water actor replaced");
            }
        }
        System.out.println("SPECIAL WATER PASS: reserved/stored pocket halo; public pipeline; exposed reserved/stored/dungeon water preserved; ordinary smoothing retained");
    }

    private static Map<Vector2, Tile> specialWaterFixture(boolean stored, boolean blocked, int radius) {
        Map<Vector2, Tile> map = new LinkedHashMap<>();
        Route forest = new Route("forest1", 20), water = new Route(stored ? "" : "river1", 20);
        if (stored) water.storedPokemon.add(new Pokemon("goldeen", 20));
        for (int y = -radius; y <= radius; y++) for (int x = -radius; x <= radius; x++) {
            boolean center = x == 0 && y == 0;
            String upper = blocked && Math.abs(x) <= 1 && Math.abs(y) <= 1 && !center ? "rock1_color" : "";
            Tile tile = new Tile(center ? "water2" : "green1", upper, new Vector2(x * 16, y * 16), true, center ? water : forest);
            tile.biome = "forest"; map.put(tile.position, tile);
        }
        return map;
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
