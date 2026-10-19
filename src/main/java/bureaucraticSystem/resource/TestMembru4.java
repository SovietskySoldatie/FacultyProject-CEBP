package bureaucraticSystem.resource;

import bureaucraticSystem.counter.DeskWorker;
import bureaucraticSystem.counter.OfficeQueue;
// ai nevoie sa-ti importi clasa ta de ticket in functie de cum l-au facut


public class TestMembru4 {
    public static void main(String[] args) throws Exception {
        SafeStamp stamp = new SafeStamp();
        CoffeeBreakScheduler scheduler = new CoffeeBreakScheduler();
        OfficeQueue queue = new OfficeQueue();

        scheduler.registerDesk("Desk-A");
        scheduler.startScheduler("Desk-A", 2, 3); // pauză mai deasă ca să se vadă la test

        // Creezi worker-ul modificat de tine
        DeskWorker desk1 = new DeskWorker("Desk-A", queue, scheduler, stamp);
        Thread t1 = new Thread(desk1);
        t1.start();

        // Trimiti cateva tichete de test (aici va depinde cum e definit ServiceTicket la voi)
        // ... (restul testului depinde de constructorul de ServiceTicket)

        Thread.sleep(10000); // lasi testul sa ruleze 10s
        scheduler.shutdown();
        desk1.stop();
        t1.interrupt();
    }
}