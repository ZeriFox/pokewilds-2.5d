package com.pkmngen.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.math.Vector2;
import com.pkmngen.leaks.LeakTracer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Native Tile flags and save data drive geometry, with no edits to collision/topology. */
public final class WorldElevationTest {
    private static Throwable failure;
    public static void main(String[] args) {
        Game.leakTracer = LeakTracer.NoOp.INSTANCE;
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false); config.setWindowedMode(320, 288); config.setForegroundFPS(30);
        new Lwjgl3Application(new TestGame(), config);
        if (failure != null) { failure.printStackTrace(); System.exit(1); }
        System.out.println("ELEVATION PASS: nested plateaus, 3-wide ramp continuity, diagonal corners, signed craters, exterior datum, shuffled/reloaded maps, structural cache invalidation, tidal no-rebuild, in-place water/lava invalidation, continuous implicit openings, contradictory edge localization");
        System.exit(0);
    }
    private static final class TestGame extends Game {
        TestGame() { super(new String[0], 2); }
        @Override public void create() {
            try { super.create(); actionStack.clear(); run(); }
            catch (Throwable error) { failure = error; }
            Gdx.app.exit();
        }
        @Override public void render() {}
        private void run() throws Exception {
            Map<Vector2, Tile> map = fixture(9, 5, false, true, false);
            long before = ModernWorldGenerator.fingerprint(map);
            WorldElevation elevation = new WorldElevation(); elevation.update(map);
            equal(0, elevation.height(8 * 16, 0), "Exterior datum");
            equal(14, elevation.height(0, 0), "Raised plateau");
            equal(14, elevation.tileHeight(map.get(new Vector2(-80, -80))), "Convex diagonal corner");
            equal(0, elevation.lowHeight(map.get(new Vector2(-80, -80))), "Corner low side");
            check(elevation.raised(map.get(new Vector2(-80, -80))), "Corner ledge missing relief");
            for (int x = -1; x <= 1; x++) {
                equal(0, elevation.height(x * 16 + 8, -80), "Ramp low edge");
                equal(7, elevation.height(x * 16 + 8, -72), "Ramp center");
                check(Math.abs(14 - elevation.height(x * 16 + 8, -64.001f)) < .01, "Ramp high edge continuity");
                equal(14, elevation.height(x * 16 + 8, -64), "Ramp-to-plateau seam");
            }
            check(elevation.getConflicts() == 0, "Coherent plateau conflicts");
            check(before == ModernWorldGenerator.fingerprint(map), "Elevation mutated source tiles");
            Map<Vector2, Tile> reverse = new LinkedHashMap<>();
            List<Tile> values = new ArrayList<>(map.values()); Collections.reverse(values);
            for (Tile tile : values) reverse.put(tile.position.cpy(), tile);
            WorldElevation reversed = new WorldElevation(); reversed.update(reverse);
            Map<Vector2, Tile> loaded = new LinkedHashMap<>();
            for (Tile tile : values) {
                Tile copy = Tile.get(new Network.TileData(tile, 100), tile.routeBelongsTo);
                loaded.put(copy.position.cpy(), copy);
            }
            WorldElevation reloaded = new WorldElevation(); reloaded.update(loaded);
            for (Tile tile : values) {
                equal(elevation.tileHeight(tile), reversed.tileHeight(tile), "Insertion order");
                equal(elevation.tileHeight(tile), reloaded.tileHeight(loaded.get(tile.position)), "TileData reload");
            }
            int builds = elevation.getRebuilds(); elevation.update(map);
            check(elevation.getRebuilds() == builds, "Unchanged map rebuilt");
            Tile repainted=map.get(new Vector2(0, 0)); repainted.name="mountain2"; repainted.init();
            elevation.observe(repainted); elevation.update(map);
            check(elevation.getRebuilds() == builds, "Texture-only edit rebuilt the entire surface");
            repainted.name="sand3_tidalwater"; repainted.init(); elevation.observe(repainted); elevation.update(map);
            repainted.name="sand3_tidaloff"; repainted.init(); elevation.observe(repainted); elevation.update(map);
            check(elevation.getRebuilds() == builds, "Time-of-day tidal texture changes rebuilt terrain");


            Tile edited = map.get(new Vector2(4 * 16, -5 * 16));
            edited.nameUpper = ""; edited.init(); elevation.observe(edited); elevation.update(map);
            check(elevation.getRebuilds() == builds + 1, "In-place edit did not invalidate");
            equal(14, elevation.height(0, 0), "A narrow opening must retain the surrounding plateau");
            check(elevation.isSloped(edited) || elevation.isSloped(map.get(new Vector2(64, -96))), "Opening has no continuous transition");
            float previous=elevation.height(72, -112), minimum=previous, maximum=previous;
            for(float y=-111.5f;y<=-48;y+=.5f) {
                float value=elevation.height(72,y);
                check(Math.abs(value-previous)<.5f, "Implicit opening introduced a walking height jump");
                minimum=Math.min(minimum,value); maximum=Math.max(maximum,value); previous=value;
            }
            check(maximum-minimum>13.9f, "Opening no longer connects the two full terrace levels: "+minimum+".."+maximum);
            int terrainBuilds=elevation.getRebuilds();
            float dryOpening=elevation.height(72,-48);
            edited.name="water1"; edited.init();
            check(edited.isWater && !edited.isLava, "Water edit fixture is not actual water");
            elevation.observe(edited); elevation.update(map);
            check(elevation.getRebuilds()==++terrainBuilds, "In-place land-to-water edit kept stale opening constraints");
            check(Math.abs(dryOpening-elevation.height(72,-48))>1, "Wet opening kept the derived dry ramp");
            edited.name="lava1"; edited.init();
            check(edited.isLava && !edited.isWater, "Lava edit fixture is not actual lava");
            elevation.observe(edited); elevation.update(map);
            check(elevation.getRebuilds()==++terrainBuilds, "In-place water-to-lava edit kept stale constraints");
            edited.name="mountain3"; edited.init(); elevation.observe(edited); elevation.update(map);
            check(elevation.getRebuilds()==++terrainBuilds, "In-place lava-to-land edit kept stale constraints");
            equal(dryOpening,elevation.height(72,-48), "Restored dry opening did not restore its slope");


            Map<Vector2, Tile> nested = fixture(9, 5, false, false, true);
            elevation.update(nested); equal(28, elevation.height(0, 0), "Second terrace");
            equal(14, elevation.height(3 * 16, 0), "Outer terrace");
            Map<Vector2, Tile> crater = fixture(9, 5, true, true, false);
            elevation.update(crater); equal(-14, elevation.height(0, 0), "Crater signed level");
            equal(-7, elevation.height(8, -72), "Crater ramp");
            equal(0, elevation.tileHeight(crater.get(new Vector2(-80, -80))), "Crater corner high side");
            Map<Vector2, Tile> smallRim = fixture(8, 7, false, false, false);
            elevation.update(smallRim); equal(0, elevation.height(8 * 16, 0), "Small exterior datum");
            equal(14, elevation.height(0, 0), "Large interior plateau");
            Map<Vector2, Tile> conflicting = fixture(9, 5, false, false, false);
            Tile bad = conflicting.get(new Vector2(0, -80)); bad.biome = "graveyard";
            elevation.update(conflicting);
            check(elevation.getConflicts() > 0, "Contradictory biome rise not reported");
            equal(14, elevation.height(0, 0), "One conflicting edge must not flatten an entire plateau");
            if (Boolean.getBoolean("elevation.savedIsland")) savedIsland();
        }
        private void savedIsland() throws Exception {
            Network.MapSaveData data = com.pkmngen.game.util.Save.readData("../modern-world/smoke-world.sav/map0,0", Network.MapSaveData.class);
            Map<Vector2, Tile> tiles = new LinkedHashMap<>();
            for (Network.TileData value : data.mapTiles.tiles) {
                Network.RouteData savedRoute = data.mapTiles.routes.get(value.routeBelongsTo);
                Route route = savedRoute == null ? null : new Route(savedRoute.name, savedRoute.level);
                Tile tile = Tile.get(value, route); tiles.put(tile.position.cpy(), tile);
            }
            WorldElevation derived = new WorldElevation(); derived.update(tiles);
            int count = 0, slopes = 0, ramps = 0; float min = 0, max = 0;
            for (Tile tile : tiles.values()) {
                float height = derived.tileHeight(tile);
                if (Math.abs(height) > .1f) count++;
                min = Math.min(min, height); max = Math.max(max, height);
                if (ModernWorldGenerator.isRamp(tile)) { ramps++; if (derived.raised(tile)) slopes++; }
            }
            System.out.println("ELEVATION generated island: raised=" + count + "/" + tiles.size() + ", min=" + min + ", max=" + max
                + ", slopes=" + slopes + "/" + ramps + ", outlierConstraints=" + derived.getConflicts());
            check(count > 100, "Generated island lost all derived heights");
        }
    }
    private static Map<Vector2, Tile> fixture(int size, int radius, boolean crater, boolean ramp, boolean nested) {
        Map<Vector2, Tile> map = new LinkedHashMap<>(); Route route = new Route("mountain1", 22);
        for (int y = -size; y <= size; y++) for (int x = -size; x <= size; x++) {
            String upper = ring(x, y, radius, crater);
            if (nested && upper.isEmpty()) upper = ring(x, y, 2, false);
            String name = "mountain3";
            if (ramp && y == -radius && Math.abs(x) <= 1) { name = "ledge_grass_ramp"; upper = ""; }
            Tile tile = new Tile(name, upper, new Vector2(x * 16, y * 16), true, route);
            tile.biome = "mountain"; map.put(tile.position.cpy(), tile);
        }
        return map;
    }
    private static String ring(int x, int y, int r, boolean inverse) {
        if (Math.max(Math.abs(x), Math.abs(y)) != r) return "";
        String sides = "";
        if (y == -r) sides += inverse ? "S" : "N";
        if (y == r) sides += inverse ? "N" : "S";
        if (x == -r) sides += inverse ? "W" : "E";
        if (x == r) sides += inverse ? "E" : "W";
        return "ledges3_" + sides;
    }
    private static void equal(float expected, float actual, String message) {
        check(Math.abs(expected - actual) < .001f, message + ": expected=" + expected + ", actual=" + actual);
    }
    private static void check(boolean result, String message) { if (!result) throw new IllegalStateException(message); }
}
