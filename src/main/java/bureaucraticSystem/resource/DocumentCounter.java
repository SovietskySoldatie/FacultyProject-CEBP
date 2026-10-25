package bureaucraticSystem.resource;

public interface DocumentCounter {
    void acquireCounter();
    void releaseCounter();
    boolean tryAcquireCounter(long timeoutMs) throws InterruptedException;
    int increment(String deskId);
    int getCount();
}