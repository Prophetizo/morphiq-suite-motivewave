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
        
        // Verify each level has data
        for (int i = 0; i < numLevels; i++) {
            assertNotNull(levels[i]);
            assertEquals(testSignal.length, levels[i].length);
            
            // Check for non-zero values
            boolean hasNonZero = false;
            for (double val : levels[i]) {
                if (val != 0.0) {
                    hasNonZero = true;
                    break;
                }
            }
            assertTrue(hasNonZero, "Level " + i + " should contain non-zero values");
        }
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
}