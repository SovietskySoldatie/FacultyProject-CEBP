package bureaucraticSystem.config.data;

import java.util.Set;

public record Document(String id, String officeId, Set<String> prerequisites) {
    public Document {
        prerequisites = (prerequisites == null) ? Set.of() : Set.copyOf(prerequisites);
    }
}
