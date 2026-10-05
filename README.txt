POKEWILDS 0.8.11 - BASE PC E PROTOTIPO GRAFICO 2.5D

NOTA PER IL REPOSITORY GITHUB
Le istruzioni aggiornate per download e compilazione sono in README.md.
Il pacchetto pokewilds-2.5d-windows-x64.zip della release include gioco e Java.
Il clone Git contiene i sorgenti: Java e dipendenze binarie non sono inclusi.
Per compilarlo servono JDK 17, Python 3.10+ e l'archivio
pokewilds-2.5d-build-inputs.zip estratto nella root, accanto a build.py.
I riferimenti a Java/dipendenze "inclusi" qui sotto riguardano la distribuzione
completa, non il solo clone. Download ZIP di GitHub non e' il gioco pronto.
Release privata: https://github.com/ZeriFox/pokewilds-2.5d/releases/tag/v0.8.11-johto.1

AVVIO SU WINDOWS
Apri run-johto.cmd per il prototipo 2.5D ispirato a HeartGold/SoulSilver.
Ora usa sprite 2D nel mondo prospettico, nuovi alberi/rocce in pixel art,
menu ridisegnati e una nuova presentazione delle lotte con transizione coerente.
run-johto-sprites.cmd avvia la stessa versione 2.5D.
Apri run.cmd per la grafica classica.
Il launcher usa il Java 17 incluso in toolchain/jdk-17 e mantiene impostazioni
e salvataggi in run-johto (2.5D) o run (classica). Le due cartelle sono separate.
Conserva insieme tutte le cartelle del progetto.
Non occorre installare Java o modificare il Java di sistema.
Leggi GRAFICA-25D.txt per contenuto e limiti del primo prototipo.

COMANDI DEL GIOCO
Frecce: movimento e selezione. Z: conferma/A. X: indietro/B e corsa.
Invio: Start. F11: schermo intero.

RICOMPILAZIONE
Apri build.cmd, oppure esegui: python build.py
Richiede Python 3.10 o successivo; il JDK 17 e le dipendenze sono inclusi.
La build compila tutti i sorgenti Java in src/com/pkmngen e produce
dist/pokewilds-rebuilt.jar. Non scarica nulla dalla rete.
build/build-report.json registra versioni, hash, conteggi e verifica del JAR.
Usando gli stessi sorgenti, runtime e strumenti si deve ottenere lo stesso JAR.

TEST DI AVVIO
Esegui: python tools/smoke_test.py
Apre una finestra grafica invisibile, verifica 120 frame del menu e si chiude.
Richiede una sessione desktop con OpenGL. Log e immagine sono in build/smoke.

TEST DEL MONDO E AMBITO DELLE MODIFICHE
python tools/world_smoke_test.py
Genera un mondo piccolo con il generatore reale, verifica che la vista 2.5D sia
attiva, prova un'emote, simula un movimento e confronta i dati dopo salvataggio
e ricaricamento.
Log e schermate: build/world-smoke/johto. Dura circa un minuto su questo PC.
Con --classic verifica invece il rendering originale in una cartella separata.
python tools/modern_ui_smoke_test.py
Verifica menu, crafting, incontri reali avviati dall'IA originale, turni di
lotta, fuga, cattura, aumento di livello e salvataggio/caricamento.
Screenshot e log: build/ui-battle-smoke. Usa soltanto partite di prova.
python tools/verify_visual_scope.py
Confronta gli hash con la base PC e controlla che le modifiche ai sorgenti
originali siano limitate ai collegamenti grafici e alle azioni di presentazione
elencate. Generatore, logica Battle, Pokemon/Player, input e save restano invariati.
Il diff completo dei collegamenti e' prodotto in build/visual-hooks.patch.

STRUTTURA
src/                       Sorgenti Java modificabili del gioco e diagnostica.
resources/                 Nuove texture del prototipo 2.5D.
lib/upstream-runtime.jar   Librerie binarie e risorse originali del gioco.
lib/gltf-2.1.0.jar         Caricamento dei modelli glTF/GLB animati.
dist/pokewilds-rebuilt.jar Gioco PC ricompilato, con dipendenze e risorse incluse.
toolchain/jdk-17/          JDK Temurin 17 portatile per Windows x64.
tools/                     Preparazione runtime e verifiche di avvio.
build/                     Classi, log, schermate e salvataggi isolati dei test.
run/                       Directory di lavoro del gioco e futuri salvataggi.
run-johto/                 Impostazioni e salvataggi della modalita' 2.5D.
baseline/                  Game.java, report e JAR della base PC classica.
PROVENANCE.json            Provenienza, versioni e hash dei materiali di partenza.
ART_PROMPT.txt             Prompt e provenienza della nuova texture generata.
ART_FOLIAGE_PROMPT.txt     Prompt Imagegen dei nuovi alberi e rocce in pixel art.
MODELS-PROVENANCE.json     Catalogo, hash e fonti dei modelli 3D Pokemon.
licenses/                 Licenze delle nuove dipendenze e attribuzioni asset.

COME E' STATO RICOSTRUITO
Il repository pubblico ufficiale non fornisce il progetto Java completo.
L'archivio non ufficiale CFR e' stato scaricato e ne e' stato verificato SHA-256.
I sorgenti CFR non compilavano. Dal JAR originale verificato sono stati quindi
rigenerati i 198 sorgenti com/pkmngen con Vineflower 1.12.0, usando le dipendenze
originali per risolvere i tipi. Le cinque classi nate in Kotlin sono state
ricostruite in Java; tre di queste hanno richiesto correzioni manuali.

Le altre correzioni necessarie riguardano tre file:
- Battle.java: variabile numerica ricostruita come byte invece di int.
- PlayerStanding.java: separazione tra booleano e livello di evoluzione intero.
- DynamicTextures.java: ripristino dell'assegnazione numerica color2 = 0.
Le correzioni sono state confrontate con l'altra decompilazione o il bytecode.
tools/recovery.patch registra le modifiche rispetto all'output Vineflower.

Il runtime e' stato ottenuto rimuovendo TUTTE le entry com/pkmngen/ dal JAR
originale: nessuna classe precompilata del gioco e' usata come ripiego.
La build ricrea da sorgente l'intero namespace del gioco e controlla che le
classi del JAR finale coincidano con quelle appena compilate.
Le librerie esterne originali rimangono binarie, senza cambi di versione.
Per il renderer e' stata aggiunta gdx-gltf 2.1.0 come dipendenza separata.
Il precedente esperimento con attori 3D resta nei sorgenti e negli asset ma e'
disattivato per impostazione predefinita. MODELLI-3D.txt documenta quella prova;
per riattivarla esplicitamente occorre -Dpokewilds.models=on.

Per rigenerare lib/upstream-runtime.jar da una copia del JAR ufficiale:
python tools/prepare_runtime.py "percorso/pokewilds.jar"
Lo script controlla prima l'hash della versione 0.8.11.

LIMITI
Questa e' una ricostruzione da bytecode, non il progetto sorgente originale.
La compilazione e un test di avvio non garantiscono l'equivalenza di ogni
meccanica del gioco. Leggere VERIFICA.txt per i controlli effettivamente eseguiti.
Non e' una versione Nintendo 3DS: serve da base PC per il lavoro successivo.

PROVENIENZA E ATTRIBUZIONI
Gioco originale: https://github.com/SheerSt/pokewilds
Release originale: https://github.com/SheerSt/pokewilds/releases/tag/v0.8.11
Archivio: https://github.com/TryTheSauceBoss/pokewilds-v0.8.11-decompiled-archive
Decompilatore: https://github.com/Vineflower/vineflower/releases/tag/1.12.0
JDK: Eclipse Temurin 17, distribuzione originale con i suoi file di licenza.
Gioco, risorse e dipendenze mantengono attribuzioni e condizioni dei rispettivi
autori. Questo progetto non assegna una nuova licenza a tali materiali.
