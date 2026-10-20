package bureaucraticSystem.client;

import bureaucraticSystem.client.request.ServiceTicket;
import bureaucraticSystem.config.data.Document;
import bureaucraticSystem.config.graph.RouteResolver;
import bureaucraticSystem.counter.OfficeQueue;
import bureaucraticSystem.scheduler.TicketDispatcher;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import java.util.stream.Collectors;

/**
 * Reprezinta un client al sistemului birocratic.
 * Clientul ruleaza pe propriul sau fir de executie (Thread) si navigheaza prin birouri
 * pentru a obtine toate documentele intermediare necesare pana la obtinerea actului final.
 */
public class Client implements Runnable {
    private final String clientId;
    private final String targetDocumentId;
    private final Set<Document> ownedDocuments; // assigned to ConcurrentHashMap.newKeySet
    private final Map<String, Document> receivedDocuments = new ConcurrentHashMap<>();
    private final RouteResolver routeResolver;
    private final TicketDispatcher scheduler;
    private final Function<String, OfficeQueue> officeQueueResolver;
    private volatile boolean running = true;
    private volatile boolean completed = false;
    private final Object requestLock = new Object();
    private volatile Thread runnerThread;
    private volatile ServiceTicket currentTicket;

    /**
     * Constructor principal care foloseste Scheduler-ul pentru alocarea catre birouri.
     */
    public Client(String clientId,
                  String targetDocumentId,
                  RouteResolver routeResolver,
                  TicketDispatcher scheduler,
                  Set<Document> initialOwnedDocs) {
        this.clientId = Objects.requireNonNull(clientId, "clientId must not be null");
        this.targetDocumentId = Objects.requireNonNull(targetDocumentId, "targetDocumentId must not be null");
        this.routeResolver = routeResolver;
        this.scheduler = scheduler;
        this.officeQueueResolver = (scheduler != null) ? scheduler::getOfficeQueue : null;
        this.ownedDocuments = ConcurrentHashMap.newKeySet();
        if (initialOwnedDocs != null) {
            this.ownedDocuments.addAll(initialOwnedDocs);
        }
    }

    public Client(String clientId,
                  String targetDocumentId,
                  RouteResolver routeResolver,
                  TicketDispatcher scheduler) {
        this(clientId, targetDocumentId, routeResolver, scheduler, Collections.emptySet());
    }

    /**
     * Constructor convenabil ce folosește automat instanța Singleton RouteResolver.getInstance().
     */
    public Client(String clientId,
                  String targetDocumentId,
                  TicketDispatcher scheduler) {
        this(clientId, targetDocumentId, RouteResolver.getInstance(), scheduler, Collections.emptySet());
    }

    /**
     * Constructor cu functie de rezolvare a cozilor (pentru teste sau scenarii fara scheduler centralizat).
     */
    public Client(String clientId,
                  String targetDocumentId,
                  RouteResolver routeResolver,
                  Function<String, OfficeQueue> officeQueueResolver,
                  Set<Document> initialOwnedDocs) {
        this.clientId = Objects.requireNonNull(clientId, "clientId must not be null");
        this.targetDocumentId = Objects.requireNonNull(targetDocumentId, "targetDocumentId must not be null");
        this.routeResolver = routeResolver;
        this.scheduler = null;
        this.officeQueueResolver = officeQueueResolver;
        this.ownedDocuments = ConcurrentHashMap.newKeySet();
        if (initialOwnedDocs != null) {
            this.ownedDocuments.addAll(initialOwnedDocs);
        }
    }

    public Client(String clientId,
                  String targetDocumentId,
                  RouteResolver routeResolver,
                  Function<String, OfficeQueue> officeQueueResolver) {
        this(clientId, targetDocumentId, routeResolver, officeQueueResolver, Collections.emptySet());
    }

    public Client(String clientId,
                  String targetDocumentId,
                  RouteResolver routeResolver,
                  Map<String, OfficeQueue> officeQueues) {
        this(clientId, targetDocumentId, routeResolver, officeQueues != null ? officeQueues::get : null, Collections.emptySet());
    }

    /**
     * Constructor convenabil pentru o singura coada (fara graf complet de rute).
     */
    public Client(String clientId, String targetDocumentId, OfficeQueue singleQueue) {
        this.clientId = Objects.requireNonNull(clientId, "clientId must not be null");
        this.targetDocumentId = Objects.requireNonNull(targetDocumentId, "targetDocumentId must not be null");
        this.routeResolver = null;
        this.scheduler = null;
        this.officeQueueResolver = officeId -> singleQueue;
        this.ownedDocuments = ConcurrentHashMap.newKeySet();
    }

    public String getClientId() {
        return clientId;
    }

    public String getTargetDocumentId() {
        return targetDocumentId;
    }

    public Set<Document> getOwnedDocuments() {
        return Collections.unmodifiableSet(ownedDocuments);
    }

    public Set<String> getOwnedDocumentIds() {
        return ownedDocuments.stream()
                .map(Document::getId)
                .collect(Collectors.toSet());
    }

    public boolean ownsDocument(String documentId) {
        if (documentId == null) {
            return false;
        }
        return ownedDocuments.stream().anyMatch(doc -> documentId.equals(doc.getId()));
    }

    public Document getOwnedDocument(String documentId) {
        if (documentId == null) {
            return null;
        }
        return ownedDocuments.stream()
                .filter(doc -> documentId.equals(doc.getId()))
                .findFirst()
                .orElse(null);
    }

    public Map<String, Document> getReceivedDocuments() {
        return Collections.unmodifiableMap(receivedDocuments);
    }

    public Document getReceivedDocument(String documentId) {
        return receivedDocuments.get(documentId);
    }

    public boolean isCompleted() {
        return completed || ownsDocument(targetDocumentId);
    }

    @FunctionalInterface
    private interface TicketSubmitter {
        void dispatch(ServiceTicket ticket) throws Exception;
    }

    public void stop() {
        this.running = false;
        ServiceTicket ticket = this.currentTicket;
        if (ticket != null) {
            ticket.markFailed();
        }
        Thread thread = this.runnerThread;
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public TicketDispatcher getScheduler() {
        return scheduler;
    }

    public RouteResolver getRouteResolver() {
        return routeResolver;
    }

    public ServiceTicket getCurrentTicket() {
        return currentTicket;
    }

    /**
     * Metodă internă comună de trimitere și așteptare a unui tichet.
     * Asigură execuția serializată per client, propagarea semnalului de stop()
     * și curățarea oricăror tichete active în caz de eroare sau întrerupere.
     */
    private boolean executeTicketRequest(String documentId, TicketSubmitter dispatcher) throws Exception {
        if (!running || Thread.currentThread().isInterrupted()) {
            return false;
        }

        synchronized (requestLock) {
            if (!running || Thread.currentThread().isInterrupted()) {
                return false;
            }

            boolean isCallerRunner = (this.runnerThread == null);
            if (isCallerRunner) {
                this.runnerThread = Thread.currentThread();
            }

            ServiceTicket ticket = new ServiceTicket(this, documentId);
            this.currentTicket = ticket;
            try {
                dispatcher.dispatch(ticket);
                boolean success = ticket.waitForCompletion();
                if (success) {
                    Document issued = ticket.getIssuedDocument();
                    if (issued != null) {
                        ownedDocuments.add(issued);
                        receivedDocuments.put(documentId, issued);
                    } else {
                        Document fallbackDoc = new Document(documentId, "UNKNOWN", Set.of(), -1);
                        ownedDocuments.add(fallbackDoc);
                        receivedDocuments.put(documentId, fallbackDoc);
                    }
                    if (targetDocumentId.equals(documentId)) {
                        completed = true;
                    }
                    return true;
                }
                return false;
            } catch (InterruptedException e) {
                ticket.markFailed();
                throw e;
            } catch (Exception e) {
                ticket.markFailed();
                throw e;
            } finally {
                this.currentTicket = null;
                if (isCallerRunner) {
                    this.runnerThread = null;
                }
            }
        }
    }

    /**
     * Trimite un tichet la coada unui birou și așteaptă finalizarea acestuia în mod thread-safe.
     */
    public boolean requestDocument(OfficeQueue queue, String documentId) throws Exception {
        if (queue == null) {
            throw new IllegalArgumentException("OfficeQueue cannot be null");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId cannot be null");
        }
        return executeTicketRequest(documentId, queue::addTicket);
    }

    /**
     * Trimite un tichet prin Scheduler (care se ocupă de alocarea la birou) și așteaptă finalizarea în mod thread-safe.
     */
    public boolean requestDocument(TicketDispatcher scheduler, String documentId, String officeId) throws Exception {
        if (scheduler == null) {
            throw new IllegalArgumentException("Scheduler cannot be null");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId cannot be null");
        }
        if (officeId == null) {
            throw new IllegalArgumentException("officeId cannot be null");
        }
        return executeTicketRequest(documentId, t -> scheduler.dispatch(t, officeId));
    }

    /**
     * Trimite un tichet prin Scheduler fără a specifica biroul (alocare automată pe baza registrului de documente).
     */
    public boolean requestDocument(TicketDispatcher scheduler, String documentId) throws Exception {
        if (scheduler == null) {
            throw new IllegalArgumentException("Scheduler cannot be null");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId cannot be null");
        }
        return executeTicketRequest(documentId, scheduler::dispatch);
    }

    /**
     * Solicită obținerea unui document folosind infrastructura configurată a clientului (Scheduler sau coadă).
     */
    public boolean requestDocument(String documentId) throws Exception {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId cannot be null");
        }
        if (scheduler != null) {
            return requestDocument(scheduler, documentId);
        } else if (officeQueueResolver != null) {
            OfficeQueue queue = officeQueueResolver.apply(null);
            if (queue == null) {
                throw new IllegalStateException("Nu există o coadă disponibilă pentru clientul " + clientId);
            }
            return requestDocument(queue, documentId);
        } else {
            throw new IllegalStateException("Niciun scheduler sau coadă configurată pentru clientul " + clientId);
        }
    }

    /**
     * Solicită obținerea unui document la un anumit birou folosind infrastructura configurată.
     */
    public boolean requestDocument(String documentId, String officeId) throws Exception {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId cannot be null");
        }
        if (scheduler != null) {
            if (officeId != null) {
                return requestDocument(scheduler, documentId, officeId);
            } else {
                return requestDocument(scheduler, documentId);
            }
        } else if (officeQueueResolver != null) {
            OfficeQueue queue = officeQueueResolver.apply(officeId);
            if (queue == null) {
                throw new IllegalStateException("Nu există o coadă disponibilă pentru biroul " + officeId);
            }
            return requestDocument(queue, documentId);
        } else {
            throw new IllegalStateException("Niciun scheduler sau coadă configurată pentru clientul " + clientId);
        }
    }

    @Override
    public void run() {
        this.runnerThread = Thread.currentThread();
        try {
            System.out.println("Clientul " + clientId + " a pornit in cautarea actului: " + targetDocumentId);

            while (running && !isCompleted() && !Thread.currentThread().isInterrupted()) {
                try {
                    if (ownsDocument(targetDocumentId)) {
                        completed = true;
                        break;
                    }

                    // Cazul 1 (General): Navigare pe baza grafului de dependente (RouteResolver)
                    if (routeResolver != null) {
                        Optional<Document> nextDocOpt = routeResolver.resolve(getOwnedDocumentIds(), targetDocumentId);
                        if (nextDocOpt.isEmpty()) {
                            System.err.println("[" + clientId + "] Nu s-a putut determina urmatorul pas pentru actul: " + targetDocumentId);
                            break;
                        }

                        Document nextDoc = nextDocOpt.get();
                        boolean success;

                        if (scheduler != null) {
                            // Clientul obtine tichetul, iar alocarea la birou este facuta de Scheduler
                            if (nextDoc.getOfficeId() != null) {
                                System.out.println("[" + clientId + "] Trimite tichetul pentru " + nextDoc.getId() + " prin Scheduler catre biroul " + nextDoc.getOfficeId());
                                success = requestDocument(scheduler, nextDoc.getId(), nextDoc.getOfficeId());
                            } else {
                                System.out.println("[" + clientId + "] Trimite tichetul pentru " + nextDoc.getId() + " prin Scheduler (alocare automata)");
                                success = requestDocument(scheduler, nextDoc.getId());
                            }
                        } else if (officeQueueResolver != null) {
                            OfficeQueue queue = officeQueueResolver.apply(nextDoc.getOfficeId());
                            if (queue == null) {
                                System.err.println("[" + clientId + "] Coada pentru biroul " + nextDoc.getOfficeId() + " nu a fost gasita!");
                                break;
                            }
                            System.out.println("[" + clientId + "] Se aseaza la coada biroului " + nextDoc.getOfficeId() + " pentru actul " + nextDoc.getId());
                            success = requestDocument(queue, nextDoc.getId());
                        } else {
                            System.err.println("[" + clientId + "] Niciun scheduler sau rezolvitor de cozi configurat!");
                            break;
                        }

                        if (!success) {
                            System.err.println("[" + clientId + "] Cererea a esuat pentru actul " + nextDoc.getId());
                            break;
                        }
                    // Cazul 2 (Caz particular / Unit Tests):
                    // Util in scenarii de testare izolate (UTs) sau coada unica (routeResolver == null).
                    // Clientul nu navigheaza un graf de dependente, ci solicita direct actul dorit.
                    } else if (officeQueueResolver != null) {
                        OfficeQueue queue = officeQueueResolver.apply(null);
                        if (queue == null) {
                            System.err.println("[" + clientId + "] Coada unica nu a fost gasita!");
                            break;
                        }
                        requestDocument(queue, targetDocumentId);
                        break;
                    // Cazul 3 (Eroare de configurare / Garda defensiva):
                    // Niciun mecanism de rezolvare a cozilor, a rutelor sau scheduler nu a fost configurat.
                    } else {
                        System.err.println("[" + clientId + "] Niciun rezolvitor de cozi configurat.");
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Clientul " + clientId + " a fost intrerupt.");
                    break;
                } catch (Exception e) {
                    System.err.println("Eroare la clientul " + clientId + ": " + e.getMessage());
                    break;
                }
            }
        } finally {
            this.runnerThread = null;
        }

        if (ownsDocument(targetDocumentId)) {
            completed = true;
            System.out.println("Clientul " + clientId + " a obtinut cu succes actul final: " + targetDocumentId);
        } else {
            System.out.println("Clientul " + clientId + " s-a oprit fara a finaliza actul " + targetDocumentId);
        }
    }
}
