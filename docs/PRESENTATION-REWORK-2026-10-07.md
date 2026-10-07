# Presentation rework, 7 October 2026

Implemented from feature/stardew-world-battle, preserving the terrain/habitat/input work on main.

- Curated Stardew source crops for grass, desert, snow, cave, volcano, rocks, trees and house materials/furniture. Intentional transparency and the complete source canvas are preserved; rectangular sprites are not sliced into 16px pieces. The isolated tree component keeps its original origin. Every crop and recolor has provenance. Mechanical assets not replaced in this pass retain the previous credited landscape art.
- Reusable FireRed/LeafGreen menu frames for party/storage and bag. Party uses a lead slot plus five rows without changing the original menu state or selection rules. The supplied sheet is Pokemon Menu, not the FRLG bag screen.
- Full structural walls and caps, neighbour-aware exposed sides, proper full furniture sprites, intact door collision semantics.
- Wide battle overlays survive the final composition pass. Real move actions classify Surf/weather as screen effects and retain local target coordinates for Tackle, Water Gun, Teleport and Double Team. Full-viewport FBOs and GPU row operations keep the HUD separate. Dig uses the current PMD actor, with original timing and foot anchor.
- Diagonal PMD poses retained; stable contact offset, contact shadows and a slightly smaller distant opponent. Arena ground uses stronger perspective. This is layered 2.5D, not a new 3D combat simulator.
- WASD/mouse migration and focus handling preserve custom bindings; original cut, headbutt, planting, construction, digging, flight, fishing, couch, bed and crafting actions retain their state changes and modern presentation. Habitat gates cover ordinary land/water/fishing paths while preserving authored events and unknown metadata defaults.

No new source PNGs are claimed as original or freely licensed. Read the exact source/rights records before redistribution. All existing credits are retained.

Verification commands and outcomes belong to the matching CI run and release BUILD-INFO. This document alone is not evidence that a test passed. Remaining work includes a full manual campaign, all special move effects and exhaustive map-generation visual review.
