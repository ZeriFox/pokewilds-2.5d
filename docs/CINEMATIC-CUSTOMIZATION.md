# Arene, costa e guardaroba

Questa revisione continua dalla build locale `ac200935`. Mantiene il mondo
procedurale esistente e aggiorna la sua presentazione; non richiede una nuova
isola né modifica il formato dei salvataggi.

## Lotta

I fondali dipinti occupano tutta la scena. Foresta, spiaggia, neve, deserto,
grotta, vulcano e cimitero hanno ambienti dedicati. Il pavimento e i personaggi
condividono una proiezione prospettica, con profondità diverse per gli elementi
vicini e lontani. La telecamera compie una rotazione contenuta durante la
scelta delle mosse; durante le azioni torna alla posizione neutra, mantenendo
gli effetti delle mosse allineati ai bersagli. Gli attori rimangono sprite 2D.

## Mondo e illuminazione

Le pozze e il velo d'acqua sulla sabbia usano bordi morbidi e collegamenti
derivati dalle celle bagnate adiacenti. L'acqua conserva onde, riflessi del cielo
e punti luce; i raccordi delle pareti e le ombre di contatto rendono più leggibili
le montagne. Sono effetti raster e shader: non è ray tracing hardware RTX.
La geometria visiva non modifica le collisioni né il ciclo delle maree.

Il fantasma notturno ha quattro fotogrammi dedicati, ispirati allo spettro
misterioso di Lavandonia. Il suo aspetto non rivela la specie nascosta;
apparizione, inseguimento, incontro e sparizione restano governati dalle azioni
originali.

## Apertura e interfaccia

Prima del titolo viene mostrata una sequenza di 7,2 secondi dalla spiaggia alla
radura, con il personaggio e un compagno. Il comando di conferma salta la
sequenza; una nuova pressione apre il menù. Disabilitare la scena iniziale
nelle impostazioni evita l'apertura. La preferenza di movimento ridotto evita
la sequenza e le animazioni di selezione. Le aree cliccabili dei menù rimangono
ferme durante le transizioni dei colori e degli indicatori.

## Guardaroba

Dal selettore allenatori scegli **Customize** oppure premi **Maiusc + Invio**.
In una partita già avviata apri il menù, premi **F1** e scegli **Customize character**.
Il guardaroba permette di cambiare vestito, zaino, scarpe, cappello e capelli,
con colori separati. Le alternative comprendono giacca/impermeabile, due zaini,
sneakers/stivali, berretto/cappello di lana e quattro acconciature.

Il tasto Tab gira l'anteprima. Conferma **Apply appearance** per conservare le
scelte; Esc annulla la bozza. **Original** ripristina la parte del personaggio
di partenza. I 19 personaggi rimangono disponibili; le mod grafiche installate
mantengono il proprio aspetto.

I profili cosmetici sono salvati localmente in `run-johto/appearance-profiles.json`,
con una copia `.bak`, separatamente dai salvataggi del mondo. Per trasferire
la personalizzazione su un altro PC bisogna copiare anche questo file.
La personalizzazione è locale e non viene trasmessa agli altri client multiplayer.

## Asset

`resources/visual/battle-scenes/` contiene i nuovi fondali e le sorgenti;
`resources/visual/ghost/` contiene il foglio dello spettro e la provenienza;
`resources/visual/wardrobe/` contiene i componenti del guardaroba.
Le nuove immagini sono state generate con lo strumento integrato ImageGen,
con prompt e riferimenti conservati nel progetto. Sono adattamenti fan art,
non sprite ufficiali estratti dai giochi Nintendo.

`VERIFICA.txt` identifica il JAR effettivamente compilato e i controlli eseguiti.
Le prove native si svolgono in directory isolate, senza aprire salvataggi personali.
