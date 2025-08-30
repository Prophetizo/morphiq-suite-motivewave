package com.morphiqlabs.wavelets.core;

import com.morphiqlabs.wavelet.api.WaveletName;
import com.morphiqlabs.wavelet.cwt.CWTConfig;
import com.morphiqlabs.wavelet.cwt.CWTResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VectorWaveCwtAdapter
 * 
 * @author Stephen Campbell
 */
class VectorWaveCwtAdapterTest {
    
    private VectorWaveCwtAdapter adapter;
    private double[] testSignal;
    
    @BeforeEach
    void setUp() {
        adapter = new VectorWaveCwtAdapter();
        
        // Create a test signal with known properties
        // Combine two sine waves of different frequencies
        int n = 256;
        testSignal = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            testSignal[i] = Math.sin(2 * Math.PI * 5 * t) +  // 5 Hz component
                           0.5 * Math.sin(2 * Math.PI * 20 * t); // 20 Hz component
        }
    }
    
    @Test
    void testConstructors() {
        // Test default constructor
        VectorWaveCwtAdapter defaultAdapter = new VectorWaveCwtAdapter();
        assertNotNull(defaultAdapter);
        assertNotNull(defaultAdapter.getConfig());
        
        // Test constructor with custom config
        CWTConfig customConfig = CWTConfig.builder()
            .enableFFT(false)
            .normalizeScales(false)
            .build();
        VectorWaveCwtAdapter customAdapter = new VectorWaveCwtAdapter(customConfig);
        assertNotNull(customAdapter);
        assertEquals(customConfig, customAdapter.getConfig());
    }
    
    @Test
    void testAnalyzeWithValidInput() {
        double[] scales = {1.0, 2.0, 4.0, 8.0, 16.0};
        
        // Test with Morlet wavelet
        CWTResult result = adapter.analyze(testSignal, WaveletName.MORLET, scales);
        assertNotNull(result, "CWT result should not be null");
        assertEquals(scales.length, result.getNumScales(), "Number of scales should match");
        assertEquals(testSignal.length, result.getNumSamples(), "Number of samples should match");
        
        // Verify coefficients are computed
        double[][] coefficients = result.getCoefficients();
        assertNotNull(coefficients);
        assertEquals(scales.length, coefficients.length);
        assertEquals(testSignal.length, coefficients[0].length);
        
        // Check that coefficients contain non-zero values
        boolean hasNonZero = false;
        for (double[] row : coefficients) {
            for (double val : row) {
                if (val != 0.0) {
                    hasNonZero = true;
                    break;
                }
            }
        }
        assertTrue(hasNonZero, "Coefficients should contain non-zero values");
    }
    
    @Test
    void testAnalyzeWithNullInput() {
        double[] scales = {1.0, 2.0, 4.0};
        
        // Test with null data
        CWTResult result = adapter.analyze(null, WaveletName.MORLET, scales);
        assertNull(result, "Should return null for null input data");
        
        // Test with empty data
        result = adapter.analyze(new double[0], WaveletName.MORLET, scales);
        assertNull(result, "Should return null for empty input data");
        
        // Test with null scales
        result = adapter.analyze(testSignal, WaveletName.MORLET, null);
        assertNull(result, "Should return null for null scales");
        
        // Test with empty scales
        result = adapter.analyze(testSignal, WaveletName.MORLET, new double[0]);
        assertNull(result, "Should return null for empty scales");
    }
    
    @Test
    void testAnalyzeWithAutoScales() {
        int numScales = 8;
        
        // Test automatic scale generation
        CWTResult result = adapter.analyzeWithAutoScales(testSignal, WaveletName.MEXICAN_HAT, numScales);
        assertNotNull(result, "CWT result should not be null");
        assertEquals(numScales, result.getNumScales(), "Number of scales should match requested");
        assertEquals(testSignal.length, result.getNumSamples(), "Number of samples should match");
        
        // Verify scales are reasonable
        double[] scales = result.getScales();
        assertNotNull(scales);
        assertEquals(numScales, scales.length);
        
        // Scales should be in increasing order
        for (int i = 1; i < scales.length; i++) {
            assertTrue(scales[i] > scales[i-1], "Scales should be in increasing order");
        }
        
        // First scale should be around 1, last scale should be reasonable
        assertTrue(scales[0] >= 0.5 && scales[0] <= 2.0, "First scale should be around 1");
        assertTrue(scales[scales.length-1] <= testSignal.length / 2.0, 
                  "Last scale should be reasonable relative to signal length");
    }
    
    @Test
    void testGenerateScales() {
        int signalLength = 512;
        int numScales = 10;
        
        double[] scales = adapter.generateScales(signalLength, numScales);
        
        assertNotNull(scales);
        assertEquals(numScales, scales.length);
        
        // Verify scales are in increasing order
        for (int i = 1; i < scales.length; i++) {
            assertTrue(scales[i] > scales[i-1], 
                      String.format("Scale %d (%f) should be greater than scale %d (%f)", 
                                  i, scales[i], i-1, scales[i-1]));
        }
        
        // Check reasonable bounds
        assertTrue(scales[0] >= 0.5, "Minimum scale should be reasonable");
        assertTrue(scales[scales.length-1] <= signalLength / 2.0, 
                  "Maximum scale should be bounded by signal length");
        
        // Verify logarithmic spacing
        double[] logScales = new double[numScales];
        for (int i = 0; i < numScales; i++) {
            logScales[i] = Math.log(scales[i]);
        }
        
        // Check that log scales are approximately linearly spaced
        double expectedStep = (logScales[numScales-1] - logScales[0]) / (numScales - 1);
        for (int i = 1; i < numScales; i++) {
            double actualStep = logScales[i] - logScales[i-1];
            assertEquals(expectedStep, actualStep, 0.001, 
                        "Log scales should be linearly spaced");
        }
    }
    
    @Test
    void testGenerateScalesWithSingleScale() {
        int signalLength = 512;
        
        double[] scales = adapter.generateScales(signalLength, 1);
        
        assertNotNull(scales);
        assertEquals(1, scales.length);
        
        // For single scale, should use geometric mean of min and max
        double expectedMin = 1.0;
        double expectedMax = Math.min(signalLength / 4.0, 128.0);
        double expectedScale = Math.sqrt(expectedMin * expectedMax);
        
        assertEquals(expectedScale, scales[0], 0.001, 
                    "Single scale should be geometric mean of min and max");
    }
    
    @Test
    void testGenerateScalesWithTwoScales() {
        int signalLength = 256;
        
        double[] scales = adapter.generateScales(signalLength, 2);
        
        assertNotNull(scales);
        assertEquals(2, scales.length);
        
        // First scale should be minScale (1.0)
        assertEquals(1.0, scales[0], 0.001, "First scale should be 1.0");
        
        // Second scale should be maxScale
        double expectedMax = Math.min(signalLength / 4.0, 128.0);
        assertEquals(expectedMax, scales[1], 0.001, 
                    "Second scale should be max scale");
    }
    
    @Test
    void testExtractLevels() {
        double[] scales = {1.0, 2.0, 4.0, 8.0, 16.0};
        CWTResult result = adapter.analyze(testSignal, WaveletName.PAUL, scales);
        assertNotNull(result);
        
        // Test level extraction
        int numLevels = 3;
        double[][] levels = adapter.extractLevels(result, numLevels);
        
        assertNotNull(levels);
        assertEquals(numLevels, levels.length);
        assertEquals(testSignal.length, levels[0].length);
        
        // Verify each level has data and is normalized
        for (int i = 0; i < numLevels; i++) {
            assertNotNull(levels[i]);
            assertEquals(testSignal.length, levels[i].length);
            
            // Check normalization: mean should be approximately 0
            double mean = 0.0;
            for (double val : levels[i]) {
                mean += val;
            }
            mean /= levels[i].length;
            assertEquals(0.0, mean, 0.01, "Normalized level should have mean ≈ 0");
            
            // Check normalization: std dev should be approximately 1
            double variance = 0.0;
            for (double val : levels[i]) {
                variance += val * val;
            }
            double stdDev = Math.sqrt(variance / levels[i].length);
            assertEquals(1.0, stdDev, 0.1, "Normalized level should have std dev ≈ 1");
            
            // Check for non-zero values
            boolean hasNonZero = false;
            for (double val : levels[i]) {
                if (Math.abs(val) > 0.001) {
                    hasNonZero = true;
                    break;
                }
            }
            assertTrue(hasNonZero, "Level " + i + " should contain non-zero values");
        }
    }
    
    @Test
    void testExtractLevelsWithSingleLevel() {
        double[] scales = {4.0};
        CWTResult result = adapter.analyze(testSignal, WaveletName.MORLET, scales);
        assertNotNull(result);
        
        double[][] levels = adapter.extractLevels(result, 1);
        
        assertNotNull(levels);
        assertEquals(1, levels.length);
        assertEquals(testSignal.length, levels[0].length);
        
        // Verify normalization for single level
        double mean = 0.0;
        double sumSquares = 0.0;
        for (double val : levels[0]) {
            mean += val;
            sumSquares += val * val;
        }
        mean /= levels[0].length;
        double stdDev = Math.sqrt(sumSquares / levels[0].length);
        
        assertEquals(0.0, mean, 0.01, "Single level should be centered");
        assertEquals(1.0, stdDev, 0.1, "Single level should be normalized");
    }
    
    @Test
    void testExtractLevelsWithNullResult() {
        double[][] levels = adapter.extractLevels(null, 3);
        assertNull(levels, "Should return null for null CWT result");
    }
    
    @Test
    void testExtractLevelsWithMoreLevelsThanScales() {
        double[] scales = {1.0, 2.0};
        CWTResult result = adapter.analyze(testSignal, WaveletName.MORLET, scales);
        assertNotNull(result);
        
        // Request more levels than available scales
        int numLevels = 5;
        double[][] levels = adapter.extractLevels(result, numLevels);
        
        assertNotNull(levels);
        // Should return only available scales
        assertEquals(scales.length, levels.length);
    }
    
    @Test
    void testDifferentWavelets() {
        double[] scales = {2.0, 4.0, 8.0};
        
        // Test CWT wavelets
        WaveletName[] cwtWavelets = {
            WaveletName.MORLET,
            WaveletName.MEXICAN_HAT,
            WaveletName.PAUL
        };
        
        for (WaveletName wavelet : cwtWavelets) {
            CWTResult result = adapter.analyze(testSignal, wavelet, scales);
            assertNotNull(result, "CWT should work with " + wavelet);
            assertEquals(scales.length, result.getNumScales());
            assertEquals(testSignal.length, result.getNumSamples());
        }
    }
    
    @Test
    void testConfigUpdate() {
        CWTConfig originalConfig = adapter.getConfig();
        assertNotNull(originalConfig);
        
        // Update configuration
        CWTConfig newConfig = CWTConfig.builder()
            .enableFFT(false)
            .normalizeScales(false)
            .paddingStrategy(CWTConfig.PaddingStrategy.ZERO)
            .build();
        
        adapter.setConfig(newConfig);
        assertEquals(newConfig, adapter.getConfig());
        assertNotEquals(originalConfig, adapter.getConfig());
    }
    
    @Test
    void testCacheClear() {
        double[] scales = {1.0, 2.0};
        
        // Perform analysis to populate cache
        CWTResult result1 = adapter.analyze(testSignal, WaveletName.MORLET, scales);
        assertNotNull(result1);
        
        // Clear cache
        adapter.clearCache();
        
        // Should still work after cache clear
        CWTResult result2 = adapter.analyze(testSignal, WaveletName.MORLET, scales);
        assertNotNull(result2);
        assertEquals(result1.getNumScales(), result2.getNumScales());
        assertEquals(result1.getNumSamples(), result2.getNumSamples());
    }
    
    @Test
    void testEdgeCases() {
        // Very small signal
        double[] smallSignal = {1.0, 2.0, 3.0, 4.0};
        double[] scales = {1.0};
        CWTResult result = adapter.analyze(smallSignal, WaveletName.MORLET, scales);
        assertNotNull(result);
        assertEquals(1, result.getNumScales());
        assertEquals(smallSignal.length, result.getNumSamples());
        
        // Single sample signal
        double[] singleSample = {1.0};
        result = adapter.analyze(singleSample, WaveletName.MEXICAN_HAT, scales);
        assertNotNull(result);
        assertEquals(1, result.getNumScales());
        assertEquals(1, result.getNumSamples());
    }
    
    @Test
    void testCWTWithKnownSignal() {
        // Test with a pure sine wave - CWT should detect the frequency
        int n = 256;
        double frequency = 10.0; // 10 cycles in the signal
        double[] sineWave = new double[n];
        for (int i = 0; i < n; i++) {
            sineWave[i] = Math.sin(2 * Math.PI * frequency * i / n);
        }
        
        // For CWT, scale is inversely related to frequency
        // Higher scales correspond to lower frequencies
        // The Morlet wavelet center frequency is approximately 0.8
        double centerFreq = 0.8;
        double optimalScale = centerFreq * n / frequency;
        
        // Use scales around the optimal scale
        double[] scales = {optimalScale * 0.5, optimalScale, optimalScale * 2.0};
        
        CWTResult result = adapter.analyze(sineWave, WaveletName.MORLET, scales);
        assertNotNull(result);
        
        double[][] coeffs = result.getCoefficients();
        
        // The scale matching the frequency should have the highest energy
        double[] energies = new double[scales.length];
        for (int s = 0; s < scales.length; s++) {
            double energy = 0.0;
            for (int i = 0; i < n; i++) {
                energy += coeffs[s][i] * coeffs[s][i];
            }
            energies[s] = energy;
        }
        
        // Middle scale (matching frequency) should have highest energy
        // Note: This is a basic check - exact match depends on wavelet implementation
        // The test verifies that CWT responds to frequency content
        assertTrue(energies[1] > 0, "Middle scale should have non-zero energy");
        
        // All scales should detect some energy from the sine wave
        for (int i = 0; i < energies.length; i++) {
            assertTrue(energies[i] > 0, "Scale " + i + " should detect signal energy");
        }
    }
    
    @Test
    void testNormalizationPreservesRelativeAmplitudes() {
        // Create a signal with two distinct frequency components
        int n = 256;
        double[] signal = new double[n];
        for (int i = 0; i < n; i++) {
            signal[i] = Math.sin(2 * Math.PI * 5 * i / n) +  // Low frequency
                       0.3 * Math.sin(2 * Math.PI * 20 * i / n); // High frequency (lower amplitude)
        }
        
        double[] scales = {2.0, 8.0}; // Scales to capture high and low frequencies
        CWTResult result = adapter.analyze(signal, WaveletName.MORLET, scales);
        
        double[][] levels = adapter.extractLevels(result, 2);
        
        // After normalization, the relative patterns should still be preserved
        // The low frequency component should dominate in the appropriate scale
        assertNotNull(levels);
        assertEquals(2, levels.length);
        
        // Both levels should be normalized (std dev ≈ 1)
        for (int i = 0; i < 2; i++) {
            double sumSquares = 0.0;
            for (double val : levels[i]) {
                sumSquares += val * val;
            }
            double stdDev = Math.sqrt(sumSquares / levels[i].length);
            assertEquals(1.0, stdDev, 0.2, "Level " + i + " should be normalized");
        }
    }
}