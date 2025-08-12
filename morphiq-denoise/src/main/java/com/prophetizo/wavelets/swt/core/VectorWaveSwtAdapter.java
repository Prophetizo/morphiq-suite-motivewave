package com.prophetizo.wavelets.swt.core;

import java.util.Arrays;

/**
 * Bridge to VectorWave for SWT/MODWT transforms with per-level thresholding.
 * Provides graceful fallbacks when VectorWave is not available.
 */
public class VectorWaveSwtAdapter {
    // Simple console logging for standalone compilation - optimized for performance
    private static void log(String level, String message, Object... args) {
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
    private final boolean vectorWaveAvailable;
    
    public VectorWaveSwtAdapter(String waveletType) {
        this.waveletType = waveletType;
        this.vectorWaveAvailable = checkVectorWaveAvailability();
        
        if (vectorWaveAvailable) {
            log("INFO", "VectorWave is available for SWT processing with wavelet: {}", waveletType);
        } else {
            log("WARN", "VectorWave is not available, using fallback implementation");
        }
    }
    
    /**
     * Check if VectorWave classes are available in the classpath
     */
    private boolean checkVectorWaveAvailability() {
        try {
            Class.forName("ai.prophetizo.wavelet.api.WaveletRegistry");
            Class.forName("ai.prophetizo.wavelet.transforms.SWT");
            return true;
        } catch (ClassNotFoundException e) {
            log("DEBUG", "VectorWave classes not found: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Perform SWT/MODWT transform on input data
     */
    public SwtResult transform(double[] data, int levels) {
        if (vectorWaveAvailable) {
            return performVectorWaveSwt(data, levels);
        } else {
            return performFallbackSwt(data, levels);
        }
    }
    
    /**
     * Use VectorWave for SWT computation
     */
    private SwtResult performVectorWaveSwt(double[] data, int levels) {
        try {
            // Use reflection to avoid compile-time dependency on VectorWave
            Class<?> registryClass = Class.forName("ai.prophetizo.wavelet.api.WaveletRegistry");
            Class<?> swtClass = Class.forName("ai.prophetizo.wavelet.transforms.SWT");
            
            // Get wavelet
            Object wavelet = registryClass.getMethod("getWavelet", String.class)
                    .invoke(null, waveletType);
            
            // Create SWT instance
            Object swtInstance = swtClass.getConstructor(
                    Class.forName("ai.prophetizo.wavelet.api.Wavelet"))
                    .newInstance(wavelet);
            
            // Perform forward transform
            Object result = swtClass.getMethod("forwardTransform", double[].class, int.class)
                    .invoke(swtInstance, data, levels);
            
            // Extract approximation and details using reflection
            double[] approximation = (double[]) result.getClass()
                    .getMethod("getApproximation").invoke(result);
            
            double[][] details = new double[levels][];
            for (int j = 1; j <= levels; j++) {
                details[j-1] = (double[]) result.getClass()
                        .getMethod("getDetail", int.class).invoke(result, j);
            }
            
            log("DEBUG", "VectorWave SWT completed for {} levels", levels);
            return new SwtResult(approximation, details, waveletType);
            
        } catch (Exception e) {
            log("ERROR", "VectorWave SWT failed, falling back to simple implementation: {}", e.getMessage(), e);
            return performFallbackSwt(data, levels);
        }
    }
    
    /**
     * Simple fallback SWT implementation when VectorWave is not available
     */
    private SwtResult performFallbackSwt(double[] data, int levels) {
        log("WARN", "Using simplified fallback SWT implementation");
        
        // Simple undecimated decomposition using moving averages
        // This is a simplified approximation - real SWT requires proper wavelet filters
        double[] current = Arrays.copyOf(data, data.length);
        double[][] details = new double[levels][];
        
        for (int j = 0; j < levels; j++) {
            int scale = 1 << (j + 1); // 2^(j+1)
            double[] smoothed = applySimpleSmoothing(current, scale);
            
            // Detail = original - smoothed (high-pass approximation)
            details[j] = new double[data.length];
            for (int i = 0; i < data.length; i++) {
                details[j][i] = current[i] - smoothed[i];
            }
            
            current = smoothed; // Use smoothed for next level
        }
        
        // Final approximation
        double[] approximation = Arrays.copyOf(current, current.length);
        
        log("DEBUG", "Fallback SWT completed for {} levels", levels);
        return new SwtResult(approximation, details, waveletType + "_fallback");
    }
    
    /**
     * Simple smoothing filter for fallback implementation
     */
    private double[] applySimpleSmoothing(double[] data, int scale) {
        double[] result = new double[data.length];
        int halfWindow = Math.max(1, scale / 2);
        
        for (int i = 0; i < data.length; i++) {
            double sum = 0;
            int count = 0;
            
            int start = Math.max(0, i - halfWindow);
            int end = Math.min(data.length - 1, i + halfWindow);
            
            for (int j = start; j <= end; j++) {
                sum += data[j];
                count++;
            }
            
            result[i] = sum / count;
        }
        
        return result;
    }
    
    /**
     * Result container for SWT transform
     */
    public static class SwtResult {
        private final double[] approximation;
        private final double[][] details;
        private final String waveletType;
        
        public SwtResult(double[] approximation, double[][] details, String waveletType) {
            this.approximation = Arrays.copyOf(approximation, approximation.length);
            this.details = new double[details.length][];
            for (int i = 0; i < details.length; i++) {
                this.details[i] = Arrays.copyOf(details[i], details[i].length);
            }
            this.waveletType = waveletType;
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
            
            double[] detail = details[level - 1];
            
            for (int i = 0; i < detail.length; i++) {
                if (softThresholding) {
                    // Soft thresholding
                    if (Math.abs(detail[i]) <= threshold) {
                        detail[i] = 0.0;
                    } else {
                        detail[i] = Math.signum(detail[i]) * (Math.abs(detail[i]) - threshold);
                    }
                } else {
                    // Hard thresholding
                    if (Math.abs(detail[i]) <= threshold) {
                        detail[i] = 0.0;
                    }
                }
            }
        }
        
        /**
         * Reconstruct signal from approximation and specified detail levels
         */
        public double[] reconstruct(int maxLevel) {
            double[] result = Arrays.copyOf(approximation, approximation.length);
            
            // Add back detail coefficients up to maxLevel
            for (int j = 1; j <= Math.min(maxLevel, details.length); j++) {
                double[] detail = details[j - 1];
                for (int i = 0; i < result.length; i++) {
                    result[i] += detail[i];
                }
            }
            
            return result;
        }
        
        /**
         * Reconstruct approximation only (smooth trend)
         */
        public double[] reconstructApproximation() {
            return Arrays.copyOf(approximation, approximation.length);
        }
    }
}