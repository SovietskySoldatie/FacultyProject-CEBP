package bureaucraticSystem.counter;

import bureaucraticSystem.client.request.ServiceTicket;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * IMPLEMENTARE CU EROARE INTENȚIONATĂ DE CONCURENȚĂ (Faulty Solution - Etapa 1).
 *
 * Reprezintă fault-ul asumat de Membru 2:
 * "OfficeQueue :: blocking queue vs non-blocking :: data incoherence // race condition"
 *
 * Erori intenționate introduse:
 *  1. Colecție nesincronizată (Data Race): Înlocuiește LinkedBlockingQueue cu un simplu
 *     java.util.LinkedList. Când mai mulți clienți apelează simultan addTicket(), pointerii
 *     interni (prev/next) și contorul size++ se corup reciproc, pierzându-se cereri (lost updates).
 *  2. Comportament non-blocant (Data Incoherence): takeTicket() nu mai blochează firul ghișeului
 *     în starea WAITING dacă lista este goală, ci returnează null imediat sau aruncă excepții.
 *
 * Grație polimorfismului (extinde OfficeQueue), această clasă defectuoasă poate fi injectată
 * direct prin Dependency Injection în DeskWorker sau Scheduler pentru a demonstra defectul la laborator.
 */
public class FaultyOfficeQueue extends OfficeQueue {

    // LinkedList NU este thread-safe și nu oferă niciun mecanism de sincronizare internă.
    private final List<ServiceTicket> unsafeList = new LinkedList<>();

    /**
     * FAULT: Adăugare concurentă nesincronizată.
     * Nu utilizează niciun lock. Mai multe fire de execuție client accesează lista simultan.
     */
    @Override
    public void addTicket(ServiceTicket ticket) {
        // RACE CONDITION: Două fire pot citi aceeași poziție de sfârșit și se suprascriu reciproc.
        // Tichetul unui client se pierde în neant!
        unsafeList.add(ticket);
    }

    /**
     * FAULT: Preluare non-blocantă nesincronizată.
     * Dacă lista este goală, NU adoarme firul în WAITING, ci returnează imediat null.
     */
    @Override
    public ServiceTicket takeTicket() {
        // RACE CONDITION & NON-BLOCKING:
        if (unsafeList.isEmpty()) {
            return null; // Comportament non-blocant greșit
        }
        return unsafeList.remove(0);
    }

    @Override
    public ServiceTicket pollTicket(long timeout, TimeUnit unit) {
        if (unsafeList.isEmpty()) {
            return null;
        }
        return unsafeList.remove(0);
    }

    @Override
    public int size() {
        return unsafeList.size();
    }

    @Override
    public boolean isEmpty() {
        return unsafeList.isEmpty();
    }

    @Override
    public void clear() {
        unsafeList.clear();
    }
}
