package bureaucraticSystem.office;

import bureaucraticSystem.common.CommonTypes.ServiceTicket;

/**
 * Reprezintă un ghișeu individual dintr-un birou, care rulează pe propriul său fir de execuție (Thread).
 * Extrage tichete din OfficeQueue și le rezolvă într-un mod simplu și direct.
 */
public class DeskWorker implements Runnable {
    private final String deskId;
    private final OfficeQueue queue;
    private volatile boolean running = true;

    public DeskWorker(String deskId, OfficeQueue queue) {
        this.deskId = deskId;
        this.queue = queue;
    }

    /**
     * Procesează cererea unui client:
     *  - simulează timpul de lucru al funcționarului (150ms)
     *  - marchează tichetul ca fiind finalizat cu succes
     */
    protected void processTicket(ServiceTicket ticket) throws InterruptedException {
        // Simulează redactarea și verificarea documentului
        Thread.sleep(150);

        // Notifică clientul că actul este gata
        ticket.markCompleted();
    }

    /**
     * Oprește bucla de lucru a ghișeului.
     * Variabila 'running' este volatile pentru a asigura vizibilitatea imediată între thread-uri.
     */
    public void stop() {
        this.running = false;
    }

    public boolean isRunning() {
        return running;
    }

    public String getDeskId() {
        return deskId;
    }

    @Override
    public void run() {
        System.out.println("Ghișeul " + deskId + " s-a deschis.");
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // 1. Așteaptă la coadă un tichet (operație blocantă thread-safe)
                ServiceTicket ticket = queue.takeTicket();
                System.out.println(deskId + " a preluat clientul: " + ticket.clientId());

                // 2. Procesează direct tichetul
                processTicket(ticket);

            } catch (InterruptedException e) {
                // Dacă ghișeul este întrerupt în timp ce aștepta în coadă sau procesa actul,
                // restaurăm fanionul de întrerupere și ieșim curat
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("Ghișeul " + deskId + " s-a închis.");
    }
}
