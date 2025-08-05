package com.prophetizo.wavelets.trading;

/**
 * Enumeration of market regimes detected through wavelet analysis.
 * Based on trend strength, volatility levels, and coefficient patterns.
 */
public enum MarketRegime {
    /**
     * Strong upward trend with low to moderate volatility.
     * Characterized by persistent positive trend signals.
     */
    TRENDING_BULL("Trending Bull", "Strong upward momentum, suitable for trend following"),
    
    /**
     * Strong downward trend with low to moderate volatility.
     * Characterized by persistent negative trend signals.
     */
    TRENDING_BEAR("Trending Bear", "Strong downward momentum, consider short positions"),
    
    /**
     * Sideways movement with high volatility.
     * Characterized by oscillating signals and high detail coefficient energy.
     */
    RANGING_HIGH_VOL("Ranging High Vol", "Choppy market, high volatility range-bound"),
    
    /**
     * Sideways movement with low volatility.
     * Characterized by weak signals and low coefficient energy.
     */
    RANGING_LOW_VOL("Ranging Low Vol", "Low volatility consolidation, breakout potential"),
    
    /**
     * Sudden strong movement breaking from previous pattern.
     * Characterized by spike in high-frequency detail coefficients.
     */
    BREAKOUT("Breakout", "Strong directional move, momentum building"),
    
    /**
     * Trend reversal pattern detected.
     * Characterized by changing sign in trend signal and coefficient patterns.
     */
    REVERSAL("Reversal", "Trend change detected, momentum shifting"),
    
    /**
     * Market crash or extreme volatility event.
     * Characterized by abnormal coefficient patterns and extreme values.
     */
    CRISIS("Crisis", "Extreme volatility, risk management critical"),
    
    /**
     * Unable to determine regime due to insufficient data or unclear patterns.
     */
    UNKNOWN("Unknown", "Insufficient data or unclear market structure");
    
    private final String displayName;
    private final String description;
    
    MarketRegime(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    
    /**
     * Determine if this regime indicates a trending market.
     */
    public boolean isTrending() {
        return this == TRENDING_BULL || this == TRENDING_BEAR;
    }
    
    /**
     * Determine if this regime indicates a ranging market.
     */
    public boolean isRanging() {
        return this == RANGING_HIGH_VOL || this == RANGING_LOW_VOL;
    }
    
    /**
     * Determine if this regime indicates high volatility conditions.
     */
    public boolean isHighVolatility() {
        return this == RANGING_HIGH_VOL || this == BREAKOUT || this == CRISIS;
    }
    
    /**
     * Determine if this regime suggests bullish conditions.
     */
    public boolean isBullish() {
        return this == TRENDING_BULL || (this == BREAKOUT); // Breakout could be either way
    }
    
    /**
     * Determine if this regime suggests bearish conditions.
     */
    public boolean isBearish() {
        return this == TRENDING_BEAR || this == CRISIS;
    }
    
    /**
     * Get trading recommendation based on regime.
     */
    public String getTradingRecommendation() {
        return switch (this) {
            case TRENDING_BULL -> "Follow trend, buy dips, momentum strategies";
            case TRENDING_BEAR -> "Follow trend, sell rallies, short strategies";
            case RANGING_HIGH_VOL -> "Mean reversion, range trading, volatility strategies";
            case RANGING_LOW_VOL -> "Prepare for breakout, accumulation strategies";
            case BREAKOUT -> "Momentum strategies, trend confirmation";
            case REVERSAL -> "Counter-trend strategies, be cautious";
            case CRISIS -> "Risk management, defensive positioning";
            case UNKNOWN -> "Wait for clearer signals, neutral positioning";
        };
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}