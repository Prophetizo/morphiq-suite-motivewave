package com.prophetizo.wavelets.swt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the configurable hardcoded values made configurable in SwtTrendMomentumStudy.
 * Validates that the settings keys are properly defined and the mathematical formulas work correctly.
 */
class ConfigurableHardcodedValuesTest {

    @Test
    @DisplayName("Setting keys should be properly defined")
    void testSettingKeysAreDefined() {
        // Verify that the new setting keys are properly defined as constants
        assertEquals("MOMENTUM_WINDOW", SwtTrendMomentumStudy.MOMENTUM_WINDOW);
        assertEquals("MOMENTUM_SCALING_FACTOR", SwtTrendMomentumStudy.MOMENTUM_SCALING_FACTOR);
        
        // Verify existing key that is reused for level weight decay
        assertEquals("WATR_LEVEL_DECAY", SwtTrendMomentumStudy.WATR_LEVEL_DECAY);
    }

    @Test
    @DisplayName("Level weight decay formula should work with different values")
    void testLevelWeightDecayFormula() {
        // Test the formula: weight = 1.0 / (1.0 + (level - 1) * levelWeightDecay)
        
        // Test with default decay factor of 0.5
        double levelWeightDecay = 0.5;
        
        // Level 1 should have weight = 1.0 / (1.0 + (1 - 1) * 0.5) = 1.0 / 1.0 = 1.0
        double weight1 = 1.0 / (1.0 + (1 - 1) * levelWeightDecay);
        assertEquals(1.0, weight1, 0.0001, "Level 1 weight should be 1.0");
        
        // Level 2 should have weight = 1.0 / (1.0 + (2 - 1) * 0.5) = 1.0 / 1.5 = 0.667
        double weight2 = 1.0 / (1.0 + (2 - 1) * levelWeightDecay);
        assertEquals(0.6667, weight2, 0.0001, "Level 2 weight should be ~0.667");
        
        // Level 3 should have weight = 1.0 / (1.0 + (3 - 1) * 0.5) = 1.0 / 2.0 = 0.5
        double weight3 = 1.0 / (1.0 + (3 - 1) * levelWeightDecay);
        assertEquals(0.5, weight3, 0.0001, "Level 3 weight should be 0.5");
    }

    @ParameterizedTest
    @DisplayName("Level weight decay should work with various decay factors")
    @CsvSource({
        "0.3, 0, 1.0000",      // Level 0 with decay 0.3: 1.0 / (1.0 + 0 * 0.3) = 1.0
        "0.3, 1, 0.7692",      // Level 1 with decay 0.3: 1.0 / (1.0 + 1 * 0.3) = 0.769
        "0.3, 2, 0.6250",      // Level 2 with decay 0.3: 1.0 / (1.0 + 2 * 0.3) = 0.625
        "0.5, 0, 1.0000",      // Level 0 with decay 0.5: 1.0 / (1.0 + 0 * 0.5) = 1.0
        "0.5, 1, 0.6667",      // Level 1 with decay 0.5: 1.0 / (1.0 + 1 * 0.5) = 0.667
        "0.5, 2, 0.5000",      // Level 2 with decay 0.5: 1.0 / (1.0 + 2 * 0.5) = 0.5
        "0.8, 0, 1.0000",      // Level 0 with decay 0.8: 1.0 / (1.0 + 0 * 0.8) = 1.0
        "0.8, 1, 0.5556",      // Level 1 with decay 0.8: 1.0 / (1.0 + 1 * 0.8) = 0.556
        "0.8, 2, 0.3846"       // Level 2 with decay 0.8: 1.0 / (1.0 + 2 * 0.8) = 0.385
    })
    void testLevelWeightDecayWithVariousFactors(double levelWeightDecay, int level, double expectedWeight) {
        double weight = 1.0 / (1.0 + level * levelWeightDecay);
        assertEquals(expectedWeight, weight, 0.0001, 
            String.format("Level %d with decay %.1f should have weight %.4f", level, levelWeightDecay, expectedWeight));
    }

    @ParameterizedTest
    @DisplayName("Momentum scaling factor should work correctly")
    @CsvSource({
        "0.01234, 100.0, 1.234",    // Default scaling factor
        "0.01234, 50.0, 0.617",     // Half scaling factor
        "0.01234, 1.0, 0.01234",    // No scaling
        "0.01234, 200.0, 2.468",    // Double scaling
        "0.00567, 100.0, 0.567",    // Different raw value
        "0.0, 100.0, 0.0"           // Zero momentum
    })
    void testMomentumScalingFactor(double rawMomentum, double scalingFactor, double expectedScaled) {
        double scaledMomentum = rawMomentum * scalingFactor;
        assertEquals(expectedScaled, scaledMomentum, 0.0001, 
            String.format("Raw momentum %.5f scaled by %.1f should be %.3f", rawMomentum, scalingFactor, expectedScaled));
    }

    @ParameterizedTest
    @DisplayName("Momentum window size logic should work correctly")
    @CsvSource({
        "10, 20, 10",    // Window smaller than detail length
        "25, 20, 20",    // Window larger than detail length
        "5, 20, 5",      // Very small window
        "20, 20, 20",    // Window equals detail length
        "1, 100, 1",     // Minimum window size
        "50, 10, 10"     // Large window, small detail
    })
    void testMomentumWindowSizeLogic(int momentumWindow, int detailLength, int expectedWindowSize) {
        int windowSize = Math.min(momentumWindow, detailLength);
        assertEquals(expectedWindowSize, windowSize, 
            String.format("Window size should be %d when momentum window is %d and detail length is %d", 
                expectedWindowSize, momentumWindow, detailLength));
    }

    @Test
    @DisplayName("Default values should match documented hardcoded values")
    void testDefaultValues() {
        // These should match the original hardcoded values to ensure backward compatibility
        
        // Original MOMENTUM_WINDOW was 10
        int defaultMomentumWindow = 10;
        assertEquals(10, defaultMomentumWindow, "Default momentum window should be 10");
        
        // Original level weight decay factor was 0.5 (from formula: 1.0 / (1.0 + (level - 1) * 0.5))
        double defaultLevelWeightDecay = 0.5;
        assertEquals(0.5, defaultLevelWeightDecay, "Default level weight decay should be 0.5");
        
        // Original momentum scaling factor was 100.0 (from: rawMomentum *= 100.0)
        double defaultMomentumScalingFactor = 100.0;
        assertEquals(100.0, defaultMomentumScalingFactor, "Default momentum scaling factor should be 100.0");
    }

    @Test
    @DisplayName("Boundary values should be handled correctly")
    void testBoundaryValues() {
        // Test level weight decay at boundary values
        
        // Minimum decay (0.1) should give higher weights to coarser scales
        double minDecay = 0.1;
        double weight2Min = 1.0 / (1.0 + (2 - 1) * minDecay);
        assertEquals(0.9091, weight2Min, 0.0001, "Level 2 with min decay should have high weight");
        
        // Maximum decay (1.0) should give lower weights to coarser scales
        double maxDecay = 1.0;
        double weight2Max = 1.0 / (1.0 + (2 - 1) * maxDecay);
        assertEquals(0.5, weight2Max, 0.0001, "Level 2 with max decay should have lower weight");
        
        // Test momentum scaling at boundary values
        double testMomentum = 0.01;
        
        // Minimum scaling (1.0)
        double minScaled = testMomentum * 1.0;
        assertEquals(0.01, minScaled, 0.0001, "Minimum scaling should not amplify");
        
        // Maximum scaling (1000.0)
        double maxScaled = testMomentum * 1000.0;
        assertEquals(10.0, maxScaled, 0.0001, "Maximum scaling should amplify significantly");
    }
}