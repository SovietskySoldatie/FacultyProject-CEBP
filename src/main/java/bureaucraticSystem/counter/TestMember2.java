package bureaucraticSystem.counter;

import bureaucraticSystem.client.request.ServiceTicket;

/**
 * Test independent pentru Membru 2 (Birouri, Cozi și Ghișee).
 *
 * Validează cerințele de bază ale laboratorului:
 *  1. Coada blocantă OfficeQueue (LinkedBlockingQueue fără busy-waiting).
 *  2. Modelul Producer-Consumer: 2 ghișee paralele care deservesc aceeași coadă.
 *  3. Handshake-ul asincron prin ServiceTicket (waitForCompletion / markCompleted).
 *  4. Oprirea curată a worker-ilor (Graceful Shutdown cu volatile și interrupt).
 */
public class TestMember2 {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Pornire TestMember2: Cozi & Ghișee (Worker Pool) ===\n");

        // 1. Creăm coada unică a biroului
        OfficeQueue queue = new OfficeQueue();

        // 2. Deschidem două ghișee paralele la același birou (cerință oficială din temă)
        DeskWorker desk1 = new DeskWorker("Desk-1", queue);
        DeskWorker desk2 = new DeskWorker("Desk-2", queue);

        Thread threadDesk1 = new Thread(desk1, "Thread-Desk-1");
        Thread threadDesk2 = new Thread(desk2, "Thread-Desk-2");

        threadDesk1.start();
        threadDesk2.start();

        // 3. Trimitem 4 clienți la birou
        for (int i = 1; i <= 4; i++) {
            String clientName = "Client-" + i;
            ServiceTicket tichet = new ServiceTicket(clientName, "CertificatNastere");

            System.out.println(">> " + clientName + " se așază la coada biroului.");
            queue.addTicket(tichet);

            // Clientul așteaptă până când unul dintre cele două ghișee îi rezolvă actul
            boolean actFinalizat = tichet.waitForCompletion();

            if (actFinalizat) {
                System.out.println("<< " + clientName + " și-a primit actul și pleacă mulțumit!\n");
            }
        }

        // 4. Închidere curată a ambelor ghișee (Graceful Shutdown)
        desk1.stop();
        desk2.stop();

        threadDesk1.interrupt();
        threadDesk2.interrupt();

        threadDesk1.join(2000);
        threadDesk2.join(2000);

        System.out.println("=== TestMember2 finalizat cu succes! ===");
    }
}
