package metrics;

import java.util.Locale;

public final class StressTest {

    private static final int WRITERS = 4;
    private static final int SNAPSHOTS = 10_000;

    private static volatile boolean stopRequested;

    private StressTest() {
    }

    public static void main(String[] arguments) throws InterruptedException {
        if (arguments.length < 1) {
            System.err.println("использование: StressTest <реализация>");
            System.exit(2);
        }
        String implementationName = arguments[0];
        MetricsCollector collector = CollectorFactory.create(implementationName);
        int[] values = ZipfLoad.generate();

        long[] callsPerWriter = new long[WRITERS];
        Thread[] writers = new Thread[WRITERS];
        for (int writerIndex = 0; writerIndex < WRITERS; writerIndex++) {
            final int index = writerIndex;
            writers[writerIndex] = new Thread(() -> {
                long localCalls = 0;
                int valueIndex = (index * 1000) % values.length;
                while (!stopRequested) {
                    collector.record(values[valueIndex]);
                    localCalls++;
                    valueIndex++;
                    if (valueIndex == values.length) {
                        valueIndex = 0;
                    }
                }
                callsPerWriter[index] = localCalls;
            }, "writer-" + writerIndex);
            writers[writerIndex].start();
        }

        int broken = 0;
        int sumLess = 0;
        int sumGreater = 0;
        for (int snapshotIndex = 0; snapshotIndex < SNAPSHOTS; snapshotIndex++) {
            Snapshot snapshot = collector.snapshot();
            long bucketSum = snapshot.bucketSum();
            if (bucketSum != snapshot.count()) {
                broken++;
                if (bucketSum < snapshot.count()) {
                    sumLess++;
                } else {
                    sumGreater++;
                }
            }
        }

        stopRequested = true;
        long totalCalls = 0;
        for (int writerIndex = 0; writerIndex < WRITERS; writerIndex++) {
            writers[writerIndex].join();
            totalCalls += callsPerWriter[writerIndex];
        }
        long finalCount = collector.snapshot().count();

        System.out.printf(Locale.ROOT, "impl=%s writers=%d snapshots=%d%n", implementationName, WRITERS, SNAPSHOTS);
        System.out.printf(Locale.ROOT, "broken=%d (%.2f%%)  sum<count=%d  sum>count=%d%n",
                broken, 100.0 * broken / SNAPSHOTS, sumLess, sumGreater);
        System.out.printf(Locale.ROOT, "calls=%d  finalCount=%d  difference=%d%n",
                totalCalls, finalCount, finalCount - totalCalls);
    }
}
