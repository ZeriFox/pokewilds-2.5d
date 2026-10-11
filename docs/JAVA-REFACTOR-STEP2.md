# Java / passaggio 2 — materiali distinti e atmosfera discreta

Base remota: `392411be7bcb13f19a63948ad4544c36d9cec164` (passaggio 1).
Java/LibGDX resta il gioco; Python resta lo strumento per build e test.

## Responsabilità

- `WorldMaterials`: identità del terreno e degli oggetti, categorie geometriche, materiali dei muri e selezione degli alberi. Non carica texture e non modifica Tile, collisioni, generatori casuali o salvataggi.
- `BwAssets`: caricamento, ritaglio, campionamento, animazioni, dimensioni, ancore e proprietà delle texture. I metodi pubblici `terrainName` e `objectName` delegano al resolver per mantenere i chiamanti compatibili.
- `JohtoRenderer`: costruzione della scena, trasformazioni e passaggi grafici. Usa le categorie esplicite per distinguere arredamento, rocce, muri, finestre, porte, tetti, ponti e dettagli a terra.
- `WorldMeshBatch`: buffer e ciclo di vita delle mesh, invariato dal passaggio 1.
- `WorldAmbientOverlay`: piccoli accenti nello spazio del mondo, con mesh e shader propri. Viene disegnato dopo terreno/attori e prima di acqua trasparente, ombre, emote e UI, con test di profondità e senza scrittura della profondità.

La traduzione dei vecchi nomi dei tile rimane centralizzata nel resolver: non è una riscrittura dei dati del mondo o del generatore.

## Correzioni circoscritte

Un nome di pavimento contenente `rock`, `grass` o `house` non crea più da solo un oggetto verticale. L'oggetto superiore prevale sul nome del pavimento: per esempio una roccia sopra un ponte non viene inghiottita dal ramo che disegna il ponte. Le crepe vulcaniche (`volcanic_cracked`) appartengono ai dettagli piatti, non agli sprite verticali. Il rilevamento dei muri confinanti usa la stessa categoria del muro disegnato, comprese le pareti delle caverne.

Rocce, vegetazione e mobili usano le regioni intere e conservano dimensioni/ancore degli asset. Nessun nuovo PNG viene introdotto o ritagliato in questa fase. Non si promette la correzione di ogni eventuale difetto già presente nei fogli sorgente.

## Accenti ambientali

| Contesto | Accento |
| --- | --- |
| Lava reale | Poche scintille calde in salita; niente scintille sulla pietra asciutta |
| Bosco, bosco fitto e zone umide di notte | Piccole lucciole |
| Deserto e savana di giorno | Granelli di polvere radi |
| Cimitero | Chiazze basse di foschia morbida, soltanto su superfici piane dello stesso bioma |
| Neve | Pochi fiocchi lenti, non una bufera |
| Acqua di giorno | Piccoli riflessi, senza coprire la superficie |
| Case e caverne | Nessun nuovo effetto atmosferico esterno |

Configurazione tracciata: `resources/visual/biomes/ambient-overlays.json`.
Valori predefiniti: intensità **0,65**, massimo **24 particelle** e **6 chiazze di foschia** per fotogramma.
Limiti accettati: intensità 0..1, particelle 0..48, chiazze 0..8. I massimi sono tetti, non densità obbligatorie.

Niente bloom globale, tremolio della camera, filtro sopra il menu o distorsione delle battaglie.
Il contorno dei quad è sfumato nello shader, senza immagini rettangolari opache. Il punto d'interazione del giocatore ha una zona di rispetto; terreno e attori partecipano al test di profondità. Ogni particella nasce da un hash delle coordinate: nessun consumo di `Game.rand` o `map.rand`.

Per disattivare soltanto questi nuovi accenti, aggiungere prima di `-jar` al comando Java:
`-Dpokewilds.ambientOverlays=off`.
La nebbia/palette già esistente dei biomi e gli effetti delle abilità rimangono attivi.

## Verifica

`python build.py`
`python tools/world_presentation_layers_test.py` su un desktop OpenGL, oppure con Xvfb/Mesa.

Il nuovo test usa il JAR compilato, non classi di produzione sostituite nel classpath. Controlla le categorie e il vero dispatch delle crepe/rocce, sette coppie di immagini on/off, budget delle particelle, pixel della UI e zona centrale del giocatore, determinismo a parità di orologio, assenza di mutazioni dei tile e dei generatori casuali, pulizia entrando in un interno e assenza di errori GL. Le immagini sono in `build/world-presentation-layers/`.

La regressione storica `world_mesh_batch_test.py` continua a confrontare esattamente la scena con il renderer originale: disabilita esplicitamente soltanto i nuovi accenti. La loro resa attiva è verificata separatamente dal nuovo test; non vengono presentati come graficamente identici alla vecchia versione.

Accettazione completa: `python tools/verify_rework.py --stage all` su un checkout pulito. La suite comprende i precedenti 23 runner nativi più quello nuovo. Le ricevute e i log determinano l'esito, non questo documento.

Limiti: fixture controllate, non collaudo manuale di tutti i biomi/salvataggi/GPU o multiplayer. Il passaggio non pubblica da solo uno ZIP Windows né aggiorna il gioco già scaricato. Nessun incremento di FPS viene dichiarato.
