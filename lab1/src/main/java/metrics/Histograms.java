package metrics;

public final class Histograms {

    public static final int BUCKETS = 256;
    public static final int BUCKET_WIDTH_MS = 4;

    private Histograms() {
    }

    public static int bucketOf(long value) {
        if (value < 0)
            return 0;
        long bucketIndex = value / BUCKET_WIDTH_MS;
        return (int) Math.min(bucketIndex, BUCKETS - 1);
    }

    public static long percentile(long[] buckets, long count, double fraction) {
        if (count <= 0) {
            return 0;
        }
        double threshold = count * fraction;
        long accumulated = 0;
        for (int bucketIndex = 0; bucketIndex < buckets.length; bucketIndex++) {
            accumulated += buckets[bucketIndex];
            if (accumulated >= threshold) {
                return (long) bucketIndex * BUCKET_WIDTH_MS;
            }
        }
        return (long) (buckets.length - 1) * BUCKET_WIDTH_MS;
    }
}
