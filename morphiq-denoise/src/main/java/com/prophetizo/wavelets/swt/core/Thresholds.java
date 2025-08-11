package com.prophetizo.wavelets.swt.core;

import java.util.Arrays;

/**
 * Threshold calculation methods for wavelet denoising.
 * Implements Universal, BayesShrink, and SURE thresholding.
 */
public class Thresholds {
    // Simple console logging for standalone compilation
    private static void log(String message, Object... args) {
        System.out.printf("[Thresholds] " + message + "%n", args);
    }
    
    public enum ThresholdMethod {
        UNIVERSAL("Universal"),
        BAYES("BayesShrink"),
        SURE("SURE");
        
        private final String displayName;
        
        ThresholdMethod(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public static ThresholdMethod fromString(String str) {
            for (ThresholdMethod method : values()) {
                if (method.displayName.equalsIgnoreCase(str) || method.name().equalsIgnoreCase(str)) {
                    return method;
                }
            }
            return UNIVERSAL; // Default fallback
        }
    }
    
    public enum ShrinkageType {
        SOFT("Soft"),
        HARD("Hard");
        
        private final String displayName;
        
        ShrinkageType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public static ShrinkageType fromString(String str) {
            for (ShrinkageType type : values()) {
                if (type.displayName.equalsIgnoreCase(str) || type.name().equalsIgnoreCase(str)) {
                    return type;
                }
            }
            return SOFT; // Default fallback
        }
    }
    
    /**
     * Calculate threshold for a detail level using specified method
     */
    public static double calculateThreshold(double[] detailCoeffs, ThresholdMethod method, int level) {
        if (detailCoeffs == null || detailCoeffs.length == 0) {
            return 0.0;
        }
        
        switch (method) {
            case UNIVERSAL:
                return calculateUniversalThreshold(detailCoeffs);
            case BAYES:
                return calculateBayesThreshold(detailCoeffs, level);
            case SURE:
                return calculateSureThreshold(detailCoeffs);
            default:
                log("Unknown threshold method %s, using Universal", method);
                return calculateUniversalThreshold(detailCoeffs);
        }
    }
    
    /**
     * Universal threshold: σ * sqrt(2 * log(N))
     * where σ is the noise standard deviation estimated from the finest detail level
     */
    public static double calculateUniversalThreshold(double[] detailCoeffs) {
        // Handle edge cases
        if (detailCoeffs == null || detailCoeffs.length == 0) {
            return 0.0;
        }
        
        double sigma = estimateNoiseSigma(detailCoeffs);
        int n = detailCoeffs.length;
        double threshold = sigma * Math.sqrt(2.0 * Math.log(n));
        
        // log("Universal threshold: σ={:.4f}, N={}, threshold={:.4f}", sigma, n, threshold);
        return threshold;
    }
    
    /**
     * BayesShrink threshold - adapts to local signal characteristics
     */
    public static double calculateBayesThreshold(double[] detailCoeffs, int level) {
        double sigma = estimateNoiseSigma(detailCoeffs);
        double sigmaY = calculateStandardDeviation(detailCoeffs);
        
        // Estimate signal variance
        double sigmaX2 = Math.max(0, sigmaY * sigmaY - sigma * sigma);
        
        if (sigmaX2 <= 0) {
            // Pure noise - use high threshold
            double threshold = sigma * Math.sqrt(2.0 * Math.log(detailCoeffs.length));
            // log("BayesShrink (pure noise) level {}: threshold={:.4f}", level, threshold);
            return threshold;
        }
        
        double threshold = (sigma * sigma) / Math.sqrt(sigmaX2);
        
        // Scale by level (finer details need more aggressive thresholding)
        double levelFactor = 1.0 + 0.1 * (level - 1);
        threshold *= levelFactor;
        
        // log("BayesShrink level {}: σ={:.4f}, σY={:.4f}, σX²={:.4f}, threshold={:.4f}", 
        //            level, sigma, sigmaY, sigmaX2, threshold);
        return threshold;
    }
    
    /**
     * SURE threshold (Stein's Unbiased Risk Estimate)
     * Minimizes an unbiased estimate of the risk
     */
    public static double calculateSureThreshold(double[] detailCoeffs) {
        double[] absCoeffs = Arrays.stream(detailCoeffs)
                .map(Math::abs)
                .sorted()
                .toArray();
        
        int n = absCoeffs.length;
        if (n < 2) {
            return calculateUniversalThreshold(detailCoeffs);
        }
        
        double sigma = estimateNoiseSigma(detailCoeffs);
        double minRisk = Double.MAX_VALUE;
        double bestThreshold = sigma * Math.sqrt(2.0 * Math.log(n));
        
        // Search for threshold that minimizes SURE risk
        for (int k = 1; k < n; k++) {
            double threshold = absCoeffs[k];
            double risk = calculateSureRisk(absCoeffs, threshold, sigma);
            
            if (risk < minRisk) {
                minRisk = risk;
                bestThreshold = threshold;
            }
        }
        
        // log("SURE threshold: best={:.4f}, risk={:.4f}", bestThreshold, minRisk);
        return bestThreshold;
    }
    
    /**
     * Calculate SURE (Stein's Unbiased Risk Estimate) risk for a given threshold.
     * 
     * SURE provides an unbiased estimate of the mean squared error (MSE) between
     * the thresholded coefficients and the true underlying signal coefficients.
     * 
     * The SURE risk formula for soft thresholding is:
     * SURE(λ) = n - 2 * #{|X_i| > λ} + Σ min(|X_i|², λ²)
     * 
     * Where:
     * - n: total number of coefficients
     * - λ: threshold value being evaluated
     * - #{|X_i| > λ}: count of coefficients with absolute value greater than threshold
     * - Σ min(|X_i|², λ²): sum of squared values (coefficient squared if below threshold,
     *                       threshold squared if above)
     * 
     * The risk is normalized by σ² (noise variance) to make it scale-invariant.
     * Lower risk values indicate better threshold choices that minimize reconstruction error.
     * 
     * @param sortedAbsCoeffs Array of wavelet coefficients sorted by absolute value
     * @param threshold The threshold value to evaluate
     * @param sigma Estimated noise standard deviation
     * @return SURE risk estimate (lower is better)
     */
    private static double calculateSureRisk(double[] sortedAbsCoeffs, double threshold, double sigma) {
        int n = sortedAbsCoeffs.length;
        int numKept = 0;
        double sumSquares = 0;
        
        for (double coeff : sortedAbsCoeffs) {
            if (coeff > threshold) {
                numKept++;
                sumSquares += threshold * threshold;
            } else {
                sumSquares += coeff * coeff;
            }
        }
        
        // SURE risk estimate: (n - 2*numKept + sumSquares) / σ²
        // This estimates the mean squared error between thresholded and true coefficients
        double risk = (n - 2 * numKept + sumSquares) / (sigma * sigma);
        return risk;
    }
    
    /**
     * Estimate noise standard deviation using Median Absolute Deviation (MAD)
     * σ ≈ MAD / 0.6745
     */
    public static double estimateNoiseSigma(double[] detailCoeffs) {
        if (detailCoeffs.length == 0) {
            return 1.0; // Default fallback
        }
        
        double[] absCoeffs = Arrays.stream(detailCoeffs)
                .map(Math::abs)
                .toArray();
        Arrays.sort(absCoeffs);
        
        double median = calculateMedian(absCoeffs);
        double sigma = median / 0.6745; // MAD to standard deviation conversion
        
        // Ensure reasonable bounds
        sigma = Math.max(sigma, 1e-10);
        sigma = Math.min(sigma, calculateStandardDeviation(detailCoeffs) * 2.0);
        
        return sigma;
    }
    
    /**
     * Calculate median of sorted array
     */
    private static double calculateMedian(double[] sortedArray) {
        int n = sortedArray.length;
        if (n == 0) return 0.0;
        if (n == 1) return sortedArray[0];
        
        if (n % 2 == 0) {
            return (sortedArray[n/2 - 1] + sortedArray[n/2]) / 2.0;
        } else {
            return sortedArray[n/2];
        }
    }
    
    /**
     * Calculate standard deviation
     */
    private static double calculateStandardDeviation(double[] data) {
        if (data.length == 0) return 0.0;
        if (data.length == 1) return 0.0;
        
        double mean = Arrays.stream(data).average().orElse(0.0);
        double variance = Arrays.stream(data)
                .map(x -> Math.pow(x - mean, 2))
                .average().orElse(0.0);
        
        return Math.sqrt(variance);
    }
    
    /**
     * Apply thresholding to coefficients
     */
    public static double[] applyThreshold(double[] coeffs, double threshold, ShrinkageType type) {
        double[] result = new double[coeffs.length];
        
        for (int i = 0; i < coeffs.length; i++) {
            result[i] = applySingleThreshold(coeffs[i], threshold, type);
        }
        
        return result;
    }
    
    /**
     * Apply threshold to a single coefficient
     */
    public static double applySingleThreshold(double coeff, double threshold, ShrinkageType type) {
        switch (type) {
            case SOFT:
                if (Math.abs(coeff) <= threshold) {
                    return 0.0;
                } else {
                    return Math.signum(coeff) * (Math.abs(coeff) - threshold);
                }
            case HARD:
                if (Math.abs(coeff) <= threshold) {
                    return 0.0;
                } else {
                    return coeff;
                }
            default:
                return coeff;
        }
    }
    
    /**
     * Auto-select threshold method based on data characteristics
     */
    public static ThresholdMethod autoSelectMethod(double[] detailCoeffs, int level) {
        if (detailCoeffs.length < 32) {
            return ThresholdMethod.UNIVERSAL; // Simple method for small data
        }
        
        double sigma = estimateNoiseSigma(detailCoeffs);
        double sigmaY = calculateStandardDeviation(detailCoeffs);
        double snr = sigmaY / Math.max(sigma, 1e-10);
        
        if (snr < 1.5) {
            return ThresholdMethod.UNIVERSAL; // High noise - use robust method
        } else if (snr > 3.0) {
            return ThresholdMethod.SURE; // High signal - use adaptive method
        } else {
            return ThresholdMethod.BAYES; // Balanced - use intermediate method
        }
    }
}