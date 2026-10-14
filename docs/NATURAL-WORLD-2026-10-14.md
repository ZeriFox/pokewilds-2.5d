# Revisione locale: ambiente, cavalcature e lotte

Base: commit `1f916e24033d70998b046252dc6bb4f2805a4b27`, release johto.5.
Questa iterazione viene compilata e verificata in locale. Non identifica una nuova release GitHub.

## Ambiente

I rialzi usano pareti di terra nei boschi e materiali rocciosi sulle montagne,
con un bordo erboso alla quota superiore, ombra alla base e angoli convessi
formati da sei segmenti. Le frecce e le strisce gialle sono rimosse. Le scale
conservano gradini, raccordi e quote della superficie realmente percorribile.
Le smussature rimangono entro il profilo della cella: le collisioni e la
geometria di cammino dei mondi già salvati conservano il loro significato.

Acqua e lava hanno texture dedicate, campionate in coordinate del mondo per
non ricominciare ad ogni tile. Il mare presenta creste, riflessi, onde lente,
schiuma e risacca sulla spiaggia. La lava presenta canali incandescenti tra
isole di crosta scura, con corrente e pulsazioni lente. La modalità movimento
ridotto rende stabili le animazioni ambientali. I biomi e le cascate
conservano la propria classificazione e le condizioni di attraversamento.

I tre nuovi materiali originali provengono dal tool integrato `image_gen`.
Originali e prompt esatti sono in `resources/visual/terrain-relief/sources/`;
`PROVENANCE.json` registra hash e trasformazioni. `prepare_terrain_relief.py
--check` ricostruisce e confronta i PNG a 128 pixel senza ridipingerli.

## Ride, Surf e Volo

Le cavalcature usano corpi seduti dedicati, nelle quattro direzioni, senza
tagliare le gambe dello sprite in cammino. I 19 personaggi mantengono testa,
volto, capelli e cappello risolti dall'aspetto corrente. Il corpo montato
riprende colori e materiali dell'abito: è una posa compatta, con dettagli
semplificati rispetto alla camminata. Anche i mod possono fornire pose
`ride`, `surf` e `fly` esplicite, con priorità sulla composizione generata.

Il sedile segue la cavalcatura e il Volo conserva l'altezza dell'azione
originale. La sagoma del Pokémon montato usa le animazioni di locomozione
senza restringersi per includere fotogrammi di attacco fuori misura.
Le posizioni effettive e le abilità restano quelle del gioco.

## Lotta

Il fondale è una panoramica periodica sul cilindro: la camera ruota per
360 gradi in 48 secondi. Pavimento, Pokémon ed effetti condividono l'orbita;
schede HP e comandi rimangono fissi. La texture del fondale non oscilla né
viene specchiata durante le mosse. La chiusura fra settori usa una breve
sfumatura; le illustrazioni esistenti vengono ripetute in tre settori,
quindi non si tratta di nuovi panorami unici a 360 gradi.

I metadati legacy di righe e inversioni si applicano soltanto a una copia
trasparente del bersaglio, senza campionare ambiente e HUD. Le particelle
aggiunte coprono 46 mosse con sequenze già presenti, in dieci famiglie: fuoco, acqua, elettrico,
erba, ghiaccio, spettro, psichico, roccia, veleno ed energia. Le mosse non
incluse conservano gli effetti precedenti proiettati nella camera attuale.
La tabella riconosce anche altri alias, che non introducono da soli nuove mosse.
Danni, catture, audio e durata delle azioni conservano le regole originali.

## Nuovo mondo e incontri

Nella schermata di creazione scegli **Local → New island → Game mode →
Creative** oppure **Adventure**, poi avvia. Creative fornisce materiali
illimitati e il catalogo Pokémon attraverso **menu partita → F1 → Creative
sandbox**. La preferenza si salva nel sidecar del mondo; caricare un mondo
esistente legge la sua preferenza. Host e Join conservano Adventure.
I dettagli sono in [CREATIVE-MODE.md](CREATIVE-MODE.md).

Regole ecologiche specifiche correggono 28 specie e relative famiglie,
fra acqua marina/dolce, neve, foresta, spiaggia e aree aride/vulcaniche.
La copertura dell'intero Pokédex resta parziale; i Pokémon già salvati e
gli incontri speciali non vengono spostati. Fonti e limiti sono in
[HABITAT-REFINEMENT.md](HABITAT-REFINEMENT.md).

## Verifiche

Le prove usano scene isolate e salvataggi generati automaticamente, senza
aprire quelli personali. `tools/verify_rework.py` include 41 suite native,
fra cui la nuova verifica di pose montate per tutto il cast. La ricevuta
`build/rework-verification/native.json` è valida soltanto con stato `passed`,
lo stesso commit e lo stesso JAR indicati in `VERIFICA.txt`.

Queste prove controllano geometria, pixel, GPU, input, persistenza e
progressione delle azioni; non equivalgono a una campagna manuale completa.
