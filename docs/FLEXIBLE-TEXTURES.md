# Texture indipendenti dalla griglia logica

La griglia del gioco resta di 16 unita mondo. Non rappresenta piu un vincolo
alla risoluzione di ogni campione grafico: una casella puo mostrare un'immagine
intera da 24, 32, 48, 64, 128, 256 pixel, anche rettangolare. Non vengono modificati
collisioni, salvataggi, generazione o regole di movimento.

## Compatibilita

`BwAssets` carica prima `visual/landscape/world-atlas.json`: 202 regioni e sei
sequenze animate, con materiali dedicati ai biomi. Dall'atlante
`visual/unova/world-atlas.json` carica soltanto i protagonisti `trainer_*`.
Non ripristina i vecchi paesaggi B/W al posto dei materiali locali.

I materiali inclusi conservano il precedente mosaico a campioni da 16x16,
ridotto per regioni piu piccole. Per i 68 ritagli inclusi non divisibili per 16,
il mosaico conserva esplicitamente il comportamento precedente; lo sprite
completo restituito da `named()` conserva tutti i pixel. Questa eccezione di
compatibilita non si applica agli override personalizzati, che devono essere
divisibili esattamente. Una regione inclusa 64x64 resta un mosaico di 4x4 caselle.

Se esiste `resources/visual/custom/world-overrides.json`, viene caricato dopo la
base. Sostituisce soltanto le regioni nominate nel file e conserva le altre.
Nessun override personalizzato e attivo nel pacchetto. Le immagini e animazioni
del paesaggio locale rimangono incluse; il supporto agli override non aggiunge
automaticamente altre immagini o nuovi biomi.

## Installare un primo pacchetto

1. Inserire i propri PNG in `resources/visual/custom/`.
2. Creare `resources/visual/custom/world-overrides.json` con le regioni da cambiare.
3. Ricompilare con `build.cmd` e avviare la presentazione 2.5D.

`docs/texture-overrides.example.json` e un esempio inattivo. Non copiarlo come
file attivo prima di aver aggiunto i PNG referenziati o eliminato le voci mancanti.
I percorsi nel JSON sono relativi alla radice delle risorse: `visual/custom/...`,
NON `resources/visual/custom/...`. Il file viene incluso nel JAR dalla build;
non e un sistema di hot reload dei file accanto all'eseguibile.

Esempio completo per un'erba 64x64 ripetuta su ciascuna casella:

```json
{
  "regions": {
    "grass": {
      "texture": "visual/custom/grass-64.png",
      "x": 0, "y": 0,
      "width": 64, "height": 64,
      "sampleWidth": 64, "sampleHeight": 64
    }
  }
}
```

Per l'erba chiara usare anche la chiave `grass_light`, eventualmente con lo stesso
PNG: viene caricato una sola volta. I nomi devono corrispondere a quelli usati
in `BwAssets`/`JohtoRenderer`; aggiungere un nome nuovo non lo distribuisce da solo
sulla mappa. Ogni override e una definizione completa della regione, non una
fusione campo per campo con la definizione precedente.

## Campionamento

- `x`, `y`, `width`, `height`: rettangolo di origine in pixel; X/Y partono dall'alto
  a sinistra del PNG. Width/height sono obbligatori; X/Y predefiniti sono zero.
- `sampleWidth`, `sampleHeight`: pixel sorgente per una casella del mondo.
  Devono dividere esattamente la regione. Specificarli esplicitamente nei nuovi
  pack; senza questi campi resta il comportamento legacy a 16 pixel.
- `texture`: PNG separato facoltativo. Senza questo campo viene usata la texture
  landscape di base, oppure `texture` al livello principale insieme a `regions`.
  Per sostituire un frame `trainer_*` usare esplicitamente il proprio PNG o
  `visual/unova/world-atlas.png`.

Esempi: una regione 64x64 con campioni 64x64 si ripete su ogni casella; una regione
128x128 con campioni 64x64 copre un motivo continuo di 2x2 caselle; una regione
128x192 con campioni 64x48 copre 2x4 caselle. Per un singolo sprite 96x160 usare
sampleWidth=96 e sampleHeight=160, mantenendo l'immagine completa.

Non avvengono ridimensionamenti del PNG a 16 pixel. Vengono cambiate le coordinate
UV del campione, non la griglia logica. L'orientamento e la ripetizione alle
coordinate negative mantengono la convenzione precedente. Le sottoregioni sono
precalcolate al caricamento, senza allocazioni per ciascuna casella a ogni frame.

I ritagli devono rimanere dentro il PNG. Dimensioni frazionarie, campioni custom
non divisibili, ancore fuori intervallo, metadati non finiti e campioni eccessivi
producono un errore esplicito con nome del file e della regione. Un override
invalido interrompe il caricamento dell'atlante e ne libera le texture: correggere
il file e riavviare. Il caricatore non nasconde l'errore ripristinando vecchie mappe.

Le sei sequenze incluse restano animate. Sostituire un loro singolo frame (per
esempio `water_0`) conserva la sequenza; sostituire il nome della sequenza
(`water`, `lava_bright`, `fire`, ecc.) la rende intenzionalmente statica con la
nuova regione. Questo formato non definisce nuove sequenze animate. Campioni e
metadati dei frame correlati devono essere coerenti fra loro.

## Dimensione e ancoraggio degli sprite del mondo

Per alberi, oggetti, vegetazione, protagonisti e altri sprite verticali degli atlanti:

| Campo | Unita / comportamento |
| --- | --- |
| `worldWidth`, `worldHeight` | Dimensione disegnata, in unita mondo; 16 unita = una casella. |
| `anchorX`, `anchorY` | Frazioni da 0 a 1 dell'immagine, misurate da sinistra e dal basso. |
| `offsetX`, `offsetY` | Spostamento visivo del punto di appoggio nel piano della mappa. |
| `elevation` | Spostamento visivo verticale. Non introduce una quota nelle regole. |

Questa tabella descrive gli override custom. L'atlante landscape incluso usa
ancore in pixel: il caricatore le divide esplicitamente per larghezza/altezza
prima di passarle a `SpriteLayout`. Per esempio anchorX=44 in uno sprite largo
88 diventa 0.5. I metodi storici `anchorX(nome)` e `anchorY(nome)` mantengono
invece i valori in pixel. Non copiare direttamente le ancore del JSON landscape
in un override custom, dove valori fuori da [0,1] vengono rifiutati.

Se viene specificata solo una dimensione, l'altra segue le proporzioni del PNG.
Se mancano entrambe, restano le dimensioni legacy del renderer. Il punto tipico
per un albero e anchorX=0.5, anchorY=0. Gli offset di alberi/oggetti/protagonisti
spostano anche le rispettive ombre. Dimensioni e ancoraggi non alterano collisioni.
I frame dei protagonisti mantengono un'altezza predefinita di 27.2 unita mondo
(32 * 0.85), anche se il PNG passa a 64 o 128 pixel; i campi worldWidth/worldHeight
permettono di cambiarla intenzionalmente. Applicare gli stessi metadati ai frame
correlati per evitare variazioni di dimensione durante l'animazione.

Questi campi non ridimensionano pavimenti, pareti a mesh, prismi o l'intero edificio:
quella geometria continua a seguire le celle esistenti. Le texture di tali superfici
possono comunque essere sostituite. Gli sprite PMD mantengono i loro metadati e
non sono convertiti a questo formato. Altre schermate che usano `BwAssets` possono
vedere la nuova texture, ma la disposizione descritta qui riguarda `JohtoRenderer`.

## Limiti e verifiche

Non sono introdotti import automatico degli autotile RPG Maker, nuove animazioni,
collisioni personalizzate, import di modelli 3D, hot reload o modifica della camera.
Il filtro resta Nearest. Più pixel non aggiungono dettaglio a un PNG che non lo ha,
e risoluzione visibile e memoria disponibile dipendono da viewport/GPU.
Per sprite con ingombri o offset molto grandi va verificato anche il margine di
raccolta della scena, che questa modifica lascia invariato.

Eseguire i test del solo campionatore con Python e JDK 17+:

```text
python tools/test_flexible_sampling.py
```

Il test compila ed esegue `VisualSampling.java` reale. Copre le forme legacy,
coordinate positive/negative e confini, campioni 24/32/48/64/96/128/256,
mosaici rettangolari, coordinate non finite e dimensioni non valide.
Risultato verificato durante lo sviluppo: 12.309 controlli superati.
Questo NON e una compilazione completa di BwAssets/JohtoRenderer ne un test GPU.

Il test nativo aggiunto durante l'integrazione verifica il caricatore reale con
OpenGL: tutte le 202 regioni complete, il precedente mosaico a coordinate
positive/negative, 48 frame nelle sei animazioni, un campione 64x64, un mosaico
rettangolare 2x4, gli ancoraggi convertiti, dimensioni/offset, rifiuto di file
invalidi e rilascio/ricaricamento delle texture. I colori sono confrontati con il
framebuffer; i PNG e il log vengono salvati in `build/bw-assets-integration/`.
Il test richiama anche i metodi reali `trainerImage` e `upright` del renderer:
controlla i vertici, le dimensioni in unita mondo, i piedi, i ritagli proporzionali
25/26/14 dei protagonisti a 32 e 64 pixel, ancoraggi, offset e ombre degli oggetti.
Il test crea i propri override solo in una cartella temporanea sotto `build/`:
non attiva pacchetti custom nelle risorse distribuite.

```text
python tools/bw_assets_integration_test.py
```

Il comando normale usa il JAR gia compilato; `--sources` compila esplicitamente
BwAssets, VisualSampling, JohtoRenderer e VisualGeometry sopra quel JAR e lo
dichiara nel log. La prova del caricatore e dei vertici non sostituisce le scene
complete: quote, occlusione, giorno/notte e interni richiedono le suite native
del gioco. Per build, hash del JAR e prove effettivamente ripetute sulla consegna,
fare riferimento a `VERIFICA.txt`.

Conservare licenze, provenienza e crediti delle nuove immagini nel proprio pack.
