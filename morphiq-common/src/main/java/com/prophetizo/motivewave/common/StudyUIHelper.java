package com.prophetizo.motivewave.common;

import com.motivewave.platform.sdk.common.NVP;
import com.prophetizo.wavelets.WaveletType;
import com.prophetizo.wavelets.WaveletOptionsProvider;
import com.prophetizo.wavelets.WaveletAnalyzerFactory;
import java.util.List;
import java.util.ArrayList;

/**
 * UI helper for MotiveWave studies.
 * This class bridges the gap between the core wavelet functionality
 * and the MotiveWave-specific UI components.
 */
public class StudyUIHelper {
    
    /**
     * Private constructor to prevent instantiation.
     */
    private StudyUIHelper() {
        // Utility class
    }
    
    /**
     * Creates NVP list for all available wavelets.
     * 
     * @return List of NVP objects for wavelet selection dropdown
     */
    public static List<NVP> createWaveletOptions() {
        List<NVP> options = new ArrayList<>();
        for (WaveletType type : WaveletOptionsProvider.getAllWavelets()) {
            options.add(new NVP(type.getDisplayName(), type.getDisplayName()));
        }
        return options;
    }
    
    /**
     * Creates NVP list for wavelets suitable for a specific use case.
     * 
     * @param useCase the trading use case
     * @return List of NVP objects for wavelet selection dropdown
     */
    public static List<NVP> createWaveletOptionsForUseCase(WaveletAnalyzerFactory.UseCase useCase) {
        List<NVP> options = new ArrayList<>();
        for (WaveletType type : WaveletOptionsProvider.getWaveletsForUseCase(useCase)) {
            options.add(new NVP(type.getDisplayName(), type.getDisplayName()));
        }
        return options;
    }
}