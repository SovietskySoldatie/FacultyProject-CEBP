package bureaucraticSystem.demo;

import bureaucraticSystem.client.Client;
import bureaucraticSystem.config.data.Document;
import bureaucraticSystem.config.data.DocumentRegistry;
import bureaucraticSystem.config.graph.RouteResolver;
import bureaucraticSystem.counter.DeskWorker;
import bureaucraticSystem.counter.OfficeQueue;
import bureaucraticSystem.scheduler.Scheduler;
import bureaucraticSystem.resource.CoffeeBreakScheduler;
import bureaucraticSystem.resource.SafeStamp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Clasa de asamblare finală care simulează întregul flux al instituției birocratice.
 */
public class MainTest {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Începere Simulare Sistem Birocratic ===\n");

        // ---------------------------------------------------------
        // 1. CONFIGURARE DATE ȘI RUTARE (Membrul 1)
        // ---------------------------------------------------------
        DocumentRegistry registry = new DocumentRegistry();
        // Acte simple, fără precondiții
        registry.registerDocument(new Document("CertificatNastere", "StareaCivila", Set.of()));
        registry.registerDocument(new Document("AdeverintaVenit", "ANAF", Set.of()));
        // Act final complex, care are nevoie de cele două acte de mai sus
        registry.registerDocument(new Document("Pasaport", "Pasapoarte", Set.of("CertificatNastere", "AdeverintaVenit")));

        // Inițializăm RouteResolver-ul Singleton
        RouteResolver routeResolver = RouteResolver.getInstance(registry);

        // ---------------------------------------------------------
        // 2. DISPECERAT CENTRAL ȘI COZI (Scheduler + Membrul 2)
        // ---------------------------------------------------------
        Scheduler scheduler = Scheduler.getInstance(registry);

        OfficeQueue stareaCivilaQueue = new OfficeQueue();
        OfficeQueue anafQueue = new OfficeQueue();
        OfficeQueue pasapoarteQueue = new OfficeQueue();

        scheduler.registerOffice("StareaCivila", stareaCivilaQueue);
        scheduler.registerOffice("ANAF", anafQueue);
        scheduler.registerOffice("Pasapoarte", pasapoarteQueue);

        // ---------------------------------------------------------
        // 3. RESURSE ȘI HAOS (Membrul 4 - Partea ta)
        // ---------------------------------------------------------
        SafeStamp stamp = new SafeStamp(); // O singură ștampilă pentru toată instituția!
        CoffeeBreakScheduler breakScheduler = new CoffeeBreakScheduler();

        breakScheduler.registerDesk("Desk-StareaCivila");
        breakScheduler.registerDesk("Desk-ANAF");
        breakScheduler.registerDesk("Desk-Pasapoarte");

        // Setăm ca funcționarul de la ANAF să ia pauze dese (la fiecare 3 secunde, ia 2 secunde pauză)
        // Setăm ca cel de la Pașapoarte să ia la 4 secunde, 1 secundă pauză.
        breakScheduler.startScheduler("Desk-ANAF", 3, 2);
        breakScheduler.startScheduler("Desk-Pasapoarte", 4, 1);

        // ---------------------------------------------------------
        // 4. PORNIRE GHIȘEE (Membrul 2 + Membrul 4)
        // ---------------------------------------------------------
        DeskWorker worker1 = new DeskWorker("Desk-StareaCivila", stareaCivilaQueue, breakScheduler, stamp);
        DeskWorker worker2 = new DeskWorker("Desk-ANAF", anafQueue, breakScheduler, stamp);
        DeskWorker worker3 = new DeskWorker("Desk-Pasapoarte", pasapoarteQueue, breakScheduler, stamp);

        Thread t1 = new Thread(worker1, worker1.getDeskId());
        Thread t2 = new Thread(worker2, worker2.getDeskId());
        Thread t3 = new Thread(worker3, worker3.getDeskId());

        t1.start();
        t2.start();
        t3.start();

        System.out.println("--- Instituția s-a deschis. Angajații sunt la ghișee! ---\n");
        Thread.sleep(1000); // Lăsăm 1 secundă să se așeze lucrurile

        // ---------------------------------------------------------
        // 5. LANSARE CLIENȚI (Membrul 3)
        // ---------------------------------------------------------
        System.out.println("--- Au intrat clienții! ---");
        List<Thread> clientThreads = new ArrayList<>();

        // Lansăm 3 clienți simultan care vor pașaport
        for (int i = 1; i <= 3; i++) {
            Client client = new Client("Cetatean-" + i, "Pasaport", routeResolver, scheduler);
            Thread ct = new Thread(client, "Thread-" + client.getClientId());
            clientThreads.add(ct);
            ct.start();
        }

        // ---------------------------------------------------------
        // 6. AȘTEPTĂM FINALIZAREA
        // ---------------------------------------------------------
        for (Thread ct : clientThreads) {
            ct.join(); // Firul principal așteaptă până când fiecare cetățean termină
        }

        System.out.println("\n--- Toți clienții și-au primit actele și au plecat! ---");

        // ---------------------------------------------------------
        // 7. ÎNCHIDERE CURATĂ (Graceful Shutdown)
        // ---------------------------------------------------------
        worker1.stop();
        worker2.stop();
        worker3.stop();

        t1.interrupt();
        t2.interrupt();
        t3.interrupt();

        breakScheduler.shutdown();
        Scheduler.resetInstance();
        RouteResolver.resetInstance();

        System.out.println("=== Simulare încheiată cu succes ===");
    }
}