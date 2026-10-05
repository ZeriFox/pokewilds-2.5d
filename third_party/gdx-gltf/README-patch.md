# gdx-gltf 2.1.0 mesh splitting patch

The runtime uses gdx-gltf 2.1.0 with one local, ABI-compatible replacement:
`net.mgsx.gltf.loaders.shared.geometry.MeshSpliter`.

The original JAR is preserved as `gltf-2.1.0-original.jar`. The replacement source
is under `src/`; `PATCH-PROVENANCE.json` records original, patched and source
hashes plus the verification results. The upstream library's license remains
applicable to the bundled library; this replacement implements the existing
method contract.

## Why the patch is necessary

The upstream 2.1.0 splitter can emit index 65,535 into a batch containing only
65,535 vertices when an unsigned-index mesh crosses a batch boundary. This occurs
with the valid Chimecho GLB: the GPU would read outside the vertex buffer. The
2.2.1 adjustment avoids that specific index but can still split an individual
triangle across batches and change the geometry.

The replacement processes complete triangles or lines in source order. It
flushes a batch before the next primitive would exceed 65,535 unique vertices,
then remaps that entire primitive into the next batch. It copies every vertex
record exactly, including normals, UVs, weights and any additional attributes.
Shared vertices at boundaries are duplicated; no primitive is dropped, reordered
or simplified. Every emitted unsigned 16-bit index addresses a vertex in its own
batch. Invalid source indices and incomplete primitives are rejected explicitly.

This changes only model asset loading. It does not alter GLB files, gameplay,
procedural generation, collision coordinates or saved games.

## Reproduce and verify

From the project directory run `python tools/patch_gltf_loader.py`. Python's
standard library and the bundled JDK 17 are sufficient. The script checks the
pinned upstream JAR hash, compiles this source, runs
`tools/MeshSplitterInvariantTest.java`, and compares every non-patch JAR entry
with the original before replacing `lib/gltf-2.1.0.jar`.

The invariant suite verifies source-order reconstruction of every primitive,
bit-identical vertex records, valid local unsigned indices, boundary crossings,
repeated vertices, degenerate triangles, lines, sparse input, empty input and
invalid-input rejection. It also tests all three Chimecho primitives from the
actual asset: 168,701 + 57,447 + 64,226 triangles remain identical after splitting
into 8 + 3 + 3 batches.

Upstream reference:
[gdx-gltf 2.1.0 MeshSpliter](https://github.com/mgsx-dev/gdx-gltf/blob/2.1.0/gltf/src/net/mgsx/gltf/loaders/shared/geometry/MeshSpliter.java).
