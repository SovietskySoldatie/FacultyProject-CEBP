package bureaucraticSystem.office;

import bureaucraticSystem.common.CommonTypes.ServiceTicket;

/**
 * Test simplu și clar pentru Membru 2:
 * Demonstrăm cum funcționează coada (OfficeQueue) și ghișeul (DeskWorker).
 *
 * Scenariul din test:
 * 1. Se deschide ghișeul "Desk-1" pe un fir de execuție separat.
 * 2. Trei clienți vin pe rând, își depun cererea la coadă și așteaptă la rând.
 * 3. Ghișeul preia cererea, o rezolvă și marchează tichetul ca finalizat.
 * 4. Clientul își ia actul și pleacă.
 * 5. Ghișeul se închide curat.
 */
public class TestMember2 {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Pornire TestMember2: Cozi & Ghișee ===\n");

        // 1. Creăm coada biroului
        OfficeQueue queue = new OfficeQueue();

        // 2. Creăm ghișeul și îl pornim pe un fir de execuție separat (Thread dedicat)
        DeskWorker desk = new DeskWorker("Desk-1", queue);
        Thread deskThread = new Thread(desk, "Thread-Desk-1");
        deskThread.start();

        // 3. Trimitem 3 clienți pe rând la birou
        for (int i = 1; i <= 3; i++) {
            String clientName = "Client-" + i;

            // Clientul completează un tichet cu numele lui și actul dorit
            ServiceTicket tichet = new ServiceTicket(clientName, "CertificatNastere");

            System.out.println(">> " + clientName + " se așază la coadă.");
            queue.addTicket(tichet);

            // Clientul stă la rând până când ghișeul finalizează actul
            boolean actFinalizat = tichet.waitForCompletion();

            if (actFinalizat) {
                System.out.println("<< " + clientName + " și-a primit actul și pleacă mulțumit!\n");
            }
        }

        // 4. Toți clienții au fost serviți. Închidem ghișeul curat (graceful shutdown)
        desk.stop();              // setăm running = false
        deskThread.interrupt();   // deblocăm ghișeul dacă aștepta în coadă
        deskThread.join(2000);    // așteptăm să se oprească firul

        System.out.println("=== TestMember2 finalizat cu succes! ===");
    }
}
