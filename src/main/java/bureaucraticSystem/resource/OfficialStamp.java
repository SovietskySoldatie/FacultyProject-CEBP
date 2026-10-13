package bureaucraticSystem.resource;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Resursă unică partajată între ghișee.
 */
public class OfficialStamp {
    // Fair lock = prevenire starvation între ghișee
    private final ReentrantLock lock = new ReentrantLock(true);

    public void useStamp(String deskId, long durationMs) throws InterruptedException {
        lock.lock();
        try {
            System.out.println(" Stamp taken by " + deskId);
            Thread.sleep(durationMs);
        } finally {
            System.out.println(" Stamp released by " + deskId);
            lock.unlock();
        }
    }
}