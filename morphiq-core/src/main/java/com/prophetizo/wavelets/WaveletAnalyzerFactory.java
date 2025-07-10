package com.prophetizo.wavelets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for creating WaveletAnalyzer instances.
 * This centralizes the creation logic and makes it easier to manage
 * different wavelet types across the application.
 */
public class WaveletAnalyzerFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(WaveletAnalyzerFactory.class);
    
    /**
     * Private constructor to prevent instantiation.
     */
    private WaveletAnalyzerFactory() {
        // Factory class - no instantiation
    }
    
    /**
     * Creates a WaveletAnalyzer with the specified wavelet type.
     * 
     * @param type the wavelet type to use
     * @return a new WaveletAnalyzer instance
     * @throws IllegalArgumentException if type is null
     */
    public static WaveletAnalyzer create(WaveletType type) {
        if (type == null) {
            throw new IllegalArgumentException("Wavelet type cannot be null");
        }
        
        logger.debug("Creating WaveletAnalyzer with type: {}", type);
        return new WaveletAnalyzer(type.createWavelet());
    }
    
    /**
     * Creates a WaveletAnalyzer by parsing the wavelet type from a string.
     * 
     * @param typeName the name of the wavelet type
     * @return a new WaveletAnalyzer instance
     */
    public static WaveletAnalyzer create(String typeName) {
        WaveletType type = WaveletType.parse(typeName);
        logger.debug("Parsed wavelet type '{}' as {}", typeName, type);
        return create(type);
    }
    
    /**
     * Creates a WaveletAnalyzer with the default wavelet type (DAUBECHIES4).
     * 
     * @return a new WaveletAnalyzer instance with default wavelet
     */
    public static WaveletAnalyzer createDefault() {
        return create(WaveletType.DAUBECHIES4);
    }
    
    /**
     * Creates a WaveletAnalyzer optimized for the specified use case.
     * 
     * @param useCase the intended use case
     * @return a new WaveletAnalyzer instance with appropriate wavelet
     */
    public static WaveletAnalyzer createForUseCase(UseCase useCase) {
        if (useCase == null) {
            throw new IllegalArgumentException("Use case cannot be null");
        }
        WaveletType type = getRecommendedType(useCase);
        logger.debug("Creating WaveletAnalyzer for {} with recommended type: {}", useCase, type);
        return create(type);
    }
    
    /**
     * Gets the recommended wavelet type for a specific use case.
     * 
     * @param useCase the use case
     * @return the recommended wavelet type
     */
    private static WaveletType getRecommendedType(UseCase useCase) {
        switch (useCase) {
            case BREAKOUT_DETECTION:
            case HIGH_FREQUENCY_TRADING:
                return WaveletType.HAAR;
            case SCALPING:
                return WaveletType.DAUBECHIES2;
            case DAY_TRADING:
                return WaveletType.SYMLET4;
            case SWING_TRADING:
                return WaveletType.SYMLET8;
            case POSITION_TRADING:
            case TREND_FOLLOWING:
                return WaveletType.COIFLET3;
            case NOISE_REDUCTION:
                return WaveletType.DAUBECHIES6;
            case GENERAL_PURPOSE:
            default:
                return WaveletType.DAUBECHIES4;
        }
    }
    
    /**
     * Enumeration of common use cases for wavelet analysis.
     */
    public enum UseCase {
        BREAKOUT_DETECTION("Detecting price breakouts and regime changes"),
        HIGH_FREQUENCY_TRADING("Ultra-fast signal processing for HFT"),
        SCALPING("Quick in-and-out trades on small movements"),
        DAY_TRADING("Intraday trading with balanced analysis"),
        SWING_TRADING("Multi-day position trading"),
        POSITION_TRADING("Long-term trend following"),
        TREND_FOLLOWING("Smooth trend extraction"),
        NOISE_REDUCTION("Removing market noise from signals"),
        GENERAL_PURPOSE("General market analysis");
        
        private final String description;
        
        UseCase(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}