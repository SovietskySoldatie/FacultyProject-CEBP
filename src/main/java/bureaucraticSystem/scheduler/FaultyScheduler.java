package bureaucraticSystem.scheduler;

import bureaucraticSystem.client.request.ServiceTicket;
import bureaucraticSystem.config.data.Document;
import bureaucraticSystem.config.data.DocumentRegistry;
import bureaucraticSystem.counter.OfficeQueue;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IMPLEMENTARE CU EROARE INTENTIONATA DE CONCURENTA (Faulty Solution).
 *
 * Fault: "Singleton not instantiated / multiple instantiation :: race
 * condition".
 *
 * getInstance() verifica 'instance == null' fara nicio sincronizare (si
 * 'instance'
 * nu este volatile). Daca mai multe fire apeleaza simultan getInstance(), toate
 * pot
 * vedea null si fiecare creeaza propriul FaultyScheduler. Fiecare instanta are
 * propriile cozi de birouri, deci birourile inregistrate intr-una nu sunt
 * vizibile
 * in celelalte (tichete pierdute / "coada inexistenta").
 *
 * Este o clasa independenta (nu extinde Scheduler); implementeaza aceeasi
 * interfata
 * TicketDispatcher ca varianta corecta, deci poate fi injectata (Dependency
 * Injection).
 * Restul logicii este intentionat identic cu Scheduler, singurul fault fiind
 * singletonul.
 */
public class FaultyScheduler implements TicketDispatcher {

    // FAULT: nu este volatile, iar accesul nu este sincronizat.
    private static FaultyScheduler instance;

    // Numar de instante create efectiv (pentru demonstratie).
    private static final AtomicInteger INSTANCES_CREATED = new AtomicInteger();

    private final Map<String, OfficeQueue> officeQueues = new ConcurrentHashMap<>();
    private final DocumentRegistry documentRegistry;
    private volatile boolean running = true;

    private FaultyScheduler(DocumentRegistry documentRegistry) {
        this.documentRegistry = documentRegistry;
        INSTANCES_CREATED.incrementAndGet();
    }

    public static FaultyScheduler getInstance() {
        return getInstance(null);
    }

    public static FaultyScheduler getInstance(DocumentRegistry documentRegistry) {
        // FAULT: check-then-act neatomic. Doua fire pot trece ambele de acest if.
        if (instance == null) {
            try {
                // Largeste intentionat fereastra de race, ca fault-ul sa apara determinist la
                // demo.
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            instance = new FaultyScheduler(documentRegistry);
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
        INSTANCES_CREATED.set(0);
    }

    /**
     * Cate instante de FaultyScheduler au fost create de la ultimul reset (ar
     * trebui 1).
     */
    public static int getInstancesCreated() {
        return INSTANCES_CREATED.get();
    }

    @Override
    public void registerOffice(String officeId, OfficeQueue queue) {
        Objects.requireNonNull(officeId, "officeId must not be null");
        Objects.requireNonNull(queue, "queue must not be null");
        officeQueues.put(officeId, queue);
    }

    @Override
    public void unregisterOffice(String officeId) {
        officeQueues.remove(officeId);
    }

    @Override
    public OfficeQueue getOfficeQueue(String officeId) {
        return officeQueues.get(officeId);
    }

    @Override
    public void dispatch(ServiceTicket ticket, String officeId) throws InterruptedException {
        Objects.requireNonNull(ticket, "ticket must not be null");
        Objects.requireNonNull(officeId, "officeId must not be null");

        if (!running) {
            ticket.markFailed();
            throw new IllegalStateException("Scheduler is stopped.");
        }

        OfficeQueue queue = officeQueues.get(officeId);
        if (queue == null) {
            ticket.markFailed();
            throw new IllegalArgumentException("Nu exista coada inregistrata pentru biroul: " + officeId);
        }

        queue.addTicket(ticket);
    }

    @Override
    public void dispatch(ServiceTicket ticket) throws InterruptedException {
        Objects.requireNonNull(ticket, "ticket must not be null");

        if (documentRegistry == null) {
            throw new IllegalStateException("DocumentRegistry nu a fost configurat in Scheduler!");
        }

        Document doc = documentRegistry.getDocument(ticket.getDocumentId());
        if (doc == null) {
            ticket.markFailed();
            throw new IllegalArgumentException("Document necunoscut: " + ticket.getDocumentId());
        }

        dispatch(ticket, doc.getOfficeId());
    }

    @Override
    public void stop() {
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
