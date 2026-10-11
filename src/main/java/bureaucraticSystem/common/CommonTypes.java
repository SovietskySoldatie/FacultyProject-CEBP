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

    // Tichetul de schimb: clientul îl depune în coadă, ghișeul îl procesează.
    // completionSignal este "pagerul / biletul de ordine" prin care ghișeul notifică clientul când actul este finalizat.
    public record ServiceTicket(
            String clientId,
            String documentId,
            CompletableFuture<Boolean> completionSignal
    ) {}
}
