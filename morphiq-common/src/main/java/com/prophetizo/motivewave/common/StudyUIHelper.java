package com.prophetizo.motivewave.common;

import com.motivewave.platform.sdk.common.NVP;
import com.motivewave.platform.sdk.common.desc.DiscreteDescriptor;
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
    
    /**
     * Creates a DiscreteDescriptor for wavelet type selection.
     * This method encapsulates the common pattern of creating a wavelet type dropdown.
     * 
     * @param settingKey the setting key for the wavelet type
     * @param label the label to display (defaults to "Wavelet Type" if null)
     * @param defaultValue the default wavelet type
     * @param options the list of NVP options for the dropdown
     * @return DiscreteDescriptor configured for wavelet type selection
     */
    public static DiscreteDescriptor createWaveletTypeDescriptor(String settingKey, String label, 
            String defaultValue, List<NVP> options) {
        String displayLabel = (label != null) ? label : "Wavelet Type";
        return new DiscreteDescriptor(settingKey, displayLabel, defaultValue, options);
    }
    
    /**
     * Creates a DiscreteDescriptor for wavelet type selection with default label.
     * 
     * @param settingKey the setting key for the wavelet type
     * @param defaultValue the default wavelet type
     * @param options the list of NVP options for the dropdown
     * @return DiscreteDescriptor configured for wavelet type selection
     */
    public static DiscreteDescriptor createWaveletTypeDescriptor(String settingKey, 
            String defaultValue, List<NVP> options) {
        return createWaveletTypeDescriptor(settingKey, null, defaultValue, options);
    }
}