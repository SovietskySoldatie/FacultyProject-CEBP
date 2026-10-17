package bureaucraticSystem.counter;

import bureaucraticSystem.client.request.ServiceTicket;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Coada thread-safe a unui birou.
 *
 * Folosește LinkedBlockingQueue (model Producer-Consumer):
 * - Clienții adaugă cereri prin addTicket() (Producers)
 * - Ghișeele (DeskWorker) preiau cereri prin takeTicket() (Consumers)
 *
 * Dacă coada este goală, ghișeul este pus automat în așteptare (WAITING)
 * fără busy-waiting (fără consum inutil de procesor).
 */
public class OfficeQueue {
    private final BlockingQueue<ServiceTicket> queue = new LinkedBlockingQueue<>();

    /**
     * Adaugă un tichet în coadă (operație executată de client).
     */
    public void addTicket(ServiceTicket ticket) throws InterruptedException {
        queue.put(ticket);
    }

    /**
     * Preia primul tichet din coadă (operație executată de ghișeu).
     * Dacă coada este goală, firul de execuție se blochează până când sosește un
     * client.
     */
    public ServiceTicket takeTicket() throws InterruptedException {
        return queue.take(); // Operație blocantă thread-safe => WAITING
    }

    /**
     * Preia un tichet cu timp maxim de așteptare.
     */
    public ServiceTicket pollTicket(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit); // non-blocking, returnează null dacă nu există tichete
    }

    /**
     * Returnează numărul de clienți care așteaptă la coadă în acest moment.
     */
    public int size() {
        return queue.size();
    }

    /**
     * Verifică dacă coada este goală.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
