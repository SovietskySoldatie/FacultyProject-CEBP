# FacultyProject-CEBP
CEBP (ro. PCBE) project - Year 4, Sem 1

This is a faculty project for the Event-Based & Concurrent Programming (en. CEBP, ro. PCBE) course at Politehnica University of Timișoara.
This project is designed and intended to be completed by four students, those being the owner of this repo (SovietskySoldatie) and all codeowners (BiaG14, Luca-Adrian Mihut (lukettoOoO) & Tatiana Lazar (per1cle)).

Project original specs may be found under the "original specs" folder.
The full project specifications may be found at https://docs.google.com/document/d/1L7y-9PixbeBWSjfRTmJsURariE3YkuhAN-OvhlYhYVU/edit?tab=t.0
Requirements may be found under the "reqs" folder.

---

## Current Code & Executable Demo Status

The simulation engine is centralized in [`Demo.java`](src/main/java/bureaucraticSystem/demo/Demo.java).

### Available Demo Variants (`DemoVariant`)
1. **`ALL_DEMOS`**: Runs all 5 scenarios in order, intentionally keeping `DEADLOCK` last.
2. **`ALL_GOOD`**: Full working solution with 100% thread-safe components (Scheduler + SafeStamp + SafeCounter + BlockingOfficeQueue).
3. **`RACE_CONDITION_SCHEDULER`**: Demonstrates the `FaultyScheduler` Singleton race condition when multiple threads instantiate the dispatcher simultaneously.
4. **`RACE_CONDITION_QUEUE`**: Demonstrates `FaultyOfficeQueue` producer-consumer race condition and lost updates on an unsynchronized `LinkedList`.
5. **`RACE_CONDITION_COUNTER`**: Demonstrates `UnsafeCounter` non-atomic increment collisions under concurrent multi-desk load.
6. **`DEADLOCK`**: Demonstrates Coffman circular-wait deadlock on shared resources (`UnsafeStamp` + `UnsafeCounter`).

### Build-Time Configuration Knobs
Set at the top of [`Demo.java`](src/main/java/bureaucraticSystem/demo/Demo.java):
```java
// Selected scenario to run when no CLI arguments are supplied
private static DemoVariant selectedDemo = DemoVariant.ALL_DEMOS;

// Default client count per scenario
public static final int DEFAULT_CLIENT_COUNT = 100;
private static int defaultClientCount = DEFAULT_CLIENT_COUNT;

// Deadlock guard: false = indefinite freeze on deadlock; true = defensive timeout & diagnostic report
private static boolean deadlockGuardActive = false;
```

### Running the Demo
From the project root:

```bash
# 1. Compile project
mvn compile

# 2. Run with build-time defaults (0 arguments) and dump output:
java -cp target/classes bureaucraticSystem.demo.Demo 2>&1 | Tee-Object -FilePath demo_output.log

# 3. Or run specific scenario with CLI overrides:
# Syntax: java -cp target/classes bureaucraticSystem.demo.Demo <MODE> [CLIENT_COUNT] [GUARD_ACTIVE] [TIMEOUT_SECONDS]
java -cp target/classes bureaucraticSystem.demo.Demo GOOD 10
java -cp target/classes bureaucraticSystem.demo.Demo SCHEDULER 20
java -cp target/classes bureaucraticSystem.demo.Demo QUEUE 50
java -cp target/classes bureaucraticSystem.demo.Demo COUNTER 100
java -cp target/classes bureaucraticSystem.demo.Demo DEADLOCK 10 true
java -cp target/classes bureaucraticSystem.demo.Demo DEADLOCK 10 true 5     # Guard activ cu timeout explicit de 5 secunde
java -cp target/classes bureaucraticSystem.demo.Demo DEADLOCK 10 5          # Prescurtat: durata numerica activeaza automat guard-ul
```



Kind regards,
![Real scrum master](./.important/RealScrumBTW.jpeg)

## TO-DOs:
- [x] Add specs & reqs
	- [x] Add project original specifications ([`original specs/specs.md`](original%20specs/specs.md))
	- [x] Decompose project specifications
	- [x] Add requirements ([`reqs/reqs.md`](reqs/reqs.md))
	- [x] Decompose project requirements
- [x] Define project architecture
	- [x] Define software architecture
		- [x] Create package structure
		- [x] Create interfaces (`TicketDispatcher`, `OfficialStamp`, `DocumentCounter`, `OfficeQueue`)
		- [x] Improve architecture
			- [x] Add diagrams (Mermaid diagrams in [`reqs.md`](reqs/reqs.md))
			- [x] Better define logic separation (5 modular layers)
			- [x] Better define interfaces (polymorphic Dependency Injection for faults)
			- [x] Keep architecture up-to-date
- [x] Implement code
	- [x] Initial ramp-up
		- [x] Implement base app logic
		- [x] Implement dependencies related logic (4-tier DAG with 10 documents)
		- [x] Implement scheduler & resource management logic
		- [x] Implement requests logic
	- [x] Polish and review code
		- [x] Verify Scheduler component for thread-safety (DCL Singleton, `volatile`, cancellation)
	- [x] Create an executable demo ([`Demo.java`](src/main/java/bureaucraticSystem/demo/Demo.java))
		- [x] Demo preparations
			- [x] Simulation extensions
				- [x] Scale in size (7 offices, 10 desks, 10 documents, configurable client counts up to large batches)
				- [x] Overlapping routing (Dual target documents: `Pasaport` & `PUZ` both traverse all 7 offices)
				- [x] Graceful shutdown and deadlock timeout guard
			- [x] Fault introductions (via Dependency Injection)
				- [x] OfficeQueue :: `FaultyOfficeQueue` (unsynchronized `LinkedList` data race & dropped tickets)
				- [x] Scheduler :: `FaultyScheduler` (Check-Then-Act Singleton race condition creating multiple instances)
				- [x] DocumentCounter :: `UnsafeCounter` (unsynchronized `count++` lost updates)
				- [x] Shared Resources :: `UnsafeStamp` + `UnsafeCounter` (Circular Wait Deadlock with reversed lock orders)
		- [x] Polish the demo (`DemoVariant` enum, build-time configurations, CLI flags, graceful resource teardown)
	- [] Present the demo (Oral evaluation - Week 5)
- [/] Implement tests
	- [x] Unit & Integration suites (`TestMember2`, `TestMember2Fault`, `MainTest`, `TestSafe`)
	- [] Move tests to `src/test/java` folder
	- [x] Add test & fault documentation in [`reqs.md`](reqs/reqs.md)
