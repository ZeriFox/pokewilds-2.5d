package com.pkmngen.game;

import com.badlogic.gdx.math.Vector2;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Authored terrain modules assembled during NEW island generation, before spawns.
 * A symbol determines ground, obstacle collision and elevation together. The
 * renderer reads the resulting ordinary Tiles; there is no second visual map. */
public final class AuthoredTerrain {
    public static final int SIZE = 17;
    private static final int[] DX = {0, 1, 0, -1}, DY = {1, 0, -1, 0};
    private static final String SYMBOLS = ".ptfohHR";

    // Rows are north to south. Ports are three tiles wide; trees have deliberately
    // spaced trunks. h/H are raised grass/path, R a real, walkable stair opening.
    private static final Module CLEARING = new Module("meadow-crossing",
        ".......ppp.......",
        ".......ppp.......",
        "..t....ppp...t...",
        "....ff.ppp.......",
        "..t....ppp..ff...",
        ".......pppp......",
        "......ppppp......",
        "ppppppppppppppppp",
        "ppppppppppppppppp",
        "ppppppppppppppppp",
        "......ppppp......",
        ".......pppp...t..",
        "..t....ppp.......",
        "....ff.ppp..ff...",
        "...t...ppp....t..",
        ".......ppp.......",
        ".......ppp.......");

    private static final Module GROVE = new Module("woodland-bend",
        ".......ppp.......",
        ".......ppp.......",
        "..t....ppp...t...",
        ".....t.ppp.......",
        ".f.....pppp......",
        "...t...ppppp..t..",
        "......pppppp.....",
        "ppppppppppppppppp",
        "ppppppppppppppppp",
        "ppppppppppppppppp",
        "....pppppp.......",
        "..t.pppppp...t...",
        ".....ppppp.......",
        "...f..pppp.t.....",
        "..t....ppp....t..",
        ".......ppp.......",
        ".......ppp.......");

    private static final Module TERRACE = new Module("double-stair-terrace",
        ".......ppp.......",
        ".......ppp.......",
        "..ppppppppppppp..",
        "..pp...ppp...pp..",
        "..pphhhRRRhhhpp..",
        "..pphhhHHHhhhpp..",
        "..pphhhHHHhhhpp..",
        "pppphhhHHHhhhpppp",
        "pppphhhHHHhhhpppp",
        "pppphhhHHHhhhpppp",
        "..pphhhHHHhhhpp..",
        "..pphhhHHHhhhpp..",
        "..pphhhRRRhhhpp..",
        "..pp...ppp...pp..",
        "..ppppppppppppp..",
        ".......ppp.......",
        ".......ppp.......");

    private static final Module[] MODULES = {CLEARING, GROVE, TERRACE};
    private static final Module SMALL_CLEARING = new Module("small-woodland-clearing",
        "...ppp...", "...ppp...", ".t.ppp.t.", "ppppppppp", "ppppppppp",
        "ppppppppp", "...ppp...", ".t.ppp.t.", "...ppp...");
    private static final Module SMALL_GROVE = new Module("small-woodland-path",
        "...ppp...", "...ppp...", ".f.ppp.t.", "ppppppppp", "ppppppppp",
        "ppppppppp", "...ppp...", ".t.ppp.f.", "...ppp...");
    private static final Module SMALL_TERRACE = new Module("small-double-stair-terrace",
        "...ppp...", ".ppppppp.", ".phRRRhp.", "pphHHHhpp", "pphHHHhpp",
        "pphHHHhpp", ".phRRRhp.", ".ppppppp.", "...ppp...");
    private static final Module[] SMALL_MODULES = {SMALL_CLEARING, SMALL_GROVE, SMALL_TERRACE};
    private AuthoredTerrain() {}

    public static Report apply(Map<Vector2, Tile> tiles, long seed, Set<Vector2> reservedPositions) {
        Grid grid = new Grid(tiles);
        Report report = new Report();
        Set<Long> reserved = new HashSet<>();
        for (Vector2 pos : reservedPositions) reserved.add(pack(Math.round(pos.x / 16), Math.round(pos.y / 16)));
        prepareNaturalInterior(grid, seed, reserved, report);
        // Seeded phase and module choices never consume the game's encounter RNG.
        Set<Long> occupied = new HashSet<>();
        assemble(grid, seed, reserved, occupied, MODULES, report);
        // Nine-cell pieces fill narrow but genuinely inland areas where a full
        // seventeen-cell room cannot fit. Never stamp over an accepted module.
        assemble(grid, seed ^ 0x534d414c4cL, reserved, occupied, SMALL_MODULES, report);
        return report;
    }

    private static void assemble(Grid grid, long seed, Set<Long> reserved, Set<Long> occupied, Module[] modules, Report report) {
        int size = modules[0].size;
        int phaseX = Math.floorMod(mix(seed), size), phaseY = Math.floorMod(mix(seed ^ 0x50415448L), size);
        Set<Long> origins = new HashSet<>();
        for (Tile tile : grid.tiles.values()) {
            int x = Math.round(tile.position.x / 16), y = Math.round(tile.position.y / 16);
            origins.add(pack(Math.floorDiv(x - phaseX, size) * size + phaseX,
                Math.floorDiv(y - phaseY, size) * size + phaseY));
        }
        List<Long> ordered = new ArrayList<>(origins);
        ordered.sort(Comparator.comparingInt(AuthoredTerrain::unpackY).thenComparingInt(AuthoredTerrain::unpackX));
        for (long origin : ordered) {
            int x = unpackX(origin), y = unpackY(origin);
            Candidate candidate = candidate(grid, x, y, size, reserved, occupied, report);
            if (candidate == null) { report.rejectedBoundaries++; continue; }
            long selection = mix(seed ^ origin);
            Module module = modules[Math.floorMod(selection, modules.length)];
            boolean flipX = (selection & 8) != 0, flipY = (selection & 16) != 0;
            if (module.terrace && !terraceFits(candidate, module, flipX, flipY)) module = modules[1];
            Plan plan = plan(candidate, module, flipX, flipY);
            if (!connected(candidate, plan)) { report.rejectedConnectivity++; continue; }
            // Validation happens before mutation. No footprint, route, item or
            // scripted object is replaced, and no new save format is needed.
            for (int i = 0; i < plan.cells.length; i++) {
                Cell cell = plan.cells[i];
                if (cell == null) continue;
                Tile tile = candidate.cells[i];
                replace(tile, cell.ground, cell.upper);
                report.cellsRebuilt++;
                if (cell.ramp) report.ramps++;
                if (cell.upper.startsWith("ledges3")) report.cliffCells++;
                if (cell.tree) report.treeTrunks++;
            }
            report.modules++;
            if (module.terrace) report.terraces++;
            if (size < SIZE) report.smallModules++;
            report.protectedCells += candidate.protectedCount;
            report.placements.add(new Placement(module.name, x, y, size));
            for (int dy = 0; dy < size; dy++) for (int dx = 0; dx < size; dx++) occupied.add(pack(x + dx, y + dy));
        }
    }

    public static Report apply(Map<Vector2, Tile> tiles, long seed) {
        return apply(tiles, seed, Collections.emptySet());
    }

    /** Build a new, connected land substrate before arranging modules. Retaining
     * every old random ledge as a placement constraint made real islands reject
     * virtually all rooms. Old contours and scattered vegetation are removed in
     * the safe natural interior; only the authored modules create new contours.
     * Starting the flood at old walkable cells means opening ground cannot add
     * an inaccessible dry component or break any old connection. */
    private static void prepareNaturalInterior(Grid grid, long seed, Set<Long> reserved, Report report) {
        Set<Long> eligible = new HashSet<>();
        for (Map.Entry<Long, Tile> entry : grid.tiles.entrySet()) {
            Tile tile = entry.getValue();
            if (!naturalGround(tile) || protectedCell(tile)) continue;
            int x = unpackX(entry.getKey()), y = unpackY(entry.getKey());
            boolean safe = true;
            for (int dy = -1; dy <= 1 && safe; dy++) for (int dx = -1; dx <= 1; dx++) {
                Tile neighbor = grid.at(x + dx, y + dy);
                if (!naturalGround(neighbor) || reserved.contains(pack(x + dx, y + dy))
                    || protectedCell(neighbor) && !ordinaryEncounterTree(neighbor)) { safe = false; break; }
            }
            if (safe) eligible.add(entry.getKey());
        }
        Set<Long> connected = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (long position : eligible) if (ModernWorldGenerator.isWalkable(grid.tiles.get(position))) {
            connected.add(position); queue.addLast(position);
        }
        while (!queue.isEmpty()) {
            long position = queue.removeFirst(); int x = unpackX(position), y = unpackY(position);
            for (int direction = 0; direction < 4; direction++) {
                long next = pack(x + DX[direction], y + DY[direction]);
                if (eligible.contains(next) && connected.add(next)) queue.addLast(next);
            }
        }
        List<Long> ordered = new ArrayList<>(connected);
        ordered.sort(Comparator.comparingInt(AuthoredTerrain::unpackY).thenComparingInt(AuthoredTerrain::unpackX));
        for (long position : ordered) {
            Tile tile = grid.tiles.get(position);
            String family = family(tile);
            int x = unpackX(position), y = unpackY(position);
            // The partial edge of a room still uses the authored open-ground
            // vocabulary; it does not inherit the old speckled texture pattern.
            char symbol = SMALL_GROVE.at(Math.floorMod(x, 9), Math.floorMod(y, 9), (seed & 1) != 0, false);
            String ground = family.equals("snow") ? "snow1" : family.equals("mountain") ? "mountain3"
                : symbol == 'f' ? "grass4" : "green1";
            if (tile.isLedge) report.oldCliffsRemoved++;
            if (tile.isSolid && (tile.nameUpper.contains("tree") || tile.name.contains("tree"))) report.oldTreesReplanned++;
            replace(tile, ground, "");
            report.preparedCells++;
        }
    }

    private static void replace(Tile tile, String ground, String upper) {
        String biome = tile.biome;
        tile.shoreOcean = null; tile.shoreTidal = null;
        tile.drawAsTree = false; tile.drawSavannaTree = false; tile.yOffset = 0; tile.isBottomMtnLayer = false;
        tile.init(ground, upper, tile.position, true, tile.routeBelongsTo);
        tile.biome = biome;
    }

    /** A complete natural inland footprint, with a protected halo around events.
     * Saved ledges may be redesigned only when wholly inside the footprint;
     * cutting a contour at its edge would create an ambiguous height seam. */
    private static Candidate candidate(Grid grid, int ox, int oy, int size, Set<Long> reserved, Set<Long> occupied, Report report) {
        Candidate result = new Candidate(size);
        String family = null;
        for (int y = -1; y <= size; y++) for (int x = -1; x <= size; x++) {
            Tile tile = grid.at(ox + x, oy + y);
            if (x >= 0 && y >= 0 && x < size && y < size && occupied.contains(pack(ox + x, oy + y))) { report.rejectedOverlap++; return null; }
            if (!naturalGround(tile)) { report.rejectedNatural++; return null; }
            String nextFamily = family(tile);
            if (family == null) family = nextFamily;
            if (!family.equals(nextFamily)) { report.rejectedBiome++; return null; }
            boolean edge = x < 2 || y < 2 || x >= size - 2 || y >= size - 2;
            if (edge && (tile.isLedge || ModernWorldGenerator.isRamp(tile))) { report.rejectedContour++; return null; }
            boolean reservedActor = reserved.contains(pack(ox + x, oy + y));
            if (protectedCell(tile) || reservedActor) {
                // Ordinary headbutt encounters remain on their original tree.
                // Unlike a scripted landmark, this one-cell trunk does not
                // require freezing its entire old random vegetation patch.
                int halo = !reservedActor && ordinaryEncounterTree(tile) ? 0 : 1;
                for (int dy = -halo; dy <= halo; dy++) for (int dx = -halo; dx <= halo; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx >= 0 && ny >= 0 && nx < size && ny < size) result.keep[ny * size + nx] = true;
                }
            }
            if (x < 0 || y < 0 || x >= size || y >= size) continue;
            int index = y * size + x;
            result.cells[index] = tile;
        }
        for (boolean keep : result.keep) if (keep) result.protectedCount++;
        // Extensive structures and encounter clusters belong to their original
        // authored location, rather than to a partially stamped module.
        if (result.protectedCount > size * size / 5) { report.rejectedProtected++; return null; }
        result.family = family;
        return result;
    }

    private static String family(Tile tile) {
        String profile = BiomeProfiles.forTile(tile).id;
        return profile.equals("forest") || profile.equals("plains") ? "temperate" : profile;
    }
    private static boolean naturalGround(Tile tile) {
        if (tile == null || tile.getClass() != Tile.class || tile.isWater || tile.isLava || tile.isTidal || tile.isWaterfall) return false;
        if (tile.routeBelongsTo != null && tile.routeBelongsTo.isDungeon) return false;
        String biome = family(tile);
        if (!(biome.equals("temperate") || biome.equals("wetland") || biome.equals("mountain") || biome.equals("snow"))) return false;
        // Deliberately excludes maze trees, hidden multi-cell tree blockers,
        // ruins, pits, entrances, player construction and shore/puddle variants.
        return tile.name.matches("(?:green[0-9]+|green_woodedlake|grass[1-4]|flower[1-4]|mountain[1-6]|snow[1-6]|tree[24567]|ledge_grass_ramp|path[1-3]|ground[1-3])");
    }
    private static boolean protectedCell(Tile tile) {
        if (tile.hasItem != null || tile.items != null || tile.doorTiles != null || tile.squishedTiles != null) return true;
        if (tile.routeBelongsTo != null && !tile.routeBelongsTo.storedPokemon.isEmpty()) return true;
        String upper = tile.nameUpper;
        return !(upper.isEmpty() || upper.matches("(?:tree[2467]|bush2_color[0-9]*|grass[1-4]|flower[1-4]|rock[15](?:_color)?|rock_ice2|ledges3(?:snow)?_[NSEW]+(?:_inner)?)"));
    }
    private static boolean ordinaryEncounterTree(Tile tile) {
        return tile.hasItem == null && tile.items == null && tile.doorTiles == null && tile.squishedTiles == null
            && tile.nameUpper.matches("tree[2467]") && tile.routeBelongsTo != null
            && tile.routeBelongsTo.name.isEmpty() && !tile.routeBelongsTo.storedPokemon.isEmpty();
    }

    private static Plan plan(Candidate candidate, Module module, boolean flipX, boolean flipY) {
        int size = candidate.size;
        Plan plan = new Plan(size);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int index = y * size + x;
            if (candidate.keep[index]) continue;
            char symbol = module.at(x, y, flipX, flipY);
            boolean snowy = candidate.family.equals("snow"), mountain = candidate.family.equals("mountain");
            String ground = snowy ? "snow1" : mountain ? "mountain3" : "green1", upper = "";
            boolean path = symbol == 'p' || symbol == 'H', tree = symbol == 't', ramp = symbol == 'R';
            if (path) ground = snowy ? "snow1" : "path1";
            if (tree) upper = snowy ? "tree4" : "tree2";
            if (symbol == 'f') ground = snowy ? "snow1" : mountain ? "mountain3" : "grass4";
            if (symbol == 'o') upper = snowy ? "rock_ice2" : "rock1_color";
            if (ramp) ground = "ledge_grass_ramp";
            else if (raised(symbol)) {
                String directions = "";
                if (!raised(module.at(x, y - 1, flipX, flipY))) directions += "N";
                if (!raised(module.at(x, y + 1, flipX, flipY))) directions += "S";
                if (!raised(module.at(x - 1, y, flipX, flipY))) directions += "E";
                if (!raised(module.at(x + 1, y, flipX, flipY))) directions += "W";
                if (!directions.isEmpty()) upper = (snowy ? "ledges3snow_" : "ledges3_") + directions;
            }
            // A held event's original neighbor cells stay untouched. A new tree
            // next to one would still change access; keep that approach open.
            if (tree && protectedNeighbor(candidate, x, y)) { upper = ""; tree = false; }
            boolean walkable = !tree && symbol != 'o' && !upper.startsWith("ledges3");
            plan.cells[index] = new Cell(ground, upper, walkable, ramp, tree);
        }
        return plan;
    }
    private static boolean protectedNeighbor(Candidate candidate, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            int nx = x + dx, ny = y + dy;
            if (nx >= 0 && ny >= 0 && nx < candidate.size && ny < candidate.size && candidate.keep[ny * candidate.size + nx]) return true;
        }
        return false;
    }
    private static boolean raised(char symbol) { return symbol == 'h' || symbol == 'H' || symbol == 'R'; }

    private static boolean terraceFits(Candidate candidate, Module module, boolean flipX, boolean flipY) {
        for (int y = 0; y < candidate.size; y++) for (int x = 0; x < candidate.size; x++) {
            if (!candidate.keep[y * candidate.size + x]) continue;
            // Preserve the entire raised footprint, retaining wall sides and
            // clear landings. Protected low corners do not flatten a whole hill.
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
                if (raised(module.at(x + dx, y + dy, flipX, flipY))) return false;
        }
        return true;
    }

    /** Every dry cell in the proposed module must reach its border. Every old
     * walkable border cell remains open: all external connections are retained,
     * even when their routes lie outside this module or another biome. */
    private static boolean connected(Candidate before, Plan plan) {
        int size = before.size;
        boolean[] walkable = new boolean[size * size], seen = new boolean[size * size];
        int start = -1, count = 0;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int index = y * size + x;
            walkable[index] = plan.cells[index] == null ? ModernWorldGenerator.isWalkable(before.cells[index]) : plan.cells[index].walkable;
            boolean edge = x == 0 || y == 0 || x == size - 1 || y == size - 1;
            if (edge && ModernWorldGenerator.isWalkable(before.cells[index])) {
                if (!walkable[index]) return false;
                start = index;
            }
            if (walkable[index]) count++;
        }
        if (start < 0) return false;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start); seen[start] = true; int visited = 0;
        while (!queue.isEmpty()) {
            int index = queue.removeFirst(), x = index % size, y = index / size; visited++;
            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d], ny = y + DY[d], next = ny * size + nx;
                if (nx < 0 || ny < 0 || nx >= size || ny >= size || !walkable[next] || seen[next]) continue;
                seen[next] = true; queue.addLast(next);
            }
        }
        return visited == count;
    }

    private static long mix(long value) {
        value = (value ^ value >>> 30) * 0xbf58476d1ce4e5b9L;
        value = (value ^ value >>> 27) * 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }
    private static long pack(int x, int y) { return (long)x << 32 | y & 0xffffffffL; }
    private static int unpackX(long value) { return (int)(value >> 32); }
    private static int unpackY(long value) { return (int)value; }
    private static final class Grid {
        final Map<Long, Tile> tiles = new HashMap<>();
        Grid(Map<Vector2, Tile> source) {
            for (Tile tile : source.values()) tiles.put(pack(Math.round(tile.position.x / 16), Math.round(tile.position.y / 16)), tile);
        }
        Tile at(int x, int y) { return tiles.get(pack(x, y)); }
    }
    private static final class Module {
        final String name; final String[] rows; final int size; final boolean terrace;
        Module(String name, String... rows) {
            this.name = name; this.rows = rows; this.size = rows.length; this.terrace = name.contains("terrace");
            if (size < 3) throw new IllegalArgumentException(name + ": invalid height");
            for (String row : rows) {
                if (row.length() != size) throw new IllegalArgumentException(name + ": invalid width");
                for (char c : row.toCharArray()) if (SYMBOLS.indexOf(c) < 0) throw new IllegalArgumentException(name + ": invalid symbol " + c);
            }
        }
        char at(int x, int y, boolean flipX, boolean flipY) {
            if (x < 0 || y < 0 || x >= size || y >= size) return '.';
            return rows[flipY ? y : size - 1 - y].charAt(flipX ? size - 1 - x : x);
        }
    }
    private static final class Candidate {
        final Tile[] cells; final boolean[] keep; final int size;
        int protectedCount; String family;
        Candidate(int size) { this.size = size; cells = new Tile[size * size]; keep = new boolean[size * size]; }
    }
    private static final class Plan { final Cell[] cells; Plan(int size) { cells = new Cell[size * size]; } }
    private static final class Cell {
        final String ground, upper; final boolean walkable, ramp, tree;
        Cell(String ground, String upper, boolean walkable, boolean ramp, boolean tree) {
            this.ground = ground; this.upper = upper; this.walkable = walkable; this.ramp = ramp; this.tree = tree;
        }
    }
    public static final class Placement {
        public final String module; public final int x, y, size;
        Placement(String module, int x, int y, int size) { this.module = module; this.x = x; this.y = y; this.size = size; }
    }
    public static final class Report {
        public int modules, smallModules, terraces, cellsRebuilt, ramps, cliffCells, treeTrunks, protectedCells, rejectedBoundaries, rejectedConnectivity;
        public int preparedCells, oldCliffsRemoved, oldTreesReplanned;
        public int rejectedNatural, rejectedBiome, rejectedContour, rejectedProtected, rejectedOverlap;
        public final List<Placement> placements = new ArrayList<>();
        @Override public String toString() {
            return "AUTHORED modules=" + modules + " terraces=" + terraces + " rebuilt=" + cellsRebuilt
                + " prepared=" + preparedCells + " oldCliffsRemoved=" + oldCliffsRemoved + " oldTreesReplanned=" + oldTreesReplanned
                + " ramps=" + ramps + " cliffs=" + cliffCells + " trees=" + treeTrunks + " protected=" + protectedCells
                + " small=" + smallModules + " rejectedBoundary=" + rejectedBoundaries + " rejectedConnectivity=" + rejectedConnectivity
                + " rejected[natural/biome/contour/protected/overlap]=" + rejectedNatural + "/" + rejectedBiome + "/" + rejectedContour + "/" + rejectedProtected + "/" + rejectedOverlap;
        }
    }
}
