package bureaucraticSystem.scheduler;

import bureaucraticSystem.client.Client;
import bureaucraticSystem.client.request.ServiceTicket;
import bureaucraticSystem.config.data.Document;
import bureaucraticSystem.config.data.DocumentRegistry;
import bureaucraticSystem.counter.OfficeQueue;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Gestionează alocarea thread-safe a cererilor (ServiceTicket) către birouri
 * și resursele sistemului birocratic (ghișee, cozi, disponibilitate).
 */
public class Scheduler implements TicketDispatcher {
    private static volatile Scheduler instance;
    private static volatile CompletableFuture<Scheduler> instanceFuture = new CompletableFuture<>();
    private static final Object LOCK = new Object();
    private static volatile boolean wasReset = false;

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
     * Returnează instanța unică (Singleton) a Scheduler-ului.
     * Dacă Scheduler-ul nu a fost încă inițializat, firul apelant este pus în așteptare
     * până când un alt fir inițializează instanța cu un DocumentRegistry valid.
     */
    public static Scheduler getInstance() {
        return getInstance((DocumentRegistry) null);
    }

    /**
     * Returnează sau inițializează instanța unică cu un DocumentRegistry specificat.
     * - Dacă documentRegistry == null: verifică dacă instanța a fost resetată (eșuează imediat)
     *   sau blochează firul apelant pur determinist până la inițializare.
     * - Dacă documentRegistry != null: blochează pe LOCK până când instanța este creată și
     *   emite evenimentul prin CompletableFuture (instanceFuture.complete), deblocând toate firele în așteptare.
     */
    public static Scheduler getInstance(DocumentRegistry documentRegistry) {
        Scheduler localInstance = instance;
        if (localInstance != null) {
            return localInstance;
        }

        if (documentRegistry == null) {
            if (wasReset) {
                throw new IllegalStateException("Scheduler-ul a fost resetat / oprit și nu mai acceptă cereri.");
            }
            try {
                return instanceFuture.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Firul a fost întrerupt în timp ce aștepta inițializarea Scheduler-ului.", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                String message = cause != null ? cause.getMessage() : e.getMessage();
                throw new IllegalStateException("Așteptarea inițializării a eșuat: " + message, e);
            }
        }

        synchronized (LOCK) {
            localInstance = instance;
            if (localInstance == null) {
                localInstance = new Scheduler(documentRegistry);
                instance = localInstance;
                wasReset = false;
                if (instanceFuture.isDone()) {
                    instanceFuture = new CompletableFuture<>();
                }
                instanceFuture.complete(localInstance);
            }
            return localInstance;
        }
    }

    /**
     * Returnează instanța unică sau așteaptă un timp maxim specificat inițializarea acesteia.
     */
    public static Scheduler getInstance(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        Scheduler localInstance = instance;
        if (localInstance != null) {
            return localInstance;
        }
        return instanceFuture.get(timeout, unit);
    }

    /**
     * Returnează semnalul CompletableFuture asociat inițializării Scheduler-ului.
     */
    public static CompletableFuture<Scheduler> getInstanceFuture() {
        return instanceFuture;
    }

    /**
     * Reinițializează instanța Singleton (util pentru Unit Tests / scenarii de testare repetate).
     * Transmite determinist un semnal de excepție către toate firele care așteaptă inițializarea.
     */
    public static void resetInstance() {
        synchronized (LOCK) {
            if (instance != null) {
                instance.stop();
            }
            wasReset = true;
            if (!instanceFuture.isDone()) {
                instanceFuture.completeExceptionally(new IllegalStateException("Scheduler-ul a fost resetat / oprit."));
            }
            instance = null;
            instanceFuture = new CompletableFuture<>();
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
     * Eliberează și marchează ca eșuate eventualele tichete rămase în coadă.
     */
    public void unregisterOffice(String officeId) {
        if (officeId == null) {
            return;
        }
        OfficeQueue removedQueue = officeQueues.remove(officeId);
        if (removedQueue != null) {
            try {
                ServiceTicket t;
                while ((t = removedQueue.pollTicket(0, TimeUnit.MILLISECONDS)) != null) {
                    t.markFailed();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Returnează coada asociată unui birou.
     */
    public OfficeQueue getOfficeQueue(String officeId) {
        if (officeId == null) {
            return null;
        }
        return officeQueues.get(officeId);
    }

    /**
     * Metodă statică de dataflow: Alocă și plasează tichetul la coada biroului indicat.
     *
     * @param ticket   Tichetul creat de client
     * @param officeId ID-ul biroului la care este alocat tichetul
     */
    public static void dispatchTicket(ServiceTicket ticket, String officeId) throws InterruptedException {
        Objects.requireNonNull(ticket, "ticket must not be null");
        try {
            getInstance().dispatchTicketInternal(ticket, officeId);
        } catch (InterruptedException e) {
            ticket.markFailed();
            throw e;
        } catch (Exception e) {
            ticket.markFailed();
            throw e;
        }
    }

    /**
     * Metodă statică de dataflow: Alocare automată pe baza registrului de documente.
     */
    public static void dispatchTicket(ServiceTicket ticket) throws InterruptedException {
        Objects.requireNonNull(ticket, "ticket must not be null");
        try {
            getInstance().dispatchTicketInternal(ticket);
        } catch (InterruptedException e) {
            ticket.markFailed();
            throw e;
        } catch (Exception e) {
            ticket.markFailed();
            throw e;
        }
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

        if (ticket.isDone()) {
            return;
        }

        OfficeQueue queue = officeQueues.get(officeId);
        if (queue == null) {
            ticket.markFailed();
            throw new IllegalArgumentException("Nu există coadă înregistrată pentru biroul: " + officeId);
        }

        // Alocă tichetul către coada biroului în mod thread-safe
        try {
            queue.addTicket(ticket);
            if (!running) {
                ticket.markFailed();
            }
        } catch (InterruptedException e) {
            ticket.markFailed();
            throw e;
        } catch (Exception e) {
            ticket.markFailed();
            throw e;
        }
    }

    /**
     * Implementarea internă a alocării automate pe baza registrului.
     */
    public void dispatchTicketInternal(ServiceTicket ticket) throws InterruptedException {
        Objects.requireNonNull(ticket, "ticket must not be null");

        if (!running) {
            ticket.markFailed();
            throw new IllegalStateException("Scheduler is stopped.");
        }

        if (ticket.isDone()) {
            return;
        }

        if (documentRegistry == null) {
            ticket.markFailed();
            throw new IllegalStateException("DocumentRegistry nu a fost configurat în Scheduler!");
        }

        Document doc = documentRegistry.getDocument(ticket.getDocumentId());
        if (doc == null) {
            ticket.markFailed();
            throw new IllegalArgumentException("Document necunoscut: " + ticket.getDocumentId());
        }

        if (doc.getOfficeId() == null) {
            ticket.markFailed();
            throw new IllegalArgumentException("Documentul " + ticket.getDocumentId() + " nu are un birou asociat!");
        }

        dispatchTicketInternal(ticket, doc.getOfficeId());
    }

    /**
     * Înregistrează și trimite o cerere în numele unui client către biroul specificat.
     */
    public boolean scheduleClient(Client client, String documentId, String officeId) throws Exception {
        Objects.requireNonNull(client, "client must not be null");
        return client.requestDocument(this, documentId, officeId);
    }

    /**
     * Înregistrează și trimite o cerere în numele unui client, determinând biroul automat.
     */
    public boolean scheduleClient(Client client, String documentId) throws Exception {
        Objects.requireNonNull(client, "client must not be null");
        return client.requestDocument(this, documentId);
    }

    @Override
    public void dispatch(ServiceTicket ticket, String officeId) throws InterruptedException {
        dispatchTicketInternal(ticket, officeId);
    }

    @Override
    public void dispatch(ServiceTicket ticket) throws InterruptedException {
        dispatchTicketInternal(ticket);
    }

    public void stop() {
        this.running = false;
        for (OfficeQueue queue : officeQueues.values()) {
            try {
                ServiceTicket t;
                while ((t = queue.pollTicket(0, TimeUnit.MILLISECONDS)) != null) {
                    t.markFailed();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public boolean isRunning() {
        return running;
    }
}
