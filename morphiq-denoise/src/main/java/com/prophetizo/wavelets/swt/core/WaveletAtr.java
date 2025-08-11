package com.prophetizo.wavelets.swt.core;




import java.util.Arrays;

/**
 * Wavelet-based Average True Range (WATR) calculation.
 * Uses RMS energy from detail coefficients to estimate volatility.
 */
public class WaveletAtr {
    // Simple console logging for standalone compilation
    private static void log(String level, String message, Object... args) {
        System.out.printf("[%s] [WaveletAtr] " + message + "%n", level, args);
    }
    
    /**
     * Level weight decay factor for multi-scale energy calculation.
     * 
     * This factor controls how quickly the weight decreases for coarser wavelet scales.
     * A value of 0.5 means each successive level (coarser scale) contributes 
     * proportionally less to the total volatility estimate.
     * 
     * Rationale: Finer scales (lower levels) capture high-frequency price movements
     * and short-term volatility, which are more relevant for ATR-like measurements.
     * Coarser scales represent longer-term trends and should have less influence
     * on the immediate volatility estimate.
     * 
     * Weight formula: weight = 1.0 / (1.0 + level * LEVEL_WEIGHT_DECAY)
     * - Level 0: weight = 1.00 (100% contribution)
     * - Level 1: weight = 0.67 (67% contribution)  
     * - Level 2: weight = 0.50 (50% contribution)
     * - Level 3: weight = 0.40 (40% contribution)
     */
    private static final double LEVEL_WEIGHT_DECAY = 0.5;
    
    private final int smoothingPeriod;
    private final double alpha; // Smoothing factor
    
    // Circular buffer for smoothing
    private double[] buffer;
    private int bufferIndex = 0;
    private boolean bufferFilled = false;
    private double ema = 0.0;
    
    public WaveletAtr(int smoothingPeriod) {
        this.smoothingPeriod = Math.max(1, smoothingPeriod);
        this.alpha = 2.0 / (smoothingPeriod + 1.0);
        this.buffer = new double[this.smoothingPeriod];
        Arrays.fill(buffer, 0.0);
        
        log("DEBUG", "Initialized WaveletAtr with smoothing period: {}", this.smoothingPeriod);
    }
    
    /**
     * Calculate WATR from detail coefficients of first k levels
     */
    public double calculate(double[][] allDetails, int k) {
        if (allDetails == null || allDetails.length == 0 || k <= 0) {
            return 0.0;
        }
        
        int levelsToUse = Math.min(k, allDetails.length);
        double rmsEnergy = calculateRmsEnergy(allDetails, levelsToUse);
        
        // Apply smoothing
        double smoothedWatr = addToSmoothing(rmsEnergy);
        
        log("TRACE", "WATR calculation: raw RMS={:.4f}, smoothed={:.4f}, levels={}", 
                    rmsEnergy, smoothedWatr, levelsToUse);
        
        return smoothedWatr;
    }
    
    /**
     * Calculate WATR from SWT result
     */
    public double calculate(VectorWaveSwtAdapter.SwtResult swtResult, int k) {
        if (swtResult == null || k <= 0) {
            return 0.0;
        }
        
        int levelsToUse = Math.min(k, swtResult.getLevels());
        double[][] details = new double[levelsToUse][];
        
        for (int i = 0; i < levelsToUse; i++) {
            details[i] = swtResult.getDetail(i + 1); // SWT uses 1-based indexing
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
                double weight = 1.0 / (1.0 + level * LEVEL_WEIGHT_DECAY);
                totalEnergy += levelEnergy * weight;
                totalSamples += detail.length;
                
                log("TRACE", "Level {} energy: {:.4f}, weight: {:.2f}", level + 1, levelEnergy, weight);
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
     * Add value to smoothing buffer and return smoothed result
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
     * Reset the smoothing state
     */
    public void reset() {
        Arrays.fill(buffer, 0.0);
        bufferIndex = 0;
        bufferFilled = false;
        ema = 0.0;
        
        log("DEBUG", "WaveletAtr state reset");
    }
    
    /**
     * Get current smoothed value without adding new data
     */
    public double getCurrentValue() {
        return ema;
    }
    
    /**
     * Calculate instantaneous WATR without smoothing
     */
    public static double calculateInstantaneous(double[][] details, int k) {
        if (details == null || details.length == 0 || k <= 0) {
            return 0.0;
        }
        
        int levelsToUse = Math.min(k, details.length);
        double totalEnergy = 0.0;
        int totalSamples = 0;
        
        for (int level = 0; level < levelsToUse; level++) {
            double[] detail = details[level];
            if (detail != null && detail.length > 0) {
                // Use only the most recent coefficient for instantaneous calculation
                double lastCoeff = detail[detail.length - 1];
                double weight = 1.0 / (1.0 + level * 0.5);
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
        
        if (currentWatr < 0.001) {
            return 2.0; // Default for very low volatility
        } else if (currentWatr < 0.01) {
            return 1.5; // Conservative for low volatility
        } else if (currentWatr > 0.1) {
            return 3.0; // Wide bands for high volatility
        } else {
            return 2.0; // Standard multiplier
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