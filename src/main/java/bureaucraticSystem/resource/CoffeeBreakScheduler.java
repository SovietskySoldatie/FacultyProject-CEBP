package bureaucraticSystem.resource;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class CoffeeBreakScheduler {
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);

    /**
     * Clasa de stare per-ghișeu cerută în Code Review.
     * Reține starea booleană, dar și referințele către task-urile planificate,
     * pentru a putea fi anulate dacă se suprapun.
     */
    private static class DeskState {
        final AtomicBoolean onBreak = new AtomicBoolean(false);
        ScheduledFuture<?> mainCycleTask;
        ScheduledFuture<?> resetTask;
    }

    private final Map<String, DeskState> deskStates = new ConcurrentHashMap<>();

    public void registerDesk(String deskId) {
        if (deskId == null) {
            throw new IllegalArgumentException("deskId can't be null");
        }
        deskStates.putIfAbsent(deskId, new DeskState());
    }

    public boolean isOnBreak(String deskId) {
        if (deskId == null) return false;
        DeskState state = deskStates.get(deskId);
        return state != null && state.onBreak.get();
    }

    public void startScheduler(String deskId, long intervalSec, long breakDurationSec) {
        if (deskId == null) {
            throw new IllegalArgumentException("deskId can't be null");
        }
        if (intervalSec <= 0 || breakDurationSec <= 0) {
            throw new IllegalArgumentException("Intervals must be positive");
        }
        // Validarea cerută: pauza nu poate fi mai lungă sau egală cu intervalul
        if (breakDurationSec >= intervalSec) {
            throw new IllegalArgumentException("Break can't be longer than interval!");
        }

        DeskState state = deskStates.get(deskId);
        if (state == null) {
            throw new IllegalStateException("Desk " + deskId + " is not registered.");
        }

        // Sincronizăm pe starea ghișeului pentru a preveni race conditions
        // în cazul în care startScheduler e apelat de mai multe ori simultan
        synchronized (state) {
            // Dacă există deja o planificare pentru acest ghișeu, o anulăm pe cea veche
            if (state.mainCycleTask != null && !state.mainCycleTask.isCancelled()) {
                state.mainCycleTask.cancel(false);
            }
            if (state.resetTask != null && !state.resetTask.isCancelled()) {
                state.resetTask.cancel(false);
            }
            state.onBreak.set(false);

            // Planificăm noul ciclu
            state.mainCycleTask = executor.scheduleWithFixedDelay(() -> {
                synchronized (state) {
                    // Dacă dintr-un motiv neprevăzut e deja în pauză, ignorăm declanșarea
                    if (state.onBreak.get()) return;

                    state.onBreak.set(true);
                    System.out.println(deskId + " entered coffee break!");

                    // Planificăm task-ul care îl va scoate din pauză
                    state.resetTask = executor.schedule(() -> {
                        state.onBreak.set(false);
                        System.out.println(deskId + " is back from coffee break.");
                    }, breakDurationSec, TimeUnit.SECONDS);
                }
            }, intervalSec, intervalSec, TimeUnit.SECONDS);
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}