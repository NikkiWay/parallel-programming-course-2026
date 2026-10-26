package lab2;

/* Этап 2: лок, о котором знает планировщик. Поток, ждущий занятый лок, не попадает в enabled;
 * обычный synchronized / ReentrantLock здесь нельзя — поток уснёт с ходом в руках и всё зависнет.
 * Лог: "lock <имя>", "unlock <имя>". Планировщик должен знать все локи, чтобы печатать held={a:A, b:B}. */
public final class SchedLock {
    final String name;
    Scheduler.Worker owner;

    /** Создаётся в setup() тестовой программы; зарегистрируйте лок в планировщике s. */
    public SchedLock(Scheduler scheduler, String name) {
        this.name = name;
        scheduler.register(this);
    }

    /** Пауза; если лок занят — ждать, не участвуя в выборе; затем стать владельцем. */
    public void lock() {
        Scheduler.Worker worker = Scheduler.startOperation(() -> owner == null);
        owner = worker;
        worker.finishOperation("lock " + name);
    }

    /** Пауза; освободить лок, ждавшие потоки снова могут быть выбраны. */
    public void unlock() {
        Scheduler.Worker worker = Scheduler.startOperation(Scheduler.ALWAYS);
        if (owner != worker) throw new IllegalStateException("thread " + worker.letter + " unlocks " + name + " without holding it");
        owner = null;
        worker.finishOperation("unlock " + name);
    }
}
