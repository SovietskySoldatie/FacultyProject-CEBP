package bureaucraticSystem.client;

import bureaucraticSystem.client.request.ServiceTicket;
import bureaucraticSystem.config.data.Document;
import bureaucraticSystem.config.graph.RouteResolver;
import bureaucraticSystem.counter.OfficeQueue;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Reprezintă un client al sistemului birocratic.
 * Clientul rulează pe propriul său fir de execuție (Thread) și navighează prin birouri
 * pentru a obține toate documentele intermediare necesare până la obținerea actului final.
 */
public class Client implements Runnable {
    private final String clientId;
    private final String targetDocumentId;
    private final Set<String> ownedDocuments;
    private final RouteResolver routeResolver;
    private final Function<String, OfficeQueue> officeQueueResolver;
    private volatile boolean running = true;
    private volatile boolean completed = false;

    /**
     * Constructor complet cu rezolvitor de rute și funcție de căutare a cozilor pe baza ID-ului biroului.
     */
    public Client(String clientId,
                  String targetDocumentId,
                  RouteResolver routeResolver,
                  Function<String, OfficeQueue> officeQueueResolver,
                  Set<String> initialOwnedDocs) {
        this.clientId = Objects.requireNonNull(clientId, "clientId must not be null");
        this.targetDocumentId = Objects.requireNonNull(targetDocumentId, "targetDocumentId must not be null");
        this.routeResolver = routeResolver;
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
     * Constructor convenabil pentru o singură coadă (fără graf complet de rute).
     */
    public Client(String clientId, String targetDocumentId, OfficeQueue singleQueue) {
        this(clientId, targetDocumentId, null, officeId -> singleQueue, Collections.emptySet());
    }

    public String getClientId() {
        return clientId;
    }

    public String getTargetDocumentId() {
        return targetDocumentId;
    }

    public Set<String> getOwnedDocuments() {
        return Collections.unmodifiableSet(ownedDocuments);
    }

    public boolean isCompleted() {
        return completed || ownedDocuments.contains(targetDocumentId);
    }

    public void stop() {
        this.running = false;
    }

    /**
     * Trimite un tichet la coada unui birou și așteaptă finalizarea acestuia.
     */
    public boolean requestDocument(OfficeQueue queue, String documentId) throws Exception {
        if (queue == null) {
            throw new IllegalArgumentException("OfficeQueue cannot be null");
        }
        ServiceTicket ticket = new ServiceTicket(clientId, documentId);
        queue.addTicket(ticket);
        boolean success = ticket.waitForCompletion();
        if (success) {
            ownedDocuments.add(documentId);
        }
        return success;
    }

    @Override
    public void run() {
        System.out.println("Clientul " + clientId + " a pornit în căutarea actului: " + targetDocumentId);

        while (running && !isCompleted() && !Thread.currentThread().isInterrupted()) {
            try {
                if (ownedDocuments.contains(targetDocumentId)) {
                    completed = true;
                    break;
                }

                // Cazul 1 (General): Navigare pe baza grafului de dependențe (RouteResolver)
                if (routeResolver != null) {
                    Optional<Document> nextDocOpt = routeResolver.resolveNextRequiredDocument(ownedDocuments, targetDocumentId);
                    if (nextDocOpt.isEmpty()) {
                        System.err.println("[" + clientId + "] Nu s-a putut determina următorul pas pentru actul: " + targetDocumentId);
                        break;
                    }

                    Document nextDoc = nextDocOpt.get();
                    OfficeQueue queue = officeQueueResolver != null ? officeQueueResolver.apply(nextDoc.getOfficeId()) : null;
                    if (queue == null) {
                        System.err.println("[" + clientId + "] Coada pentru biroul " + nextDoc.getOfficeId() + " nu a fost găsită!");
                        break;
                    }

                    System.out.println("[" + clientId + "] Se așază la coada biroului " + nextDoc.getOfficeId() + " pentru actul " + nextDoc.getId());
                    boolean success = requestDocument(queue, nextDoc.getId());
                    if (!success) {
                        System.err.println("[" + clientId + "] Cererea a eșuat pentru actul " + nextDoc.getId());
                        break;
                    }
                // Cazul 2 (Caz particular / Unit Tests):
                // Util în scenarii de testare izolate (UTs) sau coadă unică (routeResolver == null).
                // Clientul nu navighează un graf de dependențe, ci solicită direct actul dorit la coada dedicată.
                } else if (officeQueueResolver != null) {
                    OfficeQueue queue = officeQueueResolver.apply(null);
                    if (queue == null) {
                        System.err.println("[" + clientId + "] Coada unică nu a fost găsită!");
                        break;
                    }
                    requestDocument(queue, targetDocumentId);
                    break;
                // Cazul 3 (Eroare de configurare / Gardă defensivă):
                // Niciun mecanism de rezolvare a cozilor sau a rutelor nu a fost configurat.
                } else {
                    System.err.println("[" + clientId + "] Niciun rezolvitor de cozi configurat.");
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Clientul " + clientId + " a fost întrerupt.");
                break;
            } catch (Exception e) {
                System.err.println("Eroare la clientul " + clientId + ": " + e.getMessage());
                break;
            }
        }

        if (ownedDocuments.contains(targetDocumentId)) {
            completed = true;
            System.out.println("Clientul " + clientId + " a obținut cu succes actul final: " + targetDocumentId);
        } else {
            System.out.println("Clientul " + clientId + " s-a oprit fără a finaliza actul " + targetDocumentId);
        }
    }
}
