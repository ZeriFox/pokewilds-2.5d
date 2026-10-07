# PokeWilds presentation recovery — live checkpoint

Updated: 2026-10-07, Windows x64. This document records evidence, not planned results.

## Recovery base

- Worktree: `C:/Users/Utente/Documents/GitHub/pokewilds-2.5d`
- Active branch: `codex/presentation-recovery`
- Base: `eec20e70ef3183cb87c60272f9a536638dfad8ad` (`origin/feature/stardew-world-battle`).
- Fetched main: `7777904bd4c0d7d4ea682edac46967c2fee85df1`, verified ancestor of the base.
- Existing checkouts in the 2026-10-03 Codex task are clean and preserved. Main includes the local terrain/biome/UI checkpoint `1aefbe4d`.
- Alternate FRLG branch inspected; duplicate source art will not be merged wholesale.
- CI run `37655229267`, job `112908529601`: confirmed failure at the wall alpha validator before compilation. Recovery artifact `11497523880` exists but is not build evidence.
- No applicable AGENTS.md found in this worktree or its ancestors.

## Current work

M0 asset recovery complete: wall failure reproduced (three semitransparent sheet-shadow rows), opaque 16x45 face selected without weakening validation. Checkpoints `b40aba97`, `b0ecca37` and `73883be6` are pushed to `origin/codex/presentation-recovery`; `17b4a14a` contains the verified runtime implementation, pending the tooling checkpoint and complete acceptance below. Main is unchanged pending final verification. Java migration is integrated in tracked sources; its old entrypoint only verifies frozen hashes and never rewrites Java.

Current deterministic version-2 atlas: 218 regions, 56 source crops, explicit source-pixel anchors and all six animations including cooled lava. Full furniture/rocks, isolated tree and retained legacy chimney are pixel-tested. Three PMD-derived compatibility overworld sheets preserve the existing Entei/Raikou/Suicune silhouettes required by legacy constructors; modern PMD sources remain unchanged.

Complete Windows acceptance passed on commit `ce84efaa431cdaef0c9efb97993af9c38c1eb5b0`, JAR `b5fd0df0b00f3b8025273958d3f9bc1cd14ba66787f3f25a9166811d47e6d245`: all 21 native checks, eight pure checks, preparation and complete 220-source build. The interior correction passes with 1,301/1,301 feet and 2,406/2,406 torso pixels recognizable, preserving original collision/save data. Receipts are in `build/rework-verification-ce84/`. The branch now includes diagnostic commit `a44cf0f7`, with no further Java changes.

Cross-platform preparation correction: Linux rejected different compressed bytes for two PNGs despite identical dimensions and RGBA hashes. CI run `37675139666` records the diagnostic evidence. Both generators now use a shared canonical RGBA PNG encoder with fixed stored-DEFLATE blocks, documented checksums and no platform compressor. All four PNGs retain identical pixels; Windows Pillow 11.1/11.3 reproduce the same eight output files. Encoder (3), asset (9), PMD compatibility (1), sampling/geometry and preparation checks pass. Final complete acceptance and Linux CI must validate this asset-only checkpoint before packaging.

## Verification so far

- Git status/branches/ancestry and remote fetch: passed, both original checkouts clean.
- Required build-input archive exists at `../Codex/2026-10-03/https-github-com-sheerst-pokewilds-https/work/github-release-v2/pokewilds-2.5d-build-inputs.zip` (relative to Documents).
- Archive SHA-256 verified: `5692696180a62dc1c8822e2a2d2a324f60647535a821865d7fb556e109b3883f`.
- Complete Java 17 compile passed; native tested JAR `8d5c4b844abf0bcd9f1b0f17b1c293c4427cb3e32f37d8802ac308b932127b15` (intermediate, before the final isolated-tree asset correction).
- Sampling 12,309 checks; geometry 312 checks; scope protection and 16 negative mutations: passed.
- 9 asset pixel/determinism/input-hash regression tests plus one PMD compatibility test: passed on current assets.
- Native Windows / NVIDIA RTX 4060: landscape and desktop controls passed; PMD battle passed 183 actual draw assertions and 18 captures; full-viewport effects passed 1280x720, 1920x1080, 1024x768, 1600x600.
- The 1080p harness now requests an undecorated window and logs actual framebuffer dimensions; a decorated window was clamped by Windows and correctly failed its pixel probe.
- Extended field lifecycle passed on `54c4e03d…`: 2,137 modern frames, 108 field-effect frames, 691,297 suppressed classic submissions and zero submitted. Original BUILD/DIG consume exactly the expected materials; cancellation is unchanged. FLY takes off, moves and lands; CUT/HEADBUTT/plant, couch, bed, kiln and fishing execute original lifecycles. The test adapts only the reflective Game-subclass parameter type to match the actual launcher.
- Catalog: 340 source-evidenced Tile identities construct successfully on the current candidate, with zero missing materials, unclassified solid objects or source warnings. Generated-name fragments and custom combinations remain documented limits.
- Actor-only scissor passes BattleViewport at five sizes/six biomes, preserving full-screen effects. Atlas integration passes 8,759 checks with 218 regions and 24 animation frames.
- Current candidate R15: eight actual move sequences pass 1280×720, 1024×768 and 1600×600, totaling 3,771 frames; local effects leave margins unchanged, global effects cover them, original action clocks/GL state/HUD are preserved, and mid-move shutdown releases its FBO. Real screenshots inspected; the magenta band in the isolated move fixture is an intentional HUD sentinel, absent from the real end-to-end game.
- Full vertical slice passed on current `f5a817d5…`: 4,055 frames covering original mountain/ramp/waterfall, furnished house travel/collision, inventory, save/load and three battle/flee cycles. Warmed resource counts remain stable across three cycles: 523 textures, 4 shaders, 3 PMD sheets / 2,191,360 bytes. See `docs/VERTICAL-SLICE.md` and its benchmark comparison for measured performance and limits.
- Preexisting-save compatibility passed on current `f5a817d5…` against preserved main's verified JAR `928958d8…`: 215,337 exterior tiles, 9,025 interior tiles, 314 wild creatures; party/inventory/world identities retained and original four files unchanged. Both versions render 120 real frames and retain matching persistent category hashes through load/save/reload. Reports persist hashes/counts only; no private save or screenshot is committed.
- Real `GIOCA.cmd` development bundle passed 120 menu frames using its copied runtime, exit 0 and no surviving owned processes. Final ZIP assembly/extraction/launcher verification is still required.

## Open requirements / output

M0–M7 behavior passes the complete Windows suite, including the strengthened R13/M5 interior check. M8 cross-platform preparation/final regression and M9 delivery are still in progress. No new release ZIP exists yet. No outstanding rendering defect is being hidden by disabling a scene or validator. Detailed results are in `docs/REWORK-TESTS.md`, asset coverage in `docs/ASSET-COVERAGE.md`.

## Resume

Next command from the committed deterministic-PNG checkpoint: `python tools/verify_rework.py --stage all`; verify the same commit in CI. Source review remains frozen and all 17 negative mutation tests pass; only JohtoRenderer changed after runtime commit `17b4a14a`. Battle rules outside the one move-presentation action remain immutable. Then repeat the final-JAR save/performance diagnostics, assemble a fresh version with `python tools/package_windows.py --version v0.8.11-stardew.1` and verify the extracted package. Preserve this shared worktree and its ignored evidence.
