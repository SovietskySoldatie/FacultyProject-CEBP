package bureaucraticSystem.counter;

/**
 * Implementarea CORECTĂ și thread-safe a cozii de birou (Good Implementation).
 *
 * Utilizează LinkedBlockingQueue pentru a asigura:
 *  - Excludere mutuală la acces concurent (thread-safety).
 *  - Comportament blocant fără busy-waiting: pune thread-ul în starea WAITING
 *    la consum 0% CPU atunci când coada este goală.
 *
 * Utilizată prin Dependency Injection când sistemul rulează în modul corect.
 */
public class BlockingOfficeQueue extends OfficeQueue {
    // Moștenește implementarea corectă, robustă și sincronizată din clasa de bază OfficeQueue.
}

