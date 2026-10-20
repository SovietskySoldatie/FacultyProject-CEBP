package bureaucraticSystem.demo;

import bureaucraticSystem.client.request.ServiceTicket;
import bureaucraticSystem.config.data.Document;
import bureaucraticSystem.config.data.DocumentRegistry;
import bureaucraticSystem.config.graph.RouteResolver;
import bureaucraticSystem.counter.DeskWorker;
import bureaucraticSystem.counter.OfficeQueue;
import bureaucraticSystem.resource.OfficialStamp;
import bureaucraticSystem.scheduler.FaultyScheduler;
import bureaucraticSystem.scheduler.Scheduler;
import bureaucraticSystem.scheduler.TicketDispatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Demo pentru fault-ul: "Scheduler singleton :: multiple instantiation :: race
 * condition".
 *
 * Simuleaza un sistem real: birouri cu ghisee (DeskWorker), un registru de
 * documente si
 * mai multi clienti pe fire separate. La pornire, fiecare componenta (setup-ul
 * birourilor
 * si fiecare client) isi cere dispecerul prin getInstance(), simultan.
 *
 * - Cu FaultyScheduler: singletonul se creeaza de mai multe ori. Birourile sunt
 * inregistrate intr-o instanta, iar clientii folosesc alte instante, goale ->
 * "nu exista coada pentru biroul X", clientii nu isi primesc documentele.
 * - Cu Scheduler corect: o singura instanta, toti clientii isi primesc
 * documentele.
 *
 * Ambele variante sunt folosite prin interfata TicketDispatcher (Dependency
 * Injection).
 */
public class SchedulerFaultDemo {

    private static final int CLIENTS = 5;
    private static final String FINAL_DOC = "Pasaport";

    public static void main(String[] args) throws Exception {
        DocumentRegistry registry = new DocumentRegistry();
        registry.registerDocument(new Document("CertificatNastere", "StareaCivila", Set.of()));
        registry.registerDocument(new Document("AdeverintaVenit", "ANAF", Set.of()));
        registry.registerDocument(new Document("Pasaport", "Pasapoarte",
                Set.of("CertificatNastere", "AdeverintaVenit")));

        System.out.println("##### VARIANTA 1: FaultyScheduler (cu fault) #####");
        FaultyScheduler.resetInstance();
        run(registry, () -> FaultyScheduler.getInstance(registry));
        System.out.println("Instante FaultyScheduler create: " + FaultyScheduler.getInstancesCreated()
                + " (ar trebui 1)\n");

        System.out.println("##### VARIANTA 2: Scheduler (corect) #####");
        Scheduler.resetInstance();
        run(registry, () -> Scheduler.getInstance(registry));
        System.out.println("Instanta Scheduler: singura, partajata de toate componentele.");
    }

    private static void run(DocumentRegistry registry, Supplier<TicketDispatcher> dispatcherFactory)
            throws Exception {
        // Birouri si ghisee (nu depind de scheduler)
        String[] offices = { "StareaCivila", "ANAF", "Pasapoarte" };
        OfficeQueue[] queues = new OfficeQueue[offices.length];
        DeskWorker[] workers = new DeskWorker[offices.length];
        Thread[] workerThreads = new Thread[offices.length];
        OfficialStamp stamp = new OfficialStamp();
        for (int i = 0; i < offices.length; i++) {
            queues[i] = new OfficeQueue();
            workers[i] = new DeskWorker("Desk-" + offices[i], queues[i], null, stamp);
            workerThreads[i] = new Thread(workers[i], workers[i].getDeskId());
            workerThreads[i].start();
        }

        Set<Integer> instancesSeen = Collections.synchronizedSet(new HashSet<>());
        AtomicInteger succeeded = new AtomicInteger();
        CyclicBarrier startLine = new CyclicBarrier(CLIENTS + 1);
        // Clientii nu trimit tichete pana nu s-au inregistrat birourile (altfel ar fi
        // un alt race, de ordine la pornire).
        CountDownLatch officesReady = new CountDownLatch(1);

        // Setup-ul birourilor: isi ia dispecerul si inregistreaza cozile
        Thread setup = new Thread(() -> {
            await(startLine);
            TicketDispatcher d = dispatcherFactory.get();
            instancesSeen.add(System.identityHashCode(d));
            System.out.println("[Setup] a primit dispecerul @" + System.identityHashCode(d)
                    + " si inregistreaza birourile");
            for (int i = 0; i < offices.length; i++) {
                d.registerOffice(offices[i], queues[i]);
            }
            officesReady.countDown();
        }, "Setup");
        setup.start();

        // Clienti: fiecare isi ia dispecerul si cere documentul final
        List<Thread> clients = new ArrayList<>();
        for (int c = 1; c <= CLIENTS; c++) {
            String clientId = "Cetatean-" + c;
            Thread t = new Thread(() -> {
                await(startLine);
                TicketDispatcher d = dispatcherFactory.get();
                instancesSeen.add(System.identityHashCode(d));
                System.out.println("[" + clientId + "] a primit dispecerul @" + System.identityHashCode(d));
                try {
                    officesReady.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (obtainDocument(clientId, registry, d)) {
                    succeeded.incrementAndGet();
                }
            }, clientId);
            clients.add(t);
            t.start();
        }

        setup.join();
        for (Thread t : clients) {
            t.join();
        }

        System.out.println("\n--- REZULTAT ---");
        System.out.println("Dispeceri distincti folositi: " + instancesSeen.size() + " (ar trebui 1)");
        System.out.println("Clienti care au primit " + FINAL_DOC + ": " + succeeded.get() + " din " + CLIENTS);

        for (int i = 0; i < offices.length; i++) {
            workers[i].stop();
            workerThreads[i].interrupt();
        }
        for (Thread t : workerThreads) {
            t.join(2000);
        }
    }

    /**
     * Clientul parcurge lantul de dependente (RouteResolver) si cere fiecare act
     * la biroul potrivit, prin dispecerul primit.
     */
    private static boolean obtainDocument(String clientId, DocumentRegistry registry, TicketDispatcher d) {
        Set<String> owned = ConcurrentSet.create();
        try {
            while (!owned.contains(FINAL_DOC)) {
                Document next = RouteResolver.resolveNextRequiredDocument(registry, owned, FINAL_DOC).orElse(null);
                if (next == null) {
                    System.out.println("[" + clientId + "] nu poate determina urmatorul act");
                    return false;
                }
                ServiceTicket ticket = new ServiceTicket(clientId, next.getId());
                d.dispatch(ticket, next.getOfficeId());
                if (!ticket.waitForCompletion(5, TimeUnit.SECONDS)) {
                    System.out.println("[" + clientId + "] cererea pentru " + next.getId() + " a esuat");
                    return false;
                }
                owned.add(next.getId());
                System.out.println("[" + clientId + "] a primit " + next.getId());
            }
            return true;
        } catch (IllegalArgumentException e) {
            System.out.println("[" + clientId + "] EROARE: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.out.println("[" + clientId + "] EROARE: " + e);
            return false;
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Set thread-safe mic, doar pentru lizibilitate. */
    private static final class ConcurrentSet {
        static Set<String> create() {
            return java.util.concurrent.ConcurrentHashMap.newKeySet();
        }
    }
}
