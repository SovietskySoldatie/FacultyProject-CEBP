package bureaucraticSystem.scheduler;

import bureaucraticSystem.client.request.ServiceTicket;
import bureaucraticSystem.counter.OfficeQueue;

/**
 * Contractul unui scheduler de tichete. Este implementat de varianta corecta
 * (Scheduler) si de varianta cu fault intentionat (FaultyScheduler), astfel incat
 * consumatorii sa poata primi oricare dintre ele prin Dependency Injection.
 */
public interface TicketDispatcher {

    void registerOffice(String officeId, OfficeQueue queue);

    void unregisterOffice(String officeId);

    OfficeQueue getOfficeQueue(String officeId);

    /**
     * Plaseaza tichetul la coada biroului indicat.
     */
    void dispatch(ServiceTicket ticket, String officeId) throws InterruptedException;

    /**
     * Plaseaza tichetul la biroul care emite documentul cerut (pe baza registrului).
     */
    void dispatch(ServiceTicket ticket) throws InterruptedException;

    void stop();

    boolean isRunning();
}
