package bureaucraticSystem.config.graph;

import bureaucraticSystem.config.data.Document;
import bureaucraticSystem.config.data.DocumentRegistry;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Rezolvitor singleton thread-safe pentru calculul lanțurilor de dependențe
 * între documente în cadrul sistemului birocratic.
 */

//test test
public class RouteResolver {
    private static volatile RouteResolver instance;
    private static final Object LOCK = new Object();

    private volatile DocumentRegistry registry;

    // Constructor privat conform pattern-ului Singleton
    private RouteResolver() {
        this(null);
    }

    private RouteResolver(DocumentRegistry registry) {
        this.registry = registry != null ? registry : new DocumentRegistry();
    }

    /**
     * Returnează instanța unică (Singleton) a RouteResolver-ului.
     */
    public static RouteResolver getInstance() {
        return getInstance(null);
    }

    /**
     * Returnează sau configurează instanța unică cu un DocumentRegistry specificat.
     */
    public static RouteResolver getInstance(DocumentRegistry registry) {
        RouteResolver localInstance = instance;
        if (localInstance == null) {
            synchronized (LOCK) {
                localInstance = instance;
                if (localInstance == null) {
                    instance = localInstance = new RouteResolver(registry);
                } else if (registry != null) {
                    localInstance.registry = registry;
                }
            }
        } else if (registry != null && localInstance.registry != registry) {
            localInstance.registry = registry;
        }
        return localInstance;
    }

    /**
     * Reinițializează instanța Singleton (util pentru Unit Tests).
     */
    public static void resetInstance() {
        synchronized (LOCK) {
            instance = null;
        }
    }

    public DocumentRegistry getRegistry() {
        return registry;
    }

    public void setRegistry(DocumentRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /**
     * Metodă statică de dataflow: determină primul document necesar folosind registrul din instanța Singleton.
     */
    public static Optional<Document> resolveNextRequiredDocument(Set<String> ownedDocs, String targetDocId) {
        return getInstance().resolve(ownedDocs, targetDocId);
    }

    /**
     * Metodă statică de dataflow pur: determină primul document necesar pentru un DocumentRegistry specificat.
     */
    public static Optional<Document> resolveNextRequiredDocument(DocumentRegistry registry, Set<String> ownedDocs, String targetDocId) {
        return resolveRecursive(registry, ownedDocs, targetDocId, new HashSet<>());
    }

    /**
     * Metodă instanță de conveniență.
     */
    public Optional<Document> resolve(Set<String> ownedDocs, String targetDocId) {
        return resolveRecursive(this.registry, ownedDocs, targetDocId, new HashSet<>());
    }

    private static Optional<Document> resolveRecursive(DocumentRegistry registry,
                                                       Set<String> ownedDocs,
                                                       String currentDocId,
                                                       Set<String> visiting) {
        if (registry == null) {
            return Optional.empty();
        }

        Document target = registry.getDocument(currentDocId);
        if (target == null) {
            return Optional.empty();
        }

        // Protecție împotriva buclelor infinite (dependențe circulare A -> B -> A)
        if (!visiting.add(currentDocId)) {
            throw new IllegalStateException("Dependență circulară detectată pentru actul: " + currentDocId);
        }

        for (String reqId : target.getPrerequisites()) {
            if (!ownedDocs.contains(reqId)) {
                // Dacă precondiția are la rândul ei dependențe nesatisfăcute, coborâm recursiv
                return resolveRecursive(registry, ownedDocs, reqId, visiting);
            }
        }

        visiting.remove(currentDocId);
        return Optional.of(target);
    }
}
