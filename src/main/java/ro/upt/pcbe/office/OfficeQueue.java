package ro.upt.pcbe.office;

import ro.upt.pcbe.common.CommonTypes.ServiceTicket;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Coada blocantă thread-safe pentru un birou administrativ.
 * Clienții acționează ca producători (adaugă ServiceTicket prin addTicket),
 * iar ghișeele (DeskWorker) acționează ca consumatori (preiau tichete prin takeTicket).
 *
 * Implementarea folosește LinkedBlockingQueue, garantând sincronizarea corectă
 * fără sincronizare manuală (fără blocuri synchronized explicite sau busy-waiting).
 */
public class OfficeQueue {
    private final BlockingQueue<ServiceTicket> queue = new LinkedBlockingQueue<>();

    /**
     * Adaugă un tichet în coadă (operație Producer).
     * Blochează execuția firului dacă coada atinge capacitatea maximă (în caz de coadă mărginită).
     *
     * @param ticket tichetul depus de client
     * @throws InterruptedException dacă firul de execuție este întrerupt în timpul așteptării
     */
    public void addTicket(ServiceTicket ticket) throws InterruptedException {
        queue.put(ticket);
    }

    /**
     * Preia și elimină primul tichet din coadă (operație Consumer).
     * Blochează execuția firului dacă coada este goală, până când un client adaugă un nou tichet.
     *
     * @return tichetul preluat
     * @throws InterruptedException dacă firul de execuție este întrerupt în timp ce aștepta în coadă
     */
    public ServiceTicket takeTicket() throws InterruptedException {
        return queue.take();
    }

    /**
     * Preia un tichet cu timeout (util pentru operațiuni non-blocante indefinit sau shutdown curat).
     *
     * @param timeout timpul maxim de așteptare
     * @param unit unitatea de timp
     * @return tichetul preluat sau null dacă a expirat timeout-ul
     * @throws InterruptedException dacă firul este întrerupt
     */
    public ServiceTicket pollTicket(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    /**
     * Returnează numărul de clienți/tichete care așteaptă în acest moment la coadă.
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
