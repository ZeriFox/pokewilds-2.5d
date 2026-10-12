# Cast giocabile in stile Bianco e Nero

La revisione mantiene le 19 identità selezionabili: Leaf, Gold, Kris, Brendan,
May, Hilbert, Hilda, Rosa, Calem, Serena, Chase, Elaine, Gloria, Mark, Mint,
Lunick, Summer, Kellyn e Lyra. Il selettore mostra ciascun personaggio una volta.

Hilbert e Hilda usano i fogli originali B/W già presenti. Gli altri 17 hanno
nuovi sprite ispirati allo stesso stile, ridisegnati con lo strumento integrato
image_gen conservando gli elementi distintivi del personaggio. Sono adattamenti
fan art assistiti da IA, non sprite ufficiali estratti da Bianco e Nero.

Ogni nuovo foglio contiene 12 fotogrammi: quattro direzioni con due passi e
una posa ferma. La corsa usa questi passi con cadenza più rapida; Hilbert e
Hilda usano anche le pose di corsa native già disponibili. Il fotogramma fermo
dei due personaggi originali è stato corretto. Gli sprite sono normalizzati
a celle di 32 pixel e mantengono lo stesso riferimento dei piedi al terreno.

Anteprima, mondo, scheda giocatore e apparizione in battaglia usano la stessa
identità. Surf, cavalcatura, pesca, seduta e riposo mantengono le composizioni
2.5D esistenti, ora con il personaggio selezionato. Non sono stati creati nuovi
fogli specifici per ogni azione speciale. Le regole e i tempi delle azioni,
il terreno e il formato dei salvataggi non cambiano.

I colori personalizzati modificano solo piccole zone degli abiti dei 17
personaggi ridisegnati; pelle, capelli e forma rimangono invariati. Il primo
colore del selettore mantiene la palette originale. Hilbert e Hilda conservano
i loro colori B/W originali, come nella versione precedente.

Le vecchie preferenze di stile non riattivano il cast a bassa risoluzione.
I personaggi già salvati mantengono la propria identità e ricevono la nuova
presentazione senza iniziare un'altra partita. Le mod del giocatore e gli
override espliciti conservano la precedenza. Una mod nuova deve includere
anche la vista posteriore necessaria al caricamento originale, oltre a cammino,
corsa, pesca, seduta e sacco a pelo, per essere proposta nel selettore.

## Asset e riproducibilità

`resources/visual/trainers-bw/sources/` conserva immagini generate, prompt
integrali e riferimenti. `PROVENANCE.json` registra hash, ritagli, ordinamento
dei fotogrammi, scala e maschere. La preparazione campiona i pixel esistenti
senza disegnare nuovi dettagli. Le direzioni di due fotogrammi nei fogli di
Brendan e Lyra sono ordinate secondo la posa effettivamente raffigurata.

`python tools/prepare_trainer_assets.py --check` verifica senza scrivere che
i 34 PNG di gioco e i metadati corrispondano alle sorgenti conservate.
`tools/trainer_style_test.py` verifica identità, direzioni, animazioni, colori,
mod, selezione e visibilità effettiva nel mondo sul JAR compilato.
`VERIFICA.txt` identifica la build locale e le prove realmente eseguite.
