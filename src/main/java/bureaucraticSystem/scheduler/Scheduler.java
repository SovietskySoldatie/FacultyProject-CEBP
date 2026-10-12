package bureaucraticSystem.scheduler;

import bureaucraticSystem.client.request.ServiceTicket;
import bureaucraticSystem.config.data.Document;
import bureaucraticSystem.config.data.DocumentRegistry;
import bureaucraticSystem.counter.OfficeQueue;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionează alocarea thread-safe a cererilor (ServiceTicket) către birouri
 * și resursele sistemului birocratic (ghișee, cozi, disponibilitate).
 */
public class Scheduler {
    private static volatile Scheduler instance;
    private static final Object LOCK = new Object();

    private final Map<String, OfficeQueue> officeQueues;
    private final DocumentRegistry documentRegistry;
    private volatile boolean running = true;

    /**
     * Constructor privat pentru prevenirea instanțierii necontrolate din exterior.
     */
    private Scheduler() {
        this(null);
    }

    /**
     * Constructor protejat pentru inițializare specifică sau teste izolate (UTs).
     */
    protected Scheduler(DocumentRegistry documentRegistry) {
        this.documentRegistry = documentRegistry;
        this.officeQueues = new ConcurrentHashMap<>();
    }

    /**
     * Returnează instanța unică (Singleton) a Scheduler-ului folosind Double-Checked Locking thread-safe.
     */
    public static Scheduler getInstance() {
        return getInstance(null);
    }

    /**
     * Returnează sau inițializează instanța unică cu un DocumentRegistry specificat.
     */
    public static Scheduler getInstance(DocumentRegistry documentRegistry) {
        Scheduler localInstance = instance;
        if (localInstance == null) {
            synchronized (LOCK) {
                localInstance = instance;
                if (localInstance == null) {
                    instance = localInstance = new Scheduler(documentRegistry);
                }
            }
        }
        return localInstance;
    }

    /**
     * Reinițializează instanța Singleton (util pentru Unit Tests / scenarii de testare repetate).
     */
    public static void resetInstance() {
        synchronized (LOCK) {
            if (instance != null) {
                instance.stop();
            }
            instance = null;
        }
    }

    /**
     * Înregistrează coada unui birou în scheduler (operație thread-safe).
     */
    public void registerOffice(String officeId, OfficeQueue queue) {
        Objects.requireNonNull(officeId, "officeId must not be null");
        Objects.requireNonNull(queue, "queue must not be null");
        officeQueues.put(officeId, queue);
    }

    /**
     * Elimină un birou din scheduler (de ex. la închidere).
     */
    public void unregisterOffice(String officeId) {
        officeQueues.remove(officeId);
    }

    /**
     * Returnează coada asociată unui birou.
     */
    public OfficeQueue getOfficeQueue(String officeId) {
        return officeQueues.get(officeId);
    }

    /**
     * Metodă statică de dataflow: Alocă și plasează tichetul la coada biroului indicat.
     *
     * @param ticket   Tichetul creat de client
     * @param officeId ID-ul biroului la care este alocat tichetul
     */
    public static void dispatchTicket(ServiceTicket ticket, String officeId) throws InterruptedException {
        getInstance().dispatchTicketInternal(ticket, officeId);
    }

    /**
     * Metodă statică de dataflow: Alocare automată pe baza registrului de documente.
     */
    public static void dispatchTicket(ServiceTicket ticket) throws InterruptedException {
        getInstance().dispatchTicketInternal(ticket);
    }

    /**
     * Implementarea internă a alocării la birou.
     */
    public void dispatchTicketInternal(ServiceTicket ticket, String officeId) throws InterruptedException {
        Objects.requireNonNull(ticket, "ticket must not be null");
        Objects.requireNonNull(officeId, "officeId must not be null");

        if (!running) {
            ticket.markFailed();
            throw new IllegalStateException("Scheduler is stopped.");
        }

        OfficeQueue queue = officeQueues.get(officeId);
        if (queue == null) {
            ticket.markFailed();
            throw new IllegalArgumentException("Nu există coadă înregistrată pentru biroul: " + officeId);
        }

        // Alocă tichetul către coada biroului în mod thread-safe
        queue.addTicket(ticket);
    }

    /**
     * Implementarea internă a alocării automate pe baza registrului.
     */
    public void dispatchTicketInternal(ServiceTicket ticket) throws InterruptedException {
        Objects.requireNonNull(ticket, "ticket must not be null");

        if (documentRegistry == null) {
            throw new IllegalStateException("DocumentRegistry nu a fost configurat în Scheduler!");
        }

        Document doc = documentRegistry.getDocument(ticket.getDocumentId());
        if (doc == null) {
            ticket.markFailed();
            throw new IllegalArgumentException("Document necunoscut: " + ticket.getDocumentId());
        }

        dispatchTicketInternal(ticket, doc.getOfficeId());
    }

    public void stop() {
        this.running = false;
    }

    public boolean isRunning() {
        return running;
    }
}
