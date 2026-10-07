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

M0 asset recovery complete: wall failure reproduced (three semitransparent sheet-shadow rows), opaque 16x45 face selected without weakening validation. Checkpoints `b40aba97` and `b0ecca37` are pushed to `origin/codex/presentation-recovery`; main is unchanged pending final verification. Java migration is integrated in tracked sources; its old entrypoint only verifies frozen hashes and never rewrites Java.

Current deterministic version-2 atlas: 218 regions, 56 source crops, explicit source-pixel anchors and all six animations including cooled lava. Full furniture/rocks, isolated tree and retained legacy chimney are pixel-tested. Three PMD-derived compatibility overworld sheets preserve the existing Entei/Raikou/Suicune silhouettes required by legacy constructors; modern PMD sources remain unchanged.

Parallel audits: material contract/crop coverage; runtime/package and exact-JAR testing; scene continuity, input, habitat, elevation and ghost behavior.

## Verification so far

- Git status/branches/ancestry and remote fetch: passed, both original checkouts clean.
- Required build-input archive exists at `../Codex/2026-10-03/https-github-com-sheerst-pokewilds-https/work/github-release-v2/pokewilds-2.5d-build-inputs.zip` (relative to Documents).
- Archive SHA-256 verified: `5692696180a62dc1c8822e2a2d2a324f60647535a821865d7fb556e109b3883f`.
- Complete Java 17 compile passed; native tested JAR `8d5c4b844abf0bcd9f1b0f17b1c293c4427cb3e32f37d8802ac308b932127b15` (intermediate, before the final isolated-tree asset correction).
- Sampling 12,309 checks; geometry 312 checks; scope protection and 16 negative mutations: passed.
- 9 asset pixel/determinism/input-hash regression tests plus one PMD compatibility test: passed on current assets.
- Native Windows / NVIDIA RTX 4060: landscape and desktop controls passed; PMD battle passed 183 actual draw assertions and 18 captures; full-viewport effects passed 1280x720, 1920x1080, 1024x768, 1600x600.
- The 1080p harness now requests an undecorated window and logs actual framebuffer dimensions; a decorated window was clamped by Windows and correctly failed its pixel probe.
- Field lifecycle: 690 modern frames, 108 field-effect frames, 216,477 classic submissions suppressed and zero submitted. Original mouse-driven CUT/HEADBUTT/planting complete once with original timing; RMB cancellation and Surf/Ride movement pass. Earlier fishing/sleep/sit/craft captures are pose coverage only.
- A catalog audit found three original Tile constructor errors and a chimney mapping gap; fixes are present but await the new JAR. BattleViewport caught staged trainers in panorama margins; actor-only scissor fix is pending verification, leaving global effects full viewport.
- Real move data audit found that Battle.LoadAndPlayAnimation bypasses SpriteProxy for full-size PNG frames and screenshot/row-copy effects. Actual-move regression and presentation integration are in progress; synthetic screen-effect PASS is not sufficient for R15.
- Expanded habitat/fishing gates, full interior/elevation/save-load vertical slice, and true packaged launcher verification are in progress. Current source changes require a new build. The last build failed on a removed no-op helper reference; the remaining calls are now removed, not yet recompiled.

## Open requirements / output

R01–R16 remain under verification. Milestones M0–M9 are not claimed complete. No new release ZIP exists yet. Detailed test results will be recorded in `docs/REWORK-TESTS.md`, asset coverage in `docs/ASSET-COVERAGE.md`.

## Resume

Next command after the assigned Java edits stabilize: `python tools/verify_visual_scope.py --inventory` (review the explicit new presentation/input/habitat scope and freeze only reviewed hashes), then `python build.py`. Run individual diagnostic suites before committing; run `python tools/verify_rework.py --stage native` only from the clean final checkpoint. Pending Java, test and packaging edits in this shared worktree belong to this recovery and must be preserved.
