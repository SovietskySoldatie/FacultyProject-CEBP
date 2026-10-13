package bureaucraticSystem.resource;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gestionează pauzele de cafea neprevăzute pentru ghișee.
 */
public class CoffeeBreakScheduler {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<String, AtomicBoolean> deskBreakStates = new ConcurrentHashMap<>();

    public void registerDesk(String deskId) {
        deskBreakStates.put(deskId, new AtomicBoolean(false));
    }

    public boolean isOnBreak(String deskId) {
        AtomicBoolean status = deskBreakStates.get(deskId);
        return status != null && status.get();
    }

    public void startScheduler(String deskId, long intervalSec, long breakDurationSec) {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                System.out.println(" Break " + deskId + " starts coffee break!");
                deskBreakStates.get(deskId).set(true);
                Thread.sleep(TimeUnit.SECONDS.toMillis(breakDurationSec));
                System.out.println(" Break " + deskId + " finished coffee break!");
                deskBreakStates.get(deskId).set(false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, intervalSec, intervalSec, TimeUnit.SECONDS);
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}