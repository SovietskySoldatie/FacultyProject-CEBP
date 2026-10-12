package bureaucraticSystem.config.data;

import java.util.Objects;
import java.util.Set;

/**
 * Reprezintă un document din sistemul birocratic.
 * Clasă imutabilă și thread-safe ce conține identificatorul documentului,
 * biroul emitent și setul de documente precondiție.
 */
public class Document {
    private final String id;
    private final String officeId;
    private final Set<String> prerequisites;

    public Document(String id, String officeId, Set<String> prerequisites) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.officeId = Objects.requireNonNull(officeId, "officeId must not be null");
        this.prerequisites = (prerequisites == null) ? Set.of() : Set.copyOf(prerequisites);
    }

    public String getId() {
        return id;
    }

    public String getOfficeId() {
        return officeId;
    }

    public Set<String> getPrerequisites() {
        return prerequisites;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Document document = (Document) o;
        return Objects.equals(id, document.id) &&
                Objects.equals(officeId, document.officeId) &&
                Objects.equals(prerequisites, document.prerequisites);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, officeId, prerequisites);
    }

    @Override
    public String toString() {
        return "Document{" +
                "id='" + id + '\'' +
                ", officeId='" + officeId + '\'' +
                ", prerequisites=" + prerequisites +
                '}';
    }
}
