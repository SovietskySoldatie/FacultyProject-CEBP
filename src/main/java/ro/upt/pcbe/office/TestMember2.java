package ro.upt.pcbe.office;

import ro.upt.pcbe.common.CommonTypes.ServiceTicket;

import java.util.concurrent.CompletableFuture;

/**
 * Independent test runner to validate Member 2 deliverables:
 *  - OfficeQueue (thread-safe blocking queue based on LinkedBlockingQueue)
 *  - DeskWorker (worker thread continuously taking and processing tickets)
 *  - Asynchronous handshake completion using CompletableFuture
 *
 * Compatible with JDK 25 and modern Java.
 */
public class TestMember2 {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Starting TestMember2: Office Queue & Desk Workers ===");
        OfficeQueue queue = new OfficeQueue();

        // Dummy processing callback: simulates 200ms processing time, then completes the ticket
        DeskWorker desk1 = new DeskWorker("Desk-1", queue, ticket -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            ticket.completionSignal().complete(true);
        });

        Thread deskThread = new Thread(desk1, "Thread-Desk-1");
        deskThread.start();

        // Submit 3 mock requests into the office queue
        for (int i = 1; i <= 3; i++) {
            final int clientId = i;
            CompletableFuture<Boolean> signal = new CompletableFuture<>();
            queue.addTicket(new ServiceTicket("Client-" + clientId, "DocA", signal));
            signal.thenRun(() -> System.out.println("Client-" + clientId + " notified: document issued successfully!"));
        }

        // Wait to allow the desk worker to process all submitted tickets
        Thread.sleep(1000);

        // Graceful shutdown of the desk worker
        desk1.stop();
        deskThread.interrupt();
        deskThread.join(2000);

        System.out.println("=== TestMember2 completed successfully! ===");
    }
}
