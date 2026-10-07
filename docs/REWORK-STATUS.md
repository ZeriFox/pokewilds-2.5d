# PokeWilds presentation recovery — live checkpoint

Updated: 2026-10-07, Windows x64. Completed evidence and remaining verification are separate.

## Recovery and integration

- Worktree: `C:/Users/Utente/Documents/GitHub/pokewilds-2.5d`; branch `codex/presentation-recovery`.
- Recovery base: `eec20e70ef3183cb87c60272f9a536638dfad8ad`, `feature/stardew-world-battle`.
- Original main: `7777904bd4c0d7d4ea682edac46967c2fee85df1`, verified ancestor and fetched again before integration. Original clean checkouts and alternate branches preserved; no reset/force-push. No applicable AGENTS.md found.
- Original CI `37655229267` failed at wall alpha. Three source rows were semitransparent sheet shadows; the corrected opaque 16×45 face preserves validation.
- Milestones: `b40aba97` wall/assets; `b0ecca37` tracked migration; `73883be6` atlas/PMD compatibility; `17b4a14a` moves/field/habitat; `ce84efaa` interior visibility and complete verification/package tooling; `a44cf0f7` cross-platform diagnosis; `4d3a1d02` canonical PNGs; `9066b067` exact FBO/nearest-boundary regression.
- **Remote main confirmed at `9066b0674e3343e12d00110b8863937be234c44c`**, normal fast-forward. New annotated tag **`v0.8.11-stardew.1`** resolves to that commit; no existing tag replaced.

Java migration is in tracked sources; its historical entrypoint verifies hashes and never rewrites Java. Java/LibGDX and original PMD sources remain. Original simulation/save scope is protected by 17 negative mutation tests.

## Delivered package

- ZIP: `build/release/pokewilds-2.5d-windows-x64-v0.8.11-stardew.1.zip`, **461,587,802 bytes**.
- User copy: `C:/Users/Utente/Downloads/PokeWilds-2.5D-v0.8.11-stardew.1/`, with ZIP, SHA256SUMS, BUILD-INFO, startup receipt and real screenshots. Copied ZIP hash verified.
- ZIP SHA-256: `cb66c990f4acf060300c6883a8719246c8eadfb27f9caff08a9fee131aacac0d`.
- JAR SHA-256: `ad9a49459a306a379634f9fb37ce2766ce064ede612a50fd0a884bec4e8828ea`.
- Source aggregate: `87dfcad0628f47faabc385ce6a199ff53b6c44f6ad31cc8cb40a80582f6b2b2d`; complete 220-source Java 17 build.
- Runtime: Windows x64 Temurin 17.0.20.1+1, native libraries/notices included. No personal saves/settings packaged.
- **Extracted ZIP startup PASS**: all 529 payload files verified; actual `GIOCA.cmd` rendered 120 menu frames using only extracted Java, exit 0, zero surviving owned processes. Separate packaged-JAR native smoke passed. Receipt: `build/windows-package-verification/windows-package-verification.json`.
- Automated real Windows OpenGL launch, not a manual campaign. `LEGGIMI.txt` explains extraction, controls and backup/copy of `run-johto`. Existing islands are not regenerated; generation changes apply to new areas/islands.

## Verified checkpoint and follow-up

Clean packaged commit `9066b067` passes **21/21 native suites on Windows**. Full Linux Xvfb/Mesa [CI 37678950172](https://github.com/ZeriFox/pokewilds-2.5d/actions/runs/37678950172) passes deterministic preparation, nine pure checks, complete compilation and **21/21 native suites**, producing the identical JAR. Windows receipt/logs: `build/rework-verification/`; Linux receipts/artifact: `build/ci-9066b067/`. Historical diagnostics are preserved separately and are not acceptance evidence.

Additional active-move resize passes on this exact JAR: same Surf action completes 348 frames through 1280×720 → 1024×768 → 1600×600, recreates/disposes its FBO and affects the expanded field. Same Tackle action completes 57 frames; 3,384 and 564 opaque authored PNG pixels match the enemy impact after the two resizes. Original clocks, target identity, next action, HUD and GL state pass. Scratch evidence: `build/resize-diagnostic/`. The follow-up tracks this as a 22nd native check; its Windows runner and full Linux CI are pending at this document checkpoint. **No production source, asset or packaged byte changes for this follow-up.**

## Requirement evidence and explicit limits

| Requirement | Evidence / boundary of claim |
| --- | --- |
| R01 | GhostDiagnostic: intentional night entity, trigger matrix, correct sprite/state; no removed entity or per-frame log spam. |
| R02 | UiLayout/ModernUiSmoke: 81 real menu cases, long names, varied proportions and live resize; OS DPI not tested. |
| R03 | DesktopControls/FieldScene: WASD, LMB, held RMB, Enter, custom preferences, typing, focus/release barrier, controller polling. Physical controller hardware not tested. |
| R04 | Pixel/crop tests, 12,309 sampling and 312 geometry checks; rectangular objects, anchors/alpha/padding. |
| R05 | BattleViewport/vertical slice: actual transitions with zero submitted legacy-world frames in tested paths. |
| R06 | PMD fixtures, five battle sizes/six biomes: anchors, clipping, poses and original transitions. |
| R07 | FieldScene: 2,137 modern frames, original selection/action/cancel/consumption and movement return, 108 effect frames. |
| R08–R09 | BiomeScene: volcano, graveyard fog/desaturation, day/night; real screenshots. |
| R10 | WorldElevation/Geometry/VerticalSlice: terrain, ramps, waterfall, contact and old-save compatibility. |
| R11 | 1,005 catalog identities/32 route branches audited; ordinary spawn paths including fishing gated, 66 explicit rules. 296 identities retain authored/default routes with unknown metadata; 578 have no ordinary habitat defined. Ecological coverage limits, not removed owned/event creatures. |
| R12 | 218 regions/six animations/14 profiles: 71 reference, 146 declared retained landscape, one special chimney. PMD unchanged. Full replacement of retained art and public redistribution rights are not claimed. |
| R13 | Furnished room/collision/save-load; stronger wall test recognizes all 1,301 feet and 2,406 torso reference pixels within documented transparency tolerance. |
| R14 | Actual inventory/party/stats layouts/interactions, empty/paged cases. FRLG party reference is not falsely called a complete bag sheet. |
| R15 | Eight actual moves, 3,771 frames at three sizes; global/local pixels, schedules, HUD/GL/FBO/dispose, exact FBO composition and negative inversion/shift controls. Additional in-flight Surf/Tackle resize passes above. |
| R16 | Fresh version/tag, real ZIP, Windows extracted launcher PASS, user copy with checked SHA-256. |

Vertical slice executes 4,055 frames: house travel, collision, bag, serialization and three battle/Surf/flee/return cycles. Warmed resources stay at 523 textures, four shaders, three PMD sheets / 2,191,360 PMD bytes.

Old-save comparison on final JAR versus preserved main retains 215,337 exterior tiles, 9,025 interior tiles, 314 wild creatures, party/inventory through both load/save/reload paths. Four original files remain byte-identical. Only aggregate evidence retained; no private saves committed.

Three paired runs measure p95 CPU render wall time **3.9091 → 4.1095 ms (+5.13%)**, mean 3.3639 → 3.5546 ms, about 4.438 MB allocation/frame (baseline 4.453 MB). Managed counts stable; RGBA8 texture estimate +122,880 bytes, not measured VRAM. No improved FPS, allocation-free rendering, manual campaign or multiplayer claim. Method/receipts: `docs/VERTICAL-SLICE.md`.

Wider redistribution rights remain unresolved for supplied Stardew/FRLG derivatives. The local implementation/review ZIP retains credits; no public GitHub binary release was automated. See `docs/ASSET-RIGHTS.md`, asset and habitat audits.

## Resume

Next command: `python tools/active_move_resize_test.py`, then inspect full 22-suite CI for the follow-up commit and fast-forward main only after PASS. Release tag/ZIP stay immutable at `9066b067`; subsequent test/documentation commits do not change production bytes. Repeat packaged startup with `python tools/verify_windows_package.py build/release/pokewilds-2.5d-windows-x64-v0.8.11-stardew.1.zip`. Preserve this worktree, original checkouts and ignored evidence.
