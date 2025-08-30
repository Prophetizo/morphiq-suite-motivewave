package com.morphiqlabs.wavelets.core;

import com.morphiqlabs.wavelet.api.ContinuousWavelet;
import com.morphiqlabs.wavelet.api.Wavelet;
import com.morphiqlabs.wavelet.api.WaveletName;
import com.morphiqlabs.wavelet.api.WaveletRegistry;
import com.morphiqlabs.wavelet.cwt.CWTConfig;
import com.morphiqlabs.wavelet.cwt.CWTResult;
import com.morphiqlabs.wavelet.cwt.CWTTransform;
import com.morphiqlabs.wavelet.cwt.ScaleSpace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter for VectorWave Continuous Wavelet Transform (CWT) implementation.
 * Provides a bridge between MotiveWave indicators and VectorWave CWT library.
 * 
 * @author Stephen Campbell
 */
public class VectorWaveCwtAdapter {
    private static final Logger logger = LoggerFactory.getLogger(VectorWaveCwtAdapter.class);
    
    // Cache for CWT transforms to avoid recreation
    private final Map<WaveletName, CWTTransform> transformCache = new HashMap<>();
    
    // Cache for continuous wavelets
    private final Map<WaveletName, ContinuousWavelet> waveletCache = new HashMap<>();
    
    // Current configuration
    private CWTConfig config;
    private WaveletName currentWaveletName;
    private CWTTransform currentTransform;
    
    /**
     * Create a new CWT adapter with default configuration
     */
    public VectorWaveCwtAdapter() {
        this(createDefaultConfig());
    }
    
    /**
     * Create a new CWT adapter with custom configuration
     * @param config CWT configuration
     */
    public VectorWaveCwtAdapter(CWTConfig config) {
        this.config = config;
    }
    
    /**
     * Create default CWT configuration optimized for financial data
     */
    private static CWTConfig createDefaultConfig() {
        return CWTConfig.builder()
            .paddingStrategy(CWTConfig.PaddingStrategy.SYMMETRIC)
            .normalizeScales(true)
            .enableFFT(true)
            .build();
    }
    
    /**
     * Perform CWT analysis on input data
     * 
     * @param data Input signal data
     * @param waveletName The wavelet to use
     * @param scales Array of scales for the transform
     * @return CWT result containing coefficients and analysis
     */
    public CWTResult analyze(double[] data, WaveletName waveletName, double[] scales) {
        if (data == null || data.length == 0) {
            logger.error("Input data is null or empty");
            return null;
        }
        
        if (scales == null || scales.length == 0) {
            logger.error("Scales array is null or empty");
            return null;
        }
        
        try {
            // Get or create transform for this wavelet
            CWTTransform transform = getOrCreateTransform(waveletName);
            if (transform == null) {
                logger.error("Failed to create CWT transform for wavelet: {}", waveletName);
                return null;
            }
            
            // Perform the CWT analysis
            CWTResult result = transform.analyze(data, scales);
            
            if (result == null) {
                logger.error("CWT analysis returned null result");
                return null;
            }
            
            if (logger.isDebugEnabled()) {
                logger.debug("CWT analysis completed: {} scales, {} samples", 
                    result.getNumScales(), result.getNumSamples());
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error performing CWT analysis", e);
            return null;
        }
    }
    
    /**
     * Perform CWT analysis with automatic scale selection
     * 
     * @param data Input signal data
     * @param waveletName The wavelet to use
     * @param numScales Number of scales to generate
     * @return CWT result containing coefficients and analysis
     */
    public CWTResult analyzeWithAutoScales(double[] data, WaveletName waveletName, int numScales) {
        if (data == null || data.length == 0) {
            logger.error("Input data is null or empty");
            return null;
        }
        
        // Generate scales automatically based on data length
        double[] scales = generateScales(data.length, numScales);
        
        return analyze(data, waveletName, scales);
    }
    
    /**
     * Generate automatic scales for CWT based on signal length
     * Uses dyadic (powers of 2) scaling which is common for financial data
     * 
     * @param signalLength Length of the input signal
     * @param numScales Number of scales to generate
     * @return Array of scales
     */
    public double[] generateScales(int signalLength, int numScales) {
        double[] scales = new double[numScales];
        
        // Use dyadic scaling: scales are powers of 2
        // Start from scale 1 and go up to approximately signal_length/4
        double minScale = 1.0;
        double maxScale = Math.min(signalLength / 4.0, 128.0); // Cap at 128 for performance
        
        // Handle special case when only 1 scale is requested
        if (numScales == 1) {
            // Use a middle scale for single level analysis
            scales[0] = Math.sqrt(minScale * maxScale);  // Geometric mean
            if (logger.isDebugEnabled()) {
                logger.debug("Generated single scale: {}", scales[0]);
            }
            return scales;
        }
        
        // Generate logarithmically spaced scales for multiple levels
        double logMin = Math.log(minScale);
        double logMax = Math.log(maxScale);
        double logStep = (logMax - logMin) / (numScales - 1);
        
        for (int i = 0; i < numScales; i++) {
            scales[i] = Math.exp(logMin + i * logStep);
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("Generated {} scales from {} to {}", 
                numScales, scales[0], scales[numScales - 1]);
        }
        
        return scales;
    }
    
    /**
     * Get or create a CWT transform for the specified wavelet
     */
    private CWTTransform getOrCreateTransform(WaveletName waveletName) {
        // Check if we already have this transform cached
        if (waveletName.equals(currentWaveletName) && currentTransform != null) {
            return currentTransform;
        }
        
        // Check the transform cache
        CWTTransform cached = transformCache.get(waveletName);
        if (cached != null) {
            currentWaveletName = waveletName;
            currentTransform = cached;
            return cached;
        }
        
        // Create new transform
        ContinuousWavelet wavelet = getOrCreateContinuousWavelet(waveletName);
        if (wavelet == null) {
            return null;
        }
        
        CWTTransform transform = new CWTTransform(wavelet, config);
        
        // Cache it
        transformCache.put(waveletName, transform);
        currentWaveletName = waveletName;
        currentTransform = transform;
        
        logger.info("Created new CWT transform for wavelet: {}", waveletName);
        
        return transform;
    }
    
    /**
     * Get or create a continuous wavelet
     */
    private ContinuousWavelet getOrCreateContinuousWavelet(WaveletName waveletName) {
        // Check cache first
        ContinuousWavelet cached = waveletCache.get(waveletName);
        if (cached != null) {
            return cached;
        }
        
        try {
            // Get wavelet from registry
            Wavelet wavelet = WaveletRegistry.getWavelet(waveletName);
            
            if (!(wavelet instanceof ContinuousWavelet)) {
                logger.error("Wavelet {} is not a continuous wavelet", waveletName);
                return null;
            }
            
            ContinuousWavelet continuous = (ContinuousWavelet) wavelet;
            
            // Cache it
            waveletCache.put(waveletName, continuous);
            
            if (logger.isDebugEnabled()) {
                logger.debug("Created continuous wavelet: {}", waveletName);
            }
            
            return continuous;
            
        } catch (Exception e) {
            logger.error("Failed to create continuous wavelet: {}", waveletName, e);
            return null;
        }
    }
    
    /**
     * Extract decomposition levels from CWT result for compatibility with MODWT display
     * Maps CWT scales to pseudo-levels for visualization
     * 
     * @param result CWT result
     * @param numLevels Number of levels to extract
     * @return Array of decomposition levels (time series for each scale)
     */
    public double[][] extractLevels(CWTResult result, int numLevels) {
        if (result == null) {
            return null;
        }
        
        int numSamples = result.getNumSamples();
        int numScales = result.getNumScales();
        
        // Limit levels to available scales
        int actualLevels = Math.min(numLevels, numScales);
        
        double[][] levels = new double[actualLevels][numSamples];
        double[][] coefficients = result.getCoefficients();
        
        // Map scales to levels and normalize
        // We'll select scales at exponentially increasing intervals
        for (int level = 0; level < actualLevels; level++) {
            // Map level to scale index
            int scaleIndex = mapLevelToScale(level, actualLevels, numScales);
            
            // Extract the coefficients for this scale
            double[] scaleCoeffs = coefficients[scaleIndex];
            
            // Find the standard deviation for normalization
            double mean = 0.0;
            double maxAbs = 0.0;
            for (int i = 0; i < numSamples; i++) {
                mean += scaleCoeffs[i];
                maxAbs = Math.max(maxAbs, Math.abs(scaleCoeffs[i]));
            }
            mean /= numSamples;
            
            // Calculate standard deviation
            double variance = 0.0;
            for (int i = 0; i < numSamples; i++) {
                double diff = scaleCoeffs[i] - mean;
                variance += diff * diff;
            }
            double stdDev = Math.sqrt(variance / numSamples);
            
            // Normalize coefficients by standard deviation if it's significant
            // This brings CWT coefficients to a similar scale as MODWT
            if (stdDev > 1e-10) {
                for (int i = 0; i < numSamples; i++) {
                    levels[level][i] = (scaleCoeffs[i] - mean) / stdDev;
                }
            } else {
                // If stdDev is too small, just center the data
                for (int i = 0; i < numSamples; i++) {
                    levels[level][i] = scaleCoeffs[i] - mean;
                }
            }
            
            if (logger.isDebugEnabled()) {
                logger.debug("CWT Level {} (scale {}): mean={}, stdDev={}, maxAbs={}", 
                           level, scaleIndex, mean, stdDev, maxAbs);
            }
        }
        
        return levels;
    }
    
    /**
     * Map a decomposition level to a scale index
     * Uses exponential mapping to cover the range of scales
     */
    private int mapLevelToScale(int level, int numLevels, int numScales) {
        if (numLevels == 1) {
            return numScales / 2; // Middle scale if only one level
        }
        
        // Exponential mapping
        double ratio = (double) level / (numLevels - 1);
        int scaleIndex = (int) Math.round(ratio * (numScales - 1));
        
        return Math.min(Math.max(scaleIndex, 0), numScales - 1);
    }
    
    /**
     * Get the current configuration
     */
    public CWTConfig getConfig() {
        return config;
    }
    
    /**
     * Update the configuration
     */
    public void setConfig(CWTConfig config) {
        this.config = config;
        // Clear transform cache as config has changed
        transformCache.clear();
        currentTransform = null;
    }
    
    /**
     * Clear all caches
     */
    public void clearCache() {
        transformCache.clear();
        waveletCache.clear();
        currentTransform = null;
        currentWaveletName = null;
    }
}