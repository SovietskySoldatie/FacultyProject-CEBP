package bureaucraticSystem.resource;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class CoffeeBreakScheduler {
    // Thread pool pentru toate ghișeele
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);

    private static class DeskState {
        final AtomicBoolean onBreak = new AtomicBoolean(false);
        ScheduledFuture<?> mainCycleTask;
        ScheduledFuture<?> resetTask;
        // Generation token
        long generation = 0;
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
        if (deskId == null) throw new IllegalArgumentException("deskId can't be null");
        if (intervalSec <= 0 || breakDurationSec <= 0) {
            throw new IllegalArgumentException("Intervals must be positive");
        }
        if (breakDurationSec >= intervalSec) {
            throw new IllegalArgumentException("Break should take less than interval!");
        }

        DeskState state = deskStates.get(deskId);
        if (state == null) {
            throw new IllegalStateException("Desk " + deskId + " not registered.");
        }

        synchronized (state) {
            state.generation++;
            final long currentGen = state.generation;

            if (state.mainCycleTask != null && !state.mainCycleTask.isCancelled()) {
                state.mainCycleTask.cancel(false);
            }
            if (state.resetTask != null && !state.resetTask.isCancelled()) {
                state.resetTask.cancel(false);
            }

            state.onBreak.set(false);

            state.mainCycleTask = executor.scheduleWithFixedDelay(() -> {
                synchronized (state) {
                    // Dacă acest ciclu recurent aparține unei planificări vechi, îl ignorăm
                    if (state.generation != currentGen) return;
                    if (state.onBreak.get()) return;

                    state.onBreak.set(true);
                    System.out.println(deskId + " is on coffee break!");

                    state.resetTask = executor.schedule(() -> {
                        synchronized (state) {
                            // VERIFICAREA CHEIE: Doar dacă generația este aceeași, modificăm starea
                            if (state.generation == currentGen) {
                                state.onBreak.set(false);
                                System.out.println(" 🏢 " + deskId + " is back from coffee break.");
                            }
                        }
                    }, breakDurationSec, TimeUnit.SECONDS);
                }
            }, intervalSec, intervalSec, TimeUnit.SECONDS);
        }
    }

    /**
     * Oprește planificarea pauzelor strict pentru un singur ghișeu,
     * lăsând restul instituției neafectată.
     */
    public void stopDeskBreaks(String deskId) {
        if (deskId == null) return;
        DeskState state = deskStates.get(deskId);
        if (state != null) {
            synchronized (state) {
                state.generation++; // Invalidează orice task aflat în așteptare
                if (state.mainCycleTask != null) state.mainCycleTask.cancel(false);
                if (state.resetTask != null) state.resetTask.cancel(false);
                state.onBreak.set(false);
            }
        }
    }

    /**
     * Oprește complet executorul global pentru TOATE ghișeele.
     * Utilizat doar la închiderea sistemului (System Shutdown).
     */
    public void shutdownAll() {
        executor.shutdownNow();
    }
}