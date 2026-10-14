# Terrain relief materials

These two opaque 128 x 128 PNG materials were generated specifically for this project with the built-in `image_gen.imagegen` tool. The selected original PNGs and the exact prompts are retained in `sources/`; their hashes and output hashes are recorded in `PROVENANCE.json`.

- `rock-face.png`: continuous eroded geological strata, warm grey and olive midtones, chipped rounded shelves and a few fissures. Intended for repeat-wrapped cliff faces. Edge differences are comparable to normal pixel-neighbour differences; there is no painted frame or solid black wall.
- `stair-stone.png`: parallel worn stone tread bands with softly lit noses, using the same palette. Intended for clamped stair surfaces; it does not contain scene perspective.

Import with `python tools/prepare_terrain_relief.py`; verify with `python tools/prepare_terrain_relief.py --check`. Normalization uses only deterministic pixel-centre nearest-neighbour sampling and the repository's canonical lossless PNG encoder. It applies no painting, palette quantization, blending, blur, alpha changes or seam synthesis.

The selected rock surface is the second generated rock variant. The first variant was rejected because its separate blocks looked too much like masonry. Stair material uses the first generated stair variant. Runtime geometry, lighting and biome tinting are provided by the renderer.
# Materiali aggiunti nella revisione naturale

`earth-face.png` distingue i terrazzamenti erbosi dalle montagne.
`lava-surface.png` presenta magma luminoso e crosta scura.
`water-surface.png` presenta onde pixel-art su una superficie blu.
Le due superfici fluide usano UV continui nel mondo e texture autonome con
Repeat, così lo scorrimento non può leggere altre celle dell'atlante.
I prompt esatti e gli originali sono conservati in `sources/`, con hash in
`PROVENANCE.json`. Il tool integrato image_gen ha generato i materiali;
l'importatore applica solo campionamento nearest deterministico a 128 pixel
e codifica PNG lossless riproducibile. Verifica: `python
tools/prepare_terrain_relief.py --check`.
