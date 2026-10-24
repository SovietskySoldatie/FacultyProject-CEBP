package bureaucraticSystem.counter;

import bureaucraticSystem.client.request.ServiceTicket;
import bureaucraticSystem.config.data.Document;
import bureaucraticSystem.resource.CoffeeBreakScheduler;
import bureaucraticSystem.resource.DocumentCounter;
import bureaucraticSystem.resource.OfficialStamp;

import java.time.LocalTime;
import java.util.Objects;
import java.util.Set;

/**
 * Reprezintă un ghișeu administrativ individual care rulează pe propriul său fir de execuție (Thread).
 *
 * Funcționează ca un Worker într-un Worker Pool:
 *  - Verifică starea pauzei de cafea înainte de a extrage un tichet din coadă (astfel încât un ghișeu
 *    aflat în pauză să nu blocheze un tichet pe care l-ar putea prelua un alt ghișeu activ).
 *  - Extrage continuu tichete din OfficeQueue (operație blocantă takeTicket).
 *  - Solicită acces exclusiv la resursele partajate (OfficialStamp și DocumentCounter) prin Dependency Injection.
 *  - Notifică asincron clientul când actul este eliberat (ticket.markCompleted()), sau garantează
 *    deblocarea clientului prin ticket.markFailed() în blocul finally în caz de întrerupere/excepție.
 *  - Permite oprire curată (graceful shutdown) prin variabila volatile 'running' și auto-întrerupere în stop().
 */
public class DeskWorkerUnsafe implements DeskWorker {

    public static final long COFFEE_BREAK_POLL_MS = 300L;
    public static final long DRAFTING_DURATION_MS = 150L;
    public static final long STAMP_DURATION_MS = 200L;
    public static final long RESOURCE_TRY_LOCK_TIMEOUT_MS = 300L;
    public static final long RESOURCE_CONFLICT_WINDOW_MS = 100L;
    public static final long RESOURCE_RETRY_BACKOFF_MS = 50L;

    private final String deskId;
    private final OfficeQueue queue;
    private final CoffeeBreakScheduler breakScheduler;
    private final OfficialStamp stamp;
    private final DocumentCounter documentCounter;
    private final boolean stampFirst;

    private volatile boolean running = true;
    private volatile Thread workerThread;

    /**
     * Constructor simplificat: util pentru teste izolate (fără pauză de cafea și ștampilă externă).
     */
    public DeskWorkerUnsafe(String deskId, OfficeQueue queue) {
        this(deskId, queue, null, null, null, true);
    }

    /**
     * Constructor cu ștampilă: integrează planificatorul de pauze și ștampila instituției.
     */
    public DeskWorkerUnsafe(String deskId, OfficeQueue queue, CoffeeBreakScheduler breakScheduler, OfficialStamp stamp) {
        this(deskId, queue, breakScheduler, stamp, null, true);
    }

    /**
     * Constructor complet cu ambele resurse partajate (OfficialStamp și DocumentCounter) în ordinea standard.
     */
    public DeskWorkerUnsafe(String deskId, OfficeQueue queue, CoffeeBreakScheduler breakScheduler,
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
    public DeskWorkerUnsafe(String deskId, OfficeQueue queue, CoffeeBreakScheduler breakScheduler,
                      OfficialStamp stamp, DocumentCounter documentCounter, boolean stampFirst) {
        this.deskId = Objects.requireNonNull(deskId, "deskId can't be null");
        this.queue = Objects.requireNonNull(queue, "queue can't be null");
        this.breakScheduler = breakScheduler;
        this.stamp = stamp;
        this.documentCounter = documentCounter;
        this.stampFirst = stampFirst;
    }

    /**
     * Așteaptă cât timp ghișeul se află în pauză de cafea.
     */
    protected void waitWhileOnCoffeeBreak() throws InterruptedException {
        while (running && breakScheduler != null && breakScheduler.isOnBreak(deskId)) {
            System.out.println("[" + LocalTime.now() + "] " + deskId + " is on coffee break. Please wait...");
            Thread.sleep(COFFEE_BREAK_POLL_MS);
        }
    }

    /**
     * Logica de procesare a unui tichet:
     *  1. Verifică dacă a intervenit o pauză de cafea imediat după preluarea tichetului.
     *  2. Simulează timpul de lucru pentru redactarea documentului (DRAFTING_DURATION_MS).
     *  3. Achiziționează resursele partajate (OfficialStamp și/sau DocumentCounter).
     *  4. Notifică clientul că documentul a fost eliberat cu succes.
     */
    protected void processTicket(ServiceTicket ticket) throws InterruptedException {
        // Pasul 1: Dacă este programată pauza de cafea, ghișeul așteaptă finalizarea ei
        waitWhileOnCoffeeBreak();

        // Pasul 2: Simulează redactarea și verificarea documentului
        Thread.sleep(DRAFTING_DURATION_MS);

        int intId = 0;
        // Pasul 3: Utilizarea resurselor partajate (OfficialStamp & DocumentCounter)
        if (stamp != null && documentCounter != null) {
            intId = acquireBothResourcesAndProcess();
        } else if (stamp != null) {
            stamp.acquireStamp();
            try {
                stamp.simulateUsage(deskId, STAMP_DURATION_MS);
            } finally {
                stamp.releaseStamp();
            }
        } else if (documentCounter != null) {
            // FAULT: Apel direct neprotejat la contor fără lock (Lost Updates Race Condition)
            intId = documentCounter.increment(deskId);
        }

        // Pasul 4: Creează documentul cu intID și notifică clientul că actul este gata
        Document issuedDoc = new Document(ticket.getDocumentId(), deskId, Set.of(), intId);
        ticket.setIssuedDocument(issuedDoc);
        ticket.markCompleted();
    }

    /**
     * Achiziționează ștampila și contorul, dar OMITE intenționat lock-ul pe DocumentCounter.
     *
     * FAULT (Race Condition pe DocumentCounter):
     *  - În loc să protejeze increment() prin tryAcquireCounter() / acquireCounter(),
     *    apelează direct documentCounter.increment(deskId).
     *  - Când multiple ghișee apelează concurent increment(), operația non-atomică count++
     *    din UnsafeCounter produce pierderi de înregistrări (Lost Updates).
     */
    private int acquireBothResourcesAndProcess() throws InterruptedException {
        boolean resourcesAcquired = false;
        int issuedId = 0;
        while (!resourcesAcquired && running && !Thread.currentThread().isInterrupted()) {
            if (stampFirst) {
                stamp.acquireStamp();
                try {
                    stamp.simulateUsage(deskId, STAMP_DURATION_MS);
                } finally {
                    stamp.releaseStamp();
                }
                // FAULT: Apel direct neprotejat la contor fără lock
                issuedId = documentCounter.increment(deskId);
                resourcesAcquired = true;
            } else {
                // FAULT: Apel direct neprotejat la contor fără lock
                issuedId = documentCounter.increment(deskId);
                stamp.acquireStamp();
                try {
                    stamp.simulateUsage(deskId, STAMP_DURATION_MS);
                } finally {
                    stamp.releaseStamp();
                }
                resourcesAcquired = true;
            }

            if (!resourcesAcquired) {
                Thread.sleep(RESOURCE_RETRY_BACKOFF_MS);
            }
        }
        return issuedId;
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
        System.out.println("[" + LocalTime.now() + "] Ghiseul " + deskId + " s-a deschis.");

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
                    System.out.println("[" + LocalTime.now() + "] " + deskId + " a preluat clientul: " + ticket.getClientId());

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
            System.out.println("[" + LocalTime.now() + "] Ghiseul " + deskId + " s-a inchis.");
        }
    }
}
