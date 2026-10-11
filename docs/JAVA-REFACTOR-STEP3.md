# Java / passaggio 3 — interfaccia e controllo esplicito

Base remota: `500a9c9c6bf14bf1b407a476b44ea2f6cc562365`.
Nessun nuovo motore, nessuna migrazione Unity. Codice del gioco Java/LibGDX e strumenti Python.

## Interfaccia iniziale

La scena titolo usa asset già inclusi, bande di parallasse leggere, un allenatore e un Pokémon animato. È saltata immediatamente con conferma/Invio; non simula una partita né consuma RNG. Seguono Nuova avventura, Continua, Online, Controlli e schermo. Non è una campagna introduttiva narrativa completa o un filmato importato dai giochi commerciali.

La configurazione dell'avventura usa gli stessi hitbox per il disegno e i clic. Il controller gestisce soltanto selezione, dati della UI e attivazione: la conferma finale delega il ramo Go di `DrawSetupMenu` originale, mantenuto byte-identico. Restano così originali generazione, rete, avvisi e caricamento dei salvataggi.

## Navigazione e scrittura sono modalità diverse

WASD (o i comandi assegnati) continua a navigare anche sopra Nome, Salvataggio e Indirizzo. Per scrivere occorre confermare quel campo. Durante la modifica, le lettere sono testo e non muovono la selezione; le frecce spostano il cursore. Invio/clic sinistro conferma, Esc/destro annulla, Tab conferma e passa al campo seguente. Le cancellazioni non vengono applicate due volte da keyDown/keyTyped. Il vecchio limite di 11 caratteri per nome/salvataggio rimane; l'indirizzo IPv4 è validato.

Una barriera di rilascio impedisce che il tasto o il clic tenuto nel cambio di contesto produca una seconda azione; la perdita del focus annulla una bozza di testo e blocca gli input della finestra inattiva. Le schermate originali di soprannomi/cartelli restano delegate ai loro listener, non cancellate.

## Controlli e preferenze

F1 nel menu iniziale o di pausa apre una schermata separata. Nove azioni possono essere assegnate a tastiera o pulsanti sinistro/destro del mouse. I conflitti sono rifiutati; Esc, F1 e Tab restano percorsi di uscita/navigazione. Le frecce sono riservate alle rispettive direzioni e Invio alla funzione menu, evitando alias contraddittori. Le modifiche sono una bozza: Salva le applica; Esc/Annulla non cambia i comandi attivi. Ripristina prepara i valori predefiniti, sempre da confermare con Salva.

Si possono disattivare scena iniziale, movimento dei menu e accenti ambientali. `interface-settings.json` è un file locale, separato dai salvataggi e dal vecchio `settings.txt`, scritto con sostituzione atomica dove supportata e backup. Non contiene credenziali. In caso di configurazione invalida i comandi precedenti restano attivi e l'errore è registrato.

## Allenatori: anteprima veritiera

Sono disponibili due presentazioni dettagliate B/W e **19 identità grafiche classiche** già complete negli asset originali, inclusa Lyra prima non esposta nella selezione. Non vengono spacciate per 19 nuovi personaggi HD: il cast classico conserva il proprio stile più piccolo, ingrandito senza interpolazione. Il catalogo mantiene direzioni e colore dell'abito; anche mod locali completi già elencati dal setup rimangono selezionabili. I Pokémon PMD non sono modificati.

Il cache del catalogo possiede solo le texture che genera; non rilascia quelle di Player/TextureCache. Solo il riquadro selezionato anima la camminata; riduci movimento lo ferma. L'aspetto viene applicato solo dopo conferma e il tipo classico/dettagliato è una preferenza di presentazione locale, non un campo nuovo nel salvataggio di Player.

## Inventario

Cinque tasche con selezione a mouse, lista e quantità in colonne distinte, indicatore di scorrimento e pannello descrizione. Descrizioni lunghe consultabili con i due comandi tab (C/V predefiniti) o rotella sopra il pannello, senza cambiare oggetto. Rotella sulla lista naviga. Il primo clic su una riga diversa la seleziona; un clic successivo la apre. Margini/descrizioni non attivano Usa/Getta. Il comando menu originale attiva il riordino e il destro lo annulla senza chiudere l'intero inventario.

`DrawItemMenuGen2`, le operazioni di inventario/crafting, i valori degli oggetti, il formato dei salvataggi e le formule delle lotte non vengono riscritti.

## Verifica e limiti

`tools/desktop_menu_flow_test.py` prova eventi e polling reali del controller contro il JAR, con immagini native e controlli di focus, tasti tenuti, testo/cursore, conflitti/persistenza, identità degli allenatori, clic inventario e annullamento riordino. `ui_layout_test.py` conserva tutti i controlli precedenti e aggiunge titolo/home/allenatori/opzioni alle risoluzioni già testate. La suite complessiva include ora **25 runner nativi**. I risultati effettivi sono nelle ricevute: questo documento non certifica da solo un'esecuzione.

Il collaudo automatico non è una partita manuale completa, né verifica tutte le periferiche fisiche o il multiplayer. Il portatile Windows viene generato soltanto dopo compilazione e suite completa con ricevute associate al JAR/commit. Un eventuale test di avvio Windows va documentato separatamente. Non viene creata automaticamente una release pubblica con asset di terzi: il pacchetto di collaudo è una consegna separata, con crediti conservati.
