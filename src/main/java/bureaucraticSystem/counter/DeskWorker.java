package bureaucraticSystem.counter;

import bureaucraticSystem.client.request.ServiceTicket;
import bureaucraticSystem.resource.CoffeeBreakScheduler;
import bureaucraticSystem.resource.OfficialStamp;

import java.util.Objects;

/**
 * Reprezintă un ghișeu administrativ individual care rulează pe propriul său fir de execuție (Thread).
 *
 * Funcționează ca un Worker într-un Worker Pool:
 *  - Extrage continuu tichete din OfficeQueue (operație blocantă takeTicket).
 *  - Respectă întreruperile pentru pauza de cafea (CoffeeBreakScheduler).
 *  - Solicită acces exclusiv la resursa partajată (OfficialStamp).
 *  - Notifică asincron clientul când actul este eliberat (ticket.markCompleted()).
 *  - Permite oprire curată (graceful shutdown) prin variabila volatile 'running' și semnal de întrerupere.
 */
public class DeskWorker implements Runnable {

    private final String deskId;
    private final OfficeQueue queue;
    private final CoffeeBreakScheduler breakScheduler;
    private final OfficialStamp stamp;

    private volatile boolean running = true;

    /**
     * Constructor simplificat: util pentru teste izolate (fără pauză de cafea și ștampilă externă).
     */
    public DeskWorker(String deskId, OfficeQueue queue) {
        this(deskId, queue, null, null);
    }

    /**
     * Constructor complet: integrează planificatorul de pauze și ștampila unică a instituției.
     */
    public DeskWorker(String deskId, OfficeQueue queue, CoffeeBreakScheduler breakScheduler, OfficialStamp stamp) {
        this.deskId = Objects.requireNonNull(deskId, "deskId nu poate fi null");
        this.queue = Objects.requireNonNull(queue, "queue nu poate fi null");
        this.breakScheduler = breakScheduler;
        this.stamp = stamp;
    }

    /**
     * Logica de procesare a unui tichet:
     *  1. Verifică și așteaptă dacă ghișeul este în pauză de cafea.
     *  2. Simulează timpul de lucru pentru redactarea documentului (150ms).
     *  3. Preia și aplică ștampila unică a instituției (200ms).
     *  4. Notifică clientul că documentul a fost eliberat cu succes.
     */
    protected void processTicket(ServiceTicket ticket) throws InterruptedException {
        // Pasul 1: Dacă este programată pauza de cafea, ghișeul așteaptă finalizarea ei
        while (breakScheduler != null && breakScheduler.isOnBreak(deskId)) {
            System.out.println("☕ [" + deskId + "] este în pauză de cafea, cererea așteaptă...");
            Thread.sleep(300);
        }

        // Pasul 2: Simulează redactarea și verificarea documentului
        Thread.sleep(150);

        // Pasul 3: Solicită acces exclusiv la ștampila oficială partajată
        if (stamp != null) {
            stamp.useStamp(deskId, 200);
        }

        // Pasul 4: Notifică clientul că actul este gata
        ticket.markCompleted();
    }

    /**
     * Oprește bucla de lucru a ghișeului.
     * Variabila 'running' este volatile pentru a garanta vizibilitatea imediată
     * a modificării între toate nucleele procesorului (JMM Memory Visibility).
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
            ServiceTicket ticket = null;
            try {
                // 1. Așteaptă blocant la coadă un tichet (fără consum CPU)
                ticket = queue.takeTicket();
                System.out.println(deskId + " a preluat clientul: " + ticket.getClientId());

                // 2. Procesează efectiv cererea
                processTicket(ticket);

            } catch (InterruptedException e) {
                // Dacă firul este întrerupt în timpul procesării, deblocăm clientul cu eșec
                if (ticket != null) {
                    ticket.markFailed();
                }
                // Restaurăm fanionul de întrerupere conform bunelor practici Java Concurrency
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("Ghișeul " + deskId + " s-a închis.");
    }
}
