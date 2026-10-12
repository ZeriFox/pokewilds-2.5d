# Authored procedural terrain

New islands now run `AuthoredTerrain` before the caldera/coast finishing passes
and before ordinary wild Pokémon placement. This changes the actual generated
`Tile` data: ground, paths, tree positions, cliff contours and walkable ramps.
The renderer consumes those same tiles. It does not overlay a second map.

Existing saves are not regenerated or migrated. Load/save continues to use the
ordinary `Network.TileData` format and existing tile names. Consequently, load an
existing island to check rendering compatibility, but generate a **new island**
to inspect the newly authored terrain structure.

## Modules and assembly

`src/com/pkmngen/game/AuthoredTerrain.java` contains three editable 17×17 ASCII
modules: `meadow-crossing`, `woodland-bend` and `double-stair-terrace`, plus three
9×9 versions for narrower inland areas. Rows are
written north to south. A deterministic map fingerprint selects the lattice
phase, module and horizontal/vertical reflection. No encounter RNG is consumed.
Every module has matching three-cell path ports and an open outer border. Small
modules never overlap accepted large modules.

| Symbol | Ground and behavior |
| --- | --- |
| `.` | Open biome ground |
| `p` | Walkable path |
| `t` | Tree with ordinary tree collision and field-move interaction |
| `f` | Small tall-grass habitat patch |
| `o` | Smashable rock, available for additional modules |
| `h` | Raised biome ground |
| `H` | Raised path |
| `R` | Walkable ramp through the terrace contour |

Cliff direction is derived from adjacent raised symbols. The terrace has two
opposed, three-cell stair openings and clear landings, rather than a visual stair
drawn over an unrelated blocking tile. The resulting `ledges3_*` and
`ledge_grass_ramp` tiles give `WorldElevation` explicit, consistent height cues.
Trees use spaced trunk positions and grass lives in grouped patches; the former
per-tile vegetation pattern is replaced inside accepted modules.

Before module assembly, a flood fill reconstructs the safe natural interior. It
starts at previously walkable cells, opens reachable old vegetation and cliff
cells, and builds a consistent ground substrate. This opening pass cannot sever
an existing dry connection or introduce an isolated dry island. It removes the
old contour layout as a constraint on the new map rather than merely changing
its cliff artwork. Its cells and removed contours are reported separately from
the cells subsequently placed by full modules, so those counts are not additive.

The first module set targets dry temperate land, mountain, snowy and wetland
interiors. Coastlines, water, puddles, lava, biome boundaries, dungeon structures,
the deep-forest maze and special desert/graveyard areas keep their existing
generation. The opening pass preserves a one-cell halo around unsafe terrain.
A full natural footprint plus one-cell surrounding halo is required for a module.
Any ledges remaining along the outer two cells prevent placement, so a preserved
contour is never cut by a room. This is deliberately not a claim that every cell
of every biome is rebuilt by the first six modules.

## Protected content and connectivity

Items, doors, scripted stored encounters, early placed Pokémon, special upper
objects and their one-cell approach halo stay unchanged. Ordinary unnamed
headbutt encounters retain their exact original tree and route; new neighboring
trees are suppressed to keep their approach open. A module with extensive protected
content is skipped. A terrace is used only when its raised footprint and one-cell
landing halo are free of protected cells; a retained low corner can coexist with
the hill. Tile
instances, per-cell route objects and biome identifiers are retained.

Before any mutation, a flood fill validates the proposed module. Every proposed
dry cell must reach the same outer border, and every previously walkable border
cell must remain walkable. This preserves outside connections without assuming
that routes stop at module boundaries. A failed plan changes no tiles.

## Verification

Run `python tools/authored_terrain_test.py` after building, or add `--sources`
when developing the generator against the previous JAR. The native test uses
real `Tile.init`, `WorldElevation` and `Network.TileData` with an invisible GL
context; it does not read personal save files.

The fixtures cover 12 seeds, reversed map insertion order, a dense 129×129 forest,
mountains containing legacy ledges, protected items/actor positions, preserved
route/biome identity, rejected special biomes/water, unchanged footprint,
non-increasing dry components and all six actual sloped ramp cells per terrace.
Every rebuilt fixture tile is round-tripped through the existing save schema.
The forest coverage requirement is over 50%; actual production coverage depends
on island shape and protected terrain and is printed in `WORLDGEN v2 ... AUTHORED`.

`python tools/modern_world_test.py` additionally exercises the original real
island generator, field movement and save/load in `build/modern-world`. Its
report includes accepted modules, terraces, rebuilt cells, true ramps, trees,
protected cells, and rejected boundary/connectivity plans.

## Caldera structure

The caldera removes legacy contours from its reconstructed volcanic footprint.
It plans a complete basin before mutation: one closed inward-facing contour,
one 14-unit depth, two three-cell stair entrances with clear landings, two
continuous lava pools, a central dry causeway and an inner walking ring. It
does not convert intersections with old ledges into additional ramps.

The footprint avoids structures, items, early Pokémon and scripted encounters.
Ordinary volcanic encounter rocks can remain unchanged on flat dry cells,
outside walls, lava, stairs and landings. A flood fill validates all final dry
cells and external connections before accepting this retained-rock variant.
When no complete footprint fits, the safe fallback retains a flat volcanic
surface and its existing lava footprint, with no partial crater or inferred
stair chain. The report's `basin=...,r0` explicitly identifies that fallback.

The topology fixtures and actual island smoke test check a basin's six real
ramps, unobstructed causeway and exactly 14 units between its rim and floor.
A separate fixture retains the original encounter route and Pokémon object on
a natural volcanic rock inside the accepted footprint.
