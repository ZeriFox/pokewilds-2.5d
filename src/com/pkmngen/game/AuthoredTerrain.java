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
import java.util.TreeMap;

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

    // These pieces have different footprints, not merely different palettes.
    // Dry washes bend between rock clusters; wetland paths follow open banks;
    // ridge shelves have a narrower contour than the forest's square plateau.
    private static final Module DESERT_WASH = new Module("desert-dry-wash",
        "...ppp...", ".o.ppp...", "...pppp..", "....pppp.", ".....pppp",
        "...pppp..", "..pppp.o.", ".pppp....", "pppp.....");
    private static final Module DESERT_MESA = new Module("desert-mesa-terrace",
        "...ppp...", "..ppppp..", "..hRRRh..", "..hHHHh..", "..hHHHh..",
        "..hHHHh..", "..hRRRh..", "..ppppp..", "...ppp...");
    private static final Module DESERT_GARDEN = new Module("desert-cactus-crescent",
        ".........", "..t......", ".....t...", ".f.....t.", ".f..ppp..",
        "...pppp..", "..pppp.o.", "...ppp...", "...ppp...");
    private static final Module BEACH_DUNE = new Module("beach-dune-walk",
        ".........", "..ff..o..", "..ff.....", "pppp.....", "ppppppppp",
        ".....pppp", "..t......", "......t..", ".........");
    private static final Module BEACH_COVE = new Module("beach-rock-cove",
        ".........", "..o...o..", "...fff...", "....ppp..", "ppppppp..",
        "....ppp..", ".t..ppp..", "....ppp..", "....ppp..");
    private static final Module WETLAND_REEDS = new Module("wetland-reed-ring",
        "...ppp...", "..ppppp..", ".ppfffpp.", ".pff.ffp.", "ppf...fpp",
        ".pff.ffp.", ".ppfffpp.", "..ppppp..", "...ppp...");
    private static final Module WETLAND_BANK = new Module("wetland-willow-bank",
        "......ppp", "..t..ppp.", "....ppp..", ".ff.ppp..", ".ff.ppp..",
        "....ppp..", "..t.ppp..", ".....ppp.", "......ppp");
    private static final Module MOUNTAIN_PASS = new Module("mountain-rock-pass",
        "....ppp..", "..o.ppp..", "....ppp..", "..ppppp..", "..ppp....",
        "..ppp.o..", "..ppppp..", "....ppp..", "....ppp..");
    private static final Module MOUNTAIN_RIDGE = new Module("mountain-ridge-terrace",
        "...ppp...", "...ppp...", ".hhRRRhh.", ".hhHHHhh.", ".hhHHHhh.",
        ".hhHHHhh.", ".hhRRRhh.", "...ppp...", "...ppp...");
    private static final Module SNOW_SHELTER = new Module("snow-pine-shelter",
        "...ppp...", ".ffpppff.", ".t.ppp.t.", "...ppp...", "...pppppp",
        ".ffpppff.", ".t.ppp.t.", "...ppp...", "...ppp...");
    private static final Module SNOW_CIRQUE = new Module("snow-cirque-terrace",
        "...ppp...", "...ppp...", "..hRRRh..", "..hHHHh..", "..hHHHh..",
        "..hHHHh..", "..hRRRh..", "...ppp...", "...ppp...");
    private static final Module OASIS_ORCHARD = new Module("oasis-palm-court",
        "...ppp...", "...ppp...", ".t.ppp.t.", "...ppp...", "ppppppppp",
        "...ppp...", ".t.ppp.t.", "...ppp...", "...ppp...");
    private static final Module OASIS_GARDEN = new Module("oasis-grass-garden",
        "...ppp...", "..ppppp..", ".pp...pp.", ".p.fff.p.", "pp.fff.pp",
        ".p.fff.p.", ".pp...pp.", "..ppppp..", "...ppp...");
    private static final Module SAVANNA_ACACIA = new Module("savanna-acacia-glade",
        ".........", "..t......", "......ff.", "...pppff.", "ppppppppp",
        "...ppp...", ".ff...t..", ".ff......", ".........");
    private static final Module SAVANNA_WASH = new Module("savanna-grass-ribbon",
        "...ppp...", ".ffppp...", ".ffppp.o.", "..pppp...", "..ppp....",
        "..pppff..", "..pppff..", "...pppp..", "....ppp..");
    private static final Module POCKET_GROVE = new Module("woodland-pocket",
        "..ppp", ".tppp", "ppppp", "pppt.", "ppp..");
    private static final Module POCKET_WASH = new Module("desert-pocket-wash",
        "...pp", ".o.pp", "..ppp", "ppp..", "pp...");
    private static final Module POCKET_COAST = new Module("beach-pocket-dune",
        ".....", ".ff..", "ppppp", "..ppp", "..ppp");
    private static final Module POCKET_MARSH = new Module("wetland-pocket-bank",
        "..ppp", ".fppp", ".fppp", "..ppp", "..ppp");
    private static final Module POCKET_ROCK = new Module("mountain-pocket-pass",
        "..ppp", ".oppp", "..ppp", "ppppp", "ppp..");
    private static final Module POCKET_SNOW = new Module("snow-pocket-shelter",
        "ffppp", ".tppp", "ffppp", "ffppp", "ffppp");
    private static final Module POCKET_OASIS = new Module("oasis-pocket-court",
        "ppppp", "p.f.p", "p.f.p", "p...p", "ppppp");
    private static final Module POCKET_SAVANNA = new Module("savanna-pocket-grass",
        "..ppp", ".fppp", ".fppp", "..ppp", "ppppp");
    private static final Module DEEP_ROOM = new Module("deep-forest-root-room",
        ".....", ".t.f.", "..ppp", ".fppp", "..ppp");
    private static final Module DEEP_TURN = new Module("deep-forest-turn",
        "..ppp", ".fppp", "ppppp", "pppf.", "ppp..");
    private static final Module GRAVE_COURT = new Module("graveyard-memorial-court",
        "ppppp", "p.f.p", "p...p", "p.f.p", "ppppp");
    private static final Module GRAVE_PATH = new Module("graveyard-quiet-path",
        "..ppp", ".fppp", "..ppp", "ppppp", "ppp..");
    private static final Module[] EMPTY_MODULES = {};

    private static Module[] catalog(String family, int size) {
        if (size == SIZE) return family.equals("temperate") ? MODULES : EMPTY_MODULES;
        if (size == 5) switch (family) {
            case "temperate": return new Module[]{POCKET_GROVE};
            case "desert": return new Module[]{POCKET_WASH};
            case "beach": return new Module[]{POCKET_COAST};
            case "wetland": return new Module[]{POCKET_MARSH};
            case "mountain": return new Module[]{POCKET_ROCK};
            case "snow": return new Module[]{POCKET_SNOW};
            case "oasis": return new Module[]{POCKET_OASIS};
            case "savanna": return new Module[]{POCKET_SAVANNA};
            case "deep_forest": return new Module[]{DEEP_ROOM, DEEP_TURN};
            case "graveyard": return new Module[]{GRAVE_COURT, GRAVE_PATH};
            default: return EMPTY_MODULES;
        }
        switch (family) {
            case "temperate": return SMALL_MODULES;
            case "desert": return new Module[]{DESERT_WASH, DESERT_GARDEN, DESERT_MESA};
            case "beach": return new Module[]{BEACH_DUNE, BEACH_COVE};
            case "wetland": return new Module[]{WETLAND_REEDS, WETLAND_BANK};
            case "mountain": return new Module[]{MOUNTAIN_PASS, MOUNTAIN_RIDGE};
            case "snow": return new Module[]{SNOW_SHELTER, SNOW_CIRQUE};
            case "oasis": return new Module[]{OASIS_ORCHARD, OASIS_GARDEN};
            case "savanna": return new Module[]{SAVANNA_ACACIA, SAVANNA_WASH};
            default: return EMPTY_MODULES;
        }
    }
    private AuthoredTerrain() {}

    public static Report apply(Map<Vector2, Tile> tiles, long seed, Set<Vector2> reservedPositions) {
        Grid grid = new Grid(tiles);
        Report report = new Report();
        for (Tile tile : grid.tiles.values()) if (naturalGround(tile))
            report.naturalByBiome.merge(family(tile), 1, Integer::sum);
        Set<Long> reserved = new HashSet<>();
        for (Vector2 pos : reservedPositions) reserved.add(pack(Math.round(pos.x / 16), Math.round(pos.y / 16)));
        prepareNaturalInterior(grid, seed, reserved, report);
        // Seeded phase and module choices never consume the game's encounter RNG.
        Set<Long> occupied = new HashSet<>();
        assemble(grid, seed, reserved, occupied, SIZE, report);
        // Nine-cell pieces fill narrow but genuinely inland areas where a full
        // seventeen-cell room cannot fit. Never stamp over an accepted module.
        assemble(grid, seed ^ 0x534d414c4cL, reserved, occupied, 9, report);
        assemble(grid, seed ^ 0x504f434b4554L, reserved, occupied, 5, report);
        return report;
    }

    private static void assemble(Grid grid, long seed, Set<Long> reserved, Set<Long> occupied, int size, Report report) {
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
            Tile center = grid.at(x + size / 2, y + size / 2);
            if (center == null) continue;
            Module[] modules = catalog(family(center), size);
            if (modules.length == 0) continue;
            Candidate candidate = candidate(grid, x, y, size, family(center), reserved, occupied, report);
            if (candidate == null) { report.rejectedBoundaries++; continue; }
            long selection = mix(seed ^ origin);
            Module module = modules[Math.floorMod(selection, modules.length)];
            boolean flipX = (selection & 8) != 0, flipY = (selection & 16) != 0;
            if (module.terrace && !terraceFits(candidate, module, flipX, flipY)) module = modules[0];
            Plan plan = plan(candidate, module, flipX, flipY);
            int changed = 0;
            for (int i = 0; i < plan.cells.length; i++) if (plan.cells[i] != null
                && (!plan.cells[i].ground.equals(candidate.cells[i].name) || !plan.cells[i].upper.equals(candidate.cells[i].nameUpper))) changed++;
            if (changed < (size == 5 ? 8 : size)) { report.rejectedUnchanged++; continue; }
            if (!connected(candidate, plan)) { report.rejectedConnectivity++; continue; }
            // Validation happens before mutation. No footprint, route, item or
            // scripted object is replaced, and no new save format is needed.
            for (int i = 0; i < plan.cells.length; i++) {
                Cell cell = plan.cells[i];
                if (cell == null) continue;
                Tile tile = candidate.cells[i];
                replace(tile, cell.ground, cell.upper);
                report.cellsRebuilt++;
                report.rebuiltByBiome.merge(candidate.family, 1, Integer::sum);
                if (cell.ramp) report.ramps++;
                if (cell.upper.startsWith("ledges3")) report.cliffCells++;
                if (cell.tree) report.treeTrunks++;
            }
            report.modules++;
            report.changedCells += changed;
            report.modulesByBiome.merge(candidate.family, 1, Integer::sum);
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
            String ground = symbol == 'f' ? grassGround(tile, family) : baseGround(tile, family);
            if (tile.isLedge) report.oldCliffsRemoved++;
            if (tile.isSolid && (tile.nameUpper.contains("tree") || tile.name.contains("tree"))) report.oldTreesReplanned++;
            replace(tile, ground, "");
            report.preparedCells++;
            report.preparedByBiome.merge(family, 1, Integer::sum);
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
    private static Candidate candidate(Grid grid, int ox, int oy, int size, String family, Set<Long> reserved, Set<Long> occupied, Report report) {
        Candidate result = new Candidate(size);
        for (int y = -1; y <= size; y++) for (int x = -1; x <= size; x++) {
            Tile tile = grid.at(ox + x, oy + y);
            if (x >= 0 && y >= 0 && x < size && y < size && occupied.contains(pack(ox + x, oy + y))) { report.rejectedOverlap++; return null; }
            boolean natural = naturalGround(tile);
            if (!natural || !family.equals(family(tile))) {
                // Flat pocket pieces can follow a real bank or maze corridor.
                // Their water/walls and foreign-biome cells are immutable, never
                // converted into the module's material or collision shape.
                if (size == 5 && retainedBoundary(tile, family) && !protectedCell(tile)
                    && !reserved.contains(pack(ox + x, oy + y))) {
                    if (x >= 0 && y >= 0 && x < size && y < size) {
                        result.keep[y * size + x] = true; result.cells[y * size + x] = tile;
                        result.retainedBoundary++;
                    }
                    continue;
                }
                if (!natural) report.rejectedNatural++; else report.rejectedBiome++;
                return null;
            }
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
        if (size == 5 && size * size - result.protectedCount < 10
            || result.protectedCount - result.retainedBoundary > (size * size - result.retainedBoundary) / 5) {
            report.rejectedProtected++; return null;
        }
        result.family = family;
        return result;
    }

    private static boolean retainedBoundary(Tile tile, String family) {
        if (tile == null || tile.getClass() != Tile.class || tile.routeBelongsTo != null && tile.routeBelongsTo.isDungeon) return false;
        if (tile.isWater || tile.isTidal || tile.isWaterfall || tile.name.contains("_tidal") || tile.name.startsWith("shore")) return true;
        if (naturalGround(tile) && !family(tile).equals(family) && !tile.isLedge && !ModernWorldGenerator.isRamp(tile)) return true;
        // All cells of a multi-cell tree remain untouched, including its hidden
        // blockers. No partial tree is opened or detached from its anchor.
        if (tile.name.contains("tree_large") || tile.nameUpper.contains("tree_large")) return true;
        return family.equals("deep_forest") && family(tile).equals(family) && tile.isSolid
            && (tile.name.matches("tree[1567]") || tile.nameUpper.matches("tree[2467]"));
    }

    private static String family(Tile tile) {
        String profile = BiomeProfiles.forTile(tile).id;
        return profile.equals("forest") || profile.equals("plains") ? "temperate" : profile;
    }
    private static boolean naturalGround(Tile tile) {
        if (tile == null || tile.getClass() != Tile.class || tile.isWater || tile.isLava || tile.isTidal || tile.isWaterfall) return false;
        if (tile.routeBelongsTo != null && tile.routeBelongsTo.isDungeon) return false;
        String biome = family(tile);
        if (!(biome.equals("temperate") || biome.equals("wetland") || biome.equals("mountain") || biome.equals("snow")
            || biome.equals("desert") || biome.equals("beach") || biome.equals("oasis") || biome.equals("savanna")
            || biome.equals("deep_forest") || biome.equals("graveyard"))) return false;
        // Deep forest walls encode a maze. Only existing open rooms can receive
        // small pieces; preparation cannot open a wall or bypass its puzzle.
        if ((biome.equals("deep_forest") || biome.equals("graveyard")) && !ModernWorldGenerator.isWalkable(tile)) return false;
        String route = tile.routeBelongsTo == null ? "" : tile.routeBelongsTo.name;
        if (route.startsWith("ruins") || route.equals("sand_pit1")) return false;
        // No sand pits, trapinch triggers, cracked floors, ice sliding, shores,
        // tidal variants, large-tree blockers or multi-cell cactus footprints.
        return tile.name.matches("(?:green[0-9]+|green_woodedlake|green_deepforest|green_savanna[1-5]|grass[1-4]|grass_sand[23]|grass_savanna[12]|grass_graveyard1|flower[1-4]|mountain[1-6]|snow[1-6]|sand[1-4]|desert[146]|tree[2467]|ledge_grass_ramp|path[1-3]|ground[1-3])");
    }
    private static boolean protectedCell(Tile tile) {
        if (tile.hasItem != null || tile.items != null || tile.doorTiles != null || tile.squishedTiles != null) return true;
        if (tile.routeBelongsTo != null && !tile.routeBelongsTo.storedPokemon.isEmpty()) return true;
        String upper = tile.nameUpper;
        return !(upper.isEmpty() || upper.matches("(?:tree[2467]|tree_savanna1|bush_savanna1|grass_savanna[12]|cactus[1-6]|bush2_color[0-9]*|grass[1-4]|flower[1-4]|rock[15](?:_color)?|rock_ice2|ledges3(?:snow|desert)?_[NSEW]+(?:_inner)?)"));
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
            boolean snowy = candidate.family.equals("snow");
            String ground = baseGround(candidate.cells[index], candidate.family), upper = "";
            boolean path = symbol == 'p' || symbol == 'H', tree = symbol == 't', ramp = symbol == 'R';
            if (path) ground = pathGround(candidate.cells[index], candidate.family);
            if (tree) upper = snowy ? "tree4" : candidate.family.equals("desert") ? "cactus2"
                : candidate.family.equals("savanna") ? "tree_savanna1" : "tree2";
            if (symbol == 'f') ground = grassGround(candidate.cells[index], candidate.family);
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
            boolean rock = symbol == 'o';
            if ((tree || rock) && protectedNeighbor(candidate, x, y)) { upper = ""; tree = false; rock = false; }
            boolean walkable = !tree && !rock && !upper.startsWith("ledges3");
            plan.cells[index] = new Cell(ground, upper, walkable, ramp, tree);
        }
        return plan;
    }

    private static String baseGround(Tile tile, String family) {
        switch (family) {
            case "snow": return "snow1";
            case "mountain": return "mountain3";
            case "desert": return "desert4";
            case "beach": return "sand1";
            case "wetland": return "green_woodedlake";
            case "deep_forest": return "green_deepforest";
            case "savanna": return "green_savanna1";
            case "graveyard": return labelledBiome(tile, family) ? "green12" : "grass_graveyard1";
            default: return "green1";
        }
    }

    private static String grassGround(Tile tile, String family) {
        if ((family.equals("desert") || family.equals("beach")) && !labelledBiome(tile, family)) return baseGround(tile, family);
        switch (family) {
            case "snow": return labelledBiome(tile, family) ? "grass4" : "snow3";
            case "mountain": return "mountain3";
            case "desert": return "grass_sand3";
            case "beach": return "grass_sand2";
            case "savanna": return "grass_savanna1";
            case "graveyard": return "grass_graveyard1";
            default: return "grass4";
        }
    }

    private static String pathGround(Tile tile, String family) {
        if (family.equals("snow")) return "snow1";
        // Some old natural cells encode their biome only in their ground name.
        // Preserve that identity instead of turning an unlabelled dune into the
        // plains habitat simply because a path replaces the sand tile.
        if (!labelledBiome(tile, family)) return baseGround(tile, family);
        return "path1";
    }

    private static boolean labelledBiome(Tile tile, String family) {
        String biome = tile.biome == null ? "" : tile.biome;
        String route = tile.routeBelongsTo == null ? "" : tile.routeBelongsTo.name;
        switch (family) {
            case "desert": return biome.equals("desert") || route.startsWith("desert") || route.startsWith("ruins") || route.equals("sand_pit1");
            case "beach": return biome.equals("beach") || route.contains("beach") || route.equals("ocean1") || route.equals("sea1");
            case "mountain": return biome.equals("mountain") || route.startsWith("mountain");
            case "snow": return biome.equals("snow") || biome.equals("tundra") || route.equals("snow1");
            case "graveyard": return biome.equals("graveyard") || route.equals("graveyard1");
            default: return true;
        }
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
            if (module.at(x, y, flipX, flipY) == 'R' && !labelledBiome(candidate.cells[y * candidate.size + x], candidate.family)) return false;
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
        int protectedCount, retainedBoundary; String family;
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
        public int preparedCells, oldCliffsRemoved, oldTreesReplanned, changedCells, rejectedUnchanged;
        public int rejectedNatural, rejectedBiome, rejectedContour, rejectedProtected, rejectedOverlap;
        public final List<Placement> placements = new ArrayList<>();
        public final Map<String, Integer> naturalByBiome = new TreeMap<>(), preparedByBiome = new TreeMap<>(),
            rebuiltByBiome = new TreeMap<>(), modulesByBiome = new TreeMap<>();
        @Override public String toString() {
            return "AUTHORED modules=" + modules + " terraces=" + terraces + " rebuilt=" + cellsRebuilt
                + " changed=" + changedCells + " rejectedUnchanged=" + rejectedUnchanged
                + " prepared=" + preparedCells + " oldCliffsRemoved=" + oldCliffsRemoved + " oldTreesReplanned=" + oldTreesReplanned
                + " ramps=" + ramps + " cliffs=" + cliffCells + " trees=" + treeTrunks + " protected=" + protectedCells
                + " small=" + smallModules + " rejectedBoundary=" + rejectedBoundaries + " rejectedConnectivity=" + rejectedConnectivity
                + " rejected[natural/biome/contour/protected/overlap]=" + rejectedNatural + "/" + rejectedBiome + "/" + rejectedContour + "/" + rejectedProtected + "/" + rejectedOverlap
                + " byBiome[natural/prepared/rebuilt/modules]=" + naturalByBiome + "/" + preparedByBiome + "/" + rebuiltByBiome + "/" + modulesByBiome;
        }
    }
}
