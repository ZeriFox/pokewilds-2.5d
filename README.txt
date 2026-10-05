POKEWILDS 0.8.11 — PC 2.5D, RELEASE v0.8.11-johto.2

AVVIO SU WINDOWS
Scarica ed estrai completamente pokewilds-2.5d-windows-x64.zip:
https://github.com/ZeriFox/pokewilds-2.5d/releases/tag/v0.8.11-johto.2
Repository e release privati: serve un account GitHub con accesso.
Code > Download ZIP contiene i sorgenti, non il gioco pronto.

Apri run-johto.cmd oppure run-johto-sprites.cmd. Java 17 Windows x64 e' incluso
nel pacchetto pronto; non serve cambiare il Java di sistema.
Salvataggi/impostazioni: run-johto/. run.cmd sceglie la presentazione classica
e usa run/, separata. La scelta grafica non disattiva il Pokedex ampliato o
la generazione moderna. Conserva insieme le cartelle distribuite.
Comandi: frecce, Z conferma, X indietro/corsa, Invio Start, F11 schermo intero.

NOVITA' E COMPATIBILITA'
Sprite e portrait PMDCollab; trainer, terreno, vegetazione e materiali B/W;
menu coordinati, arene composte con materiali B/W e transizioni. Giorno, notte e interni condividono
la vista prospettica. La build normale esclude GLB e usa sprite 2D.

552 specie aggiuntive, oltre alle 410 della base, portano il totale a 962.
Le aggiunte hanno dati reali e risorse compatibili. Le mosse sono
limitate a quelle implementate; condizioni evolutive, forme e artwork mancanti
sono documentati in POKEDEX-ESPANSO.txt. Il catalogo dei portrait PMD e' piu'
ampio del Pokedex effettivamente giocabile.

EMBER CALDERA aggiunge sentieri, rocce e lava solida. Rampe reali collegano
alcune terrazze. Il passaggio riguarda nuove isole e nuove aree oltre i bordi;
non riscrive i terreni dei salvataggi caricati. Il formato resta originale.
Leggi GENERAZIONE-MODERNA.txt. La proprieta' JVM -Dpokewilds.worldgen=classic
disabilita il passaggio per confronti, indipendentemente dalla grafica.

RICOMPILAZIONE DAL REPOSITORY
Servono Python 3.10+, JDK 17 e un clone completo. Configura JAVA_HOME oppure
java/javac/jar nel PATH. Il JDK incluso nel pacchetto pronto viene usato se
presente; non e' incluso nel clone Git.

Scarica pokewilds-2.5d-build-inputs.zip dalla release johto.2 ed estrailo nella
root del clone, accanto a build.py. Contiene soltanto:
  lib/upstream-runtime.jar
  lib/gltf-2.1.0.jar
Le risorse PMD, B/W e del Pokedex sono nel repository, in resources/visual/.
Non servono Git LFS o GLB per compilare la versione corrente.

Esegui build.cmd oppure:
  python build.py
Non scarica nulla. Produce dist/pokewilds-rebuilt.jar e verifica che le classi
incluse coincidano con quelle compilate. Versioni/hash: build/build-report.json.

Opzionale: recupera solo i GLB dal build-inputs della release johto.1 nella
cartella resources/visual/johto/models/pokemon/ ed esegui:
  python build.py --include-3d
L'opzione include i file disponibili; non riattiva da sola gli attori 3D
nel renderer corrente. MODELLI-3D.txt documenta l'esperimento precedente.

prepare_pmd_assets.py, prepare_unova_assets.py e prepare_expansion_dex.py
ricostruiscono risorse/dati dalle fonti registrate. Sono in tools/ e non
servono per compilare un clone completo. L'importazione PMD richiede Pillow
e requests; B/W richiede Pillow e sorgenti verificati. I dati del Pokedex
possono essere scaricati dagli snapshot fissati.

VERIFICHE DISPONIBILI
python tools/smoke_test.py
  Avvio e menu; evidenze in build/smoke.
python tools/world_smoke_test.py
  Generazione S reale, rendering, emote, movimento e salvataggio/caricamento.
  Evidenze in build/world-smoke/johto. --classic sceglie il renderer classico.
python tools/modern_world_test.py
  Determinismo del passaggio, rampe, lava, celle protette, TileData e generazione.
python tools/landscape_smoke_test.py
  Fixture giorno/notte, rilievi, caldera, interni e viaggio; build/landscape-smoke.
python tools/modern_ui_smoke_test.py
  Menu, crafting, incontri IA, lotte, fuga, cattura, livello e save/load.
  Evidenze in build/ui-battle-smoke.
python tools/expansion_dex_test.py
  Le 552 aggiunte, dati/mosse, evoluzione, crescita, uovo/schiusa, audio e save/load.
python tools/pmd_asset_smoke_test.py --bundled
  Verifica gli asset PMD incorporati nel JAR.
python tools/pmd_battle_visual_test.py
  Campioni nativi di portrait e animazioni PMD in lotta.
python tools/verify_visual_scope.py
  Confronta ambito e hash delle modifiche grafiche, del generatore e del Pokedex
  con la base PC. Non prova equivalenza del gameplay.

I test grafici richiedono desktop/OpenGL e partite isolate in build/.
--sources/--classes-override indicano prove di sviluppo, non il solo JAR.
VERIFICA.txt riporta controlli effettivamente conclusi, versione e hash testati.

STRUTTURA
src/                       Sorgenti Java.
resources/visual/pmd/       PNG/XML PMD, catalogo e metadati.
resources/visual/unova/     Atlante B/W, originali e provenienza.
resources/visual/dex/       Dati e grida del Pokedex aggiuntivo.
lib/                       Dipendenze binarie di compilazione.
dist/pokewilds-rebuilt.jar  Gioco con librerie e risorse.
toolchain/jdk-17/           JDK del pacchetto Windows.
tools/                     Preparazione e test.
build/                     Classi, log, immagini e partite isolate.
baseline/                  Base PC e manifest per il confronto.
licenses/                  Licenze e attribuzioni.

RICOSTRUZIONE DEL CODICE
Il repository ufficiale non fornisce il progetto Java completo. L'archivio
non ufficiale CFR e' stato verificato SHA-256, ma non compilava. I 198 sorgenti
di base sono stati recuperati dal JAR verificato con Vineflower 1.12.0.
Le cinque classi nate in Kotlin sono state ricostruite in Java, con tre
correzioni manuali. Altre correzioni: Battle.java (byte/int), PlayerStanding.java
(booleano/livello), DynamicTextures.java (color2=0). tools/recovery.patch registra
le correzioni rispetto alla decompilazione.

lib/upstream-runtime.jar esclude tutte le classi com/pkmngen originali:
nessuna classe precompilata del gioco viene usata come ripiego. Le librerie
esterne restano binarie. gdx-gltf 2.1.0 e la patch locale rimangono dipendenze
dei sorgenti dell'esperimento 3D.
Per ricreare il runtime da una copia verificata del JAR 0.8.11:
  python tools/prepare_runtime.py "percorso/pokewilds.jar"

LIMITI E FONTI
Simboli di puzzle, strutture speciali come la Pokemon Mansion, oggetti,
emote e pickup possono restare originali.
Mosse sul campo, costruzione, pesca, riposo e scene con indicatori particolari
usano il renderer normale del mondo; i menu sostituiti mantengono il tema.
I test dei flussi principali non verificano ogni contenuto o il multigiocatore.
Questa versione PC non e' un port Nintendo 3DS.

Gioco: https://github.com/SheerSt/pokewilds
Originale: https://github.com/SheerSt/pokewilds/releases/tag/v0.8.11
Archivio: https://github.com/TryTheSauceBoss/pokewilds-v0.8.11-decompiled-archive
Decompilatore: https://github.com/Vineflower/vineflower/releases/tag/1.12.0
PMD: https://sprites.pmdcollab.org/ e https://github.com/PMDCollab/SpriteCollab
B/W: https://www.spriters-resource.com/ds_dsi/pokemonblackwhite/
Dati: https://github.com/PokeAPI/pokeapi
Grida: https://github.com/PokeAPI/cries

Leggi PROVENANCE.json, PMD-PROVENANCE.json, resources/visual/unova/PROVENANCE.json,
resources/visual/unova/CREDITS.txt, POKEDEX-ESPANSO.txt e licenses/.
PMDCollab personalizzati: CC BY-NC 4.0; artwork originali: rispettivi titolari.
B/W: Barubary (trainer), Brom (ambiente), tsuka (arene), Nintendo/Game Freak/
The Pokemon Company. Questo progetto non assegna nuove licenze ad asset altrui.
