package bureaucraticSystem.client.request;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tichetul de schimb: clientul îl depune în coadă, ghișeul îl procesează.
 * Ascunde detaliile tehnice de semnalizare în spatele unor metode simple:
 *  - markCompleted() folosit de ghișeu când termină actul
 *  - markFailed() folosit de ghișeu când cererea eșuează
 *  - waitForCompletion() folosit de client când așteaptă la rând
 */
public class ServiceTicket {
    private final String clientId;
    private final String documentId;
    private final CompletableFuture<Boolean> completionSignal;

    public ServiceTicket(String clientId, String documentId, CompletableFuture<Boolean> completionSignal) {
        this.clientId = Objects.requireNonNull(clientId, "clientId must not be null");
        this.documentId = Objects.requireNonNull(documentId, "documentId must not be null");
        this.completionSignal = completionSignal != null ? completionSignal : new CompletableFuture<>();
    }

    public ServiceTicket(String clientId, String documentId) {
        this(clientId, documentId, new CompletableFuture<>());
    }

    public String getClientId() {
        return clientId;
    }

    // Compatibilitate cu apelurile record-style
    public String clientId() {
        return clientId;
    }

    public String getDocumentId() {
        return documentId;
    }

    // Compatibilitate cu apelurile record-style
    public String documentId() {
        return documentId;
    }

    public CompletableFuture<Boolean> getCompletionSignal() {
        return completionSignal;
    }

    // Compatibilitate cu apelurile record-style
    public CompletableFuture<Boolean> completionSignal() {
        return completionSignal;
    }

    /**
     * Anunță că tichetul a fost rezolvat cu succes de către ghișeu.
     */
    public void markCompleted() {
        if (completionSignal != null) {
            completionSignal.complete(true);
        }
    }

    /**
     * Anunță că cererea a eșuat sau a fost întreruptă.
     */
    public void markFailed() {
        if (completionSignal != null) {
            completionSignal.complete(false);
        }
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
