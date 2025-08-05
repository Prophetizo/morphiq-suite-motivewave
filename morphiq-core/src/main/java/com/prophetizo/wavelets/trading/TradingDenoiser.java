package com.prophetizo.wavelets.trading;

import ai.prophetizo.wavelet.api.BoundaryMode;
import ai.prophetizo.wavelet.api.Wavelet;
import ai.prophetizo.wavelet.api.WaveletRegistry;
import ai.prophetizo.wavelet.denoising.WaveletDenoiser;
import ai.prophetizo.wavelet.modwt.MODWTTransformFactory;
import ai.prophetizo.wavelet.modwt.MultiLevelMODWTResult;
import ai.prophetizo.wavelet.modwt.MultiLevelMODWTTransform;
import com.prophetizo.LoggerConfig;
import org.slf4j.Logger;

/**
 * Service class for trading-focused wavelet denoising using VectorWave's financial capabilities.
 * Replaces the old WaveletDenoiser with rich domain objects and financial-optimized processing.
 */
public class TradingDenoiser {
    private static final Logger logger = LoggerConfig.getLogger(TradingDenoiser.class);
    
    private final WaveletDenoiser vectorDenoiser;
    private MultiLevelMODWTTransform modwtTransform; // Made non-final for lazy initialization
    private final String waveletType;
    
    // Default configuration
    private static final int DEFAULT_LEVELS = 5;
    private static final DenoiseStrategy DEFAULT_STRATEGY = DenoiseStrategy.BALANCED;
    
    /**
     * Constructor using VectorWave's financial-optimized denoiser.
     */
    public TradingDenoiser(WaveletDenoiser vectorDenoiser, String waveletType) {
        this.vectorDenoiser = vectorDenoiser;
        this.waveletType = waveletType;
        
        // Create MODWT transform for approximation extraction
        // Note: We don't create the MODWT transform here anymore since wavelet creation is failing
        // The vectorDenoiser already has wavelet capabilities, so we'll rely on that
        
        logger.info("Initialized TradingDenoiser with VectorWave financial optimization, wavelet type: {}", waveletType);
    }
    
    /**
     * Main denoising method with strategy-based approach.
     * This replaces the old denoise() method with richer functionality.
     */
    public DenoisedPriceData denoise(double[] prices, DenoiseStrategy strategy) {
        if (prices == null || prices.length == 0) {
            logger.warn("Empty or null price data provided for denoising");
            return new DenoisedPriceData(new double[0], new double[0], strategy);
        }
        
        try {
            logger.debug("Denoising {} price points using {} strategy", prices.length, strategy);
            
            // Use VectorWave's financial denoiser with strategy parameters
            double[] cleanedPrices = vectorDenoiser.denoiseMultiLevel(
                prices, 
                DEFAULT_LEVELS,
                strategy.getThresholdMethod(),
                strategy.getThresholdType()
            );
            
            // Create rich result object with quality metrics
            DenoisedPriceData result = new DenoisedPriceData(cleanedPrices, prices, strategy);
            
            logger.debug("Denoising completed - Quality: {:.2f}, Noise Reduction: {:.1f}%", 
                        result.getQualityScore(), result.getNoiseReductionRatio() * 100);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error during denoising process", e);
            // Return original data on error
            return new DenoisedPriceData(prices.clone(), prices, strategy);
        }
    }
    
    /**
     * Denoise with custom parameters.
     */
    public DenoisedPriceData denoise(double[] prices, int levels, DenoiseStrategy strategy) {
        if (prices == null || prices.length == 0) {
            return new DenoisedPriceData(new double[0], new double[0], strategy);
        }
        
        try {
            double[] cleanedPrices = vectorDenoiser.denoiseMultiLevel(
                prices, 
                levels,
                strategy.getThresholdMethod(),
                strategy.getThresholdType()
            );
            
            return new DenoisedPriceData(cleanedPrices, prices, strategy);
            
        } catch (Exception e) {
            logger.error("Error during custom denoising", e);
            return new DenoisedPriceData(prices.clone(), prices, strategy);
        }
    }
    
    /**
     * Get or create MODWT transform lazily. If creation fails, methods will use fallback approach.
     */
    private MultiLevelMODWTTransform getModwtTransform() {
        if (modwtTransform == null) {
            try {
                // Use VectorWave's new discovery API
                java.util.Set<String> availableWavelets = WaveletRegistry.getAvailableWavelets();
                Wavelet wavelet = null;
                String selectedWavelet = null;
                
                // Try the requested wavelet type first
                if (WaveletRegistry.isWaveletAvailable(waveletType)) {
                    wavelet = WaveletRegistry.getWavelet(waveletType);
                    selectedWavelet = waveletType;
                } else {
                    // Try mapped name
                    String mappedType = WaveletConfigHelper.mapWaveletType(waveletType);
                    if (WaveletRegistry.isWaveletAvailable(mappedType)) {
                        wavelet = WaveletRegistry.getWavelet(mappedType);
                        selectedWavelet = mappedType;
                    } else {
                        // Use first available wavelet as fallback
                        String[] preferredOrder = {"db4", "haar", "db2", "db6", "sym4"};
                        for (String preferred : preferredOrder) {
                            if (availableWavelets.contains(preferred)) {
                                wavelet = WaveletRegistry.getWavelet(preferred);
                                selectedWavelet = preferred;
                                logger.debug("Using fallback wavelet '{}' for MODWT (requested: '{}')", 
                                           preferred, waveletType);
                                break;
                            }
                        }
                        
                        // Last resort - use any available
                        if (wavelet == null && !availableWavelets.isEmpty()) {
                            selectedWavelet = availableWavelets.iterator().next();
                            wavelet = WaveletRegistry.getWavelet(selectedWavelet);
                            logger.debug("Using first available wavelet '{}' for MODWT", selectedWavelet);
                        }
                    }
                }
                
                if (wavelet != null) {
                    modwtTransform = MODWTTransformFactory.createMultiLevel(wavelet, BoundaryMode.SYMMETRIC);
                    logger.debug("Successfully created MODWT transform with wavelet: {}", selectedWavelet);
                }
                
            } catch (Exception e) {
                logger.warn("Failed to use VectorWave discovery API for MODWT, trying fallback approach", e);
                
                // Fallback to old method
                String[] namesToTry = {waveletType, "db4", "haar"};
                for (String name : namesToTry) {
                    try {
                        Wavelet wavelet = WaveletRegistry.getWavelet(name);
                        if (wavelet != null) {
                            modwtTransform = MODWTTransformFactory.createMultiLevel(wavelet, BoundaryMode.SYMMETRIC);
                            logger.debug("Successfully created MODWT transform with fallback wavelet: {}", name);
                            break;
                        }
                    } catch (Exception ex) {
                        logger.debug("Failed to create MODWT with wavelet name '{}': {}", name, ex.getMessage());
                    }
                }
            }
            
            if (modwtTransform == null) {
                logger.warn("Could not create MODWT transform for any wavelet name");
            }
        }
        return modwtTransform;
    }
    
    /**
     * Aggressive denoising by completely zeroing specified detail levels.
     * This replaces the old denoiseByZeroing method.
     */
    public DenoisedPriceData denoiseByZeroing(double[] prices, int levels, int[] levelsToZero) {
        if (prices == null || prices.length == 0) {
            return new DenoisedPriceData(new double[0], new double[0], DenoiseStrategy.AGGRESSIVE);
        }
        
        try {
            logger.debug("Zeroing denoising - {} levels, zeroing levels: {}", levels, java.util.Arrays.toString(levelsToZero));
            
            MultiLevelMODWTTransform transform = getModwtTransform();
            if (transform == null) {
                logger.warn("MODWT transform not available, using standard denoising instead");
                return denoise(prices, DenoiseStrategy.AGGRESSIVE);
            }
            
            // Perform MODWT decomposition
            MultiLevelMODWTResult result = transform.decompose(prices, levels);
            
            // Create modified result with specified levels zeroed
            MultiLevelMODWTResult modifiedResult = zeroSpecificLevels(result, levelsToZero);
            
            // Reconstruct signal
            double[] cleanedPrices = transform.reconstruct(modifiedResult);
            
            return new DenoisedPriceData(cleanedPrices, prices, DenoiseStrategy.AGGRESSIVE);
            
        } catch (Exception e) {
            logger.error("Error during zero-out denoising", e);
            return new DenoisedPriceData(prices.clone(), prices, DenoiseStrategy.AGGRESSIVE);
        }
    }
    
    /**
     * Extract approximation (smooth trend) signal.
     * This replaces the old getApproximation method with VectorWave's capabilities.
     */
    public double[] extractTrend(double[] prices, int levels) {
        if (prices == null || prices.length == 0) {
            logger.warn("Empty price data for trend extraction");
            return new double[0];
        }
        
        try {
            logger.debug("Extracting trend from {} price points using {} levels", prices.length, levels);
            
            MultiLevelMODWTTransform transform = getModwtTransform();
            if (transform == null) {
                logger.warn("MODWT transform not available, returning simple moving average trend");
                // Fallback to simple moving average for trend
                return calculateSimpleMovingAverage(prices, Math.min(prices.length / 4, 50));
            }
            
            // Use MODWT to decompose and reconstruct from approximation only
            MultiLevelMODWTResult result = transform.decompose(prices, levels);
            
            // Reconstruct using only the approximation coefficients (deepest level)
            double[] trendSignal = transform.reconstructFromLevel(result, levels);
            
            logger.debug("Trend extraction completed, trend length: {}", trendSignal.length);
            return trendSignal;
            
        } catch (Exception e) {
            logger.error("Error extracting trend", e);
            return prices.clone(); // Return original on error
        }
    }
    
    /**
     * Adaptive denoising that adjusts based on market volatility.
     */
    public DenoisedPriceData adaptiveDenoise(double[] prices, double volatilityLevel) {
        DenoiseStrategy strategy = DenoiseStrategy.forVolatility(volatilityLevel);
        return denoise(prices, strategy);
    }
    
    /**
     * Multi-strategy denoising for comparison.
     * Returns results from different strategies for analysis.
     */
    public MultiStrategyResult multiStrategyDenoise(double[] prices) {
        if (prices == null || prices.length == 0) {
            return new MultiStrategyResult(prices);
        }
        
        try {
            DenoisedPriceData conservative = denoise(prices, DenoiseStrategy.CONSERVATIVE);
            DenoisedPriceData balanced = denoise(prices, DenoiseStrategy.BALANCED);
            DenoisedPriceData aggressive = denoise(prices, DenoiseStrategy.AGGRESSIVE);
            
            return new MultiStrategyResult(prices, conservative, balanced, aggressive);
            
        } catch (Exception e) {
            logger.error("Error in multi-strategy denoising", e);
            return new MultiStrategyResult(prices);
        }
    }
    
    /**
     * Quality assessment of input data for denoising.
     */
    public DataQualityAssessment assessDataQuality(double[] prices) {
        if (prices == null || prices.length == 0) {
            return new DataQualityAssessment(0.0, "No data provided");
        }
        
        try {
            // Analyze data characteristics
            double variance = calculateVariance(prices);
            double noiseLevel = estimateNoiseLevel(prices);
            boolean hasGaps = detectDataGaps(prices);
            boolean hasOutliers = detectOutliers(prices);
            
            double qualityScore = calculateQualityScore(variance, noiseLevel, hasGaps, hasOutliers);
            String assessment = generateQualityAssessment(qualityScore, hasGaps, hasOutliers);
            
            return new DataQualityAssessment(qualityScore, assessment);
            
        } catch (Exception e) {
            logger.error("Error assessing data quality", e);
            return new DataQualityAssessment(0.5, "Quality assessment failed");
        }
    }
    
    // Private helper methods
    
    private MultiLevelMODWTResult zeroSpecificLevels(MultiLevelMODWTResult original, int[] levelsToZero) {
        // This is a conceptual implementation - VectorWave may provide better ways to do this
        // For now, we'd need to create a modified result object
        // This might require extending VectorWave's API or creating wrapper classes
        
        logger.warn("Zero-specific-levels not fully implemented - needs VectorWave API extension");
        return original; // Return original for now
    }
    
    private double calculateVariance(double[] data) {
        if (data.length <= 1) return 0.0;
        
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
    
    private double estimateNoiseLevel(double[] prices) {
        if (prices.length < 2) return 0.0;
        
        // Estimate noise as high-frequency component
        double totalVariation = 0.0;
        for (int i = 1; i < prices.length; i++) {
            totalVariation += Math.abs(prices[i] - prices[i-1]);
        }
        
        return totalVariation / (prices.length - 1);
    }
    
    private boolean detectDataGaps(double[] prices) {
        if (prices.length < 2) return false;
        
        double avgChange = estimateNoiseLevel(prices);
        for (int i = 1; i < prices.length; i++) {
            if (Math.abs(prices[i] - prices[i-1]) > avgChange * 5) {
                return true;
            }
        }
        return false;
    }
    
    private boolean detectOutliers(double[] prices) {
        if (prices.length < 3) return false;
        
        double mean = 0.0;
        for (double price : prices) {
            mean += price;
        }
        mean /= prices.length;
        
        double stdDev = Math.sqrt(calculateVariance(prices));
        
        for (double price : prices) {
            if (Math.abs(price - mean) > stdDev * 3) {
                return true;
            }
        }
        return false;
    }
    
    private double calculateQualityScore(double variance, double noiseLevel, boolean hasGaps, boolean hasOutliers) {
        double score = 1.0;
        
        // Penalize high noise
        if (noiseLevel > variance * 0.1) {
            score *= 0.8;
        }
        
        // Penalize gaps
        if (hasGaps) {
            score *= 0.7;
        }
        
        // Penalize outliers
        if (hasOutliers) {
            score *= 0.8;
        }
        
        return Math.max(0.1, Math.min(1.0, score));
    }
    
    private String generateQualityAssessment(double score, boolean hasGaps, boolean hasOutliers) {
        if (score >= 0.8) {
            return "High quality data, suitable for all denoising strategies";
        } else if (score >= 0.6) {
            return "Good quality data, minor issues detected";
        } else if (score >= 0.4) {
            String issues = "";
            if (hasGaps) issues += "data gaps ";
            if (hasOutliers) issues += "outliers ";
            return "Moderate quality data with " + issues + "- use conservative denoising";
        } else {
            return "Poor quality data - denoising may not be effective";
        }
    }
    
    // Nested classes for complex return types
    
    /**
     * Result from multi-strategy denoising.
     */
    public static class MultiStrategyResult {
        private final double[] originalPrices;
        private final DenoisedPriceData conservative;
        private final DenoisedPriceData balanced;
        private final DenoisedPriceData aggressive;
        
        public MultiStrategyResult(double[] original) {
            this.originalPrices = original.clone();
            this.conservative = null;
            this.balanced = null;
            this.aggressive = null;
        }
        
        public MultiStrategyResult(double[] original, DenoisedPriceData conservative, 
                                 DenoisedPriceData balanced, DenoisedPriceData aggressive) {
            this.originalPrices = original.clone();
            this.conservative = conservative;
            this.balanced = balanced;
            this.aggressive = aggressive;
        }
        
        public double[] getOriginalPrices() { return originalPrices.clone(); }
        public DenoisedPriceData getConservative() { return conservative; }
        public DenoisedPriceData getBalanced() { return balanced; }
        public DenoisedPriceData getAggressive() { return aggressive; }
        
        /**
         * Get the best strategy based on quality scores.
         */
        public DenoisedPriceData getBestResult() {
            if (conservative == null || balanced == null || aggressive == null) {
                return null;
            }
            
            DenoisedPriceData best = conservative;
            if (balanced.getQualityScore() > best.getQualityScore()) {
                best = balanced;
            }
            if (aggressive.getQualityScore() > best.getQualityScore()) {
                best = aggressive;
            }
            
            return best;
        }
    }
    
    /**
     * Data quality assessment result.
     */
    public static class DataQualityAssessment {
        private final double qualityScore;
        private final String assessment;
        
        public DataQualityAssessment(double qualityScore, String assessment) {
            this.qualityScore = qualityScore;
            this.assessment = assessment;
        }
        
        public double getQualityScore() { return qualityScore; }
        public String getAssessment() { return assessment; }
        
        public boolean isHighQuality() { return qualityScore >= 0.7; }
        public boolean isSuitableForDenoising() { return qualityScore >= 0.3; }
        
        @Override
        public String toString() {
            return String.format("DataQuality{score=%.2f, assessment='%s'}", qualityScore, assessment);
        }
    }
    
    // Getters
    public String getWaveletType() { return waveletType; }
    
    /**
     * Calculate simple moving average as fallback for trend extraction.
     */
    private double[] calculateSimpleMovingAverage(double[] prices, int period) {
        if (prices == null || prices.length == 0 || period <= 0) {
            return new double[0];
        }
        
        period = Math.min(period, prices.length);
        double[] result = new double[prices.length];
        
        // Initialize first values
        double sum = 0.0;
        for (int i = 0; i < period && i < prices.length; i++) {
            sum += prices[i];
            result[i] = sum / (i + 1);
        }
        
        // Calculate remaining values
        for (int i = period; i < prices.length; i++) {
            sum = sum - prices[i - period] + prices[i];
            result[i] = sum / period;
        }
        
        return result;
    }
}