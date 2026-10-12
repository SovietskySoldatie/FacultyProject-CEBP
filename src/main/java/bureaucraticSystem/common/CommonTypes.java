package bureaucraticSystem.common;

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
    public record Document(String id, String officeId, Set<String> prerequisites) {}
}
