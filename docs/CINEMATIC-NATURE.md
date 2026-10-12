# Mare, chiome e scenari di battaglia

Questa revisione locale prosegue dalla build `f3b55100`. Il ramo
`backup/before-cinematic-nature-2026-10-12` e l'archivio
`baseline/before-cinematic-nature` conservano il punto precedente.

## Mondo

Il mare usa onde ampie sovrapposte a increspature e riflessi mobili. Il colore
varia anche al largo, dove non esiste una costa vicina. Le coordinate del mondo
mantengono il moto continuo fra i tile; la risacca e la sabbia umida continuano
a seguire le rive reali. Non vengono spostate superfici di collisione.

Le chiome degli alberi vivi del catalogo — alberi comuni, querce, pini, alberi
innevati e salici — oscillano sopra una base rigida. Tronchi e radici restano
fermi. Gli alberi secchi, carbonizzati e gli altri oggetti rigidi conservano
la propria forma. Bottintesta, taglio e crescita usano le rispettive animazioni
di azione; il vento non ne modifica i tempi.

Ombre morbide delle chiome seguono il terreno asciutto alla quota dell'albero.
Il loro contrasto tiene conto della luce del giorno; le luci locali rendono
più visibili anche i punti illuminati dentro l'ombra. Si tratta di ombre
approssimate per questa presentazione 2.5D, non di una simulazione completa
delle ombre di ogni oggetto.

## Battaglie

Il panorama ha un'animazione specifica per ambiente: acqua sulle coste,
nuvole e luce negli ambienti aperti, nebbia nel cimitero e riflessi caldi
nell'ambiente vulcanico. La camera è un movimento lento a parallasse del
fondale 2.5D, con profondità diverse fra primo piano e distanza.

Le pedane dei Pokémon e il terreno vicino restano fermi, insieme ai comandi,
ai combattenti e ai punti di applicazione delle mosse. La presentazione non
ruota gli sprite come modelli 3D. Lo shader del panorama viene ripristinato
prima di disegnare attori, effetti di lotta e interfaccia. Le texture dello
scenario sono riutilizzate fino al cambio di ambiente o dimensioni della finestra.

## Avvio e verifiche

Avviare `AVVIA-POKEWILDS.cmd` dalla cartella completa oppure usare il
collegamento desktop della prova locale. Questi effetti valgono anche sui
mondi già salvati: non serve rigenerare il terreno.

L'opzione di movimento ridotto congela vento, onde, effetti ambientali e
parallasse. Personaggi, mosse e regole di gioco continuano normalmente.

`tools/nature_animation_test.py` controlla mare aperto, rive, chiome, luci,
movimento ridotto e immutabilità di mappa/RNG. `tools/battle_scenery_test.py`
verifica gli scenari nativi, i punti fissi, le dimensioni della finestra,
il ripristino del batch e il ciclo di vita delle risorse. La ricevuta della
build consegnata e le altre prove eseguite sono in `VERIFICA.txt`.
