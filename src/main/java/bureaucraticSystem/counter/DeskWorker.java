package bureaucraticSystem.counter;

import bureaucraticSystem.client.request.ServiceTicket;
import bureaucraticSystem.resource.CoffeeBreakScheduler;
import bureaucraticSystem.resource.DocumentCounter;
import bureaucraticSystem.resource.OfficialStamp;

import java.util.Objects;

/**
 * Reprezintă un ghișeu administrativ individual care rulează pe propriul său fir de execuție (Thread).
 *
 * Funcționează ca un Worker într-un Worker Pool:
 *  - Extrage continuu tichete din OfficeQueue (operație blocantă takeTicket).
 *  - Respectă întreruperile pentru pauza de cafea (CoffeeBreakScheduler).
 *  - Solicită acces exclusiv la resursele partajate (OfficialStamp și DocumentCounter) prin Dependency Injection.
 *  - Notifică asincron clientul când actul este eliberat (ticket.markCompleted()).
 *  - Permite oprire curată (graceful shutdown) prin variabila volatile 'running' și semnal de întrerupere.
 */
public class DeskWorker implements Runnable {

    private final String deskId;
    private final OfficeQueue queue;
    private final CoffeeBreakScheduler breakScheduler;
    private final OfficialStamp stamp;
    private final DocumentCounter documentCounter;
    private final boolean stampFirst;

    private volatile boolean running = true;
    private volatile Thread workerThread;

    /**
     * Constructor simplificat: util pentru teste izolate (fără pauză de cafea și resurse externe).
     */
    public DeskWorker(String deskId, OfficeQueue queue) {
        this(deskId, queue, null, null, null, true);
    }

    /**
     * Constructor cu ștampilă: integrează planificatorul de pauze și ștampila instituției.
     */
    public DeskWorker(String deskId, OfficeQueue queue, CoffeeBreakScheduler breakScheduler, OfficialStamp stamp) {
        this(deskId, queue, breakScheduler, stamp, null, true);
    }

    /**
     * Constructor complet cu ambele resurse partajate (OfficialStamp și DocumentCounter) în ordinea standard.
     */
    public DeskWorker(String deskId, OfficeQueue queue, CoffeeBreakScheduler breakScheduler,
                      OfficialStamp stamp, DocumentCounter documentCounter) {
        this(deskId, queue, breakScheduler, stamp, documentCounter, true);
    }

    /**
     * Constructor complet cu Dependency Injection pentru ambele resurse și ordinea de achiziție a lock-urilor.
     *
     * @param stampFirst dacă este true, ghișeul achiziționează întâi OfficialStamp apoi DocumentCounter;
     *                   dacă este false, achiziționează întâi DocumentCounter apoi OfficialStamp
     *                   (util pentru demonstrarea Deadlock-ului cu UnsafeStamp/UnsafeCounter vs
     *                   prevenirea Deadlock-ului prin tryLock cu SafeStamp/SafeCounter).
     */
    public DeskWorker(String deskId, OfficeQueue queue, CoffeeBreakScheduler breakScheduler,
                      OfficialStamp stamp, DocumentCounter documentCounter, boolean stampFirst) {
        this.deskId = Objects.requireNonNull(deskId, "deskId nu poate fi null");
        this.queue = Objects.requireNonNull(queue, "queue nu poate fi null");
        this.breakScheduler = breakScheduler;
        this.stamp = stamp;
        this.documentCounter = documentCounter;
        this.stampFirst = stampFirst;
    }

    /**
     * Logica de procesare a unui tichet:
     *  1. Verifică și așteaptă dacă ghișeul este în pauză de cafea.
     *  2. Simulează timpul de lucru pentru redactarea documentului (150ms).
     *  3. Achiziționează resursele partajate (OfficialStamp și/sau DocumentCounter).
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

        // Pasul 3: Utilizarea resurselor partajate (OfficialStamp & DocumentCounter)
        if (stamp != null && documentCounter != null) {
            acquireBothResourcesAndProcess();
        } else if (stamp != null) {
            stamp.acquireStamp();
            try {
                stamp.simulateUsage(deskId, 200);
            } finally {
                stamp.releaseStamp();
            }
        } else if (documentCounter != null) {
            documentCounter.acquireCounter();
            try {
                documentCounter.increment(deskId);
            } finally {
                documentCounter.releaseCounter();
            }
        }

        // Pasul 4: Notifică clientul că actul este gata
        ticket.markCompleted();
    }

    /**
     * Achiziționează ambele resurse partajate (OfficialStamp și DocumentCounter).
     *  - Cu SafeStamp + SafeCounter: tryAcquire folosește tryLock(timeout), eliberează prima resursă
     *    dacă a doua este ocupată și reîncearcă, evitând complet Deadlock-ul.
     *  - Cu UnsafeStamp + UnsafeCounter: tryAcquire apelează lock.lock() blocant, ceea ce duce la
     *    Circular Wait Deadlock când două ghișee au ordine opusă (stampFirst = true vs false).
     */
    private void acquireBothResourcesAndProcess() throws InterruptedException {
        boolean completed = false;
        while (!completed && running && !Thread.currentThread().isInterrupted()) {
            if (stampFirst) {
                stamp.acquireStamp();
                try {
                    stamp.simulateUsage(deskId, 200);
                    if (documentCounter.tryAcquireCounter(300)) {
                        try {
                            documentCounter.increment(deskId);
                            completed = true;
                        } finally {
                            documentCounter.releaseCounter();
                        }
                    } else {
                        System.out.println("⚠️ [" + deskId + "] Counter ocupat, eliberează ștampila pentru a evita deadlock-ul și reîncearcă...");
                    }
                } finally {
                    stamp.releaseStamp();
                }
            } else {
                documentCounter.acquireCounter();
                try {
                    Thread.sleep(100);
                    if (stamp.tryAcquireStamp(300)) {
                        try {
                            stamp.simulateUsage(deskId, 200);
                            documentCounter.increment(deskId);
                            completed = true;
                        } finally {
                            stamp.releaseStamp();
                        }
                    } else {
                        System.out.println("⚠️ [" + deskId + "] Ștampilă ocupată, eliberează counter-ul pentru a evita deadlock-ul și reîncearcă...");
                    }
                } finally {
                    documentCounter.releaseCounter();
                }
            }

            if (!completed) {
                Thread.sleep(50);
            }
        }
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
        } finally {
            this.workerThread = null;
            System.out.println("Ghișeul " + deskId + " s-a închis.");
        }
    }
}
