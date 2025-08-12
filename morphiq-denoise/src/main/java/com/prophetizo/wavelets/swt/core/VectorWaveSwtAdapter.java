package com.prophetizo.wavelets.swt.core;

import ai.prophetizo.wavelet.api.BoundaryMode;
import ai.prophetizo.wavelet.api.Wavelet;
import ai.prophetizo.wavelet.api.WaveletRegistry;
import ai.prophetizo.wavelet.modwt.MutableMultiLevelMODWTResult;
import ai.prophetizo.wavelet.modwt.MutableMultiLevelMODWTResultImpl;
import java.util.Arrays;

/**
 * Wrapper for VectorWave SWT adapter providing additional convenience methods.
 * Uses VectorWave's native SWT/MODWT implementation for all transforms.
 */
public class VectorWaveSwtAdapter {
    // Simple logging for debugging
    private static void log(String level, String message, Object... args) {
        // Skip TRACE level logs completely for performance
        if ("TRACE".equals(level)) {
            return;
        }
        // Extract exception if present as last argument
        Throwable exception = null;
        int formatArgCount = args.length;
        
        if (args.length > 0 && args[args.length - 1] instanceof Throwable) {
            exception = (Throwable) args[args.length - 1];
            formatArgCount--; // Don't include exception in format args
        }
        
        // Build message more efficiently without array creation for simple cases
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(level).append("] [VectorWaveSwtAdapter] ");
        
        // Format message with arguments if present
        if (formatArgCount > 0) {
            // Only create array copy if we have format arguments and an exception
            if (exception != null && formatArgCount < args.length) {
                Object[] formatArgs = Arrays.copyOf(args, formatArgCount);
                sb.append(String.format(message, formatArgs));
            } else {
                sb.append(String.format(message, args));
            }
        } else {
            sb.append(message);
        }
        
        System.out.println(sb.toString());
        
        // Print stack trace if exception present
        if (exception != null) {
            exception.printStackTrace(System.out);
        }
    }
    
    private final String waveletType;
    private final ai.prophetizo.wavelet.swt.VectorWaveSwtAdapter swtAdapter;
    private final Wavelet wavelet;
    
    public VectorWaveSwtAdapter(String waveletType) {
        this.waveletType = waveletType;
        this.wavelet = WaveletRegistry.getWavelet(waveletType);
        this.swtAdapter = new ai.prophetizo.wavelet.swt.VectorWaveSwtAdapter(wavelet, BoundaryMode.PERIODIC);
        log("INFO", "VectorWave SWT adapter initialized with wavelet: %s", waveletType);
    }
    
    public VectorWaveSwtAdapter(String waveletType, BoundaryMode boundaryMode) {
        this.waveletType = waveletType;
        this.wavelet = WaveletRegistry.getWavelet(waveletType);
        this.swtAdapter = new ai.prophetizo.wavelet.swt.VectorWaveSwtAdapter(wavelet, boundaryMode);
        log("INFO", "VectorWave SWT adapter initialized with wavelet: %s, boundary: %s", waveletType, boundaryMode);
    }
    
    
    /**
     * Perform SWT/MODWT transform on input data
     */
    public SwtResult transform(double[] data, int levels) {
        MutableMultiLevelMODWTResult result = swtAdapter.forward(data, levels);
        
        // Extract approximation and details from VectorWave result
        double[] approximation = result.getApproximationCoeffs();
        double[][] details = new double[levels][];
        
        for (int j = 1; j <= levels; j++) {
            details[j-1] = result.getDetailCoeffsAtLevel(j);
        }
        
        log("DEBUG", "VectorWave SWT completed for %d levels", levels);
        return new SwtResult(approximation, details, waveletType, result);
    }
    
    /**
     * Denoise signal using universal threshold
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
     * Result container for SWT transform
     */
    public static class SwtResult {
        private final double[] approximation;
        private final double[][] details;
        private final String waveletType;
        
        private final MutableMultiLevelMODWTResult vectorWaveResult;
        
        public SwtResult(double[] approximation, double[][] details, String waveletType, MutableMultiLevelMODWTResult vectorWaveResult) {
            if (vectorWaveResult == null) {
                throw new IllegalArgumentException("VectorWave result cannot be null");
            }
            this.approximation = Arrays.copyOf(approximation, approximation.length);
            this.details = new double[details.length][];
            for (int i = 0; i < details.length; i++) {
                this.details[i] = Arrays.copyOf(details[i], details[i].length);
            }
            this.waveletType = waveletType;
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
         * Apply threshold to a specific detail level
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
         * Reconstruct signal from approximation and specified detail levels
         */
        public double[] reconstruct(int maxLevel) {
            // Create a copy of the result to avoid modifying the original
            MutableMultiLevelMODWTResult tempResult = new MutableMultiLevelMODWTResultImpl(
                vectorWaveResult.toImmutable());
            
            // Zero out detail coefficients beyond maxLevel
            for (int level = maxLevel + 1; level <= details.length; level++) {
                double[] mutableDetails = tempResult.getMutableDetailCoeffs(level);
                Arrays.fill(mutableDetails, 0.0);
            }
            tempResult.clearCaches();
            
            // Use VectorWave's inverse transform for proper reconstruction
            VectorWaveSwtAdapter parent = new VectorWaveSwtAdapter(waveletType);
            return parent.inverse(tempResult);
        }
        
        /**
         * Reconstruct approximation only (smooth trend)
         */
        public double[] reconstructApproximation() {
            return Arrays.copyOf(approximation, approximation.length);
        }
    }
}