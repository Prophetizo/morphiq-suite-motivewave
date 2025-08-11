package com.prophetizo.wavelets.trading;

/**
 * Enumeration of trading styles with recommended wavelet parameters.
 */
public enum TradingStyle {
    /**
     * Scalping - very short term, high frequency trading.
     * Optimized for fast response and minimal lag.
     */
    SCALPING("Scalping", "High frequency, sub-minute trading", 
             1, 3, "db2", DenoiseStrategy.CONSERVATIVE),
    
    /**
     * Day trading - intraday positions, holding for hours.
     * Balanced between responsiveness and noise filtering.
     */
    DAY_TRADING("Day Trading", "Intraday positions, hourly timeframes", 
                3, 5, "db4", DenoiseStrategy.BALANCED),
    
    /**
     * Swing trading - positions held for days to weeks.
     * Emphasizes trend identification over noise reduction.
     */
    SWING_TRADING("Swing Trading", "Multi-day positions, daily timeframes", 
                  4, 6, "sym4", DenoiseStrategy.BALANCED),
    
    /**
     * Position trading - long term positions, weeks to months.
     * Heavily filtered for clear trend signals.
     */
    POSITION_TRADING("Position Trading", "Long-term positions, weekly timeframes", 
                     5, 7, "coif3", DenoiseStrategy.AGGRESSIVE);
    
    private final String displayName;
    private final String description;
    private final int minLevels;
    private final int maxLevels;
    private final String recommendedWavelet;
    private final DenoiseStrategy defaultDenoiseStrategy;
    
    TradingStyle(String displayName, String description, 
                int minLevels, int maxLevels, 
                String recommendedWavelet, DenoiseStrategy defaultStrategy) {
        this.displayName = displayName;
        this.description = description;
        this.minLevels = minLevels;
        this.maxLevels = maxLevels;
        this.recommendedWavelet = recommendedWavelet;
        this.defaultDenoiseStrategy = defaultStrategy;
    }
    
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int getMinLevels() { return minLevels; }
    public int getMaxLevels() { return maxLevels; }
    public String getRecommendedWavelet() { return recommendedWavelet; }
    public DenoiseStrategy getDefaultDenoiseStrategy() { return defaultDenoiseStrategy; }
    
    /**
     * Get optimal decomposition levels for the given bar size.
     */
    public int getOptimalLevels(long barSizeMinutes) {
        // Adjust levels based on timeframe
        if (barSizeMinutes <= 1) {
            return minLevels; // Very short timeframes need fewer levels
        } else if (barSizeMinutes <= 60) {
            return (minLevels + maxLevels) / 2; // Medium timeframes
        } else {
            return maxLevels; // Longer timeframes benefit from more levels
        }
    }
    
    /**
     * Determine if this style is suitable for the given timeframe.
     */
    public boolean isSuitableForTimeframe(long barSizeMinutes) {
        return switch (this) {
            case SCALPING -> barSizeMinutes <= 5;
            case DAY_TRADING -> barSizeMinutes >= 1 && barSizeMinutes <= 240;
            case SWING_TRADING -> barSizeMinutes >= 60 && barSizeMinutes <= 1440;
            case POSITION_TRADING -> barSizeMinutes >= 240;
        };
    }
    
    /**
     * Get computational complexity factor (1.0 = baseline).
     * Scalping needs fastest computation, position trading can afford more complex analysis.
     */
    public double getComputationalComplexity() {
        return switch (this) {
            case SCALPING -> 0.8;      // Optimize for speed
            case DAY_TRADING -> 1.0;   // Balanced
            case SWING_TRADING -> 1.2; // More analysis acceptable
            case POSITION_TRADING -> 1.5; // Full analysis
        };
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}