package com.prophetizo.wavelets.swt.core;

import com.prophetizo.LoggerConfig;
import org.slf4j.Logger;

import java.util.Arrays;

/**
 * Wavelet-based Average True Range (WATR) calculation.
 * Uses RMS energy from detail coefficients to estimate volatility.
 * 
 * <h3>Level Weight Decay</h3>
 * The level weight decay factor controls how quickly the weight decreases for coarser wavelet scales.
 * This is crucial for accurate volatility estimation as different wavelet levels capture different
 * frequency components of price movements.
 * 
 * <h4>Weight Formula</h4>
 * <pre>
 * weight = 1.0 / (1.0 + levelIndex * levelWeightDecay)
 * </pre>
 * where levelIndex is the 0-based array index used in the calculation loop.
 * 
 * <h4>Rationale</h4>
 * Finer scales (lower levels) capture high-frequency price movements and short-term volatility,
 * which are more relevant for ATR-like measurements. Coarser scales represent longer-term trends
 * and should have less influence on the immediate volatility estimate.
 * 
 * <h4>Examples with levelWeightDecay = 0.5</h4>
 * <ul>
 *   <li>Detail Level D₁ (array index 0): weight = 1.0 / (1.0 + 0 * 0.5) = 1.00 (100% contribution)</li>
 *   <li>Detail Level D₂ (array index 1): weight = 1.0 / (1.0 + 1 * 0.5) = 0.67 (67% contribution)</li>
 *   <li>Detail Level D₃ (array index 2): weight = 1.0 / (1.0 + 2 * 0.5) = 0.50 (50% contribution)</li>
 *   <li>Detail Level D₄ (array index 3): weight = 1.0 / (1.0 + 3 * 0.5) = 0.40 (40% contribution)</li>
 * </ul>
 * 
 * <h4>Optimal Values by Market Condition</h4>
 * <ul>
 *   <li>0.3-0.4: More weight on coarser scales (trending markets)</li>
 *   <li>0.5-0.6: Balanced weighting (default)</li>
 *   <li>0.7-0.8: More weight on finer scales (volatile/choppy markets)</li>
 * </ul>
 */
public class WaveletAtr {
    private static final Logger logger = LoggerConfig.getLogger(WaveletAtr.class);
    
    /**
     * Default level weight decay factor (0.5 = balanced weighting).
     * See class documentation for detailed explanation of weight decay formula.
     */
    private static final double DEFAULT_LEVEL_WEIGHT_DECAY = 0.5;
    
    /**
     * Epsilon value for floating-point equality comparisons.
     * Used to determine if two double values are effectively equal,
     * accounting for floating-point precision limitations.
     */
    private static final double EPSILON = 0.001;
    
    /**
     * Volatility thresholds for estimating appropriate WATR band multipliers.
     * These values categorize market volatility levels.
     */
    private static final double VERY_LOW_VOLATILITY_THRESHOLD = 0.001;
    private static final double LOW_VOLATILITY_THRESHOLD = 0.01;
    private static final double HIGH_VOLATILITY_THRESHOLD = 0.1;
    
    /**
     * WATR band multipliers for different volatility regimes.
     * These control the width of volatility-based bands.
     */
    private static final double DEFAULT_MULTIPLIER = 2.0;
    private static final double CONSERVATIVE_MULTIPLIER = 1.5;
    private static final double WIDE_BANDS_MULTIPLIER = 3.0;
    
    /**
     * Calculate weight for a given wavelet level.
     * 
     * @param level 0-based array index (0 for D₁, 1 for D₂, etc.)
     * @param levelWeightDecay the decay factor for level weights
     * @return weight value between 0 and 1
     */
    private static double calculateLevelWeight(int level, double levelWeightDecay) {
        return 1.0 / (1.0 + level * levelWeightDecay);
    }
    
    private final int smoothingPeriod;
    private final double alpha; // Smoothing factor
    private final double levelWeightDecay; // Configurable weight decay factor
    
    // Pre-calculated level weights for performance optimization
    private static final int MAX_CACHED_LEVELS = 10; // Most decompositions use <= 10 levels
    private final double[] cachedLevelWeights;
    
    // Circular buffer for smoothing - requires synchronization for thread safety
    private double[] buffer;
    private int bufferIndex = 0;
    private boolean bufferFilled = false;
    private double ema = 0.0;
    
    // Lock object for synchronizing mutable state
    private final Object stateLock = new Object();
    
    /**
     * Constructor with default level weight decay.
     * @param smoothingPeriod period for smoothing the WATR values
     */
    public WaveletAtr(int smoothingPeriod) {
        this(smoothingPeriod, DEFAULT_LEVEL_WEIGHT_DECAY);
    }
    
    /**
     * Constructor with configurable level weight decay.
     * @param smoothingPeriod period for smoothing the WATR values
     * @param levelWeightDecay decay factor for level weights (0.3-0.8 typical)
     */
    public WaveletAtr(int smoothingPeriod, double levelWeightDecay) {
        this.smoothingPeriod = Math.max(1, smoothingPeriod);
        this.alpha = 2.0 / (smoothingPeriod + 1.0);
        this.levelWeightDecay = Math.max(0.1, Math.min(1.0, levelWeightDecay)); // Clamp to reasonable range
        this.buffer = new double[this.smoothingPeriod];
        Arrays.fill(buffer, 0.0);
        
        // Pre-calculate level weights for performance optimization
        this.cachedLevelWeights = new double[MAX_CACHED_LEVELS];
        for (int i = 0; i < MAX_CACHED_LEVELS; i++) {
            cachedLevelWeights[i] = calculateLevelWeight(i, this.levelWeightDecay);
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("Initialized WaveletAtr with smoothing period: {}, level weight decay: {}", 
                        this.smoothingPeriod, this.levelWeightDecay);
            logger.debug("Pre-calculated {} level weights for performance", MAX_CACHED_LEVELS);
        }
    }
    
    /**
     * Calculate WATR from detail coefficients of first k levels.
     * 
     * <p>Thread-safety: This method is thread-safe with respect to internal state.
     * For input safety, it makes defensive copies of the required array portions
     * to prevent issues from concurrent modification of the input arrays.
     * 
     * @param allDetails array of detail coefficient arrays (may be modified by other threads)
     * @param k number of detail levels to use
     * @return smoothed WATR value
     */
    public double calculate(double[][] allDetails, int k) {
        if (allDetails == null || allDetails.length == 0 || k <= 0) {
            return 0.0;
        }
        
        int levelsToUse = Math.min(k, allDetails.length);
        
        // Make defensive copies of the arrays we'll read to prevent concurrent modification issues
        // Only copy the arrays we actually need (first k levels)
        double[][] detailsCopy = new double[levelsToUse][];
        for (int i = 0; i < levelsToUse; i++) {
            if (allDetails[i] != null) {
                // Defensive copy to prevent concurrent modification during calculation
                detailsCopy[i] = Arrays.copyOf(allDetails[i], allDetails[i].length);
            }
        }
        
        double rmsEnergy = calculateRmsEnergy(detailsCopy, levelsToUse);
        
        // Apply smoothing - synchronized for thread safety
        double smoothedWatr;
        synchronized (stateLock) {
            smoothedWatr = addToSmoothing(rmsEnergy);
        }
        
        if (logger.isTraceEnabled()) {
            logger.trace("WATR calculation: raw RMS={}, smoothed={}, levels={}", 
                        String.format("%.4f", rmsEnergy), 
                        String.format("%.4f", smoothedWatr), 
                        levelsToUse);
        }
        
        return smoothedWatr;
    }
    
    /**
     * Calculate WATR from SWT result.
     * 
     * <p>Thread-safe: Extracts detail arrays from the SWT result and delegates to
     * the main calculate method. The SwtResult.getDetail() method returns defensive
     * copies, so this is safe from concurrent modification.
     * 
     * @param swtResult the SWT decomposition result
     * @param k number of detail levels to use
     * @return smoothed WATR value
     */
    public double calculate(VectorWaveSwtAdapter.SwtResult swtResult, int k) {
        if (swtResult == null || k <= 0) {
            return 0.0;
        }
        
        int levelsToUse = Math.min(k, swtResult.getLevels());
        double[][] details = new double[levelsToUse][];
        
        for (int i = 0; i < levelsToUse; i++) {
            details[i] = swtResult.getDetail(i + 1); // SWT uses 1-based indexing, returns defensive copy
        }
        
        return calculate(details, k);
    }
    
    /**
     * Calculate RMS energy across specified detail levels
     */
    private double calculateRmsEnergy(double[][] details, int levels) {
        if (details.length == 0 || levels <= 0) {
            return 0.0;
        }
        
        double totalEnergy = 0.0;
        int totalSamples = 0;
        
        for (int level = 0; level < levels && level < details.length; level++) {
            double[] detail = details[level];
            if (detail != null && detail.length > 0) {
                // Calculate energy for this level
                double levelEnergy = 0.0;
                for (double coeff : detail) {
                    levelEnergy += coeff * coeff;
                }
                
                // Weight by level (finer details contribute more to volatility)
                // Apply hyperbolic decay: weight = 1.0 / (1.0 + level * decay)
                // This emphasizes short-term volatility and reduces the influence of coarser (long-term) trends.
                double weight = (level < MAX_CACHED_LEVELS) ? 
                    cachedLevelWeights[level] : 
                    calculateLevelWeight(level, levelWeightDecay);
                totalEnergy += levelEnergy * weight;
                totalSamples += detail.length;
                
                if (logger.isTraceEnabled()) {
                    logger.trace("Detail D{} (index {}): energy={}, weight={}", 
                                level + 1,
                                level,
                                String.format("%.4f", levelEnergy), 
                                String.format("%.2f", weight));
                }
            }
        }
        
        if (totalSamples == 0) {
            return 0.0;
        }
        
        // RMS = sqrt(mean(squares))
        double meanEnergy = totalEnergy / totalSamples;
        double rms = Math.sqrt(Math.max(0.0, meanEnergy));
        
        return rms;
    }
    
    /**
     * Add value to smoothing buffer and return smoothed result.
     * Must be called within synchronized block.
     */
    private double addToSmoothing(double value) {
        // Add to circular buffer
        buffer[bufferIndex] = value;
        bufferIndex = (bufferIndex + 1) % buffer.length;
        
        if (!bufferFilled && bufferIndex == 0) {
            bufferFilled = true;
        }
        
        // Use EMA for smoothing
        if (ema == 0.0) {
            // Initialize EMA with first value
            ema = value;
        } else {
            // Update EMA
            ema = alpha * value + (1.0 - alpha) * ema;
        }
        
        return ema;
    }
    
    /**
     * Reset the smoothing state.
     * Thread-safe: synchronizes access to mutable state.
     */
    public void reset() {
        synchronized (stateLock) {
            Arrays.fill(buffer, 0.0);
            bufferIndex = 0;
            bufferFilled = false;
            ema = 0.0;
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("WaveletAtr state reset");
        }
    }
    
    /**
     * Get current smoothed value without adding new data.
     * Thread-safe: synchronizes access to ema.
     */
    public double getCurrentValue() {
        synchronized (stateLock) {
            return ema;
        }
    }
    
    /**
     * Calculate instantaneous WATR without smoothing using default decay
     */
    public static double calculateInstantaneous(double[][] details, int k) {
        return calculateInstantaneous(details, k, DEFAULT_LEVEL_WEIGHT_DECAY);
    }
    
    /**
     * Calculate instantaneous WATR without smoothing with configurable decay
     */
    public static double calculateInstantaneous(double[][] details, int k, double levelWeightDecay) {
        if (details == null || details.length == 0 || k <= 0) {
            return 0.0;
        }
        
        int levelsToUse = Math.min(k, details.length);
        double totalEnergy = 0.0;
        int totalSamples = 0;
        
        // Pre-calculate weights for common case (DEFAULT_LEVEL_WEIGHT_DECAY)
        // to avoid repeated calculations in this static context
        boolean useDefaultWeights = Math.abs(levelWeightDecay - DEFAULT_LEVEL_WEIGHT_DECAY) < EPSILON;
        double[] weights = null;
        if (useDefaultWeights) {
            weights = new double[Math.min(MAX_CACHED_LEVELS, levelsToUse)];
            for (int i = 0; i < weights.length; i++) {
                weights[i] = calculateLevelWeight(i, DEFAULT_LEVEL_WEIGHT_DECAY);
            }
        }
        
        for (int level = 0; level < levelsToUse; level++) {
            double[] detail = details[level];
            if (detail != null && detail.length > 0) {
                // Use only the most recent coefficient for instantaneous calculation
                double lastCoeff = detail[detail.length - 1];
                double weight;
                if (useDefaultWeights && level < weights.length) {
                    weight = weights[level];
                } else {
                    weight = calculateLevelWeight(level, levelWeightDecay);
                }
                totalEnergy += lastCoeff * lastCoeff * weight;
                totalSamples++;
            }
        }
        
        if (totalSamples == 0) {
            return 0.0;
        }
        
        return Math.sqrt(totalEnergy / totalSamples);
    }
    
    /**
     * Calculate WATR bands for stop/target placement
     */
    public static class WatrBands {
        private final double center;
        private final double watr;
        private final double upperBand;
        private final double lowerBand;
        private final double multiplier;
        
        public WatrBands(double center, double watr, double multiplier) {
            this.center = center;
            this.watr = watr;
            this.multiplier = multiplier;
            this.upperBand = center + watr * multiplier;
            this.lowerBand = center - watr * multiplier;
        }
        
        public double getCenter() { return center; }
        public double getWatr() { return watr; }
        public double getUpperBand() { return upperBand; }
        public double getLowerBand() { return lowerBand; }
        public double getMultiplier() { return multiplier; }
        
        public boolean isInsideBands(double price) {
            return price >= lowerBand && price <= upperBand;
        }
        
        public double getDistanceFromCenter(double price) {
            return Math.abs(price - center);
        }
        
        public double getRelativePosition(double price) {
            if (watr == 0.0) return 0.0;
            return (price - center) / (watr * multiplier);
        }
        
        @Override
        public String toString() {
            return String.format("WatrBands{center=%.2f, watr=%.4f, bands=[%.2f, %.2f], mult=%.1f}",
                    center, watr, lowerBand, upperBand, multiplier);
        }
    }
    
    /**
     * Create WATR bands around a price level
     */
    public WatrBands createBands(double centerPrice, double multiplier) {
        return new WatrBands(centerPrice, getCurrentValue(), multiplier);
    }
    
    /**
     * Estimate appropriate multiplier based on recent volatility
     */
    public double estimateMultiplier() {
        double currentWatr = getCurrentValue();
        
        if (currentWatr < VERY_LOW_VOLATILITY_THRESHOLD) {
            return DEFAULT_MULTIPLIER; // Default for very low volatility
        } else if (currentWatr < LOW_VOLATILITY_THRESHOLD) {
            return CONSERVATIVE_MULTIPLIER; // Conservative for low volatility
        } else if (currentWatr > HIGH_VOLATILITY_THRESHOLD) {
            return WIDE_BANDS_MULTIPLIER; // Wide bands for high volatility
        } else {
            return DEFAULT_MULTIPLIER; // Standard multiplier
        }
    }
    
    /**
     * Get smoothing statistics
     */
    public String getStats() {
        double bufferSum = Arrays.stream(buffer).sum();
        double bufferMean = bufferSum / buffer.length;
        
        return String.format("WaveletAtr{period=%d, ema=%.4f, bufferMean=%.4f, filled=%s}",
                smoothingPeriod, ema, bufferMean, bufferFilled);
    }
}