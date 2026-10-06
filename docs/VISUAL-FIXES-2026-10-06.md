# Correzioni visive del 6 ottobre 2026

Il campionamento flessibile e gia integrato in main (PR #1).

## Correzioni

- In battaglia, il Pokemon amico usa le pose PMD `up-right`, l'avversario `down-left`. La stessa selezione vale per Idle, Attack, Hurt, Sleep e l'uscita dalla Pokeball. Non viene ruotata artificialmente l'immagine; le rotazioni delle mosse restano di competenza delle azioni originali.
- Gli oggetti alti sono scalati uniformemente: il limite di altezza non comprime piu soltanto l'asse verticale.
- Le pareti dei dirupi usano l'intera regione `cliff`, non una delle sue celle da 16 pixel scelta in base alla coordinata Y.
- Le coperture dei bordi montuosi sono rettangoli disgiunti, con UV proporzionali. Gli angoli non sovrappongono piu due coperture sullo stesso piano. I lati esposti sono chiusi, quelli interni o condivisi con un dirupo adiacente sono omessi.
- Una sedia o una scrivania su `house5_floor1` non viene piu interpretata come una parete alta. Le pareti di caverna hanno copertura rocciosa anziche piastrelle chiare.

## Ambito

Nessuna modifica a collisioni, griglia logica, generazione, salvataggi o risorse PNG.
I dirupi mantengono l'altezza decorativa preesistente di 8 unita: NON viene introdotto un sistema globale di altitudini. Le rampe e il centro percorribile delle celle restano liberi.
Le scene ancora affidate al renderer classico e gli sprite speciali senza sostituzione B/W non sono trasformati da questa correzione.

## Verifica ripetibile

```text
python tools/test_flexible_sampling.py
python tools/test_visual_geometry.py
python build.py
python tools/landscape_smoke_test.py
python tools/pmd_battle_visual_test.py
```

Il test geometrico standalone e stato compilato con `javac --release 17` ed eseguito: 312 controlli superati.
Il workflow `Visual regression` verifica inoltre la compilazione completa e prova i renderer nativi con Xvfb/Mesa. Per l'esito effettivo fare riferimento al run del commit in uso, non alla semplice presenza del workflow.
Gli screenshot dei test sono negli artifact del run. Un test automatico non sostituisce la revisione visiva della scena specifica segnalata dall'utente.
