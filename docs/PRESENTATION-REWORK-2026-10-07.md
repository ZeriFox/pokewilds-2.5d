# Presentation rework, 7 October 2026

Implemented from feature/stardew-world-battle, preserving the terrain/habitat/input work on main.

- Curated Stardew source crops for grass, desert, snow, cave, volcano, rocks, trees and house materials/furniture. Alpha is trimmed for props; rectangular sprites are not sliced into 16px pieces. Every crop and recolor has provenance. Mechanical assets not replaced in this pass retain the previous credited landscape art.
- Reusable FireRed/LeafGreen menu frames for party/storage and bag. Party uses a lead slot plus five rows without changing the original menu state or selection rules. The supplied sheet is Pokemon Menu, not the FRLG bag screen.
- Full structural walls and caps, neighbour-aware exposed sides, proper full furniture sprites, intact door collision semantics.
- Wide battle overlays survive the final composition pass. Authored large screen effects expand proportionally to cover the viewport; small targeted effects and Pokemon are not stretched. Their original actions/timings are preserved.
- Diagonal PMD poses retained; stable contact offset, contact shadows and a slightly smaller distant opponent. Arena ground uses stronger perspective. This is layered 2.5D, not a new 3D combat simulator.
- Existing WASD/mouse bindings, graveyard atmosphere, world elevation, field-action rendering and habitat filtering are preserved, not reimplemented.

No new source PNGs are claimed as original or freely licensed. Read the exact source/rights records before redistribution. All existing credits are retained.

Verification commands and outcomes belong to the matching CI run and release BUILD-INFO. This document alone is not evidence that a test passed. Remaining work includes a full manual campaign, all special move effects and exhaustive map-generation visual review.
