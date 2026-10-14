#!/usr/bin/env python3
"""Verify frozen modernization scope against the original 198 source files.

This phase intentionally changes presentation, procedural generation, roster, desktop bindings and explicitly requested local Creative mode.
It does not claim full gameplay equivalence. Neither normal verification nor
--inventory refreshes approved hashes. Approval requires explicit source review
and edits to the manifest and this verifier's frozen manifest digest.
"""
from __future__ import annotations
import argparse
import difflib
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = "baseline/modernization-scope.json"
CLASSIC_REPORT = "baseline/classic-build-report.json"
CLASSIC_REPORT_SHA256 = "ff9ad5b0313ecc39d252d450ccd8f04cd47c467f7ce695b28a0c99e0cfae239e"
CLASSIC_SOURCES = "baseline/johto-v1/classic-sources"
# Set only after coordinated final source review. No command auto-updates this.
FROZEN_MANIFEST_SHA256 = "3f82feb16ce1055e986fb3095e7db183277732b4f145b3852f08c6d0e8e0175e"
RELIEF_WATER_ARCHIVE = {'baseline/before-relief-water-2026-10-14/modernization-scope.json': '3e0b0f2ba7a8b2e74adf6553f7928932d382d72b41268240f8977acd0d3d795d', 'baseline/before-relief-water-2026-10-14/VERIFICA.txt': '3b64611ad48f151700c6b1a3a950ad0f426fcfc0d20496d7d3dad504b59769c9', 'baseline/before-relief-water-2026-10-14/verify_scope_test.py': '15be2509c967d9c78a5dae37ae84e7d5b59405ff84b5635eb2b184877d1ab3dd', 'baseline/before-relief-water-2026-10-14/verify_visual_scope.py': '0c7d9059283d765dea9074db703f0ad87532d2a5a3ade22778dbf0ac44f6de93'}
PREFIX = "src/com/pkmngen/game/"
MAP_FIX_ARCHIVE = {'baseline/before-map-fix-2026-10-14/modernization-scope.json': 'bacee0c943378321ad78d68a32f872047a68780d9772fa156c2c6607220aa41d', 'baseline/before-map-fix-2026-10-14/VERIFICA.txt': '7533322544f7c87b26c9a45067212941407af7f071fe22539600057f44160dfb', 'baseline/before-map-fix-2026-10-14/verify_scope_test.py': '0ccc76f41ad62f42b5d747e610712ab19914dd14c1757cc9812a8785c53425a7', 'baseline/before-map-fix-2026-10-14/verify_visual_scope.py': '92be571e2df25556a3141f8291b8abef02788e6aecf3e5177b969958aab03478'}

NATURAL_WORLD_ARCHIVE = {'baseline/before-natural-world-2026-10-14/modernization-scope.json': 'de53c924dc3dcfa4e27c6ae0f7a7999fec7906df1826cfc24e2c497c5063431d', 'baseline/before-natural-world-2026-10-14/VERIFICA.txt': '1a89c2a2a5ca1e1b2ce9b0b3aaa719056cb7dbc9c911838c83596ee250d20a89', 'baseline/before-natural-world-2026-10-14/verify_scope_test.py': 'd84e08cb77da5e6228df62a29d2241137d1f96245a5ae9133edf63d6c8e2a63d', 'baseline/before-natural-world-2026-10-14/verify_visual_scope.py': '69a4ff13404fa237771ba8f4942e5663b2a51ee4fadd41d630216d4d4a631e7c'}

RELIEF_ARCHIVE = {'baseline/before-relief-refinement/modernization-scope.json': '6449bde64b50c3a5cf5c3dd804b824af1c7b355a2e6caa5178adab1060c50dfe', 'baseline/before-relief-refinement/verify_visual_scope.py': '43d6398d747ec99e1ca5496b0d8811c1751e360891a06a28d36ecc0c65f55e0b', 'baseline/before-relief-refinement/verify_scope_test.py': '5a5b4e9b5c55d8c6991af9468a83fcc813abc6fe86dfc3ad1efb50d0eba1aed1', 'baseline/before-relief-refinement/VERIFICA.txt': '7b8dc3ab46ad3535a3d545aef90b42d7807afd86d5a4989b475d6abb6c8a2b49'}

PLAYTEST_ARCHIVE = {'baseline/before-playtest-2026-10-14/modernization-scope.json': '67a078821afeee2595a1a7274bc8ec775b431333eb100b320cd327729ba1cf3b',
 'baseline/before-playtest-2026-10-14/verify_visual_scope.py': '8a650dd5ce3894a638ee9bf33ee486569196e040b9a05c0704b915def459f584',
 'baseline/before-playtest-2026-10-14/verify_scope_test.py': '6b3c666a4b678dd77e699608f819cbe91e2d317db279b958e971b91424bfe2f6',
 'baseline/before-playtest-2026-10-14/VERIFICA.txt': 'c1f25b75d6c7ac0b35f37cc681632fac5f63959d535436baee69d7e803a6b2a7',
 'baseline/before-playtest-2026-10-14/DrawBattleMenuSafariZone.java': '01092eceba1e423ddf338b6e8703be7f8ebd8a0872a799b0da649f78cad7b8f2'}

CINEMATIC_CUSTOMIZATION_ARCHIVE = {
    "baseline/before-cinematic-customization/modernization-scope.json": "f2c1f39f4c9abfd42bcb92d1c1580c10ce8fd2bb6c4665e5077b002f7081d574",
    "baseline/before-cinematic-customization/VERIFICA.txt": "da6ac2c8e922fcc913bb9fc9302c6288c30bc8fb75f8cb11d537cc55964dd7f7",
    "baseline/before-cinematic-customization/verify_visual_scope.py": "ca1f20158801848b86ccc54554478e5655e1d9d7726d447fd16d8a8658bddbfa"
}
TRAINER_REDRAW_ARCHIVE = {
    "baseline/before-trainer-redraw/modernization-scope.json": "835e48420251cbeb252e3ef0f716969f06c4e5509a8a0f8b3cf73432e0df4a66",
    "baseline/before-trainer-redraw/verify_visual_scope.py": "c2b822bf4e69d622c4ecbf70299925580fbd2675b4f4a162194197dcd2310f43",
    "baseline/before-trainer-redraw/VERIFICA.txt": "f1a4395114625c89446bf77aec2571c1e907b939c7d8dc451185bd1cb296428f",
}
# Immutable local build accepted before stronger nature/battle presentation.
CINEMATIC_NATURE_ARCHIVE = {
    "baseline/before-cinematic-nature/modernization-scope.json": "6a4d1767b748c53ae6e7ec1c10470f8cf09091af2cc356175fbbbe4fa5ff1a67",
    "baseline/before-cinematic-nature/verify_visual_scope.py": "892de550361952c2f57846ad7f1a54589ebbbc305beb324d82a77627e18409f9",
    "baseline/before-cinematic-nature/VERIFICA.txt": "d03d78cdb7d03d31cbf30b13931313ec6466ecd30b82e81acfa13f5a911aef3a",
}
# Immutable local build accepted before the broader biome/nature pass.
LIVING_WORLD_ARCHIVE = {
    "baseline/before-living-world/modernization-scope.json": "b46f145b24d5ad774f5aebfdd4a8fa23b2166968b097bdfffabb00a7b703ed13",
    "baseline/before-living-world/verify_visual_scope.py": "2eff484468c477a000e4fc90d881f1c95d493bf2b4fb7f655e6f50ce44177735",
    "baseline/before-living-world/VERIFICA.txt": "6b0b42e2652eaf31d275a3340a7da4a7f9064b4605964bdd1c630de20f6cb183",
}
V2_ARCHIVE = {
    "baseline/modernization-v2/modernization-scope.json": "f978edd535ac4440d6ce01924bc98135105a718e3a57faf9838827cd3ebf2343",
    "baseline/modernization-v2/verify_visual_scope.py": "20aa2d3c1257379ecc5168b66693bc0f076c3c0bfdc09cab762a7e3c38ce727b",
    "baseline/modernization-v2/VERIFICA.txt": "ddcc0a7f24914d6d0d91fa28abadb9367f61de9d601dd2f1e038e6187e5b1a82",
}
V3_ARCHIVE = {
    "baseline/modernization-v3/modernization-scope.json": "4b9d503e27d00be1b822f8e39e8bc4f425610d8f631bad4cea2ef347d398c700",
    "baseline/modernization-v3/verify_visual_scope.py": "a0266056798619d31219a225e8e12e0b0daef4d0ec93a4739e7ffd0c69b0e58e",
    "baseline/modernization-v3/VERIFICA.txt": "e531e3ad3f94911cd37f5cf9a5330f5e7b132108751c3a7ac08e13ceddba3baf",
}
V4_ARCHIVE = {
    "baseline/modernization-v4/modernization-scope.json": "8171c83e110e6bdae3380832dc1c6eafaab24a10ead7a11cbcc8862f674b7de5",
    "baseline/modernization-v4/verify_visual_scope.py": "2b181884511149c8355ad62ddbf175d17b20d766ce9fc6551698d247ce030cab",
    "baseline/modernization-v4/VERIFICA.txt": "454c76557b823134030b26f055738ee07828b15e67e6520f6714725b2b709ab4",
    "baseline/modernization-v4/archive.json": "ec7a08ddded007938b9cfe9464a1b036d8456ebe8242ebc4d42751c7a102830d",
}
V5_ARCHIVE = {
    "baseline/modernization-v5/modernization-scope.json": "ed68a9912360aa7972864acf09358b02b8d126463622f044d44f1bfbff6e4003",
    "baseline/modernization-v5/verify_visual_scope.py": "8cc684671bacdd3f94b4f4373f6a6f1004de69d4e56e7bf67ef9f8dcd9433e49",
    "baseline/modernization-v5/VERIFICA.txt": "ec11e79cf8eb0177424156f966bb452e0e7e463565840ae93f48525020568f31",
    "baseline/modernization-v5/archive.json": "bc32b1a3a40124c457960c34dfc7a7b4126ab96594b67806e7a900098403f4ac",
}

# Immutable pre-step-2 scope; only reviewed presentation helpers are extended.
TERRAIN_ARCHIVE = {'baseline/before-structural-terrain/modernization-scope.json': '135470bb46ec9cd535c34abbdaafa624190c5c18acd1fe9a3a5778208d115b6a', 'baseline/before-structural-terrain/verify_visual_scope.py': '7bc8f4490433d8b553bec9ec33011256af3b913964a41c92397f9a97440e0f70'}

STEP3_ARCHIVE = {'baseline/java-interface-step3/modernization-scope.json': '0662c4a3fab0ec871e834ea3e4c185374c75a239c378d325b20d521e6975c3f9', 'baseline/java-interface-step3/verify_visual_scope.py': 'f62f378e9aa1962baf64487f3680b5af7c05f107931ca6b31722ec9fa45360e8'}

STEP2_ARCHIVE = {'baseline/java-material-step2/modernization-scope.json': '400091117298b225cfa21c5b7174f5da8b514999e8fd11be12d9b6953d66c146', 'baseline/java-material-step2/verify_visual_scope.py': '151d33992c895559f0b30d61c58372818aaf810b9f34d2e08697e0f5ab835e06'}

# Names and review scope are explicit; an edited manifest cannot expand them.
MODIFIED_SCOPES = {
    "ThrowRock.java": "Presentation-only CaptureBallVisual constructor/draw substitutions; after reversing those exact hooks the full source must equal classic, including capture outcomes, inventory/party changes, RNG, sound schedules and frame counts.",
    "ThrowBait.java": "Presentation-only CaptureBallVisual constructor/draw substitutions; after reversing those exact hooks the full source must equal classic, including capture outcomes, inventory/party changes, RNG, sound schedules and frame counts.",
    "ThrowHyperPokeball.java": "Presentation-only CaptureBallVisual constructor/draw substitutions; after reversing those exact hooks the full source must equal classic, including capture outcomes, inventory/party changes, RNG, sound schedules and frame counts.",
    "ThrowFastPokeball.java": "Presentation-only CaptureBallVisual constructor/draw substitutions; after reversing those exact hooks the full source must equal classic, including capture outcomes, inventory/party changes, RNG, sound schedules and frame counts.",
    "ThrowPokeball.java": "Presentation-only CaptureBallVisual constructor/draw substitutions; after reversing those exact hooks the full source must equal classic, including capture outcomes, inventory/party changes, RNG, sound schedules and frame counts.",
    "CatchPokemonWobblesThenCatch.java": "Presentation-only CaptureBallVisual constructor/draw substitutions; after reversing those exact hooks the full source must equal classic, including capture outcomes, inventory/party changes, RNG, sound schedules and frame counts.",
    "CatchPokemonWobbles3Times.java": "Presentation-only CaptureBallVisual constructor/draw substitutions; after reversing those exact hooks the full source must equal classic, including capture outcomes, inventory/party changes, RNG, sound schedules and frame counts.",
    "CatchPokemonWobbles2Times.java": "Presentation-only CaptureBallVisual constructor/draw substitutions; after reversing those exact hooks the full source must equal classic, including capture outcomes, inventory/party changes, RNG, sound schedules and frame counts.",
    "CatchPokemonWobbles1Time.java": "Presentation-only CaptureBallVisual constructor/draw substitutions; after reversing those exact hooks the full source must equal classic, including capture outcomes, inventory/party changes, RNG, sound schedules and frame counts.",
    "CatchPokemonWobbles0Times.java": "Presentation-only CaptureBallVisual constructor/draw substitutions; after reversing those exact hooks the full source must equal classic, including capture outcomes, inventory/party changes, RNG, sound schedules and frame counts.",
    "DrawSetupMenu.java": "One exact CreativeMode.newWorld(game, this) hook after new PkmnMap construction when creating a world, passing the attached local setup draft. Existing setup/load/network dispatch remains classic.",
    "PkmnMap.java": "Exact Creative sidecar save call after existing map/minimap saves and dedicated IOException reporting. Existing serialized map format, load path and all other map logic remain classic.",
    "Network.java": "Exact stored-inventory accessor substitution in PlayerData so virtual Creative supply never enters network or save payloads. Every other byte of network/schema logic remains classic.",
    "Player.java": "Explicit local Creative mode: virtual recipe/material inventory reads and non-consuming writes, with stored inventory accessor and sorting bridge. Exact reviewed methods only; all fields, combat/player behavior and serialized schema remain classic.",
    "Battle.java": "Presentation only inside LoadAndPlayAnimation and one exact CaptureBallVisual.select hook in the existing item-ball branch. All combat formulas and rules outside those narrow boundaries remain identical to classic sources.",
    "CycleDayNight.java": "Remove three unconditional per-frame countdown prints; encounter triggers, timing, lighting and day/night simulation unchanged.",
    "Tile.java": "Repair original ledge corner, colored ledge and pedestal asset construction; habitat-filter rod-restricted fishing and natural evolutions, preserving empty-pool no-nibble behavior; no serialization changes.",
    "desktop/DesktopLauncher.java": "Desktop input focus callbacks and opt-in packaged-launch verification with a hidden window and nonzero crash exit; normal launcher and save location retained.",
    "BattleFadeOut.java": "Presentation: replace fade drawing while retaining action timing and dispatch.",
    "BattleIntro.java": "Presentation: preserve the modern world image during the original battle intro.",
    "BattleIntroAnim1.java": "Presentation: replace intro frame and border drawing, retain frame progression.",
    "DrawAttacksMenu.java": "Presentation: modern move panel; retain cursor state progression and original input.",
    "DrawBattle.java": "Presentation: arena, actor drawing hooks, masks and night tint.",
    "DrawBattleMenuNormal.java": "Presentation: modern battle commands; retain selection and input logic.",
    "DrawBattleMenuSafariZone.java": "Existing modern safari presentation plus one exact safari-ball art identity hook before original capture calculation. Previous reviewed source otherwise remains identical.",
    "DrawControls.java": "Desktop controls presentation: show configured movement, confirm, back/run and menu bindings.",
    "DrawPokemonMenu.java": "Desktop UI layout: remove stale fixed-key guidance from the party menu while preserving menu selection and actions.",
    "DrawUseTossMenu.java": "Desktop UI layout: show configured action bindings without altering item-use/toss state transitions.",
    "DrawEnemyHealthGen2.java": "Presentation: modern enemy HP panel; retain health updates.",
    "DrawFriendlyHealthGen2.java": "Presentation: modern party HP/EXP panel; retain health updates.",
    "EggHatchAnim.java": "Presentation: modern event background and exact PMD actor/shiny appearance.",
    "EnemyFaint.java": "Presentation: PMD actor during original enemy faint action; retain removal and victory flow.",
    "EnterBuilding.java": "Presentation: replace travel fade drawing; retain interior selection, timers and actions.",
    "EscapeRope.java": "Presentation: replace travel fade drawing; retain travel destination and action timing.",
    "EvolutionAnim.java": "Presentation: modern event background and PMD pre/post-evolution actor, preserving shiny identity.",
    "FadeAnim.java": "Presentation: replace fade drawing; retain original timer and transition condition.",
    "FriendlyFaint.java": "Presentation: PMD actor during original friendly faint action; retain status/action flow.",
    "Game.java": "Integration: exclusive modern world/UI drawing without skipping action steps; resource lifecycle; desktop defaults and legacy preset migration; unified modern viewport sizing.",
    "GenIsland1.java": "Generation: modern terrain pass before coastification, habitat-aware wild selection, safe oasis anchoring and explicit bounded retries for mansion drafts lacking a valid endpoint.",
    "InputProcessor.java": "Authorized desktop input: WASD and mouse bindings, debounced press/held/release, text-entry isolation, preserving gamepad and mobile controls. Step 3 adds modern UI context routing, permanent menu arrow/Escape aliases and consume-until-release on edit/menu transitions; physical gamepad polling and world movement semantics retained.",
    "PlayMusic.java": "Expanded roster initialization: resolve exact imported Pokemon cry paths.",
    "PlaySound.java": "Expanded roster initialization: resolve exact imported Pokemon cry paths.",
    "PlayerStanding.java": "Wild encounter coherence: profile-based habitat/rarity selection and valid natural evolutions; retain encounter rates, levels, scripted branches and original time availability.",
    "Pokemon.java": "Expanded roster initialization: National Dex lookup, imported growth data and 87.5 percent female ratio; keep uncaught aquatic species in water while preserving owned-companion movement.",
    "PokemonFrame.java": "Presentation: modern reveal background and exact PMD actor/shiny appearance.",
    "RegigigasBattle.java": "Presentation: modern nested intro and arena drawing; retain battle action progression.",
    "Route.java": "Expanded roster generation: append verified additional species to biome spawn candidates.",
    "SpecialBattleMegaGengar.java": "Presentation: modern special intro, dedicated boss arena, PMD actor and exact Pokemon cry paths; preserve original battle action progression and timing.",
    "SpecialBattleMewtwo.java": "Presentation: modern special intro, boss arena, PMD actor, rocks and ripple; preserve original battle action progression, RNG and timing.",
    "SpecialMegaGengar1.java": "Boss resource compatibility: initialize the shipped Mega Gengar boss with a dedicated mgengar identity backed by existing Gengar data; preserve its explicit stats, attacks, display name and battle rules.",
    "Specie.java": "Species initialization: verified additional species and the shipped Mega Gengar boss alias before legacy initialization; support fresh-process boss reload without changing serialization.",
    "ThrowOutPokemon.java": "Presentation: exact PMD or original-palette fallback send-out actor and orbit-aware poof fragments, preserving original timings, sounds, positions and next actions.",
    "ThrowOutPokemonCrystal.java": "Presentation: projected PMD/fallback actor and orbit-aware poofs without double projection; original action schedule and sprite slices unchanged.",
    "TrainerTipsTile.java": "Desktop tutorial presentation: substitute current binding labels in original sign text; preserve sign identity and interaction mechanics.",
    "util/SpriteProxy.java": "Presentation: known projected actors first, then full-screen or orbit-anchored effects; gameplay Sprite transforms remain read-only.",
}
ADDED_SCOPES = {
    "ModernMapUi.java": "Read-only modern world-map presentation from the original DrawMiniMap texture/state on the UI canvas, aspect-preserving island overview, pan translation, player/camp/warp markers and clipped viewport with GL restoration. Borrows action-owned textures; no gameplay input, teleport rules, camera/player/terrain/minimap writes, saves or RNG.",
    "ModernMoveEffects.java": "Deterministic family-based particles for reviewed move animation names, projected through the existing rotating arena camera. Retains original frame counts and audio/gameplay actions, bounded shader/texture lifecycle, HUD clipping and reduced-motion behavior; no gameplay RNG or camera resets.",
    "MountedTrainerSprites.java": "Presentation-only seated Ride, Surf and Fly body composition, preserving resolved idle identity/head/wardrobe materials and explicit mounted mod priority. Bounded texture cache, read-only retained CPU pixels and deterministic pose sway; no player, move, collision, clock, save or RNG writes.",
    "TerrainReliefAssets.java": "Read-only shared v2 earth, rock and mineral cap plus original stair, lava and water material loader with nearest filtering, repeating world materials, dry-only face fallback and bounded dispose/reload. Required fluids fail clearly. No tile, height, collision, gameplay RNG or save writes.",
    "WorldReadability.java": "Read-only semantic helpers for jumpable ledges, exact CUT requirement tree tiers, interior material classification and action-aware companion presentation. Saved tiles, hitboxes, interactions and field-action clocks remain authoritative.",
    "UserSpriteAssets.java": "Read-only supplied atlas loader with 470 explicit item identities, distinct capture balls, status/party badges and 64 berry identities with growth/animation frames. Alpha-trimmed source regions, validated semantic mappings, bounded cache and disposal/reload; no item or capture mechanics.",
    "ShinyVisuals.java": "Visible four-point sparkle cue for shiny actors, shared battle/party rendering, reduced-motion static cue; hidden ghosts and eggs excluded. No invented palettes or gameplay state changes.",
    "CaptureBallVisual.java": "Supplied named ball sprite selection and camera-projected throw/wobble/poof rendering on unchanged legacy capture action clocks. No catch probability or result changes.",
    "CreativeModeUi.java": "Creative menu draft/search/level/shiny selectors and explicit local mode toggles routed through CreativeMode; bounded pointer rows and input focus isolation; no autonomous Pokemon or resource grants.",
    "CreativeMode.java": "Explicit local Creative resource/species grants and optional new-world setup draft; load remains sidecar-based, host/join excluded, inventory serialization uses owned items. Save-mode sidecar and grants preserve existing party limits and game/save schemas.",
    "GhostVisualAssets.java": "Shared four-frame transparent concealed spirit artwork with finite-clock selection and explicit texture disposal/reload. Does not inspect or reveal the hidden Pokemon species.",
    "StartupCinematic.java": "Skippable 7.2-second opening using authored coast/forest artwork and selected trainer/companion, bounded parallax, title fade and full viewport bars. Disabled for reduced motion; owns/disposes artwork; no map, RNG, save or action mutations.",
    "MenuMotion.java": "Bounded presentation-only selection and entrance easing, fixed layout and immediate reduced-motion state; no input or gameplay state.",
    "CharacterAppearance.java": "Validated local per-name/trainer cosmetic profiles with atomic file persistence and backup, independent generated outfit/backpack/shoes/headwear/hair variants and colors, face-preserving layered composition and run anticipation. No game-save schema, inventory, network or Pokemon changes.",
    "BattleScenery.java": "Rigid periodic cylindrical battle panorama and a rotating world floor; no mirrored scenery or time-driven panorama UV warps. Continuous full camera orbit, environment color/weather accents only, fixed HUD, reduced motion and GPU state retained.",
    "WorldNatureMotion.java": "Pure coordinate-derived rooted vegetation movement, shore distance and same-height live tidal adjacency masks. No simulation clocks, map mutation, gameplay RNG or saved state.",
    "AuthoredTerrain.java": "Generation-only reconstruction and deterministic 17/9/5-cell authored modules for ten natural biome families. Region-specific footprints, minimum useful pocket area, immutable water/tidal/maze boundaries, protected event and encounter access, biome/route preservation, dry connectivity and no save-load regeneration.",
    'DesktopInterfaceSettings.java': 'Explicit local UI/control preferences, draft validation, reserved menu keys and duplicate rejection, atomic save with backup; no game-save schema or legacy settings-file rewrite.',
    "DesktopMenuController.java": "Desktop menu/text/trainer/wardrobe state plus explicit Adventure/Creative row only for new local worlds, sharing count-aware presenter and pointer hitboxes. Original dispatch indexes, text-entry isolation, host/join/load flow and draft behavior retained.",
    'MenuTextSession.java': 'Explicit reversible setup text draft with caret/deletion, length/character constraints and IPv4 validation; selection alone never consumes WASD as text.',
    "ModernStartupUi.java": "Modern startup/options/trainer/wardrobe presentation with an accessible eight-row local-world setup including Adventure/Creative and explanatory hint. Uses controller hitboxes and preserves original launch/load actions.",
    "TrainerCatalog.java": "Nineteen-trainer appearance resolution, custom-sheet priority and bounded frame cache; retains owned CPU copies for deterministic mounted composition and disposes them with textures. Existing style/wardrobe/head identity unchanged.",
    "WorldMaterials.java": "Presentation-only semantic terrain/prop resolver with actual upper-layer indoor floors, solid interior walls, visible jump-ledges and distinct tree species for existing CUT requirement tiers. Natural dry transitions, regional trees and cave floor caps retained; no Tile mutations or saved-data changes.",
    "WorldAmbientOverlay.java": "Sparse deterministic world-space mist, embers, fireflies, dust, snow and water glints with count/opacity budgets, protected actor focus, depth testing and separate shader ownership. Respects ambient toggle and freezes environmental motion with reduced motion; no gameplay RNG, indoor/UI overlays or saved-state writes.",
    "WorldMeshBatch.java": "CPU vertex storage, identity-based texture grouping, triangle-aligned mesh upload/reuse and GPU ownership extracted from JohtoRenderer; no camera, scene state, shader uniforms, texture ownership, gameplay or saved data.",
    "ActorModelRenderer.java": "Existing optional 3D helper, disabled by the modern sprite launcher.",
    "BiomeProfiles.java": "Shared data-driven biome identity, materials, geometry rules, atmosphere, transitions and spawn habitats, derived from existing Tile/Route state.",
    "BwAssets.java": "Read-only terrain/prop/trainer region resolution, current wardrobe and custom art priority, plus complete dedicated mounted pose selection; shared owned textures and explicit custom mounted overrides. No gameplay state or saved terrain writes.",
    "DesktopControls.java": "Authorized desktop binding parser, exact legacy-preset migration, text-entry detection and truthful dynamic hints. Step 3 treats only an explicitly active setup edit as text focus; highlighting a row is navigation.",
    "StartupVerification.java": "Opt-in packaged-launch probe inside the delivered Game: observe 120 original menu frames, verify nonempty framebuffer, save capture and exit normally; disabled in ordinary play.",
    "ExpansionDex.java": "Imported roster initialization and habitat-aware expansion route registration, with sourced family spawnRoutes overriding incomplete later-generation habitat/type defaults. Preserves existing species data, growth/audio/schema, rarity and script-owned creatures.",
    "JohtoBattleRenderer.java": "Continuous whole-arena orbit across command and action phases, fixed top HUD, actor depth clearing and projected legacy effect anchors, identity-concealing encounter transition and full viewport coverage; reduced motion and exact original action timing retained.",
    "JohtoRenderer.java": "Exclusive perspective world presentation with irregular rock/soil relief and weathered shoulders, rounded eroded faces sealed to existing upper/lower surfaces, resolved biome floors and continuous geological UVs; perceptible two-current water texture advection with bounded refraction and reduced-motion freeze. Preserves all accepted mounted poses and world effects, derived elevation/collision, player/Pokemon positions, gameplay RNG/action clocks and layered interior visibility.",
    "ModernBatch.java": "Suppress legacy draw submissions while original menu actions advance.",
    "ModernInventoryUi.java": "Modern inventory/item/crafting/guide presentation with supplied identity-mapped icons, five pocket tabs, quantity-safe rows and details. Shows INF/Unlimited only for explicit Creative virtual supplies; original item-use, sorting and crafting actions remain authoritative.",
    "ModernPartyUi.java": "Modern party/storage/nickname UI with shared cards, supplied party-ball/status badges, full animated summary actors and shiny sparkle cues. Existing hitboxes, setup delegation, reduced-motion preferences and action semantics retained.",
    "ModernUi.java": "Central modern UI rendering/viewport and action bridges, including explicit UI-canvas map presentation after perspective world drawing. Original DrawMiniMap action retains opening/input/pan/teleport/close progression and texture ownership; no original gameplay or minimap data modified.",
    "ModernWorldGenerator.java": "Authorized deterministic procedural terrain generation, version 3, with biome-authored modules and the existing connected single-depth caldera. Preserves dungeon routes, deep-forest maze walls, graveyards, ruins and special pits during volcanic finishing; shoreline smoothing excludes reserved actors, stored encounters and dungeon water while retaining normal inland smoothing. No save schema change.",
    "MoveEffectPresentation.java": "Modern move raster presentation: legacy row/recolor metadata composites only the transparent target actor buffer; never distorts the battle panorama. Retains authored normal-move PNG projection, shrink stages, original timing, target registration, resize/FBO/GL restoration and fixed HUD.",
    "PmdBattleSprites.java": "Eight-direction projected PMD billboards, stable animation bounds, transparent-aware ground depth sorting across legacy action layers, orbit-aware trainer/send-out fragments, genuine original shiny-art fallback and sparkle overlay. No species leak or combat changes.",
    "PmdPokemonSprites.java": "Pinned PMD portrait/animation loading, timing/directions, bounded texture cache and complete animation bounds for uncropped actors.",
    "TrainerModel3D.java": "Existing optional experimental trainer model helper.",
    "VisualGeometry.java": "Pure presentation helpers for diagonal battle-facing poses, uniform sprite fitting, cliff footprint math and building-wall classification; no map or collision writes.",
    "VisualSampling.java": "Validated pixel-region sampling independent of the fixed simulation grid, including deterministic signed world coordinates and exact source-cell boundaries.",
    "WorldBatch.java": "Drop legacy world draw submissions while preserving original action step execution and state changes.",
    "WildSpawnRules.java": "New-wild habitat filtering/rarity, metadata-backed habitat and fishing pools, no out-of-pool species injection, preserved authored dungeons, oasis encounter anchoring and uncaught aquatic movement gating; no saved or owned Pokemon migration.",
    "WorldElevation.java": "Read-only geometry sidecar derived from saved ledges and ramps, with six-unit visible jump-ledge elevation. Deterministic plateau/boundary constraint resolution, continuous ramp heights and cache invalidation without serialized height fields or gameplay collision edits.",
}
PROTECTED_NAMES = (
    "Attack.java", "util/Save.java", "CheckMovesLearned.java", "CheckEndOfBattle.java",
)

CLASSIC_HOOK_REPLACEMENTS = {'Player.java': [('   Map<String, Integer> getItemsDict() {\n'
                  '      return CreativeMode.inventory(this, this.itemsDict);\n'
                  '   }\n'
                  '\n'
                  '   Map<String, Integer> getStoredItemsDict() { return this.itemsDict; }\n'
                  '\n'
                  '   boolean hasItem(String name) {\n'
                  '      return CreativeMode.infinite(this,name) || '
                  'this.itemsDict.containsKey(name);\n'
                  '   }\n'
                  '\n'
                  '   Integer getItemAmount(String name) {\n'
                  '      return CreativeMode.infinite(this,name) ? CreativeMode.SUPPLY : '
                  'this.itemsDict.get(name);\n'
                  '   }\n'
                  '\n'
                  '   void setItemAmount(String name, int amount) {\n'
                  '      if (CreativeMode.infinite(this,name)) return;\n'
                  '      this.itemsDict.put(name, amount);\n'
                  '   }\n'
                  '\n'
                  '   void removeItem(String name) {\n'
                  '      if (CreativeMode.infinite(this,name)) return;\n'
                  '      this.itemsDict.remove(name);\n'
                  '   }\n'
                  '\n'
                  '   void setPlayerItems(Map<String, Integer> itemsDict) {\n'
                  '      this.itemsDict = '
                  'CreativeMode.storedAfterSort(this,itemsDict,this.itemsDict);\n'
                  '   }\n'
                  '\n',
                  '   Map<String, Integer> getItemsDict() {\n'
                  '      return this.itemsDict;\n'
                  '   }\n'
                  '\n'
                  '   boolean hasItem(String name) {\n'
                  '      return this.itemsDict.containsKey(name);\n'
                  '   }\n'
                  '\n'
                  '   Integer getItemAmount(String name) {\n'
                  '      return this.itemsDict.get(name);\n'
                  '   }\n'
                  '\n'
                  '   void setItemAmount(String name, int amount) {\n'
                  '      this.itemsDict.put(name, amount);\n'
                  '   }\n'
                  '\n'
                  '   void removeItem(String name) {\n'
                  '      this.itemsDict.remove(name);\n'
                  '   }\n'
                  '\n'
                  '   void setPlayerItems(Map<String, Integer> itemsDict) {\n'
                  '      this.itemsDict = itemsDict;\n'
                  '   }\n'
                  '\n')],
 'Network.java': [('         this.itemsDict = player.getStoredItemsDict();',
                   '         this.itemsDict = player.getItemsDict();')],
 'DrawSetupMenu.java': [('                  game.map = new PkmnMap(mapName);\n'
                         '                  if (this.newLoadIndex == 0) '
                         'CreativeMode.newWorld(game, this);',
                         '                  game.map = new PkmnMap(mapName);')],
 'PkmnMap.java': [('         Save.saveMinimap(this, this.id + ".sav/map" + this.currMapId + '
                   '".png");\n'
                   '         CreativeMode.save(game);\n'
                   '      } catch (Save.SaveFailure e) {\n'
                   '         Game.saveErrorLogAndNotifyUser("An issue occurred while '
                   'saving:\\n\\n", e);\n'
                   '      } catch (IOException e) {\n'
                   '         Game.saveErrorLogAndNotifyUser("Could not save Creative mode '
                   'settings:\\n\\n", e);\n'
                   '      }\n'
                   '\n',
                   '         Save.saveMinimap(this, this.id + ".sav/map" + this.currMapId + '
                   '".png");\n'
                   '      } catch (Save.SaveFailure e) {\n'
                   '         Game.saveErrorLogAndNotifyUser("An issue occurred while '
                   'saving:\\n\\n", e);\n'
                   '      }\n'
                   '\n')]}
CAPTURE_PRESENTATION_SOURCES = ('CatchPokemonWobbles0Times.java', 'CatchPokemonWobbles1Time.java', 'CatchPokemonWobbles2Times.java', 'CatchPokemonWobbles3Times.java', 'CatchPokemonWobblesThenCatch.java', 'ThrowPokeball.java', 'ThrowFastPokeball.java', 'ThrowHyperPokeball.java', 'ThrowBait.java', 'ThrowRock.java')


def verify_narrow_hooks(root: Path) -> None:
    for name, replacements in CLASSIC_HOOK_REPLACEMENTS.items():
        source=(root / PREFIX / name).read_text(encoding="utf-8")
        for reviewed, original in replacements:
            if source.count(reviewed)!=1:
                raise RuntimeError("Protected Creative bridge changed or moved: "+name)
            source=source.replace(reviewed,original,1)
        if source!=(root / CLASSIC_SOURCES / PREFIX / name).read_text(encoding="utf-8"):
            raise RuntimeError("Protected original logic outside Creative bridges changed: "+name)
    for name in CAPTURE_PRESENTATION_SOURCES:
        source=(root / PREFIX / name).read_text(encoding="utf-8")
        source=source.replace("CaptureBallVisual.create(game, text,", "new Sprite(text,")
        source=source.replace("CaptureBallVisual.draw(game, this.sprite);", "this.sprite.draw(game.uiBatch);")
        if source!=(root / CLASSIC_SOURCES / PREFIX / name).read_text(encoding="utf-8"):
            raise RuntimeError("Protected capture timing/outcome logic changed: "+name)
    safari=(root / PREFIX / "DrawBattleMenuSafariZone.java").read_text(encoding="utf-8")
    reviewed='            CaptureBallVisual.select(game, "safari ball");\n            Action catchAction = this.calcIfCaught(game);'
    if safari.count(reviewed)!=1:
        raise RuntimeError("Protected safari capture identity hook changed")
    safari=safari.replace(reviewed,'            Action catchAction = this.calcIfCaught(game);',1)
    if safari!=(root / "baseline/before-playtest-2026-10-14/DrawBattleMenuSafariZone.java").read_text(encoding="utf-8"):
        raise RuntimeError("Protected safari rules changed outside capture presentation hook")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _scope(root: Path) -> tuple[dict, dict, dict, list[str], list[str], dict]:
    for archived, expected in RELIEF_WATER_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Immutable pre-relief-water checkpoint changed: " + archived)
    for archived, expected in MAP_FIX_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Immutable pre-map-fix checkpoint changed: " + archived)
    for archived, expected in NATURAL_WORLD_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Immutable pre-natural-world checkpoint changed: " + archived)
    for archived, expected in RELIEF_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Immutable pre-relief checkpoint changed: " + archived)
    for archived, expected in PLAYTEST_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Pre-playtest 2026-10-14 archive changed: " + archived)
    for archived, expected in CINEMATIC_CUSTOMIZATION_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Pre-cinematic customization archive changed: " + archived)
    for archived, expected in TRAINER_REDRAW_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Pre-trainer redraw archive changed: " + archived)
    for archived, expected in CINEMATIC_NATURE_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Pre-cinematic nature archive changed: " + archived)
    for archived, expected in LIVING_WORLD_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Immutable pre-living-world review archive changed: " + archived)
    for archived, expected in TERRAIN_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Immutable pre-terrain review archive changed: " + archived)
    for archived, expected in STEP3_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Immutable pre-step-3 review archive changed: " + archived)
    for archived, expected in STEP2_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Immutable pre-step-2 review archive changed: " + archived)
    for archived, expected in V2_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Immutable schema 2 archive changed: " + archived)
    for archived, expected in V3_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Immutable schema 3 archive changed: " + archived)
    for archived, expected in V4_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Immutable schema 4 archive changed: " + archived)
    for archived, expected in V5_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Immutable schema 5 archive changed: " + archived)
    if sha256(root / CLASSIC_REPORT) != CLASSIC_REPORT_SHA256:
        raise RuntimeError("Classic baseline report differs from its immutable digest")
    baseline = json.loads((root / CLASSIC_REPORT).read_text(encoding="utf-8"))
    original = baseline["source_files_sha256"]
    if baseline["source_count"] != 198 or len(original) != 198:
        raise RuntimeError("Expected the original 198-source baseline")
    current = {p.relative_to(root).as_posix(): sha256(p) for p in (root / "src").rglob("*.java")}
    if set(original) - set(current):
        raise RuntimeError("Original sources removed: " + str(sorted(set(original) - set(current))))
    protected = {}
    for name in PROTECTED_NAMES:
        path = PREFIX + name
        if current.get(path) != original.get(path) or path not in original:
            raise RuntimeError("Protected battle/rule/save source changed: " + path)
        protected[path] = current[path]
    for path, expected in original.items():
        snapshot = root / CLASSIC_SOURCES / path
        if not snapshot.is_file() or sha256(snapshot) != expected:
            raise RuntimeError("Archived original source differs from classic digest: " + path)
    verify_narrow_hooks(root)
    # R15 needs the real move renderer. Keep combat logic outside that one
    # presentation action immutable instead of allowing the whole Battle class.
    battle_path = PREFIX + "Battle.java"
    def battle_rules(path: Path) -> tuple[str, str]:
        source = path.read_text(encoding="utf-8")
        before, start, tail = source.partition("   static class LoadAndPlayAnimation extends Action {")
        _, end, after = tail.partition("   class Network {")
        if not start or not end:
            raise RuntimeError("Battle presentation boundary missing")
        capture='            if (itemName.contains("ball")) {\n               CaptureBallVisual.select(game, itemName);'
        if path == root / battle_path:
            if before.count(capture)!=1:
                raise RuntimeError("Protected combat capture identity hook changed")
            before=before.replace(capture,'            if (itemName.contains("ball")) {',1)
        return before, end + after
    if battle_rules(root / battle_path) != battle_rules(root / CLASSIC_SOURCES / battle_path):
        raise RuntimeError("Protected combat rules outside LoadAndPlayAnimation changed")
    changed = sorted(path for path, expected in original.items() if current[path] != expected)
    added = sorted(set(current) - set(original))
    expected_changed = {PREFIX + name for name in MODIFIED_SCOPES}
    expected_added = {PREFIX + name for name in ADDED_SCOPES}
    if set(changed) != expected_changed or set(added) != expected_added:
        raise RuntimeError("Unexpected modernization source scope: " + json.dumps({
            "unexpected_changes": sorted(set(changed) - expected_changed),
            "expected_but_unchanged": sorted(expected_changed - set(changed)),
            "unexpected_additions": sorted(set(added) - expected_added),
            "missing_additions": sorted(expected_added - set(added)),
        }))
    manifest = json.loads((root / MANIFEST).read_text(encoding="utf-8"))
    if manifest.get("schema") != 11 or manifest.get("classic_report_sha256") != CLASSIC_REPORT_SHA256:
        raise RuntimeError("Expected schema 11 manifest bound to classic source baseline")
    if set(manifest.get("modified_original_sources", {})) != expected_changed:
        raise RuntimeError("Manifest changes differ from explicit source allowlist")
    if set(manifest.get("added_sources", {})) != expected_added:
        raise RuntimeError("Manifest additions differ from explicit source allowlist")
    if manifest.get("protected_original_sources") != sorted(protected):
        raise RuntimeError("Manifest protected-source set differs from verifier")
    for path, entry in manifest["modified_original_sources"].items():
        if entry.get("baseline_sha256") != original[path] or entry.get("scope") != MODIFIED_SCOPES[path[len(PREFIX):]]:
            raise RuntimeError("Manifest original baseline/scope mismatch: " + path)
    for path, entry in manifest["added_sources"].items():
        if entry.get("scope") != ADDED_SCOPES[path[len(PREFIX):]]:
            raise RuntimeError("Manifest added-source scope mismatch: " + path)
    return original, current, manifest, changed, added, protected


def audit(root: Path = ROOT, inventory: bool = False) -> tuple[dict, str]:
    original, current, manifest, changed, added, protected = _scope(root)
    if not inventory:
        if FROZEN_MANIFEST_SHA256 is None or manifest.get("status") != "frozen":
            raise RuntimeError("Modernization review is not frozen. --inventory is read-only; no approved hashes are updated.")
        if sha256(root / MANIFEST) != FROZEN_MANIFEST_SHA256:
            raise RuntimeError("Frozen manifest was edited; source hashes cannot be silently re-approved")
        for group in ("modified_original_sources", "added_sources"):
            for path, entry in manifest[group].items():
                if current[path] != entry.get("reviewed_sha256"):
                    raise RuntimeError("Source changed after explicit review freeze: " + path)
    patch = []
    for path in changed + added:
        old = (root / CLASSIC_SOURCES / path).read_text(encoding="utf-8") if path in original else ""
        new = (root / path).read_text(encoding="utf-8")
        patch.extend(difflib.unified_diff(old.splitlines(True), new.splitlines(True),
            fromfile=(CLASSIC_SOURCES + "/" + path) if path in original else "/dev/null", tofile=path))
    combined_patch = "".join(patch)
    diff_digest = hashlib.sha256(combined_patch.encode("utf-8")).hexdigest()
    if not inventory and diff_digest != manifest.get("reviewed_patch_sha256"):
        raise RuntimeError("Complete source diff differs from reviewed patch digest")
    result = {
        "status": "inventory_not_approved" if inventory else "success",
        "manifest_schema": 11, "baseline_source_count": len(original), "current_source_count": len(current),
        "unchanged_original_sources": len(original) - len(changed),
        "modified_original_sources": changed, "added_sources": added,
        "modified_source_scopes": {PREFIX + key: value for key, value in MODIFIED_SCOPES.items()},
        "added_source_scopes": {PREFIX + key: value for key, value in ADDED_SCOPES.items()},
        "protected_rule_save_sources_sha256": protected,
        "current_source_files_sha256": current, "complete_patch_sha256": diff_digest,
        "frozen_manifest_sha256": FROZEN_MANIFEST_SHA256,
        "scope": "Authorized update includes exclusive modern presentation, coherent biome profiles, derived terrain geometry, habitat-aware wild encounters/movement, desktop bindings, procedural generation, additional Pokemon data and opt-in local Creative mode. All other original sources remain byte-identical. Success binds every changed and added source to a reviewed manifest and complete diff.",
        "limits": "Source-scope verification is NOT proof of total gameplay equivalence. Generator, wild habitats, uncaught aquatic movement, roster and desktop input intentionally change. Combat formulas and serialized formats remain protected. Exact Creative inventory/persistence bridges and capture presentation hooks are explicitly normalized against classic or the prior frozen checkpoint. Creative mode intentionally changes local resource availability and roster grants; native tests remain required.",
    }
    return result, combined_patch


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inventory", action="store_true", help="Read scope/diff without approving or freezing source hashes")
    args = parser.parse_args()
    report, patch = audit(inventory=args.inventory)
    stem = "modernization-scope-inventory" if args.inventory else "visual-scope"
    destination = ROOT / "build" / (stem + ".json")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
    patch_path = ROOT / "build" / ("modernization-review.patch" if args.inventory else "visual-hooks.patch")
    patch_path.write_text(patch, encoding="utf-8", newline="\n")
    label = "INVENTORY ONLY (not approved)" if args.inventory else "PASS"
    print(f"{label}: {report['unchanged_original_sources']} originals unchanged; "
          f"{len(report['modified_original_sources'])} explicitly scoped originals; {len(report['added_sources'])} added sources.")
    print("Scope includes intentional generator/roster/input/Creative changes; this is not total gameplay equivalence.")
    print(f"Report: {destination}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, KeyError, RuntimeError) as error:
        print("SOURCE SCOPE FAIL: " + str(error), file=sys.stderr)
        sys.exit(1)
