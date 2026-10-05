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
            map.put(pos, tile);
        }
        return map;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
