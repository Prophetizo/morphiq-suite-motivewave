package com.prophetizo.wavelets;

import com.prophetizo.LoggerConfig;
import org.slf4j.Logger;
import java.util.Arrays;

/**
 * WaveletDenoiser provides advanced denoising capabilities for financial time series.
 * It filters out high-frequency noise while preserving underlying trends by manipulating
 * wavelet coefficients in the transform domain.
 * 
 * Key features:
 * - Configurable noise level filtering (D1, D2, etc.)
 * - Market volatility-based adaptive thresholding
 * - Soft and hard thresholding options
 * - Reconstruction via inverse MODWT
 */
public class WaveletDenoiser {
    private static final Logger logger = LoggerConfig.getLogger(WaveletDenoiser.class);
    
    // Note: This class is now deprecated. Use VectorWave TradingDenoiser instead.
    
    // Denoising parameters
    private int[] noiseLevels = {0, 1}; // D1, D2 by default (zero-indexed)
    private ThresholdType thresholdType = ThresholdType.HARD;
    private double thresholdMultiplier = 1.0;
    private boolean adaptiveThresholding = false;
    
    public enum ThresholdType {
        HARD,    // Set coefficients below threshold to zero
        SOFT     // Shrink coefficients towards zero
    }
    
    @Deprecated
    public WaveletDenoiser() {
        // Legacy constructor - use VectorWave TradingDenoiser instead
    }
    
    
    
    /**
     * Apply denoising filters to wavelet coefficients.
     * 
     * @param coefficients Original coefficients [level][time]
     * @param originalData Original price data for adaptive thresholding
     * @return Denoised coefficients
     */
    private double[][] applyDenoising(double[][] coefficients, double[] originalData) {
        double[][] denoised = new double[coefficients.length][];
        
        // Copy all coefficients first
        for (int level = 0; level < coefficients.length; level++) {
            denoised[level] = coefficients[level].clone();
        }
        
        // Apply denoising to specified noise levels
        for (int noiseLevel : noiseLevels) {
            if (noiseLevel < coefficients.length) {
                denoised[noiseLevel] = applyThresholding(
                    coefficients[noiseLevel], 
                    calculateThreshold(coefficients[noiseLevel], originalData),
                    originalData
                );
                
                logger.debug("Applied {} thresholding to level D{}", 
                    thresholdType, noiseLevel + 1);
            }
        }
        
        return denoised;
    }
    
    /**
     * Zero out specified noise levels completely.
     * This is a more aggressive approach than thresholding.
     * 
     * @param coefficients Original coefficients [level][time]
     * @return Coefficients with specified levels zeroed out
     */
    private double[][] zeroOutNoiseLevels(double[][] coefficients) {
        double[][] denoised = new double[coefficients.length][];
        
        // Copy all coefficients first
        for (int level = 0; level < coefficients.length; level++) {
            denoised[level] = coefficients[level].clone();
        }
        
        // Zero out specified noise levels
        for (int noiseLevel : noiseLevels) {
            if (noiseLevel >= coefficients.length) {
                logger.warn("Noise level D{} is out of range and will be ignored. Coefficients length: {}", 
                    noiseLevel + 1, coefficients.length);
                continue;
            }
            
            // Fill with zeros
            Arrays.fill(denoised[noiseLevel], 0.0);
            logger.debug("Zeroed out level D{} completely", noiseLevel + 1);
        }
        
        return denoised;
    }
    
    /**
     * Calculate appropriate threshold for a given level of coefficients.
     * 
     * @param coefficients Coefficients for this level
     * @param originalData Original price data
     * @return Threshold value
     */
    private double calculateThreshold(double[] coefficients, double[] originalData) {
        if (adaptiveThresholding) {
            return calculateAdaptiveThreshold(coefficients, originalData);
        } else {
            return calculateFixedThreshold(coefficients);
        }
    }
    
    /**
     * Calculate fixed threshold based on coefficient statistics.
     */
    private double calculateFixedThreshold(double[] coefficients) {
        // Use median absolute deviation (MAD) for robust threshold estimation
        double[] absCoeffs = new double[coefficients.length];
        for (int i = 0; i < coefficients.length; i++) {
            absCoeffs[i] = Math.abs(coefficients[i]);
        }
        
        double median = calculateMedian(absCoeffs);
        double mad = calculateMAD(coefficients, median);
        
        // Universal threshold: sigma * sqrt(2 * log(n))
        // Use MAD as robust estimate of sigma
        double sigma = mad / 0.6745; // Convert MAD to standard deviation estimate
        double universalThreshold = sigma * Math.sqrt(2 * Math.log(coefficients.length));
        
        return universalThreshold * thresholdMultiplier;
    }
    
    /**
     * Calculate adaptive threshold based on market volatility.
     */
    private double calculateAdaptiveThreshold(double[] coefficients, double[] originalData) {
        // Base threshold
        double baseThreshold = calculateFixedThreshold(coefficients);
        
        // Volatility adjustment
        double volatility = calculateRecentVolatility(originalData);
        double avgVolatility = calculateAverageVolatility(originalData);
        
        double volatilityRatio = volatility / avgVolatility;
        
        // Increase threshold during high volatility (preserve more signal)
        // Decrease threshold during low volatility (remove more noise)
        double adaptiveFactor = Math.max(0.5, Math.min(2.0, volatilityRatio));
        
        logger.debug("Adaptive threshold factor: {} (vol ratio: {})", 
            String.format("%.3f", adaptiveFactor), String.format("%.3f", volatilityRatio));
        
        return baseThreshold * adaptiveFactor;
    }
    
    
    /**
     * Apply thresholding to coefficients based on selected method.
     * Uses parallel processing for large coefficient arrays with JWave 1.0.7-SNAPSHOT.
     */
    private double[] applyThresholding(double[] coefficients, double threshold, double[] originalData) {
        double[] thresholded = new double[coefficients.length];
        
        // Use parallel processing for large arrays
        if (coefficients.length >= 512) {
            // Parallel thresholding using Java 8+ parallel streams
            java.util.stream.IntStream.range(0, coefficients.length)
                .parallel()
                .forEach(i -> {
                    switch (thresholdType) {
                        case HARD:
                            thresholded[i] = hardThreshold(coefficients[i], threshold);
                            break;
                        case SOFT:
                            thresholded[i] = softThreshold(coefficients[i], threshold);
                            break;
                    }
                });
            logger.debug("Applied parallel {} thresholding to {} coefficients", 
                thresholdType, coefficients.length);
        } else {
            // Sequential processing for smaller arrays
            for (int i = 0; i < coefficients.length; i++) {
                switch (thresholdType) {
                    case HARD:
                        thresholded[i] = hardThreshold(coefficients[i], threshold);
                        break;
                    case SOFT:
                        thresholded[i] = softThreshold(coefficients[i], threshold);
                        break;
                }
            }
        }
        
        return thresholded;
    }
    
    /**
     * Hard thresholding: set to zero if below threshold.
     */
    private double hardThreshold(double value, double threshold) {
        return Math.abs(value) > threshold ? value : 0.0;
    }
    
    /**
     * Soft thresholding: shrink towards zero.
     */
    private double softThreshold(double value, double threshold) {
        if (Math.abs(value) <= threshold) {
            return 0.0;
        } else {
            return Math.signum(value) * (Math.abs(value) - threshold);
        }
    }
    
    
    
    // Utility methods for statistical calculations
    
    private double calculateMedian(double[] values) {
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int n = sorted.length;
        
        if (n % 2 == 0) {
            return (sorted[n/2 - 1] + sorted[n/2]) / 2.0;
        } else {
            return sorted[n/2];
        }
    }
    
    private double calculateMAD(double[] values, double median) {
        double[] deviations = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            deviations[i] = Math.abs(values[i] - median);
        }
        return calculateMedian(deviations);
    }
    
    private double calculateRecentVolatility(double[] prices) {
        if (prices.length < 20) return 1.0;
        
        // Calculate volatility over last 20 periods
        int start = Math.max(0, prices.length - 20);
        double sum = 0;
        int count = 0;
        
        for (int i = start + 1; i < prices.length; i++) {
            double change = Math.log(prices[i] / prices[i-1]);
            sum += change * change;
            count++;
        }
        
        return Math.sqrt(sum / count);
    }
    
    private double calculateAverageVolatility(double[] prices) {
        if (prices.length < 50) return 1.0;
        
        double sum = 0;
        int count = 0;
        
        for (int i = 1; i < prices.length; i++) {
            double change = Math.log(prices[i] / prices[i-1]);
            sum += change * change;
            count++;
        }
        
        return Math.sqrt(sum / count);
    }
    
    // Configuration methods
    
    public WaveletDenoiser setNoiseLevels(int... levels) {
        this.noiseLevels = levels;
        return this;
    }
    
    public WaveletDenoiser setThresholdType(ThresholdType type) {
        this.thresholdType = type;
        return this;
    }
    
    public WaveletDenoiser setThresholdMultiplier(double multiplier) {
        this.thresholdMultiplier = multiplier;
        return this;
    }
    
    public WaveletDenoiser setAdaptiveThresholding(boolean adaptive) {
        this.adaptiveThresholding = adaptive;
        return this;
    }
    
    // Getters for current configuration
    
    public int[] getNoiseLevels() {
        return noiseLevels.clone();
    }
    
    public ThresholdType getThresholdType() {
        return thresholdType;
    }
    
    public double getThresholdMultiplier() {
        return thresholdMultiplier;
    }
    
    public boolean isAdaptiveThresholding() {
        return adaptiveThresholding;
    }
}