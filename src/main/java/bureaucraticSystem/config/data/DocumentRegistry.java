package bureaucraticSystem.config.data;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DocumentRegistry {
    private final Map<String, Document> documents = new ConcurrentHashMap<>();

    public void registerDocument(Document doc) {
        if (doc == null || doc.id() == null) {
            throw new IllegalArgumentException("Documentul și ID-ul nu pot fi null");
        }
        documents.put(doc.id(), doc);
    }

    public Document getDocument(String id) {
        return documents.get(id);
    }

    public Map<String, Document> getAll() {
        return Collections.unmodifiableMap(documents);
    }
}
