package metrics;

import java.util.SplittableRandom;

public final class ZipfLoad {

    public static final int SIZE = 1 << 20;
    public static final int MAX_VALUE = 1023;
    public static final double EXPONENT = 1.15;
    public static final long SEED = 42L;

    private ZipfLoad() {
    }

    public static int[] generate() {
        return generate(SIZE, MAX_VALUE, EXPONENT, SEED);
    }

    public static int[] generate(int size, int maxValue, double exponent, long seed) {
        double[] cumulativeProbability = new double[maxValue];
        double weightSum = 0.0;
        for (int value = 1; value <= maxValue; value++) {
            weightSum += Math.pow(value, -exponent);
            cumulativeProbability[value - 1] = weightSum;
        }
        for (int index = 0; index < maxValue; index++) {
            cumulativeProbability[index] /= weightSum;
        }
        cumulativeProbability[maxValue - 1] = 1.0;
        SplittableRandom random = new SplittableRandom(seed);
        int[] values = new int[size];
        for (int index = 0; index < size; index++) {
            double uniformRandom = random.nextDouble();
            values[index] = 1 + firstIndexAtLeast(cumulativeProbability, uniformRandom);
        }
        return values;
    }

    private static int firstIndexAtLeast(double[] sortedValues, double threshold) {
        int lowIndex = 0;
        int highIndex = sortedValues.length - 1;
        while (lowIndex < highIndex) {
            int middleIndex = (lowIndex + highIndex) >>> 1;
            if (sortedValues[middleIndex] >= threshold) {
                highIndex = middleIndex;
            } else {
                lowIndex = middleIndex + 1;
            }
        }
        return lowIndex;
    }
}
