package bureaucraticSystem.demo;

import bureaucraticSystem.client.Client;
import bureaucraticSystem.config.data.Document;
import bureaucraticSystem.config.data.DocumentRegistry;
import bureaucraticSystem.config.graph.RouteResolver;
import bureaucraticSystem.counter.BlockingOfficeQueue;
import bureaucraticSystem.counter.DeskWorker;
import bureaucraticSystem.counter.DeskWorkerSafe;
import bureaucraticSystem.counter.DeskWorkerUnsafe;
import bureaucraticSystem.counter.FaultyOfficeQueue;
import bureaucraticSystem.counter.OfficeQueue;
import bureaucraticSystem.resource.CoffeeBreakScheduler;
import bureaucraticSystem.resource.DocumentCounter;
import bureaucraticSystem.resource.OfficialStamp;
import bureaucraticSystem.resource.DeadlockStamp;
import bureaucraticSystem.resource.SafeCounter;
import bureaucraticSystem.resource.SafeStamp;
import bureaucraticSystem.resource.UnsafeCounter;
import bureaucraticSystem.resource.UnsafeStamp;
import bureaucraticSystem.scheduler.FaultyScheduler;
import bureaucraticSystem.scheduler.Scheduler;
import bureaucraticSystem.scheduler.TicketDispatcher;

import java.time.LocalTime;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Clasa unificata de orchestrare si simulare a institutiei birocratice.
 *
 * Centralizeaza asamblarea componentelor sistemului prin Dependency Injection,
 * acceptand contractele (interfetele) fiecarui subsistem:
 *  - {@link TicketDispatcher} (Scheduler / FaultyScheduler)
 *  - {@link OfficialStamp} (SafeStamp / UnsafeStamp)
 *  - {@link DocumentCounter} (SafeCounter / UnsafeCounter)
 *
 * Pastreaza aceeasi instantiere determinista pentru:
 *  1. Graful de dependente al documentelor (DocumentRegistry & RouteResolver DAG).
 *  2. Birourile administrative create ("StareaCivila", "ANAF", "Pasapoarte") si cozile lor.
 *  3. Ghiseele (DeskWorkers) si orarul pauzelor de cafea (CoffeeBreakScheduler).
 *  4. Cererile concurente ale cetatenilor (Client requests), calibrate pentru numere mari.
 */
public class Demo {

    /**
     * Variantele demonstrative disponibile pentru sistemul birocratic:
     *  - ALL_DEMOS: ruleaza toate variantele demonstrative secvential, lasand DEADLOCK intentionat ultimul.
     *  - ALL_GOOD: simularea completa corecta (toate componentele sunt thread-safe).
     *  - RACE_CONDITION_SCHEDULER: demonstratie cursa critica Singleton pe Scheduler (FaultyScheduler).
     *  - RACE_CONDITION_QUEUE: demonstratie cursa critica Producer-Consumer pe coada (FaultyOfficeQueue).
     *  - RACE_CONDITION_COUNTER: demonstratie pierderi de inregistrari (Lost Updates pe UnsafeCounter).
     *  - DEADLOCK: demonstratie circular-wait deadlock pe resursele partajate (UnsafeStamp + UnsafeCounter).
     */
    public enum DemoVariant {
        ALL_DEMOS,
        ALL_GOOD,
        RACE_CONDITION_SCHEDULER,
        RACE_CONDITION_QUEUE,
        RACE_CONDITION_COUNTER,
        DEADLOCK
    }

    /**
     * Variabila statica ce retine varianta de demo selectata pentru executie (setata la build time).
     */
    private static DemoVariant selectedDemo = DemoVariant.RACE_CONDITION_QUEUE;

    public static DemoVariant getSelectedDemo() {
        return selectedDemo;
    }

    public static void setSelectedDemo(DemoVariant variant) {
        selectedDemo = Objects.requireNonNull(variant, "DemoVariant cannot be null");
    }

    /**
     * Numarul implicit de cetateni per scenariu pentru demonstratii (setat la build time).
     */
    public static final int DEFAULT_CLIENT_COUNT = 10000;
    private static int defaultClientCount = DEFAULT_CLIENT_COUNT;

    public static int getDefaultClientCount() {
        return defaultClientCount;
    }

    public static void setDefaultClientCount(int clientCount) {
        if (clientCount > 0) {
            defaultClientCount = clientCount;
        }
    }

    /**
     * Optiune ce selecteaza daca mecanismul de protectie Deadlock (Deadlock Guard cu timeout defensiv)
     * este activ sau nu in cadrul demonstratiei (setata la build time).
     * Cand este dezactivat (false), clientii asteapta nedefinit (ct.join()), blocand executia in caz de Deadlock real.
     * Cand este activat (true), se foloseste un timeout defensiv (ct.join(remainingMs)) pentru diagnostic automat.
     */
    private static boolean deadlockGuardActive = false;

    /**
     * Durata maxima de asteptare in secunde pentru Deadlock Guard (0 sau negativ = auto-calculat dinamic prin formula).
     */
    private static long deadlockTimeoutSeconds = 0;

    public static boolean isDeadlockGuardActive() {
        return deadlockGuardActive;
    }

    public static void setDeadlockGuardActive(boolean active) {
        deadlockGuardActive = active;
    }

    public static long getDeadlockTimeoutSeconds() {
        return deadlockTimeoutSeconds;
    }

    public static void setDeadlockTimeoutSeconds(long seconds) {
        deadlockTimeoutSeconds = Math.max(0, seconds);
    }

    // Birouri administrative (7 Offices)
    private static final String OFFICE_STAREA_CIVILA = "StareaCivila";
    private static final String OFFICE_ANAF = "ANAF";
    private static final String OFFICE_POLITIE = "Politie";
    private static final String OFFICE_EVIDENTA_POPULATIEI = "EvidentaPopulatiei";
    private static final String OFFICE_PRIMARIE = "Primarie";
    private static final String OFFICE_DIRECTIA_SANATATE = "DirectiaSanatatePublica";
    private static final String OFFICE_PASAPOARTE = "Pasapoarte";

    // 10 Documente administrative (10 Document types forming a 4-tier DAG)
    // Tier 0: Acte de baza (0 preconditii)
    private static final String DOC_CERTIFICAT_NASTERE = "CertificatNastere";
    private static final String DOC_ADEVERINTA_VENIT = "AdeverintaVenit";
    private static final String DOC_CAZIER_JUDICIAR = "CazierJudiciar";
    private static final String DOC_ADEVERINTA_MEDICALA = "AdeverintaMedicala";

    // Tier 1: Acte intermediare
    private static final String DOC_CARTE_IDENTITATE = "CarteIdentitate";
    private static final String DOC_CERTIFICAT_FISCAL = "CertificatFiscal";
    private static final String DOC_AVIZ_PSIHO = "AvizPsihologic";

    // Tier 2: Acte avansate (StareaCivila si Primarie emit al doilea tip de document)
    private static final String DOC_CERTIFICAT_CASATORIE = "CertificatCasatorie";
    private static final String DOC_AUTORIZATIE_MEDIU = "AutorizatieMediu";

    // Tier 3: Act final complex ce necesita intregul pachet de acte
    private static final String FINAL_DOCUMENT_ID_PASAPORT = "Pasaport";
    private static final String FINAL_DOCUMENT_ID_PUZ = "PUZ";

    private static final int FINAL_WAIT_TIMEOUT_MILISECONDS = 1500;

    public Demo() {
        // Constructor public implicit
    }

    /**
     * Metoda ceruta demo: primeste tipurile de clasa (Class types) pentru fiecare dintre
     * interfetele considerate si instantiaza componentele corespunzatoare.
     *
     * @param dispatcherClass clasa dispeceratului de tichete (ex. Scheduler.class / FaultyScheduler.class)
     * @param stampClass      clasa stampilei oficiale (ex. SafeStamp.class / UnsafeStamp.class)
     * @param counterClass    clasa contorului de documente (ex. SafeCounter.class / UnsafeCounter.class)
     * @throws Exception in caz de eroare de instantiere sau executie
     */
    public void demo(Class<? extends TicketDispatcher> dispatcherClass,
                     Class<? extends OfficialStamp> stampClass,
                     Class<? extends DocumentCounter> counterClass) throws Exception {
        demo(dispatcherClass, stampClass, counterClass, BlockingOfficeQueue.class, getDefaultClientCount());
    }

    /**
     * Overload ce permite specificarea numarului de cetateni concurenti (large numbers).
     */
    public void demo(Class<? extends TicketDispatcher> dispatcherClass,
                     Class<? extends OfficialStamp> stampClass,
                     Class<? extends DocumentCounter> counterClass,
                     int clientCount) throws Exception {
        demo(dispatcherClass, stampClass, counterClass, BlockingOfficeQueue.class, clientCount);
    }

    /**
     * Overload complet ce permite si injectarea tipului de coada si de worker (Safe/Unsafe).
     */
    public void demo(Class<? extends TicketDispatcher> dispatcherClass,
                     Class<? extends OfficialStamp> stampClass,
                     Class<? extends DocumentCounter> counterClass,
                     Class<? extends OfficeQueue> queueClass,
                     Class<? extends DeskWorker> workerClass,
                     int clientCount) throws Exception {
        executeSimulation(dispatcherClass, stampClass, counterClass, queueClass, workerClass, clientCount);
    }

    public void demo(Class<? extends TicketDispatcher> dispatcherClass,
                     Class<? extends OfficialStamp> stampClass,
                     Class<? extends DocumentCounter> counterClass,
                     Class<? extends OfficeQueue> queueClass,
                     int clientCount) throws Exception {
        executeSimulation(dispatcherClass, stampClass, counterClass, queueClass, clientCount);
    }

    /**
     * Metode statice utilitare pentru apel direct fara instantierea clasei Demo.
     */
    public static void runDemo(Class<? extends TicketDispatcher> dispatcherClass,
                               Class<? extends OfficialStamp> stampClass,
                               Class<? extends DocumentCounter> counterClass) throws Exception {
        new Demo().demo(dispatcherClass, stampClass, counterClass);
    }

    public static void runDemo(Class<? extends TicketDispatcher> dispatcherClass,
                               Class<? extends OfficialStamp> stampClass,
                               Class<? extends DocumentCounter> counterClass,
                               int clientCount) throws Exception {
        new Demo().demo(dispatcherClass, stampClass, counterClass, clientCount);
    }

    /**
     * Overload-uri pentru compatibilitate inversa in cazul apelului cu obiecte deja instantiate.
     */
    @SuppressWarnings("unchecked")
    public void demo(TicketDispatcher dispatcher, OfficialStamp stamp, DocumentCounter counter) throws Exception {
        Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        Objects.requireNonNull(stamp, "stamp must not be null");
        Objects.requireNonNull(counter, "counter must not be null");
        demo((Class<? extends TicketDispatcher>) dispatcher.getClass(), stamp.getClass(), counter.getClass());
    }

    @SuppressWarnings("unchecked")
    public void demo(TicketDispatcher dispatcher, OfficialStamp stamp, DocumentCounter counter, int clientCount) throws Exception {
        Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        Objects.requireNonNull(stamp, "stamp must not be null");
        Objects.requireNonNull(counter, "counter must not be null");
        demo((Class<? extends TicketDispatcher>) dispatcher.getClass(), stamp.getClass(), counter.getClass(), clientCount);
    }

    // =============================================================
    // Helper methods pentru instantierea obiectelor din tipurile de clase
    // =============================================================

    protected static OfficialStamp instantiateStamp(Class<? extends OfficialStamp> stampClass) throws Exception {
        Objects.requireNonNull(stampClass, "stampClass must not be null");
        return stampClass.getDeclaredConstructor().newInstance();
    }

    protected static DocumentCounter instantiateCounter(Class<? extends DocumentCounter> counterClass) throws Exception {
        Objects.requireNonNull(counterClass, "counterClass must not be null");
        return counterClass.getDeclaredConstructor().newInstance();
    }

    protected static OfficeQueue instantiateQueue(Class<? extends OfficeQueue> queueClass) throws Exception {
        if (queueClass == null) {
            return new BlockingOfficeQueue();
        }
        return queueClass.getDeclaredConstructor().newInstance();
    }

    protected static DeskWorker createWorker(Class<? extends DeskWorker> workerClass,
                                             String deskId, OfficeQueue queue,
                                             CoffeeBreakScheduler breakScheduler,
                                             OfficialStamp stamp, DocumentCounter counter,
                                             boolean stampFirst) throws Exception {
        Class<? extends DeskWorker> targetClass = (workerClass != null) ? workerClass : DeskWorkerSafe.class;
        Constructor<? extends DeskWorker> ctor = targetClass.getConstructor(
                String.class, OfficeQueue.class, CoffeeBreakScheduler.class,
                OfficialStamp.class, DocumentCounter.class, boolean.class);
        return ctor.newInstance(deskId, queue, breakScheduler, stamp, counter, stampFirst);
    }

    @SuppressWarnings("unchecked")
    protected static TicketDispatcher instantiateDispatcher(Class<? extends TicketDispatcher> dispatcherClass,
                                                           DocumentRegistry registry) throws Exception {
        Objects.requireNonNull(dispatcherClass, "dispatcherClass must not be null");
        try {
            Method getInstanceMethod = dispatcherClass.getMethod("getInstance", DocumentRegistry.class);
            return (TicketDispatcher) getInstanceMethod.invoke(null, registry);
        } catch (NoSuchMethodException e1) {
            try {
                Method getInstanceMethod = dispatcherClass.getMethod("getInstance");
                return (TicketDispatcher) getInstanceMethod.invoke(null);
            } catch (NoSuchMethodException e2) {
                try {
                    Constructor<? extends TicketDispatcher> ctor = dispatcherClass.getDeclaredConstructor(DocumentRegistry.class);
                    ctor.setAccessible(true);
                    return ctor.newInstance(registry);
                } catch (NoSuchMethodException e3) {
                    Constructor<? extends TicketDispatcher> ctor = dispatcherClass.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    return ctor.newInstance();
                }
            }
        }
    }

    protected static void resetDispatcher(Class<? extends TicketDispatcher> dispatcherClass) {
        if (dispatcherClass == null) return;
        try {
            Method resetMethod = dispatcherClass.getMethod("resetInstance");
            resetMethod.invoke(null);
        } catch (Exception ignored) {
        }
    }

    /**
     * Construiește și populează configurația canonică a grafului de documente (DAG).
     * Conține 11 documente administrative distribuite pe 4 niveluri de dependențe,
     * integrând ambele tipuri de documente finale solicitate de cetățeni: "Pasaport" și "PUZ".
     *
     * @return instanță configurată de {@link DocumentRegistry}
     */
    public static DocumentRegistry createDefaultDocumentRegistry() {
        DocumentRegistry registry = new DocumentRegistry();

        // Tier 0: Acte de baza fara preconditii (4 acte)
        registry.registerDocument(new Document(DOC_CERTIFICAT_NASTERE, OFFICE_STAREA_CIVILA, Set.of()));
        registry.registerDocument(new Document(DOC_ADEVERINTA_VENIT, OFFICE_ANAF, Set.of()));
        registry.registerDocument(new Document(DOC_CAZIER_JUDICIAR, OFFICE_POLITIE, Set.of()));
        registry.registerDocument(new Document(DOC_ADEVERINTA_MEDICALA, OFFICE_DIRECTIA_SANATATE, Set.of()));

        // Tier 1: Acte intermediare (3 acte)
        registry.registerDocument(new Document(DOC_CARTE_IDENTITATE, OFFICE_EVIDENTA_POPULATIEI,
                Set.of(DOC_CERTIFICAT_NASTERE)));
        registry.registerDocument(new Document(DOC_CERTIFICAT_FISCAL, OFFICE_PRIMARIE,
                Set.of(DOC_ADEVERINTA_VENIT)));
        registry.registerDocument(new Document(DOC_AVIZ_PSIHO, OFFICE_DIRECTIA_SANATATE,
                Set.of(DOC_ADEVERINTA_MEDICALA)));

        // Tier 2: Acte avansate (2 acte)
        registry.registerDocument(new Document(DOC_CERTIFICAT_CASATORIE, OFFICE_STAREA_CIVILA,
                Set.of(DOC_CARTE_IDENTITATE, DOC_CERTIFICAT_NASTERE)));
        registry.registerDocument(new Document(DOC_AUTORIZATIE_MEDIU, OFFICE_PRIMARIE,
                Set.of(DOC_CERTIFICAT_FISCAL, DOC_CAZIER_JUDICIAR)));

        // Tier 3: Acte finale complexe (Target documents)
        // Toti cetatenii trec prin aceleasi birouri partajate si obtin aceleasi preconditii intermediare.
        // Doar actul final difera (50% cer "Pasaport", 50% cer "PUZ"), ambele eliberate la acelasi birou final ("Pasapoarte"):
        Set<String> sharedPrerequisites = Set.of(
                DOC_CARTE_IDENTITATE,
                DOC_CERTIFICAT_FISCAL,
                DOC_CAZIER_JUDICIAR,
                DOC_AVIZ_PSIHO,
                DOC_AUTORIZATIE_MEDIU,
                DOC_CERTIFICAT_CASATORIE
        );

        registry.registerDocument(new Document(FINAL_DOCUMENT_ID_PASAPORT, OFFICE_PASAPOARTE, sharedPrerequisites));
        registry.registerDocument(new Document(FINAL_DOCUMENT_ID_PUZ, OFFICE_PASAPOARTE, sharedPrerequisites));

        return registry;
    }

    /**
     * Executia propriu-zisa a simularii.
     * Pastreaza aceeasi topologie si configuratie de fiecare data cand este apelata.
     */
    protected void executeSimulation(Class<? extends TicketDispatcher> dispatcherClass,
                                     Class<? extends OfficialStamp> stampClass,
                                     Class<? extends DocumentCounter> counterClass,
                                     Class<? extends OfficeQueue> queueClass,
                                     int clientCount) throws Exception {
        Class<? extends DeskWorker> workerClass = (counterClass != null && UnsafeCounter.class.isAssignableFrom(counterClass) && (SafeStamp.class.isAssignableFrom(stampClass) || UnsafeStamp.class.isAssignableFrom(stampClass)))
                ? DeskWorkerUnsafe.class
                : DeskWorkerSafe.class;
        executeSimulation(dispatcherClass, stampClass, counterClass, queueClass, workerClass, clientCount);
    }

    protected void executeSimulation(Class<? extends TicketDispatcher> dispatcherClass,
                                     Class<? extends OfficialStamp> stampClass,
                                     Class<? extends DocumentCounter> counterClass,
                                     Class<? extends OfficeQueue> queueClass,
                                     Class<? extends DeskWorker> workerClass,
                                     int clientCount) throws Exception {
        Objects.requireNonNull(dispatcherClass, "dispatcherClass must not be null");
        Objects.requireNonNull(stampClass, "stampClass must not be null");
        Objects.requireNonNull(counterClass, "counterClass must not be null");
        if (queueClass == null) {
            queueClass = BlockingOfficeQueue.class;
        }
        if (workerClass == null) {
            workerClass = (UnsafeCounter.class.isAssignableFrom(counterClass) && (SafeStamp.class.isAssignableFrom(stampClass) || UnsafeStamp.class.isAssignableFrom(stampClass)))
                    ? DeskWorkerUnsafe.class
                    : DeskWorkerSafe.class;
        }
        if (clientCount <= 0) {
            clientCount = getDefaultClientCount();
        }

        // Resetam singletons inainte de test
        resetDispatcher(dispatcherClass);
        RouteResolver.resetInstance();

        System.out.println("\n[" + LocalTime.now() + "] ===============================================================");
        System.out.println("[" + LocalTime.now() + "] === Incepere Simulare Sistem Birocratic (Demo) ===");
        System.out.println("[" + LocalTime.now() + "] ===============================================================");
        System.out.printf("[" + LocalTime.now() + "] Numar cetateni concurenti: %d (50%% %s, 50%% %s)\n", clientCount, FINAL_DOCUMENT_ID_PASAPORT, FINAL_DOCUMENT_ID_PUZ);
        System.out.println("[" + LocalTime.now() + "] Clasa Dispecerat:           " + dispatcherClass.getSimpleName());
        System.out.println("[" + LocalTime.now() + "] Clasa Stampila:             " + stampClass.getSimpleName());
        System.out.println("[" + LocalTime.now() + "] Clasa Contor:              " + counterClass.getSimpleName());
        System.out.println("[" + LocalTime.now() + "] Clasa Cozi de birou:       " + queueClass.getSimpleName());
        System.out.println("[" + LocalTime.now() + "] Clasa Ghisee (Worker):      " + workerClass.getSimpleName());
        System.out.println("[" + LocalTime.now() + "] ---------------------------------------------------------------\n");

        // -------------------------------------------------------------
        // 1. GRAFUL DE DOCUMENTE (11 acte pe 4 niveluri de dependente)
        //    Toti cetatenii trec prin aceleasi birouri administrative partajate.
        //    Demonstreaza birouri care emit mai multe tipuri de documente:
        //    - StareaCivila emite CertificatNastere & CertificatCasatorie
        //    - Primarie emite CertificatFiscal & AutorizatieMediu
        //    - DirectiaSanatatePublica emite AdeverintaMedicala & AvizPsihologic
        //    - Pasapoarte emite Pasaport & PUZ (actele finale)
        // -------------------------------------------------------------
        DocumentRegistry registry = createDefaultDocumentRegistry();

        RouteResolver routeResolver = RouteResolver.getInstance(registry);
        routeResolver.setRegistry(registry);

        OfficialStamp stamp = instantiateStamp(stampClass);
        DocumentCounter counter = instantiateCounter(counterClass);

        // -------------------------------------------------------------
        // 2. BIROURI SI COZI ADMINISTRATIVE (7 birouri cu cozi dedicate)
        // -------------------------------------------------------------
        OfficeQueue stareaCivilaQueue = instantiateQueue(queueClass);
        OfficeQueue anafQueue = instantiateQueue(queueClass);
        OfficeQueue politieQueue = instantiateQueue(queueClass);
        OfficeQueue evidentaPopulatieiQueue = instantiateQueue(queueClass);
        OfficeQueue primarieQueue = instantiateQueue(queueClass);
        OfficeQueue directiaSanatateQueue = instantiateQueue(queueClass);
        OfficeQueue pasapoarteQueue = instantiateQueue(queueClass);

        CyclicBarrier initBarrier = new CyclicBarrier(clientCount + 1);
        CountDownLatch officesReady = new CountDownLatch(1);
        Set<TicketDispatcher> distinctDispatchers = Collections.synchronizedSet(new HashSet<>());

        // Thread dedicat pentru setup-ul birourilor in dispecerat
        Thread setupThread = new Thread(() -> {
            try {
                // Se aliniaza la linia de start cu clientii pentru a forta cursa critica pe Singleton
                initBarrier.await();
                TicketDispatcher setupDispatcher = instantiateDispatcher(dispatcherClass, registry);
                distinctDispatchers.add(setupDispatcher);

                setupDispatcher.registerOffice(OFFICE_STAREA_CIVILA, stareaCivilaQueue);
                setupDispatcher.registerOffice(OFFICE_ANAF, anafQueue);
                setupDispatcher.registerOffice(OFFICE_POLITIE, politieQueue);
                setupDispatcher.registerOffice(OFFICE_EVIDENTA_POPULATIEI, evidentaPopulatieiQueue);
                setupDispatcher.registerOffice(OFFICE_PRIMARIE, primarieQueue);
                setupDispatcher.registerOffice(OFFICE_DIRECTIA_SANATATE, directiaSanatateQueue);
                setupDispatcher.registerOffice(OFFICE_PASAPOARTE, pasapoarteQueue);
            } catch (Exception e) {
                System.err.println("[" + LocalTime.now() + "] [Setup] Eroare la inregistrarea birourilor: " + e.getMessage());
            } finally {
                officesReady.countDown();
            }
        }, "Setup-Dispatcher-Thread");
        setupThread.start();

        // -------------------------------------------------------------
        // 3. CONFIGURATIE PAUZE DE CAFEA (CoffeeBreakScheduler)
        // -------------------------------------------------------------
        CoffeeBreakScheduler breakScheduler = new CoffeeBreakScheduler();

        // Inregistram ghiseele din institutie
        breakScheduler.registerDesk("Desk-StareaCivila");
        breakScheduler.registerDesk("Desk-ANAF-1");
        breakScheduler.registerDesk("Desk-ANAF-2");
        breakScheduler.registerDesk("Desk-Politie");
        breakScheduler.registerDesk("Desk-EvidentaPopulatiei-1");
        breakScheduler.registerDesk("Desk-EvidentaPopulatiei-2");
        breakScheduler.registerDesk("Desk-Primarie");
        breakScheduler.registerDesk("Desk-DirectiaSanatate");
        breakScheduler.registerDesk("Desk-Pasapoarte-1");
        breakScheduler.registerDesk("Desk-Pasapoarte-2");

        // Setam pauze periodice conforme specificatiilor
        breakScheduler.startScheduler("Desk-ANAF-1", 5, 2);
        breakScheduler.startScheduler("Desk-EvidentaPopulatiei-2", 6, 2);
        breakScheduler.startScheduler("Desk-Politie", 7, 2);
        breakScheduler.startScheduler("Desk-DirectiaSanatate", 8, 2);
        breakScheduler.startScheduler("Desk-Pasapoarte-1", 5, 1);

        // -------------------------------------------------------------
        // 4. PORNIRE GHISEE (Worker Pool: unele birouri au 2 ghisee paralele)
        //    Alternam ordinea resurselor partajate (stampFirst = true vs false)
        //    pentru a verifica prevenirea Deadlock-ului (SafeStamp/SafeCounter)
        //    sau declansarea acestuia (UnsafeStamp/UnsafeCounter).
        // -------------------------------------------------------------
        List<DeskWorker> workers = new ArrayList<>();
        List<Thread> workerThreads = new ArrayList<>();

        DeskWorker w1 = createWorker(workerClass, "Desk-StareaCivila", stareaCivilaQueue, breakScheduler, stamp, counter, true);
        DeskWorker w2 = createWorker(workerClass, "Desk-ANAF-1", anafQueue, breakScheduler, stamp, counter, true);
        DeskWorker w3 = createWorker(workerClass, "Desk-ANAF-2", anafQueue, breakScheduler, stamp, counter, false);
        DeskWorker w4 = createWorker(workerClass, "Desk-Politie", politieQueue, breakScheduler, stamp, counter, true);
        DeskWorker w5 = createWorker(workerClass, "Desk-EvidentaPopulatiei-1", evidentaPopulatieiQueue, breakScheduler, stamp, counter, true);
        DeskWorker w6 = createWorker(workerClass, "Desk-EvidentaPopulatiei-2", evidentaPopulatieiQueue, breakScheduler, stamp, counter, true);
        DeskWorker w7 = createWorker(workerClass, "Desk-Primarie", primarieQueue, breakScheduler, stamp, counter, true);
        DeskWorker w8 = createWorker(workerClass, "Desk-DirectiaSanatate", directiaSanatateQueue, breakScheduler, stamp, counter, true);
        DeskWorker w9 = createWorker(workerClass, "Desk-Pasapoarte-1", pasapoarteQueue, breakScheduler, stamp, counter, true);
        DeskWorker w10 = createWorker(workerClass, "Desk-Pasapoarte-2", pasapoarteQueue, breakScheduler, stamp, counter, true);

        Collections.addAll(workers, w1, w2, w3, w4, w5, w6, w7, w8, w9, w10);

        for (DeskWorker worker : workers) {
            Thread wt = new Thread(worker, worker.getDeskId());
            workerThreads.add(wt);
            wt.start();
        }

        System.out.println("[" + LocalTime.now() + "] --- Institutia s-a deschis. Angajatii sunt la ghisee! ---\n");
        Thread.sleep(500);

        // -------------------------------------------------------------
        // 5. CERERI CLIENTI (Instantierea Scheduler-ului la crearea fiecarui Client)
        //    Subliniaza defectul de Singleton: in loc sa reutilizeze o instanta injectata extern,
        //    fiecare client isi instantiaza dispecerul conform contractului singleton.
        // -------------------------------------------------------------
        List<Client> clients = Collections.synchronizedList(new ArrayList<>(clientCount));
        List<Thread> clientThreads = new ArrayList<>(clientCount);
        CountDownLatch startGate = new CountDownLatch(1);

        for (int i = 1; i <= clientCount; i++) {
            final int clientIndex = i;
            // Jumatate din clienti cer Pasaport, jumatate cer PUZ
            String targetDoc = (i % 2 == 1) ? FINAL_DOCUMENT_ID_PASAPORT : FINAL_DOCUMENT_ID_PUZ;

            Thread ct = new Thread(() -> {
                try {
                    // Sincronizare la linia de pornire: toate firele client si setup cer instanta simultan
                    initBarrier.await();
                    TicketDispatcher clientDispatcher = instantiateDispatcher(dispatcherClass, registry);
                    distinctDispatchers.add(clientDispatcher);

                    Client client = new Client("Cetatean-" + clientIndex, targetDoc, routeResolver, clientDispatcher);
                    clients.add(client);

                    // Asteapta finalizarea inregistrarii birourilor si semnalul de start
                    officesReady.await(5, TimeUnit.SECONDS);
                    startGate.await();
                    client.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("[" + LocalTime.now() + "] Eroare la pornire client Cetatean-" + clientIndex + ": " + e.getMessage());
                }
            }, "Thread-Cetatean-" + clientIndex);
            clientThreads.add(ct);
            ct.start();
        }

        // Asteptam ca birourile sa fie inregistrate in setup
        officesReady.await(5, TimeUnit.SECONDS);

        System.out.printf("[" + LocalTime.now() + "] --- Au sosit %d cetateni simultan la ghisee (50%% Pasaport, 50%% PUZ)! ---\n", clientCount);
        startGate.countDown(); // Eliberam toti clientii simultan

        // -------------------------------------------------------------
        // 6. ASTEPTARE FINALIZARE (Deadlock Guard configurabil)
        // -------------------------------------------------------------
        try {
            if (deadlockGuardActive) {
                long timeoutMs;
                if (deadlockTimeoutSeconds > 0) {
                    timeoutMs = deadlockTimeoutSeconds * 1000L;
                } else {
                    long estimatedMs = (long) Math.ceil((clientCount * 10.0 / workers.size()) * 1000L) + 25_000L;
                    timeoutMs = Math.max(45_000L, estimatedMs);
                }
                long deadlineMs = System.currentTimeMillis() + timeoutMs;
                System.out.printf("[" + LocalTime.now() + "] [Deadlock Guard] Activ - limita maxima de asteptare: %d secunde.\n", timeoutMs / 1000L);

                boolean timedOut = false;
                for (Thread ct : clientThreads) {
                    long remainingMs = deadlineMs - System.currentTimeMillis();
                    if (remainingMs > 0) {
                        ct.join(remainingMs);
                    } else if (ct.isAlive()) {
                        timedOut = true;
                    }
                }

                if (timedOut || System.currentTimeMillis() >= deadlineMs) {
                    System.err.printf("\n[" + LocalTime.now() + "] [DEADLOCK GUARD TRIGGERED] Timeout-ul defensiv de %d secunde a EXPIRAT!\n" +
                            "  -> A fost detectat blocaj concurent (Deadlock / Circular Wait pe resurse partajate) la ora %s!\n" +
                            "  -> Se declanseaza oprirea fortata si raportul de diagnostic.\n",
                            timeoutMs / 1000L, LocalTime.now());
                } else {
                    System.out.println("[" + LocalTime.now() + "] [Deadlock Guard] Toti cetatenii au finalizat inainte de expirarea timeout-ului.");
                }
            } else {
                // Deadlock Guard dezactivat: asteptare nedefinita (in caz de Deadlock, executia ramane blocata)
                for (Thread ct : clientThreads) {
                    ct.join();
                }
            }

            long completedPasaport = clients.stream()
                    .filter(c -> FINAL_DOCUMENT_ID_PASAPORT.equals(c.getTargetDocumentId()) && c.isCompleted())
                    .count();
            long totalPasaport = clients.stream()
                    .filter(c -> FINAL_DOCUMENT_ID_PASAPORT.equals(c.getTargetDocumentId()))
                    .count();

            long completedPUZ = clients.stream()
                    .filter(c -> FINAL_DOCUMENT_ID_PUZ.equals(c.getTargetDocumentId()) && c.isCompleted())
                    .count();
            long totalPUZ = clients.stream()
                    .filter(c -> FINAL_DOCUMENT_ID_PUZ.equals(c.getTargetDocumentId()))
                    .count();

            long completedCount = completedPasaport + completedPUZ;
            long stuckClients = clientCount - completedCount;

            System.out.println("\n[" + LocalTime.now() + "] ---------------------------------------------------------------");
            System.out.println("[" + LocalTime.now() + "] === REZULTATE SIMULARE ===");
            System.out.println("[" + LocalTime.now() + "] ---------------------------------------------------------------");
            System.out.printf("[" + LocalTime.now() + "] Dispeceri unici instantiati: %d (ar trebui 1)\n", distinctDispatchers.size());
            System.out.printf("[" + LocalTime.now() + "] Cetateni finalizati pentru %s: %d / %d\n", FINAL_DOCUMENT_ID_PASAPORT, completedPasaport, totalPasaport);
            System.out.printf("[" + LocalTime.now() + "] Cetateni finalizati pentru %s: %d / %d\n", FINAL_DOCUMENT_ID_PUZ, completedPUZ, totalPUZ);
            System.out.printf("[" + LocalTime.now() + "] Total cetateni finalizati: %d / %d\n", completedCount, clientCount);
            System.out.println("[" + LocalTime.now() + "] Documente inregistrate de contor: " + counter.getCount());

            if (distinctDispatchers.size() > 1) {
                System.out.printf("[" + LocalTime.now() + "] [FAULT CONFIRMAT] Singleton-ul %s a fost instantiat de %d ori din cauza cursei critice (Race Condition)!\n" +
                        "  -> Birourile au fost inregistrate intr-o instanta, iar clientii au primit alte instante goale.\n",
                        dispatcherClass.getSimpleName(), distinctDispatchers.size());
            }

            if (stuckClients > 0) {
                System.out.printf("[" + LocalTime.now() + "] [ALERTA FAULT DETECTAT] %d cetateni nu au reusit sa finalizeze actul in timpul alocat!\n", stuckClients);
                System.out.println("[" + LocalTime.now() + "] Posibile cauze:\n" +
                        " - Deadlock intre ghisee concurente pe stampila si contor (UnsafeStamp/UnsafeCounter)\n" +
                        " - Pierderi de tichete / race condition pe coada (FaultyOfficeQueue)\n" +
                        " - Incoerenta dispecerului singleton (FaultyScheduler)\n");
            } else {
                System.out.println("[" + LocalTime.now() + "] [SUCCES] Toti cetatenii si-au primit documentele cu succes!");
            }

        } finally {
            // ---------------------------------------------------------
            // 7. INCHIDERE CURATA (Graceful Shutdown)
            // ---------------------------------------------------------
            System.out.println("\n[" + LocalTime.now() + "] --- Oprire curata a tuturor firelor de executie si resurselor ---");
            breakScheduler.shutdownAll();
            for (DeskWorker w : workers) {
                w.stop();
            }
            for (Client c : clients) {
                c.stop();
            }
            for (Thread wt : workerThreads) {
                wt.interrupt();
            }
            for (Thread ct : clientThreads) {
                ct.interrupt();
            }
            long joinDeadline = System.currentTimeMillis() + FINAL_WAIT_TIMEOUT_MILISECONDS;
            for (Thread wt : workerThreads) {
                long waitLeft = joinDeadline - System.currentTimeMillis();
                if (waitLeft > 0) {
                    wt.join(waitLeft);
                }
            }
            for (Thread ct : clientThreads) {
                long waitLeft = joinDeadline - System.currentTimeMillis();
                if (waitLeft > 0) {
                    ct.join(waitLeft);
                }
            }

            for (TicketDispatcher d : distinctDispatchers) {
                try {
                    d.stop();
                } catch (Exception ignored) {}
            }
            resetDispatcher(dispatcherClass);
            RouteResolver.resetInstance();

            System.out.println("[" + LocalTime.now() + "] === Simulare incheiata ===\n");
        }
    }

    /**
     * Lanseaza varianta de demonstratie specificata prin switch-case.
     *
     * @param variant     varianta de demonstratie
     * @param clientCount numarul de cetateni per scenariu
     */
    public void runDemo(DemoVariant variant, int clientCount) throws Exception {
        DemoVariant target = (variant != null) ? variant : selectedDemo;
        int count = clientCount > 0 ? clientCount : getDefaultClientCount();

        switch (target) {
            case ALL_GOOD -> {
                System.out.println("\n[" + LocalTime.now() + "] ===============================================================");
                System.out.println("[" + LocalTime.now() + "] === 1. DEMO: ALL-GOOD (Toate subsistemele sunt thread-safe) ===");
                System.out.println("[" + LocalTime.now() + "] ===    Scheduler (Singleton DCL) + SafeStamp + SafeCounter + BlockingOfficeQueue ===");
                System.out.println("[" + LocalTime.now() + "] ===============================================================");
                demo(Scheduler.class, SafeStamp.class, SafeCounter.class, BlockingOfficeQueue.class, count);
            }
            case RACE_CONDITION_SCHEDULER -> {
                System.out.println("\n[" + LocalTime.now() + "] ===============================================================");
                System.out.println("[" + LocalTime.now() + "] === 2. DEMO: RACE CONDITION - SCHEDULER (SINGLETON FAULT)   ===");
                System.out.println("[" + LocalTime.now() + "] ===    FaultyScheduler (Check-then-act nesincronizat)       ===");
                System.out.println("[" + LocalTime.now() + "] ===    Demonstreaza: Multi-instantiere si cozi inexistente  ===");
                System.out.println("[" + LocalTime.now() + "] ===============================================================");
                demo(FaultyScheduler.class, SafeStamp.class, SafeCounter.class, BlockingOfficeQueue.class, count);
            }
            case RACE_CONDITION_QUEUE -> {
                System.out.println("\n[" + LocalTime.now() + "] ===============================================================");
                System.out.println("[" + LocalTime.now() + "] === 3. DEMO: RACE CONDITION - OFFICE QUEUE (DATA INCOHERENCE) ===");
                System.out.println("[" + LocalTime.now() + "] ===    FaultyOfficeQueue (LinkedList nesincronizata)        ===");
                System.out.println("[" + LocalTime.now() + "] ===    Demonstreaza: Pierdere tichete si comportament non-blocant ===");
                System.out.println("[" + LocalTime.now() + "] ===============================================================");
                demo(Scheduler.class, SafeStamp.class, SafeCounter.class, FaultyOfficeQueue.class, count);
            }
            case RACE_CONDITION_COUNTER -> {
                System.out.println("\n[" + LocalTime.now() + "] ===============================================================");
                System.out.println("[" + LocalTime.now() + "] === 4. DEMO: RACE CONDITION - DOCUMENT COUNTER (LOST UPDATES) ===");
                System.out.println("[" + LocalTime.now() + "] ===    UnsafeCounter (count++ nesincronizat)                ===");
                System.out.println("[" + LocalTime.now() + "] ===    DeskWorkerUnsafe (apel neprotejat la contor)         ===");
                System.out.println("[" + LocalTime.now() + "] ===    UnsafeStamp (cedeaza resursa, fara lock exclusiv)    ===");
                System.out.println("[" + LocalTime.now() + "] ===    Demonstreaza: Pierderi de inregistrari la concurenta  ===");
                System.out.println("[" + LocalTime.now() + "] ===============================================================");
                demo(Scheduler.class, UnsafeStamp.class, UnsafeCounter.class, BlockingOfficeQueue.class, DeskWorkerUnsafe.class, count);
            }
            case DEADLOCK -> {
                System.out.println("\n[" + LocalTime.now() + "] ===============================================================");
                System.out.println("[" + LocalTime.now() + "] === 5. DEMO: DEADLOCK (CIRCULAR WAIT PE RESURSE PARTAJATE)   ===");
                System.out.println("[" + LocalTime.now() + "] ===    DeadlockStamp (lock blocant) + UnsafeCounter (lock blocant) ===");
                System.out.println("[" + LocalTime.now() + "] ===    Demonstreaza: Circular Wait intre ghisee concurente  ===");
                System.out.println("[" + LocalTime.now() + "] ===============================================================");
                demo(Scheduler.class, DeadlockStamp.class, UnsafeCounter.class, BlockingOfficeQueue.class, count);
            }
            case ALL_DEMOS -> {
                System.out.println("\n[" + LocalTime.now() + "] ***************************************************************");
                System.out.println("[" + LocalTime.now() + "] *** RULARE TOATE VARIANTELE DE DEMO (DEADLOCK RAMANE ULTIMUL) ***");
                System.out.println("[" + LocalTime.now() + "] ***************************************************************\n");

                // 1. All-Good Demo
                runDemo(DemoVariant.ALL_GOOD, count);
                Thread.sleep(1000);

                // 2. Race Condition: Singleton Scheduler
                runDemo(DemoVariant.RACE_CONDITION_SCHEDULER, count);
                Thread.sleep(1000);

                // 3. Race Condition: Office Queue
                runDemo(DemoVariant.RACE_CONDITION_QUEUE, count);
                Thread.sleep(1000);

                // 4. Race Condition: Document Counter
                runDemo(DemoVariant.RACE_CONDITION_COUNTER, count);
                Thread.sleep(1000);

                // 5. Deadlock Demo (lasat intentionat la sfarsit!)
                runDemo(DemoVariant.DEADLOCK, count);
            }
            default -> {
                // In caz default, se utilizeaza valoarea configurata la build time (selectedDemo)
                runDemo(selectedDemo, count);
            }
        }
    }

    public void runDemo(DemoVariant variant) throws Exception {
        runDemo(variant, getDefaultClientCount());
    }

    public void runDemo() throws Exception {
        runDemo(selectedDemo, getDefaultClientCount());
    }

    public void runDemo(int clientCount) throws Exception {
        runDemo(selectedDemo, clientCount);
    }

    /**
     * Punct de intrare pentru executia demonstratiilor.
     * Selecteaza si lanseaza demo-ul folosind switch-case:
     * cazul default foloseste direct valoarea setata la build time (selectedDemo).
     * Cand nu se transmit argumente, se utilizeaza numarul implicit de cetateni (getDefaultClientCount()).
     */
    public static void main(String[] args) throws Exception {
        Demo demoInstance = new Demo();

        String mode = (args != null && args.length > 0) ? args[0].trim().toUpperCase() : "DEFAULT";
        DemoVariant variant = switch (mode) {
            case "ALL", "ALL_DEMOS" -> DemoVariant.ALL_DEMOS;
            case "GOOD", "ALL_GOOD", "SAFE" -> DemoVariant.ALL_GOOD;
            case "SCHEDULER", "RACE_CONDITION_SCHEDULER", "SINGLETON" -> DemoVariant.RACE_CONDITION_SCHEDULER;
            case "QUEUE", "RACE_CONDITION_QUEUE", "FAULTY_QUEUE" -> DemoVariant.RACE_CONDITION_QUEUE;
            case "COUNTER", "RACE_CONDITION_COUNTER", "LOST_UPDATES" -> DemoVariant.RACE_CONDITION_COUNTER;
            case "DEADLOCK" -> DemoVariant.DEADLOCK;
            default -> selectedDemo; // Cazul default foloseste direct valoarea configurata la build time
        };

        int count = getDefaultClientCount();
        if (args != null && args.length > 1) {
            try {
                count = Integer.parseInt(args[1].trim());
            } catch (NumberFormatException ignored) {}
        }

        if (args != null && args.length > 2) {
            String guardArg = args[2].trim().toLowerCase();
            try {
                // Suporta direct numar de secunde ca al 3-lea argument (ex: DEADLOCK 100 15)
                long seconds = Long.parseLong(guardArg.replaceAll("[^0-9]", ""));
                if (seconds > 0) {
                    deadlockGuardActive = true;
                    deadlockTimeoutSeconds = seconds;
                } else {
                    deadlockGuardActive = false;
                    deadlockTimeoutSeconds = 0;
                }
            } catch (NumberFormatException e) {
                // Sau valori booleene / etichete (ex: DEADLOCK 100 true / guard / active)
                deadlockGuardActive = "true".equals(guardArg) || "guard".equals(guardArg)
                        || "active".equals(guardArg) || "yes".equals(guardArg) || "1".equals(guardArg);
            }
        }

        if (args != null && args.length > 3) {
            String timeArg = args[3].trim().toLowerCase();
            try {
                // Al 4-lea argument: durata explicita a timeout-ului in secunde (ex: DEADLOCK 100 true 15)
                long seconds = Long.parseLong(timeArg.replaceAll("[^0-9]", ""));
                if (seconds > 0) {
                    deadlockTimeoutSeconds = seconds;
                    deadlockGuardActive = true;
                }
            } catch (NumberFormatException ignored) {}
        }

        String guardStatusDesc;
        if (deadlockGuardActive) {
            guardStatusDesc = (deadlockTimeoutSeconds > 0)
                    ? "ACTIV (" + deadlockTimeoutSeconds + "s)"
                    : "ACTIV (auto/formula)";
        } else {
            guardStatusDesc = "INACTIV";
        }

        System.out.printf("[" + LocalTime.now() + "] >>> Lansare demonstratie: %s (Numar cetateni per scenariu: %d, Deadlock Guard: %s) <<<\n",
                variant, count, guardStatusDesc);
        demoInstance.runDemo(variant, count);
    }
}
