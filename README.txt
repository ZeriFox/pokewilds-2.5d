POKEWILDS 0.8.11 — PC 2.5D, BIOMI E INTERFACCIA — LOCALE 2026-10-07

Queste modifiche locali non sono pubblicate su GitHub. Il link seguente
identifica la precedente release johto.2. Per giocare usa AVVIA-POKEWILDS.cmd
oppure run-johto.cmd: non serve eseguire build.cmd. VERIFICA.txt identifica
il JAR verificato e gli esiti effettivi; AGGIORNAMENTO-BIOMI.txt descrive
implementazione e limiti in quattro sezioni: bug, UI, biomi e habitat.

AVVIO SU WINDOWS
Per la versione pubblicata precedente, estrai pokewilds-2.5d-windows-x64.zip:
https://github.com/ZeriFox/pokewilds-2.5d/releases/tag/v0.8.11-johto.2
Repository e release privati: serve un account GitHub con accesso.
Code > Download ZIP contiene i sorgenti, non il gioco pronto.
Questo aggiornamento richiede la cartella locale completa: la vecchia release
johto.2 non include i nuovi profili, materiali e correzioni.

Apri run-johto.cmd oppure run-johto-sprites.cmd. Java 17 Windows x64 e' incluso
nel pacchetto pronto; non serve cambiare il Java di sistema.
Salvataggi/impostazioni: run-johto/. run.cmd sceglie la presentazione classica
e usa run/, separata. La scelta grafica non disattiva il Pokedex ampliato o
la generazione moderna. Conserva insieme le cartelle distribuite.
Comandi locali: WASD movimento/navigazione, sinistro mouse (LMB) conferma,
destro mouse (RMB) indietro; tieni il destro mentre cammini per correre.
Invio apre il menu, C/V selezione sul campo, F11 schermo intero.
Nei nomi WASD digita lettere: frecce su/giu' cambiano campo, Invio conferma
il soprannome. Gamepad invariato. Il preset originale completo frecce/Z/X
viene migrato a WASD/MouseLeft/MouseRight in settings.txt; custom preservati.
Gli aiuti a schermo indicano i binding attivi.

NOVITA' E COMPATIBILITA'
Sprite e portrait PMDCollab; trainer B/W; nuovo paesaggio da Pixel Crawler,
Woods, Cold Cave e Forest_1, con set vulcanico secondario di Sevarihk e tre
elementi CC0 per lapidi/cactus: 202 regioni e 6 sequenze animate;
menu coordinati, Pokemon intero animato sulle tre pagine del riepilogo,
arene composte con gli stessi materiali del paesaggio e transizioni. Giorno, notte e interni condividono
la vista prospettica. La build normale esclude GLB e usa sprite 2D.

14 profili coordinano terreno, pareti, rilievi visivi, fluidi, decorazioni,
atmosfera, habitat e transizioni. Deserto, vulcano e cimitero hanno materiali
dedicati; il cimitero recupera nebbia e toni grigio-verdi. Il fantasma notturno
e' un incontro originale legittimo, ora visualizzato come apparizione moderna;
non viene usato come segnaposto di un asset mancante.
La revisione locale v5 dei rilievi ricostruisce piani superiori davvero alzati.
WorldElevation risolve i vincoli dei dirupi e delle rampe salvati; gli angoli
condivisi raccordano i passaggi aperti. Superfici, piedi e camera usano le
stesse quote. Le pareti collegano solo dislivelli reali, senza muretti quando
i due lati hanno la stessa quota. Bordi sottili, ombre di contatto e luce
sulla pendenza aiutano a leggere le terrazze. I segni delle rampe seguono
il pendio; i raccordi mantengono il materiale del bioma.
La cache usa una firma strutturale: maree e sole texture non invalidano il
rilievo. Anche i mondi caricati ricevono la nuova vista, senza nuove altezze
salvate, migrazioni dei salvataggi o cambiamenti a collisioni e movimento.
Questa revisione resta soltanto sul PC locale; non e' pubblicata su GitHub.
Maree guadabili e pozzanghere conservano il fondale visibile. I materiali dei
sentieri mansion seguono l'ambiente vicino anche quando la route originale
indica neve; la correzione grafica non cambia route o incontri.
Nuovi incontri e acquatici selvatici rispettano gli habitat. Milotic generato
nell'oasi viene ancorato all'acqua, con vincolo anche durante il movimento.
I Pokemon posseduti sono esclusi dal nuovo vincolo; salvataggi e Pokemon
gia' caricati non vengono riscritti, eliminati o teletrasportati.

Comandi e tutorial usano righe/colonne, margini e binding attivi. Il canvas UI
viene scalato uniformemente; testo lungo e quantita' hanno spazi separati.
Le prove di finestre al 100/125/150% equivalenti e a diversi rapporti non
certificano ogni monitor o cambio DPI hardware. Dettagli in VERIFICA.txt.

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
Le risorse PMD, B/W e del Pokedex provengono dal repository. L'integrazione
landscape e' solo locale e non e' pubblicata: per ricompilare questa iterazione
usa la cartella locale completa con resources/visual/landscape/.
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
e requests; B/W richiede Pillow e sorgenti verificati.
prepare_landscape_assets.py ricrea offline l'atlante locale dai download
fissati dell'utente e dalle fonti aggiuntive registrate; richiede Pillow.
verify_landscape_assets.py controlla hash, ritagli, opacita', animazioni e
riferimenti di tutti i profili. I dati del Pokedex
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
python tools/field_scene_test.py
  POWER/BUILD/DIG/SURF/FLY: action originali, movimento, preview e luce con
  renderer moderno esclusivo; conta draw legacy soppressi/inviati.
python tools/modern_ui_smoke_test.py
  Menu, crafting, incontri IA, lotte, fuga, cattura, livello e save/load.
  Evidenze in build/ui-battle-smoke.
python tools/desktop_controls_test.py
  Input polled mouse/tastiera/gamepad simulato, migrazione impostazioni,
  nickname WASD, nove catture native e bounds delle animazioni nel riepilogo.
  Evidenze in build/desktop-controls. --sources esclude la prova parser Game
  finche' non viene ricompilato il JAR; la verifica finale omette il flag.
python tools/expansion_dex_test.py
  Le 552 aggiunte, dati/mosse, evoluzione, crescita, uovo/schiusa, audio e save/load.
python tools/pmd_asset_smoke_test.py --bundled
  Verifica gli asset PMD incorporati nel JAR.
python tools/pmd_battle_visual_test.py
  Campioni nativi di portrait e animazioni PMD in lotta.
python tools/battle_viewport_test.py
  Panorama e HUD a cinque risoluzioni, cinque biomi e cinque zoom;
  copertura ai bordi e uscita dalla lotta senza esporre layer classici.
python tools/boss_battle_visual_test.py
  Intro/effect/send-out Mewtwo, Mega Gengar e Regigigas; caricamento del boss
  Mega Gengar in processo fresco e round-trip dati senza mutare Gengar normale.
python tools/verify_visual_scope.py
  Confronta ambito e hash delle modifiche grafiche, dei comandi PC,
  del generatore e del Pokedex
  con la base PC. Non prova equivalenza del gameplay.
python tools/verify_scope_test.py
  Respinge 13 mutazioni non autorizzate in copie temporanee isolate.
python tools/verify_landscape_assets.py
  Verifica l'atlante, tutti i materiali dei profili, animazioni e provenienza.
python tools/biome_habitat_test.py
  Regole degli habitat, selezione pesata, nuovi incontri e casi dell'oasi.
python tools/biome_scene_test.py
  Biomi, atmosfera, fantasma originale e geometria ricostruita nelle scene native.
python tools/world_elevation_test.py --saved-island
  Vincoli di quota, angoli condivisi, aperture, rampe, cache e determinismo.
python tools/relief_visual_test.py
  Caldera salvata isolata, superfici realmente alzate, pareti supportate,
  contrasto e rampe; PlayerMoving originale con piedi/camera continui.
  Evidenze in build/relief-visual; la copia del salvataggio conserva gli hash.
python tools/ui_layout_test.py
  Menu reali a risoluzioni equivalenti 100/125/150%, rapporti estremi e
  dimensioni del desktop; scala uniforme e separazione etichette/tasti.

I test grafici richiedono desktop/OpenGL e partite isolate in build/.
--sources/--classes-override indicano prove di sviluppo, non il solo JAR.
VERIFICA.txt riporta controlli effettivamente conclusi, versione e hash testati.

STRUTTURA
src/                       Sorgenti Java.
resources/visual/pmd/       PNG/XML PMD, catalogo e metadati.
resources/visual/unova/     Trainer B/W e archivio materiali precedenti.
resources/visual/landscape/ Paesaggio locale, 202 regioni, provenienza e crediti.
resources/visual/biomes/    14 profili completi e regole delle specie.
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
Edifici speciali, oggetti e simboli del mondo ricevono materiali e indicatori
moderni. Alcuni effetti di lotta e segnali di stato conservano risorse originali.
Una cella solida non classificata mostra comunque il suo ostacolo reale.
Mosse sul campo, costruzione, pesca e riposo mantengono la vista 2.5D.
Gli indicatori necessari sono proiettati nel mondo moderno; le azioni di gioco
continuano a essere eseguite anche quando i vecchi draw vengono soppressi.
I test dei flussi principali non verificano ogni contenuto o il multigiocatore.
Le regole habitat coprono le specie dichiarate; le altre mantengono i pool
originali delle aree. Profondita' e umidita' sono indicatori geometrici delle
celle vicine. La verifica delle classi non prova equivalenza completa del gioco.
Questa versione PC non e' un port Nintendo 3DS.

Gioco: https://github.com/SheerSt/pokewilds
Originale: https://github.com/SheerSt/pokewilds/releases/tag/v0.8.11
Archivio: https://github.com/TryTheSauceBoss/pokewilds-v0.8.11-decompiled-archive
Decompilatore: https://github.com/Vineflower/vineflower/releases/tag/1.12.0
PMD: https://sprites.pmdcollab.org/ e https://github.com/PMDCollab/SpriteCollab
B/W trainer: https://www.spriters-resource.com/ds_dsi/pokemonblackwhite/
Paesaggio locale:
  Anokolisa / Pixel Crawler: https://anokolisa.itch.io/free-pixel-art-asset-pack-topdown-tileset-rpg-16x16-sprites
  zedpxl / Woods: https://zedpxl.itch.io/pixelart-forest-asset-pack
  Asset Alliance / Cold Cave: https://gif-superretroworld.itch.io/cold-cave
  Haydeos / Forest_1: https://haydeos.itch.io/fantasy-forest-rpg-maker-tileset
  Sevarihk / Stone and Lava Ground Tiles: https://opengameart.org/content/stone-and-lava-ground-tiles
    CC BY 4.0: https://creativecommons.org/licenses/by/4.0/
    Modifiche: ritagli, palette e 16 frame di scorrimento per lava calda/raffreddata.
    Attribuzione completa: resources/visual/landscape/LICENSE-Sevarihk-CC-BY.txt
  marionline / Forest / Graveyard: https://opengameart.org/content/forest-graveyard-tileset
  ScratchIO / Desert Level Decorations: https://opengameart.org/content/desert-level-decorations-pixel-art
    Entrambi CC0: https://creativecommons.org/publicdomain/zero/1.0/
Ricerca delle fonti verificata 2026-10-07: resources/visual/landscape/ASSET-RESEARCH.json.
LimeZu e PixiVan sono stati valutati ma non integrati; nessun acquisto effettuato.
Dati: https://github.com/PokeAPI/pokeapi
Grida: https://github.com/PokeAPI/cries

Leggi PROVENANCE.json, PMD-PROVENANCE.json, resources/visual/unova/PROVENANCE.json,
resources/visual/unova/CREDITS.txt, resources/visual/landscape/PROVENANCE.json,
resources/visual/landscape/CREDITS.txt, POKEDEX-ESPANSO.txt e licenses/.
PMDCollab personalizzati: CC BY-NC 4.0; artwork originali: rispettivi titolari.
B/W: Barubary (trainer attuali), Brom e tsuka (materiali precedenti archiviati),
Nintendo/Game Freak/The Pokemon Company. Gli originali dei pack paesaggio
restano fuori progetto; l'atlante e' un'integrazione locale nel gioco. Questo progetto non assegna nuove licenze ad asset altrui.
