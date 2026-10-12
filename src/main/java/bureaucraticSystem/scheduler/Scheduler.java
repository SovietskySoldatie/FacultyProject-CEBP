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
    private final Map<String, OfficeQueue> officeQueues;
    private final DocumentRegistry documentRegistry;
    private volatile boolean running = true;

    public Scheduler() {
        this(null);
    }

    public Scheduler(DocumentRegistry documentRegistry) {
        this.documentRegistry = documentRegistry;
        this.officeQueues = new ConcurrentHashMap<>();
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
     * Alocă și plasează tichetul la coada biroului indicat.
     * Aici se concentrează logica de thread-safety și dispatching a scheduler-ului.
     *
     * @param ticket   Tichetul creat de client
     * @param officeId ID-ul biroului la care este alocat tichetul
     */
    public void dispatchTicket(ServiceTicket ticket, String officeId) throws InterruptedException {
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
     * Alocare automată pe baza registrului de documente:
     * Caută biroul emitent al actului cerut și plasează tichetul.
     */
    public void dispatchTicket(ServiceTicket ticket) throws InterruptedException {
        Objects.requireNonNull(ticket, "ticket must not be null");

        if (documentRegistry == null) {
            throw(new IllegalStateException("DocumentRegistry nu a fost configurat în Scheduler!"));
        }

        Document doc = documentRegistry.getDocument(ticket.getDocumentId());
        if (doc == null) {
            ticket.markFailed();
            throw new IllegalArgumentException("Document necunoscut: " + ticket.getDocumentId());
        }

        dispatchTicket(ticket, doc.getOfficeId());
    }

    public void stop() {
        this.running = false;
    }

    public boolean isRunning() {
        return running;
    }
}
