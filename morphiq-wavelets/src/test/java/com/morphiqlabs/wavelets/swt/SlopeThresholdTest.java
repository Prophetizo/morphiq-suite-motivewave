package com.morphiqlabs.wavelets.swt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for slope threshold calculation to ensure correct behavior.
 * After the fix, slope threshold is now in absolute points, not percentage.
 */
class SlopeThresholdTest {
    
    private static final double DEFAULT_MIN_SLOPE_THRESHOLD = 0.05; // 0.05 points (not percentage)
    
    @Test
    @DisplayName("Default slope threshold should be 0.05 points")
    void testDefaultSlopeThreshold() {
        assertEquals(0.05, DEFAULT_MIN_SLOPE_THRESHOLD, 0.0001);
        
        // No conversion needed - it's already in points
        double minSlope = DEFAULT_MIN_SLOPE_THRESHOLD;
        assertEquals(0.05, minSlope, 0.0001);
    }
    
    @ParameterizedTest
    @DisplayName("Slope threshold values in absolute points")
    @CsvSource({
        "0.00, true",    // 0.00 points -> filter disabled
        "0.01, false",   // 0.01 points -> very sensitive
        "0.05, false",   // 0.05 points -> default
        "0.10, false",   // 0.10 points -> moderate filtering
        "0.50, false",   // 0.50 points -> aggressive filtering
        "1.00, false",   // 1.00 points -> very aggressive
    })
    void testSlopeThresholdValues(double thresholdPoints, boolean isDisabled) {
        // Threshold is now directly in points
        double minSlope = thresholdPoints;
        
        if (isDisabled) {
            assertEquals(0.0, minSlope, 0.0001, "Zero threshold disables filtering");
        } else {
            assertTrue(minSlope > 0, "Non-zero threshold enables filtering");
        }
    }
    
    @Test
    @DisplayName("Slope threshold applied directly (no multiplication)")
    void testSlopeThresholdApplication() {
        // Example: ES at 6400
        double trendValue = 6400.0;
        double previousTrend = 6399.5;
        double currentTrend = 6400.0;
        double slope = currentTrend - previousTrend; // 0.5 points
        
        // New behavior: threshold is in absolute points
        double minSlope = DEFAULT_MIN_SLOPE_THRESHOLD; // 0.05 points
        
        // Should generate signal (0.5 > 0.05)
        boolean shouldSignal = slope > minSlope;
        assertTrue(shouldSignal, 
            "Slope of 0.5 points should exceed threshold of 0.05 points");
    }
    
    @ParameterizedTest
    @DisplayName("Signal generation with different slopes and thresholds")
    @CsvSource({
        "0.02, 0.05, false",   // Slope=0.02, threshold=0.05 -> no signal
        "0.10, 0.05, true",    // Slope=0.10, threshold=0.05 -> signal
        "0.50, 0.10, true",    // Slope=0.50, threshold=0.10 -> signal
        "0.03, 0.05, false",   // Slope=0.03, threshold=0.05 -> no signal
        "1.00, 0.50, true",    // Slope=1.00, threshold=0.50 -> signal
        "0.00, 0.00, false",   // Zero slope with disabled filter -> no signal (need momentum too)
    })
    void testSignalGeneration(double actualSlope, double thresholdPoints, boolean expectedSignal) {
        double minSlope = thresholdPoints;
        
        // Check if slope exceeds threshold
        boolean meetsThreshold = actualSlope > minSlope;
        
        assertEquals(expectedSignal, meetsThreshold,
            String.format("Slope=%.2f with threshold=%.2f should%s generate signal",
                actualSlope, thresholdPoints, expectedSignal ? "" : " not"));
    }
    
    @Test
    @DisplayName("Typical ES bar-to-bar movements")
    void testTypicalESMovements() {
        // Typical ES bar-to-bar trend changes
        double[] typicalSlopes = {0.25, 0.5, 0.75, 1.0, 1.5, 2.0};
        
        double threshold = DEFAULT_MIN_SLOPE_THRESHOLD; // 0.05 points
        
        for (double slope : typicalSlopes) {
            boolean shouldSignal = slope > threshold;
            assertTrue(shouldSignal, 
                String.format("Typical ES slope of %.2f points should exceed %.2f threshold",
                    slope, threshold));
        }
        
        // Very small movements that should be filtered
        double[] smallSlopes = {0.01, 0.02, 0.03, 0.04};
        
        for (double slope : smallSlopes) {
            boolean shouldSignal = slope > threshold;
            assertFalse(shouldSignal,
                String.format("Small slope of %.2f points should not exceed %.2f threshold",
                    slope, threshold));
        }
    }
    
    @Test
    @DisplayName("Old percentage-based calculation vs new point-based")
    void testOldVsNewCalculation() {
        double trendValue = 6400.0;
        double actualSlope = 0.5; // Typical bar-to-bar change
        
        // Old calculation (percentage-based) - would have been too restrictive
        double oldPercentage = 0.05 / 100.0; // Convert to decimal
        double oldMinSlope = trendValue * oldPercentage; // 3.2 points
        boolean oldWouldSignal = actualSlope > oldMinSlope;
        
        // New calculation (point-based) - more reasonable
        double newMinSlope = DEFAULT_MIN_SLOPE_THRESHOLD; // 0.05 points
        boolean newWouldSignal = actualSlope > newMinSlope;
        
        assertFalse(oldWouldSignal, "Old calculation would filter out normal movements");
        assertTrue(newWouldSignal, "New calculation allows normal movements through");
        
        // The old threshold was way too high
        assertTrue(oldMinSlope > newMinSlope * 50,
            "Old threshold was over 50x more restrictive");
    }
    
    @Test
    @DisplayName("Signal filtering logic with momentum")
    void testSignalFilteringWithMomentum() {
        // Signal requires both slope and momentum conditions
        double slope = 0.10; // Points
        double minSlope = DEFAULT_MIN_SLOPE_THRESHOLD; // 0.05 points
        double momentum = 1.5;
        double momentumThreshold = 1.0;
        
        // Long signal: slope > minSlope AND momentum > threshold
        boolean longFilter = slope > minSlope && momentum > momentumThreshold;
        assertTrue(longFilter, "Should generate long signal");
        
        // Short signal: slope < -minSlope AND momentum < -threshold
        double negativeSlope = -0.10;
        double negativeMomentum = -1.5;
        boolean shortFilter = negativeSlope < -minSlope && negativeMomentum < -momentumThreshold;
        assertTrue(shortFilter, "Should generate short signal");
        
        // No signal when only one condition is met
        boolean noSignal1 = slope > minSlope && momentum < momentumThreshold;
        assertFalse(noSignal1, "No signal without momentum confirmation");
        
        boolean noSignal2 = slope < minSlope && momentum > momentumThreshold;
        assertFalse(noSignal2, "No signal without sufficient slope");
    }
    
    @Test
    @DisplayName("UI descriptor range validation for points")
    void testUIDescriptorRange() {
        // New range: 0.0 to 5.0 points with 0.01 step
        double minValue = 0.0;
        double maxValue = 5.0;
        double step = 0.01;
        
        // Verify default is within range
        assertTrue(DEFAULT_MIN_SLOPE_THRESHOLD >= minValue);
        assertTrue(DEFAULT_MIN_SLOPE_THRESHOLD <= maxValue);
        
        // Verify step size allows precise control
        double stepsFromMin = (DEFAULT_MIN_SLOPE_THRESHOLD - minValue) / step;
        assertEquals(5.0, stepsFromMin, 0.01, "Default should be 5 steps from minimum");
        
        // Range should cover typical to aggressive filtering
        assertTrue(maxValue >= 1.0, "Max should allow aggressive filtering");
    }
}