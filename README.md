# PokeWilds 2.5D — prototipo PC

Una ricostruzione PC di PokeWilds 0.8.11 con una presentazione 2.5D ispirata a HeartGold/SoulSilver: sprite 2D nel mondo prospettico, nuova vegetazione, menù coordinati e una nuova interfaccia di lotta. La mappa resta procedurale e le modifiche conservano il generatore, le regole di gioco e il formato dei salvataggi.

I modelli completamente 3D della precedente prova sono disattivati per impostazione predefinita. Interni, notte e alcune scene speciali usano ancora la visuale originale. Questa è una versione PC, non un port per Nintendo 3DS.

![Mondo procedurale in 2.5D](anteprima-25d.png)

![Interfaccia di lotta](anteprima-lotta.png)

## Giocare su Windows

1. Apri la [release v0.8.11-johto.1](https://github.com/ZeriFox/pokewilds-2.5d/releases/tag/v0.8.11-johto.1).
2. Scarica **`pokewilds-2.5d-windows-x64.zip`** ed estrai l'intero archivio in una cartella.
3. Apri **`run-johto.cmd`**. Il pacchetto include Java 17 per Windows x64.

Il repository e la release sono privati: per scaricare i file serve un account GitHub con accesso. **Code → Download ZIP scarica i sorgenti, non il gioco pronto da avviare.**

Comandi predefiniti: frecce per muoversi, **Z** per confermare, **X** per tornare indietro o correre, **Invio** per Start, **F11** per lo schermo intero. I salvataggi e le impostazioni vengono creati in `run-johto/`. Il launcher `run.cmd` avvia la grafica classica con una cartella `run/` separata.

## Ricompilare dai sorgenti

Prerequisiti: **Python 3.10 o successivo**, **JDK 17** e accesso al repository. Configura `JAVA_HOME` oppure rendi `java`, `javac` e `jar` disponibili nel `PATH`.

```powershell
git clone https://github.com/ZeriFox/pokewilds-2.5d.git
cd pokewilds-2.5d
```

Scarica **`pokewilds-2.5d-build-inputs.zip`** dalla [stessa release](https://github.com/ZeriFox/pokewilds-2.5d/releases/tag/v0.8.11-johto.1) usando il browser autenticato. Estrai il contenuto **nella root del clone**, accanto a `build.py`, mantenendo i percorsi dell'archivio:

```text
pokewilds-2.5d/
  build.py
  lib/
    upstream-runtime.jar
    gltf-2.1.0.jar
  resources/visual/johto/models/pokemon/
    ... file .glb
```

L'archivio ripristina le dipendenze e gli asset della build verificata, inclusi i modelli della prova 3D opzionale. Questi file voluminosi sono esclusi dalla cronologia Git. **Non serve Git LFS.**

Esegui `build.cmd` oppure:

```powershell
python build.py
```

La build non scarica dipendenze: compila i sorgenti Java e crea `dist/pokewilds-rebuilt.jar`. Il report con hash, versioni e controlli è scritto in `build/build-report.json`. Avvia poi `run-johto.cmd`.

## Verifiche e documentazione

La build è stata verificata su Windows x64 con test di avvio, generazione del mondo, movimento, menù, crafting, lotte, cattura e salvataggio/caricamento. I test grafici richiedono una sessione desktop con OpenGL e usano partite di prova separate.

```powershell
python tools/verify_visual_scope.py
python tools/smoke_test.py
python tools/world_smoke_test.py
python tools/modern_ui_smoke_test.py
```

- [VERIFICA.txt](VERIFICA.txt): risultati, hash del JAR consegnato e limiti dei controlli.
- [GRAFICA-25D.txt](GRAFICA-25D.txt): contenuto del prototipo e scene ancora da uniformare.
- [README.txt](README.txt): dettagli della ricostruzione e strumenti disponibili.
- [PROVENANCE.json](PROVENANCE.json): versioni, hash e provenienza delle dipendenze.
- [MODELLI-3D.txt](MODELLI-3D.txt): documentazione dell'esperimento 3D opzionale.

## Origine e attribuzioni

Il gioco originale è [PokeWilds di SheerSt](https://github.com/SheerSt/pokewilds), versione [0.8.11](https://github.com/SheerSt/pokewilds/releases/tag/v0.8.11). Questo progetto è una ricostruzione da bytecode, non il progetto sorgente Java originale: i sorgenti sono stati recuperati dal JAR verificato con [Vineflower 1.12.0](https://github.com/Vineflower/vineflower/releases/tag/1.12.0), a partire dai materiali dell'[archivio non ufficiale](https://github.com/TryTheSauceBoss/pokewilds-v0.8.11-decompiled-archive). Le correzioni della ricostruzione sono registrate in [tools/recovery.patch](tools/recovery.patch).

La nuova vegetazione e l'atlante del terreno sono immagini generate per il prototipo; prompt e provenienza sono in [ART_FOLIAGE_PROMPT.txt](ART_FOLIAGE_PROMPT.txt) e [ART_PROMPT.txt](ART_PROMPT.txt). Il catalogo dei modelli opzionali è in [MODELS-PROVENANCE.json](MODELS-PROVENANCE.json).

Gioco, risorse e dipendenze mantengono attribuzioni e condizioni dei rispettivi autori. **Questo repository non assegna una nuova licenza a tali materiali.** Conservare i documenti in [licenses/](licenses/) e i crediti distribuiti con gli asset. La dipendenza glTF e la sua modifica locale sono documentate in [third_party/gdx-gltf/README-patch.md](third_party/gdx-gltf/README-patch.md).
