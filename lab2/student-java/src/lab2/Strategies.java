package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {
    private Strategies() { }

    /** Всегда первый поток из enabled. */
    public static final class FirstAlive implements Strategy {
        public int pick(List<Integer> enabled, int last) {
            return enabled.get(0);
        }
    }

    /** Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1). */
    public static final class Replay implements Strategy {
        private final String trace;
        private int position;

        public Replay(String trace) { this.trace = trace; }

        /** Сбросить позицию в трассе перед прогоном. */
        public void beginRun() { position = 0; }

        public int pick(List<Integer> enabled, int last) {
            if (position == trace.length()) {
                throw new IllegalStateException("trace " + trace + " ended after " + position + " decisions, the run needs more");
            }
            char letter = trace.charAt(position);
            int thread = letter - 'A';
            if (!enabled.contains(thread)) {
                throw new IllegalStateException("trace " + trace + ", decision " + (position + 1) + ": thread " + letter + " is not enabled");
            }
            position++;
            return thread;
        }
    }

    /** Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed). */
    public static final class RandomPick implements Strategy {
        private final Random random;

        public RandomPick(long seed) { random = new Random(seed); }

        public int pick(List<Integer> enabled, int last) {
            return enabled.get(random.nextInt(enabled.size()));
        }
    }

    /** Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     *  bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE. */
    public static final class Dfs implements Strategy {
        private final int bound;
        private final List<Branch> path = new ArrayList<>();
        private int depth;
        private int preemptions;

        public Dfs(int bound) { this.bound = bound; }

        /** Подготовиться к новому прогону (позиция в пути, счётчик вытеснений). */
        public void beginRun() {
            depth = 0;
            preemptions = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            boolean lastEnabled = enabled.contains(last);
            if (depth == path.size()) {
                path.add(new Branch(preemptions < bound || !lastEnabled ? List.copyOf(enabled) : List.of(last)));
            }
            Branch branch = path.get(depth);
            depth++;
            int chosen = branch.options.get(branch.current);
            if (lastEnabled && chosen != last) preemptions++;
            return chosen;
        }

        /** Выбрать следующий путь; false — дерево обойдено. */
        public boolean endRun() {
            while (!path.isEmpty()) {
                Branch branch = path.get(path.size() - 1);
                branch.current++;
                if (branch.current < branch.options.size()) return true;
                path.remove(path.size() - 1);
            }
            return false;
        }

        private static final class Branch {
            final List<Integer> options;
            int current;

            Branch(List<Integer> options) { this.options = options; }
        }
    }
}
