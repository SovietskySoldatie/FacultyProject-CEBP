# Project Requirements Specification: Bureaucracy System Coordinator

**Course:** Concurrent and Event-Based Programming (CEBP / PCBE) — Year 4, Semester 1 (2026/2027)  
**Institution:** Politehnica University of Timișoara (UPT)  
**Project Topic:** Bureaucratic System Coordinator (_Coordonator de Sistem Birocratic_)  
**Target Environment:** Java SE 25 LTS, Apache Maven, Standard `java.util.concurrent` API

---

## 2. High-Level Architecture & Component Map

The system is organized into modular components communicating via thread-safe abstractions and standard concurrency primitives:

```mermaid
flowchart TD
    subgraph ClientLayer["1. Client Layer (Concurrent Citizen Threads)"]
        C1["Citizen 1 (Thread)"]
        C2["Citizen 2 (Thread)"]
        C3["Citizen N (Thread)"]
    end

    subgraph RoutingLayer["2. Routing & Knowledge Layer (DAG)"]
        RR["RouteResolver (Thread-Safe DAG Traversal)"]
        DR["DocumentRegistry (ConcurrentHashMap Catalog)"]
        RR <--> DR
    end

    subgraph DispatchLayer["3. Central Dispatcher Layer"]
        SCH["Scheduler (Singleton Mediator)"]
    end

    subgraph OfficeLayer["4. Office & Counter Infrastructure (Worker Pool)"]
        subgraph OfficeCivil["Civil Registry Office"]
            Q_Civil["OfficeQueue (LinkedBlockingQueue)"]
            DW_Civil["DeskWorker 1 (Thread)"]
            Q_Civil --> DW_Civil
        end

        subgraph OfficeTax["Tax Authority (ANAF) Office"]
            Q_Tax["OfficeQueue (LinkedBlockingQueue)"]
            DW_Tax["DeskWorker 2 (Thread)"]
            Q_Tax --> DW_Tax
        end

        subgraph OfficePass["Passport Office"]
            Q_Pass["OfficeQueue (LinkedBlockingQueue)"]
            DW_Pass["DeskWorker 3 (Thread)"]
            Q_Pass --> DW_Pass
        end
    end

    subgraph ResourceLayer["5. Shared Resources & Disruption Scheduling"]
        STAMP["OfficialStamp (Fair ReentrantLock Mutex)"]
        CBS["CoffeeBreakScheduler (ScheduledExecutorService)"]
    end

    C1 & C2 & C3 -->|"Query next prerequisite"| RR
    C1 & C2 & C3 -->|"Submit ServiceTicket"| SCH
    SCH -->|"Dispatch ticket"| Q_Civil & Q_Tax & Q_Pass

    DW_Civil & DW_Tax & DW_Pass -.->|"Acquire / Release Exclusive Mutex"| STAMP
    CBS -.->|"Inject Periodic Breaks"| DW_Civil & DW_Tax & DW_Pass
    DW_Civil & DW_Tax & DW_Pass -->|"Asynchronous Completion Signal"| C1 & C2 & C3
```

---

## 3. Team Responsibilities & Ownership Matrix

| Team Member            | Module & Package                          | Key Components                                  | Concurrency Primitives Utilized                                                                          |
| :--------------------- | :---------------------------------------- | :---------------------------------------------- | :------------------------------------------------------------------------------------------------------- |
| **Member 1 (Tatiana)** | `bureaucraticSystem.config`               | `DocumentRegistry`, `RouteResolver`, `Document` | `ConcurrentHashMap`, immutable `Set.copyOf`, DAG cycle detection                                         |
| **Member 2 (Luca)**    | `bureaucraticSystem.counter`              | `OfficeQueue`, `DeskWorker`, `TestMember2`      | `LinkedBlockingQueue` (Producer-Consumer), `volatile` memory visibility, `InterruptedException` handling |
| **Member 3 (Vlad)**    | `bureaucraticSystem.client` & `scheduler` | `Client`, `Scheduler`, `ServiceTicket`          | `CompletableFuture<Boolean>`, thread lifecycles, Double-Checked Locking Singleton                        |
| **Member 4 (Bianca)**  | `bureaucraticSystem.resource`             | `OfficialStamp`, `CoffeeBreakScheduler`         | `ReentrantLock(true)` (fairness lock), `ScheduledExecutorService`, `AtomicBoolean`                       |

---

## 4. Document Dependency Graph (DAG) Model

Documents cannot be obtained directly. Prerequisite relationships form a Directed Acyclic Graph:

```mermaid
flowchart LR
    subgraph Prerequisites["Prerequisite Level 0 (Base Documents)"]
        BC["CertificatNastere (Civil Registry)"]
        IS["AdeverintaVenit (Tax Authority - ANAF)"]
    end

    subgraph Intermediate["Intermediate Level 1"]
        ID["CarteIdentitate (Police Department)"]
    end

    subgraph FinalDocs["Target Level 2 (Complex Document)"]
        PASS["Pasaport (Passport Office)"]
    end

    BC --> PASS
    IS --> PASS
    BC -.->|"Optional Prerequisite"| ID
    ID -.->|"Optional Prerequisite"| PASS

    classDef base fill:#d4edda,stroke:#28a745,stroke-width:2px;
    classDef finalDoc fill:#cce5ff,stroke:#004085,stroke-width:2px;
    class BC,IS base;
    class PASS finalDoc;
```

---

## 5. Functional Requirements (FR)

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
- **FR-2.3:** Route resolution shall be completely thread-safe, re-entrant, and read-only.

### FR-3: Office Waiting Lines & Buffering

- **FR-3.1:** Each administrative office shall have an associated thread-safe queue (`OfficeQueue`).
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

    subgraph Queue["OfficeQueue Buffer (LinkedBlockingQueue)"]
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
  3. Acquire exclusive possession of `OfficialStamp`.
  4. Signal the client that the document is complete (`ticket.markCompleted()`).
- **FR-4.4:** Workers shall support clean stopping through a `volatile boolean running` flag and thread interruption.

### FR-5: Client Request Lifecycle & Autonomous Progress

- **FR-5.1:** Each citizen shall be represented by an independent concurrent thread (`Client`).
- **FR-5.2:** Clients shall autonomously iterate through the bureaucratic pipeline until their final target document is obtained.
- **FR-5.3:** Communication between clients and desks shall occur via an asynchronous ticket handshake (`ServiceTicket`) containing a `CompletableFuture<Boolean>`.
- **FR-5.4:** The client thread must block efficiently (`waitForCompletion()`) without CPU polling while its ticket is being serviced.

### FR-6: Shared Official Stamp (Physical Resource Mutex)

- **FR-6.1:** The institution shall feature exactly one shared `OfficialStamp` resource shared among all offices and counters.
- **FR-6.2:** Only one counter may hold the stamp at any given instant.
- **FR-6.3:** Stamp acquisition shall use a fair mutual exclusion lock (`ReentrantLock(true)`) to prevent desk starvation.

### FR-7: Stochastic Disruptions (Coffee Breaks)

- **FR-7.1:** The system shall support spontaneous coffee breaks managed by `CoffeeBreakScheduler`.
- **FR-7.2:** Breaks shall be triggered periodically using a `ScheduledExecutorService`.
- **FR-7.3:** Desk break statuses shall be stored in a `ConcurrentHashMap<String, AtomicBoolean>`. When on break, a desk shall pause ticket service until the break duration elapses.

### FR-8: Central Request Dispatching

- **FR-8.1:** A central `Scheduler` (implemented as a thread-safe Singleton with Double-Checked Locking) shall mediate ticket submissions from clients to the appropriate `OfficeQueue`.

### FR-9: Graceful Shutdown

- **FR-9.1:** The application shall join all client threads, signal desk workers to terminate, interrupt sleeping workers, shut down executor services, and exit cleanly without orphaned daemon threads.

---

## 6. End-to-End Sequence of Execution

The following sequence diagram details the complete concurrent handshake across all 5 system layers:

```mermaid
sequenceDiagram
    autonumber
    actor Client as Citizen (Client Thread)
    participant RR as RouteResolver
    participant SCH as Scheduler (Singleton)
    participant Q as OfficeQueue (LinkedBlockingQueue)
    participant DW as DeskWorker (Worker Thread)
    participant CBS as CoffeeBreakScheduler
    participant Stamp as OfficialStamp (Fair Lock)

    Note over Client,DW: Phase 1: Route Resolution
    Client->>RR: resolveNextRequiredDocument(ownedDocs, "Pasaport")
    RR-->>Client: Returns Document ("CertificatNastere", officeId="StareaCivila")

    Note over Client,Q: Phase 2: Ticket Submission
    Client->>Client: Create ServiceTicket("Citizen-1", "CertificatNastere")
    Client->>SCH: submitTicket(ticket)
    SCH->>Q: addTicket(ticket) [queue.put]

    Note over Client: Citizen blocks in WAITING state<br/>via ticket.waitForCompletion()

    Note over Q,DW: Phase 3: Queue Service
    Q->>DW: takeTicket() wakes worker thread [queue.take]

    Note over DW,CBS: Phase 4: Disruption Check
    DW->>CBS: isOnBreak("Desk-StareaCivila")?
    opt If worker is on break
        DW->>DW: Thread.sleep(300) until break concludes
    end

    Note over DW,Stamp: Phase 5: Processing & Stamping
    DW->>DW: Thread.sleep(150) [Drafting document]
    DW->>Stamp: useStamp(deskId, duration=200ms)
    activate Stamp
    Stamp-->>DW: Lock acquired exclusively
    DW->>DW: Apply stamp
    DW->>Stamp: Release lock
    deactivate Stamp

    Note over DW,Client: Phase 6: Handshake Completion
    DW->>Client: ticket.markCompleted() [CompletableFuture.complete(true)]
    Client-->>Client: Unblocks! Adds "CertificatNastere" to inventory
    Note over Client: Loops back to Step 1 until "Pasaport" is owned
```

---

## 7. State Machine Models

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
    DraftingDocument --> WaitingForStamp: Redaction finished (150ms)

    WaitingForStamp --> Stamping: Lock acquired exclusively
    note right of WaitingForStamp: Fair ReentrantLock queue

    Stamping --> CompletingTicket: Stamp applied (200ms)
    CompletingTicket --> WaitingForTicket: ticket.markCompleted()

    WaitingForTicket --> Stopped: stop() called + interrupt()
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

## 8. Non-Functional & Concurrency Requirements (NFR)

```mermaid
flowchart TD
    NFR["Core Non-Functional Requirements"]
    NFR --> NFR1["NFR-1: Thread Safety & Data Integrity"]
    NFR --> NFR2["NFR-2: Deadlock & Livelock Freedom"]
    NFR --> NFR3["NFR-3: Zero Busy-Waiting (Resource Efficiency)"]
    NFR --> NFR4["NFR-4: Memory Visibility (Java Memory Model)"]
    NFR --> NFR5["NFR-5: Proper Interruption Handling"]
    NFR --> NFR6["NFR-6: Strict JDK 25 & Standard API Adherence"]
```

- **NFR-1 (Thread Safety):** All shared state (`DocumentRegistry`, `OfficeQueue`, `CoffeeBreakScheduler`) must be thread-safe under unrestricted concurrent read/write operations.
- **NFR-2 (Deadlock Freedom):** Lock acquisition must follow strict hierarchical ordering. No thread shall hold multiple locks out-of-order.
- **NFR-3 (Zero Busy-Waiting):** Threads waiting for work or signals must block using operating-system level thread suspension (`queue.take()`, `future.get()`). No unpaused `while (condition)` polling loops are permitted.
- **NFR-4 (Memory Visibility):** Loop termination flags (`running`) must be `volatile`. Shared states must use atomic primitives (`AtomicBoolean`) or synchronizers to avoid CPU cache inconsistencies across L1/L2 caches.
- **NFR-5 (Interruption Hygiene):** When an `InterruptedException` is caught, the interrupt status must be restored via `Thread.currentThread().interrupt()`.
- **NFR-6 (Standard Platform):** Must compile and execute cleanly on Java 25 LTS using Apache Maven with zero external concurrency libraries.

---

## 9. Stage 1 Faulty Solutions (Intentional Flaws Catalog)

The lab guidelines require implementing at least two deliberately flawed solutions for oral evaluation:

- **Solution 1:** Focuses on **Data Races & State Corruption**.
- **Solution 2:** Focuses on **Deadlock, Livelock & Performance Starvation**.

```mermaid
flowchart TD
    Faults["Laboratory Intentional Faults Catalog"]

    subgraph Cat1["Category 1: Data Races & State Corruption"]
        F1["Fault 1 (Luca - Counter): Unsynchronized Queue\n(LinkedList instead of LinkedBlockingQueue)\n--> Corrupted nodes, dropped tickets, NullPointerExceptions"]
        F3["Fault 3 (Bianca - Resource): Unprotected Stamp\n(Omit lock.lock/unlock)\n--> 2 desks stamp simultaneously, race condition"]
        F5["Fault 5 (Tatiana/Vlad - Config/Scheduler): Unsafe Singleton\n(Non-synchronized getInstance)\n--> Dual instances created, split queues"]
    end

    subgraph Cat2["Category 2: Deadlock, Livelock & Performance Starvation"]
        F2["Fault 2 (Luca - Counter): Busy-Waiting Loop\n(Continuous queue.poll in unpaused while loop)\n--> 100% CPU core utilization, CPU starvation"]
        F4["Fault 4 (Tatiana - Config): Unchecked Graph Cycles\n(Circular prerequisites A -> B -> A)\n--> Infinite recursive route search / StackOverflowError"]
        F6["Fault 6 (Vlad - Client/Scheduler): Inverted Lock Deadlock\n(Simultaneous dual document requests with inverted locks)\n--> Coffman circular wait deadlock"]
    end

    Faults --> Cat1
    Faults --> Cat2
```

### Detailed Breakdown of Member 2 (Luca's) Faults:

#### Fault 1: Non-Thread-Safe Queue (`OfficeQueue`)

- **Mechanism:** Replace `LinkedBlockingQueue` with a standard `java.util.LinkedList<ServiceTicket>`.
- **Concurrency Issue:** `LinkedList` is not synchronized. When multiple concurrent clients invoke `addTicket()`:
  - Concurrent updates to `node.next` and `node.prev` cause pointer corruption.
  - Non-atomic `size++` leads to lost writes.
  - Tickets are dropped silently or throw `ConcurrentModificationException` / `NullPointerException`.

#### Fault 2: Busy-Waiting & CPU Starvation (`DeskWorker`)

- **Mechanism:** Replace blocking `queue.takeTicket()` with non-blocking `queue.pollTicket()` inside a tight `while (running)` loop with no `Thread.sleep` or wait.
- **Concurrency Issue:**
  - When no tickets are queued, the worker thread spins billions of times per second.
  - The thread remains in `RUNNABLE` state, consuming **100% of a CPU core**.
  - Result: Extreme energy waste, processor thermal throttling, and **Thread Starvation** for client threads and other counters.

---

## 10. Verification & Acceptance Criteria (AC)

```mermaid
flowchart LR
    AC1["AC-1: mvn clean compile\n(JDK 25 Bytecode Success)"] --> AC2["AC-2: TestMember2\n(Isolated Desk/Queue Validation)"]
    AC2 --> AC3["AC-3: MainTest Execution\n(Full End-to-End Simulation)"]
    AC3 --> AC4["AC-4: Performance Check\n(0% Idle CPU Verification)"]
    AC4 --> AC5["AC-5: Mutex Verification\n(Strict Stamp Serialization)"]
```

| Criterion ID | Verification Procedure                | Expected Outcome                                                                                                            |
| :----------- | :------------------------------------ | :-------------------------------------------------------------------------------------------------------------------------- |
| **AC-1**     | Execute `mvn clean compile`           | Zero compiler warnings/errors on Java 25.                                                                                   |
| **AC-2**     | Execute `TestMember2` independently   | Desk opens, 3 mock clients served in sequence, clean shutdown in `< 2s`.                                                    |
| **AC-3**     | Execute `MainTest` integration suite  | All 3 concurrent citizens acquire prerequisites and obtain final passport under coffee breaks; logs confirm clean shutdown. |
| **AC-4**     | Inspect OS process thread activity    | Idle desk workers register `0.0% CPU` in `WAITING` state.                                                                   |
| **AC-5**     | Log timeline audit on `OfficialStamp` | Stamp acquisition logs prove strictly non-overlapping intervals between desks.                                              |
