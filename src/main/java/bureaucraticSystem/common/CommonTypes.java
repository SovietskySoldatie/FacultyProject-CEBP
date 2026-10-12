package bureaucraticSystem.common;

import java.util.Objects;
import java.util.Set;

/**
 * Tipuri de date partajate între toate modulele sistemului birocratic.
 * Permite compilarea și rularea independentă a componentelor fiecărui membru.
 */
public final class CommonTypes {

    private CommonTypes() {
        // Previne instanțierea clasei de utilitare/tipuri
    }

    // Reprezintă un act și actele necesare înainte de a-l putea obține
    public static class Document {
        private final String id;
        private final String officeId;
        private final Set<String> prerequisites;

        public Document(String id, String officeId, Set<String> prerequisites) {
            this.id = Objects.requireNonNull(id, "id must not be null");
            this.officeId = Objects.requireNonNull(officeId, "officeId must not be null");
            this.prerequisites = prerequisites == null ? Set.of() : Set.copyOf(prerequisites);
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
}
