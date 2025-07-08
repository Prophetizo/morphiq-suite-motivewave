package com.prophetizo.timeseries.math;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MovingAverageTest {

    @Test
    void testSimpleCase() {
        List<Float> prices = Arrays.asList(1.0f, 2.0f, 3.0f, 4.0f, 5.0f);
        List<Float> result = MovingAverage.simpleMovingAverage(prices, 2);
        // Averages of pairs: (1+2)/2=1.5, (2+3)/2=2.5, (3+4)/2=3.5, (4+5)/2=4.5
        assertEquals(Arrays.asList(1.5f, 2.5f, 3.5f, 4.5f), result);
    }

    @Test
    void testPeriodEqualsListSize() {
        List<Float> prices = Arrays.asList(10.0f, 20.0f, 30.0f);
        List<Float> result = MovingAverage.simpleMovingAverage(prices, 3);
        // Only one average: (10+20+30)/3 = 20
        assertEquals(Collections.singletonList(20.0f), result);
    }

    @Test
    void testPeriodIsOne() {
        List<Float> prices = Arrays.asList(1.0f, 2.0f, 3.0f);
        // With period = 1, SMA should be identical to the original prices
        List<Float> result = MovingAverage.simpleMovingAverage(prices, 1);
        assertEquals(Arrays.asList(1.0f, 2.0f, 3.0f), result);
    }

    @Test
    void testPeriodLargerThanSize() {
        List<Float> prices = Arrays.asList(1.0f, 2.0f);
        // period = 3, which is > prices.size(), should result in an empty list
        List<Float> result = MovingAverage.simpleMovingAverage(prices, 3);
        assertTrue(result.isEmpty());
    }

    @Test
    void testInvalidPeriod() {
        List<Float> prices = Arrays.asList(1.0f, 2.0f, 3.0f);
        // period <= 0 should throw an exception
        assertThrows(IllegalArgumentException.class, () ->
                MovingAverage.simpleMovingAverage(prices, 0)
        );
        assertThrows(IllegalArgumentException.class, () ->
                MovingAverage.simpleMovingAverage(prices, -1)
        );
    }

    @Test
    void testFloatingPointPrecision() {
        List<Float> prices = Arrays.asList(0.1f, 0.2f, 0.3f);
        // With period = 2: Averages = (0.1+0.2)/2=0.15, (0.2+0.3)/2=0.25
        List<Float> result = MovingAverage.simpleMovingAverage(prices, 2);
        assertEquals(2, result.size());
        assertEquals(0.15f, result.get(0), 1e-6f);
        assertEquals(0.25f, result.get(1), 1e-6f);
    }

    @Test
    void testRandomDataAndCheckSize() {
        List<Float> prices = Arrays.asList(5.0f, 3.2f, 4.8f, 10.0f, 9.5f);
        // period = 3 means result size = 5 - 3 + 1 = 3
        List<Float> result = MovingAverage.simpleMovingAverage(prices, 3);
        assertEquals(3, result.size());

        // Compute manually:
        // First window: (5 + 3.2 + 4.8) / 3 = 13.0 / 3 = 4.333333
        // Second window: (3.2 + 4.8 + 10.0) / 3 = 18.0 / 3 = 6.0
        // Third window: (4.8 + 10.0 + 9.5) / 3 = 24.3 / 3 = 8.1

        assertEquals(4.333333f, result.get(0), 1e-6f);
        assertEquals(6.0f, result.get(1), 1e-6f);
        assertEquals(8.1f, result.get(2), 1e-6f);
    }

    @Test
    void testEMAWithSufficientData() {
        // Suppose prices: 10, 11, 12, 13, 14, 15
        // period = 3
        // Initial EMA: SMA of first 3: (10+11+12)/3 = 11.0
        // alpha = 2/(3+1) = 0.5
        // Next EMA computations:
        // EMA2 (for 13): EMA1 + 0.5*(13-11) = 11 + 1 = 12
        // EMA3 (for 14): EMA2 + 0.5*(14-12) = 12 + 1 = 13
        // EMA4 (for 15): EMA3 + 0.5*(15-13) = 13 + 1 = 14

        List<Float> prices = Arrays.asList(10f, 11f, 12f, 13f, 14f, 15f);
        List<Float> ema = MovingAverage.exponentialMovingAverage(prices, 3);

        // We expect values at indices after the period: [11.0, 12.0, 13.0, 14.0]
        assertEquals(Arrays.asList(11.0f, 12.0f, 13.0f, 14.0f), ema);
    }

    @Test
    void testEMAPeriodEqualsSize() {
        List<Float> prices = Arrays.asList(5.0f, 10.0f, 15.0f);
        // period = 3
        // EMA starts as SMA of (5+10+15)/3 = 10.0
        // There are no subsequent values to compute, so only one EMA value
        List<Float> ema = MovingAverage.exponentialMovingAverage(prices, 3);
        assertEquals(Collections.singletonList(10.0f), ema);
    }

    @Test
    void testEMAPeriodLargerThanSize() {
        List<Float> prices = Arrays.asList(10.0f, 20.0f);
        // period = 3 > size = 2
        // Not enough data, returns empty
        List<Float> ema = MovingAverage.exponentialMovingAverage(prices, 3);
        assertTrue(ema.isEmpty());
    }

    @Test
    void testInvalidPeriodForEMA() {
        List<Float> prices = Arrays.asList(10.0f, 20.0f, 30.0f);
        assertThrows(IllegalArgumentException.class, () ->
                MovingAverage.exponentialMovingAverage(prices, 0)
        );
        assertThrows(IllegalArgumentException.class, () ->
                MovingAverage.exponentialMovingAverage(prices, -1)
        );
    }

    @Test
    void testEMAPeriodOne() {
        // With period = 1, alpha = 2/(1+1)=1, so EMA = the price itself each step after initialization
        List<Float> prices = Arrays.asList(5.0f, 6.0f, 7.0f);
        // Initial EMA = SMA of first 1 element = 5
        // Next: EMA = previousEMA + 1*(6-5) = 6
        // Next: EMA = previousEMA + 1*(7-6) = 7
        // Essentially returns the original list starting from the first element
        List<Float> ema = MovingAverage.exponentialMovingAverage(prices, 1);
        assertEquals(Arrays.asList(5.0f, 6.0f, 7.0f), ema);
    }
}
