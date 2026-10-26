package metrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class DoubleBufferCollector implements MetricsCollector {

    private static final int NOWHERE = -1;

    static final class ThreadBuffers {
        final long[][] buckets = new long[2][Histograms.BUCKETS];
        final long[] count = new long[2];
        final long[] sum = new long[2];
        final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE};
        final long[] max = {0, 0};
        final AtomicInteger inside = new AtomicInteger(NOWHERE);
    }

    private volatile int active = 0;

    private final Object snapLock = new Object();
    private final List<ThreadBuffers> allBuffers = new ArrayList<>();

    private final long[] globalBuckets = new long[Histograms.BUCKETS];
    private long globalCount;
    private long globalSum;
    private long globalMin = Long.MAX_VALUE;
    private long globalMax;

    private final boolean recheckActive;

    private final ThreadLocal<ThreadBuffers> myBuffers = ThreadLocal.withInitial(() -> {
        ThreadBuffers buffers = new ThreadBuffers();
        synchronized (snapLock) {
            allBuffers.add(buffers);
        }
        return buffers;
    });

    public DoubleBufferCollector() {
        this(true);
    }

    public DoubleBufferCollector(boolean recheckActive) {
        this.recheckActive = recheckActive;
    }

    @Override
    public void record(long value) {
        ThreadBuffers currentThreadBuffers = myBuffers.get();
        int bufferIndex;
        while (true) {
            bufferIndex = active;
            currentThreadBuffers.inside.set(bufferIndex);
            if (!recheckActive || active == bufferIndex) {
                break;
            }
            currentThreadBuffers.inside.setRelease(NOWHERE);
        }

        int bucketIndex = Histograms.bucketOf(value);
        currentThreadBuffers.buckets[bufferIndex][bucketIndex]++;
        currentThreadBuffers.count[bufferIndex]++;
        currentThreadBuffers.sum[bufferIndex] += value;
        if (value < currentThreadBuffers.min[bufferIndex]) {
            currentThreadBuffers.min[bufferIndex] = value;
        }
        if (value > currentThreadBuffers.max[bufferIndex]) {
            currentThreadBuffers.max[bufferIndex] = value;
        }

        currentThreadBuffers.inside.setRelease(NOWHERE);
    }

    @Override
    public Snapshot snapshot() {
        synchronized (snapLock) {
            int frozen = active;
            active = 1 - frozen;

            for (ThreadBuffers buffers : allBuffers) {
                while (buffers.inside.get() == frozen) {
                    Thread.onSpinWait();
                }
                for (int bucketIndex = 0; bucketIndex < Histograms.BUCKETS; bucketIndex++) {
                    globalBuckets[bucketIndex] += buffers.buckets[frozen][bucketIndex];
                }
                globalCount += buffers.count[frozen];
                globalSum += buffers.sum[frozen];
                globalMin = Math.min(globalMin, buffers.min[frozen]);
                globalMax = Math.max(globalMax, buffers.max[frozen]);

                Arrays.fill(buffers.buckets[frozen], 0);
                buffers.count[frozen] = 0;
                buffers.sum[frozen] = 0;
                buffers.min[frozen] = Long.MAX_VALUE;
                buffers.max[frozen] = 0;
            }

            long[] bucketsCopy = globalBuckets.clone();
            return new Snapshot(
                    bucketsCopy,
                    globalCount,
                    globalSum,
                    globalMin,
                    globalMax,
                    Histograms.percentile(bucketsCopy, globalCount, 0.50),
                    Histograms.percentile(bucketsCopy, globalCount, 0.99));
        }
    }
}
