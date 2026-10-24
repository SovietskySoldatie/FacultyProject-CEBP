package bureaucraticSystem.counter;

/**
 * Interfața comună pentru un ghișeu administrativ (DeskWorker).
 * Permite injectarea polimorfică a implementării sigure sau cu fault:
 *  - {@link DeskWorkerSafe}: varianta corectă, sincronizează ambele resurse partajate (Stamp & Counter)
 *  - {@link DeskWorkerUnsafe}: varianta cu fault, apelează increment() pe contor fără protecție de lock,
 *    generând fenomenul de Lost Updates la concurență (Race Condition pe DocumentCounter).
 */
public interface DeskWorker extends Runnable {

    /**
     * Returnează identificatorul unic al ghișeului (ex: "Desk-Politie").
     */
    String getDeskId();

    /**
     * Oprește bucla de lucru a ghișeului și trezește firul de execuție dacă este blocat în coadă.
     */
    void stop();

    /**
     * Indică dacă ghișeul este activ.
     */
    boolean isRunning();
}
