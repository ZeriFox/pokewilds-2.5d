# Terreno modulare e leggibilità — 12 ottobre 2026

Base recuperata senza conflitti: `ac526d14` su `origin/main`. Il ramo locale
`work/local-terrain-2026-10-12` mantiene separata questa lavorazione; il recupero
non riscrive la cronologia remota. Il precedente stato locale è conservato in
`backup/before-structural-terrain-2026-10-12`.

## Struttura delle nuove isole

`AuthoredTerrain` ricostruisce prima il terreno naturale interno raggiungibile,
togliendo vecchi contorni e vegetazione casuale nelle aree sicure. Dispone poi
sei pezzi 17×17 e 9×9 progettati in ASCII: radura, boschetto e
terrazza con due accessi. I simboli producono insieme terreno, alberi, collisioni,
bordi alti e rampe. Le coordinate dei pezzi e le varianti sono determinate dal
seed; la mappa continua quindi a essere procedurale. Non esiste una seconda
mappa decorativa sovrapposta a quella usata dal giocatore.

I moduli si inseriscono nelle zone naturali compatibili durante la generazione,
prima degli incontri. Eventi, oggetti, strutture, acqua, coste e labirinti hanno
protezioni esplicite. Gli accessi esterni e la connessione dei passaggi interni
sono verificati prima di cambiare le celle. Questo catalogo iniziale non
sostituisce i dungeon e le regioni speciali con pezzi generici. Gli alberi che
contengono incontri HEADBUTT mantengono identità, posizione e Pokémon.

La caldera viene ricostruita con un contorno chiuso, una sola quota interna,
due scalinate larghe tre celle e una passerella asciutta tra i bacini di lava.
I vecchi dirupi attraversati dai sentieri non diventano più catene di rampe.
Il bacino viene inserito soltanto quando impronta, contenuti protetti e accessi
sono compatibili; negli altri casi il terreno vulcanico rimane piano.

Una partita già salvata non viene rigenerata: costruzioni e terreno rimangono
quelli del salvataggio. Per vedere le nuove disposizioni occorre una nuova
avventura o una nuova isola; le correzioni di rendering valgono anche caricando
una partita esistente.

## Rendering e interfacce

- Le scale hanno un materiale visibile anche nei vecchi connettori piatti. Su una
  vera pendenza, pedate chiare e ombre seguono la quota del terreno.
- I materiali naturali asciutti si raccordano ai margini; acqua, dirupi,
  pavimenti e interazioni conservano bordi espliciti. Il prato riceve meno
  contrasto e saturazione nello shader, senza modificare i file grafici.
- Vegetazione, oggetti e personaggi condividono lo stesso criterio di profondità
  ai piedi. I Pokémon usano l'ingombro completo di Idle/Walk nelle quattro
  direzioni, evitando cambi di scala quando si girano e pixel sotto il terreno.
  La testa del personaggio addormentato usa la profondità del letto effettivamente
  disegnato, anche quando cambiano zoom e dimensioni degli asset personalizzati.
- Gli allenatori dettagliati B/W esistenti risultano più leggibili a schermo.
  Non viene dichiarato un nuovo set di sprite originali: il catalogo e i crediti
  degli asset recuperati da GitHub restano disponibili.
- Squadra, inventario e menu condividono pannelli chiari, testo ardesia e accenti
  verde petrolio. La transizione di battaglia usa una chiusura/apertura continua;
  la preferenza di movimento ridotto usa dissolvenze. Controlli, hitbox e tempi
  delle azioni originali sono conservati.

## Verifica

I runner dedicati `authored_terrain_test.py`, `terrain_readability_test.py` e
`battle_entry_test.py` verificano rispettivamente topologia, rendering
nativo e transizioni/interfacce contro il JAR compilato. Le ricevute in `build/`
identificano il JAR realmente provato; questo documento descrive il progetto e
non certifica da solo un'esecuzione. I test usano partite e cartelle isolate.

Il confronto CPU/GPU del contenitore `WorldMeshBatch` con la versione congelata
rimane obbligatorio. Il precedente confronto pixel-identico dell'intera scena
resta disponibile con `world_mesh_batch_test.py --historical-parity`, riferito
alla sola estrazione del contenitore: richiedere oggi gli stessi pixel dei
vecchi gradini nascosti e della vecchia profondità impedirebbe queste correzioni.
I nuovi criteri visivi sono controllati dai test dedicati, inclusi campioni GPU
di occlusione davanti/dietro, scale piatte/inclinate e immutabilità della mappa.
