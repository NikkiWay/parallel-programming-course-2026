package metrics;

public final class SingleLockCollector implements MetricsCollector {

    private final SingleThreadCollector inner = new SingleThreadCollector();

    @Override
    public synchronized void record(long value) {
        inner.record(value);
    }

    @Override
    public synchronized Snapshot snapshot() {
        return inner.snapshot();
    }
}
