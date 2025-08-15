package com.prophetizo.common.position;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PositionTracker class.
 * Tests position state tracking and calculations.
 */
class PositionTrackerTest {
    
    private PositionTracker tracker;
    
    @BeforeEach
    void setUp() {
        tracker = new PositionTracker();
    }
    
    @Test
    @DisplayName("Initial state should be flat")
    void testInitialState() {
        assertFalse(tracker.hasPosition());
        assertFalse(tracker.isLong());
        assertFalse(tracker.isShort());
        assertEquals(0.0, tracker.getEntryPrice());
        assertEquals(0.0, tracker.getStopPrice());
        assertEquals(0.0, tracker.getTargetPrice());
        assertEquals(0.0, tracker.getRiskPerUnit());
        assertEquals(0.0, tracker.getRewardPerUnit());
        assertEquals(0.0, tracker.getRiskRewardRatio());
    }
    
    @Test
    @DisplayName("Update long position should set state correctly")
    void testUpdateLongPosition() {
        tracker.updatePosition(4500.0, 4490.0, 4530.0, true);
        
        assertTrue(tracker.hasPosition());
        assertTrue(tracker.isLong());
        assertFalse(tracker.isShort());
        assertEquals(4500.0, tracker.getEntryPrice());
        assertEquals(4490.0, tracker.getStopPrice());
        assertEquals(4530.0, tracker.getTargetPrice());
        assertEquals(10.0, tracker.getRiskPerUnit()); // |4500 - 4490|
        assertEquals(30.0, tracker.getRewardPerUnit()); // |4530 - 4500|
        assertEquals(3.0, tracker.getRiskRewardRatio()); // 30 / 10
    }
    
    @Test
    @DisplayName("Update short position should set state correctly")
    void testUpdateShortPosition() {
        tracker.updatePosition(4500.0, 4510.0, 4470.0, false);
        
        assertTrue(tracker.hasPosition());
        assertFalse(tracker.isLong());
        assertTrue(tracker.isShort());
        assertEquals(4500.0, tracker.getEntryPrice());
        assertEquals(4510.0, tracker.getStopPrice());
        assertEquals(4470.0, tracker.getTargetPrice());
        assertEquals(10.0, tracker.getRiskPerUnit()); // |4500 - 4510|
        assertEquals(30.0, tracker.getRewardPerUnit()); // |4470 - 4500|
        assertEquals(3.0, tracker.getRiskRewardRatio()); // 30 / 10
    }
    
    @Test
    @DisplayName("Update entry price should only change entry when positioned")
    void testUpdateEntryPrice() {
        // Should not update when flat
        tracker.updateEntryPrice(4505.0);
        assertEquals(0.0, tracker.getEntryPrice());
        
        // Setup position
        tracker.updatePosition(4500.0, 4490.0, 4530.0, true);
        
        // Should update when positioned
        tracker.updateEntryPrice(4505.0);
        assertEquals(4505.0, tracker.getEntryPrice());
        assertEquals(4490.0, tracker.getStopPrice()); // Should remain unchanged
        assertEquals(4530.0, tracker.getTargetPrice()); // Should remain unchanged
    }
    
    @Test
    @DisplayName("Reset should clear all state")
    void testReset() {
        // Setup position
        tracker.updatePosition(4500.0, 4490.0, 4530.0, true);
        assertTrue(tracker.hasPosition());
        
        // Reset
        tracker.reset();
        
        assertFalse(tracker.hasPosition());
        assertFalse(tracker.isLong());
        assertFalse(tracker.isShort());
        assertEquals(0.0, tracker.getEntryPrice());
        assertEquals(0.0, tracker.getStopPrice());
        assertEquals(0.0, tracker.getTargetPrice());
    }
    
    @Test
    @DisplayName("Long position P&L calculation should be correct")
    void testLongPositionPnLCalculation() {
        tracker.updatePosition(4500.0, 4490.0, 4530.0, true);
        
        // Profitable scenario
        double profitPnL = tracker.calculateUnrealizedPnL(4520.0, 2);
        assertEquals(40.0, profitPnL); // (4520 - 4500) * 2 = 40
        
        // Loss scenario
        double lossPnL = tracker.calculateUnrealizedPnL(4480.0, 2);
        assertEquals(-40.0, lossPnL); // (4480 - 4500) * 2 = -40
        
        // Break-even scenario
        double evenPnL = tracker.calculateUnrealizedPnL(4500.0, 2);
        assertEquals(0.0, evenPnL);
    }
    
    @Test
    @DisplayName("Short position P&L calculation should be correct")
    void testShortPositionPnLCalculation() {
        tracker.updatePosition(4500.0, 4510.0, 4470.0, false);
        
        // Profitable scenario (price falls)
        double profitPnL = tracker.calculateUnrealizedPnL(4480.0, 2);
        assertEquals(40.0, profitPnL); // (4500 - 4480) * 2 = 40
        
        // Loss scenario (price rises)
        double lossPnL = tracker.calculateUnrealizedPnL(4520.0, 2);
        assertEquals(-40.0, lossPnL); // (4500 - 4520) * 2 = -40
        
        // Break-even scenario
        double evenPnL = tracker.calculateUnrealizedPnL(4500.0, 2);
        assertEquals(0.0, evenPnL);
    }
    
    @Test
    @DisplayName("Stop distance calculation should be correct")
    void testStopDistanceCalculation() {
        // Long position
        tracker.updatePosition(4500.0, 4490.0, 4530.0, true);
        
        assertEquals(10.0, tracker.getStopDistance(4500.0)); // At entry
        assertEquals(15.0, tracker.getStopDistance(4505.0)); // Above entry
        assertEquals(5.0, tracker.getStopDistance(4495.0)); // Between entry and stop
        assertEquals(-5.0, tracker.getStopDistance(4485.0)); // Below stop (stop hit)
        
        // Short position
        tracker.updatePosition(4500.0, 4510.0, 4470.0, false);
        
        assertEquals(10.0, tracker.getStopDistance(4500.0)); // At entry
        assertEquals(15.0, tracker.getStopDistance(4495.0)); // Below entry
        assertEquals(5.0, tracker.getStopDistance(4505.0)); // Between entry and stop
        assertEquals(-5.0, tracker.getStopDistance(4515.0)); // Above stop (stop hit)
    }
    
    @Test
    @DisplayName("Target distance calculation should be correct")
    void testTargetDistanceCalculation() {
        // Long position
        tracker.updatePosition(4500.0, 4490.0, 4530.0, true);
        
        assertEquals(30.0, tracker.getTargetDistance(4500.0)); // At entry
        assertEquals(20.0, tracker.getTargetDistance(4510.0)); // Partway to target
        assertEquals(-5.0, tracker.getTargetDistance(4535.0)); // Beyond target
        
        // Short position
        tracker.updatePosition(4500.0, 4510.0, 4470.0, false);
        
        assertEquals(30.0, tracker.getTargetDistance(4500.0)); // At entry
        assertEquals(20.0, tracker.getTargetDistance(4490.0)); // Partway to target
        assertEquals(-5.0, tracker.getTargetDistance(4465.0)); // Beyond target
    }
    
    @Test
    @DisplayName("Near stop detection should work correctly")
    void testNearStopDetection() {
        tracker.updatePosition(4500.0, 4490.0, 4530.0, true); // Risk = 10 points
        
        // 20% threshold = 2 points from stop
        assertFalse(tracker.isNearStop(4500.0, 0.2)); // 10 points away
        assertFalse(tracker.isNearStop(4495.0, 0.2)); // 5 points away
        assertTrue(tracker.isNearStop(4492.0, 0.2)); // 2 points away
        assertTrue(tracker.isNearStop(4490.5, 0.2)); // 0.5 points away
    }
    
    @Test
    @DisplayName("Near target detection should work correctly")
    void testNearTargetDetection() {
        tracker.updatePosition(4500.0, 4490.0, 4530.0, true); // Reward = 30 points
        
        // 20% threshold = 6 points from target
        assertFalse(tracker.isNearTarget(4500.0, 0.2)); // 30 points away
        assertFalse(tracker.isNearTarget(4520.0, 0.2)); // 10 points away
        assertTrue(tracker.isNearTarget(4525.0, 0.2)); // 5 points away
        assertTrue(tracker.isNearTarget(4529.0, 0.2)); // 1 point away
    }
    
    @Test
    @DisplayName("Calculations should handle invalid inputs gracefully")
    void testInvalidInputHandling() {
        // Test with no position
        assertEquals(0.0, tracker.calculateUnrealizedPnL(4500.0, 2));
        assertEquals(0.0, tracker.getStopDistance(4500.0));
        assertEquals(0.0, tracker.getTargetDistance(4500.0));
        assertFalse(tracker.isNearStop(4500.0, 0.2));
        assertFalse(tracker.isNearTarget(4500.0, 0.2));
        
        // Test with invalid prices
        tracker.updatePosition(4500.0, 4490.0, 4530.0, true);
        assertEquals(0.0, tracker.calculateUnrealizedPnL(0.0, 2));
        assertEquals(0.0, tracker.calculateUnrealizedPnL(-100.0, 2));
        assertEquals(0.0, tracker.getStopDistance(0.0));
        assertEquals(0.0, tracker.getTargetDistance(0.0));
        
        // Test with invalid thresholds
        assertFalse(tracker.isNearStop(4500.0, 0.0));
        assertFalse(tracker.isNearStop(4500.0, -0.1));
        assertFalse(tracker.isNearTarget(4500.0, 0.0));
        assertFalse(tracker.isNearTarget(4500.0, -0.1));
    }
    
    @Test
    @DisplayName("Risk/reward ratio should handle edge cases")
    void testRiskRewardRatioEdgeCases() {
        // No risk (entry == stop) should return 0
        tracker.updatePosition(4500.0, 4500.0, 4530.0, true);
        assertEquals(0.0, tracker.getRiskRewardRatio());
        
        // No reward (entry == target) should return 0
        tracker.updatePosition(4500.0, 4490.0, 4500.0, true);
        assertEquals(0.0, tracker.getRiskRewardRatio());
        
        // Normal case
        tracker.updatePosition(4500.0, 4490.0, 4520.0, true);
        assertEquals(2.0, tracker.getRiskRewardRatio()); // 20 / 10 = 2.0
    }
    
    @Test
    @DisplayName("ToString should provide readable output")
    void testToString() {
        // Flat position
        String flatStr = tracker.toString();
        assertTrue(flatStr.contains("FLAT"));
        
        // Long position
        tracker.updatePosition(4500.0, 4490.0, 4530.0, true);
        String longStr = tracker.toString();
        assertTrue(longStr.contains("LONG"));
        assertTrue(longStr.contains("4500.00"));
        assertTrue(longStr.contains("4490.00"));
        assertTrue(longStr.contains("4530.00"));
        assertTrue(longStr.contains("RR=3.00"));
        
        // Short position
        tracker.updatePosition(4500.0, 4510.0, 4470.0, false);
        String shortStr = tracker.toString();
        assertTrue(shortStr.contains("SHORT"));
        assertTrue(shortStr.contains("4500.00"));
        assertTrue(shortStr.contains("4510.00"));
        assertTrue(shortStr.contains("4470.00"));
    }
}