package com.morphiqlabs.wavelets.swt.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Random;

/**
 * Unit tests for wavelet thresholding methods.
 * Uses separate Random instances per test method to support parallel test execution.
 */
public class ThresholdsTest {
    
    /**
     * Fixed seed for reproducible test results.
     * Each test method creates its own Random instance with this seed.
     */
    private static final long TEST_SEED = 42L;
    
    @Test
    void testUniversalThreshold() {
        // Create test-specific Random instance for thread safety
        Random random = new Random(TEST_SEED);
        
        // Test with known noise characteristics
        double[] noise = generateGaussianNoise(1000, 0.0, 1.0, random);
        
        double threshold = Thresholds.calculateUniversalThreshold(noise);
        
        // Universal threshold should be around σ * sqrt(2 * log(N))
        double expectedRange = Math.sqrt(2.0 * Math.log(1000)); // ≈ 3.7
        assertTrue(threshold > 2.5 && threshold < 5.0, 
                  "Universal threshold out of expected range: " + threshold);
    }
    
    @Test
    void testBayesThreshold() {
        // Create test-specific Random instance for thread safety
        Random random = new Random(TEST_SEED);
        
        // Create signal with different noise levels
        double[] signal = generateMixedSignal(512, random);
        
        double threshold1 = Thresholds.calculateBayesThreshold(signal, 1);
        double threshold2 = Thresholds.calculateBayesThreshold(signal, 2);
        double threshold3 = Thresholds.calculateBayesThreshold(signal, 3);
        
        // Higher levels should generally have higher thresholds (level scaling)
        assertTrue(threshold2 >= threshold1 * 0.9, "Level scaling not working");
        assertTrue(threshold3 >= threshold2 * 0.9, "Level scaling not working");
    }
    
    @Test
    void testSureThreshold() {
        double[] coeffs = {0.1, 0.5, 0.05, 0.8, 0.02, 0.3, 0.01, 0.6};
        
        double threshold = Thresholds.calculateSureThreshold(coeffs);
        
        assertTrue(threshold > 0, "SURE threshold should be positive");
        assertTrue(threshold < 1.0, "SURE threshold too large");
    }
    
    @Test
    void testNoiseEstimation() {
        // Create test-specific Random instance for thread safety
        Random random = new Random(TEST_SEED);
        
        // Test MAD-based noise estimation
        double[] pureNoise = generateGaussianNoise(1000, 0.0, 2.0, random); // σ = 2.0
        
        double estimatedSigma = Thresholds.estimateNoiseSigma(pureNoise);
        
        // Should be close to true σ = 2.0
        assertTrue(Math.abs(estimatedSigma - 2.0) < 0.5, 
                  "Noise estimation inaccurate: " + estimatedSigma + " vs 2.0");
    }
    
    @Test
    void testSoftThresholding() {
        double[] coeffs = {-0.5, -0.1, 0.0, 0.1, 0.3, 0.8};
        double threshold = 0.2;
        
        double[] result = Thresholds.applyThreshold(coeffs, threshold, Thresholds.ShrinkageType.SOFT);
        
        assertEquals(-0.3, result[0], 1e-10); // -0.5 + 0.2 = -0.3
        assertEquals(0.0, result[1], 1e-10);  // |-0.1| <= 0.2
        assertEquals(0.0, result[2], 1e-10);  // 0.0
        assertEquals(0.0, result[3], 1e-10);  // |0.1| <= 0.2
        assertEquals(0.1, result[4], 1e-10);  // 0.3 - 0.2 = 0.1
        assertEquals(0.6, result[5], 1e-10);  // 0.8 - 0.2 = 0.6
    }
    
    @Test
    void testHardThresholding() {
        double[] coeffs = {-0.5, -0.1, 0.0, 0.1, 0.3, 0.8};
        double threshold = 0.2;
        
        double[] result = Thresholds.applyThreshold(coeffs, threshold, Thresholds.ShrinkageType.HARD);
        
        assertEquals(-0.5, result[0], 1e-10); // |-0.5| > 0.2
        assertEquals(0.0, result[1], 1e-10);  // |-0.1| <= 0.2
        assertEquals(0.0, result[2], 1e-10);  // 0.0
        assertEquals(0.0, result[3], 1e-10);  // |0.1| <= 0.2
        assertEquals(0.3, result[4], 1e-10);  // |0.3| > 0.2
        assertEquals(0.8, result[5], 1e-10);  // |0.8| > 0.2
    }
    
    @Test
    void testThresholdMethodParsing() {
        assertEquals(Thresholds.ThresholdMethod.UNIVERSAL, 
                    Thresholds.ThresholdMethod.fromString("Universal"));
        assertEquals(Thresholds.ThresholdMethod.BAYES, 
                    Thresholds.ThresholdMethod.fromString("bayes"));
        assertEquals(Thresholds.ThresholdMethod.SURE, 
                    Thresholds.ThresholdMethod.fromString("SURE"));
        
        // Test fallback
        assertEquals(Thresholds.ThresholdMethod.UNIVERSAL, 
                    Thresholds.ThresholdMethod.fromString("unknown"));
    }
    
    @Test
    void testShrinkageTypeParsing() {
        assertEquals(Thresholds.ShrinkageType.SOFT, 
                    Thresholds.ShrinkageType.fromString("Soft"));
        assertEquals(Thresholds.ShrinkageType.HARD, 
                    Thresholds.ShrinkageType.fromString("hard"));
        
        // Test fallback
        assertEquals(Thresholds.ShrinkageType.SOFT, 
                    Thresholds.ShrinkageType.fromString("unknown"));
    }
    
    @Test
    void testAutoSelectMethod() {
        // Create test-specific Random instance for thread safety
        Random random = new Random(TEST_SEED);
        
        // High noise scenario
        double[] highNoise = generateGaussianNoise(100, 0.0, 5.0, random);
        Thresholds.ThresholdMethod method1 = Thresholds.autoSelectMethod(highNoise, 1);
        assertEquals(Thresholds.ThresholdMethod.UNIVERSAL, method1);
        
        // Small data scenario
        double[] smallData = new double[16];
        Arrays.fill(smallData, 1.0);
        Thresholds.ThresholdMethod method2 = Thresholds.autoSelectMethod(smallData, 1);
        assertEquals(Thresholds.ThresholdMethod.UNIVERSAL, method2);
    }
    
    @Test
    void testEdgeCases() {
        // Empty array
        double threshold1 = Thresholds.calculateUniversalThreshold(new double[0]);
        assertEquals(0.0, threshold1);
        
        // Single element
        double[] single = {1.0};
        double threshold2 = Thresholds.calculateUniversalThreshold(single);
        assertTrue(threshold2 >= 0);
        
        // All zeros
        double[] zeros = new double[100];
        double threshold3 = Thresholds.calculateUniversalThreshold(zeros);
        assertTrue(threshold3 >= 0);
    }
    
    @Test
    void testThresholdConsistency() {
        // Create test-specific Random instance for thread safety
        Random random = new Random(TEST_SEED);
        
        // Same data should give same thresholds
        double[] data = generateMixedSignal(256, random);
        
        double t1 = Thresholds.calculateThreshold(data, Thresholds.ThresholdMethod.UNIVERSAL, 1);
        double t2 = Thresholds.calculateThreshold(data, Thresholds.ThresholdMethod.UNIVERSAL, 1);
        
        assertEquals(t1, t2, 1e-10);
    }
    
    // Helper methods
    
    private double[] generateGaussianNoise(int length, double mean, double stdDev, Random random) {
        double[] noise = new double[length];
        
        for (int i = 0; i < length; i++) {
            noise[i] = mean + stdDev * random.nextGaussian();
        }
        
        return noise;
    }
    
    private double[] generateMixedSignal(int length, Random random) {
        double[] signal = new double[length];
        
        for (int i = 0; i < length; i++) {
            // Signal component (smooth)
            double s = Math.sin(2 * Math.PI * i / 32) + 0.5 * Math.sin(2 * Math.PI * i / 8);
            
            // Noise component
            double n = 0.2 * random.nextGaussian();
            
            signal[i] = s + n;
        }
        
        return signal;
    }
}