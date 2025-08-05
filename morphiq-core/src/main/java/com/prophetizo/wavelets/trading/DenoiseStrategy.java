package com.prophetizo.wavelets.trading;

import ai.prophetizo.wavelet.denoising.WaveletDenoiser;

/**
 * Enumeration of denoising strategies for different trading styles and market conditions.
 */
public enum DenoiseStrategy {
    /**
     * Conservative denoising - preserves most signal details.
     * Best for: Scalping, high-frequency trading, short timeframes.
     */
    CONSERVATIVE("Conservative", "Light denoising, preserves signal details", 
                WaveletDenoiser.ThresholdMethod.UNIVERSAL, WaveletDenoiser.ThresholdType.SOFT, 0.5),
    
    /**
     * Balanced denoising - standard approach for most trading.
     * Best for: Day trading, swing trading, general purpose.
     */
    BALANCED("Balanced", "Standard denoising for general trading", 
            WaveletDenoiser.ThresholdMethod.SURE, WaveletDenoiser.ThresholdType.SOFT, 1.0),
    
    /**
     * Aggressive denoising - heavy noise removal for trend following.
     * Best for: Position trading, trend following, longer timeframes.
     */
    AGGRESSIVE("Aggressive", "Heavy denoising for clean trend signals", 
              WaveletDenoiser.ThresholdMethod.MINIMAX, WaveletDenoiser.ThresholdType.HARD, 1.5),
    
    /**
     * Adaptive denoising - adjusts based on market volatility.
     * Best for: Dynamic strategies, changing market conditions.
     */
    ADAPTIVE("Adaptive", "Market-condition based denoising", 
            WaveletDenoiser.ThresholdMethod.UNIVERSAL, WaveletDenoiser.ThresholdType.SOFT, 1.0);
    
    private final String displayName;
    private final String description;
    private final WaveletDenoiser.ThresholdMethod thresholdMethod;
    private final WaveletDenoiser.ThresholdType thresholdType;
    private final double intensityMultiplier;
    
    DenoiseStrategy(String displayName, String description, 
                   WaveletDenoiser.ThresholdMethod method, 
                   WaveletDenoiser.ThresholdType type,
                   double intensityMultiplier) {
        this.displayName = displayName;
        this.description = description;
        this.thresholdMethod = method;
        this.thresholdType = type;
        this.intensityMultiplier = intensityMultiplier;
    }
    
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public WaveletDenoiser.ThresholdMethod getThresholdMethod() { return thresholdMethod; }
    public WaveletDenoiser.ThresholdType getThresholdType() { return thresholdType; }
    public double getIntensityMultiplier() { return intensityMultiplier; }
    
    /**
     * Get recommended strategy based on trading style.
     */
    public static DenoiseStrategy forTradingStyle(TradingStyle style) {
        return switch (style) {
            case SCALPING -> CONSERVATIVE;
            case DAY_TRADING -> BALANCED;
            case SWING_TRADING -> BALANCED;
            case POSITION_TRADING -> AGGRESSIVE;
        };
    }
    
    /**
     * Get recommended strategy based on market volatility.
     */
    public static DenoiseStrategy forVolatility(double volatilityLevel) {
        if (volatilityLevel > 2.0) {
            return AGGRESSIVE; // High volatility needs more denoising
        } else if (volatilityLevel > 1.0) {
            return BALANCED;
        } else {
            return CONSERVATIVE; // Low volatility, preserve details
        }
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}