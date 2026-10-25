package bureaucraticSystem.resource;
import java.time.LocalTime;
import java.util.concurrent.locks.ReentrantLock;

public class UnsafeCounter implements DocumentCounter {
    private final ReentrantLock lock = new ReentrantLock(true);
    private int count = 0;

    @Override
    public void acquireCounter() { lock.lock(); }

    @Override
    public void releaseCounter() { lock.unlock(); }

    @Override
    public boolean tryAcquireCounter(long timeoutMs) {
        lock.lock();
        return true;
    }

    @Override
    public int increment(String deskId) {
        count++;
        System.out.println("[" + LocalTime.now() + "] Unsafe counter " + deskId + " registered. Total: " + count);
        return count;
    }

    @Override
    public int getCount() {
        return count;
    }
}