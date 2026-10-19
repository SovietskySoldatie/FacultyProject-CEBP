# Original Project Specifications

**Course:** Concurrent and Event-Based Programming (CEBP / PCBE)  
**Academic Year:** 2026/2027  
**Faculty:** Faculty of Automatics and Computers, Politehnica University of Timișoara (UPT)  

---

## 1. Chosen Topic: Bureaucratic System Coordinator

### English Description: Bureaucracy Manager
> A software system able to guide and assist clients that try to progress through the mind-numbing procedures of a classic bureaucratic system. A configuration specifies the existence of an arbitrary number of offices and any number of documents which may be required by customers. No documents may be obtained directly; to receive it, the customer must obtain a number of intermediate documents.
>
> The offices feature queues, and some offices have more than one counter for working with the public. At any time, a situation may occur where any of the counters be temporarily closed because of important coffee breaks.
>
> The application must simulate the whole system, based on a configuration information containing at least the following elements:
> - Offices and documents issued by them
> - Dependencies between documents (which documents are needed to receive another document)
>
> Customers will be simulated by threads.

### Romanian Description: Coordonator de sistem birocratic
> Un sistem software capabil să îndrume și să ajute clienții să treacă prin întortocheatele proceduri ale unui sistem birocratic clasic. Prin elemente de configurare, se specifică existența unui număr arbitrar de birouri și un număr oarecare de documente ce pot fi cerute de către clienți. Niciun document nu poate fi obținut direct; pentru a-l primi, clientul trebuie să obțină un număr de documente intermediare.
>
> La birouri sunt cozi, iar unele birouri au mai mult de un ghișeu care lucrează cu publicul. Poate să apară situația ca oricare din ghișee să fie temporar închise, la momente neprevăzute, din cauza pauzei de cafea.
>
> Aplicația trebuie să simuleze tot sistemul, bazându-se pe o informație de configurare care conține cel puțin următoarele elemente:
> - birourile și actele pe care le pot emite
> - dependențele între acte (de ce acte e nevoie pentru a primi un alt act)
>
> Clienții vor fi simulați prin fire de execuție.

---

## 2. Stage 1 General Tasks & Requirements

### Stage 1: Concurrent applications using the Java Concurrency API
The purpose of this stage is to familiarize students with the basic concepts related to concurrency:
* What concurrent applications are
* How concurrency issues or errors occur
* Thread safety
* Primitives and patterns of synchronization

Programs are written in **Java**, using the standard concurrency framework (`java.util.concurrent`).

### Mandatory Tasks:
1. **Complete and Working Solution:**
   * Implement a complete and functioning solution for the chosen topic.
   * You may use any tools during development, including AI code generation.
   * However, you must thoroughly understand the concepts and techniques the solution employs, and be able to reason about optimization, changes, and alternatives.
   * Students will be asked questions about the concurrency issues avoided, functionality, primitives, and concepts used.

2. **Faulty Solutions (Intentional Concurrency Errors):**
   * Implement **at least two faulty solutions** for the chosen topic.
   * The programs must contain **intentional errors relating to concurrency**:
     - **Solution 1:** Focuses primarily on **data races and data consistency**.
     - **Solution 2:** Focuses on issues like **deadlock, livelock, and performance (e.g. busy-waiting / thread starvation)**.
   * Students will be asked questions about concurrency issues, approaches for solving them, and synchronization primitives.

---

## 3. Milestones & Grading
* **Weeks 3 & 4:** Deadlines and intermediate milestones. Both sets of programs (working and faulty) must be submitted until Week 4.
* **Week 5:** Stage 1 wrap-up and student oral evaluation. Students receive individual grades based on their contribution, involvement, and answers to theoretical and practical questions.
* **Grade Weight:** Stage 1 counts as **35% of the total lab grade**.

