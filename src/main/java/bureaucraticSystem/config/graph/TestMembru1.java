package bureaucraticSystem.config.graph;

import bureaucraticSystem.config.data.Document;
import bureaucraticSystem.config.data.DocumentRegistry;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TestMembru1 {
    public static void main(String[] args) throws InterruptedException {
        DocumentRegistry reg = new DocumentRegistry();

        // A -> B -> C (pentru C ai nevoie de B; pentru B ai nevoie de A)
        reg.registerDocument(new Document("DocA", "Office1", Set.of()));
        reg.registerDocument(new Document("DocB", "Office2", Set.of("DocA")));
        reg.registerDocument(new Document("DocC", "Office3", Set.of("DocB")));

        RouteResolver resolver = RouteResolver.getInstance(reg);

        ExecutorService exec = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 4; i++) {
            exec.submit(() -> {
                Set<String> owned = new HashSet<>();
                var next = resolver.resolveNextRequiredDocument(owned, "DocC");
                System.out.println(Thread.currentThread().getName() + " -> Primul act necesar: " + next.get().getId());
            });
        }

        exec.shutdown();
        exec.awaitTermination(2, TimeUnit.SECONDS);
    }
}
