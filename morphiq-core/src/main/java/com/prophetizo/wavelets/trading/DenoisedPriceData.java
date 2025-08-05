package com.prophetizo.wavelets.trading;

import com.prophetizo.LoggerConfig;
import org.slf4j.Logger;

/**
 * Data object representing denoised price data with quality metrics.
 * Replaces simple arrays with rich information about the denoising process.
 */
public class DenoisedPriceData {
    private static final Logger logger = LoggerConfig.getLogger(DenoisedPriceData.class);
    
    private final double[] cleanedPrices;
    private final double[] originalPrices;
    private final double qualityScore;
    private final double noiseReductionRatio;
    private final DenoiseStrategy strategyUsed;
    private final String qualityAssessment;
    private final long processingTimestamp;
    
    /**
     * Constructor with full quality information.
     */
    public DenoisedPriceData(double[] cleanedPrices, double[] originalPrices, 
                            double qualityScore, double noiseReductionRatio,
                            DenoiseStrategy strategyUsed) {
        this.cleanedPrices = cleanedPrices.clone();
        this.originalPrices = originalPrices.clone();
        this.qualityScore = Math.max(0.0, Math.min(1.0, qualityScore));
        this.noiseReductionRatio = Math.max(0.0, noiseReductionRatio);
        this.strategyUsed = strategyUsed;
        this.qualityAssessment = assessQuality(qualityScore, noiseReductionRatio);
        this.processingTimestamp = System.currentTimeMillis();
        
        logger.debug("Created DenoisedPriceData - Strategy: {}, Quality: {:.2f}, Noise Reduction: {:.2f}%", 
                    strategyUsed, qualityScore, noiseReductionRatio * 100);
    }
    
    /**
     * Simplified constructor with automatic quality calculation.
     */
    public DenoisedPriceData(double[] cleanedPrices, double[] originalPrices, DenoiseStrategy strategy) {
        this(cleanedPrices, originalPrices, 
             calculateQualityScore(cleanedPrices, originalPrices),
             calculateNoiseReduction(cleanedPrices, originalPrices),
             strategy);
    }
    
    // Getters
    public double[] getCleanedPrices() { return cleanedPrices.clone(); }
    public double[] getOriginalPrices() { return originalPrices.clone(); }
    public double getQualityScore() { return qualityScore; }
    public double getNoiseReductionRatio() { return noiseReductionRatio; }
    public DenoiseStrategy getStrategyUsed() { return strategyUsed; }
    public String getQualityAssessment() { return qualityAssessment; }
    public long getProcessingTimestamp() { return processingTimestamp; }
    
    /**
     * Get the latest (most recent) cleaned price.
     */
    public double getLatestCleanPrice() {
        if (cleanedPrices.length == 0) {
            logger.warn("No cleaned prices available, returning 0");
            return 0.0;
        }
        return cleanedPrices[cleanedPrices.length - 1];
    }
    
    /**
     * Get the latest original price for comparison.
     */
    public double getLatestOriginalPrice() {
        if (originalPrices.length == 0) {
            logger.warn("No original prices available, returning 0");
            return 0.0;
        }
        return originalPrices[originalPrices.length - 1];
    }
    
    /**
     * Check if the denoising quality is considered high.
     */
    public boolean isHighQuality() {
        return qualityScore >= 0.7 && noiseReductionRatio >= 0.1;
    }
    
    /**
     * Check if significant noise reduction was achieved.
     */
    public boolean isSignificantNoiseReduction() {
        return noiseReductionRatio >= 0.2; // 20% or more noise reduction
    }
    
    /**
     * Get the difference between cleaned and original at the latest point.
     */
    public double getLatestDenoisingEffect() {
        return getLatestCleanPrice() - getLatestOriginalPrice();
    }
    
    /**
     * Get smoothness metric of cleaned prices (lower = smoother).
     */
    public double getSmoothnessMetric() {
        if (cleanedPrices.length < 3) {
            return 0.0;
        }
        
        double totalVariation = 0.0;
        for (int i = 1; i < cleanedPrices.length - 1; i++) {
            double secondDerivative = cleanedPrices[i+1] - 2*cleanedPrices[i] + cleanedPrices[i-1];
            totalVariation += Math.abs(secondDerivative);
        }
        
        return totalVariation / (cleanedPrices.length - 2);
    }
    
    /**
     * Get trend preservation score (how well the overall trend is maintained).
     */
    public double getTrendPreservationScore() {
        if (cleanedPrices.length < 2 || originalPrices.length < 2) {
            return 1.0; // Perfect preservation if no trend to measure
        }
        
        // Calculate overall trend direction for both series
        double originalTrend = originalPrices[originalPrices.length - 1] - originalPrices[0];
        double cleanedTrend = cleanedPrices[cleanedPrices.length - 1] - cleanedPrices[0];
        
        if (Math.abs(originalTrend) < 1e-10) {
            return 1.0; // No trend to preserve
        }
        
        // Score based on how well the trend direction and magnitude are preserved
        double directionScore = Math.signum(originalTrend) == Math.signum(cleanedTrend) ? 1.0 : 0.0;
        double magnitudeScore = Math.min(1.0, Math.abs(cleanedTrend) / Math.abs(originalTrend));
        
        return (directionScore * 0.7 + magnitudeScore * 0.3);
    }
    
    /**
     * Calculate quality score based on various metrics.
     */
    private static double calculateQualityScore(double[] cleaned, double[] original) {
        if (cleaned.length != original.length || cleaned.length == 0) {
            return 0.0;
        }
        
        try {
            // Calculate signal-to-noise improvement
            double originalVariance = calculateVariance(original);
            double cleanedVariance = calculateVariance(cleaned);
            
            if (originalVariance <= 0) {
                return 0.5; // Neutral score
            }
            
            // Quality improves as variance is reduced (noise removal)
            // but penalize excessive smoothing
            double varianceReduction = 1.0 - (cleanedVariance / originalVariance);
            
            // Penalize excessive smoothing (> 90% variance reduction might be over-smoothing)
            if (varianceReduction > 0.9) {
                varianceReduction = 0.9 - (varianceReduction - 0.9) * 2;
            }
            
            return Math.max(0.1, Math.min(0.95, varianceReduction));
            
        } catch (Exception e) {
            logger.warn("Error calculating quality score", e);
            return 0.5;
        }
    }
    
    /**
     * Calculate noise reduction ratio.
     */
    private static double calculateNoiseReduction(double[] cleaned, double[] original) {
        if (cleaned.length != original.length || cleaned.length < 2) {
            return 0.0;
        }
        
        try {
            // Estimate noise as high-frequency component (differences between consecutive points)
            double originalNoise = 0.0;
            double cleanedNoise = 0.0;
            
            for (int i = 1; i < original.length; i++) {
                originalNoise += Math.abs(original[i] - original[i-1]);
                cleanedNoise += Math.abs(cleaned[i] - cleaned[i-1]);
            }
            
            if (originalNoise <= 0) {
                return 0.0;
            }
            
            return (originalNoise - cleanedNoise) / originalNoise;
            
        } catch (Exception e) {
            logger.warn("Error calculating noise reduction", e);
            return 0.0;
        }
    }
    
    /**
     * Calculate variance of a data series.
     */
    private static double calculateVariance(double[] data) {
        if (data.length <= 1) {
            return 0.0;
        }
        
        double mean = 0.0;
        for (double value : data) {
            mean += value;
        }
        mean /= data.length;
        
        double variance = 0.0;
        for (double value : data) {
            double diff = value - mean;
            variance += diff * diff;
        }
        
        return variance / (data.length - 1);
    }
    
    /**
     * Assess quality and provide textual description.
     */
    private static String assessQuality(double qualityScore, double noiseReduction) {
        if (qualityScore >= 0.8 && noiseReduction >= 0.3) {
            return "Excellent - High quality denoising with significant noise reduction";
        } else if (qualityScore >= 0.6 && noiseReduction >= 0.2) {
            return "Good - Effective denoising with moderate noise reduction";
        } else if (qualityScore >= 0.4 && noiseReduction >= 0.1) {
            return "Fair - Some noise reduction achieved";
        } else if (qualityScore >= 0.2) {
            return "Poor - Limited denoising effectiveness";
        } else {
            return "Very Poor - Minimal or no noise reduction";
        }
    }
    
    @Override
    public String toString() {
        return String.format("DenoisedPriceData{strategy=%s, quality=%.2f, noiseReduction=%.1f%%, assessment='%s'}", 
                           strategyUsed, qualityScore, noiseReductionRatio * 100, qualityAssessment);
    }
}