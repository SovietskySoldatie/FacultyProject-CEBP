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

---

## 4. Current Code & Implementation Status (Stage 1)

All mandatory requirements for Stage 1 have been implemented, verified, and assembled into an integrated simulation engine ([`Demo.java`](../src/main/java/bureaucraticSystem/demo/Demo.java)):

### 4.1 Complete and Working Solution (`DemoVariant.ALL_GOOD`)
* **Institutional DAG:** 11 distinct administrative documents organized across 4 dependency tiers (9 shared intermediate prerequisites + 2 final target documents: **`Pasaport`** for 50% of clients and **`PUZ`** for 50% of clients, yielding 10 documents obtained per citizen). Both paths share the exact same prerequisite chain and traverse all 7 administrative offices.
* **7 Administrative Offices:** `StareaCivila`, `ANAF`, `Politie`, `EvidentaPopulatiei`, `Primarie`, `DirectiaSanatatePublica`, `Pasapoarte`.
* **10 Counter Workers:** Multi-counter offices (`ANAF`, `EvidentaPopulatiei`, `Pasapoarte`) operate concurrent desks implementing the [`DeskWorker`](../src/main/java/bureaucraticSystem/counter/DeskWorker.java) contract via [`DeskWorkerSafe`](../src/main/java/bureaucraticSystem/counter/DeskWorkerSafe.java), pulling from shared [`BlockingOfficeQueue`](../src/main/java/bureaucraticSystem/counter/BlockingOfficeQueue.java) instances.
* **Stochastic Disruptions:** Periodic coffee breaks managed by [`CoffeeBreakScheduler`](../src/main/java/bureaucraticSystem/resource/CoffeeBreakScheduler.java) backed by `ScheduledExecutorService` with atomic generation tracking.
* **Thread-Safe Shared Resources:** [`SafeStamp`](../src/main/java/bureaucraticSystem/resource/SafeStamp.java) and [`SafeCounter`](../src/main/java/bureaucraticSystem/resource/SafeCounter.java) use fair `ReentrantLock(true)` and `tryLock` with timed backoff, eliminating deadlocks.
* **Thread-Safe Dispatcher:** [`Scheduler`](../src/main/java/bureaucraticSystem/scheduler/Scheduler.java) implements Double-Checked Locking (DCL) with `volatile instance` and `CompletableFuture` signaling.

### 4.2 Faulty Solutions Catalog (Intentional Concurrency Errors via Dependency Injection)
1. **Singleton Race Condition (`DemoVariant.RACE_CONDITION_SCHEDULER`):**
   * Class: [`FaultyScheduler`](../src/main/java/bureaucraticSystem/scheduler/FaultyScheduler.java) (Check-Then-Act without synchronization on `getInstance()`).
   * Effect: Deterministically instantiates multiple dispatchers when client threads start simultaneously, causing office registrations to be scattered across distinct instances and dropping tickets.
2. **Queue Data Incoherence & Lost Updates (`DemoVariant.RACE_CONDITION_QUEUE`):**
   * Class: [`FaultyOfficeQueue`](../src/main/java/bureaucraticSystem/counter/FaultyOfficeQueue.java) (Unsynchronized `java.util.LinkedList`).
   * Effect: Concurrent producer/consumer operations corrupt internal linked-list pointers, causing dropped tickets and non-blocking null returns.
3. **Counter Lost Updates (`DemoVariant.RACE_CONDITION_COUNTER`):**
   * Classes: [`UnsafeCounter`](../src/main/java/bureaucraticSystem/resource/UnsafeCounter.java) (`count++` without atomicity or locking), [`DeskWorkerUnsafe`](../src/main/java/bureaucraticSystem/counter/DeskWorkerUnsafe.java) (omits lock acquisition before calling increment), and [`UnsafeStamp`](../src/main/java/bureaucraticSystem/resource/UnsafeStamp.java) (yields stamp access immediately without exclusive locking to maximize concurrent contention on the counter).
   * Effect: Concurrent registrations suffer write collisions, yielding a registered count significantly lower than the actual documents issued.
4. **Circular Wait Deadlock (`DemoVariant.DEADLOCK`):**
   * Classes: [`DeadlockStamp`](../src/main/java/bureaucraticSystem/resource/DeadlockStamp.java) (unconditional blocking `lock.lock()`) + [`UnsafeCounter`](../src/main/java/bureaucraticSystem/resource/UnsafeCounter.java) with blocking `lock.lock()`.
   * Effect: `Desk-ANAF-1` acquires Stamp then Counter (`stampFirst = true`); `Desk-ANAF-2` acquires Counter then Stamp (`stampFirst = false`), causing a classic Coffman circular-wait deadlock. Controlled by the configurable build-time `deadlockGuardActive` toggle and CLI watchdog timeout.

---

## 5. Team Members & Responsibilities Matrix

| Team Member | Module & Package | Key Responsibilities |
| :--- | :--- | :--- |
| **Tatiana Lazăr** ([@per1cle](https://github.com/per1cle)) | `bureaucraticSystem.config` | Immutable documents (`Document`), thread-safe catalog (`DocumentRegistry`), recursive DAG traversal (`RouteResolver`), and Singleton race condition fault (`FaultyScheduler`). |
| **Luca-Adrian Mihuț** ([@lukettoOoO](https://github.com/lukettoOoO)) | `bureaucraticSystem.counter` | Office queues (`OfficeQueue`, `BlockingOfficeQueue`), worker pool infrastructure (`DeskWorker`, `DeskWorkerSafe`), graceful termination, and unsynchronized queue fault (`FaultyOfficeQueue`). |
| **Vlad Iordăchescu** ([@SovietskySoldatie](https://github.com/SovietskySoldatie)) | `bureaucraticSystem.client`<br>`bureaucraticSystem.scheduler`<br>`bureaucraticSystem.demo` | Citizen lifecycle (`Client`), ticket handshake (`ServiceTicket` via `CompletableFuture`), DCL Singleton dispatcher (`Scheduler`), unified demonstration engine (`Demo.java`), and automated test scripts. |
| **Bianca Grovu** ([@BiaG14](https://github.com/BiaG14)) | `bureaucraticSystem.resource` | Shared institutional resources (`OfficialStamp` with `SafeStamp`/`UnsafeStamp`/`DeadlockStamp`, `DocumentCounter` with `SafeCounter`/`UnsafeCounter`), stochastic coffee breaks (`CoffeeBreakScheduler`), and Circular Wait Deadlock. |

---

## 6. How-To-Run & Verification Guide

### 6.1 Compilation
```bash
mvn clean compile
```

### 6.2 Running Automated Runner Scripts
- **Linux / macOS:**
  ```bash
  chmod +x run_all_demos.sh
  ./run_all_demos.sh
  ```
- **Windows PowerShell:**
  ```powershell
  .\run_all_demos.ps1
  ```

### 6.3 Unified Demo Engine CLI Syntax
```bash
java -cp target/classes bureaucraticSystem.demo.Demo <MODE> [CLIENT_COUNT] [GUARD_ACTIVE] [TIMEOUT_SECONDS]
```
- Fully Thread-Safe Simulation: `java -cp target/classes bureaucraticSystem.demo.Demo GOOD 10`
- Singleton Dispatcher Fault: `java -cp target/classes bureaucraticSystem.demo.Demo SCHEDULER 20`
- Queue Data Race Fault: `java -cp target/classes bureaucraticSystem.demo.Demo QUEUE 50`
- Counter Lost Updates Fault: `java -cp target/classes bureaucraticSystem.demo.Demo COUNTER 100`
- Circular Wait Deadlock (with guard): `java -cp target/classes bureaucraticSystem.demo.Demo DEADLOCK 10 true 5`

---

## 7. Related Documentation Files
- Detailed 5-layer software architecture, Mermaid diagrams, and in-depth fault specifications: 👉 **[`reqs/reqs.md`](../reqs/reqs.md)**
- Project task tracking and deliverables: 👉 **[`TO-DO.md`](../TO-DO.md)**
- Main project overview and setup: 👉 **[`README.md`](../README.md)**
