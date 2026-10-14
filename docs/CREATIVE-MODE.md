# Modalità Creativa

Alla creazione, scegli **New adventure**, lascia **Play mode: Local** e imposta
**Game mode: Creative** prima di **Begin adventure**. Il campo si cambia con
Invio, clic sinistro o A/D; **Adventure** resta la scelta iniziale. La scelta
vale per il nuovo mondo e non modifica una partita già aperta. **Continue**
carica la modalità salvata: non propone una nuova scelta.

Durante una partita **Local**, apri il menù con Invio, premi **F1** e scegli
**Creative sandbox** per accedere al catalogo o cambiare la modalità anche dopo
la creazione. La modalità Creativa abilita:

- Un catalogo di tutte le specie installate supportate, incluse quelle del Pokédex aggiuntivo.
- Ricerca per nome, livello da 1 a 100 e scelta della variante Shiny.
- Aggiunta immediata del Pokémon selezionato alla squadra. Con sei Pokémon la richiesta
  viene rifiutata senza sostituire nessuno: usa il normale comando **Pokemon > Drop** per
  lasciare un membro nel mondo e liberare un posto, poi riapri il catalogo.
- Materiali illimitati per le ricette, le costruzioni e le principali azioni di semina.
  Restano le normali regole di posizionamento e le abilità dei Pokémon: ad esempio usa
  **BUILD** con un Pokémon che possiede l'abilità per costruire.

Nel catalogo, clicca un controllo oppure usa **Tab** per passare al controllo successivo.
W/S scorrono le specie, A/D cambiano pagina; sul livello A/D diminuiscono/aumentano
il valore (Maiusc modifica di dieci livelli). Clicca **Search**, digita e premi Invio.
**Add to party** conferma l'aggiunta; Esc torna alle impostazioni.

Disattivando la modalità, gli oggetti realmente posseduti restano esattamente quelli
precedenti: le scorte illimitate sono virtuali e non vengono trasformate in pile permanenti.
I Pokémon aggiunti e le costruzioni effettuate rimangono nella partita.
Salva normalmente dal menù dell'avventura per conservare Pokémon, mondo e modalità.
Il piccolo file `creative-mode.json` nella cartella `<mondo>.sav` contiene solo
l'interruttore e l'identità del giocatore. Va trasferita tutta la cartella del salvataggio.
Non cambia il formato dei file esistenti; un file assente o malformato lascia la modalità
Avventura attiva. Le nuove avventure usano la modalità scelta alla creazione;
in assenza di una scelta esplicita usano Avventura.

La modalità è disabilitata nelle sessioni **Host** e **Join**. Nessuna scorta virtuale
viene inviata in rete o salvata nell'inventario reale. I Pokémon e gli oggetti già creati
in una partita locale non vengono cancellati se successivamente si usa quel salvataggio.

Verifica automatica: `python tools/creative_mode_test.py` sul JAR ricompilato.
`--sources` compila una copia diagnostica separata, senza cambiare il JAR distribuito.
Il test usa soltanto un mondo temporaneo in `build/creative-mode*`, controlla ricette e
consumo dei materiali, inventario originale, catalogo, limiti della squadra, rete,
menù effettivo e salvataggio/caricamento completo.
