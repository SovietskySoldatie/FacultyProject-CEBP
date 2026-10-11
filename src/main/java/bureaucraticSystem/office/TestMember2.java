package bureaucraticSystem.office;

import bureaucraticSystem.common.CommonTypes.ServiceTicket;

import java.util.concurrent.CompletableFuture;

/**
 * Test simplu și clar pentru Membru 2:
 * Demonstrăm cum funcționează coada (OfficeQueue) și ghișeul (DeskWorker).
 *
 * Scenariul din test:
 * 1. Se deschide ghișeul "Desk-1" pe un fir de execuție separat.
 * 2. Trei clienți vin pe rând, își depun cererea la coadă și așteaptă biletul de ordine.
 * 3. Ghișeul preia cererea, o rezolvă și anunță clientul.
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

            // Fiecare client își creează un bilet de ordine / bipăitor (CompletableFuture)
            // La început este gol (actul nu e gata încă)
            CompletableFuture<Boolean> biletDeOrdine = new CompletableFuture<>();

            // Clientul completează tichetul de cerere cu numele lui, actul dorit și biletul de ordine
            ServiceTicket tichet = new ServiceTicket(clientName, "CertificatNastere", biletDeOrdine);

            System.out.println(">> " + clientName + " se așază la coadă.");
            queue.addTicket(tichet);

            // Clientul așteaptă până când sună biletul de ordine!
            // Metoda .get() pune firul curent în pauză până când ghișeul apelează complete(true)
            boolean actFinalizat = biletDeOrdine.get();

            if (actFinalizat) {
                System.out.println("<< " + clientName + " și-a primit actul și pleacă mulțumit!\n");
            }
        }

        // 4. Toți clienții au fost serviți. Închidem ghișeul curat (graceful shutdown)
        desk.stop();              // setăm running = false
        deskThread.interrupt();   // deblocăm ghișeul dacă aștepta în queue.takeTicket()
        deskThread.join(2000);    // așteptăm maxim 2 secunde ca firul să se termine frumos

        System.out.println("=== TestMember2 finalizat cu succes! ===");
    }
}
