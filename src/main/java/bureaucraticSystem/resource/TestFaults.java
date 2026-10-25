package bureaucraticSystem.resource;

public class TestFaults extends Test{
    public static void main(String[] args) throws InterruptedException {
        System.out.println("DEMO: Scenariul de Deadlock");
        // SCENARIUL 2: FAULT (Deadlock - Se va bloca complet)
        OfficialStamp stamp = new DeadlockStamp();
        DocumentCounter counter = new UnsafeCounter();
        useCounterAndStamp(stamp, counter);
    }
}