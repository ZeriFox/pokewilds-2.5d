# Rework verification ledger

Platform: Windows x64, 2026-10-07. Results apply only to the source and JAR hashes recorded with each run.

Initial native checkpoint JAR: `8d5c4b844abf0bcd9f1b0f17b1c293c4427cb3e32f37d8802ac308b932127b15`. Complete Java 17 compilation succeeded; Windows NVIDIA RTX 4060 / OpenGL 4.6.0 driver 596.36.

| Check | Result | Evidence |
|---|---|---|
| Sampling / geometry | PASS, 12,309 / 312 assertions | console, pure geometry |
| Scope / mutation protection | PASS, 16 rejected mutations | build/visual-scope.json, build/scope-negative/results.txt |
| Current source asset crops | PASS, 8 pixel/integrity/determinism tests | tools/test_stardew_assets.py |
| Landscape | PASS | build/landscape-smoke/*.png and logs |
| Desktop controls | PASS, versioning/focus/custom/input/typing/controller cases | build/desktop-controls/desktop-controls.log |
| PMD battle | PASS, 183 draw assertions, 18 captures | build/pmd-battle-visual/ |
| Full viewport effect / local particle bounds | PASS at 1280x720, 1920x1080, 1024x768, 1600x600 | build/presentation-rework/ |

These native results precede the final isolated-tree crop correction and are not release acceptance. The final packaged hash must repeat the suite. An initial 1080p decorated-window test failed because Windows clamped its framebuffer to 1866x1011; the undecorated rerun confirmed an actual 1920x1080 buffer. An old PMD test expected the transparent pose origin at ground level; the updated assertion checks a stable pose origin plus opaque idle-foot contact against the arena plane. No actor, effect or alpha validation was disabled.

Evidence will include native framebuffer captures from the packaged game classes; harness classes must not replace production classes on the classpath. Failed or unavailable tests stay explicit.

## Second native checkpoint (development, not release acceptance)

JAR `ceec3e37bba151b982f92001803f3ad7c75ca17daaca836587927720ae8321ca`, complete 219-source Java 17 build on the same Windows/GPU. This includes 218 atlas regions and actor-only battle scissor, but precedes the final bed-target and real-move-effect corrections.

| Check | Result | Evidence |
|---|---|---|
| Native asset loader | PASS, 8,759 checks; 218 regions; 24 animation frames | build/bw-assets-integration/bw-assets-integration.log |
| Source-evidenced Tile catalog | PASS, 340 constructed, zero construction errors/missing/unclassified/source warnings | build/asset-audit/asset-coverage-runtime.json |
| Battle viewport | PASS, 5 sizes, 6 biomes, 170 exit frames, 82 captures, 12,154,480 panorama margin pixels | build/battle-viewport/ |
| Synthetic global/local overlays | PASS at four exact framebuffer dimensions | build/presentation-rework/ |
| Habitat gates and full roster report | PASS, 1,005 species/forms, 32 authored route branches; unknown ecological metadata documented | build/biome-habitat/ and docs/HABITAT-COVERAGE.md |
| Night ghost diagnosis | PASS, original trigger/identity/reveal checks over day/night and 5 biomes | docs/GHOST-DIAGNOSTIC.md |
| Field lifecycle | PASS, 1,705 modern world frames, 108 effect frames, zero classic world submissions | build/field-scenes/; original cut/headbutt/plant/couch/bed/kiln/fishing actions |
| Real portable launcher development copy | PASS, original GIOCA.cmd → DesktopLauncher → 120 Game frames, matching bundled java.home, exit 0, no remaining owned processes | build/launcher-development/launcher-menu.png |

The field test found sleeping on a bed was drawn at the adjacent walkable cell; the subsequent bed-target/pillow correction requires the next native run. The vertical slice reached furnished interior, original collision, inventory and serializer round-trip; final repeated battle/effect/cache checks remain pending. The original move renderer bypassed SpriteProxy, so the synthetic overlay test was never treated as complete R15 coverage. New tests execute Surf/Tackle/Rain Dance/Thunder/Dig through their actual move action and metadata.

A build attempt compiled successfully but could not replace the JAR while another native test had it open. That attempt was rejected; the successful checkpoint above came from a complete rerun after the process ended.

## Final runtime diagnostic checkpoint

Runtime commit `17b4a14a`, JAR `f5a817d55651b6060585b2373c8e1b0771d628a6b0ef8c2de55b38d56e32978a`, all 220 Java sources compiled with Java 17 on Windows x64 / NVIDIA RTX 4060. These individual diagnostics precede the final clean-checkout acceptance receipt.

| Check | Result | Evidence |
|---|---|---|
| Actual move sequences | PASS, eight moves × three resolutions, 3,771 frames | `build/move-effects/`, `docs/BATTLE-EFFECTS.md` |
| Global/local effects | PASS, orientation and PNG geometry, zero local margin changes, original clocks, GL/HUD state and mid-move shutdown cleanup | Same move report; negative earlier-JAR regressions retained |
| Source-evidenced asset catalog | PASS, 340 identities × two runs, zero errors, missing materials or warnings | `build/asset-audit/`, `docs/ASSET-COVERAGE.md` |
| Complete vertical slice | PASS, 4,055 frames; ramp/waterfall, furnished house, collision, bag, serializer and three battle/flee cycles | `build/vertical-slice/`, `docs/VERTICAL-SLICE.md` |
| Resources after repeated cycles | PASS, stable 523 managed textures / 4 shaders / 3 PMD sheets / 2,191,360 PMD bytes | Vertical-slice metrics; this is a warmed scene count, not total GPU VRAM |
| Existing-save compatibility versus preserved main | PASS, 215,337 exterior + 9,025 interior tiles, 314 wild creatures, party and inventory identities; both JARs load/save/reload and render 120 frames | `build/save-compatibility/save-compatibility.json`; original four files unchanged |
| Source scope | PASS, 17 negative mutations rejected, including Battle rules outside the presentation action | Frozen scope manifest and `build/scope-negative/` |

The extended field lifecycle, fishing distributions, full ghost trigger matrix and custom trainer-alpha/cache checks also passed against diagnostic `54c4e03d…`; those runtime areas are unchanged in `f5a817d5…`. Their final-JAR acceptance is still required by `tools/verify_rework.py --stage all`. It binds all 21 native checks to one clean commit and the exact compiled JAR. The benchmark comparison and its controlled-fixture limitations are recorded in `docs/VERTICAL-SLICE.md`.

Actual framebuffer PNGs were inspected. Isolated effect fixtures deliberately paint the command region magenta as a mutation sentinel; product screenshots use the ordinary `Game.render` path in the vertical slice. The renderer, entities, input checks and alpha validators remain active.

### Additional interior regression found during independent review

The earlier vertical-slice visibility check accepted any 40 changed pixels and therefore passed a player with only its head visible. On the intact `f5a817d5…` JAR, the strengthened test fails: feet **0/1,301**, torso **1,309/2,406**, head **3,840/3,840**. A test-only counterfactual replacing just the front wall row with floor restores the full silhouette and establishes the wall cap as the cause. The production map and collision remain intact. Logs and the before/reference PNGs are retained under `build/interior-occlusion-before/`.

Acceptance now requires at least 70% of both feet and torso reference pixels to remain recognizable, using an explicit color tolerance for transparency. The final renderer correction must pass this check and retain original wall collision and save identities. The previous weak visibility PASS is not evidence that R13 was complete.

## Complete Windows acceptance — ce84efaa

All **21 native checks**, deterministic asset preparation, all eight pure checks and the complete 220-source build passed from clean commit `ce84efaa431cdaef0c9efb97993af9c38c1eb5b0`. Tested Windows JAR: `b5fd0df0b00f3b8025273958d3f9bc1cd14ba66787f3f25a9166811d47e6d245`. Receipts/logs are preserved in `build/rework-verification-ce84/` before subsequent acceptance runs.

The corrected interior preserves **1,301/1,301 feet pixels and 2,406/2,406 torso pixels** within the documented tolerance. Original wall collision and save identities pass. The genuine Surf capture now records authored frame 100, showing the wave and row displacement rather than its initial transparent frame.

Linux CI on this commit failed **before compilation**, during byte-deterministic asset preparation. Diagnostic commit `a44cf0f7` proved that `world-atlas.png` and `suicune_overw1.png` have identical dimensions and RGBA hashes on Linux and Windows, but different compressed bytes; their two provenance files consequently differ too. Pillow versions alone do not explain it: isolated Windows Pillow 11.1 and 11.3 produce identical outputs. Linux reports zlib 1.3, Windows zlib 1.3.1.zlib-ng. The PNG encoder correction must retain every pixel and restore exact cross-platform bytes before the final CI/package acceptance.

## Canonical assets and final-JAR verification — 4d3a1d02

The shared stored-DEFLATE PNG encoder preserves every RGBA pixel and generates identical Windows/Linux bytes. Windows clean commit `4d3a1d02446e72411ff61c325848a489036407fd` passes preparation, nine pure checks, all 220-source compilation and all 21 native checks. Final JAR SHA-256: `ad9a49459a306a379634f9fb37ce2766ce064ede612a50fd0a884bec4e8828ea`. Linux CI run `37675849895` independently produces this exact JAR; preparation/build and 20 native checks pass, while the R15 snapshot comparison correctly reports a driver-dependent discrepancy.

Two targeted diagnostic runs isolate the discrepancy to exact nearest-neighbor boundaries when the arena is repainted into an FBO on Mesa. All 19,938 differing margin pixels equal an exact vertical neighbor, while raw FBO versus final composite differs at zero of 230,400 pixels. `docs/BATTLE-EFFECTS.md` records the narrowly justified comparator and its negative controls. The corrected tracked R15 fixture passes on Windows at 1280×720, 1024×768 and 1600×600: 690,240 raw-FBO/composite pixels match exactly, inversion/general translation are rejected, and all original move/UV/local-margin/timing/state/resource checks remain active. This individual diagnostic does not replace the required complete clean-commit Windows and Linux acceptance before packaging.

The same final JAR passes the preexisting-save comparison with all four originals unchanged. Its three paired performance runs measure p95 3.9091 → 4.1095 ms (+5.13%), with stable texture/shader counts. Exact receipts and limits are in `docs/VERTICAL-SLICE.md`. No gameplay source or resource changed for the Mesa fixture correction.

## Delivered checkpoint — 9066b067 / v0.8.11-stardew.1

Clean commit `9066b0674e3343e12d00110b8863937be234c44c` passes **21/21 native suites on Windows**. Linux [CI 37678950172](https://github.com/ZeriFox/pokewilds-2.5d/actions/runs/37678950172) passes preparation, nine pure checks, complete build and **21/21 native suites**, producing the identical `ad9a4945…` JAR. Mesa verifies 690,240 FBO/composite pixels exactly; only 12 sampled baseline alternatives occur at exact texel boundaries at 1280×720, none at the other sizes. Negative inversion/translation controls reject the incorrect images.

ZIP `pokewilds-2.5d-windows-x64-v0.8.11-stardew.1.zip` SHA-256: `cb66c990f4acf060300c6883a8719246c8eadfb27f9caff08a9fee131aacac0d`. Actual Windows extraction/launcher verification passes all 529 payload files. `GIOCA.cmd` uses extracted Java for 120 menu frames, exits 0 and leaves zero owned processes. Separate packaged-JAR smoke passes. Real launcher image and receipt: `build/windows-package-verification/`; user copies accompany the ZIP in Downloads. Remote main and fresh annotated tag confirmed at `9066b067` after normal fast-forward publication.

Additional active-move resize passes against this exact JAR: Surf 348 and Tackle 57 frames, four in-flight resizes, exact authored local impact pixels after resize, clocks/targets/completion, global coverage, HUD and GL/FBO cleanup. This closes the earlier dynamic-move resize gap; `docs/BATTLE-EFFECTS.md` describes the tracked follow-up. Its runner passes on clean Windows commit `53696d3e1d47701cc64ce884ec5377ce3dc9a274`; full Linux [CI 37681037801](https://github.com/ZeriFox/pokewilds-2.5d/actions/runs/37681037801) passes all **22 native suites**, preparation and build. This commit was then confirmed integrated on remote main. It adds tests/documentation only and never alters the release JAR/ZIP. The preceding 21 Windows checks belong to clean release commit `9066b067`; the additive Windows check belongs to clean `53696d3e`, with every production source/resource hash and JAR byte unchanged.
