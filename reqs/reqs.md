# Project Requirements Specification: Bureaucracy System Coordinator

**Course:** Concurrent and Event-Based Programming (CEBP / PCBE) — Year 4, Semester 1 (2026/2027)  
**Institution:** Politehnica University of Timișoara (UPT)  
**Project Topic:** Bureaucratic System Coordinator (_Coordonator de Sistem Birocratic_)  
**Target Environment:** Java SE 25 LTS, Apache Maven, Standard `java.util.concurrent` API

---

## 1. Project Overview & Objectives

The **Bureaucracy System Coordinator** is a multi-threaded Java simulation that models citizen navigation through an administrative institution with interdependent documents, multi-counter waiting queues, shared institutional resources, and stochastic service disruptions (coffee breaks).

In accordance with **Stage 1** laboratory requirements, the system provides:
1. **A Complete, Thread-Safe Working Solution:** Built exclusively with standard `java.util.concurrent` primitives (`LinkedBlockingQueue`, `ConcurrentHashMap`, `CompletableFuture`, `ReentrantLock(true)`, `ScheduledExecutorService`, `AtomicBoolean`, and `volatile` state flags).
2. **Intentional Concurrency Fault Implementations via Dependency Injection:** Each module exposes an abstraction (interface or base class) with two interchangeable implementations—a **Safe (Good)** implementation and a **Faulty (Bad)** implementation—allowing deterministic demonstration of **Data Races / State Corruption** and **Deadlock / Livelock / Starvation**.

---

## 2. High-Level Architecture & Component Map

The system is organized into 5 modular layers communicating via thread-safe abstractions and Dependency Injection:

```mermaid
flowchart TD
    subgraph ClientLayer["1. Client Layer (Concurrent Citizen Threads)"]
        C1["Citizen 1..N (50% target Pasaport)"]
        C2["Citizen N+1..2N (50% target PUZ)"]
    end

    subgraph RoutingLayer["2. Routing & Knowledge Layer (4-Tier DAG)"]
        RR["RouteResolver (Thread-Safe DAG Traversal)"]
        DR["DocumentRegistry (ConcurrentHashMap Catalog - 10 Documents)"]
        RR <--> DR
    end

    subgraph DispatchLayer["3. Central Dispatcher Layer (TicketDispatcher Interface)"]
        SCH["Scheduler (Singleton DCL) / FaultyScheduler (Check-then-Act Race)"]
    end

    subgraph OfficeLayer["4. Office Infrastructure (7 Offices / 10 Desks)"]
        O1["StareaCivila (1 Desk)"]
        O2["ANAF (2 Desks)"]
        O3["Politie (1 Desk)"]
        O4["EvidentaPopulatiei (2 Desks)"]
        O5["Primarie (1 Desk)"]
        O6["DirectiaSanatatePublica (1 Desk)"]
        O7["Pasapoarte (2 Desks)"]
    end

    subgraph ResourceLayer["5. Shared Resources & Disruption Scheduling"]
        STAMP["OfficialStamp (SafeStamp / UnsafeStamp)"]
        CNT["DocumentCounter (SafeCounter / UnsafeCounter)"]
        CBS["CoffeeBreakScheduler (ScheduledExecutorService)"]
    end

    C1 & C2 -->|"1. Query next prerequisite"| RR
    C1 & C2 -->|"2. Submit ServiceTicket"| SCH
    SCH -->|"3. Dispatch ticket to OfficeQueue"| O1 & O2 & O3 & O4 & O5 & O6 & O7

    O1 & O2 & O3 & O4 & O5 & O6 & O7 -.->|"Acquire / Release Stamp"| STAMP
    O1 & O2 & O3 & O4 & O5 & O6 & O7 -.->|"Acquire / Increment Counter"| CNT
    CBS -.->|"Inject Periodic Coffee Breaks"| O1 & O2 & O3 & O4 & O5 & O6 & O7
    O1 & O2 & O3 & O4 & O5 & O6 & O7 -->|"Asynchronous Completion Signal"| C1 & C2
```

---

## 3. Team Responsibilities & Ownership Matrix

| Team Member            | Module & Package                          | Key Components                                                                                                   | Concurrency Primitives & Patterns Utilized                                                                                     |
| :--------------------- | :---------------------------------------- | :--------------------------------------------------------------------------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------- |
| **Member 1 (Tatiana)** | `bureaucraticSystem.config`               | `Document`, `DocumentRegistry`, `RouteResolver`                                                                  | `ConcurrentHashMap`, defensive immutable `Set.copyOf`, 4-tier DAG validation, recursive prerequisite traversal                 |
| **Member 2 (Luca)**    | `bureaucraticSystem.counter`              | `OfficeQueue`, `BlockingOfficeQueue`, `FaultyOfficeQueue`, `DeskWorker`, `TestMember2`, `TestMember2Fault`       | `LinkedBlockingQueue` (Producer-Consumer) vs unsynchronized `LinkedList`, `volatile` visibility, Worker Pool, graceful shutdown|
| **Member 3 (Vlad)**    | `bureaucraticSystem.client` & `scheduler` | `Client`, `ServiceTicket`, `TicketDispatcher`, `Scheduler`, `FaultyScheduler`, `Demo`                            | `CompletableFuture<Boolean>` async handshake, `ConcurrentHashMap.newKeySet()`, DCL Singleton vs Check-Then-Act Race Condition |
| **Member 4 (Bianca)**  | `bureaucraticSystem.resource`             | `OfficialStamp`, `SafeStamp`, `UnsafeStamp`, `DocumentCounter`, `SafeCounter`, `UnsafeCounter`, `CoffeeBreakScheduler` | Fair `ReentrantLock(true)`, `tryLock(timeout)` deadlock avoidance vs blocking `lock()` circular wait, `ScheduledExecutorService` |

---

## 4. Dependency Injection Architecture (Safe vs. Faulty Implementations)

To cleanly isolate intentional concurrency faults from the working solution without duplicating business logic, components rely on polymorphic Dependency Injection across 4 subsystem interfaces:

```mermaid
classDiagram
    class TicketDispatcher {
        <<interface>>
        +registerOffice(String, OfficeQueue)
        +unregisterOffice(String)
        +getOfficeQueue(String) OfficeQueue
        +dispatch(ServiceTicket, String)
        +dispatch(ServiceTicket)
        +stop()
    }
    class Scheduler {
        <<Safe Implementation>>
        Double-Checked Locking Singleton with volatile instance
    }
    class FaultyScheduler {
        <<Faulty Implementation>>
        Unsynchronized Check-Then-Act causing multi-instantiation race
    }

    class OfficeQueue {
        +addTicket(ServiceTicket)
        +takeTicket() ServiceTicket
        +pollTicket(long, TimeUnit) ServiceTicket
        +size() int
    }
    class BlockingOfficeQueue {
        <<Safe Implementation>>
        LinkedBlockingQueue (Thread-Safe & Blocking)
    }
    class FaultyOfficeQueue {
        <<Faulty Implementation>>
        LinkedList (Unsynchronized & Non-Blocking)
    }

    class OfficialStamp {
        <<interface>>
        +acquireStamp()
        +releaseStamp()
        +tryAcquireStamp(long) boolean
        +simulateUsage(String, long)
    }
    class SafeStamp {
        <<Safe Implementation>>
        ReentrantLock(true) + tryLock(timeout)
    }
    class UnsafeStamp {
        <<Faulty Implementation>>
        Blocking lock() ignoring timeout
    }

    class DocumentCounter {
        <<interface>>
        +acquireCounter()
        +releaseCounter()
        +tryAcquireCounter(long) boolean
        +increment(String) int
        +getCount() int
    }
    class SafeCounter {
        <<Safe Implementation>>
        ReentrantLock(true) + synchronized increment
    }
    class UnsafeCounter {
        <<Faulty Implementation>>
        Non-atomic count++ causing Lost Updates
    }

    TicketDispatcher <|.. Scheduler
    TicketDispatcher <|.. FaultyScheduler
    OfficeQueue <|-- BlockingOfficeQueue
    OfficeQueue <|-- FaultyOfficeQueue
    OfficialStamp <|.. SafeStamp
    OfficialStamp <|.. UnsafeStamp
    DocumentCounter <|.. SafeCounter
    DocumentCounter <|.. UnsafeCounter

    DeskWorker --> OfficeQueue : injected via constructor
    DeskWorker --> OfficialStamp : injected via constructor
    DeskWorker --> DocumentCounter : injected via constructor
    Client --> TicketDispatcher : injected via constructor
```

---

## 5. Document Dependency Graph (DAG) Model

Documents cannot be obtained directly. Prerequisite relationships form a 4-tier Directed Acyclic Graph with 10 documents, featuring dual target documents (`Pasaport` and `PUZ`) ensuring all citizens traverse the exact same institutional offices:

```mermaid
flowchart TD
    subgraph Tier0["Tier 0: Acte de Baza (0 Preconditii)"]
        D1["CertificatNastere\n(StareaCivila)"]
        D2["AdeverintaVenit\n(ANAF)"]
        D3["CazierJudiciar\n(Politie)"]
        D4["AdeverintaMedicala\n(DirectiaSanatatePublica)"]
    end

    subgraph Tier1["Tier 1: Acte Intermediare"]
        D5["CarteIdentitate\n(EvidentaPopulatiei)\n[Req: Nastere + Cazier]"]
        D6["CertificatFiscal\n(Primarie)\n[Req: AdeverintaVenit]"]
        D7["AvizPsihologic\n(DirectiaSanatatePublica)\n[Req: AdeverintaMedicala]"]
    end

    subgraph Tier2["Tier 2: Acte Avansate"]
        D8["CertificatCasatorie\n(StareaCivila)\n[Req: Identitate + Fiscal]"]
        D9["AutorizatieMediu\n(Primarie)\n[Req: CertificatFiscal]"]
    end

    subgraph Tier3["Tier 3: Acte Finale Complexe (50% / 50% split)"]
        TARGET_PASAPORT["Pasaport\n(Pasapoarte)\n[Req: Casatorie, Venit, Cazier, AvizPsiho]"]
        TARGET_PUZ["PUZ\n(Pasapoarte)\n[Req: Mediu, Nastere, Medicala, Identitate]"]
    end

    D1 --> D5
    D3 --> D5

    D2 --> D6

    D4 --> D7

    D5 --> D8
    D6 --> D8

    D6 --> D9

    D8 --> TARGET_PASAPORT
    D2 --> TARGET_PASAPORT
    D3 --> TARGET_PASAPORT
    D7 --> TARGET_PASAPORT

    D9 --> TARGET_PUZ
    D1 --> TARGET_PUZ
    D4 --> TARGET_PUZ
    D5 --> TARGET_PUZ
```

### Shared Office Routing Guarantee:
Whether a citizen aims for `Pasaport` or `PUZ`, both routes require visiting **all 7 offices** (`StareaCivila`, `ANAF`, `Politie`, `EvidentaPopulatiei`, `Primarie`, `DirectiaSanatatePublica`, `Pasapoarte`), ensuring maximum concurrency, lock contention, and shared queue pressure.

---

## 6. Functional Requirements (FR)

### FR-1: Document & Catalog Management

- **FR-1.1:** The system shall define administrative documents as immutable objects (`Document`) containing:
  - Unique document ID (`String`)
  - Issuing office ID (`String`)
  - Immutable set of prerequisite document IDs (`Set<String>`)
- **FR-1.2:** The system shall maintain an in-memory catalog (`DocumentRegistry`) backed by a `ConcurrentHashMap` supporting concurrent insertions and thread-safe lock-free reads.
- **FR-1.3:** The system shall support dynamic registration of an arbitrary number of offices and documents.

### FR-2: Dependency Graph & Route Resolution

- **FR-2.1:** Given a client's currently owned document inventory and a designated target document, `RouteResolver` shall inspect the DAG and compute the immediate next prerequisite document that the client does not possess.
- **FR-2.2:** If a missing prerequisite itself has unmet prerequisites, the resolution shall recursively traverse deeper until a base document with zero prerequisites (or all prerequisites satisfied) is identified.
- **FR-2.3:** Route resolution shall be completely thread-safe, re-entrant, and read-only, detecting circular dependencies (`IllegalStateException`) without infinite recursion.

### FR-3: Office Waiting Lines & Buffering

- **FR-3.1:** Each administrative office shall have an associated queue (`OfficeQueue` / `BlockingOfficeQueue`).
- **FR-3.2:** The waiting line shall implement FIFO (First-In, First-Out) ordering for submitted `ServiceTicket` instances.
- **FR-3.3:** Adding a ticket (`addTicket`) shall never corrupt internal pointers when executed concurrently by multiple client threads.
- **FR-3.4:** Extracting a ticket (`takeTicket`) shall block if the queue is empty, placing the consuming counter thread in a zero-CPU `WAITING` state until a ticket arrives.

```mermaid
flowchart LR
    subgraph Producers["Producers (Concurrent Clients)"]
        C1["Client Thread 1"]
        C2["Client Thread 2"]
        C3["Client Thread 3"]
    end

    subgraph Queue["OfficeQueue Buffer (BlockingOfficeQueue)"]
        T1["Ticket A"]
        T2["Ticket B"]
        T3["Ticket C"]
    end

    subgraph Consumers["Consumers (Parallel Desk Workers)"]
        D1["DeskWorker 1 (Thread)"]
        D2["DeskWorker 2 (Thread)"]
    end

    C1 & C2 & C3 -->|"queue.put(ticket)"| Queue
    Queue -->|"queue.take() (Blocking WAITING)"| D1 & D2
```

### FR-4: Desk Workers & Service Counters

- **FR-4.1:** Each service counter within an office shall run on an independent thread (`DeskWorker`).
- **FR-4.2:** Multiple desk workers within the same office shall concurrently pull and service tickets from the single shared `OfficeQueue` without duplicate processing.
- **FR-4.3:** Desk workers shall process tickets through distinct phases:
  1. Check and wait for active coffee breaks to conclude.
  2. Simulate administrative validation and drafting (`Thread.sleep`).
  3. Acquire exclusive access to institutional resources (`OfficialStamp` and `DocumentCounter`).
  4. Signal the client that the document is complete (`ticket.markCompleted()`).
- **FR-4.4:** Calling `worker.stop()` shall set the `volatile boolean running` flag to `false` and automatically interrupt the worker thread (`workerThread.interrupt()`) to wake it immediately if blocked in `queue.takeTicket()`.

### FR-5: Client Request Lifecycle & Autonomous Progress

- **FR-5.1:** Each citizen shall be represented by an independent concurrent thread (`Client`).
- **FR-5.2:** Clients shall autonomously iterate through the bureaucratic pipeline until their final target document is obtained.
- **FR-5.3:** Communication between clients and desks shall occur via an asynchronous ticket handshake (`ServiceTicket`) containing a `CompletableFuture<Boolean>`.
- **FR-5.4:** The client thread must block efficiently (`waitForCompletion()`) without CPU polling while its ticket is being serviced.

### FR-6: Shared Institutional Resources (`OfficialStamp` & `DocumentCounter`)

- **FR-6.1:** The institution shall feature shared resources (`OfficialStamp` and `DocumentCounter`) accessed across counters via Dependency Injection.
- **FR-6.2:** Safe implementations (`SafeStamp`, `SafeCounter`) shall use fair locks (`ReentrantLock(true)`) and timed acquisition (`tryLock(timeout, TimeUnit.MILLISECONDS)`) with lock release backoff to prevent both starvation and circular-wait deadlocks.

### FR-7: Stochastic Disruptions (Coffee Breaks)

- **FR-7.1:** The system shall support spontaneous coffee breaks managed by `CoffeeBreakScheduler`.
- **FR-7.2:** Breaks shall be triggered periodically using a `ScheduledExecutorService`.
- **FR-7.3:** Desk break statuses shall be stored in a `ConcurrentHashMap<String, AtomicBoolean>`. When on break, a desk shall pause ticket service until the break duration elapses.

### FR-8: Central Request Dispatching

- **FR-8.1:** A central `Scheduler` (implemented as a thread-safe Singleton with Double-Checked Locking) shall mediate ticket submissions from clients to the appropriate `OfficeQueue`.

### FR-9: Graceful Shutdown

- **FR-9.1:** The application shall join all client threads, invoke `stop()` on desk workers (which self-interrupts waiting worker threads), shut down executor services, and exit cleanly without orphaned threads.

---

## 7. End-to-End Sequence of Execution

The following sequence diagram details the complete concurrent handshake across all 5 system layers:

```mermaid
sequenceDiagram
    autonumber
    actor Client as Citizen (Client Thread)
    participant RR as RouteResolver
    participant SCH as Scheduler (Singleton)
    participant Q as OfficeQueue (BlockingOfficeQueue)
    participant DW as DeskWorker (Worker Thread)
    participant CBS as CoffeeBreakScheduler
    participant Stamp as OfficialStamp (SafeStamp)
    participant Cnt as DocumentCounter (SafeCounter)

    Note over Client,DW: Phase 1: Route Resolution
    Client->>RR: resolveNextRequiredDocument(ownedDocs, "Pasaport")
    RR-->>Client: Returns Document ("CertificatNastere", officeId="StareaCivila")

    Note over Client,Q: Phase 2: Ticket Submission
    Client->>Client: Create ServiceTicket("Citizen-1", "CertificatNastere")
    Client->>SCH: dispatchTicket(ticket, "StareaCivila")
    SCH->>Q: addTicket(ticket) [queue.put]

    Note over Client: Citizen blocks in WAITING state<br/>via ticket.waitForCompletion()

    Note over Q,DW: Phase 3: Queue Service
    Q->>DW: takeTicket() wakes worker thread [queue.take]

    Note over DW,CBS: Phase 4: Disruption Check
    DW->>CBS: isOnBreak("Desk-StareaCivila")?
    opt If worker is on break
        DW->>DW: Thread.sleep(300) until break concludes
    end

    Note over DW,Cnt: Phase 5: Processing, Stamping & Counting
    DW->>DW: Thread.sleep(150) [Drafting document]
    DW->>Stamp: acquireStamp() & simulateUsage(deskId, 200ms)
    DW->>Cnt: tryAcquireCounter(300ms) & increment(deskId)
    DW->>Cnt: releaseCounter()
    DW->>Stamp: releaseStamp()

    Note over DW,Client: Phase 6: Handshake Completion
    DW->>Client: ticket.markCompleted() [CompletableFuture.complete(true)]
    Client-->>Client: Unblocks! Adds "CertificatNastere" to inventory
    Note over Client: Loops back to Phase 1 until "Pasaport" is owned
```

---

## 8. State Machine Models

### Desk Worker Lifecycle:

```mermaid
stateDiagram-v2
    [*] --> Starting: Thread instantiated
    Starting --> WaitingForTicket: run() loop entered

    WaitingForTicket --> CheckingBreak: Ticket received via queue.take()
    note right of WaitingForTicket: Thread state = WAITING\n(0% CPU consumption)

    CheckingBreak --> Resting: isOnBreak == true
    Resting --> CheckingBreak: Thread.sleep(300) finishes

    CheckingBreak --> DraftingDocument: isOnBreak == false
    DraftingDocument --> AcquiringResources: Redaction finished (150ms)

    AcquiringResources --> StampingAndCounting: Stamp & Counter locks acquired
    AcquiringResources --> BackoffRetry: tryAcquire timeout (releases 1st lock)
    BackoffRetry --> AcquiringResources: Retry after 50ms

    StampingAndCounting --> CompletingTicket: Stamp applied & counter incremented
    CompletingTicket --> WaitingForTicket: ticket.markCompleted()

    WaitingForTicket --> Stopped: stop() called (sets running=false & interrupts workerThread)
    Resting --> Stopped: interrupted during sleep
    DraftingDocument --> Stopped: interrupted during sleep
    Stopped --> [*]: Worker thread terminates cleanly
```

### Client Thread Lifecycle:

```mermaid
stateDiagram-v2
    [*] --> AnalyzingPrerequisites: Thread started
    AnalyzingPrerequisites --> ReadyToSubmit: Missing prerequisite found

    ReadyToSubmit --> QueuedAtOffice: ticket dispatched to Scheduler
    QueuedAtOffice --> AwaitingService: Ticket enqueued in OfficeQueue

    AwaitingService --> ProcessingByCounter: Worker took ticket
    note right of AwaitingService: Client thread blocked\nin ticket.waitForCompletion()

    ProcessingByCounter --> DocumentAcquired: ticket.markCompleted() signal received
    DocumentAcquired --> CheckingGoal: Add document to ownedDocs

    CheckingGoal --> AnalyzingPrerequisites: Target document not yet owned
    CheckingGoal --> MissionComplete: Target document acquired!
    MissionComplete --> [*]: Client thread terminates with SUCCESS
```

---

## 9. Non-Functional & Concurrency Requirements (NFR)

```mermaid
flowchart TD
    NFR["Core Non-Functional Requirements"]
    NFR --> NFR1["NFR-1: Thread Safety & Data Integrity"]
    NFR --> NFR2["NFR-2: Deadlock & Livelock Freedom"]
    NFR --> NFR3["NFR-3: Zero Busy-Waiting (Resource Efficiency)"]
    NFR --> NFR4["NFR-4: Memory Visibility (Java Memory Model)"]
    NFR --> NFR5["NFR-5: Proper Interruption Hygiene"]
    NFR --> NFR6["NFR-6: Strict JDK 25 & Standard API Adherence"]
```

- **NFR-1 (Thread Safety):** All shared state (`DocumentRegistry`, `BlockingOfficeQueue`, `CoffeeBreakScheduler`, `SafeStamp`, `SafeCounter`) must be thread-safe under unrestricted concurrent read/write operations.
- **NFR-2 (Deadlock Freedom):** When multiple locks (`OfficialStamp` and `DocumentCounter`) are acquired, safe implementations must use timed lock acquisition (`tryLock`) and release held locks on timeout to break Coffman's Hold-and-Wait and Circular-Wait conditions.
- **NFR-3 (Zero Busy-Waiting):** Threads waiting for work or signals must block using operating-system level thread suspension (`queue.take()`, `future.get()`).
- **NFR-4 (Memory Visibility):** Loop termination flags (`running`) and worker thread references (`workerThread`) must be `volatile`. Shared states must use atomic primitives (`AtomicBoolean`) or explicit locks.
- **NFR-5 (Interruption Hygiene):** When an `InterruptedException` is caught, any in-flight ticket is marked failed (`ticket.markFailed()`) so the client thread never hangs indefinitely, and the interrupt flag is restored via `Thread.currentThread().interrupt()`.
- **NFR-6 (Standard Platform):** Must compile and execute cleanly on Java 25 LTS using Apache Maven with zero external concurrency libraries.

---

## 10. Stage 1 Faulty Solutions (Intentional Concurrency Flaws Catalog)

The laboratory requirements specify implementing intentional concurrency flaws covering both **Data Races / Data Incoherence** and **Deadlock / Performance Issues** using Dependency Injection:

```mermaid
flowchart TD
    Faults["Stage 1 Intentional Concurrency Faults (Dependency Injection)"]

    subgraph Cat1["Category 1: Data Races & Data Incoherence"]
        F1["Member 2 Fault (Luca - Counter):\nFaultyOfficeQueue vs BlockingOfficeQueue\nUnsynchronized LinkedList & Non-blocking takeTicket()\n--> Race condition, lost updates (dropped tickets), premature null returns"]
        F2["Member 1 / Member 3 Faults (Config & Scheduler):\nUnsynchronized Registry / Routing / Dispatch State\n--> Inconsistent state visibility & race conditions"]
    end

    subgraph Cat2["Category 2: Deadlock, Livelock & Resource Starvation"]
        F3["Member 4 + Member 2 Fault (Bianca & Luca - Resource/Counter):\nUnsafeStamp + UnsafeCounter vs SafeStamp + SafeCounter\nOpposite lock acquisition order with blocking lock()\n--> Circular Wait Deadlock between Desks"]
    end

    Faults --> Cat1
    Faults --> Cat2
```

### Detailed Breakdown of Implemented Faults:

#### 1. Member 2 Fault (Luca): `FaultyOfficeQueue` vs. `BlockingOfficeQueue` (Data Race & Non-Blocking Incoherence)
- **Dependency Injection Hierarchy:** Base class `OfficeQueue` extended by `BlockingOfficeQueue` (Safe) and `FaultyOfficeQueue` (Faulty).
- **Fault Mechanism:**
  - Replaces `LinkedBlockingQueue` with an unsynchronized `java.util.LinkedList<ServiceTicket>`.
  - `takeTicket()` checks `if (unsafeList.isEmpty()) return null;` instead of blocking the consumer thread in `WAITING`.
- **Demonstrated Concurrency Failure (`TestMember2Fault` / `DemoVariant.RACE_CONDITION_QUEUE`):**
  - When concurrent producer threads insert tickets, unsynchronized mutations of `LinkedList` node pointers (`last.next`) and `size++` collide across CPU cores, dropping tickets silently (*Lost Updates*).
  - Calling `takeTicket()` on an empty queue returns `null` immediately instead of suspending the worker thread.

#### 2. Member 3 Fault (Vlad): `FaultyScheduler` vs. `Scheduler` (Singleton Multi-Instantiation Race Condition)
- **Dependency Injection Hierarchy:** `TicketDispatcher` interface implemented by `Scheduler` (DCL Safe Singleton) and `FaultyScheduler` (Faulty).
- **Fault Mechanism:**
  - `FaultyScheduler.getInstance(registry)` checks `if (instance == null)` without synchronization, and sleeps briefly before assigning `new FaultyScheduler(registry)`.
  - When clients and setup threads arrive concurrently (coordinated by `CyclicBarrier`), multiple threads simultaneously see `instance == null` and instantiate distinct `FaultyScheduler` objects.
- **Demonstrated Concurrency Failure (`DemoVariant.RACE_CONDITION_SCHEDULER`):**
  - The setup thread registers offices in one instance, while client threads query different instances that have empty office registries, causing tickets to be dropped.

#### 3. Member 4 Fault (Bianca): `UnsafeCounter` vs. `SafeCounter` (Lost Updates Race Condition)
- **Dependency Injection Hierarchy:** `DocumentCounter` interface implemented by `SafeCounter` (Safe) and `UnsafeCounter` (Faulty).
- **Fault Mechanism:**
  - `UnsafeCounter.increment(deskId)` performs `int current = this.count; current++; this.count = current;` without atomic primitives or synchronization.
- **Demonstrated Concurrency Failure (`DemoVariant.RACE_CONDITION_COUNTER`):**
  - Under heavy concurrent load across all 10 desks, simultaneous reads and writes to `count` overwrite each other, resulting in a recorded document count significantly lower than the actual documents issued.

#### 4. Member 4 & Member 2 Fault (Bianca & Luca): `UnsafeStamp` / `UnsafeCounter` vs. `SafeStamp` / `SafeCounter` (Circular Wait Deadlock)
- **Dependency Injection Hierarchy:**
  - `OfficialStamp` implemented by `SafeStamp` (`tryLock(timeout)`) and `UnsafeStamp` (blocking `lock()`).
  - `DocumentCounter` implemented by `SafeCounter` (`tryLock(timeout)`) and `UnsafeCounter` (blocking `lock()`).
- **Fault Mechanism:**
  - Desk 1 (`stampFirst = true`) acquires `OfficialStamp` first and then requests `DocumentCounter`.
  - Desk 2 (`stampFirst = false`) acquires `DocumentCounter` first and then requests `OfficialStamp`.
  - With `UnsafeStamp` and `UnsafeCounter`, both desks hold their first lock and block indefinitely waiting for the second lock (**Coffman Circular Wait Deadlock**).
  - With `SafeStamp` and `SafeCounter`, `tryAcquire` times out after 300ms, the desk releases its first lock in `finally`, backs off for 50ms, and retries—eliminating the deadlock completely.

---

## 11. Verification & Acceptance Criteria (AC)

```mermaid
flowchart LR
    AC1["AC-1: mvn clean compile\n(JDK 25 Bytecode Success)"] --> AC2["AC-2: TestMember2\n(Worker Pool & Clean Shutdown)"]
    AC2 --> AC3["AC-3: TestMember2Fault\n(DI Race Condition vs Safe Queue)"]
    AC3 --> AC4["AC-4: MainTest Execution\n(Full End-to-End Simulation)"]
    AC4 --> AC5["AC-5: Demo Integration Suite\n(All Safe & Faulty Scenarios)"]
```

| Criterion ID | Verification Procedure                | Expected Outcome                                                                                                            |
| :----------- | :------------------------------------ | :-------------------------------------------------------------------------------------------------------------------------- |
| **AC-1**     | Execute `mvn clean compile`           | Zero compiler errors on Java 25.                                                                                            |
| **AC-2**     | Execute `TestMember2` independently   | Worker pool of 2 desks processes 4 concurrent clients on a shared queue; `stop()` wakes and closes both desks cleanly.       |
| **AC-3**     | Execute `TestMember2Fault`            | Demonstrates dropped tickets on `FaultyOfficeQueue` vs 1000/1000 preserved tickets on `BlockingOfficeQueue`.                |
| **AC-4**     | Execute `MainTest` integration suite  | All 3 concurrent citizens acquire prerequisites and obtain final passport under coffee breaks; logs confirm clean shutdown. |
| **AC-5**     | Execute `Demo.main` suite             | Supports all demo variants, build-time defaults, deadlock guard toggle, and cleanly exits or demonstrates faults as configured.|

---

## 12. Unified Simulation & Demonstration Engine (`Demo.java`)

[`Demo.java`](src/main/java/bureaucraticSystem/demo/Demo.java) centralizes institutional assembly, dependency injection wiring, execution modes, and fault verification into a single orchestrator.

### 12.1 Available Demo Variants (`DemoVariant` Enum)
- `ALL_DEMOS`: Runs all scenarios sequentially, intentionally keeping `DEADLOCK` last.
- `ALL_GOOD`: The full safe system (all components thread-safe; 100% completion).
- `RACE_CONDITION_SCHEDULER`: Triggers `FaultyScheduler` singleton multi-instantiation.
- `RACE_CONDITION_QUEUE`: Triggers `FaultyOfficeQueue` linked-list mutation race.
- `RACE_CONDITION_COUNTER`: Triggers `UnsafeCounter` lost updates race.
- `DEADLOCK`: Triggers circular-wait deadlock on shared stamp and counter.

### 12.2 Build-Time Configuration & Defaults
Configurable at the top of [`Demo.java`](src/main/java/bureaucraticSystem/demo/Demo.java):
```java
private static DemoVariant selectedDemo = DemoVariant.ALL_DEMOS;
public static final int DEFAULT_CLIENT_COUNT = 100;
private static int defaultClientCount = DEFAULT_CLIENT_COUNT;
private static boolean deadlockGuardActive = false;
```
- **Zero-Argument CLI Execution:** Running `java -cp target/classes bureaucraticSystem.demo.Demo` executes `selectedDemo` with `defaultClientCount` and the build-time `deadlockGuardActive` setting.
- **Deadlock Guard:** When `false`, the main thread joins client threads indefinitely (`ct.join()`), demonstrating the unmitigated deadlock freeze. When `true`, a defensive timeout detects the deadlock, outputs the diagnostic report, and shuts down cleanly.

