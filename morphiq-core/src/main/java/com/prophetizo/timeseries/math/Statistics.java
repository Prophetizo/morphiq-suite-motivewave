package com.prophetizo.timeseries.math;

/**
 * A collection of static methods for basic statistical computations on arrays of numerical values.
 * <p>
 * These methods assume the arrays are not null and not empty. If they are, an IllegalArgumentException is thrown.
 * <p>
 * This implementation calculates the population variance and standard deviation. If sample statistics are needed,
 * adjust the divisor from 'count' to 'count - 1'.
 */
public class Statistics {

    /**
     * Computes the mean (average) of a non-empty double array.
     * @param values an array of double values, not null or empty
     * @return the mean of the values
     * @throws IllegalArgumentException if values is null or empty
     */
    public static double mean(double[] values) {
        validateNotEmpty(values);
        return meanDouble(values);
    }

    /**
     * Computes the mean (average) of a non-empty long array.
     * @param values an array of long values, not null or empty
     * @return the mean of the values
     * @throws IllegalArgumentException if values is null or empty
     */
    public static double mean(long[] values) {
        validateNotEmpty(values);
        return meanDouble(toDoubleArray(values));
    }

    /**
     * Computes the population variance of a non-empty double array using Welford's algorithm.
     * @param values an array of double values, not null or empty
     * @return the population variance of the values
     * @throws IllegalArgumentException if values is null or empty
     */
    public static double variance(double[] values) {
        validateNotEmpty(values);
        return varianceDouble(values);
    }

    /**
     * Computes the population variance of a non-empty long array using Welford's algorithm.
     * @param values an array of long values, not null or empty
     * @return the population variance of the values
     * @throws IllegalArgumentException if values is null or empty
     */
    public static double variance(long[] values) {
        validateNotEmpty(values);
        return varianceDouble(toDoubleArray(values));
    }

    /**
     * Computes the population standard deviation of a non-empty double array.
     * @param values an array of double values, not null or empty
     * @return the population standard deviation of the values
     * @throws IllegalArgumentException if values is null or empty
     */
    public static double standardDeviation(double[] values) {
        validateNotEmpty(values);
        return Math.sqrt(varianceDouble(values));
    }

    /**
     * Computes the population standard deviation of a non-empty long array.
     * @param values an array of long values, not null or empty
     * @return the population standard deviation of the values
     * @throws IllegalArgumentException if values is null or empty
     */
    public static double standardDeviation(long[] values) {
        validateNotEmpty(values);
        return Math.sqrt(varianceDouble(toDoubleArray(values)));
    }

    // ---- Private Helper Methods ----

    /**
     * Validates that the double array is not null or empty.
     */
    private static void validateNotEmpty(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Array must not be null or empty.");
        }
    }

    /**
     * Validates that the long array is not null or empty.
     */
    private static void validateNotEmpty(long[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Array must not be null or empty.");
        }
    }

    /**
     * Converts a long[] array into a double[] array.
     */
    private static double[] toDoubleArray(long[] values) {
        double[] doubles = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            doubles[i] = values[i];
        }
        return doubles;
    }

    /**
     * Computes the mean of a validated non-empty double[] array.
     */
    private static double meanDouble(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    /**
     * Computes the population variance of a validated non-empty double[] array
     * using Welford's algorithm for numerical stability and single-pass computation.
     */
    private static double varianceDouble(double[] values) {
        double mean = 0.0;
        double m2 = 0.0;
        int count = 0;

        for (double value : values) {
            count++;
            double delta = value - mean;
            mean += delta / count;
            m2 += delta * (value - mean);
        }

        // Population variance: divide by count
        return m2 / count;
    }
}
