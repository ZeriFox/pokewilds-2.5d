# R01 — Night ghost diagnosis

The night spirit is an intentional encounter, not a missing Pokémon texture. The recovered implementation had two separate representations which must not be conflated: a scripted overworld action and a real Pokémon with a concealed identity in battle.

## Trigger and identity

- `CycleDayNight.step`: the ordinary night chase can start only on the overworld, at night, while the player can move, away from a campfire, in `deep_forest`, `graveyard`, or `wooded_lake`. A countdown inserts `SpawnGhost`; it is not a material resolver creating an entity.
- `SpawnGhost`: retains the original 80/40/50 frame phases, music and temporary movement restriction. It explicitly requests `ghost_spawn1.png` and `ghost_sheet1.png`, then inserts `DrawGhost`.
- `DrawGhost`: creates one actual Pokémon from the authored Litwick/Lampent/Chandelure/Mimikyu/Misdreavus/Sableye/Gastly/Haunter/Gengar pool and calls `Pokemon.spookify()`. The Pokémon object, species, level and interactions remain real; `isGhost=true` means its identity is deliberately concealed.
- `Pokemon.spookify`: replaces battle art with `Specie.spriteGhost` and the hidden-identity nickname. `revealGhost()` restores that same object's species art. Ordinary Gastly and other Ghost-type Pokémon do not automatically have `isGhost=true`.
- `DrawGhost.step`: daylight, capture/defeat, missing healthy party, campfire/torch/FLASH proximity lead to the original despawn path. Contact still starts the original battle; its collision and action timing have not been removed.

Other authored events are distinct: graveyard sleep can create the Gengar shadow event, and desert cacti can create a Cacturne chase. Those must not be reclassified as failed ghost texture lookup.

## Presentation and diagnostics

`JohtoRenderer.ghost` reads the current Spawn/Draw/Despawn action after its normal update and renders a luminous procedural spirit at the shared terrain height. It does not call `step`, consume simulation RNG, create a replacement Pokémon, or retain the spirit after its action disappears. Its diagnostic is once per action, using a weak-key identity map:

```
GhostDiagnostic identity=<SpawnGhost|DrawGhost|DespawnGhost>
 source=scripted-encounter isGhost=not-a-Pokemon
 asset=procedural-spirit fallback=false
 biome=<actual biome> time=<day|night>
 shader=world-atmosphere layer=translucent
```

`PmdPokemonSprites.frame(Pokemon,...)` intentionally returns null for concealed ghosts (and eggs); that is not an absent ordinary PMD asset. `PmdBattleSprites` handles `isGhost=true` explicitly, keeps the species concealed and logs once per Pokémon:

```
GhostPresentation entity=Pokemon species=<actual hidden species>
 isGhost=true requested=hidden-identity asset=procedural-spirit
 fallback=false time=<actual time> shader=ui-default layer=battle-actor
```

Unconcealed Pokémon continue to request their exact PMD species/form assets. They cannot become a ghost merely because an asset lookup fails. Missing ordinary assets remain a separate diagnostic.

## Verification and limits

The trigger/identity suite passed on Windows x64 / Java 17.0.20.1 / NVIDIA RTX 4060 OpenGL 4.6 against JAR SHA-256 `54c4e03de7045e1b1551953a91e1b3a08e17cee7fae82885da6d28e458253d9a`. Its fixed seed selected Chandelure as the concealed real species. This rebuilt JAR includes the removal of three numeric countdown prints; trigger behavior is unchanged. `pmd_battle_visual_test.py` also passed on that same JAR, including concealed Gastly and exact-species reveal with distinct real framebuffer images.

The subsequent JAR `d785a7ed929689a3f81754c74de935923e202a4ed1a7273d6dd0035f39616278` corrects move-effect UV orientation. It does not change the ghost trigger, authored identity or reveal logic described here. The diagnosis above retains its actual verified `54c4…` hash; this source review is not a claim that its native tests have been rerun against `d785…`. Final acceptance logs record verification of the final artifact separately.

- `python tools/ghost_diagnostic_test.py`: invokes the original `CycleDayNight.step` for a 5-biome × day/night matrix plus campfire and movement exclusions. It checks the real hidden species pool, intentional PMD concealment, normal Gastly art, and exact-object reveal. The helper links only the delivered JAR; it does not overlay production classes.
- `python tools/biome_scene_test.py`: existing real `Game.render` fixtures compare desert, volcano and graveyard day/night, assert no ghost draws without an action, drive Spawn/Draw/Despawn, check original lifetimes and require zero ghost draws after despawn. Genuine images are in `build/biome-scenes/08-ghost-spawning.png` through `11-ghost-gone.png` when this suite has run.
- The trigger test advances the genuine trigger directly with a fixed countdown; it is a deterministic native regression, not a claim that a human waited through a natural night. Rendering and lifecycle evidence comes from the separate scene test.
- Exact current JAR hashes and executed results belong to the generated test logs and `REWORK-TESTS.md`. This document records the diagnosis; a command listed here is not by itself a PASS.
