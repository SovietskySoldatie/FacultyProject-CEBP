package ro.upt.pcbe.common;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class CommonTypes {

    private CommonTypes() {
        // Private constructor to prevent instantiation of utility container class
    }

    // Reprezintă un act și actele necesare înainte de a-l putea obține
    public record Document(String id, String officeId, Set<String> prerequisites) {}

    // Tichetul de schimb: clientul îl depune în coadă, ghișeul îl completează
    public record ServiceTicket(
            String clientId,
            String documentId,
            CompletableFuture<Boolean> completionSignal
    ) {}
}
