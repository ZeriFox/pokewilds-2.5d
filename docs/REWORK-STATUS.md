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

M0 asset recovery complete: wall failure reproduced (three semitransparent sheet-shadow rows), opaque 16x45 face selected without weakening validation. Corrected complete tree/palm, rock and furniture crops. Deterministic version-2 atlas has 213 regions, 52 source crops. Java migration has been applied in this worktree and is being integrated as tracked source; build no longer needs it.

Parallel audits: material contract/crop coverage; runtime/package and exact-JAR testing; scene continuity, input, habitat, elevation and ghost behavior.

## Verification so far

- Git status/branches/ancestry and remote fetch: passed, both original checkouts clean.
- Required build-input archive exists at `../Codex/2026-10-03/https-github-com-sheerst-pokewilds-https/work/github-release-v2/pokewilds-2.5d-build-inputs.zip` (relative to Documents).
- Archive SHA-256 verified: `5692696180a62dc1c8822e2a2d2a324f60647535a821865d7fb556e109b3883f`.
- Complete Java 17 compile passed; native tested JAR `8d5c4b844abf0bcd9f1b0f17b1c293c4427cb3e32f37d8802ac308b932127b15` (intermediate, before the final isolated-tree asset correction).
- Sampling 12,309 checks; geometry 312 checks; scope protection and 16 negative mutations: passed.
- 8 asset pixel/determinism/input-hash regression tests: passed on current assets.
- Native Windows / NVIDIA RTX 4060: landscape and desktop controls passed; PMD battle passed 183 actual draw assertions and 18 captures; full-viewport effects passed 1280x720, 1920x1080, 1024x768, 1600x600.
- The 1080p harness now requests an undecorated window and logs actual framebuffer dimensions; a decorated window was clamped by Windows and correctly failed its pixel probe.
- Field lifecycle/interior/catalog audits remain in progress. Current assets require a new JAR and all acceptance tests will be repeated on its exact hash.

## Open requirements / output

R01–R16 remain under verification. Milestones M0–M9 are not claimed complete. No new release ZIP exists yet. Detailed test results will be recorded in `docs/REWORK-TESTS.md`, asset coverage in `docs/ASSET-COVERAGE.md`.

## Resume

Next command from this worktree: `python build.py`, then `python tools/verify_rework.py --stage native` after the reviewed source checkpoint is committed. Pending Java, test and packaging edits in this shared worktree belong to this recovery and must be preserved.
