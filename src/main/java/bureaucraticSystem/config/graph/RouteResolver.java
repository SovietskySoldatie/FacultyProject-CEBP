package bureaucraticSystem.config.graph;

import bureaucraticSystem.config.data.Document;
import bureaucraticSystem.config.data.DocumentRegistry;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class RouteResolver {
    private final DocumentRegistry registry;

    public RouteResolver(DocumentRegistry registry) {
        this.registry = registry;
    }

    // Întoarce primul document lipsă din lanțul de dependențe
    public Optional<Document> resolveNextRequiredDocument(Set<String> ownedDocs, String targetDocId) {
        return resolveRecursive(ownedDocs, targetDocId, new HashSet<>());
    }

    private Optional<Document> resolveRecursive(Set<String> ownedDocs, String currentDocId, Set<String> visiting) {
        Document target = registry.getDocument(currentDocId);
        if (target == null) {
            return Optional.empty();
        }

        // Protecție împotriva buclelor infinite (dependențe circulare A -> B -> A)
        if (!visiting.add(currentDocId)) {
            throw new IllegalStateException("Dependență circulară detectată pentru actul: " + currentDocId);
        }

        for (String reqId : target.prerequisites()) {
            if (!ownedDocs.contains(reqId)) {
                // Dacă precondiția are la rândul ei dependențe nesatisfăcute, coborâm recursiv
                return resolveRecursive(ownedDocs, reqId, visiting);
            }
        }

        visiting.remove(currentDocId);
        return Optional.of(target);
    }
}
