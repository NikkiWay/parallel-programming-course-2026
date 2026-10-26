package metrics;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

public final class Bench {

    private static final int[] DEFAULT_THREAD_COUNTS = {1, 2, 4, 8, 16};
    private static final int DEFAULT_SECONDS_PER_RUN = 5;
    private static final int DEFAULT_RUNS_PER_POINT = 5;

    private static volatile boolean stopRequested;

    private Bench() {
    }

    public static void main(String[] arguments) throws InterruptedException {
        if (arguments.length < 1) {
            System.err.println("использование: Bench <реализация> [потоки=1,2,4,8,16] [секунд=5] [забегов=5]");
            System.exit(2);
        }
        String implementationName = arguments[0];
        int[] threadCounts = arguments.length > 1 ? parseIntList(arguments[1]) : DEFAULT_THREAD_COUNTS;
        int secondsPerRun = arguments.length > 2 ? Integer.parseInt(arguments[2]) : DEFAULT_SECONDS_PER_RUN;
        int runsPerPoint = arguments.length > 3 ? Integer.parseInt(arguments[3]) : DEFAULT_RUNS_PER_POINT;

        int[] values = ZipfLoad.generate();
        System.out.println("impl=" + implementationName + " seed=" + ZipfLoad.SEED);

        for (int threadCount : threadCounts) {
            MetricsCollector collector = CollectorFactory.create(implementationName);
            double median = measurePoint(collector, values, threadCount, secondsPerRun, runsPerPoint);
            System.out.printf(Locale.ROOT, "T=%2d  median=%8.2f Mops/s  count=%d%n",
                    threadCount, median, collector.snapshot().count());
        }
    }

    static double measurePoint(MetricsCollector collector, int[] values, int threadCount,
                               int secondsPerRun, int runsPerPoint) throws InterruptedException {
        run(collector, values, threadCount, secondsPerRun);
        double[] runResults = new double[runsPerPoint];
        for (int runIndex = 0; runIndex < runsPerPoint; runIndex++) {
            runResults[runIndex] = run(collector, values, threadCount, secondsPerRun);
        }
        System.out.println("  runs=" + Arrays.toString(runResults));
        return median(runResults);
    }

    static double run(MetricsCollector collector, int[] values, int threadCount, int seconds)
            throws InterruptedException {
        CountDownLatch startSignal = new CountDownLatch(1);
        long[] operationsPerThread = new long[threadCount];
        Thread[] workers = new Thread[threadCount];
        stopRequested = false;

        for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {
            final int workerIndex = threadIndex;
            workers[threadIndex] = new Thread(() -> {
                long localOperations = 0;
                int valueIndex = (workerIndex * 1000) % values.length;
                try {
                    startSignal.await();
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                    return;
                }
                while (!stopRequested) {
                    collector.record(values[valueIndex]);
                    localOperations++;
                    valueIndex++;
                    if (valueIndex == values.length) {
                        valueIndex = 0;
                    }
                }
                operationsPerThread[workerIndex] = localOperations;
            }, "worker-" + threadIndex);
            workers[threadIndex].start();
        }

        long startNanoseconds = System.nanoTime();
        startSignal.countDown();
        Thread.sleep(seconds * 1000L);
        stopRequested = true;
        long endNanoseconds = System.nanoTime();
        for (Thread worker : workers) {
            worker.join();
        }

        long totalOperations = 0;
        for (long operations : operationsPerThread) {
            totalOperations += operations;
        }
        double elapsedSeconds = (endNanoseconds - startNanoseconds) / 1e9;
        return totalOperations / elapsedSeconds / 1e6;
    }

    static double median(double[] results) {
        double[] sorted = results.clone();
        Arrays.sort(sorted);
        int size = sorted.length;
        return size % 2 == 1 ? sorted[size / 2] : (sorted[size / 2 - 1] + sorted[size / 2]) / 2;
    }

    private static int[] parseIntList(String commaSeparated) {
        String[] parts = commaSeparated.split(",");
        int[] numbers = new int[parts.length];
        for (int partIndex = 0; partIndex < parts.length; partIndex++) {
            numbers[partIndex] = Integer.parseInt(parts[partIndex].trim());
        }
        return numbers;
    }
}
