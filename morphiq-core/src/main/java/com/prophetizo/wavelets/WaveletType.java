package com.prophetizo.wavelets;

import jwave.transforms.wavelets.Wavelet;
import jwave.transforms.wavelets.daubechies.Daubechies4;
import jwave.transforms.wavelets.daubechies.Daubechies6;
import jwave.transforms.wavelets.haar.Haar1;
import jwave.transforms.wavelets.daubechies.Daubechies2;
import jwave.transforms.wavelets.symlets.Symlet4;
import jwave.transforms.wavelets.symlets.Symlet8;
import jwave.transforms.wavelets.coiflet.Coiflet3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enumeration of available wavelet types for signal processing.
 * This enum provides a centralized way to manage different wavelet implementations
 * and their properties.
 */
public enum WaveletType {
    // Existing wavelets
    DAUBECHIES4("Daubechies4", "General purpose, 4 vanishing moments"),
    DAUBECHIES6("Daubechies6", "Smoother than DB4, 6 vanishing moments"),
    
    // New wavelets for expanded capabilities
    HAAR("Haar", "Simplest wavelet, excellent for breakout detection"),
    DAUBECHIES2("Daubechies2", "Fast response, good for HFT"),
    SYMLET4("Symlet4", "Optimized for day trading with minimal phase distortion and fast response, suitable for trend analysis"),
    SYMLET8("Symlet8", "Better trend preservation than Sym4"),
    COIFLET3("Coiflet3", "Superior trend smoothing, 3 vanishing moments");

    private static final Logger logger = LoggerFactory.getLogger(WaveletType.class);
    
    private final String displayName;
    private final String description;

    WaveletType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Gets the display name for this wavelet type.
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets a description of this wavelet's characteristics.
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Creates a JWave Wavelet instance for this type.
     * @return a new Wavelet instance
     */
    public Wavelet createWavelet() {
        switch (this) {
            case HAAR:
                return new Haar1();
            case DAUBECHIES2:
                return new Daubechies2();
            case DAUBECHIES4:
                return new Daubechies4();
            case DAUBECHIES6:
                return new Daubechies6();
            case SYMLET4:
                return new Symlet4();
            case SYMLET8:
                return new Symlet8();
            case COIFLET3:
                return new Coiflet3();
            default:
                logger.warn("Unknown wavelet type: {}, defaulting to Daubechies4", this);
                return new Daubechies4();
        }
    }

    /**
     * Parses a wavelet type from a string representation.
     * @param waveletTypeStr the string to parse
     * @return the corresponding WaveletType, or DAUBECHIES4 if parsing fails
     */
    public static WaveletType parse(String waveletTypeStr) {
        if (waveletTypeStr == null || waveletTypeStr.trim().isEmpty()) {
            logger.warn("Empty wavelet type string, using default DAUBECHIES4");
            return DAUBECHIES4;
        }
        
        // Try exact match first
        for (WaveletType type : values()) {
            if (type.displayName.equals(waveletTypeStr)) {
                return type;
            }
        }
        
        // Try enum name match (case insensitive)
        try {
            return valueOf(waveletTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown wavelet type '{}', using default DAUBECHIES4", waveletTypeStr);
            return DAUBECHIES4;
        }
    }

    /**
     * Gets recommended use cases for this wavelet type.
     * @return recommended use cases
     */
    public String getRecommendedUse() {
        switch (this) {
            case HAAR:
                return "Breakout detection, regime changes, HFT";
            case DAUBECHIES2:
                return "High-frequency trading, scalping, tick data";
            case DAUBECHIES4:
                return "General purpose, day trading, balanced analysis";
            case DAUBECHIES6:
                return "Swing trading, smoother trends, noise reduction";
            case SYMLET4:
                return "Day trading, momentum detection, minimal phase distortion";
            case SYMLET8:
                return "Swing trading, trend following, pattern recognition";
            case COIFLET3:
                return "Position trading, smooth trends, volatility analysis";
            default:
                return "General purpose analysis";
        }
    }

    /**
     * Gets the computational complexity relative to Haar (1.0).
     * @return relative computational cost
     */
    public double getComputationalCost() {
        switch (this) {
            case HAAR:
                return 1.0;
            case DAUBECHIES2:
                return 1.5;
            case DAUBECHIES4:
                return 2.0;
            case DAUBECHIES6:
                return 2.5;
            case SYMLET4:
                return 2.2;
            case SYMLET8:
                return 3.0;
            case COIFLET3:
                return 2.8;
            default:
                return 2.0;
        }
    }
}