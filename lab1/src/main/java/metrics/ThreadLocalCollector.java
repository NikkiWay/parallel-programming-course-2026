package metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public final class ThreadLocalCollector implements MetricsCollector {

    static final class ThreadState {
        final AtomicLongArray buckets = new AtomicLongArray(Histograms.BUCKETS);
        final AtomicLong count = new AtomicLong();
        final AtomicLong sum = new AtomicLong();
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong max = new AtomicLong(0);
    }

    private final List<ThreadState> allStates = new ArrayList<>();
    private final Object listLock = new Object();

    private final ThreadLocal<ThreadState> myState = ThreadLocal.withInitial(() -> {
        ThreadState state = new ThreadState();
        synchronized (listLock) {
            allStates.add(state);
        }
        return state;
    });

    @Override
    public void record(long value) {
        ThreadState state = myState.get();
        int bucketIndex = Histograms.bucketOf(value);

        state.buckets.setRelease(bucketIndex, state.buckets.getPlain(bucketIndex) + 1);
        state.count.setRelease(state.count.getPlain() + 1);
        state.sum.setRelease(state.sum.getPlain() + value);

        if (value < state.min.getPlain()) {
            state.min.setRelease(value);
        }
        if (value > state.max.getPlain()) {
            state.max.setRelease(value);
        }
    }

    @Override
    public Snapshot snapshot() {
        List<ThreadState> statesCopy;
        synchronized (listLock) {
            statesCopy = new ArrayList<>(allStates);
        }

        long[] totalBuckets = new long[Histograms.BUCKETS];
        long totalCount = 0;
        long totalSum = 0;
        long totalMin = Long.MAX_VALUE;
        long totalMax = 0;
        for (ThreadState state : statesCopy) {
            for (int bucketIndex = 0; bucketIndex < Histograms.BUCKETS; bucketIndex++) {
                totalBuckets[bucketIndex] += state.buckets.get(bucketIndex);
            }
            totalCount += state.count.get();
            totalSum += state.sum.get();
            totalMin = Math.min(totalMin, state.min.get());
            totalMax = Math.max(totalMax, state.max.get());
        }
        return new Snapshot(
                totalBuckets,
                totalCount,
                totalSum,
                totalMin,
                totalMax,
                Histograms.percentile(totalBuckets, totalCount, 0.50),
                Histograms.percentile(totalBuckets, totalCount, 0.99));
    }
}
