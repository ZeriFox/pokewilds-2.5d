# Rilievi, roccia e scale

La prova locale del 14 ottobre prosegue dalla build `26ffcf6f`. Avvia
`run-johto.cmd` per usare il nuovo aspetto anche nei mondi salvati.

Le terrazze hanno angoli convessi arrotondati con più segmenti. Il piano alto,
la parete e il terreno inferiore condividono il contorno: i piccoli cunei
rimossi dalla smussatura ricevono un fondale alla quota inferiore. I raccordi
con rampe, acqua e superfici inclinate seguono le quote già presenti.

Le pareti asciutte usano una texture geologica dedicata, con strati continui
tra celle e angoli. Luce direzionale, bordo superiore e ombra alla base rendono
visibile il dislivello. Il colore varia con il bioma: pietra più calda nel
deserto, fredda sulla neve e scura ma leggibile nella caldera. Le sponde dei
fluidi mantengono i propri materiali.

Le scale mostrano sei fasce di gradini in pietra, nasi consumati e raccordi
laterali. La texture segue la direzione della salita; i gradini adiacenti si
uniscono nei passaggi larghi. I connettori piani conservano una lettura diversa
dalle salite effettive. Il rendering segue il sistema di quote e i percorsi
della partita.

## Materiali

`resources/visual/terrain-relief/rock-face.png` e `stair-stone.png` sono texture
create per questo progetto con ImageGen integrato. Originali, prompt esatti e
hash sono in `sources/` e `PROVENANCE.json` nella stessa cartella. L'importatore
normalizza a 128 pixel con campionamento nearest e PNG deterministico:

```powershell
python tools/prepare_terrain_relief.py --check
```

## Verifiche

`tools/relief_presentation_test.py` controlla otto scene di montagna, deserto,
neve e foresta, giorno e notte: angoli chiusi, rientri concavi, quattro direzioni
delle scale, continuità delle quote, texture, stato OpenGL e cache. La prova
usa scene isolate; `--before` conserva il confronto con il JAR precedente e
`--sources` identifica esplicitamente la diagnostica con classi sorgente.

`tools/relief_visual_test.py` percorre i rilievi con le azioni originali e una
partita di test della caldera, verificando superfici renderizzate, camera,
gradini e integrità dei file del mondo. I salvataggi personali non vengono
caricati da queste prove.

L'hash del JAR locale è in `VERIFICA.txt` e `build/build-report.json`; la
ricevuta completa delle prove sul JAR è `build/rework-verification/native.json`.
La release GitHub johto.4 precede questa iterazione locale.
