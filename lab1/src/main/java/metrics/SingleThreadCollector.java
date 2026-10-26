package metrics;

public final class SingleThreadCollector implements MetricsCollector {

    private final long[] buckets = new long[Histograms.BUCKETS];
    private long count;
    private long sum;
    private long min = Long.MAX_VALUE;
    private long max;

    @Override
    public void record(long value) {
        buckets[Histograms.bucketOf(value)]++;
        count++;
        sum += value;
        if (value < min) {
            min = value;
        }
        if (value > max) {
            max = value;
        }
    }

    @Override
    public Snapshot snapshot() {
        long[] bucketsCopy = buckets.clone();
        return new Snapshot(
                bucketsCopy,
                count,
                sum,
                min,
                max,
                Histograms.percentile(bucketsCopy, count, 0.50),
                Histograms.percentile(bucketsCopy, count, 0.99));
    }
}
