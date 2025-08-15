package com.prophetizo.wavelets.swt.core;

import ai.prophetizo.wavelet.api.BoundaryMode;
import ai.prophetizo.wavelet.api.Wavelet;
import ai.prophetizo.wavelet.api.WaveletRegistry;
import ai.prophetizo.wavelet.modwt.MutableMultiLevelMODWTResult;
import ai.prophetizo.wavelet.modwt.MutableMultiLevelMODWTResultImpl;
import com.prophetizo.LoggerConfig;
import org.slf4j.Logger;
import java.util.Arrays;

/**
 * Wrapper for VectorWave's Stationary Wavelet Transform (SWT) adapter.
 * 
 * <p>This class provides a convenient interface to VectorWave's native SWT/MODWT 
 * implementation, offering wavelet-based signal processing capabilities including:
 * <ul>
 *   <li>Forward and inverse SWT transforms</li>
 *   <li>Signal denoising with various thresholding methods</li>
 *   <li>Level-specific feature extraction</li>
 *   <li>Coefficient thresholding (soft and hard)</li>
 * </ul>
 * 
 * <p>The SWT (also known as the undecimated or redundant wavelet transform) 
 * maintains the same data length at each decomposition level, making it 
 * shift-invariant and suitable for financial time series analysis.
 *
 * @see ai.prophetizo.wavelet.swt.VectorWaveSwtAdapter
 */
public class VectorWaveSwtAdapter {
    private static final Logger logger = LoggerConfig.getLogger(VectorWaveSwtAdapter.class);
    
    private final String waveletType;
    private final BoundaryMode boundaryMode;
    private final ai.prophetizo.wavelet.swt.VectorWaveSwtAdapter swtAdapter;
    private final Wavelet wavelet;
    
    /**
     * Creates a new SWT adapter with the specified wavelet type and periodic boundary handling.
     * 
     * @param waveletType the wavelet type identifier (e.g., "db4", "sym8", "haar")
     * @throws IllegalArgumentException if the wavelet type is not recognized
     */
    public VectorWaveSwtAdapter(String waveletType) {
        this.waveletType = waveletType;
        this.boundaryMode = BoundaryMode.PERIODIC; // Default boundary mode
        this.wavelet = WaveletRegistry.getWavelet(waveletType);
        this.swtAdapter = new ai.prophetizo.wavelet.swt.VectorWaveSwtAdapter(wavelet, this.boundaryMode);
        logger.info("VectorWave SWT adapter initialized with wavelet: {}", waveletType);
    }
    
    /**
     * Creates a new SWT adapter with the specified wavelet type and boundary mode.
     * 
     * @param waveletType the wavelet type identifier (e.g., "db4", "sym8", "haar")
     * @param boundaryMode the boundary handling mode for the transform
     * @throws IllegalArgumentException if the wavelet type is not recognized
     */
    public VectorWaveSwtAdapter(String waveletType, BoundaryMode boundaryMode) {
        this.waveletType = waveletType;
        this.boundaryMode = boundaryMode;
        this.wavelet = WaveletRegistry.getWavelet(waveletType);
        this.swtAdapter = new ai.prophetizo.wavelet.swt.VectorWaveSwtAdapter(wavelet, this.boundaryMode);
        logger.info("VectorWave SWT adapter initialized with wavelet: {}, boundary: {}", waveletType, boundaryMode);
    }
    
    
    /**
     * Performs a forward SWT/MODWT transform on the input data.
     * 
     * @param data the input signal to transform
     * @param levels the number of decomposition levels
     * @return an SwtResult containing the approximation and detail coefficients
     * @throws IllegalArgumentException if data is null or levels is invalid
     */
    public SwtResult transform(double[] data, int levels) {
        MutableMultiLevelMODWTResult result = swtAdapter.forward(data, levels);
        
        // Extract approximation and details from VectorWave result
        double[] approximation = result.getApproximationCoeffs();
        double[][] details = new double[levels][];
        
        for (int j = 1; j <= levels; j++) {
            details[j-1] = result.getDetailCoeffsAtLevel(j);
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("VectorWave SWT completed for {} levels", levels);
        }
        return new SwtResult(approximation, details, waveletType, boundaryMode, result);
    }
    
    /**
     * Denoises a signal using the universal threshold method.
     * 
     * @param data the noisy signal to denoise
     * @param levels the number of decomposition levels
     * @return the denoised signal
     */
    public double[] denoise(double[] data, int levels) {
        return swtAdapter.denoise(data, levels);
    }
    
    /**
     * Denoise signal with custom threshold
     */
    public double[] denoise(double[] data, int levels, double threshold, boolean softThresholding) {
        return swtAdapter.denoise(data, levels, threshold, softThresholding);
    }
    
    /**
     * Extract a specific level from the decomposition
     */
    public double[] extractLevel(double[] data, int totalLevels, int levelToExtract) {
        return swtAdapter.extractLevel(data, totalLevels, levelToExtract);
    }
    
    /**
     * Apply threshold to a specific level in the result
     */
    public void applyThreshold(MutableMultiLevelMODWTResult result, int level, double threshold, boolean softThresholding) {
        swtAdapter.applyThreshold(result, level, threshold, softThresholding);
    }
    
    /**
     * Apply universal threshold to all levels
     */
    public void applyUniversalThreshold(MutableMultiLevelMODWTResult result, boolean softThresholding) {
        swtAdapter.applyUniversalThreshold(result, softThresholding);
    }
    
    /**
     * Perform inverse SWT transform
     */
    public double[] inverse(MutableMultiLevelMODWTResult result) {
        return swtAdapter.inverse(result);
    }
    
    /**
     * Container for SWT transform results, holding both approximation and detail coefficients.
     * 
     * <p>This class provides methods to access and manipulate the wavelet coefficients
     * resulting from an SWT decomposition, including thresholding operations for denoising
     * and signal reconstruction capabilities.
     */
    public static class SwtResult {
        private final double[] approximation;
        private final double[][] details;
        private final String waveletType;
        private final BoundaryMode boundaryMode;
        
        private final MutableMultiLevelMODWTResult vectorWaveResult;
        // Cache the adapter for efficient reconstruction
        private VectorWaveSwtAdapter cachedAdapter;
        
        public SwtResult(double[] approximation, double[][] details, String waveletType, BoundaryMode boundaryMode, MutableMultiLevelMODWTResult vectorWaveResult) {
            if (vectorWaveResult == null) {
                throw new IllegalArgumentException("VectorWave result cannot be null");
            }
            this.approximation = Arrays.copyOf(approximation, approximation.length);
            this.details = new double[details.length][];
            for (int i = 0; i < details.length; i++) {
                this.details[i] = Arrays.copyOf(details[i], details[i].length);
            }
            this.waveletType = waveletType;
            this.boundaryMode = boundaryMode;
            this.vectorWaveResult = vectorWaveResult;
        }
        
        public double[] getApproximation() {
            return Arrays.copyOf(approximation, approximation.length);
        }
        
        public double[] getDetail(int level) {
            if (level < 1 || level > details.length) {
                throw new IllegalArgumentException("Level must be between 1 and " + details.length);
            }
            return Arrays.copyOf(details[level - 1], details[level - 1].length);
        }
        
        public double[][] getAllDetails() {
            double[][] result = new double[details.length][];
            for (int i = 0; i < details.length; i++) {
                result[i] = Arrays.copyOf(details[i], details[i].length);
            }
            return result;
        }
        
        public int getLevels() {
            return details.length;
        }
        
        public String getWaveletType() {
            return waveletType;
        }
        
        /**
         * Applies thresholding to a specific detail level for denoising.
         * 
         * @param level the detail level to threshold (1-based)
         * @param threshold the threshold value
         * @param softThresholding if true, uses soft thresholding; otherwise uses hard thresholding
         * @throws IllegalArgumentException if level is out of range
         */
        public void applyShrinkage(int level, double threshold, boolean softThresholding) {
            if (level < 1 || level > details.length) {
                throw new IllegalArgumentException("Level must be between 1 and " + details.length);
            }
            
            // Apply threshold directly to VectorWave result
            double[] mutableDetails = vectorWaveResult.getMutableDetailCoeffs(level);
            for (int i = 0; i < mutableDetails.length; i++) {
                if (softThresholding) {
                    // Soft thresholding
                    if (Math.abs(mutableDetails[i]) <= threshold) {
                        mutableDetails[i] = 0.0;
                    } else {
                        mutableDetails[i] = Math.signum(mutableDetails[i]) * (Math.abs(mutableDetails[i]) - threshold);
                    }
                } else {
                    // Hard thresholding
                    if (Math.abs(mutableDetails[i]) <= threshold) {
                        mutableDetails[i] = 0.0;
                    }
                }
            }
            vectorWaveResult.clearCaches();
            // Update local copy
            details[level - 1] = Arrays.copyOf(mutableDetails, mutableDetails.length);
        }
        
        /**
         * Get the underlying VectorWave result for advanced operations
         */
        public MutableMultiLevelMODWTResult getVectorWaveResult() {
            return vectorWaveResult;
        }
        
        /**
         * Reconstructs a signal from approximation and specified detail levels.
         * 
         * <p>This method performs an inverse SWT transform using coefficients up to
         * the specified level. Higher-level details are zeroed out, resulting in
         * a smoother reconstruction.
         * 
         * <p>Performance optimization: This method implements a sophisticated caching
         * strategy to minimize object allocation:
         * <ul>
         *   <li>Works directly with the original mutable result (no deep copy)</li>
         *   <li>Temporarily modifies coefficients for reconstruction</li>
         *   <li>Restores original coefficients after reconstruction</li>
         *   <li>Caches the VectorWaveSwtAdapter to avoid recreation</li>
         * </ul>
         * 
         * @param maxLevel the maximum detail level to include in reconstruction
         * @return the reconstructed signal
         */
        public double[] reconstruct(int maxLevel) {
            // Optimized strategy: Work directly with vectorWaveResult
            // No need to create copies - we'll restore coefficients after use
            // This eliminates the expensive toImmutable() -> new MutableMultiLevelMODWTResultImpl() conversion
            
            // Store original coefficients for levels we'll zero out
            double[][] savedCoeffs = null;
            int startLevel = maxLevel + 1;
            int endLevel = details.length;
            
            if (startLevel <= endLevel) {
                // Save coefficients we're about to zero
                savedCoeffs = new double[endLevel - startLevel + 1][];
                for (int level = startLevel; level <= endLevel; level++) {
                    double[] mutableDetails = vectorWaveResult.getMutableDetailCoeffs(level);
                    savedCoeffs[level - startLevel] = Arrays.copyOf(mutableDetails, mutableDetails.length);
                    Arrays.fill(mutableDetails, 0.0);
                }
                // Clear caches after modification
                vectorWaveResult.clearCaches();
            }
            
            try {
                // Use cached adapter for efficient reconstruction
                if (cachedAdapter == null) {
                    cachedAdapter = new VectorWaveSwtAdapter(waveletType, boundaryMode);
                }
                return cachedAdapter.inverse(vectorWaveResult);
            } finally {
                // Restore original coefficients
                if (savedCoeffs != null) {
                    for (int level = startLevel; level <= endLevel; level++) {
                        double[] mutableDetails = vectorWaveResult.getMutableDetailCoeffs(level);
                        System.arraycopy(savedCoeffs[level - startLevel], 0, mutableDetails, 0, mutableDetails.length);
                    }
                    // Clear caches after restoration
                    vectorWaveResult.clearCaches();
                }
            }
        }
        
        /**
         * Reconstruct approximation only (smooth trend)
         */
        public double[] reconstructApproximation() {
            return Arrays.copyOf(approximation, approximation.length);
        }
    }
}