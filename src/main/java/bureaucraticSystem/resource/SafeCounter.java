package bureaucraticSystem.resource;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class SafeCounter implements DocumentCounter {
    private final ReentrantLock lock = new ReentrantLock(true);
    private int count = 0;

    @Override
    public void acquireCounter() { lock.lock(); }

    @Override
    public void releaseCounter() { lock.unlock(); }

    @Override
    public boolean tryAcquireCounter(long timeoutMs) throws InterruptedException {
        return lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public int increment(String deskId) {
        lock.lock();
        try {
            count++;
            System.out.println("[" + LocalTime.now() + "] Safe Counter: " + deskId + " registered. Total office: " + count);
            return count;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int getCount() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }
}