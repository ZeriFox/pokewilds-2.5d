# Colline, montagne e acqua in movimento

Revisione locale successiva a `d8f4d014`, senza nuova release GitHub.

Le pareti dei rilievi usano nuovi materiali originali con rocce irregolari e
terra compatta. La superficie superiore, la spalla e il piede del rilievo
devono restare distinguibili, anche sulla sabbia. Gli angoli convessi e i
raccordi sono costruiti dal renderer sulle quote già derivate dal mondo;
collisioni, rampe e dati salvati conservano il loro significato.

La texture blu dell'acqua è conservata. Lo shader ne muove la superficie
con due correnti e una rifrazione contenuta, oltre a creste e risacca. La
modalità movimento ridotto mantiene ferma l'animazione ambientale.

## Materiali originali

Generazione: skill `imagegen`, tool integrato `image_gen.imagegen`.
Tre prompt separati producono tre materiali, senza immagini esterne:

- `rock-face-v2.png`: massi naturali grigio-bruni e fratture irregolari,
  senza le precedenti fasce sedimentarie sottili.
  [Prompt esatto](../resources/visual/terrain-relief/sources/rock-face-v2-prompt.txt).
- `earth-face-v2.png`: zolle di terra ocra, pietre incassate e radici rade.
  [Prompt esatto](../resources/visual/terrain-relief/sources/earth-face-v2-prompt.txt).
- `hill-surface-v2.png`: terreno minerale beige chiaro visto dall'alto,
  con piccoli ciottoli e aree consumate.
  [Prompt esatto](../resources/visual/terrain-relief/sources/hill-surface-v2-prompt.txt).

File usati dal gioco in `resources/visual/terrain-relief/`. Gli originali
selezionati e i prompt completi esatti sono nella sottocartella `sources/`,
con i suffissi `-source.png` e `-prompt.txt`. Hash e trasformazioni sono
registrati in `PROVENANCE.json`. Gli asset precedenti rimangono conservati.
L'importazione applica solo campionamento nearest deterministico a 128 pixel
e codifica PNG lossless; non ridipinge o sfuma le immagini generate.

Verifica riproducibilità: `python tools/prepare_terrain_relief.py --check`.
Le verifiche native usano scene isolate sotto `build/`; nessun salvataggio
personale viene aperto. Le immagini di prova non sono nuovi mondi distribuiti.
