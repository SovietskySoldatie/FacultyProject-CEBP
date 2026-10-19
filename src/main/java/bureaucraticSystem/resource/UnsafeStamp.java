package bureaucraticSystem.resource;
import java.util.concurrent.locks.ReentrantLock;

public class UnsafeStamp implements OfficialStamp {
    private final ReentrantLock lock = new ReentrantLock(true);

    @Override
    public void acquireStamp() { lock.lock(); }

    @Override
    public void releaseStamp() { lock.unlock(); }

    @Override
    public boolean tryAcquireStamp(long timeoutMs) {
        // Implementare intenționat blocantă pentru simularea problemei
        lock.lock();
        return true;
    }

    @Override
    public void simulateUsage(String deskId, long durationMs) throws InterruptedException {
        System.out.println("Unsafe Stamp: " + deskId + " uses stamp...");
        Thread.sleep(durationMs);
    }
}