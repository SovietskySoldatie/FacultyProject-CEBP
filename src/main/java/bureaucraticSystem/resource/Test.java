package bureaucraticSystem.resource;

import bureaucraticSystem.client.Client;
import bureaucraticSystem.counter.DeskWorker;
import bureaucraticSystem.counter.DeskWorkerSafe;
import bureaucraticSystem.counter.OfficeQueue;

import java.util.ArrayList;
import java.util.List;

public class Test {
    static void useCounterAndStamp(OfficialStamp stamp, DocumentCounter counter) throws InterruptedException {
        OfficeQueue queue = new OfficeQueue();

        DeskWorker worker1 = new DeskWorkerSafe("Desk-1-StareaCivila", queue, null, stamp, counter, true);
        DeskWorker worker2 = new DeskWorkerSafe("Desk-2-StareaCivila", queue, null, stamp, counter, false);

        Thread t1 = new Thread(worker1, worker1.getDeskId());
        Thread t2 = new Thread(worker2, worker2.getDeskId());

        t1.start();
        t2.start();

        System.out.println("--- Ghiseele au fost deschise. Asteptam clientii... ---\n");
        Thread.sleep(500);

        List<Thread> clientThreads = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            // Nu folosim RouteResolver complet pentru demo, doar dăm actul direct în coadă
            Client client = new Client("Cetatean-" + i, "CertificatNastere", queue);
            Thread ct = new Thread(client, "Thread-" + client.getClientId());
            clientThreads.add(ct);
            ct.start();
        }

        for (Thread ct : clientThreads) {
            ct.join();
        }

        System.out.println("\n--- Toti clientii au primit actul! ---");

        worker1.stop();
        worker2.stop();
        t1.interrupt();
        t2.interrupt();
    }
}
