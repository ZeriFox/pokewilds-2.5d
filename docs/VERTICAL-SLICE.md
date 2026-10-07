# Native integration and performance evidence

`python tools/vertical_slice_test.py` compiles only its test class against the
complete delivered JAR and uses a temporary working directory. Every ordinary
frame executes `Game.render` under Java 17 / native LibGDX OpenGL at 1280×720.
It does not prepend replacement production classes or loose assets.

The fixed fixture contains a raised mountain terrace, connected waterfall and
traversable ramp, then a furnished house with walls, window, door and eight
furniture categories. It drives the original keyboard polling, building travel,
wall collision, inventory, serializer, battle intro, authored Surf animation,
Run command and return to the world. Three battle cycles check that warmed
managed textures, shader programs and PMD sheet/byte counts stop growing.

Additional assertions cover draw-only simulation/RNG invariants, visible player
pixels behind the foreground wall, structural collision, furniture not classified
as walls, and zero legacy world pixel submissions. The fixture's direct travel
action tests the original transition; it is not an automated building editor or
a claim to have tested every generated interior.

Evidence appears in `build/vertical-slice/`: numbered genuine framebuffer PNGs,
the execution log, per-frame timing/allocation CSV, metrics JSON and a receipt
bound to JAR/harness/artifact SHA-256 hashes. A failed receipt does not validate
screenshots left by an earlier run. The room deliberately isolates object
families and geometry; it is a test scene, not a complete generated settlement.

## Paired comparison

```
python tools/performance_baseline.py --baseline-jar <preserved-prior-build.jar>
```

This explicitly supplied baseline is read, never modified. The same
`RenderBenchmark.java` fixture is separately compiled against the old and new
complete JARs, with the same bundled JDK, 1280×720 viewport, fixed seeds and
3,721-tile mountain/forest/waterfall scene. Each process warms up for 120 frames
and measures 600 more. Three pairs alternate execution order. Close other GPU
tests before running it; the program cannot prove the entire computer is idle.

`build/performance-comparison/comparison.json` records both exact JAR hashes,
hardware/driver, per-run mean/median/p95/max CPU render wall time, thread
allocation and managed resource counts. Comparisons reject differing GPU, Java,
resolution or fixture identity. CPU wall time includes driver stalls and excludes
the application's frame limiter; it is not a GPU timer or a general FPS claim.
This workload complements, rather than measures, the longer interactive slice.

## Previous saves

```
python tools/save_compatibility_test.py --save-directory <existing.sav> --baseline-jar <preserved-prior-build.jar>
```

The tool hashes the explicit original directory, copies it into separate
temporary folders for the two JARs and passes only these copies to the JVMs.
Each runtime loads, saves and reloads through the original game serializer,
compares player/game, inventory/party, world/interiors and wild Pokémon state,
then executes 120 real rendering frames. Cross-version checks compare the loaded
persistent state. Computed rendering caches are not saved state.

The permanent receipt contains only aggregate hashes and counts, not save names,
player names, inventories, raw save data, screenshots or private load logs. The
temporary copies/logs are removed and the original directory hashes checked
again. This is an explicit local diagnostic and is not part of CI, which must not
depend on private saves or upload them.

## Recorded local verification — 2026-10-07

Candidate JAR SHA-256:
`f5a817d55651b6060585b2373c8e1b0771d628a6b0ef8c2de55b38d56e32978a`.
Baseline JAR:
`928958d81c26d5c296fd88207daee2bdcb0e33e89c2c26bbae09523fd66bc39c`,
from the preserved clean checkout at commit
`7777904bd4c0d7d4ea682edac46967c2fee85df1`. Its build report matches the JAR and
all 218 source hashes. This is the measured prior build, not an assertion that
the baseline is checkpoint `eec20e70`.

The vertical slice passed 4,055 real frames, two draw-only invariant checks and
three complete battle/Surf/flee/return cycles. Every warmed cycle retained 523
managed textures, four managed shader programs, three PMD sheets and 2,191,360
PMD texture bytes. The ten framebuffer captures, timing CSV and JSON receipt are
in `build/vertical-slice/`; the receipt lists each artifact hash.

A copied preexisting save also passed under both JARs: 215,337 exterior tiles,
9,025 interior tiles, 314 world Pokémon, a three-member party and two inventory
categories. Game/player/party/inventory, tiles/interiors and world Pokémon hashes
matched across versions and each save/load round trip. The four original files
remained byte-identical. Route identity strings are process-local object IDs;
the comparison uses the actual serialized route contents instead of those IDs.
Only hashes/counts are retained in `build/save-compatibility/`.

The paired benchmark ran sequentially after the other agent GPU tests ended:
NVIDIA GeForce RTX 4060, OpenGL 4.6.0 / driver 596.36, Java 17.0.20.1+1, 1280×720,
three runs per JAR with 120 warmup and 600 measured frames each.

| Metric | Prior build | Candidate |
| --- | ---: | ---: |
| Median of per-run p95 CPU render time | 3.9282 ms | 4.5195 ms |
| Median of per-run mean CPU render time | 3.3379 ms | 3.6940 ms |
| Mean render-thread allocation, approximately | 4.453 MB/frame | 4.438 MB/frame |
| Managed textures, each run | 66 | 66 |
| Managed shader programs, each run | 3 | 3 |
| Managed texture RGBA8 base-level estimate | 38,794,492 bytes | 38,917,372 bytes |

The measured p95 increased by 0.5913 ms (15.05%); this is not a performance
improvement claim. Candidate p95 varied from 4.3086 to 4.7681 ms, while baseline
p95 varied from 3.9180 to 3.9625 ms. The texture estimate increased by 122,880
bytes: it sums each managed texture's width × height × four bytes, excluding
mipmaps, unmanaged textures and driver overhead. It is not measured VRAM.
Thread allocation remains substantial in both builds; this test reports it
rather than claiming allocation-free rendering. The raw comparison, per-run metrics, source/JAR
provenance and real before/after framebuffers are in
`build/performance-comparison/`. These observations do not promise FPS on other
hardware or replace a manual campaign, multiplayer or operating-system DPI test.

## Final-JAR repeat — 2026-10-07

The preceding measurements are historical diagnostic evidence for `f5a817d5…`.
All compatibility and paired performance runs were repeated for the final JAR
`ad9a49459a306a379634f9fb37ce2766ce064ede612a50fd0a884bec4e8828ea`, against the same
verified prior JAR `928958d8…`, with the same fixture, Java, GPU and resolution.
The complete vertical slice also passes on this JAR, including the strengthened
foreground-wall check: all 1,301 feet and 2,406 torso reference pixels remain
recognizable within the documented transparency tolerance; collision is unchanged.

| Metric | Prior build | Final JAR |
| --- | ---: | ---: |
| Median of per-run p95 CPU render time | 3.9091 ms | 4.1095 ms |
| Median of per-run mean CPU render time | 3.3639 ms | 3.5546 ms |
| Approximate render-thread allocation | 4.453 MB/frame | 4.438 MB/frame |
| Managed textures / shaders | 66 / 3 | 66 / 3 |
| RGBA8 base-level texture estimate | 38,794,492 bytes | 38,917,372 bytes |

The measured p95 increase is 0.2004 ms (+5.13%). The same measurement limitations
above apply: this is CPU render wall time, not a GPU/FPS guarantee; the texture
estimate is not measured VRAM, and allocation remains substantial in both builds.
Receipt `build/performance-comparison/comparison.json` SHA-256:
`cae05c217621a9f2e0ef7b189ffd5eb7d938d257bc63fe9fa9e4f01627a2e2da`.

The final-JAR old-save check retains the same 215,337 exterior tiles, 9,025
interior tiles, 314 wild Pokémon, party and inventory identities through both
versions' load/save/reload. All four original files remain byte-identical.
Receipt `build/save-compatibility/save-compatibility.json` SHA-256:
`0bf32e1c66f2f2796f08e049fbbaaa00c380bf88f5ff7e2ee04b6fa6a8108e41`.
