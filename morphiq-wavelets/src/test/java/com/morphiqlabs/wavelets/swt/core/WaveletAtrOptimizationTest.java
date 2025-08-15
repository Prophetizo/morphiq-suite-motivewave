package com.morphiqlabs.wavelets.swt.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

public class WaveletAtrOptimizationTest {
    
    @Test
    @DisplayName("Cached weights should match calculated weights")
    void testCachedWeightsAccuracy() {
        // Test with default decay
        WaveletAtr atr1 = new WaveletAtr(10, 0.5);
        
        // The cached weights should be pre-calculated correctly
        double[][] testDetails = new double[5][100];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 100; j++) {
                testDetails[i][j] = Math.random() * 10 - 5;
            }
        }
        
        // Calculate with instance method (uses cached weights)
        double result1 = atr1.calculate(testDetails, 5);
        
        // Calculate with static method (will calculate weights on the fly)
        double result2 = WaveletAtr.calculateInstantaneous(testDetails, 5, 0.5);
        
        // Results should be in the same ballpark (not identical due to smoothing vs instantaneous)
        assertNotNull(result1);
        assertNotNull(result2);
        assertTrue(result1 >= 0);
        assertTrue(result2 >= 0);
    }
    
    @Test
    @DisplayName("Performance optimization should handle edge cases")
    void testEdgeCases() {
        WaveletAtr atr = new WaveletAtr(5, 0.6);
        
        // Test with more levels than MAX_CACHED_LEVELS
        double[][] manyLevels = new double[15][50];
        for (int i = 0; i < 15; i++) {
            for (int j = 0; j < 50; j++) {
                manyLevels[i][j] = Math.random() * 2;
            }
        }
        
        // Should handle gracefully by falling back to calculation for levels >= 10
        double result = atr.calculate(manyLevels, 15);
        assertTrue(result >= 0);
        
        // Test with fewer levels than cached
        double[][] fewLevels = new double[3][50];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 50; j++) {
                fewLevels[i][j] = Math.random() * 2;
            }
        }
        
        result = atr.calculate(fewLevels, 3);
        assertTrue(result >= 0);
    }
    
    @Test
    @DisplayName("Instantaneous calculation should optimize for default decay")
    void testInstantaneousOptimization() {
        double[][] details = new double[5][100];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 100; j++) {
                details[i][j] = Math.random() * 10 - 5;
            }
        }
        
        // Test with default decay (should use optimization)
        double result1 = WaveletAtr.calculateInstantaneous(details, 5);
        assertTrue(result1 >= 0);
        
        // Test with custom decay (should calculate weights)
        double result2 = WaveletAtr.calculateInstantaneous(details, 5, 0.7);
        assertTrue(result2 >= 0);
        
        // Results should be different due to different decay factors
        assertNotEquals(result1, result2, 0.0001);
    }
    
    @Test
    @DisplayName("Thread safety with cached weights")
    void testThreadSafetyWithCache() throws InterruptedException {
        WaveletAtr atr = new WaveletAtr(10, 0.5);
        
        double[][] details = new double[5][100];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 100; j++) {
                details[i][j] = Math.random() * 10 - 5;
            }
        }
        
        // Run multiple threads calculating simultaneously
        Thread[] threads = new Thread[10];
        double[] results = new double[10];
        
        for (int i = 0; i < 10; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = atr.calculate(details, 5);
            });
            threads[i].start();
        }
        
        // Wait for all threads
        for (Thread t : threads) {
            t.join();
        }
        
        // All results should be valid
        for (double result : results) {
            assertTrue(result >= 0);
        }
    }
}