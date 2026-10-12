# Java step 4 - terrain readability and encounter polish

Base: ac526d14. Java/LibGDX and Python only.
Presentation scope: derived 64px lawn/path materials across 4x4 cells;
natural dry boundary feathering; visible tread/shadow pairs on actual
slopes (including implicit ramps); shared vertical prop/actor depth;
cached complete PMD Idle/Walk envelopes for stable scale and dry feet;
shared slate/teal UI palette and eased full-viewport encounter bands.

Original sheet PNGs, PMD images, player identities, controls, collision,
terrain-height solver, map generation, battle rules and saves are unchanged.
A foreground tree may legitimately obscure a Pokemon: the change is a
consistent depth convention, not removal of trees or collision bypass.
Classic trainers are not silently replaced or advertised as newly drawn HD art.
Additional authored detailed trainers are not included in this checkpoint.

The palette treatment is a derived asset transformation with recorded
provenance, not a screen overlay or replacement of the original sources.
Water, lava, cliffs, authored ramps, structures and puzzle boundaries do
not receive feathered edges. Existing terrain errors are not hidden by fog.
Entering battle keeps original action duration, dispatch and sounds.
Reduced Motion replaces moving entrance bands with a restrained static
card/fade; no strobing, global bloom or camera shake is introduced.

Tests: test_readability_assets.py (opaque periodic surfaces and source hashes),
world_readability_test.py (built-JAR GL scenes, seam eligibility, ramps,
PMD envelopes, depth convention, RNG/tile invariance and intro progression).
Historical mesh parity explicitly sets readability=off: it checks unchanged
mesh/geometry behavior, not equivalence of the new terrain/depth treatment.
New behavior is tested ON separately; all existing assertions are retained.
FieldSceneTest locates furniture with the new foot-depth style, then runs
the same submitted bed/pillow/seated actor geometry assertions as before.

Receipts, not this document, determine whether tests and packaging passed.
No manual full-world or multiplayer playthrough is implied by fixtures.
This checkpoint does not promise every saved-island stair or source-art defect
is corrected. Verify problematic saved positions using the user's save next.
