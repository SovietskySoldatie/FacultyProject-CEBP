package bureaucraticSystem.office;

import bureaucraticSystem.common.CommonTypes.ServiceTicket;

import java.util.function.Consumer;

/**
 * Reprezintă un ghișeu individual dintr-un birou, care rulează pe propriul său fir de execuție (Thread).
 * Extrage tichete din OfficeQueue și le rezolvă.
 */
public class DeskWorker implements Runnable {
    private final String deskId;
    private final OfficeQueue queue;
    private final Consumer<ServiceTicket> processCallback;
    private volatile boolean running = true;

    /**
     * Constructor simplu: procesare implicită.
     * Simulează un scurt timp de lucru (150ms), apoi validează tichetul.
     */
    public DeskWorker(String deskId, OfficeQueue queue) {
        this(deskId, queue, ticket -> {
            try {
                Thread.sleep(150); // simulează timpul necesar eliberării actului
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // Notifică clientul că actul este finalizat cu succes
            ticket.completionSignal().complete(true);
        });
    }

    /**
     * Constructor flexibil: permite specificarea unei logici externe de procesare
     * (de exemplu, când se adaugă ștampila unică sau verificarea pauzei de cafea).
     */
    public DeskWorker(String deskId, OfficeQueue queue, Consumer<ServiceTicket> processCallback) {
        this.deskId = deskId;
        this.queue = queue;
        this.processCallback = processCallback;
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
                // Așteaptă la coadă un tichet (operație blocantă thread-safe)
                ServiceTicket ticket = queue.takeTicket();
                System.out.println(deskId + " a preluat clientul: " + ticket.clientId());

                // Procesează cererea
                processCallback.accept(ticket);
            } catch (InterruptedException e) {
                // Dacă ghișeul este întrerupt în timp ce aștepta în coadă, restaurăm flag-ul și ieșim curat
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("Ghișeul " + deskId + " s-a închis.");
    }
}
