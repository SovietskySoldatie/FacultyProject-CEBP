package bureaucraticSystem.config.data;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registru thread-safe pentru stocarea și accesarea documentelor din sistemul
 * birocratic.
 * Permite birourilor și clienților să interogheze actele disponibile și
 * precondițiile lor.
 */

public class DocumentRegistry {
    private final Map<String, Document> documents = new ConcurrentHashMap<>();

    /**
     * Înregistrează un document în registru.
     */
    public void registerDocument(Document doc) {
        if (doc == null || doc.getId() == null) {
            throw new IllegalArgumentException("Documentul și ID-ul nu pot fi null");
        }
        documents.put(doc.getId(), doc);
    }

    /**
     * Înregistrează mai multe documente simultan.
     */
    public void registerDocuments(Document... docs) {
        if (docs != null) {
            for (Document doc : docs) {
                registerDocument(doc);
            }
        }
    }

    /**
     * Returnează documentul cu ID-ul specificat sau null dacă nu există / dacă
     * ID-ul este null.
     */
    public Document getDocument(String id) {
        if (id == null) {
            return null;
        }
        return documents.get(id);
    }

    /**
     * Verifică dacă un document cu ID-ul dat este înregistrat.
     */
    public boolean containsDocument(String id) {
        return id != null && documents.containsKey(id);
    }

    public Map<String, Document> getAll() {
        return Collections.unmodifiableMap(documents);
    }
}
