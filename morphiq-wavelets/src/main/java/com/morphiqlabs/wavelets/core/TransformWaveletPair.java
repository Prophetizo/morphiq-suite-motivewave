package com.morphiqlabs.wavelets.core;

import com.morphiqlabs.wavelet.api.TransformType;
import com.morphiqlabs.wavelet.api.WaveletName;

/**
 * Encapsulates a valid transform-wavelet pair for the Wavelets study.
 * This ensures only compatible combinations are used and provides
 * clear display names for the UI.
 */
public class TransformWaveletPair {
    private final TransformType transform;
    private final WaveletName wavelet;
    private final String displayName;
    
    /**
     * Create a new transform-wavelet pair
     * @param transform The transform type (MODWT or CWT)
     * @param wavelet The wavelet to use with this transform
     */
    public TransformWaveletPair(TransformType transform, WaveletName wavelet) {
        this.transform = transform;
        this.wavelet = wavelet;
        this.displayName = createDisplayName();
    }
    
    /**
     * Create a pair from a stored string value
     * Format: "TRANSFORM:WAVELET" e.g., "MODWT:DB4"
     */
    public static TransformWaveletPair fromString(String value) {
        if (value == null || !value.contains(":")) {
            // Default fallback
            return new TransformWaveletPair(TransformType.MODWT, WaveletName.DB4);
        }
        
        String[] parts = value.split(":");
        if (parts.length != 2) {
            return new TransformWaveletPair(TransformType.MODWT, WaveletName.DB4);
        }
        
        try {
            TransformType transform = TransformType.valueOf(parts[0].trim());
            WaveletName wavelet = WaveletName.valueOf(parts[1].trim());
            return new TransformWaveletPair(transform, wavelet);
        } catch (Exception e) {
            // Invalid format, return default
            return new TransformWaveletPair(TransformType.MODWT, WaveletName.DB4);
        }
    }
    
    /**
     * Get the string representation for storage
     * Format: "TRANSFORM:WAVELET"
     */
    public String toStorageString() {
        return transform.name() + ":" + wavelet.name();
    }
    
    /**
     * Create the display name for UI
     */
    private String createDisplayName() {
        String transformPrefix = transform == TransformType.CWT ? "CWT" : "MODWT";
        String waveletDisplay = getWaveletDisplayName(wavelet);
        return transformPrefix + ": " + waveletDisplay;
    }
    
    /**
     * Get user-friendly wavelet display name
     */
    private String getWaveletDisplayName(WaveletName wavelet) {
        // Use a simplified display name
        switch (wavelet) {
            case HAAR:
                return "Haar";
            case DB2:
                return "Daubechies 2";
            case DB4:
                return "Daubechies 4";
            case DB8:
                return "Daubechies 8";
            case SYM4:
                return "Symlet 4";
            case SYM8:
                return "Symlet 8";
            case COIF1:
                return "Coiflet 1";
            case COIF2:
                return "Coiflet 2";
            case COIF3:
                return "Coiflet 3";
            case MORLET:
                return "Morlet";
            case MEXICAN_HAT:
                return "Mexican Hat";
            case PAUL:
                return "Paul";
            default:
                return wavelet.getDisplayName();
        }
    }
    
    /**
     * Get the transform type
     */
    public TransformType getTransform() {
        return transform;
    }
    
    /**
     * Get the wavelet
     */
    public WaveletName getWavelet() {
        return wavelet;
    }
    
    /**
     * Get display name for UI dropdown
     */
    @Override
    public String toString() {
        return displayName;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TransformWaveletPair that = (TransformWaveletPair) obj;
        return transform == that.transform && wavelet == that.wavelet;
    }
    
    @Override
    public int hashCode() {
        return 31 * transform.hashCode() + wavelet.hashCode();
    }
}