package ro.upt.pcbe.office;

import ro.upt.pcbe.common.CommonTypes.ServiceTicket;

import java.util.function.Consumer;

/**
 * Reprezintă un ghișeu individual dintr-un birou, rulat pe un fir de execuție dedicat (Thread).
 *
 * Funcționează ca un Worker într-un Worker Pool:
 *  - Consumă tichete din OfficeQueue prin apelul blocant takeTicket()
 *  - Procesează fiecare cerere delegând logica către processCallback
 *  - Suportă oprire curată (graceful shutdown) prin flag-ul volatile running și semnal de întrerupere
 */
public class DeskWorker implements Runnable {
    private final String deskId;
    private final OfficeQueue queue;
    private final Consumer<ServiceTicket> processCallback;
    private volatile boolean running = true;

    public DeskWorker(String deskId, OfficeQueue queue, Consumer<ServiceTicket> processCallback) {
        this.deskId = deskId;
        this.queue = queue;
        this.processCallback = processCallback;
    }

    /**
     * Oprește bucla de lucru a ghișeului.
     * Variabila running este marcată cu 'volatile' pentru a garanta vizibilitatea modificării
     * între nucleele CPU / firele de execuție fără caching local.
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
                // Așteptare blocantă a următorului tichet din coadă
                ServiceTicket ticket = queue.takeTicket();
                System.out.println(deskId + " a preluat clientul: " + ticket.clientId());

                // Prelucrare efectivă a cererii (sau semnalare, ștampilare, etc.)
                processCallback.accept(ticket);
            } catch (InterruptedException e) {
                // Dacă firul era blocat în takeTicket() și a fost trimis interrupt(),
                // restaurăm flag-ul de întrerupere și ieșim din buclă curat.
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("Ghișeul " + deskId + " s-a închis.");
    }
}
