package metrics;

public final class EmptyLockCollector implements MetricsCollector {

    @Override
    public synchronized void record(long value) {
    }

    @Override
    public Snapshot snapshot() {
        return new Snapshot(new long[Histograms.BUCKETS], 0, 0, Long.MAX_VALUE, 0, 0, 0);
    }
}
