package com.prophetizo.wavelets;

import java.util.List;
import java.util.ArrayList;

/**
 * Provider for wavelet options that can be used by UI components.
 * This class provides wavelet selection logic without depending on UI-specific classes.
 */
public class WaveletOptionsProvider {
    
    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private WaveletOptionsProvider() {
        // Utility class - no instantiation
    }
    
    /**
     * Gets all available wavelet types.
     * 
     * @return List of all WaveletType values
     */
    public static List<WaveletType> getAllWavelets() {
        List<WaveletType> wavelets = new ArrayList<>();
        for (WaveletType type : WaveletType.values()) {
            wavelets.add(type);
        }
        return wavelets;
    }
    
    /**
     * Gets wavelets suitable for a specific use case.
     * 
     * @param useCase the use case to filter wavelets for
     * @return List of WaveletType values suitable for the given use case
     */
    public static List<WaveletType> getWaveletsForUseCase(WaveletAnalyzerFactory.UseCase useCase) {
        List<WaveletType> wavelets = new ArrayList<>();
        
        switch (useCase) {
            case BREAKOUT_DETECTION:
            case HIGH_FREQUENCY_TRADING:
                wavelets.add(WaveletType.HAAR);
                wavelets.add(WaveletType.DAUBECHIES2);
                break;
            case SCALPING:
                wavelets.add(WaveletType.DAUBECHIES2);
                wavelets.add(WaveletType.HAAR);
                break;
            case DAY_TRADING:
                wavelets.add(WaveletType.SYMLET4);
                wavelets.add(WaveletType.DAUBECHIES4);
                break;
            case SWING_TRADING:
                wavelets.add(WaveletType.SYMLET8);
                wavelets.add(WaveletType.DAUBECHIES6);
                break;
            case POSITION_TRADING:
            case TREND_FOLLOWING:
                wavelets.add(WaveletType.COIFLET3);
                wavelets.add(WaveletType.SYMLET8);
                wavelets.add(WaveletType.DAUBECHIES6);
                break;
            case NOISE_REDUCTION:
                wavelets.add(WaveletType.DAUBECHIES6);
                wavelets.add(WaveletType.COIFLET3);
                wavelets.add(WaveletType.SYMLET8);
                break;
            case GENERAL_PURPOSE:
            default:
                return getAllWavelets();
        }
        
        return wavelets;
    }
    
    /**
     * Gets wavelets within a computational cost limit.
     * 
     * @param maxComputationalCost maximum acceptable computational cost (1.0 = Haar baseline)
     * @return List of WaveletType values within the computational budget
     */
    public static List<WaveletType> getWaveletsByComputationalCost(double maxComputationalCost) {
        List<WaveletType> wavelets = new ArrayList<>();
        for (WaveletType type : WaveletType.values()) {
            if (type.getComputationalCost() <= maxComputationalCost) {
                wavelets.add(type);
            }
        }
        return wavelets;
    }
    
    /**
     * Gets the default wavelet type for a given use case.
     * 
     * @param useCase the trading use case
     * @return the recommended default WaveletType
     */
    public static WaveletType getDefaultWaveletForUseCase(WaveletAnalyzerFactory.UseCase useCase) {
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
}