package com.prophetizo.wavelets.trading;

import ai.prophetizo.financial.FinancialAnalyzer;
import ai.prophetizo.financial.FinancialAnalysisConfig;
import ai.prophetizo.wavelet.api.BoundaryMode;
import ai.prophetizo.wavelet.api.Wavelet;
import ai.prophetizo.wavelet.modwt.MODWTTransformFactory;
import ai.prophetizo.wavelet.modwt.MultiLevelMODWTResult;
import ai.prophetizo.wavelet.modwt.MultiLevelMODWTTransform;
import com.prophetizo.LoggerConfig;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Service class for trading-focused wavelet analysis using VectorWave's financial capabilities.
 * Replaces the old procedural WaveletAnalyzer with rich domain objects and financial analysis.
 */
public class TradingWaveletAnalyzer {
    private static final Logger logger = LoggerConfig.getLogger(TradingWaveletAnalyzer.class);
    
    private final MultiLevelMODWTTransform modwtTransform;
    private final FinancialAnalyzer financialAnalyzer;
    private final Wavelet wavelet;
    private final BoundaryMode boundaryMode;
    
    // Configuration
    private static final double ANOMALY_THRESHOLD = 2.5; // Standard deviations
    private static final double HIGH_VOLATILITY_THRESHOLD = 1.5;
    private static final double TREND_SIGNIFICANCE_THRESHOLD = 0.1;
    
    /**
     * Constructor with VectorWave integration.
     */
    public TradingWaveletAnalyzer(Wavelet wavelet, BoundaryMode boundaryMode) {
        this.wavelet = wavelet;
        this.boundaryMode = boundaryMode;
        this.modwtTransform = MODWTTransformFactory.createMultiLevel(wavelet, boundaryMode);
        this.financialAnalyzer = createFinancialAnalyzer(wavelet);
        
        logger.info("Initialized TradingWaveletAnalyzer with wavelet: {}, boundary: {}", 
                   wavelet.getClass().getSimpleName(), boundaryMode);
    }
    
    /**
     * Perform comprehensive trading analysis on price data.
     * This is the main method that replaces the old performForwardMODWT.
     */
    public TradingAnalysisResult analyzePriceAction(double[] prices, int levels) {
        if (prices == null || prices.length == 0) {
            logger.warn("Empty or null price data provided");
            return createEmptyResult();
        }
        
        if (levels <= 0 || levels > 10) {
            logger.warn("Invalid decomposition levels: {}, using default 5", levels);
            levels = 5;
        }
        
        try {
            logger.debug("Analyzing {} price points with {} decomposition levels", prices.length, levels);
            
            // 1. VectorWave financial analysis
            double trendSignal = financialAnalyzer.analyzeRegimeTrend(prices);
            double volatilityLevel = financialAnalyzer.analyzeVolatility(prices);
            boolean anomalyDetected = financialAnalyzer.detectAnomalies(prices);
            
            // 2. MODWT decomposition
            MultiLevelMODWTResult modwtResult = modwtTransform.decompose(prices, levels);
            
            // 3. Additional analysis
            List<String> anomalies = detectSpecificAnomalies(prices, modwtResult);
            MarketRegime regime = determineMarketRegime(trendSignal, volatilityLevel, anomalyDetected, modwtResult);
            double confidence = calculateAnalysisConfidence(trendSignal, volatilityLevel, modwtResult);
            
            TradingAnalysisResult result = new TradingAnalysisResult(
                trendSignal, volatilityLevel, anomalyDetected, modwtResult, 
                regime, confidence, anomalies
            );
            
            logger.debug("Analysis completed - Regime: {}, Confidence: {:.2f}", regime, confidence);
            return result;
            
        } catch (Exception e) {
            logger.error("Error during price action analysis", e);
            return createErrorResult(e);
        }
    }
    
    /**
     * Simplified analysis for legacy compatibility.
     * Returns the detail coefficients in the expected format.
     */
    public double[][] performForwardMODWT(double[] prices, int levels) {
        try {
            MultiLevelMODWTResult result = modwtTransform.decompose(prices, levels);
            return convertToLegacyFormat(result);
        } catch (Exception e) {
            logger.error("Error in legacy MODWT analysis", e);
            return new double[levels][prices.length]; // Return zeros on error
        }
    }
    
    /**
     * Extract approximation (smooth trend) signal.
     * This replaces the old getApproximation method with VectorWave's capabilities.
     */
    public double[] extractApproximation(double[] prices, int levels) {
        try {
            MultiLevelMODWTResult result = modwtTransform.decompose(prices, levels);
            
            // Reconstruct using only approximation coefficients
            // This gives us the smoothed trend component
            return modwtTransform.reconstructFromLevel(result, levels);
            
        } catch (Exception e) {
            logger.error("Error extracting approximation", e);
            return prices.clone(); // Return original on error
        }
    }
    
    /**
     * Real-time analysis for streaming data.
     * Optimized for repeated calls with new data points.
     */
    public TradingAnalysisResult analyzeStreamingPrice(double[] recentPrices, int levels, 
                                                      TradingAnalysisResult previousResult) {
        // For now, perform full analysis
        // TODO: Implement incremental analysis using VectorWave's streaming capabilities
        return analyzePriceAction(recentPrices, levels);
    }
    
    /**
     * Get optimal decomposition levels based on data length and trading style.
     */
    public int getOptimalLevels(int dataLength, TradingStyle tradingStyle) {
        // VectorWave's built-in calculation
        int maxPossible = modwtTransform.getMaximumLevels(dataLength);
        
        // Adjust based on trading style
        int styleOptimal = tradingStyle.getOptimalLevels(60); // Assume 1-hour default
        
        return Math.min(maxPossible, styleOptimal);
    }
    
    /**
     * Validate that the analyzer is properly configured.
     */
    public boolean isValidConfiguration() {
        try {
            // Test with small sample
            double[] testData = {100.0, 101.0, 100.5, 102.0, 101.5};
            MultiLevelMODWTResult result = modwtTransform.decompose(testData, 2);
            return result != null && result.isValid();
        } catch (Exception e) {
            logger.error("Configuration validation failed", e);
            return false;
        }
    }
    
    // Private helper methods
    
    private FinancialAnalyzer createFinancialAnalyzer(Wavelet wavelet) {
        try {
            // Create financial analysis configuration with available methods
            FinancialAnalysisConfig config = new FinancialAnalysisConfig.Builder()
                    .crashAsymmetryThreshold(2.0)
                    .volatilityLowThreshold(0.5)
                    .volatilityHighThreshold(2.0)
                    .regimeTrendThreshold(0.1)
                    .anomalyDetectionThreshold(2.5)
                    .windowSize(50)
                    .confidenceLevel(0.95)
                    .build();
            
            return new FinancialAnalyzer(config);
            
        } catch (Exception e) {
            logger.error("Failed to create FinancialAnalyzer, using default configuration", e);
            // Fallback to default configuration
            return new FinancialAnalyzer(new FinancialAnalysisConfig.Builder().build());
        }
    }
    
    private MarketRegime determineMarketRegime(double trend, double volatility, boolean anomaly, 
                                             MultiLevelMODWTResult modwtResult) {
        // Use VectorWave's energy analysis for regime detection
        if (anomaly && volatility > 3.0) {
            return MarketRegime.CRISIS;
        }
        
        if (anomaly || isBreakoutPattern(modwtResult)) {
            return MarketRegime.BREAKOUT;
        }
        
        double absTrend = Math.abs(trend);
        
        if (absTrend > TREND_SIGNIFICANCE_THRESHOLD) {
            // Check for regime change indicators
            if (isReversalPattern(modwtResult, trend)) {
                return MarketRegime.REVERSAL;
            }
            return trend > 0 ? MarketRegime.TRENDING_BULL : MarketRegime.TRENDING_BEAR;
        } else {
            return volatility > HIGH_VOLATILITY_THRESHOLD ? 
                   MarketRegime.RANGING_HIGH_VOL : MarketRegime.RANGING_LOW_VOL;
        }
    }
    
    private boolean isBreakoutPattern(MultiLevelMODWTResult result) {
        try {
            // Look for sudden energy spike in high-frequency components
            double[] energies = result.getRelativeEnergyDistribution();
            if (energies.length < 2) return false;
            
            // Breakout typically shows high energy in D1/D2 levels
            return energies[0] > 0.3 || energies[1] > 0.25;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean isReversalPattern(MultiLevelMODWTResult result, double currentTrend) {
        try {
            // Look for conflicting signals between different frequency components
            // This is a simplified heuristic - could be enhanced with more sophisticated analysis
            double[] energies = result.getRelativeEnergyDistribution();
            if (energies.length < 3) return false;
            
            // Reversal might show mixed energy distribution
            double highFreqEnergy = energies[0] + energies[1];
            double lowFreqEnergy = energies[energies.length - 1];
            
            return highFreqEnergy > 0.4 && lowFreqEnergy > 0.3;
        } catch (Exception e) {
            return false;
        }
    }
    
    private List<String> detectSpecificAnomalies(double[] prices, MultiLevelMODWTResult result) {
        List<String> anomalies = new ArrayList<>();
        
        try {
            // Detect various types of anomalies using wavelet analysis
            if (detectGaps(prices)) {
                anomalies.add("Price gap detected");
            }
            
            if (detectSpikes(result)) {
                anomalies.add("Coefficient spike detected");
            }
            
            if (detectUnusualVolatility(result)) {
                anomalies.add("Unusual volatility pattern");
            }
            
        } catch (Exception e) {
            logger.warn("Error detecting specific anomalies", e);
        }
        
        return anomalies;
    }
    
    private boolean detectGaps(double[] prices) {
        if (prices.length < 2) return false;
        
        double avgChange = 0.0;
        for (int i = 1; i < prices.length; i++) {
            avgChange += Math.abs(prices[i] - prices[i-1]);
        }
        avgChange /= (prices.length - 1);
        
        // Look for changes significantly larger than average
        for (int i = 1; i < prices.length; i++) {
            if (Math.abs(prices[i] - prices[i-1]) > avgChange * 5) {
                return true;
            }
        }
        return false;
    }
    
    private boolean detectSpikes(MultiLevelMODWTResult result) {
        try {
            // Check for unusual coefficient values in high-frequency components
            // VectorWave uses 1-based indexing for levels
            double[] d1Coeffs = result.getDetailCoeffsAtLevel(1);
            if (d1Coeffs == null || d1Coeffs.length == 0) return false;
            
            double mean = 0.0;
            for (double coeff : d1Coeffs) {
                mean += Math.abs(coeff);
            }
            mean /= d1Coeffs.length;
            
            for (double coeff : d1Coeffs) {
                if (Math.abs(coeff) > mean * ANOMALY_THRESHOLD) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean detectUnusualVolatility(MultiLevelMODWTResult result) {
        try {
            double[] energies = result.getRelativeEnergyDistribution();
            if (energies.length < 2) return false;
            
            // Unusual volatility might show very high energy in specific bands
            for (double energy : energies) {
                if (energy > 0.6) { // More than 60% energy in one band is unusual
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    private double calculateAnalysisConfidence(double trend, double volatility, MultiLevelMODWTResult result) {
        try {
            // Base confidence on signal consistency and energy distribution
            double baseConfidence = 0.5;
            
            // Higher trend strength increases confidence
            double trendFactor = Math.min(1.0, Math.abs(trend) / 2.0);
            
            // Moderate volatility is optimal (too low or too high reduces confidence)
            double volFactor = volatility > 0.5 && volatility < 2.0 ? 1.0 : 0.7;
            
            // Energy concentration in approximation suggests reliable signal
            double approxEnergy = result.getApproximationEnergy();
            double totalEnergy = result.getTotalEnergy();
            double energyFactor = totalEnergy > 0 ? Math.min(1.0, approxEnergy / totalEnergy * 2) : 0.5;
            
            double confidence = baseConfidence + 
                              (trendFactor * 0.3) + 
                              (volFactor * 0.1) + 
                              (energyFactor * 0.1);
            
            return Math.max(0.1, Math.min(0.95, confidence));
            
        } catch (Exception e) {
            logger.warn("Error calculating confidence, using default", e);
            return 0.5;
        }
    }
    
    private double[][] convertToLegacyFormat(MultiLevelMODWTResult result) {
        try {
            int levels = result.getLevels();
            int signalLength = result.getSignalLength();
            double[][] coeffMatrix = new double[levels][signalLength];
            
            for (int level = 0; level < levels; level++) {
                // VectorWave uses 1-based indexing for levels
                double[] coeffs = result.getDetailCoeffsAtLevel(level + 1);
                if (coeffs != null && coeffs.length == signalLength) {
                    System.arraycopy(coeffs, 0, coeffMatrix[level], 0, signalLength);
                }
            }
            
            return coeffMatrix;
        } catch (Exception e) {
            logger.error("Error converting to legacy format", e);
            return new double[0][0];
        }
    }
    
    private TradingAnalysisResult createEmptyResult() {
        return new TradingAnalysisResult(0.0, 0.0, false, null, MarketRegime.UNKNOWN, 0.0, List.of());
    }
    
    private TradingAnalysisResult createErrorResult(Exception e) {
        return new TradingAnalysisResult(0.0, 0.0, true, null, MarketRegime.UNKNOWN, 0.0, 
                                       List.of("Analysis error: " + e.getMessage()));
    }
    
    // Getters for configuration info
    public Wavelet getWavelet() { return wavelet; }
    public BoundaryMode getBoundaryMode() { return boundaryMode; }
}