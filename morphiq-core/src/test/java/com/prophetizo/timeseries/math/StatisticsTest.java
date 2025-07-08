package com.prophetizo.timeseries.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatisticsTest {

    @Test
    void testMeanDoubles() {
        double[] values = {1.0, 2.0, 3.0, 4.0};
        double mean = Statistics.mean(values);
        assertEquals(2.5, mean, 1e-12);
    }

    @Test
    void testMeanLongs() {
        long[] values = {10L, 20L, 30L, 40L};
        double mean = Statistics.mean(values);
        assertEquals(25.0, mean, 1e-12);
    }

    @Test
    void testVarianceDoubles() {
        // Using a simple set: variance of {1,2,3,4} (population)
        // mean = 2.5
        // differences: (1-2.5)^2=2.25, (2-2.5)^2=0.25, (3-2.5)^2=0.25, (4-2.5)^2=2.25
        // sum = 2.25+0.25+0.25+2.25 = 5.0
        // population variance = 5.0 / 4 = 1.25
        double[] values = {1.0, 2.0, 3.0, 4.0};
        double var = Statistics.variance(values);
        assertEquals(1.25, var, 1e-12);
    }

    @Test
    void testVarianceLongs() {
        // Same logic as above but using long values
        long[] values = {1L, 2L, 3L, 4L};
        double var = Statistics.variance(values);
        assertEquals(1.25, var, 1e-12);
    }

    @Test
    void testStandardDeviationDoubles() {
        // From above variance = 1.25
        // std dev = sqrt(1.25) = 1.1180339887...
        double[] values = {1.0, 2.0, 3.0, 4.0};
        double std = Statistics.standardDeviation(values);
        assertEquals(Math.sqrt(1.25), std, 1e-12);
    }

    @Test
    void testStandardDeviationLongs() {
        long[] values = {1L, 2L, 3L, 4L};
        double std = Statistics.standardDeviation(values);
        assertEquals(Math.sqrt(1.25), std, 1e-12);
    }

    @Test
    void testSingleElementArrays() {
        double[] singleDouble = {42.0};
        long[] singleLong = {42L};

        assertEquals(42.0, Statistics.mean(singleDouble), 1e-12);
        assertEquals(42.0, Statistics.mean(singleLong), 1e-12);

        // Variance of a single value is always 0
        assertEquals(0.0, Statistics.variance(singleDouble), 1e-12);
        assertEquals(0.0, Statistics.variance(singleLong), 1e-12);

        // Std dev of a single value is always 0
        assertEquals(0.0, Statistics.standardDeviation(singleDouble), 1e-12);
        assertEquals(0.0, Statistics.standardDeviation(singleLong), 1e-12);
    }

    @Test
    void testEmptyArrayThrowsException() {
        double[] emptyDouble = {};
        long[] emptyLong = {};

        assertThrows(IllegalArgumentException.class, () -> Statistics.mean(emptyDouble));
        assertThrows(IllegalArgumentException.class, () -> Statistics.mean(emptyLong));
        assertThrows(IllegalArgumentException.class, () -> Statistics.variance(emptyDouble));
        assertThrows(IllegalArgumentException.class, () -> Statistics.variance(emptyLong));
        assertThrows(IllegalArgumentException.class, () -> Statistics.standardDeviation(emptyDouble));
        assertThrows(IllegalArgumentException.class, () -> Statistics.standardDeviation(emptyLong));
    }

    @Test
    void testNullArrayThrowsException() {
        double[] nullDouble = null;
        long[] nullLong = null;

        assertThrows(IllegalArgumentException.class, () -> Statistics.mean(nullDouble));
        assertThrows(IllegalArgumentException.class, () -> Statistics.mean(nullLong));
        assertThrows(IllegalArgumentException.class, () -> Statistics.variance(nullDouble));
        assertThrows(IllegalArgumentException.class, () -> Statistics.variance(nullLong));
        assertThrows(IllegalArgumentException.class, () -> Statistics.standardDeviation(nullDouble));
        assertThrows(IllegalArgumentException.class, () -> Statistics.standardDeviation(nullLong));
    }

    @Test
    void testLargeValues() {
        // Test stability with large values
        double[] largeDoubles = {1e15, 1e15+1, 1e15+2};
        // mean ~ 1e15+1
        double mean = Statistics.mean(largeDoubles);
        assertTrue(mean > 1e15 && mean < 1e15+2, "Mean should be within expected large range");

        double var = Statistics.variance(largeDoubles);
        // Values are close together, variance should be small compared to the mean magnitude
        // We'll just check no overflow (no NaN, no Infinity)
        assertFalse(Double.isNaN(var) || Double.isInfinite(var), "Variance should be finite");
    }
}
