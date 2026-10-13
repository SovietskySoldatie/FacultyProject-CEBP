# FacultyProject-CEBP
Progetto CEBP (ro. PCBE) - Anno 4, Semestre 1

Questo è un progetto universitario per il corso di Programmazione Concorrente e Basata su Eventi (en. CEBP, ro. PCBE) presso l'Università Politehnica di Timișoara.
Questo progetto è concepito e destinato a essere completato da quattro studenti: il proprietario di questo repository (SovietskySoldatie) e tutti i codeowner (BiaG14, Luca-Adrian Mihut (lukettoOoO) & Tatiana Lazar (per1cle)).

Le specifiche originali del progetto si trovano nella cartella "original specs".
Le specifiche complete del progetto sono disponibili all'indirizzo https://docs.google.com/document/d/1L7y-9PixbeBWSjfRTmJsURariE3YkuhAN-OvhlYhYVU/edit?tab=t.0
I requisiti si trovano nella cartella "reqs".

## TO-DO:
- [] Aggiungere specifiche e requisiti
	- [x] Aggiungere le specifiche originali del progetto
	- [] Scomporre le specifiche del progetto
	- [] Aggiungere i requisiti
	- [] Scomporre i requisiti del progetto

- [] Definire l'architettura del progetto
	- [] Definire l'architettura software
		- [x] Creare la struttura dei package
		- [x] Creare le interfacce
		- [] Migliorare (?) l'architettura
			- [] Aggiungere documentazione
				- [] Aggiungere file .docx
				- [] Aggiungere diagrammi
			- [] Definire meglio la separazione della logica
			- [] Definire meglio le interfacce
			- [] Rendere a prova di futuro classi, interfacce, altri suggerimenti, ecc.
			- [] Mantenere l'architettura aggiornata

- [] Definire il design del software
	- TBD
- [] Implementare il codice
	- [x] Avvio iniziale (ramp-up)
		- [x] Implementare la logica di base dell'applicazione
		- [x] Implementare la logica relativa alle dipendenze
		- [x] Implementare la logica dello scheduler e della gestione delle risorse
		- [x] Implementare la logica delle richieste
	- [] Perfezionare e revisionare il codice
		- [] Verificare il componente Scheduler per la thread-safety
	- [] Creare una demo eseguibile
		- [] Preparativi per la demo
			- [] Estensioni delle simulazioni
				- [] Nelle dimensioni
				- [] Cercare di ottenere sovrapposizioni
				- [] Esecuzione quasi-infinita (mini-infinite run)
			- [] Introduzione di guasti (faults)
				- [] OfficeQueue :: blocking queue vs non-blocking :: incoerenza dei dati // race condition
				- [] Counter :: BusyWaiting :: il while gira continuamente // Luca ne sa di più
				- [] OfficialStamp :: mutex vs non-mutex :: ottenere 2 timbri per lo stesso documento
				- [] Config :: nessun controllo per cicli nel grafo // eventualmente collegare alla non-prontezza del grafo
				- [] Config + Scheduler :: Singleton non istanziato/istanza multipla :: race condition
				- [] Scheduler :: race conditions
				- [] Client/Scheduler + Config :: richiedere 2+ documenti contemporaneamente :: deadlock/livelock
				- [] Config + (forse) Office :: Collection requiredDocuments mutabile vs immutabile :: race condition :: anche deadlock (se il documento necessario viene cancellato prima che il valore venga trovato) // da verificare
		- [] Perfezionare la demo
	- [] Presentare la demo
- [] Implementare i test
	- [] Spostare i test nella cartella src/test/java
	- [] Aggiungere la documentazione dei test
		- TBD
