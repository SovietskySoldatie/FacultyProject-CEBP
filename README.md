# FacultyProject-CEBP — Bureaucracy System Coordinator

**Laboratory Project: Bureaucratic System Coordinator**  
**Stage 1: Concurrent Applications using Java Concurrency API**

---

## 🏛️ About the Project & Course

This repository contains the laboratory project implementation for the following academic course:

- **Course:** Concurrent and Event-Based Programming (CEBP / PCBE — _Programare Concurentă și Bazată pe Evenimente_)
- **Academic Year:** 2026/2027 — Year 4, Semester 1
- **Institution:** Politehnica University of Timișoara (UPT) — Faculty of Automatics and Computers, Department of Computer Science and Information Technology
- **Project Topic:** _Bureaucratic System Coordinator_
- **Stage 1 Objective:** Complete modeling and simulation of a complex bureaucratic system with concurrent processes in Java SE 25 LTS, utilizing solely the standard `java.util.concurrent` framework (without external synchronization libraries), alongside **deliberately flawed solutions (Faulty Implementations)** based on Dependency Injection to demonstrate classic concurrency bugs (Data Races, Lost Updates, Starvation, and Deadlocks).

---

## 👥 Team Members & Responsibilities

The project is developed by a team of 4 students, each owning a distinct, decoupled subsystem:

| Team Member | Role / Module | Java Package | Key Responsibilities & Components |
| :--- | :--- | :--- | :--- |
| **Tatiana Lazăr**<br>([@per1cle](https://github.com/per1cle)) | **Member 1**<br>Knowledge & Config | `bureaucraticSystem.config` | Immutable document model (`Document`), thread-safe document catalog (`DocumentRegistry`), recursive DAG route resolution (`RouteResolver`), cycle detection, and Singleton Race Condition scenario (`FaultyScheduler`). |
| **Luca-Adrian Mihuț**<br>([@lukettoOoO](https://github.com/lukettoOoO)) | **Member 2**<br>Counters & Queues | `bureaucraticSystem.counter` | Producer-Consumer office queues (`OfficeQueue`, `BlockingOfficeQueue`), concurrent service desks (`DeskWorker` with `DeskWorkerSafe` / `DeskWorkerUnsafe`), Worker Pool infrastructure, Graceful Shutdown, and unsynchronized queue data race (`FaultyOfficeQueue`). |
| **Vlad Iordăchescu**<br>([@SovietskySoldatie](https://github.com/SovietskySoldatie)) | **Member 3**<br>Clients & Dispatcher | `bureaucraticSystem.client`<br>`bureaucraticSystem.scheduler`<br>`bureaucraticSystem.demo` | Concurrent citizen lifecycle (`Client`), asynchronous ticket handshake (`ServiceTicket` via `CompletableFuture`), DCL Singleton central dispatcher (`Scheduler`), unified demonstration orchestrator (`Demo.java`), and automated test scripts. |
| **Bianca Grovu**<br>([@BiaG14](https://github.com/BiaG14)) | **Member 4**<br>Resources & Chaos | `bureaucraticSystem.resource` | Mutex-protected institutional resources (`OfficialStamp` with `SafeStamp` / `UnsafeStamp` / `DeadlockStamp`, `DocumentCounter` with `SafeCounter` / `UnsafeCounter`), stochastic break scheduler (`CoffeeBreakScheduler`), and Circular-Wait Deadlock. |

---

## 🚀 How-To-Run Guide

### 📋 Prerequisites

- **Java SE Development Kit (JDK):** Version 25 LTS (recommended) or minimum JDK 21 LTS.
- **Apache Maven:** Version 3.9+ installed and configured in your `PATH`.

Verify your installed versions:

```bash
java -version
mvn -version
```

---

### 1. Compiling the Project

From the project root directory:

```bash
mvn clean compile
```

---

### 2. Running the Unified Demonstration Engine (`Demo.java`)

All simulation scenarios and concurrency fault demonstrations are centralized within [`Demo.java`](src/main/java/bureaucraticSystem/demo/Demo.java).

#### A. Automated Runner Scripts

- **On Linux / macOS (Bash):**
  ```bash
  chmod +x run_all_demos.sh
  ./run_all_demos.sh
  ```
- **On Windows (PowerShell):**
  ```powershell
  .\run_all_demos.ps1
  ```

#### B. Direct Run with Build-Time Defaults

Runs the build-time default scenario (`selectedDemo = DemoVariant.ALL_GOOD`, `DEFAULT_CLIENT_COUNT = 10000`), or pass `ALL 100` to run all 5 scenarios sequentially with 100 concurrent citizens (intentionally leaving Deadlock last):

```bash
# Run all 5 scenarios sequentially with 100 citizens:
java -cp target/classes bureaucraticSystem.demo.Demo ALL 100

# Run with build-time defaults (ALL_GOOD, 10000 citizens):
java -cp target/classes bureaucraticSystem.demo.Demo
```

or via Maven:

```bash
mvn exec:java -Dexec.mainClass="bureaucraticSystem.demo.Demo" -Dexec.args="ALL 100"
```

#### C. Running Individual Scenarios (CLI Overrides)

**CLI Argument Syntax:**

```bash
java -cp target/classes bureaucraticSystem.demo.Demo <MODE> [CLIENT_COUNT] [GUARD_ACTIVE] [TIMEOUT_SECONDS]
```

1. **Fully Thread-Safe Simulation (All-Good / 100% Thread-Safe):**
   ```bash
   java -cp target/classes bureaucraticSystem.demo.Demo GOOD 10
   ```
2. **Fault 1: Singleton Dispatcher Race Condition (`FaultyScheduler`):**
   ```bash
   java -cp target/classes bureaucraticSystem.demo.Demo SCHEDULER 20
   ```
3. **Fault 2: Unsynchronized Queue Race Condition & Lost Tickets (`FaultyOfficeQueue`):**
   ```bash
   java -cp target/classes bureaucraticSystem.demo.Demo QUEUE 50
   ```
4. **Fault 3: Non-Atomic Counter Lost Updates (`UnsafeCounter` + `DeskWorkerUnsafe` + `UnsafeStamp`):**
   ```bash
   java -cp target/classes bureaucraticSystem.demo.Demo COUNTER 100
   ```
5. **Fault 4: Circular-Wait Deadlock on Shared Resources (`DeadlockStamp` + `UnsafeCounter`):**
   - _With Deadlock Guard enabled and defensive 5-second watchdog (automatic diagnosis):_
     ```bash
     java -cp target/classes bureaucraticSystem.demo.Demo DEADLOCK 10 true 5
     ```
   - _Without Deadlock Guard (demonstrates genuine indefinite thread hang):_
     ```bash
     java -cp target/classes bureaucraticSystem.demo.Demo DEADLOCK 10 false
     ```

---

### 3. Running Individual Member Test Suites

Each team member provided dedicated executable test classes for isolated module verification:

- **Member 1 (Tatiana) — Concurrent DAG Route Resolution (`TestMembru1`):**
  ```bash
  mvn exec:java -Dexec.mainClass="bureaucraticSystem.config.graph.TestMembru1"
  ```
- **Member 2 (Luca) — Parallel Worker Pool with 2 Desks (`TestMember2`):**
  ```bash
  mvn exec:java -Dexec.mainClass="bureaucraticSystem.counter.TestMember2"
  ```
- **Member 2 (Luca) — Unsynchronized Queue Data Race Demonstration (`TestMember2Fault`):**
  ```bash
  mvn exec:java -Dexec.mainClass="bureaucraticSystem.counter.TestMember2Fault"
  ```
- **Member 4 (Bianca) — Safe Mutex Resources with `tryLock` (`TestSafe`):**
  ```bash
  mvn exec:java -Dexec.mainClass="bureaucraticSystem.resource.TestSafe"
  ```
- **Member 4 (Bianca) — Unsafe Circular-Wait Deadlock (`TestFaults`):**
  ```bash
  mvn exec:java -Dexec.mainClass="bureaucraticSystem.resource.TestFaults"
  ```

---

## 📚 Documentation & Project Specifications

- **Original Project Specifications:** See [`original specs/specs.md`](original%20specs/specs.md).
- **Full Architecture, Specifications & Faults Analysis:** See [`reqs/reqs.md`](reqs/reqs.md) for the detailed 5-layer architecture, Mermaid sequence and state diagrams, 4-tier DAG routing model, and concurrency fault breakdowns.
- **Project Task Breakdown & Progress Tracking:** All tasks and deliverables are decoupled and maintained in a dedicated file:  
  👉 **[`TO-DO.md`](TO-DO.md)**

---

Kind regards,

**Scrum Master (SM):** Mura  
![Real Scrum Master — Mura](./.important/RealScrumBTW.jpeg)

**Product Owner / Project Manager (PO/PM):** Giany  
![Real Product Owner — Giany](./.important/RealProductOwnerBTW.jpeg)
