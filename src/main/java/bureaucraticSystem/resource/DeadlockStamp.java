package bureaucraticSystem.resource;

import java.time.LocalTime;
import java.util.concurrent.locks.ReentrantLock;

/**
 * IMPLEMENTARE PENTRU DEMONSTRAREA DEFECTULUI DE DEADLOCK (Demo 5 - Deadlock).
 *
 * Reprezintă ștampila blocantă concepută special pentru a produce Coffman Circular Wait:
 *  - Metoda {@link #tryAcquireStamp(long)} ignoră timeout-ul și apelează lock.lock() blocant necondiționat.
 *  - Când este folosită în combinație cu {@link UnsafeCounter} (care blochează și el necondiționat),
 *    ghișeele cu ordini opuse de achiziție (stampFirst=true vs stampFirst=false) intră în Deadlock.
 */
public class DeadlockStamp implements OfficialStamp {
    private final ReentrantLock lock = new ReentrantLock(true);

    @Override
    public void acquireStamp() {
        lock.lock();
    }

    @Override
    public void releaseStamp() {
        lock.unlock();
    }

    @Override
    public boolean tryAcquireStamp(long timeoutMs) {
        // Implementare intenționat blocantă pentru forțarea Circular Wait Deadlock
        lock.lock();
        return true;
    }

    @Override
    public void simulateUsage(String deskId, long durationMs) throws InterruptedException {
        System.out.println("[" + LocalTime.now() + "] Deadlock Stamp: " + deskId + " uses stamp...");
        Thread.sleep(durationMs);
    }
}
