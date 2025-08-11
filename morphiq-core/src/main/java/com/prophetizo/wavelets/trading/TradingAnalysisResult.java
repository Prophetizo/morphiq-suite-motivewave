package com.prophetizo.wavelets.trading;

import ai.prophetizo.wavelet.modwt.MultiLevelMODWTResult;
import com.prophetizo.LoggerConfig;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Rich result object containing comprehensive wavelet-based trading analysis.
 * Replaces raw coefficient arrays with domain-specific information.
 */
public class TradingAnalysisResult {
    private static final Logger logger = LoggerConfig.getLogger(TradingAnalysisResult.class);
    
    private final double trendSignal;
    private final double volatilityLevel;
    private final boolean anomalyDetected;
    private final MultiLevelMODWTResult modwtResult;
    private final MarketRegime regime;
    private final double confidence;
    private final List<String> anomalies;
    private final long analysisTimestamp;
    
    /**
     * Constructor for complete analysis result.
     */
    public TradingAnalysisResult(double trendSignal, double volatilityLevel, 
                                boolean anomalyDetected, MultiLevelMODWTResult modwtResult, 
                                MarketRegime regime, double confidence, 
                                List<String> anomalies) {
        this.trendSignal = trendSignal;
        this.volatilityLevel = volatilityLevel;
        this.anomalyDetected = anomalyDetected;
        this.modwtResult = modwtResult;
        this.regime = regime;
        this.confidence = Math.max(0.0, Math.min(1.0, confidence)); // Clamp to [0,1]
        this.anomalies = anomalies != null ? List.copyOf(anomalies) : Collections.emptyList();
        this.analysisTimestamp = System.currentTimeMillis();
        
        logger.debug("Created TradingAnalysisResult - Regime: {}, Confidence: {:.2f}, Trend: {:.4f}", 
                    regime, this.confidence, trendSignal);
    }
    
    /**
     * Simplified constructor for basic analysis.
     */
    public TradingAnalysisResult(double trendSignal, double volatilityLevel, 
                                boolean anomalyDetected, MultiLevelMODWTResult modwtResult) {
        this(trendSignal, volatilityLevel, anomalyDetected, modwtResult, 
             determineRegime(trendSignal, volatilityLevel, anomalyDetected),
             calculateConfidence(trendSignal, volatilityLevel, modwtResult),
             Collections.emptyList());
    }
    
    // Getters
    public double getTrendSignal() { return trendSignal; }
    public double getVolatilityLevel() { return volatilityLevel; }
    public boolean isAnomalyDetected() { return anomalyDetected; }
    public MarketRegime getMarketRegime() { return regime; }
    public double getConfidence() { return confidence; }
    public List<String> getAnomalies() { return anomalies; }
    public long getAnalysisTimestamp() { return analysisTimestamp; }
    
    /**
     * Get detail signal at specific wavelet level.
     * This replaces direct coefficient array access.
     */
    public double getDetailSignalAtLevel(int level) {
        if (modwtResult == null) {
            logger.warn("No MODWT result available, returning 0 for level {}", level);
            return 0.0;
        }
        
        try {
            if (level >= modwtResult.getLevels()) {
                logger.warn("Requested level {} exceeds available levels {}, returning 0", 
                           level, modwtResult.getLevels());
                return 0.0;
            }
            
            // VectorWave uses 1-based indexing for levels
            double[] coeffs = modwtResult.getDetailCoeffsAtLevel(level + 1);
            if (coeffs == null || coeffs.length == 0) {
                logger.warn("No coefficients available at level {}, returning 0", level);
                return 0.0;
            }
            
            // Return the latest (most recent) coefficient
            return coeffs[coeffs.length - 1];
            
        } catch (Exception e) {
            logger.error("Error getting detail signal at level {}", level, e);
            return 0.0;
        }
    }
    
    /**
     * Get energy distribution across wavelet levels.
     * Useful for understanding market structure.
     */
    public double[] getEnergyDistribution() {
        if (modwtResult == null) {
            return new double[0];
        }
        
        try {
            return modwtResult.getRelativeEnergyDistribution();
        } catch (Exception e) {
            logger.error("Error calculating energy distribution", e);
            return new double[modwtResult.getLevels()];
        }
    }
    
    /**
     * Get the dominant frequency level (level with highest energy).
     */
    public int getDominantLevel() {
        double[] energies = getEnergyDistribution();
        if (energies.length == 0) {
            return 0;
        }
        
        int maxIndex = 0;
        double maxEnergy = energies[0];
        for (int i = 1; i < energies.length; i++) {
            if (energies[i] > maxEnergy) {
                maxEnergy = energies[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }
    
    /**
     * Check if this is a high-confidence signal suitable for trading.
     */
    public boolean isHighConfidenceSignal() {
        return confidence >= 0.7 && !anomalyDetected;
    }
    
    /**
     * Check if regime shift is detected.
     */
    public boolean isRegimeShiftDetected() {
        return regime == MarketRegime.REVERSAL || regime == MarketRegime.BREAKOUT;
    }
    
    /**
     * Get trading recommendation based on analysis.
     */
    public String getTradingRecommendation() {
        if (!isHighConfidenceSignal()) {
            return "Low confidence - wait for clearer signals";
        }
        
        if (anomalyDetected) {
            return "Anomaly detected - exercise caution";
        }
        
        return regime.getTradingRecommendation();
    }
    
    /**
     * Get signal strength classification.
     */
    public SignalStrength getSignalStrength() {
        double absTrend = Math.abs(trendSignal);
        if (absTrend > 2.0) {
            return SignalStrength.VERY_STRONG;
        } else if (absTrend > 1.0) {
            return SignalStrength.STRONG;
        } else if (absTrend > 0.5) {
            return SignalStrength.MODERATE;
        } else if (absTrend > 0.1) {
            return SignalStrength.WEAK;
        } else {
            return SignalStrength.NEUTRAL;
        }
    }
    
    /**
     * Determine market regime from analysis parameters.
     */
    private static MarketRegime determineRegime(double trend, double volatility, boolean anomaly) {
        if (anomaly) {
            return volatility > 3.0 ? MarketRegime.CRISIS : MarketRegime.BREAKOUT;
        }
        
        double absTrend = Math.abs(trend);
        
        if (absTrend > 1.0) {
            return trend > 0 ? MarketRegime.TRENDING_BULL : MarketRegime.TRENDING_BEAR;
        } else if (volatility > 1.5) {
            return MarketRegime.RANGING_HIGH_VOL;
        } else if (volatility < 0.5) {
            return MarketRegime.RANGING_LOW_VOL;
        } else {
            return MarketRegime.UNKNOWN;
        }
    }
    
    /**
     * Calculate confidence score based on signal consistency.
     */
    private static double calculateConfidence(double trend, double volatility, MultiLevelMODWTResult result) {
        if (result == null) {
            return 0.5; // Neutral confidence
        }
        
        try {
            // Base confidence on signal-to-noise ratio and energy concentration
            double totalEnergy = result.getTotalEnergy();
            double approxEnergy = result.getApproximationEnergy();
            
            if (totalEnergy <= 0) {
                return 0.5;
            }
            
            // Higher approximation energy relative to detail energy = higher confidence
            double approxRatio = approxEnergy / totalEnergy;
            
            // Adjust for trend strength
            double trendFactor = Math.min(1.0, Math.abs(trend) / 2.0);
            
            // Penalize high volatility
            double volatilityPenalty = Math.max(0.0, 1.0 - volatility / 3.0);
            
            double confidence = (approxRatio * 0.4 + trendFactor * 0.4 + volatilityPenalty * 0.2);
            return Math.max(0.1, Math.min(0.95, confidence));
            
        } catch (Exception e) {
            logger.warn("Error calculating confidence, using default", e);
            return 0.5;
        }
    }
    
    /**
     * Signal strength classification.
     */
    public enum SignalStrength {
        VERY_STRONG("Very Strong"),
        STRONG("Strong"),
        MODERATE("Moderate"),
        WEAK("Weak"),
        NEUTRAL("Neutral");
        
        private final String displayName;
        
        SignalStrength(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
        
        @Override
        public String toString() { return displayName; }
    }
    
    @Override
    public String toString() {
        return String.format("TradingAnalysisResult{regime=%s, confidence=%.2f, trend=%.4f, volatility=%.2f, strength=%s}", 
                           regime, confidence, trendSignal, volatilityLevel, getSignalStrength());
    }
}