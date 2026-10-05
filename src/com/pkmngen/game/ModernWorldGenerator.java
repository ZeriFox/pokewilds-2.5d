package com.pkmngen.game;

import com.badlogic.gdx.math.Vector2;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Topology pass for newly generated islands. Uses the existing saved Tile schema. */
public final class ModernWorldGenerator {
    public static final int VERSION = 1;
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DY = {1, 0, -1, 0};
    private ModernWorldGenerator() {}

    public static boolean enabled() {
        return !"classic".equalsIgnoreCase(System.getProperty("pokewilds.worldgen", "modern"));
    }

    public static boolean isVolcanic(Tile tile) {
        return tile != null && ("volcano".equals(tile.biome) || tile.isLava || tile.name.contains("volcano"));
    }

    public static boolean isRamp(Tile tile) {
        return tile != null && "ledge_grass_ramp".equals(tile.name) && tile.nameUpper.isEmpty();
    }

    public static String biomeLabel(Tile tile) {
        return isVolcanic(tile) ? "EMBER CALDERA" : tile == null ? "" : tile.biome.replace('_', ' ').toUpperCase(java.util.Locale.ROOT);
    }

    /** Dry movement without field moves; ledges are excluded because they are directional. */
    public static boolean isWalkable(Tile tile) {
        return tile != null && !tile.isSolid && !tile.isLedge && !tile.isWater && !tile.isLava
            && !tile.isWaterfall && !tile.isTidal;
    }

    /** Called once, before wild Pokemon placement and shoreline texture construction. */
    public static Report apply(Map<Vector2, Tile> tiles, Map<Vector2, Pokemon> earlyPokemon) {
        long seed = fingerprint(tiles);
        Report report = apply(tiles, seed);
        // Earlier scripted placements must not leave a Pokemon on a new lava cell.
        earlyPokemon.entrySet().removeIf(entry -> {
            Tile tile = tiles.get(entry.getKey());
            return isVolcanic(tile) && !isWalkable(tile);
        });
        return report;
    }

    /** Explicit seed is useful for repeatable topology tests; no global random state is read. */
    public static Report apply(Map<Vector2, Tile> tiles, long seed) {
        Report report = new Report(seed);
        Grid grid = new Grid(tiles);
        report.tilesBefore = tiles.size();
        report.componentsBefore = components(grid).sizes.size();
        smoothInlandShore(grid, report);
        caldera(grid, seed, report);
        connectMountainTerraces(grid, seed, report);
        report.componentsAfter = components(grid).sizes.size();
        report.tilesAfter = tiles.size();
        for (Tile tile : grid.sorted) {
            if (isVolcanic(tile)) report.volcanicTiles++;
            if (tile.isLava) report.lavaTiles++;
        }
        return report;
    }

    private static void smoothInlandShore(Grid grid, Report report) {
        List<Tile> fill = new ArrayList<>();
        for (Tile tile : grid.sorted) {
            // Keep tidal beaches, ocean routes, waterfalls and all map edge cells intact.
            if (!"water2".equals(tile.name) || !tile.nameUpper.isEmpty() || protectedTile(tile)) continue;
            String route = tile.routeBelongsTo == null ? "" : tile.routeBelongsTo.name;
            if (route.contains("beach") || route.equals("ocean1") || route.equals("sea1")) continue;
            int land = 0;
            boolean missing = false;
            for (int direction = 0; direction < 4; direction++) {
                Tile neighbor = grid.at(tile, DX[direction], DY[direction]);
                missing |= neighbor == null;
                if (isWalkable(neighbor)) land++;
            }
            if (!missing && land >= 3) fill.add(tile);
        }
        for (Tile tile : fill) {
            Tile neighbor = null;
            for (int direction = 0; direction < 4; direction++) {
                Tile candidate = grid.at(tile, DX[direction], DY[direction]);
                if (isWalkable(candidate)) { neighbor = candidate; break; }
            }
            if (neighbor == null) continue;
            replace(tile, "sand3", "", neighbor.biome, neighbor.routeBelongsTo);
            report.shoreCellsSmoothed++;
        }
    }

    private static void caldera(Grid grid, long seed, Report report) {
        // Anchor to the original volcano: never overwrite a dungeon with a random new island.
        long totalX = 0, totalY = 0;
        int count = 0;
        for (Tile tile : grid.sorted) if (isVolcanic(tile) && !protectedTile(tile)) {
            totalX += grid.x(tile); totalY += grid.y(tile); count++;
        }
        if (count < 9) return;
        int cx = (int)(totalX / count), cy = (int)(totalY / count);
        report.centerX = cx * 16;
        report.centerY = cy * 16;
        Route route = new Route("volcano1", 22);
        double radius = Math.max(24, Math.min(42, Math.sqrt(count / Math.PI) * 1.65));
        double phase = (mix(seed) & 65535L) / 65535.0 * Math.PI * 2;
        List<Tile> candidates = new ArrayList<>();
        Set<Long> trails = new HashSet<>();
        for (Tile tile : grid.sorted) {
            if (!naturalTerrain(tile) || protectedTile(tile) || tile.isWater || tile.isWaterfall || tile.isTidal) continue;
            int x = grid.x(tile) - cx, y = grid.y(tile) - cy;
            double distance = Math.hypot(x, y);
            double angle = Math.atan2(y, x);
            double boundary = radius * (1 + .10 * Math.sin(angle * 3 + phase) + .06 * Math.cos(angle * 5 - phase));
            if (distance > boundary) continue;
            boolean trail = Math.abs(x - 2.5 * Math.sin(y * .12 + phase)) <= 1.5
                || Math.abs(y - 2.5 * Math.cos(x * .11 - phase)) <= 1.5
                || Math.abs(distance - radius * .70) < 1.6;
            if (trail) trails.add(grid.key(tile));
            String upper = tile.nameUpper;
            boolean ledge = upper.contains("ledges3");
            if (ledge) upper = upper.replace("ledges3snow", "ledges3volcano").replace("ledges3_", "ledges3volcano_");
            else upper = "";
            if (trail) upper = "";
            String ground = trail ? (ledge ? "ledge_grass_ramp" : "volcano2")
                : tile.isLava ? "lava1" : "volcano1";
            replace(tile, ground, upper, "volcano", route);
            if (trail && ledge) report.rampsOpened++;
            candidates.add(tile);
        }
        // Channels and lava pockets follow a coherent low frequency field. The local
        // connectivity guard prevents every insertion from cutting a dry walking path.
        candidates.sort(Comparator.comparingLong(tile -> mix(seed ^ grid.key(tile))));
        for (Tile tile : candidates) {
            if (!isWalkable(tile) || trails.contains(grid.key(tile))) continue;
            double x = grid.x(tile) - cx, y = grid.y(tile) - cy;
            double distance = Math.hypot(x, y);
            double channel = Math.sin(x * .18 + .9 * Math.sin(y * .11 + phase))
                + .65 * Math.cos(y * .21 - phase);
            boolean lava = distance < radius * .28 || channel > .98 && distance < radius * .86;
            if (lava && preservesLocalPaths(grid, tile)) {
                replace(tile, "lava1", "", "volcano", route);
                report.lavaCellsAdded++;
            }
        }
        // Solid basalt outcrops also preserve all adjacent dry paths. They remain
        // smashable using the game's real rock tile and its original item/spawn rules.
        for (Tile tile : candidates) {
            if (!isWalkable(tile) || trails.contains(grid.key(tile))) continue;
            if (Math.floorMod(mix(seed + grid.key(tile)), 89) == 0 && preservesLocalPaths(grid, tile)) {
                replace(tile, "volcano1", "rock_volcano1", "volcano", route);
                report.basaltOutcrops++;
            }
        }
    }

    private static boolean naturalTerrain(Tile tile) {
        if (tile == null || tile instanceof TrainerTipsTile) return false;
        String name = tile.name;
        return name.startsWith("mountain") || name.startsWith("volcano") || name.startsWith("lava")
            || name.startsWith("snow") || name.startsWith("green") || name.startsWith("grass")
            || name.startsWith("sand") || name.startsWith("tree") || name.startsWith("flower")
            || name.equals("soot1") || isRamp(tile);
    }

    private static boolean protectedTile(Tile tile) {
        if (tile == null || tile.getClass() != Tile.class || tile.hasItem != null || tile.items != null
            || tile.doorTiles != null || tile.squishedTiles != null) return true;
        String upper = tile.nameUpper;
        return !(upper.isEmpty() || upper.startsWith("ledges3") || upper.startsWith("rock")
            || upper.startsWith("tree") || upper.startsWith("grass") || upper.equals("soot1"));
    }

    private static boolean preservesLocalPaths(Grid grid, Tile removed) {
        List<Long> neighbors = new ArrayList<>();
        int cx = grid.x(removed), cy = grid.y(removed);
        for (int d = 0; d < 4; d++) {
            Tile neighbor = grid.at(cx + DX[d], cy + DY[d]);
            if (isWalkable(neighbor)) neighbors.add(grid.key(neighbor));
        }
        if (neighbors.size() < 2) return true;
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        visited.add(neighbors.get(0)); queue.add(neighbors.get(0));
        while (!queue.isEmpty()) {
            long key = queue.removeFirst();
            int x = unpackX(key), y = unpackY(key);
            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d], ny = y + DY[d];
                if (nx == cx && ny == cy || Math.abs(nx - cx) > 3 || Math.abs(ny - cy) > 3) continue;
                long next = pack(nx, ny);
                if (!visited.contains(next) && isWalkable(grid.at(nx, ny))) { visited.add(next); queue.add(next); }
            }
        }
        return visited.containsAll(neighbors);
    }

    private static void connectMountainTerraces(Grid grid, long seed, Report report) {
        Components components = components(grid);
        int[] parents = new int[components.sizes.size()];
        for (int i = 0; i < parents.length; i++) parents[i] = i;
        List<Tile> candidates = new ArrayList<>();
        for (Tile tile : grid.sorted) {
            if (!naturalTerrain(tile) || protectedTile(tile) || tile.isLava || tile.isWater || tile.isWaterfall) continue;
            String upper = tile.nameUpper;
            // These are north/south faces; the original ramp sprite depicts that axis.
            if (upper.matches("ledges3(?:snow|volcano)?_[NS]")) candidates.add(tile);
        }
        candidates.sort(Comparator.comparingLong(tile -> mix(seed ^ grid.key(tile))));
        for (Tile tile : candidates) {
            Tile north = grid.at(tile, 0, 1), south = grid.at(tile, 0, -1);
            if (!isWalkable(north) || !isWalkable(south)) continue;
            Integer first = components.labels.get(grid.key(north)), second = components.labels.get(grid.key(south));
            if (first == null || second == null || find(parents, first) == find(parents, second)) continue;
            parents[find(parents, first)] = find(parents, second);
            replace(tile, "ledge_grass_ramp", "", tile.biome, tile.routeBelongsTo);
            report.rampsOpened++;
        }
    }

    private static int find(int[] parents, int i) {
        while (parents[i] != i) { parents[i] = parents[parents[i]]; i = parents[i]; }
        return i;
    }

    private static void replace(Tile tile, String name, String upper, String biome, Route route) {
        tile.shoreOcean = null;
        tile.shoreTidal = null;
        tile.init(name, upper, tile.position, true, route);
        tile.biome = biome;
    }

    public static int dryComponents(Map<Vector2, Tile> tiles) { return components(new Grid(tiles)).sizes.size(); }

    private static Components components(Grid grid) {
        Components result = new Components();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (Tile tile : grid.sorted) {
            long key = grid.key(tile);
            if (!isWalkable(tile) || result.labels.containsKey(key)) continue;
            int label = result.sizes.size(), size = 0;
            result.labels.put(key, label); queue.add(key);
            while (!queue.isEmpty()) {
                long current = queue.removeFirst(); size++;
                int x = unpackX(current), y = unpackY(current);
                for (int d = 0; d < 4; d++) {
                    long next = pack(x + DX[d], y + DY[d]);
                    if (!result.labels.containsKey(next) && isWalkable(grid.cells.get(next))) {
                        result.labels.put(next, label); queue.add(next);
                    }
                }
            }
            result.sizes.add(size);
        }
        return result;
    }

    /** Order-independent fingerprint; subsequent passes never read wall time or Game.rand. */
    public static long fingerprint(Map<Vector2, Tile> tiles) {
        long value = 0x454d4245524cL;
        for (Tile tile : tiles.values()) value += mix(pack(Math.round(tile.position.x / 16), Math.round(tile.position.y / 16))
            ^ ((long)tile.name.hashCode() << 32) ^ tile.nameUpper.hashCode() ^ tile.biome.hashCode());
        return mix(value);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
    private static long pack(int x, int y) { return (long)x << 32 | y & 0xffffffffL; }
    private static int unpackX(long value) { return (int)(value >> 32); }
    private static int unpackY(long value) { return (int)value; }
    private static final class Grid {
        final Map<Long, Tile> cells = new HashMap<>();
        final List<Tile> sorted;
        Grid(Map<Vector2, Tile> tiles) {
            sorted = new ArrayList<>(tiles.values());
            sorted.sort(Comparator.comparingInt(this::y).thenComparingInt(this::x));
            for (Tile tile : sorted) cells.put(key(tile), tile);
        }
        int x(Tile tile) { return Math.round(tile.position.x / 16); }
        int y(Tile tile) { return Math.round(tile.position.y / 16); }
        long key(Tile tile) { return pack(x(tile), y(tile)); }
        Tile at(int x, int y) { return cells.get(pack(x, y)); }
        Tile at(Tile tile, int x, int y) { return at(x(tile) + x, y(tile) + y); }
    }
    private static final class Components {
        final Map<Long, Integer> labels = new HashMap<>();
        final List<Integer> sizes = new ArrayList<>();
    }
    public static final class Report {
        public final long seed;
        public int tilesBefore, tilesAfter, shoreCellsSmoothed, lavaCellsAdded, basaltOutcrops, rampsOpened;
        public int componentsBefore, componentsAfter, volcanicTiles, lavaTiles, centerX, centerY;
        Report(long seed) { this.seed = seed; }
        @Override public String toString() {
            return "WORLDGEN v" + VERSION + " seed=" + seed + " tiles=" + tilesBefore + "->" + tilesAfter
                + " coast=" + shoreCellsSmoothed + " ramps=" + rampsOpened + " dryComponents="
                + componentsBefore + "->" + componentsAfter + " volcanic=" + volcanicTiles
                + " lava=" + lavaTiles + " newLava=" + lavaCellsAdded + " outcrops=" + basaltOutcrops
                + " center=" + centerX + "," + centerY;
        }
    }
}
