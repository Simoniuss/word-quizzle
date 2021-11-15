# Word Quizzle
 Implementazione del progetto per il corso di "Reti di Calcolatori e Laboratorio" 2019-20 presso l'Università di Pisa. Il progetto prevede l'implementazione di un gioco con un sistema di sfide di traduzioni italiano-inglese.


 ##  Table of Contents
 * [Introduction](#introduction)
 * [Installation](#installation)
 * [Usage](*usage)


 ## Introduction
Word Quizzle ha un'architettura client-server che permette a due giocatori di sfidarsi a una gara di traduzioni italiano-inglese. L'utente interagisce tramite un'interfaccia grafica che comunica con il server. La fase di regsitrazione di un nuovo giocatore è implementata tramite RMI. Tutte le altre connessioni sono TCP.  
Le possibili parole sulle quali sfidarsi sono insrite all'interno del file _Dictionary.txt_. Per ogni sfida il server seleziona K parole dal dizionario e tramite richiesta HTTP traduce le parole appoggiandosi al servizio [Memory Translated](​https://mymemory.translated.net/doc/spec.php​).  
Per maggiori dettagli sull'implementazione fare riferimento al file _Relazione Word Quizzle_.


 ## Installation
```
$ git clone https://github.com/Simoniuss/word-quizzle
$ cd word-quizzle
$ javac -cp .:./api/:./api/gson-2.8.6.jar MainWQS.java
$ javac -cp .:./api/:./api/gson-2.8.6.jar WQClient.java
```


 ## Usage
 Avviare il server:
```
java -cp .:./api/:./api/gson-2.8.6.jar MainWQS &
```
Avviare il client:
```
java -cp .:./api/:./api/gson-2.8.6.jar WQClient
```