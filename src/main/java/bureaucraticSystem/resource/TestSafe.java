package bureaucraticSystem.resource;

//For the safe version

public class TestSafe extends Test {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("DEMO: Scenariul Safe");
        // SCENARIUL 1: SAFE (Fără deadlock - Va renunța și va încerca din nou)
        OfficialStamp stamp = new SafeStamp();
        DocumentCounter counter = new SafeCounter();
        useCounterAndStamp(stamp, counter);
    }
}