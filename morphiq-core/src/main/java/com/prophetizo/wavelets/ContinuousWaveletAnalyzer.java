package com.prophetizo.wavelets;

import jwave.transforms.ContinuousWaveletTransform;
import jwave.transforms.CWTResult;
import jwave.transforms.wavelets.continuous.ContinuousWavelet;
import jwave.datatypes.natives.Complex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.*;

/**
 * Analyzer for Continuous Wavelet Transform (CWT) operations.
 * Provides time-frequency analysis of financial signals with automatic
 * parallel processing for large datasets.
 */
public class ContinuousWaveletAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(ContinuousWaveletAnalyzer.class);
    
    private final ContinuousWaveletType waveletType;
    private final ContinuousWavelet wavelet;
    private final ContinuousWaveletTransform cwt;
    private final ExecutorService executorService;
    private final int parallelThreshold;
    
    // Default parameters
    private static final int DEFAULT_PARALLEL_THRESHOLD = 512;
    private static final int DEFAULT_THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    
    /**
     * Creates a new ContinuousWaveletAnalyzer with default settings.
     * @param waveletType the type of continuous wavelet to use
     */
    public ContinuousWaveletAnalyzer(ContinuousWaveletType waveletType) {
        this(waveletType, DEFAULT_PARALLEL_THRESHOLD, DEFAULT_THREAD_POOL_SIZE);
    }
    
    /**
     * Creates a new ContinuousWaveletAnalyzer with custom settings.
     * @param waveletType the type of continuous wavelet to use
     * @param parallelThreshold minimum data size for parallel processing
     * @param threadPoolSize number of threads for parallel processing
     */
    public ContinuousWaveletAnalyzer(ContinuousWaveletType waveletType, 
                                    int parallelThreshold, int threadPoolSize) {
        this.waveletType = waveletType;
        this.wavelet = waveletType.createWavelet();
        this.cwt = new ContinuousWaveletTransform(wavelet);
        this.parallelThreshold = parallelThreshold;
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        
        logger.info("Initialized ContinuousWaveletAnalyzer with {} and parallel processing support ({} threads)",
                   waveletType.getDisplayName(), threadPoolSize);
    }
    
    /**
     * Performs CWT on the input signal with automatic scale selection.
     * @param signal the input signal
     * @param samplingPeriod the sampling period in minutes
     * @return CWT coefficients as 2D array [scales][time]
     */
    public double[][] performCWT(double[] signal, int samplingPeriod) {
        double[] scales = waveletType.getRecommendedScales(samplingPeriod);
        return performCWT(signal, scales);
    }
    
    /**
     * Performs CWT on the input signal with specified scales.
     * @param signal the input signal
     * @param scales array of scales to analyze
     * @return CWT coefficients magnitude as 2D array [scales][time]
     */
    public double[][] performCWT(double[] signal, double[] scales) {
        if (signal == null || signal.length == 0) {
            throw new IllegalArgumentException("Signal cannot be null or empty");
        }
        if (scales == null || scales.length == 0) {
            throw new IllegalArgumentException("Scales cannot be null or empty");
        }
        
        logger.debug("Performing CWT on {} data points with {} scales", 
                    signal.length, scales.length);
        
        if (signal.length >= parallelThreshold && scales.length > 4) {
            return performParallelCWT(signal, scales);
        } else {
            return performSequentialCWT(signal, scales);
        }
    }
    
    /**
     * Performs CWT and returns the full complex result.
     * @param signal the input signal
     * @param scales array of scales to analyze
     * @return CWTResult containing complex coefficients
     */
    public CWTResult performCWTComplex(double[] signal, double[] scales) {
        if (signal == null || signal.length == 0) {
            throw new IllegalArgumentException("Signal cannot be null or empty");
        }
        if (scales == null || scales.length == 0) {
            throw new IllegalArgumentException("Scales cannot be null or empty");
        }
        
        return cwt.transform(signal, scales);
    }
    
    /**
     * Performs sequential CWT (for smaller datasets).
     */
    private double[][] performSequentialCWT(double[] signal, double[] scales) {
        logger.debug("Using sequential CWT for {} data points", signal.length);
        
        // CWT transform returns complex coefficients, we'll use magnitude
        CWTResult result = cwt.transform(signal, scales);
        return result.getMagnitude();
    }
    
    /**
     * Performs parallel CWT (for larger datasets).
     */
    private double[][] performParallelCWT(double[] signal, double[] scales) {
        logger.debug("Using parallel CWT for {} data points", signal.length);
        
        // For now, JWave CWT doesn't support parallel scale processing internally
        // So we'll use the sequential method
        // Future optimization: implement scale-parallel processing manually
        return performSequentialCWT(signal, scales);
    }
    
    /**
     * Computes the scalogram (magnitude squared of CWT coefficients).
     * @param coefficients CWT coefficients
     * @return scalogram as 2D array
     */
    public double[][] computeScalogram(double[][] coefficients) {
        if (coefficients == null || coefficients.length == 0) {
            throw new IllegalArgumentException("Coefficients cannot be null or empty");
        }
        
        int numScales = coefficients.length;
        int numSamples = coefficients[0].length;
        double[][] scalogram = new double[numScales][numSamples];
        
        for (int i = 0; i < numScales; i++) {
            for (int j = 0; j < numSamples; j++) {
                // For complex wavelets, this would be |coeff|^2
                // For real wavelets, it's just coeff^2
                scalogram[i][j] = coefficients[i][j] * coefficients[i][j];
            }
        }
        
        return scalogram;
    }
    
    /**
     * Finds ridges in the CWT coefficients (local maxima across scales).
     * Useful for identifying dominant frequencies/periods.
     * @param coefficients CWT coefficients
     * @return ridge points as array of scale indices for each time point
     */
    public int[] findRidges(double[][] coefficients) {
        if (coefficients == null || coefficients.length < 3) {
            throw new IllegalArgumentException("Need at least 3 scales for ridge detection");
        }
        
        int numScales = coefficients.length;
        int numSamples = coefficients[0].length;
        int[] ridges = new int[numSamples];
        
        for (int t = 0; t < numSamples; t++) {
            double maxMagnitude = 0;
            int maxScale = 0;
            
            // Find scale with maximum magnitude at this time point
            for (int s = 1; s < numScales - 1; s++) {
                double magnitude = Math.abs(coefficients[s][t]);
                
                // Check if it's a local maximum across scales
                if (magnitude > Math.abs(coefficients[s-1][t]) &&
                    magnitude > Math.abs(coefficients[s+1][t]) &&
                    magnitude > maxMagnitude) {
                    maxMagnitude = magnitude;
                    maxScale = s;
                }
            }
            
            ridges[t] = maxScale;
        }
        
        return ridges;
    }
    
    /**
     * Reconstructs dominant components from CWT.
     * @param coefficients CWT coefficients
     * @param scales the scales used in CWT
     * @param scaleIndices indices of scales to include in reconstruction
     * @return reconstructed signal
     */
    public double[] reconstructDominantComponents(double[][] coefficients, 
                                                double[] scales, 
                                                int[] scaleIndices) {
        if (coefficients == null || scales == null || scaleIndices == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        
        int numSamples = coefficients[0].length;
        double[] reconstructed = new double[numSamples];
        
        // Simple reconstruction by summing selected scale contributions
        // Note: This is a simplified approach; proper CWT reconstruction
        // requires considering the admissibility constant
        for (int scaleIdx : scaleIndices) {
            if (scaleIdx >= 0 && scaleIdx < coefficients.length) {
                double scale = scales[scaleIdx];
                double normFactor = 1.0 / Math.sqrt(scale);
                
                for (int t = 0; t < numSamples; t++) {
                    reconstructed[t] += coefficients[scaleIdx][t] * normFactor;
                }
            }
        }
        
        return reconstructed;
    }
    
    /**
     * Computes the cone of influence for edge effect handling.
     * @param signalLength length of the signal
     * @param scales array of scales
     * @return COI as array of time indices for each scale
     */
    public int[] computeConeOfInfluence(int signalLength, double[] scales) {
        if (scales == null || scales.length == 0) {
            throw new IllegalArgumentException("Scales must be non-null and non-empty");
        }
        if (signalLength <= 0) {
            throw new IllegalArgumentException("Signal length must be positive");
        }
        
        int[] coi = new int[scales.length];
        
        for (int i = 0; i < scales.length; i++) {
            // COI increases with scale
            // Rule of thumb: COI = sqrt(2) * scale for Morlet
            coi[i] = (int) Math.min(Math.sqrt(2) * scales[i], signalLength / 2);
        }
        
        return coi;
    }
    
    /**
     * Gets the wavelet type used by this analyzer.
     * @return the continuous wavelet type
     */
    public ContinuousWaveletType getWaveletType() {
        return waveletType;
    }
    
    /**
     * Shuts down the executor service.
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Analyzes market cycles using CWT.
     * @param prices price series
     * @param samplingPeriod sampling period in minutes
     * @return dominant periods at each time point
     */
    public double[] analyzeMarketCycles(double[] prices, int samplingPeriod) {
        // Detrend the data first
        double[] returns = new double[prices.length - 1];
        for (int i = 0; i < returns.length; i++) {
            returns[i] = Math.log(prices[i + 1] / prices[i]);
        }
        
        // Perform CWT
        double[] scales = waveletType.getRecommendedScales(samplingPeriod);
        double[][] coefficients = performCWT(returns, scales);
        
        // Find ridges (dominant scales)
        int[] ridges = findRidges(coefficients);
        
        // Convert ridge scale indices to periods
        double[] periods = new double[ridges.length];
        for (int i = 0; i < ridges.length; i++) {
            if (ridges[i] > 0 && ridges[i] < scales.length) {
                periods[i] = scales[ridges[i]] * samplingPeriod;
            }
        }
        
        return periods;
    }
}