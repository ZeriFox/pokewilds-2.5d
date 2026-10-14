# Incontri e biomi

Questa revisione aggiunge regole specifiche per 28 specie e relative famiglie,
oltre ai metadati di habitat canonici già presenti. Le famiglie fredde restano
nei biomi innevati; Finneon, Frillish, Skrelp e Clauncher con le rispettive
evoluzioni richiedono acqua marina. Barboach e Basculin con le evoluzioni
richiedono acqua dolce. Sandygast e Palossand restano sulle spiagge asciutte,
Maractus negli ambienti aridi. Salandit e Salazzle usano aree vulcaniche,
aride o rocciose. Morelull e Shiinotic usano i boschi di notte oppure la
foresta profonda riparata anche di giorno.

Le regole si applicano alla nuova generazione, agli incontri camminando e
alla pesca. Le specie dell'espansione con una regola documentata vengono
registrate nei corrispondenti percorsi, senza affidarsi soltanto al tipo.
I duplicati nei percorsi e le probabilità relative restano invariati. Un
insieme filtrato vuoto non viene riempito con una specie estranea.

Le sorgenti e i limiti sono in
[`resources/visual/biomes/HABITAT-SOURCES.json`](../resources/visual/biomes/HABITAT-SOURCES.json).
Le condizioni ambientali sono scelte del progetto basate sulle descrizioni
del Pokédex e sulle famiglie evolutive, estratte dalla versione fissata dei
[dati PokeAPI](https://github.com/PokeAPI/pokeapi/tree/bc92d3b6029ef1abe9e7ad424c400b338f3c11fe/data/v2/csv).
Non ricostruiscono tutte le condizioni di ogni gioco Pokémon. Le specie
senza metadati o regole documentate conservano i percorsi originali: la
copertura ecologica dell'intero catalogo resta parziale.

Gli incontri di dungeon, puzzle e gli eventi già definiti conservano le
identità originali. Nessun Pokémon posseduto o già salvato viene spostato.
La profondità dell'acqua usata dai filtri indica vicini acquatici sulla
griglia, non una simulazione della profondità marina.

Verifica: `python tools/biome_habitat_test.py`, oppure `--sources` per una
prova diagnostica dei sorgenti. Il test usa tile, percorsi, Pokémon e
selezione effettiva degli incontri, verifica acqua marina/dolce, foreste
diurne/notturne, habitat freddi, 20.000 scelte riproducibili e gli incontri
protetti dell'oasi. Il rapporto completo dei percorsi va rigenerato sul
JAR finale con `python tools/habitat_coverage.py`.
