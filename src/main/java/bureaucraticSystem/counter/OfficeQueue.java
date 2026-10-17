package bureaucraticSystem.counter;

import bureaucraticSystem.client.request.ServiceTicket;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Coada thread-safe a unui birou administrativ.
 *
 * Implementează modelul clasic Producer-Consumer:
 *  - Producătorii (Clienții / Scheduler-ul) adaugă cereri prin addTicket()
 *  - Consumatorii (Ghișeele / DeskWorker) preiau cereri prin takeTicket()
 *
 * Sincronizarea este asigurată nativ de LinkedBlockingQueue:
 *  - Dacă coada este goală, ghișeul este suspendat automat în starea WAITING
 *    (0% consum CPU, eliminând complet fenomenul de busy-waiting).
 *  - Permite accesul concurent fără riscuri de race condition sau date corupte.
 */
public class OfficeQueue {

    private final BlockingQueue<ServiceTicket> queue = new LinkedBlockingQueue<>();

    /**
     * Adaugă un tichet în coadă (operație Producer).
     *
     * @param ticket tichetul depus de client (nu poate fi null)
     * @throws InterruptedException dacă firul este întrerupt în timpul așteptării
     */
    public void addTicket(ServiceTicket ticket) throws InterruptedException {
        Objects.requireNonNull(ticket, "Tichetul adăugat în coadă nu poate fi null");
        queue.put(ticket);
    }

    /**
     * Preia primul tichet disponibil din coadă (operație Consumer).
     * Dacă coada este goală, firul curent se blochează până la apariția unui tichet.
     *
     * @return tichetul extras
     * @throws InterruptedException dacă firul este întrerupt în timp ce aștepta la coadă
     */
    public ServiceTicket takeTicket() throws InterruptedException {
        return queue.take();
    }

    /**
     * Preia un tichet cu timp maxim de așteptare (timeout).
     *
     * @param timeout durata maximă de așteptare
     * @param unit    unitatea de măsură a timpului
     * @return tichetul extras sau null dacă a expirat timpul
     * @throws InterruptedException dacă firul este întrerupt
     */
    public ServiceTicket pollTicket(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    /**
     * Returnează numărul de tichete aflate în așteptare în acest moment.
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

    /**
     * Golește toate tichetele din coadă (util pentru resetare și curățare).
     */
    public void clear() {
        queue.clear();
    }
}
