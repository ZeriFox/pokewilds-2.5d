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

`src/com/pkmngen/game/AuthoredTerrain.java` contains 33 editable ASCII modules
in sizes 17×17, 9×9 and 5×5. Rows are written north to south. A deterministic map fingerprint selects the lattice
phase, module and horizontal/vertical reflection. No encounter RNG is consumed.
All modules have an open outer border; bends, courts and washes have their own
path layout rather than all using the same four-way intersection. Smaller modules
never overlap accepted larger modules.

| Natural family | Pieces |
| --- | --- |
| Temperate | Meadow crossing, woodland bend, broad terrace; small rooms and pocket groves |
| Desert | Bending dry wash, cactus crescent, narrow mesa with two stairs, pocket wash |
| Beach | Long dune walk, rock cove, pocket dune; strictly on existing dry land |
| Wetland | Reed ring and willow bank, smaller bank sections; water stays intact |
| Mountain | Rock pass and broad ridge shelf with clear stairs, pocket passes |
| Snow | Pine shelter and narrow cirque terrace, pocket shelter |
| Oasis | Palm court and grass garden, small courts; existing pools retained |
| Savanna | Widely spaced acacia glade and grass ribbon, small grass sections |
| Deep forest | Small root rooms and turns on existing open ground; maze walls retained |
| Graveyard | Memorial courts and quiet paths in open ground; tombs/interactions retained |

| Symbol | Ground and behavior |
| --- | --- |
| `.` | Open biome ground |
| `p` | Walkable path |
| `t` | Tree with ordinary tree collision and field-move interaction |
| `f` | Small tall-grass habitat patch |
| `o` | Smashable rock |
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

The opening pass now targets all ten natural families. Coastal reconstruction
stays on dry land: coastlines, water, puddles, tidal zones and their one-cell halo
remain intact. Sliding ice, desert pits/Trapinch triggers, cracked floors, ruins,
dungeon structures and multi-cell blockers are excluded. Deep forest and graveyard
preparation may change only already walkable ground: it cannot cut through a maze
wall or remove a tomb. Large modules require a full natural footprint and a
one-cell surrounding halo.
Any ledges remaining along the outer two cells prevent placement, so a preserved
contour is never cut by a room. The volcanic finishing pass follows the same maze,
graveyard, dungeon and sand-pit exclusions instead of overwriting them afterwards.

Flat 5×5 pieces can follow a real water bank or maze corridor: water/tidal cells,
maze walls, multi-cell tree blockers and other-biome cells inside the footprint
stay immutable. No partial elevated platform or stair is allowed in these pieces.
If such a retained cell contains a protected event or reserved actor, the entire
pocket is rejected, including when that cell is in its surrounding halo. The later
shore smoothing pass also skips reserved water, dungeon routes and stored encounters;
it still smooths ordinary eligible inland water.
At least ten cells must be available for reconstruction and eight must actually
change; a token edit cannot count as a module. The same connectivity validation
rejects fragments containing disconnected dry islands. Existing blocked tree
footprints are never partially removed. Ground-only habitat identity also remains
intact when a legacy tile has no route or biome label.

This reconstructs eligible natural land; it does not replace every individual cell.
Narrow maze corridors, tiny islands, coast edges and dense protected content can
prevent a complete module. The report explicitly distinguishes natural candidate
cells, prepared ground and accepted module cells per family, so missing coverage is
visible rather than hidden by a global percentage. Prepared and module counts overlap.
`changed` counts effective ground/object changes within the accepted modules;
`rebuilt` counts their reconstructed dry cells, excluding every retained cell.

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
route/biome identity, rejected volcanic terrain/water, unchanged footprint,
non-increasing dry components and all six actual sloped ramp cells per terrace.
Nine additional biome fixtures each run four seeds with native tile construction,
tidal/water borders, dungeon cells, stored encounters, reserved actors, maze walls,
graves and desert triggers. They check accepted rooms, coverage, material/habitat
identity, unchanged protected cells, real slopes and save-format compatibility.
Additional regressions cover diagonal water, a three-cell-wide maze corridor,
separate tiny islands and terrain whose habitat is encoded only by its ground.
Every rebuilt fixture tile is round-tripped through the existing save schema.
The forest coverage requirement is over 50%; actual production coverage depends
on island shape and protected terrain and is printed in `WORLDGEN v3 ... AUTHORED`.

`python tools/modern_world_test.py` additionally exercises the original real
island generator, field movement and save/load in `build/modern-world`. Its
report includes accepted modules, terraces, rebuilt cells, true ramps, trees,
protected cells, and rejected boundary/connectivity plans.
It also captures `world-biome-<family>.png` from actual accepted modules after
the save/load checks, using only fixture camera movement without further saves.
Missing rooms are reported explicitly and do not produce a fabricated preview.

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
