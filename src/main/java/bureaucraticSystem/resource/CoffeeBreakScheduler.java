package bureaucraticSystem.resource;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CoffeeBreakScheduler {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<String, AtomicBoolean> deskBreakStates = new ConcurrentHashMap<>();

    public void registerDesk(String deskId) {
        Objects.requireNonNull(deskId, "deskId must not be null");
        deskBreakStates.putIfAbsent(deskId, new AtomicBoolean(false));
    }

    public boolean isOnBreak(String deskId) {
        if (deskId == null) {
            return false;
        }
        AtomicBoolean status = deskBreakStates.get(deskId);
        return status != null && status.get();
    }

    public void startScheduler(String deskId, long intervalSec, long breakDurationSec) {
        Objects.requireNonNull(deskId, "deskId must not be null");
        registerDesk(deskId);

        scheduler.scheduleWithFixedDelay(() -> {
            AtomicBoolean state = deskBreakStates.get(deskId);
            if (state == null) {
                return;
            }

            System.out.println(" Break " + deskId + " starts coffee break!");
            state.set(true);

            scheduler.schedule(() -> {
                AtomicBoolean currentState = deskBreakStates.get(deskId);
                if (currentState != null) {
                    currentState.set(false);
                    System.out.println("Break " + deskId + " finished coffee break!");
                }
            }, breakDurationSec, TimeUnit.SECONDS);
        }, intervalSec, intervalSec, TimeUnit.SECONDS);
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}