package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.math.Vector2;
import com.pkmngen.leaks.LeakTracer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Real Tile collision/serialization checks, with an invisible native GL context. */
public final class ModernWorldGenerationTest {
    private static Throwable failure;
    public static void main(String[] args) {
        Game.leakTracer = LeakTracer.NoOp.INSTANCE;
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.setWindowedMode(320, 288);
        config.setForegroundFPS(30);
        new Lwjgl3Application(new TestGame(), config);
        if (failure != null) { failure.printStackTrace(); System.exit(1); }
        System.out.println("TOPOLOGY PASS: 8 seeds, insertion-order determinism, unchanged footprint, reachable terraces, solid lava, protected landmarks, TileData save compatibility");
        System.exit(0);
    }
    private static final class TestGame extends Game {
        TestGame() { super(new String[0], 2); }
        @Override public void create() {
            try {
                super.create();
                for (int seed = 0; seed < 8; seed++) {
                    Map<Vector2, Tile> first = fixture(false), second = fixture(true);
                    long before = ModernWorldGenerator.fingerprint(first);
                    check(before == ModernWorldGenerator.fingerprint(second), "fixture must be independent of insertion order");
                    ModernWorldGenerator.Report a = ModernWorldGenerator.apply(first, seed);
                    ModernWorldGenerator.Report b = ModernWorldGenerator.apply(second, seed);
                    check(ModernWorldGenerator.fingerprint(first) == ModernWorldGenerator.fingerprint(second), "overlay determinism " + seed);
                    check(a.tilesBefore == a.tilesAfter, "landmass footprint changed");
                    check(a.componentsAfter <= a.componentsBefore, "dry region became disconnected");
                    check(a.componentsAfter == 1, "fixture terraces are not all reachable: " + a);
                    check(a.rampsOpened >= 3, "mountain terraces need real access ramps");
                    check(a.volcanicTiles > 1000 && a.lavaCellsAdded > 20, "caldera absent");
                    check(a.shoreCellsSmoothed >= 1, "shore smoothing absent");
                    verifyCaldera(first, a);
                    Tile formerTree = first.get(new Vector2(8 * 16, 8 * 16));
                    check(!formerTree.drawAsTree && !formerTree.drawSavannaTree && formerTree.yOffset == 0
                        && !formerTree.isBottomMtnLayer && !formerTree.isTree && !formerTree.nameUpper.contains("tree"),
                        "tree-to-basalt retained legacy drawing offsets or flags");
                    Tile landmark = first.get(new Vector2(0, 0));
                    check("rare candy".equals(landmark.hasItem) && "pokeball1".equals(landmark.nameUpper), "landmark overwritten");
                    for (Tile tile : first.values()) {
                        if (tile.isLava) check(tile.isSolid && !ModernWorldGenerator.isWalkable(tile), "lava must block ordinary walking");
                        if (ModernWorldGenerator.isRamp(tile)) check(ModernWorldGenerator.isWalkable(tile), "ramp collision blocked");
                        if (ModernWorldGenerator.isVolcanic(tile)) {
                            Network.TileData data = new Network.TileData(tile, 100);
                            Tile loaded = Tile.get(data, tile.routeBelongsTo);
                            check(loaded.name.equals(tile.name) && loaded.nameUpper.equals(tile.nameUpper)
                                && loaded.biome.equals(tile.biome) && loaded.isSolid == tile.isSolid
                                && loaded.isLava == tile.isLava, "TileData round trip changed volcanic cell");
                        }
                    }
                    System.out.println(a);
                }
                protectedCalderaRock();
            } catch (Throwable error) { failure = error; }
            Gdx.app.exit();
        }
        @Override public void render() {}
    }
    private static Map<Vector2, Tile> fixture(boolean reversed) {
        Map<Vector2, Tile> map = new LinkedHashMap<>();
        Route mountain = new Route("mountain1", 22), volcano = new Route("volcano1", 22);
        ArrayList<Vector2> positions = new ArrayList<>();
        for (int y = -64; y <= 64; y++) for (int x = -64; x <= 64; x++) positions.add(new Vector2(x * 16, y * 16));
        if (reversed) Collections.reverse(positions);
        for (Vector2 pos : positions) {
            int x = (int)pos.x / 16, y = (int)pos.y / 16;
            boolean volcanic = x * x + y * y < 400;
            String name = volcanic ? "volcano1" : "mountain3";
            String upper = y == 45 || y == 50 || y == -50 ? "ledges3_N" : "";
            if (x == 52 && y == 52) name = "water2";
            Tile tile = new Tile(name, upper, pos, true, volcanic ? volcano : mountain);
            tile.biome = volcanic ? "volcano" : "mountain";
            if (x == 0 && y == 0) {
                tile.nameUpper = "pokeball1"; tile.init(); tile.hasItem = "rare candy";
            }
            if (x == 8 && y == 8) {
                tile.nameUpper = "tree2"; tile.init(); tile.biome = "volcano";
                tile.drawAsTree = true; tile.drawSavannaTree = true; tile.yOffset = 24; tile.isBottomMtnLayer = true;
            }
            map.put(pos, tile);
        }
        return map;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    static void verifyCaldera(Map<Vector2, Tile> map, ModernWorldGenerator.Report report) {
        check(report.calderaBasinRadius >= 6, "complete authored caldera basin absent: " + report);
        WorldElevation elevation = new WorldElevation(); elevation.update(map);
        int r = report.calderaBasinRadius, ramps = 0;
        float minimum = Float.POSITIVE_INFINITY, maximum = Float.NEGATIVE_INFINITY;
        for (int y = -r - 2; y <= r + 2; y++) for (int x = -r - 2; x <= r + 2; x++) {
            Tile tile = map.get(new Vector2(report.calderaBasinX + x * 16, report.calderaBasinY + y * 16));
            check(tile != null, "caldera footprint has a hole");
            float height = elevation.tileHeight(tile);
            minimum = Math.min(minimum, height); maximum = Math.max(maximum, height);
            if (ModernWorldGenerator.isRamp(tile)) {
                ramps++;
                check(Math.abs(y) == r && Math.abs(x) <= 1, "ramp outside authored entrance");
                check(elevation.isSloped(tile) && ModernWorldGenerator.isWalkable(tile), "caldera ramp is not a traversable slope");
            }
            if (Math.abs(x) <= 1) check(ModernWorldGenerator.isWalkable(tile), "caldera central causeway obstructed");
        }
        check(ramps == 6, "caldera must contain exactly six authored ramp cells");
        check(Math.abs(maximum - minimum - 14) < .1f, "caldera accumulated heights instead of a single 14-unit depth: " + minimum + ".." + maximum);
        Tile bottom = map.get(new Vector2(report.calderaBasinX, report.calderaBasinY));
        Tile rim = map.get(new Vector2(report.calderaBasinX, report.calderaBasinY + (r + 2) * 16));
        check(Math.abs(elevation.tileHeight(rim) - elevation.tileHeight(bottom) - 14) < .1f, "caldera floor is not below its rim");
    }
    private static void protectedCalderaRock() {
        Map<Vector2, Tile> prototype = fixture(false);
        ModernWorldGenerator.Report reference = ModernWorldGenerator.apply(prototype, 0);
        Map<Vector2, Tile> map = fixture(false);
        Vector2 position = new Vector2(reference.calderaBasinX + (reference.calderaBasinRadius + 1) * 16,
            reference.calderaBasinY + 2 * 16);
        Route encounter = new Route("", 22);
        Pokemon slugma = new Pokemon("slugma", 22); encounter.storedPokemon.add(slugma);
        Tile rock = new Tile("volcano1", "rock1_color", position, true, encounter); rock.biome = "volcano";
        map.put(position, rock);
        ModernWorldGenerator.Report report = ModernWorldGenerator.apply(map, 0);
        check(report.calderaBasinX == reference.calderaBasinX && report.calderaBasinY == reference.calderaBasinY,
            "safe retained rock prevented otherwise complete caldera");
        check(rock.name.equals("volcano1") && rock.nameUpper.equals("rock1_color") && rock.routeBelongsTo == encounter
            && encounter.storedPokemon.get(0) == slugma, "caldera erased or moved a protected rock encounter");
        verifyCaldera(map, report);
        check(report.componentsAfter <= report.componentsBefore, "retained caldera rock severed a dry path");
    }
}
