package com.prophetizo.wavelets.swt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for SwtTrendMomentumStrategy position sizing calculation.
 * Uses test helper method to avoid JavaFX and initialization dependencies.
 */
class CalculateFinalQuantityTest {
    
    @Test
    @DisplayName("calculateFinalQuantity should multiply position size by trade lots")
    void testCalculateFinalQuantity() {
        SwtTrendMomentumStrategy strategy = new SwtTrendMomentumStrategy();
        
        // Test the calculation logic directly
        int result = strategy.calculateFinalQuantityForTest(2, 3);
        
        // Verify: 2 (position size) * 3 (trade lots) = 6
        assertEquals(6, result, "Should multiply position size by trade lots");
    }
    
    @Test
    @DisplayName("calculateFinalQuantity with default values")
    void testCalculateFinalQuantityDefaults() {
        SwtTrendMomentumStrategy strategy = new SwtTrendMomentumStrategy();
        
        int result = strategy.calculateFinalQuantityForTest(1, 1);
        
        // Verify: 1 * 1 = 1
        assertEquals(1, result, "Default values should produce quantity of 1");
    }
    
    @Test
    @DisplayName("calculateFinalQuantity with zero position size")
    void testCalculateFinalQuantityZeroPosition() {
        SwtTrendMomentumStrategy strategy = new SwtTrendMomentumStrategy();
        
        int result = strategy.calculateFinalQuantityForTest(0, 5);
        
        // Verify: 0 * 5 = 0
        assertEquals(0, result, "Zero position size should produce zero quantity");
    }
    
    @Test
    @DisplayName("calculateFinalQuantity with large values")
    void testCalculateFinalQuantityLargeValues() {
        SwtTrendMomentumStrategy strategy = new SwtTrendMomentumStrategy();
        
        int result = strategy.calculateFinalQuantityForTest(10, 5);
        
        // Verify: 10 * 5 = 50
        assertEquals(50, result, "Should handle large multiplications correctly");
    }
    
    @Test
    @DisplayName("calculateFinalQuantity with trade lots only")
    void testCalculateFinalQuantityTradeLots() {
        SwtTrendMomentumStrategy strategy = new SwtTrendMomentumStrategy();
        
        // Position size 1, trade lots 7
        int result = strategy.calculateFinalQuantityForTest(1, 7);
        
        // Verify: 1 * 7 = 7
        assertEquals(7, result, "Trade lots should multiply base position");
    }
}