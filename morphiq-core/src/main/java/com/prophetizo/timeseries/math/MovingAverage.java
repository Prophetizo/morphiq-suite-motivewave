package com.prophetizo.timeseries.math;

import java.util.ArrayList;
import java.util.List;

public class MovingAverage {

    /**
     * Computes the Simple Moving Average (SMA) for a given list of prices and a specified period.
     * @param prices the list of price values
     * @param period the lookback period for the SMA
     * @return a list of SMA values, where the first SMA is computed starting at the `period`-th element
     * @throws IllegalArgumentException if period <= 0
     */
    public static List<Float> simpleMovingAverage(List<Float> prices, int period) {
        // Validate input
        if (period <= 0) {
            throw new IllegalArgumentException("Period must be positive");
        }
        if (period > prices.size()) {
            // No averages possible
            return new ArrayList<>();
        }

        List<Float> sma = new ArrayList<>(prices.size() - period + 1);
        double sum = 0.0;

        // Initial sum of the first 'period' elements
        for (int i = 0; i < period; i++) {
            sum += prices.get(i);
        }

        // Add the first average
        sma.add((float)(sum / period));

        // Compute subsequent averages
        for (int i = period; i < prices.size(); i++) {
            sum += prices.get(i);          // Add the new element
            sum -= prices.get(i - period); // Remove the old element
            sma.add((float)(sum / period));
        }

        return sma;
    }

    /**
     * Computes the Exponential Moving Average (EMA) for a given list of prices and a specified period.
     *
     * @param prices the list of price values
     * @param period the lookback period for the EMA
     * @return a list of EMA values, where the first EMA is computed starting at the `period`-th element
     * @throws IllegalArgumentException if period <= 0
     */
    public static List<Float> exponentialMovingAverage(List<Float> prices, int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("Period must be positive.");
        }
        if (period > prices.size()) {
            // Not enough data to compute an EMA
            return new ArrayList<>();
        }

        // Calculate the smoothing factor alpha
        double alpha = 2.0 / (period + 1.0);

        // Initialize EMA with the SMA of the first `period` prices
        double initialSum = 0.0;
        for (int i = 0; i < period; i++) {
            initialSum += prices.get(i);
        }
        double ema = initialSum / period;

        // The first EMA value corresponds to the first completed period
        List<Float> emaValues = new ArrayList<>(prices.size() - period + 1);
        emaValues.add((float) ema);

        // Compute subsequent EMAs
        for (int i = period; i < prices.size(); i++) {
            double price = prices.get(i);
            ema = ema + alpha * (price - ema);
            emaValues.add((float) ema);
        }

        return emaValues;
    }
}
