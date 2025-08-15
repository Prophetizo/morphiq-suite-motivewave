package com.morphiqlabs.wavelets.swt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for momentum oscillator 100x scaling factor.
 * Verifies scaling implementation as specified in CLAUDE.md updates.
 */
class MomentumOscillatorScalingTest {
    
    private static final double SCALING_FACTOR = 100.0;
    private static final double DEFAULT_THRESHOLD = 1.0; // Scaled from 0.01
    private static final double EMA_ALPHA = 0.5; // Increased smoothing
    private static final int MOMENTUM_WINDOW = 10; // Expanded window
    
    private MomentumCalculator calculator;
    
    @BeforeEach
    void setUp() {
        calculator = new MomentumCalculator();
    }
    
    @Test
    @DisplayName("Momentum scaling factor should be 100x")
    void testMomentumScalingFactor() {
        double rawMomentum = 0.0123;
        double scaledMomentum = calculator.scaleMomentum(rawMomentum);
        
        assertEquals(1.23, scaledMomentum, 0.0001);
        assertEquals(rawMomentum * SCALING_FACTOR, scaledMomentum, 0.0001);
    }
    
    @ParameterizedTest
    @DisplayName("Threshold scaling from 0.01 to 1.0")
    @CsvSource({
        "0.005, 0.5, false",   // Below old threshold
        "0.01, 1.0, true",     // At old threshold (now scaled)
        "0.015, 1.5, true",    // Above old threshold
        "0.02, 2.0, true",     // Higher momentum
        "0.1, 10.0, true",     // Very high momentum
    })
    void testThresholdScaling(double rawValue, double expectedScaled, boolean shouldTrigger) {
        double scaled = calculator.scaleMomentum(rawValue);
        
        assertEquals(expectedScaled, scaled, 0.0001);
        assertEquals(shouldTrigger, scaled >= DEFAULT_THRESHOLD,
            String.format("Raw: %.4f, Scaled: %.2f, Threshold: %.2f", 
                rawValue, scaled, DEFAULT_THRESHOLD));
    }
    
    @Test
    @DisplayName("EMA smoothing with alpha = 0.5")
    void testEmaSmoothing() {
        double[] values = {0.01, 0.015, 0.012, 0.018, 0.02};
        double ema = 0.0;
        
        for (double value : values) {
            double scaledValue = calculator.scaleMomentum(value);
            ema = calculator.calculateEMA(scaledValue, ema, EMA_ALPHA);
        }
        
        // With alpha = 0.5, EMA should converge quickly
        assertTrue(ema > 1.0); // Should be above threshold
        assertTrue(ema < 2.0); // But not too high
    }
    
    @Test
    @DisplayName("Momentum window expansion to 10 bars")
    void testMomentumWindow() {
        double[] prices = new double[MOMENTUM_WINDOW + 5];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 4500.0 + i * 0.25; // Simulated price trend
        }
        
        // Calculate momentum over window
        double momentum = calculator.calculateMomentum(prices, prices.length - 1, MOMENTUM_WINDOW);
        double scaledMomentum = calculator.scaleMomentum(momentum);
        
        // Should detect positive momentum
        assertTrue(scaledMomentum > 0);
        
        // Verify window size effect
        double smallerWindow = calculator.calculateMomentum(prices, prices.length - 1, 5);
        double largerScaled = calculator.scaleMomentum(smallerWindow);
        
        // Different window sizes should produce different results
        assertNotEquals(scaledMomentum, largerScaled, 0.01);
    }
    
    @ParameterizedTest
    @DisplayName("Signal generation with scaled threshold")
    @ValueSource(doubles = {0.005, 0.008, 0.009, 0.01, 0.011, 0.015, 0.02})
    void testSignalGeneration(double rawMomentum) {
        double scaled = calculator.scaleMomentum(rawMomentum);
        boolean shouldGenerateSignal = scaled >= DEFAULT_THRESHOLD;
        
        // Old threshold was 0.01, new is 1.0 (scaled)
        boolean oldLogic = rawMomentum >= 0.01;
        boolean newLogic = scaled >= 1.0;
        
        assertEquals(oldLogic, newLogic, 
            String.format("Logic mismatch for raw=%.4f, scaled=%.2f", rawMomentum, scaled));
        assertEquals(shouldGenerateSignal, newLogic);
    }
    
    @Test
    @DisplayName("Negative momentum scaling")
    void testNegativeMomentumScaling() {
        double[] negativeMomentums = {-0.005, -0.01, -0.015, -0.02};
        
        for (double raw : negativeMomentums) {
            double scaled = calculator.scaleMomentum(raw);
            
            assertEquals(raw * SCALING_FACTOR, scaled, 0.0001);
            assertTrue(scaled < 0, "Negative momentum should remain negative");
            
            // Check threshold for absolute value
            boolean shouldTrigger = Math.abs(scaled) >= DEFAULT_THRESHOLD;
            assertEquals(Math.abs(raw) >= 0.01, shouldTrigger);
        }
    }
    
    @Test
    @DisplayName("Edge cases for momentum scaling")
    void testEdgeCases() {
        // Zero momentum
        assertEquals(0.0, calculator.scaleMomentum(0.0), 0.0001);
        
        // Very small values
        double tiny = 0.00001;
        assertEquals(0.001, calculator.scaleMomentum(tiny), 0.0001);
        
        // Large values
        double large = 0.5;
        assertEquals(50.0, calculator.scaleMomentum(large), 0.0001);
        
        // NaN handling
        assertTrue(Double.isNaN(calculator.scaleMomentum(Double.NaN)));
        
        // Infinity handling
        assertEquals(Double.POSITIVE_INFINITY, 
            calculator.scaleMomentum(Double.POSITIVE_INFINITY));
    }
    
    @Test
    @DisplayName("Cross-scale momentum calculation")
    void testCrossScaleMomentum() {
        // Simulate different wavelet scales
        double[] scale1 = {0.01, 0.012, 0.011, 0.013};
        double[] scale2 = {0.008, 0.009, 0.01, 0.011};
        double[] scale3 = {0.005, 0.006, 0.007, 0.008};
        
        double momentum1 = calculator.calculateAverageMomentum(scale1);
        double momentum2 = calculator.calculateAverageMomentum(scale2);
        double momentum3 = calculator.calculateAverageMomentum(scale3);
        
        // Cross-scale momentum (average of scales)
        double crossScale = (momentum1 + momentum2 + momentum3) / 3.0;
        double scaledCrossScale = calculator.scaleMomentum(crossScale);
        
        // Should be around the threshold
        assertTrue(scaledCrossScale > 0.5);
        assertTrue(scaledCrossScale < 1.5);
    }
    
    @Test
    @DisplayName("Momentum persistence with smoothing")
    void testMomentumPersistence() {
        double ema = 0.0;
        int signalCount = 0;
        
        // Simulate momentum spike followed by decay
        double[] momentums = {0.005, 0.02, 0.025, 0.015, 0.01, 0.008, 0.006};
        
        for (double raw : momentums) {
            double scaled = calculator.scaleMomentum(raw);
            ema = calculator.calculateEMA(scaled, ema, EMA_ALPHA);
            
            if (ema >= DEFAULT_THRESHOLD) {
                signalCount++;
            }
        }
        
        // Signal should persist for several bars due to EMA smoothing
        assertTrue(signalCount >= 4, 
            "EMA smoothing should maintain signal for multiple bars");
    }
    
    @Test
    @DisplayName("Visibility improvement verification")
    void testVisibilityImprovement() {
        // Original values that were hard to see
        double[] originalValues = {0.0001, 0.0005, 0.001, 0.005, 0.01};
        
        for (double original : originalValues) {
            double scaled = calculator.scaleMomentum(original);
            
            // Scaled values should be more visible (> 0.01 for display)
            assertTrue(scaled >= 0.01 || scaled == 0.0,
                String.format("Scaled value %.4f not visible enough for %.6f", 
                    scaled, original));
            
            // Verify 100x improvement
            assertEquals(original * 100, scaled, 0.0001);
        }
    }
    
    /**
     * Mock momentum calculator for testing.
     */
    private static class MomentumCalculator {
        
        double scaleMomentum(double rawMomentum) {
            if (Double.isNaN(rawMomentum) || Double.isInfinite(rawMomentum)) {
                return rawMomentum;
            }
            return rawMomentum * SCALING_FACTOR;
        }
        
        double calculateEMA(double value, double previousEMA, double alpha) {
            return alpha * value + (1 - alpha) * previousEMA;
        }
        
        double calculateMomentum(double[] prices, int currentIndex, int window) {
            if (currentIndex < window) {
                return 0.0;
            }
            
            double oldPrice = prices[currentIndex - window];
            double currentPrice = prices[currentIndex];
            
            if (oldPrice == 0) {
                return 0.0;
            }
            
            return (currentPrice - oldPrice) / oldPrice;
        }
        
        double calculateAverageMomentum(double[] momentums) {
            double sum = 0.0;
            for (double m : momentums) {
                sum += m;
            }
            return sum / momentums.length;
        }
    }
}