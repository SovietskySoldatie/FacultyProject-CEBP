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
    private final int intID;

    /**
     * Constructor pentru documente fără precondiții (acte de bază).
     */
    public Document(String id, String officeId) {
        this(id, officeId, Set.of(), 0);
    }

    public Document(String id, String officeId, Set<String> prerequisites) {
        this(id, officeId, prerequisites, 0);
    }

    public Document(String id, String officeId, Set<String> prerequisites, int intID) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.officeId = Objects.requireNonNull(officeId, "officeId must not be null");
        if (this.id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (this.officeId.isBlank()) {
            throw new IllegalArgumentException("officeId must not be blank");
        }
        this.prerequisites = (prerequisites == null) ? Set.of() : Set.copyOf(prerequisites);
        this.intID = intID;
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

    public int getIntID() {
        return intID;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Document document = (Document) o;
        return intID == document.intID &&
                Objects.equals(id, document.id) &&
                Objects.equals(officeId, document.officeId) &&
                Objects.equals(prerequisites, document.prerequisites);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, officeId, prerequisites, intID);
    }

    @Override
    public String toString() {
        return "Document{" +
                "id='" + id + '\'' +
                ", officeId='" + officeId + '\'' +
                ", prerequisites=" + prerequisites +
                ", intID=" + intID +
                '}';
    }
}
