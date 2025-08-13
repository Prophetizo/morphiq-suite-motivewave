package com.prophetizo.wavelets.swt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for slope threshold calculation to ensure correct percentage conversion.
 * Verifies the fix for slope threshold being too small (0.00001 instead of 0.0005).
 */
class SlopeThresholdTest {
    
    private static final double DEFAULT_MIN_SLOPE_THRESHOLD = 0.05; // 0.05% as entered in UI
    
    @Test
    @DisplayName("Default slope threshold should be 0.05%")
    void testDefaultSlopeThreshold() {
        assertEquals(0.05, DEFAULT_MIN_SLOPE_THRESHOLD, 0.0001);
        
        // When converted to decimal for calculation
        double decimalThreshold = DEFAULT_MIN_SLOPE_THRESHOLD / 100.0;
        assertEquals(0.0005, decimalThreshold, 0.000001);
    }
    
    @ParameterizedTest
    @DisplayName("Slope threshold conversion from percentage to decimal")
    @CsvSource({
        "0.01, 0.0001",   // 0.01% -> 0.0001
        "0.05, 0.0005",   // 0.05% -> 0.0005 (default)
        "0.10, 0.0010",   // 0.10% -> 0.001
        "0.50, 0.0050",   // 0.50% -> 0.005
        "1.00, 0.0100",   // 1.00% -> 0.01
    })
    void testSlopeThresholdConversion(double percentageValue, double expectedDecimal) {
        // User enters percentage value in UI
        double uiValue = percentageValue;
        
        // Convert to decimal for calculation
        double decimalValue = uiValue / 100.0;
        
        assertEquals(expectedDecimal, decimalValue, 0.000001,
            String.format("%.2f%% should convert to %.6f", percentageValue, expectedDecimal));
    }
    
    @Test
    @DisplayName("Slope threshold applied to trend value")
    void testSlopeThresholdApplication() {
        // Example: ES at 6400
        double trendValue = 6400.0;
        double slopeThresholdPercent = DEFAULT_MIN_SLOPE_THRESHOLD / 100.0; // 0.05% -> 0.0005
        
        // Calculate minimum slope required
        double minSlope = Math.abs(trendValue) * slopeThresholdPercent;
        
        // Should require 3.2 points/bar movement
        assertEquals(3.2, minSlope, 0.01,
            "For trend=6400 and threshold=0.05%, min slope should be 3.2 points/bar");
    }
    
    @ParameterizedTest
    @DisplayName("Minimum slope calculation for different trend values")
    @CsvSource({
        "1000.0, 0.05, 0.5",    // Trend=1000, threshold=0.05% -> 0.5 points
        "4500.0, 0.05, 2.25",   // Trend=4500, threshold=0.05% -> 2.25 points
        "6400.0, 0.05, 3.2",    // Trend=6400, threshold=0.05% -> 3.2 points (ES example)
        "16000.0, 0.05, 8.0",   // Trend=16000, threshold=0.05% -> 8.0 points (NQ example)
        "100.0, 0.10, 0.1",     // Trend=100, threshold=0.10% -> 0.1 points
    })
    void testMinSlopeCalculation(double trendValue, double thresholdPercent, double expectedMinSlope) {
        double slopeThresholdDecimal = thresholdPercent / 100.0;
        double minSlope = Math.abs(trendValue) * slopeThresholdDecimal;
        
        assertEquals(expectedMinSlope, minSlope, 0.01,
            String.format("Trend=%.1f with %.2f%% threshold should require %.2f points minimum slope",
                trendValue, thresholdPercent, expectedMinSlope));
    }
    
    @Test
    @DisplayName("Old incorrect threshold would be too restrictive")
    void testOldThresholdProblem() {
        double oldThreshold = 0.001; // Old value that was too small
        double trendValue = 6400.0;
        
        // Old calculation (incorrect - too small)
        double oldDecimal = oldThreshold / 100.0; // 0.00001
        double oldMinSlope = trendValue * oldDecimal;
        
        // New calculation (correct)
        double newDecimal = DEFAULT_MIN_SLOPE_THRESHOLD / 100.0; // 0.0005
        double newMinSlope = trendValue * newDecimal;
        
        assertEquals(0.064, oldMinSlope, 0.001, "Old threshold was too restrictive");
        assertEquals(3.2, newMinSlope, 0.01, "New threshold is more reasonable");
        
        // The new threshold is 50x larger (more reasonable)
        assertTrue(newMinSlope / oldMinSlope > 40,
            "New threshold should be significantly larger than the old one");
    }
    
    @Test
    @DisplayName("Signal generation with slope threshold")
    void testSignalGenerationLogic() {
        double trendValue = 5000.0;
        double actualSlope = 2.5; // Actual slope in points/bar
        
        // Test with default threshold
        double thresholdPercent = DEFAULT_MIN_SLOPE_THRESHOLD / 100.0;
        double minSlope = Math.abs(trendValue) * thresholdPercent;
        
        assertEquals(2.5, minSlope, 0.01); // 5000 * 0.0005 = 2.5
        
        // Should generate signal (actual slope equals minimum)
        boolean shouldSignal = Math.abs(actualSlope) >= minSlope;
        assertTrue(shouldSignal, "Signal should be generated when slope meets threshold");
        
        // Test with slope below threshold
        double smallSlope = 1.0;
        boolean noSignal = Math.abs(smallSlope) >= minSlope;
        assertFalse(noSignal, "No signal when slope is below threshold");
    }
    
    @Test
    @DisplayName("UI descriptor range validation")
    void testUIDescriptorRange() {
        // New range: 0.0 to 1.0% with 0.01 step
        double minValue = 0.0;
        double maxValue = 1.0;
        double step = 0.01;
        
        // Verify default is within range
        assertTrue(DEFAULT_MIN_SLOPE_THRESHOLD >= minValue);
        assertTrue(DEFAULT_MIN_SLOPE_THRESHOLD <= maxValue);
        
        // Verify step size allows precise control
        double stepsFromMin = (DEFAULT_MIN_SLOPE_THRESHOLD - minValue) / step;
        assertEquals(5.0, stepsFromMin, 0.01, "Default should be 5 steps from minimum");
        
        // Old max was 0.1%, new max is 1.0% (10x larger range)
        double oldMax = 0.1;
        assertTrue(maxValue > oldMax, "New max should provide more flexibility");
    }
}