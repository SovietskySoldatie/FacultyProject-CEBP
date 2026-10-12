package bureaucraticSystem.client.request;

import java.util.concurrent.CompletableFuture;

// Tichetul de schimb: clientul îl depune în coadă, ghișeul îl completează
public record ServiceTicket(
        String clientId,
        String documentId,
        CompletableFuture<Boolean> completionSignal
) {}
