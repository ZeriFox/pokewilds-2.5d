# PokeWilds — migrazione Unity / M0

**Prima base di porting C#, non conversione completa né sostituto della build Java.**
Base Java: `3da27238880f61ff9a6229c853d8f9c81f083cdc`. `main`, sorgenti Java, asset originali e salvataggi restano intatti.

## Aprire

Target: **Unity 6.3 LTS, 6000.3.25f1**, progetto 3D Built-in. Non richiede template Asset Store.
1. Nel clone del branch `feature/unity-port`, eseguire dalla root:
   `python -m pip install Pillow==11.3.0` e `python tools/prepare_unity_assets.py`.
2. Unity Hub > Add project from disk > selezionare **unity/**, non la root Java.
3. Aprire `Assets/PokeWilds/Scenes/Migration.unity` e premere Play.

In alternativa, dopo aver installato Pillow, l'Editor offre `Tools > PokeWilds > Import Java assets`.
`POKEWILDS_PYTHON` può contenere il percorso di un interprete Python specifico. Il menu usa solo il repository locale; non scarica asset.
Un progetto ZIP preparato dalla CI include già gli asset; non richiede Python per il primo avvio.

Controlli della scena M0: WASD movimento; RMB tenuto corsa; LMB ispezione del Pokémon vicino; RMB chiudi; Invio pausa.
La camera segue il giocatore; la rampa centrale permette di salire sul plateau. A est c'è acqua con Milotic, a ovest lava, a sud il settore cimitero.

## Trasferito in questo checkpoint

- Progetto Unity separato con scena e GUID tracciati, shader pixel cutout con depth buffer e nebbia.
- Campionamento C# indipendente dalla risoluzione, wrapping negativo e ritagli rettangolari.
- Importatore dei materiali Stardew correnti e dei trainer Unova, con conversione top-left PNG -> UV bottom-left.
- Sprite PMD **Pikachu, Machop e Milotic** di collaudo. Idle/Walk, 8 direzioni, tempi a 60 tick e origini firmate sono preservati; nessun ridisegno o rotazione artificiale del PNG.
- Terreno in mesh 3D, dislivelli e rampa. Movimento con collisioni sulla stessa superficie triangolata, nessun passaggio attraverso acqua/lava/props solidi.
- Import deterministico con hash dei sorgenti, controllo dei limiti e delle durate, crediti copiati. Java e PNG originali non vengono riscritti.
- Test della parte C# senza dipendenze Unity e verifiche dell'importatore. Un menu Editor separato valida le texture importate.

**Il generatore della scena è una fixture di 32×32 celle, non il generatore procedurale originale.**
Gli attori di prova sono collocati intenzionalmente e non hanno AI, incontri, HP o mosse fittizie.
`--all-pokemon` importa più clip disponibili ma NON converte il Pokédex giocabile o le regole di lotta.
Il campionamento mantiene la semantica Java nel normale intervallo del mondo; per coordinate astronomiche fuori dall'intervallo long usa un resto double non saturato.

## Non ancora migrato

Battaglie e mosse, inventario e crafting, interni interattivi, evoluzioni, spawn completi, generazione originale,
multiplayer, audio, menu finali e salvataggi. M0 non legge né sovrascrive `run-johto/`.
Il risolutore Java `WorldElevation` non è ancora portato: M0 usa quote esplicite della fixture.
La superficie ha una quota vera in Unity, ma non è ancora una riproduzione di un'isola salvata.

Nessuna promessa di compatibilità con i vecchi salvataggi: serve un export Java in un formato versionato e
un import C# con test round-trip, prima di consentire di aprire una partita esistente.

## Verifiche

`dotnet run --project tests/unity-core/UnityCoreTests.csproj --configuration Release` (dalla root del repo).
`python tools/test_unity_bridge.py`; `python tools/prepare_unity_assets.py --check`.

Il workflow **Unity M0 core and asset bridge** esegue la compilazione C# del solo core e l'import con gli asset reali.
**Non esegue Unity Editor, Play Mode o una compilazione Windows.** Il relativo artifact è un progetto sorgente, non un gioco eseguibile.
Prima del merge: aprire l'Editor, controllare Console/import, Play Mode, contatto sui gradini e rampa, PNG rettangolari,
origini PMD (anche negative), direzioni, input/focus, cambio risoluzione, rilascio delle risorse e build Windows.

Per una compilazione effettiva nell'Editor: `Tools > PokeWilds > Build Windows M0 prototype`.
Il menu richiede il modulo Windows Build Support e produce **Builds/Windows-M0/** (EXE + dati + runtime Unity).
Non genera un JAR e non usa Java. Non è però la versione completa del gioco.

## Prossimi checkpoint

M1: collaudo Unity della scena e porting dei dati di mondo/quote; export versionato di mappe Java di prova.
M2: porting del simulatore di movimento/interazioni e del generatore con confronto di fixture Java/C#.
M3: inventario/party/save adapter, senza modificare le partite sorgenti.
M4: regole di battaglia e scheduler mosse, UI e VFX separati, prove sulle stesse sequenze Java.
M5: resto dei contenuti, regressioni e pacchetto Windows completo. Solo allora valutare la sostituzione della versione Java.

Conservare una migrazione incrementale. Non cancellare il vecchio progetto o dichiarare la conversione conclusa al solo avvio della scena.

## Riferimenti e attribuzioni

Unity: https://unity.com/releases/editor/whats-new/6000.3.25f1
Input System: https://docs.unity3d.com/Packages/com.unity.inputsystem@1.17/manual/index.html
Coordinate sprite: https://docs.unity3d.com/6000.0/Documentation/ScriptReference/Sprite.Create.html

L'importatore preserva le attribuzioni del repository. Gli asset non diventano originali, CC0 o di proprietà del porting.
Vedere `docs/ASSET-RIGHTS.md`, `resources/visual/stardew/PROVENANCE.json`, `PMD-PROVENANCE.json` e `licenses/` nel repository Java.
Nessuna nuova pubblicazione di una release binaria è inclusa in questa fase.
