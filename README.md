# PokeWilds 2.5D — v0.8.11-johto.2

Una ricostruzione PC di PokeWilds 0.8.11 con mondo prospettico, sprite animati [PMDCollab](https://sprites.pmdcollab.org/), materiali e allenatori di [Pokémon Black / White](https://www.spriters-resource.com/ds_dsi/pokemonblackwhite/), menù coordinati e nuove arene di lotta.

La versione aggiunge **552 specie giocabili** alle 410 della base, per **962 specie totali**, una caldera vulcanica e rampe percorribili tra alcune terrazze montane. La mappa resta procedurale: il nuovo passaggio di generazione modifica davvero terreno e collisioni. La presentazione predefinita usa sprite 2D e la build normale esclude i modelli GLB. È una versione Windows PC x64, non un port Nintendo 3DS.

![Mondo procedurale in 2.5D](anteprima-25d.png)

![Interfaccia di lotta](anteprima-lotta.png)

![Portrait PMD e nuove specie nella squadra](anteprima-squadra.png)

![Caldera in una mappa procedurale reale](anteprima-vulcano.png)

## Giocare su Windows

1. Apri la [release v0.8.11-johto.2](https://github.com/ZeriFox/pokewilds-2.5d/releases/tag/v0.8.11-johto.2).
2. Scarica **`pokewilds-2.5d-windows-x64.zip`** ed estrai l'intero archivio.
3. Apri **`run-johto.cmd`**. Il pacchetto include Java 17 per Windows x64.

Il repository e la release sono privati: serve un account GitHub con accesso. **Code → Download ZIP scarica i sorgenti, non il gioco pronto da avviare.**

Comandi: frecce per muoversi, **Z** conferma, **X** indietro/corsa, **Invio** Start, **F11** schermo intero. Salvataggi e impostazioni sono in `run-johto/`. `run-johto-sprites.cmd` avvia la stessa presentazione. `run.cmd` usa la grafica classica e una cartella `run/` separata; la scelta grafica non disattiva il Pokédex ampliato o la nuova generazione.

## Contenuto e limiti

- **Mondo:** materiali B/W per terreno, vegetazione, rocce ed edifici; due protagonisti B/W; Pokémon PMD animati con dimensione stabile e piedi ancorati. Giorno, notte e interni condividono il renderer prospettico.
- **Interfaccia e lotte:** squadra, deposito, borsa, riepilogo, crafting, dialoghi e impostazioni coordinati; portrait PMD, arene composte con materiali B/W, HP/EXP e transizioni.
- **Pokédex:** 552 aggiunte con dati e grida da versioni registrate. Sono usate soltanto mosse già implementate; condizioni evolutive non supportate sono escluse. Non tutte le forme alternative o varianti shiny hanno artwork esatto. Dettagli in [POKEDEX-ESPANSO.txt](POKEDEX-ESPANSO.txt).
- **Generazione:** EMBER CALDERA contiene lava realmente solida, sentieri e rocce; alcune pareti montane ricevono rampe reali. Il passaggio riguarda nuove isole e nuove aree oltre i bordi; non riscrive i terreni dei salvataggi caricati. Il formato dei salvataggi resta originale. Dettagli in [GENERAZIONE-MODERNA.txt](GENERAZIONE-MODERNA.txt).

Alcuni simboli di puzzle, strutture speciali come la Pokémon Mansion, oggetti, emote e pickup conservano le risorse originali. Mosse sul campo, costruzione, pesca, riposo e altre scene con indicatori particolari usano ancora il renderer normale del mondo. Anche le risorse PMD mancanti hanno fallback documentati. I test non coprono ogni contenuto o configurazione multigiocatore.

## Ricompilare dai sorgenti

Servono **Python 3.10+**, **JDK 17** e accesso al repository. Configura `JAVA_HOME` oppure rendi `java`, `javac` e `jar` disponibili nel `PATH`.

```powershell
git clone https://github.com/ZeriFox/pokewilds-2.5d.git
cd pokewilds-2.5d
```

Scarica **`pokewilds-2.5d-build-inputs.zip`** dalla [release johto.2](https://github.com/ZeriFox/pokewilds-2.5d/releases/tag/v0.8.11-johto.2) con il browser autenticato. Estrailo **nella root del clone**, accanto a `build.py`. Contiene soltanto le due dipendenze binarie:

```text
pokewilds-2.5d/
  build.py
  lib/
    upstream-runtime.jar
    gltf-2.1.0.jar
  resources/visual/
    pmd/       ← nel repository
    unova/     ← nel repository
    dex/       ← nel repository
```

Le risorse PMD, B/W e del Pokédex sono nel repository. **Non servono Git LFS o GLB per la build normale.**

```powershell
python build.py
```

È equivalente a `build.cmd`. La build non scarica dipendenze e produce `dist/pokewilds-rebuilt.jar`; versioni e hash sono in `build/build-report.json`. Avvia poi `run-johto.cmd`.

Solo per includere anche gli asset dell'esperimento 3D: recupera i GLB dal build-inputs della [precedente release johto.1](https://github.com/ZeriFox/pokewilds-2.5d/releases/tag/v0.8.11-johto.1), ripristina `resources/visual/johto/models/pokemon/` ed esegui `python build.py --include-3d`. L'opzione include i file presenti nel JAR; non cambia la presentazione predefinita e non riattiva da sola i vecchi attori 3D.

La preparazione delle risorse è separata dalla compilazione: `prepare_pmd_assets.py` richiede Pillow e requests, `prepare_unova_assets.py` richiede Pillow e sorgenti verificati, `prepare_expansion_dex.py` ricostruisce il Pokédex dai dataset fissati. Questi strumenti sono in `tools/` e non servono per compilare un clone completo.

## Verifiche e documentazione

[VERIFICA.txt](VERIFICA.txt) riporta i controlli effettivamente conclusi e l'hash del JAR testato. Le prove di sviluppo con classi sovrapposte sono distinte dai test sul JAR distribuito; la compilazione non dimostra l'equivalenza completa delle meccaniche.

I test grafici richiedono una sessione desktop con OpenGL e usano partite isolate:

```powershell
python tools/verify_visual_scope.py
python tools/smoke_test.py
python tools/world_smoke_test.py
python tools/modern_world_test.py
python tools/landscape_smoke_test.py
python tools/modern_ui_smoke_test.py
python tools/expansion_dex_test.py
python tools/pmd_asset_smoke_test.py --bundled
python tools/pmd_battle_visual_test.py
```

- [GRAFICA-25D.txt](GRAFICA-25D.txt): presentazione, fonti e fallback.
- [README.txt](README.txt): struttura e ricostruzione del codice.
- [POKEDEX-ESPANSO.txt](POKEDEX-ESPANSO.txt), [GENERAZIONE-MODERNA.txt](GENERAZIONE-MODERNA.txt): regole delle aggiunte.
- [PROVENANCE.json](PROVENANCE.json), [PMD-PROVENANCE.json](PMD-PROVENANCE.json), [provenienza B/W](resources/visual/unova/PROVENANCE.json): versioni, hash e autori.
- [MODELLI-3D.txt](MODELLI-3D.txt): documentazione storica dell'esperimento 3D.

## Origine e attribuzioni

Il gioco originale è [PokeWilds di SheerSt](https://github.com/SheerSt/pokewilds), versione [0.8.11](https://github.com/SheerSt/pokewilds/releases/tag/v0.8.11). Il progetto è ricostruito dal bytecode del JAR verificato con [Vineflower 1.12.0](https://github.com/Vineflower/vineflower/releases/tag/1.12.0), a partire dall'[archivio non ufficiale](https://github.com/TryTheSauceBoss/pokewilds-v0.8.11-decompiled-archive). Le correzioni di recupero sono in [tools/recovery.patch](tools/recovery.patch).

Gli sprite PMD provengono da [SpriteCollab](https://github.com/PMDCollab/SpriteCollab), con autori per variante in [PMD-PROVENANCE.json](PMD-PROVENANCE.json) e crediti in [licenses/](licenses/). Gli artwork personalizzati seguono CC BY-NC 4.0; quelli originali attribuiti a Chunsoft mantengono i rispettivi diritti.

Per B/W: [allenatori, Barubary](https://www.spriters-resource.com/ds_dsi/pokemonblackwhite/asset/34024/), [ambiente, Brom](https://textures.spriters-resource.com/ds_dsi/pokemonblackwhite/asset/375513/) e [arene, tsuka](https://textures.spriters-resource.com/ds_dsi/pokemonblackwhite/asset/368759/). Fogli originali, hash e preparazione sono in [resources/visual/unova/](resources/visual/unova/). Le texture generate della prima prova restano archiviate; il renderer corrente usa i materiali B/W.

Dati e grida aggiunti provengono da [PokeAPI](https://github.com/PokeAPI/pokeapi) e [PokeAPI/cries](https://github.com/PokeAPI/cries), con commit e hash documentati nel Pokédex. **Questo progetto non assegna nuove licenze ai materiali dei rispettivi autori.** Conservare crediti, licenze e provenienza. La dipendenza glTF e la modifica locale sono documentate in [third_party/gdx-gltf/README-patch.md](third_party/gdx-gltf/README-patch.md).
