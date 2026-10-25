package bureaucraticSystem.resource;

public interface OfficialStamp {

    void acquireStamp();
    void releaseStamp();
    boolean tryAcquireStamp(long timeoutMs) throws InterruptedException;
    void simulateUsage(String deskId, long durationMs) throws InterruptedException;
}