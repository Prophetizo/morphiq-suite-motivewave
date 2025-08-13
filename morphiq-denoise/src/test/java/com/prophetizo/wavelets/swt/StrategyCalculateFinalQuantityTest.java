package com.prophetizo.wavelets.swt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for position size calculation logic.
 * Tests the static calculatePositionSize method which is the core logic
 * used by calculateFinalQuantity.
 * 
 * Note: The calculateFinalQuantity instance method is tested indirectly
 * through integration tests that can properly initialize the MotiveWave SDK
 * components. This test focuses on the core business logic.
 */
class StrategyCalculateFinalQuantityTest {
    
    @Test
    @DisplayName("calculatePositionSize static method should multiply correctly")
    void testCalculatePositionSizeStatic() {
        // Test the static helper method directly
        assertEquals(1, SwtTrendMomentumStrategy.calculatePositionSize(1, 1), 
            "1 position * 1 lot = 1");
        assertEquals(6, SwtTrendMomentumStrategy.calculatePositionSize(2, 3),
            "2 positions * 3 lots = 6");
        assertEquals(10, SwtTrendMomentumStrategy.calculatePositionSize(5, 2),
            "5 positions * 2 lots = 10");
        assertEquals(0, SwtTrendMomentumStrategy.calculatePositionSize(0, 10),
            "0 positions * 10 lots = 0");
        assertEquals(0, SwtTrendMomentumStrategy.calculatePositionSize(10, 0),
            "10 positions * 0 lots = 0");
    }
    
    @ParameterizedTest
    @DisplayName("calculatePositionSize should handle various inputs")
    @CsvSource({
        "1, 1, 1",      // Base case
        "2, 3, 6",      // Standard multiplication
        "5, 2, 10",     // Different order
        "10, 10, 100",  // Large values
        "1, 100, 100",  // Large trade lots
        "100, 1, 100",  // Large position size
        "0, 5, 0",      // Zero position
        "5, 0, 0",      // Zero lots
        "0, 0, 0"       // Both zero
    })
    void testCalculatePositionSizeParameterized(int positionSizeFactor, int tradeLots, int expected) {
        int result = SwtTrendMomentumStrategy.calculatePositionSize(positionSizeFactor, tradeLots);
        assertEquals(expected, result, 
            String.format("%d * %d should equal %d", positionSizeFactor, tradeLots, expected));
    }
    
    @Test
    @DisplayName("calculatePositionSize should handle negative inputs safely")
    void testCalculatePositionSizeNegativeInputs() {
        // The method doesn't validate inputs, but we document the behavior
        assertEquals(-2, SwtTrendMomentumStrategy.calculatePositionSize(-1, 2),
            "Negative position size produces negative result");
        assertEquals(-6, SwtTrendMomentumStrategy.calculatePositionSize(2, -3),
            "Negative trade lots produces negative result");
        assertEquals(6, SwtTrendMomentumStrategy.calculatePositionSize(-2, -3),
            "Two negatives produce positive result");
    }
}