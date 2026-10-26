package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    /** Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner. */
    boolean logging;
    /** Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100". */
    final List<String> log = new ArrayList<>();
    /** null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2). */
    String deadlock;

    static final BooleanSupplier ALWAYS = () -> true;

    private static final ThreadLocal<Worker> CURRENT_WORKER = new ThreadLocal<>();

    private final List<Worker> workers = new ArrayList<>();
    private final List<SchedLock> locks = new ArrayList<>();
    private final StringBuilder trace = new StringBuilder();
    private final Semaphore schedulerTurn = new Semaphore(0);
    private boolean cancelled;

    public Scheduler() { }

    /** Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn. */
    public void spawn(Runnable body) {
        workers.add(new Worker(this, workers.size(), body));
    }

    /** Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last). */
    public void run(Strategy strategy) {
        try {
            for (Worker worker : workers) {
                worker.thread.start();
                waitForTurnBack(worker);
            }
            int last = -1;
            while (true) {
                List<Integer> enabled = enabledWorkers();
                if (enabled.isEmpty()) break;
                int chosen = strategy.pick(enabled, last);
                if (!enabled.contains(chosen)) {
                    throw new IllegalStateException("strategy picked thread " + chosen + " outside enabled " + enabled);
                }
                Worker worker = workers.get(chosen);
                trace.append(worker.letter);
                last = chosen;
                worker.state = State.RUNNING;
                worker.turn.release();
                waitForTurnBack(worker);
            }
            if (workers.stream().anyMatch(worker -> worker.state != State.FINISHED)) deadlock = heldLocks();
        } finally {
            cancelPausedWorkers();
            joinWorkers();
        }
    }

    /** Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB". */
    public String traceString() {
        return trace.toString();
    }

    /** window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна. */
    public static void point() {
        currentWorker().finishOperation("window");
    }

    /** Ждать, пока cond не станет true. До этого поток не попадает в enabled. */
    public static void await(BooleanSupplier condition) {
        startOperation(condition).finishOperation("await done");
    }

    static Worker startOperation(BooleanSupplier canProceed) {
        Worker worker = currentWorker();
        if (worker.pauseRequired || !canProceed.getAsBoolean()) worker.pause(canProceed);
        return worker;
    }

    void register(SchedLock lock) {
        locks.add(lock);
    }

    private static Worker currentWorker() {
        Worker worker = CURRENT_WORKER.get();
        if (worker == null) throw new IllegalStateException("scheduler operation outside a scheduler thread");
        if (worker.scheduler.cancelled) throw new RunCancelled();
        return worker;
    }

    private void waitForTurnBack(Worker worker) {
        schedulerTurn.acquireUninterruptibly();
        if (worker.failure != null) throw new IllegalStateException("thread " + worker.letter + " failed", worker.failure);
    }

    private List<Integer> enabledWorkers() {
        List<Integer> enabled = new ArrayList<>();
        for (Worker worker : workers) {
            if (worker.state == State.PAUSED && worker.canProceed.getAsBoolean()) enabled.add(worker.id);
        }
        return enabled;
    }

    private String heldLocks() {
        StringJoiner held = new StringJoiner(", ", "held={", "}");
        for (SchedLock lock : locks) {
            if (lock.owner != null) held.add(lock.name + ":" + lock.owner.letter);
        }
        return held.toString();
    }

    private void cancelPausedWorkers() {
        cancelled = true;
        for (Worker worker : workers) {
            if (worker.state == State.PAUSED) worker.turn.release();
        }
    }

    private void joinWorkers() {
        boolean interrupted = false;
        for (Worker worker : workers) {
            while (true) {
                try {
                    worker.thread.join();
                    break;
                } catch (InterruptedException interruption) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private enum State { RUNNING, PAUSED, FINISHED }

    private static final class RunCancelled extends RuntimeException {
        RunCancelled() { super(null, null, false, false); }
    }

    static final class Worker {
        final Scheduler scheduler;
        final int id;
        final char letter;
        final Thread thread;
        final Semaphore turn = new Semaphore(0);
        State state = State.RUNNING;
        BooleanSupplier canProceed = ALWAYS;
        boolean pauseRequired = true;
        Throwable failure;

        Worker(Scheduler scheduler, int id, Runnable body) {
            this.scheduler = scheduler;
            this.id = id;
            this.letter = (char) ('A' + id);
            this.thread = new Thread(() -> execute(body), String.valueOf(letter));
        }

        void finishOperation(String operation) {
            if (scheduler.logging) scheduler.log.add(letter + "  " + operation);
            pauseRequired = true;
        }

        private void pause(BooleanSupplier condition) {
            canProceed = condition;
            state = State.PAUSED;
            scheduler.schedulerTurn.release();
            turn.acquireUninterruptibly();
            if (scheduler.cancelled) throw new RunCancelled();
            pauseRequired = false;
        }

        private void execute(Runnable body) {
            CURRENT_WORKER.set(this);
            try {
                body.run();
            } catch (RunCancelled cancellation) {
                return;
            } catch (Throwable throwable) {
                failure = throwable;
            }
            state = State.FINISHED;
            scheduler.schedulerTurn.release();
        }
    }
}
