package bureaucraticSystem.counter;

import bureaucraticSystem.client.request.ServiceTicket;

import java.util.ArrayList;
import java.util.List;

/**
 * Test demonstrativ pentru validarea FAULT-ului asumat de Membru 2:
 * "OfficeQueue :: blocking queue vs non-blocking :: data incoherence // race condition"
 *
 * Utilizează DEPENDENCY INJECTION:
 *  - Injectăm FaultyOfficeQueue în locul cozii standard.
 *  - Lansăm mai mulți clienți simultan pe fire de execuție paralele.
 *  - Demonstrează în mod practic apariția fenomenului de Race Condition,
 *    pierderea de tichete (Lost Updates) și inconsistența datelor.
 */
public class TestMember2Fault {

    public static void main(String[] args) throws Exception {
        System.out.println("=== DEMONSTRAȚIE FAULT MEMBRU 2: Data Incoherence & Race Condition ===\n");

        int numThreads = 10;
        int ticketsPerThread = 100;
        int totalExpectedTickets = numThreads * ticketsPerThread; // 1000 tichete

        // -------------------------------------------------------------
        // TESTUL 1: Rularea cu Clasa REA (FaultyOfficeQueue) via Dependency Injection
        // -------------------------------------------------------------
        System.out.println("--- Testare cu FaultyOfficeQueue (Listă nesincronizată) ---");
        OfficeQueue faultyQueue = new FaultyOfficeQueue(); // <-- DEPENDENCY INJECTION a clasei cu fault

        List<Thread> producers = new ArrayList<>();
        for (int t = 1; t <= numThreads; t++) {
            final int threadId = t;
            Thread producer = new Thread(() -> {
                for (int i = 1; i <= ticketsPerThread; i++) {
                    try {
                        faultyQueue.addTicket(new ServiceTicket("Client-T" + threadId + "-" + i, "Certificat"));
                    } catch (Exception e) {
                        System.err.println("Eroare la adăugare în FaultyQueue: " + e);
                    }
                }
            });
            producers.add(producer);
            producer.start();
        }

        for (Thread p : producers) {
            p.join();
        }

        System.out.println("Tichete așteptate: " + totalExpectedTickets);
        System.out.println("Tichete găsite în FaultyQueue: " + faultyQueue.size());

        if (faultyQueue.size() != totalExpectedTickets) {
            System.out.println("🔴 REZULTAT FAULT DETECTAT: S-au pierdut "
                    + (totalExpectedTickets - faultyQueue.size())
                    + " tichete din cauza lipsei de sincronizare (RACE CONDITION)!\n");
        } else {
            System.out.println("⚠️ Notă: Race condition-ul depinde de planificarea nucleelor OS.\n");
        }

        // -------------------------------------------------------------
        // TESTUL 2: Demonstrarea Comportamentului Non-Blocant Incorect
        // -------------------------------------------------------------
        System.out.println("--- Testare comportament non-blocant pe coadă goală ---");
        OfficeQueue emptyFaultyQueue = new FaultyOfficeQueue();
        ServiceTicket ticket = emptyFaultyQueue.takeTicket();
        System.out.println("Rezultat takeTicket() pe coadă goală: " + ticket);
        if (ticket == null) {
            System.out.println("🔴 REZULTAT FAULT DETECTAT: takeTicket() a returnat null imediat în loc să blocheze firul în WAITING!\n");
        }

        // -------------------------------------------------------------
        // TESTUL 3: Rularea cu Clasa BUNĂ (BlockingOfficeQueue) pentru Comparație
        // -------------------------------------------------------------
        System.out.println("--- Comparație cu BlockingOfficeQueue (Thread-Safe) ---");
        OfficeQueue safeQueue = new BlockingOfficeQueue(); // <-- DEPENDENCY INJECTION a clasei corecte

        List<Thread> safeProducers = new ArrayList<>();
        for (int t = 1; t <= numThreads; t++) {
            final int threadId = t;
            Thread producer = new Thread(() -> {
                for (int i = 1; i <= ticketsPerThread; i++) {
                    try {
                        safeQueue.addTicket(new ServiceTicket("Client-T" + threadId + "-" + i, "Certificat"));
                    } catch (Exception ignored) {}
                }
            });
            safeProducers.add(producer);
            producer.start();
        }

        for (Thread p : safeProducers) {
            p.join();
        }

        System.out.println("Tichete așteptate: " + totalExpectedTickets);
        System.out.println("Tichete găsite în BlockingQueue: " + safeQueue.size());
        if (safeQueue.size() == totalExpectedTickets) {
            System.out.println("🟢 REZULTAT CORECT: Exact 1000/1000 tichete înregistrate (Integritate 100% garantată)!");
        }

        System.out.println("\n=== Demonstrație finalizată cu succes ===");
    }
}

