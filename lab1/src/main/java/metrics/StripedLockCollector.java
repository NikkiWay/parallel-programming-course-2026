package metrics;

import java.util.concurrent.atomic.AtomicLong;

public final class StripedLockCollector implements MetricsCollector {

    private static final int GROUPS = 16;

    private final long[] buckets = new long[Histograms.BUCKETS];
    private final Object[] groupLocks = new Object[GROUPS];
    private final AtomicLong count = new AtomicLong();
    private final AtomicLong sum = new AtomicLong();
    private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong max = new AtomicLong(0);

    public StripedLockCollector() {
        for (int group = 0; group < GROUPS; group++) {
            groupLocks[group] = new Object();
        }
    }

    @Override
    public void record(long value) {
        int bucketIndex = Histograms.bucketOf(value);
        synchronized (groupLocks[bucketIndex % GROUPS]) {
            buckets[bucketIndex]++;
        }
        count.incrementAndGet();
        sum.addAndGet(value);
        long currentMin = min.get();
        while (value < currentMin && !min.compareAndSet(currentMin, value)) {
            currentMin = min.get();
        }
        long currentMax = max.get();
        while (value > currentMax && !max.compareAndSet(currentMax, value)) {
            currentMax = max.get();
        }
    }

    @Override
    public Snapshot snapshot() {
        long[] bucketsCopy = new long[Histograms.BUCKETS];
        for (int group = 0; group < GROUPS; group++) {
            synchronized (groupLocks[group]) {
                for (int bucketIndex = group; bucketIndex < Histograms.BUCKETS; bucketIndex += GROUPS) {
                    bucketsCopy[bucketIndex] = buckets[bucketIndex];
                }
            }
        }
        long recordedCount = count.get();
        return new Snapshot(
                bucketsCopy,
                recordedCount,
                sum.get(),
                min.get(),
                max.get(),
                Histograms.percentile(bucketsCopy, recordedCount, 0.50),
                Histograms.percentile(bucketsCopy, recordedCount, 0.99));
    }
}
