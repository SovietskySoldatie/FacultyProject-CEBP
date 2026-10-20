package bureaucraticSystem.resource;
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
    public void increment(String deskId) {
        count++;
        System.out.println("Safe Counter: " + deskId + " registered. Total office: " + count);
    }
}