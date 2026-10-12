package bureaucraticSystem.common;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Tipuri de date partajate între toate modulele sistemului birocratic.
 * Permite compilarea și rularea independentă a componentelor fiecărui membru.
 */
public final class CommonTypes {

    private CommonTypes() {
        // Previne instanțierea clasei de utilitare/tipuri
    }

    // Reprezintă un act și actele necesare înainte de a-l putea obține
    public record Document(String id, String officeId, Set<String> prerequisites) {}

    /**
     * Tichetul de schimb: clientul îl depune în coadă, ghișeul îl procesează.
     * Ascunde detaliile tehnice de semnalizare în spatele unor metode simple:
     *  - markCompleted() folosit de ghișeu când termină actul
     *  - waitForCompletion() folosit de client când așteaptă la rând
     */
    public record ServiceTicket(
            String clientId,
            String documentId,
            CompletableFuture<Boolean> completionSignal
    ) {
        // Constructor comod: creează automat semnalul în spate, fără să-l declari manual
        public ServiceTicket(String clientId, String documentId) {
            this(clientId, documentId, new CompletableFuture<>());
        }

        // Metodă simplă pentru ghișeu: anunță că tichetul a fost rezolvat cu succes
        public void markCompleted() {
            if (completionSignal != null) {
                completionSignal.complete(true);
            }
        }

        // Metodă simplă pentru ghișeu: anunță că cererea a eșuat sau a fost întreruptă
        public void markFailed() {
            if (completionSignal != null) {
                completionSignal.complete(false);
            }
        }

        // Metodă simplă pentru client: stă la rând până când actul este finalizat
        public boolean waitForCompletion() throws Exception {
            return completionSignal != null && completionSignal.get();
        }
    }
}
