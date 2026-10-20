package bureaucraticSystem.counter;

import bureaucraticSystem.client.request.ServiceTicket;
import bureaucraticSystem.resource.CoffeeBreakScheduler;
import bureaucraticSystem.resource.OfficialStamp;

import java.util.Objects;

/**
 * Reprezintă un ghișeu administrativ individual care rulează pe propriul său fir de execuție (Thread).
 *
 * Funcționează ca un Worker într-un Worker Pool:
 *  - Verifică starea pauzei de cafea înainte de a extrage un tichet din coadă (astfel încât un ghișeu
 *    aflat în pauză să nu blocheze un tichet pe care l-ar putea prelua un alt ghișeu activ).
 *  - Extrage continuu tichete din OfficeQueue (operație blocantă takeTicket).
 *  - Solicită acces exclusiv la resursa partajată (OfficialStamp).
 *  - Notifică asincron clientul când actul este eliberat (ticket.markCompleted()), sau garantează
 *    deblocarea clientului prin ticket.markFailed() în blocul finally în caz de întrerupere/excepție.
 *  - Permite oprire curată (graceful shutdown) prin variabila volatile 'running' și auto-întrerupere în stop().
 */
public class DeskWorker implements Runnable {

    public static final long COFFEE_BREAK_POLL_MS = 300L;
    public static final long DRAFTING_DURATION_MS = 150L;
    public static final long STAMP_DURATION_MS = 200L;

    private final String deskId;
    private final OfficeQueue queue;
    private final CoffeeBreakScheduler breakScheduler;
    private final OfficialStamp stamp;

    private volatile boolean running = true;
    private volatile Thread workerThread;

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
     * Așteaptă cât timp ghișeul se află în pauză de cafea.
     */
    protected void waitWhileOnCoffeeBreak() throws InterruptedException {
        while (running && breakScheduler != null && breakScheduler.isOnBreak(deskId)) {
            System.out.println("☕ [" + deskId + "] este în pauză de cafea, așteaptă...");
            Thread.sleep(COFFEE_BREAK_POLL_MS);
        }
    }

    /**
     * Logica de procesare a unui tichet:
     *  1. Verifică dacă a intervenit o pauză de cafea imediat după preluarea tichetului.
     *  2. Simulează timpul de lucru pentru redactarea documentului (DRAFTING_DURATION_MS).
     *  3. Preia și aplică ștampila unică a instituției (STAMP_DURATION_MS).
     *  4. Notifică clientul că documentul a fost eliberat cu succes.
     */
    protected void processTicket(ServiceTicket ticket) throws InterruptedException {
        // Pasul 1: Dacă s-a declanșat pauza de cafea, ghișeul așteaptă finalizarea ei
        waitWhileOnCoffeeBreak();

        // Pasul 2: Simulează redactarea și verificarea documentului
        Thread.sleep(DRAFTING_DURATION_MS);

        // Pasul 3: Solicită acces exclusiv la ștampila oficială partajată
        if (stamp != null) {
            stamp.useStamp(deskId, STAMP_DURATION_MS);
        }

        // Pasul 4: Notifică clientul că actul este gata
        ticket.markCompleted();
    }

    /**
     * Oprește bucla de lucru a ghișeului și trezește imediat firul de execuție
     * dacă acesta este blocat în așteptare la coadă (queue.takeTicket()).
     * Variabila 'running' este volatile pentru a garanta vizibilitatea imediată
     * a modificării între toate nucleele procesorului (JMM Memory Visibility).
     */
    public void stop() {
        this.running = false;
        Thread thread = this.workerThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public String getDeskId() {
        return deskId;
    }

    @Override
    public void run() {
        this.workerThread = Thread.currentThread();
        System.out.println("Ghișeul " + deskId + " s-a deschis.");

        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                ServiceTicket ticket = null;
                boolean completed = false;
                try {
                    // 1. Verificăm pauza de cafea ÎNAINTE de a extrage tichetul din coadă,
                    // pentru a lăsa alte ghișee libere să preia tichetele din coada comună.
                    waitWhileOnCoffeeBreak();
                    if (!running || Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    // 2. Așteaptă blocant la coadă un tichet (fără consum CPU)
                    ticket = queue.takeTicket();
                    if (ticket == null) {
                        continue;
                    }
                    System.out.println(deskId + " a preluat clientul: " + ticket.getClientId());

                    // 3. Procesează efectiv cererea
                    processTicket(ticket);
                    completed = true;

                } catch (InterruptedException e) {
                    // Restaurăm fanionul de întrerupere conform bunelor practici Java Concurrency
                    Thread.currentThread().interrupt();
                    break;
                } finally {
                    // Garantăm deblocarea clientului chiar și în caz de InterruptedException sau RuntimeException
                    if (ticket != null && !completed) {
                        ticket.markFailed();
                    }
                }
            }
        } finally {
            this.workerThread = null;
            System.out.println("Ghișeul " + deskId + " s-a închis.");
        }
    }
}
