package com.prophetizo.wavelets;

import jwave.transforms.wavelets.continuous.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enumeration of available continuous wavelet types for time-frequency analysis.
 * Continuous wavelets are used for analyzing non-stationary signals where
 * both time and frequency information are important.
 */
public enum ContinuousWaveletType {
    // Morlet family - excellent for time-frequency localization
    MORLET("Morlet", "Complex wavelet for precise time-frequency analysis", 
           "Intraday cycles, market rhythm detection"),
    
    // Mexican Hat (Ricker) - 2nd derivative of Gaussian
    MEXICAN_HAT("Mexican Hat", "Edge detection and volatility spikes", 
                "Breakout detection, volatility clustering"),
    
    // Derivative of Gaussian  
    DOG("DOG", "Derivative of Gaussian wavelet",
        "Support/resistance, price extremes"),
    
    // Meyer wavelet - smooth in frequency domain
    MEYER("Meyer", "Smooth frequency response for clean decomposition",
          "Long-term trend extraction, cycle analysis"),
    
    // Paul wavelet - complex analytic wavelet
    PAUL("Paul", "Complex wavelet with good frequency resolution",
         "Phase analysis, lead-lag relationships");

    private static final Logger logger = LoggerFactory.getLogger(ContinuousWaveletType.class);
    
    private final String displayName;
    private final String description;
    private final String financialUse;

    ContinuousWaveletType(String displayName, String description, String financialUse) {
        this.displayName = displayName;
        this.description = description;
        this.financialUse = financialUse;
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
    
    /**
     * Gets the financial/trading use cases for this wavelet.
     * @return the financial use cases
     */
    public String getFinancialUse() {
        return financialUse;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Creates a JWave ContinuousWavelet instance for this type.
     * @return a new ContinuousWavelet instance
     * @throws UnsupportedOperationException if wavelet creation fails
     */
    public ContinuousWavelet createWavelet() {
        try {
            switch (this) {
                case MORLET:
                    // Default Morlet with center frequency = 6, bandwidth = 1
                    return new MorletWavelet(6.0, 1.0);
                    
                case MEXICAN_HAT:
                    return new MexicanHatWavelet();
                    
                case DOG:
                    // Default DOGWavelet
                    return new DOGWavelet();
                    
                case MEYER:
                    return new MeyerWavelet();
                    
                case PAUL:
                    // Default Paul wavelet
                    return new PaulWavelet();
                    
                default:
                    logger.warn("Unknown continuous wavelet type: {}, defaulting to Morlet", this);
                    return new MorletWavelet(6.0, 1.0);
            }
        } catch (Exception e) {
            logger.error("Failed to create continuous wavelet for type: " + this, e);
            throw new UnsupportedOperationException("Failed to create wavelet: " + this, e);
        }
    }
    
    /**
     * Gets recommended scales for this wavelet type in financial context.
     * @param samplingPeriod the sampling period in minutes (e.g., 1, 5, 15)
     * @return array of recommended scales
     */
    public double[] getRecommendedScales(int samplingPeriod) {
        switch (this) {
            case MORLET:
                // Scales from minutes to days for different cycles
                return generateLogScales(samplingPeriod, samplingPeriod * 390); // intraday to daily
                
            case MEXICAN_HAT:
            case DOG:
                // Shorter scales for spike detection
                return generateLogScales(samplingPeriod, samplingPeriod * 60); // up to hourly
                
            case MEYER:
                // Longer scales for smooth trends
                return generateLogScales(samplingPeriod * 30, samplingPeriod * 780); // 30 min to 2 days
                
            case PAUL:
                // Full range for phase analysis
                return generateLogScales(samplingPeriod, samplingPeriod * 780); // full spectrum
                
            default:
                return generateLogScales(samplingPeriod, samplingPeriod * 390);
        }
    }
    
    /**
     * Generates logarithmically spaced scales.
     * @param minScale minimum scale
     * @param maxScale maximum scale
     * @return array of scales
     */
    private double[] generateLogScales(double minScale, double maxScale) {
        int numScales = 32; // Good balance between resolution and computation
        double[] scales = new double[numScales];
        double logMin = Math.log(minScale);
        double logMax = Math.log(maxScale);
        double step = (logMax - logMin) / (numScales - 1);
        
        for (int i = 0; i < numScales; i++) {
            scales[i] = Math.exp(logMin + i * step);
        }
        return scales;
    }
    
    /**
     * Parses a continuous wavelet type from a string representation.
     * @param waveletTypeStr the string to parse
     * @return the corresponding ContinuousWaveletType, or MORLET if parsing fails
     */
    public static ContinuousWaveletType parse(String waveletTypeStr) {
        if (waveletTypeStr == null || waveletTypeStr.trim().isEmpty()) {
            logger.warn("Empty continuous wavelet type string, using default MORLET");
            return MORLET;
        }
        
        // Try exact match first
        for (ContinuousWaveletType type : values()) {
            if (type.displayName.equalsIgnoreCase(waveletTypeStr)) {
                return type;
            }
        }
        
        // Try enum name match (case insensitive)
        try {
            return valueOf(waveletTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown continuous wavelet type '{}', using default MORLET", waveletTypeStr);
            return MORLET;
        }
    }
}