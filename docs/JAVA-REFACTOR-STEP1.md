# Java structure — step 1: world mesh ownership

Base: `3da27238880f61ff9a6229c853d8f9c81f083cdc`. Unity migration is cancelled; Java/LibGDX remains the game and Python remains build/asset/test tooling.

`WorldMeshBatch` owns the CPU vertex arrays, identity-based texture groups, chunked GPU meshes, upload, rendering and disposal for one pass. `JohtoRenderer` retains scene collection, terrain/actor coordinates, shader and GL state, pass ordering, atmosphere and camera. The four passes remain terrain/actors, translucent overlays, shadows and emotes. Borrowed textures/shaders are not disposed by the batch.

The 8-float vertex layout, 65,532-vertex non-indexed chunks, triangle order, UVs, packed color and anchor-depth math are unchanged. This is a structural change, not new artwork or a performance claim. No game rules, PNGs, input, generation or save format is changed. No Windows binary is replaced by this commit.

## Verification

Run `python build.py`, then `python tools/world_mesh_batch_test.py` on an OpenGL desktop (or Xvfb/Mesa). The test compiles a renamed, checksum-pinned copy of the old renderer from `baseline/java-mesh-step1/JohtoRenderer.java`. No production override or loose production assets precede the built JAR.

The two probes check exact float/vertex-layout parity, actual GPU images at chunk boundaries, reuse and eviction, idempotent disposal, borrowed texture lifetime, and exact pre/post RGBA comparison in six real world scenes (day, night, volcano day/night, interior, return). Assertions and logs, not this document, determine pass/fail. Full acceptance: `python tools/verify_rework.py --stage all` on a clean commit; the native stage now includes the new test.

The one-time remote extraction script/workflow is removed from the final tree: normal builds read the tracked Java files directly. Scope hashes were refreshed only for the explicitly inspected renderer extraction and its new helper; historical rule/save protections remain.

## Next bounded step

After this checkpoint has passed, separate semantic object/material selection from world scene construction. Do not mix that change with camera, UI or gameplay changes. Preserve the test evidence and keep the original Java reference; the full-game comparison is a refactoring test, not proof of every possible gameplay path.
