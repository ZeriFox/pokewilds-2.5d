# Mondo procedurale e natura animata

La lavorazione locale prosegue dal commit `5dd0db06`. La copia precedente del
codice è nel ramo `backup/before-living-world-2026-10-12`; manifest e verifica
precedenti sono conservati in `baseline/before-living-world`.

## Terreno

Il catalogo di `AuthoredTerrain` dispone pezzi progettati da 17, 9 e 5 celle in
base al seed e all'ambiente. Dune e letti asciutti, radure, argini, creste e
corti hanno impronte diverse. La passata modifica le celle reali della nuova
isola: terreno, ostacoli e accessi condividono collisioni e grafica.

La ricostruzione copre dieci famiglie naturali: pianura/bosco, foresta profonda,
palude, montagna, neve, deserto, spiaggia, oasi, savana e cimitero. Il vulcano
mantiene la caldera con due scalinate e un percorso asciutto fra i bacini.
Il report distingue celle naturali candidate, preparazione, celle nei moduli e
numero di pezzi per famiglia; preparazione e moduli possono sovrapporsi e non
vanno sommati come percentuale di copertura.

Il nuovo assemblaggio preserva acqua, maree, ingressi, edifici, enigmi e incontri
speciali. Il passaggio successivo di raccordo delle rive, già presente nella
base, può ancora riempire singole celle `water2` interne circondate da almeno
tre celle asciutte; la generazione completa non garantisce acqua identica.
Le celle riservate a Pokémon, agli incontri memorizzati e ai dungeon sono
escluse anche da questo raccordo. I moduli piccoli non aprono l'alone intorno
a una cella trattenuta che contiene un evento o un attore riservato.
Nella foresta profonda si lavorano le stanze aperte, preservando le
pareti del labirinto; nel cimitero rimangono tombe e accessi alle interazioni.
Il catalogo non implica che ogni cella accetti un modulo: impronta, contenuti
protetti e connettività determinano dove può essere inserito.

## Acqua e vento

- Il vento piega in modo progressivo erba, fiori e vegetazione flessibile.
  Le radici restano ancorate; tronchi, personaggi, Pokémon, cactus e arredi non
  ricevono questa deformazione.
- Le onde nello shader usano coordinate mondo: il moto continua fra le celle
  senza scorrere le coordinate dell'atlante su immagini adiacenti.
- Schiuma, risacca e sabbia umida seguono acqua e coste effettive. L'effetto è
  parte della superficie; non è una seconda mappa o una striscia decorativa.
- Il fondale visivo raccorda il mare alla spiaggia; le coste rocciose e le
  quote alte conservano il proprio dislivello. Nessuna collisione è spostata
  dall'animazione.
- Le preferenze di movimento ridotto congelano gli effetti ambientali,
  comprese cascate, lava e particelle. Azioni, personaggi e ciclo del mondo
  continuano normalmente.

I materiali distinguono vegetazione della palude, foresta profonda, montagna,
neve e savana. Le sommità delle pareti delle grotte usano il materiale della
grotta. I file grafici esistenti e i relativi crediti sono conservati.

## Provare e verificare

Avviare `AVVIA-POKEWILDS.cmd` dalla cartella completa. Le animazioni funzionano
anche sulle isole salvate. Per vedere la nuova disposizione strutturale serve
una nuova avventura o una nuova isola: i salvataggi non vengono rigenerati.

`tools/nature_animation_test.py` verifica il rendering nativo a tempi fissati,
continuità dei bordi, ancoraggio dell'erba, movimento ridotto e immutabilità
della mappa/RNG. `tools/authored_terrain_test.py` e `tools/modern_world_test.py`
verificano i moduli e la loro integrazione nella generazione reale; il secondo
produce anche immagini degli ambienti effettivamente generati.
Le regressioni della generazione includono pozze con incontri speciali prima
e dopo l'assemblaggio, e verificano che il raccordo dell'acqua ordinaria resti attivo.

La ricevuta della build consegnata e l'elenco delle prove eseguite sono in
`VERIFICA.txt`. Questa descrizione da sola non certifica una prova, una campagna
manuale completa o una pubblicazione su GitHub.
