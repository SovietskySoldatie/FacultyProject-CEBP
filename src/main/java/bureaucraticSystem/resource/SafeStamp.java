package bureaucraticSystem.resource;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class SafeStamp implements OfficialStamp {
    private final ReentrantLock lock = new ReentrantLock(true);

    @Override
    public void acquireStamp() { lock.lock(); }

    @Override
    public void releaseStamp() { lock.unlock(); }

    @Override
    public boolean tryAcquireStamp(long timeoutMs) throws InterruptedException {
        // Încearcă să ia ștampila. Dacă nu reușește în 'timeoutMs', returnează false
        return lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void simulateUsage(String deskId, long durationMs) throws InterruptedException {
        System.out.println("[" + LocalTime.now() + "] Safe Stamp: " + deskId + " uses the stamp...");
        Thread.sleep(durationMs);
    }
}