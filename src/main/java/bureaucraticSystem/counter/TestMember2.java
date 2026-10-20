package bureaucraticSystem.counter;

import bureaucraticSystem.client.request.ServiceTicket;

import java.util.ArrayList;
import java.util.List;

/**
 * Test independent pentru Membru 2 (Birouri, Cozi și Ghișee).
 *
 * Validează cerințele de bază ale laboratorului:
 *  1. Coada blocantă OfficeQueue (LinkedBlockingQueue fără busy-waiting).
 *  2. Modelul Producer-Consumer: 2 ghișee paralele care deservesc simultan aceeași coadă.
 *  3. Handshake-ul asincron prin ServiceTicket (waitForCompletion / markCompleted).
 *  4. Oprirea curată a worker-ilor (Graceful Shutdown prin stop() cu auto-interrupt).
 */
public class TestMember2 {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Pornire TestMember2: Cozi & Ghisee (Worker Pool Paralel) ===\n");

        // 1. Creăm coada unică a biroului
        OfficeQueue queue = new OfficeQueue();

        // 2. Deschidem două ghișee paralele la același birou (cerință oficială din temă)
        DeskWorker desk1 = new DeskWorker("Desk-1", queue);
        DeskWorker desk2 = new DeskWorker("Desk-2", queue);

        Thread threadDesk1 = new Thread(desk1, "Thread-Desk-1");
        Thread threadDesk2 = new Thread(desk2, "Thread-Desk-2");

        threadDesk1.start();
        threadDesk2.start();

        // 3. Adăugăm toate cele 4 tichete în coadă simultan pentru ca ambele ghișee să lucreze în paralel
        List<ServiceTicket> tickets = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            String clientName = "Client-" + i;
            ServiceTicket tichet = new ServiceTicket(clientName, "CertificatNastere");
            tickets.add(tichet);

            System.out.println(">> " + clientName + " se aseaza la coada biroului.");
            queue.addTicket(tichet);
        }

        // 4. Așteptăm finalizarea fiecărui tichet procesat în paralel de cele două ghișee
        for (ServiceTicket tichet : tickets) {
            boolean actFinalizat = tichet.waitForCompletion();
            if (actFinalizat) {
                System.out.println("<< " + tichet.getClientId() + " si-a primit actul si pleaca multumit!");
            }
        }

        // 5. Închidere curată a ambelor ghișee (stop() setează running=false și trezește automat firul blocat)
        desk1.stop();
        desk2.stop();

        threadDesk1.join(2000);
        threadDesk2.join(2000);

        System.out.println("\n=== TestMember2 finalizat cu succes! ===");
    }
}
