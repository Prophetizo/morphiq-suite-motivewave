package com.prophetizo.wavelets.swt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for SwtTrendMomentumStrategy position sizing calculation.
 * Tests the static calculation method directly to avoid SDK dependencies.
 */
class CalculateFinalQuantityTest {
    
    @Test
    @DisplayName("calculatePositionSize should multiply position size by trade lots")
    void testCalculatePositionSize() {
        // Test the static calculation logic directly
        int result = SwtTrendMomentumStrategy.calculatePositionSize(2, 3);
        
        // Verify: 2 (position size) * 3 (trade lots) = 6
        assertEquals(6, result, "Should multiply position size by trade lots");
    }
    
    @Test
    @DisplayName("calculatePositionSize with default values")
    void testCalculatePositionSizeDefaults() {
        int result = SwtTrendMomentumStrategy.calculatePositionSize(1, 1);
        
        // Verify: 1 * 1 = 1
        assertEquals(1, result, "Default values should produce quantity of 1");
    }
    
    @Test
    @DisplayName("calculatePositionSize with zero position size")
    void testCalculatePositionSizeZeroPosition() {
        int result = SwtTrendMomentumStrategy.calculatePositionSize(0, 5);
        
        // Verify: 0 * 5 = 0
        assertEquals(0, result, "Zero position size should produce zero quantity");
    }
    
    @Test
    @DisplayName("calculatePositionSize with large values")
    void testCalculatePositionSizeLargeValues() {
        int result = SwtTrendMomentumStrategy.calculatePositionSize(10, 5);
        
        // Verify: 10 * 5 = 50
        assertEquals(50, result, "Should handle large multiplications correctly");
    }
    
    @Test
    @DisplayName("calculatePositionSize with trade lots only")
    void testCalculatePositionSizeTradeLots() {
        // Position size 1, trade lots 7
        int result = SwtTrendMomentumStrategy.calculatePositionSize(1, 7);
        
        // Verify: 1 * 7 = 7
        assertEquals(7, result, "Trade lots should multiply base position");
    }
}