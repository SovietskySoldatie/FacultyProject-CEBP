package bureaucraticSystem.resource;

import java.time.LocalTime;

/**
 * IMPLEMENTARE PENTRU DEMONSTRAREA DEFECTULUI DE RACE CONDITION PE CONTOR (Demo 4 - Race Condition Counter).
 *
 * Reprezintă o ștampilă defectuoasă care „cedează” resursa fără a impune excludere mutuală (fără lock exclusiv):
 *  - Metoda {@link #acquireStamp()} nu blochează niciun fir de execuție.
 *  - Metoda {@link #tryAcquireStamp(long)} returnează imediat true, cedând resursa oricărui solicitant.
 *  - Deoarece nu serializează ghișeele la ștampilă, multiple ghișee pot utiliza ștampila în paralel
 *    și ajung concurent la {@link UnsafeCounter}, permițând apariția vizibilă a fenomenului de Lost Updates.
 */
public class UnsafeStamp implements OfficialStamp {

    @Override
    public void acquireStamp() {
        // Cedează direct resursa fără a bloca sau a impune excludere mutuală
    }

    @Override
    public void releaseStamp() {
        // Nu are lock exclusiv de eliberat
    }

    @Override
    public boolean tryAcquireStamp(long timeoutMs) {
        // Cedează imediat resursa oricărui ghișeu solicitant
        return true;
    }

    @Override
    public void simulateUsage(String deskId, long durationMs) throws InterruptedException {
        System.out.println("[" + LocalTime.now() + "] Unsafe Stamp: " + deskId + " uses stamp (unprotected access)...");
        Thread.sleep(durationMs);
    }
}