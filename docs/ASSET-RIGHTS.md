# Asset provenance and distribution status

Review date: 2026-10-07. This is an evidence record, not a new asset licence.

The selected source sheets were supplied in the interrupted repository. Their hashes, extraction credits, source pages and transformations remain in `art-source/reference/manifest.json` and `resources/visual/stardew/PROVENANCE.json`. Original Stardew Valley art belongs to ConcernedApe; FireRed/LeafGreen art belongs to Nintendo / Game Freak and the respective rights holders. PMD provenance remains unchanged.

The selected environment page is <https://www.spriters-resource.com/pc_computer/stardewvalley/> and the UI reference is the Pokémon Menu sheet at <https://www.spriters-resource.com/game_boy_advance/pokemonfireredleafgreen/asset/3854/>. Direct retrieval of both pages and <https://www.spriters-resource.com/page/tou/> returned HTTP 403 in this review. Search-indexed terms distinguish custom artwork from copyrighted game content; they do not establish a rightsholder licence to redistribute these extracted game assets.

No general redistribution permission was verified for these new Stardew/FRLG derivatives. Do not label them MIT, CC0 or original artwork, and do not treat the repository's availability or a sheet credit as permission. The local playable package is an implementation/review build using the supplied assets; public release publication is not automated. Rights clearance remains an explicit limitation of R12/M9 before wider distribution. No access restriction was bypassed.

The deterministic importer supports reproducing the atlas from the exact supplied source hashes. Raw sheets are outside `resources/` and are excluded from the playable package; this exclusion does not change the rights of the derived atlas. Existing landscape, Unova and PMD credits must accompany the package as well.

## Trainer redraws, 2026-10-12

The seventeen sheets in `resources/visual/trainers-bw` are AI-assisted fan-art
redraws made with the built-in image_gen tool. They adapt the existing playable
character identities to a B/W-inspired presentation; they are not official
extracted B/W sheets. Hilbert and Hilda retain the genuine B/W art already
credited in `visual/unova`. Generated source images, exact prompts and references
are preserved in `visual/trainers-bw/sources`; its `PROVENANCE.json` records
deterministic normalization and clothing masks. The character rights remain
with their respective holders; this addition does not assert an open artwork
licence or change the distribution status recorded above.
# Arene, guardaroba e fantasma (ottobre 2026)

I nuovi fondali in `resources/visual/battle-scenes`, i componenti in
`resources/visual/wardrobe` e lo spettro in `resources/visual/ghost` sono immagini
fan art create con lo strumento integrato ImageGen. Le sorgenti e i prompt sono
conservati nelle rispettive cartelle. La foresta usa l'immagine fornita dall'utente
come riferimento di composizione e stile; lo spettro richiama il fantasma
misterioso di Lavandonia. Questi adattamenti non trasferiscono diritti sui
personaggi, sui marchi Pokémon o sui riferimenti preesistenti e non sono rip
ufficiali di Nintendo.
