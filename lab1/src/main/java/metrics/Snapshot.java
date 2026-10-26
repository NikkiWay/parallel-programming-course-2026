package metrics;

public record Snapshot(
        long[] buckets,
        long count,
        long sum,
        long min,
        long max,
        long p50,
        long p99
) {
    public Snapshot {
        if (buckets.length != Histograms.BUCKETS)
            throw new IllegalArgumentException(
                    "Полученное количество " + buckets.length + " не сходится с ожидаемым " + Histograms.BUCKETS);
    }

    public long bucketSum() {
        long total = 0;
        for (long element : buckets) {
            total += element;
        }
        return total;
    }
}
