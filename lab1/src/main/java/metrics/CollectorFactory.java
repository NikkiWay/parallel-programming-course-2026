package metrics;

public final class CollectorFactory {

    private CollectorFactory() {
    }

    public static MetricsCollector create(String implementationName) {
        return switch (implementationName) {
            case "single" -> new SingleThreadCollector();
            case "empty-lock" -> new EmptyLockCollector();
            case "single-lock" -> new SingleLockCollector();
            case "striped-lock" -> new StripedLockCollector();
            case "thread-local" -> new ThreadLocalCollector();
            case "double-buffer" -> new DoubleBufferCollector();
            case "double-buffer-norecheck" -> new DoubleBufferCollector(false);
            default -> throw new IllegalArgumentException(
                    "неизвестная реализация: " + implementationName
                            + "; доступны: single, empty-lock, single-lock, striped-lock, thread-local, "
                            + "double-buffer, double-buffer-norecheck");
        };
    }
}
