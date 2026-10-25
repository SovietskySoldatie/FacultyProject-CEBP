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
