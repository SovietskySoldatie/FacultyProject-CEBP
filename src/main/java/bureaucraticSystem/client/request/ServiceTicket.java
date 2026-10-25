package bureaucraticSystem.client.request;

import bureaucraticSystem.client.Client;
import bureaucraticSystem.config.data.Document;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tichetul de schimb: clientul îl depune în coadă, ghișeul îl procesează.
 * Ascunde detaliile tehnice de semnalizare în spatele unor metode simple:
 *  - markCompleted() folosit de ghișeu când termină actul
 *  - markFailed() folosit de ghișeu când cererea eșuează sau de scheduler/client la oprire
 *  - waitForCompletion() folosit de client când așteaptă la rând
 */
public class ServiceTicket {
    private final String clientId;
    private final String documentId;
    private final CompletableFuture<Boolean> completionSignal;
    private volatile Document issuedDocument;

    public ServiceTicket(String clientId, String documentId, CompletableFuture<Boolean> completionSignal) {
        this.clientId = Objects.requireNonNull(clientId, "clientId must not be null");
        this.documentId = Objects.requireNonNull(documentId, "documentId must not be null");
        this.completionSignal = completionSignal != null ? completionSignal : new CompletableFuture<>();
    }

    public ServiceTicket(String clientId, String documentId) {
        this(clientId, documentId, new CompletableFuture<>());
    }

    /**
     * Constructor convenabil când se deține instanța directă a Clientului.
     */
    public ServiceTicket(Client client, String documentId) {
        this(Objects.requireNonNull(client, "client must not be null").getClientId(), documentId);
    }

    public ServiceTicket(Client client, String documentId, CompletableFuture<Boolean> completionSignal) {
        this(Objects.requireNonNull(client, "client must not be null").getClientId(), documentId, completionSignal);
    }

    public String getClientId() {
        return clientId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public CompletableFuture<Boolean> getCompletionSignal() {
        return completionSignal;
    }

    public Document getIssuedDocument() {
        return issuedDocument;
    }

    public void setIssuedDocument(Document issuedDocument) {
        this.issuedDocument = issuedDocument;
    }

    /**
     * Anunță că tichetul a fost rezolvat cu succes de către ghișeu.
     * Returnează true dacă apelul a realizat tranziția în starea completată.
     */
    public boolean markCompleted() {
        return completionSignal != null && completionSignal.complete(true);
    }

    /**
     * Anunță că cererea a eșuat sau a fost întreruptă/anulată.
     * Returnează true dacă apelul a realizat tranziția în starea eșuată.
     */
    public boolean markFailed() {
        return completionSignal != null && completionSignal.complete(false);
    }

    /**
     * Indică dacă tichetul a fost soluționat (cu succes sau eșec).
     */
    public boolean isDone() {
        return completionSignal != null && completionSignal.isDone();
    }

    /**
     * Indică dacă tichetul s-a finalizat cu succes.
     */
    public boolean isCompletedSuccessfully() {
        return isDone() && Boolean.TRUE.equals(completionSignal.getNow(false));
    }

    /**
     * Indică dacă tichetul a fost marcat ca eșuat sau a fost anulat.
     */
    public boolean isFailed() {
        return isDone() && Boolean.FALSE.equals(completionSignal.getNow(true));
    }

    /**
     * Stă la rând până când actul este finalizat.
     */
    public boolean waitForCompletion() throws Exception {
        return completionSignal != null && completionSignal.get();
    }

    /**
     * Stă la rând un timp maxim de așteptare.
     */
    public boolean waitForCompletion(long timeout, TimeUnit unit) throws Exception {
        return completionSignal != null && completionSignal.get(timeout, unit);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceTicket that = (ServiceTicket) o;
        return Objects.equals(clientId, that.clientId) && Objects.equals(documentId, that.documentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId, documentId);
    }

    @Override
    public String toString() {
        return "ServiceTicket{" +
                "clientId='" + clientId + '\'' +
                ", documentId='" + documentId + '\'' +
                '}';
    }
}
