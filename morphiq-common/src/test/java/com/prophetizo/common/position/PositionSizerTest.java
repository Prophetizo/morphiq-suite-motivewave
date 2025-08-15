package com.prophetizo.common.position;

import com.motivewave.platform.sdk.common.Instrument;
import com.prophetizo.common.position.PositionSizer.PositionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PositionSizer class.
 * Tests position sizing logic, trade lots multiplication, and risk management.
 */
class PositionSizerTest {
    
    private Instrument mockInstrument;
    private PositionSizer sizer;
    
    @BeforeEach
    void setUp() {
        mockInstrument = mock(Instrument.class);
        when(mockInstrument.getSymbol()).thenReturn("ES");
        when(mockInstrument.getPointValue()).thenReturn(50.0); // ES point value
        sizer = new PositionSizer(mockInstrument);
    }
    
    @Test
    @DisplayName("Constructor should validate null instrument")
    void testConstructorNullInstrument() {
        assertThrows(IllegalArgumentException.class, () -> new PositionSizer(null));
    }
    
    @Test
    @DisplayName("Constructor should initialize with correct point value")
    void testConstructorInitialization() {
        assertNotNull(sizer);
        assertEquals(mockInstrument, sizer.getInstrument());
        assertEquals(50.0, sizer.getPointValue());
    }
    
    @ParameterizedTest
    @DisplayName("Trade lots multiplication should work correctly")
    @CsvSource({
        "1, 1, 1",     // positionSizeFactor=1, tradeLots=1, expected=1
        "1, 2, 2",     // positionSizeFactor=1, tradeLots=2, expected=2
        "2, 2, 4",     // positionSizeFactor=2, tradeLots=2, expected=4
        "3, 5, 15",    // positionSizeFactor=3, tradeLots=5, expected=15
    })
    void testTradeLotMultiplication(int positionSizeFactor, int tradeLots, int expectedQuantity) {
        PositionInfo info = sizer.calculatePosition(positionSizeFactor, tradeLots, 0, 0);
        
        assertEquals(expectedQuantity, info.getFinalQuantity());
        assertEquals(expectedQuantity, info.getBaseQuantity());
        assertFalse(info.wasRiskAdjusted());
    }
    
    @Test
    @DisplayName("Risk-based position sizing should limit quantity")
    void testRiskBasedSizing() {
        // Max risk: $500, Stop: 10 points, Point value: $50
        // Risk per unit: 10 * $50 = $500
        // Max quantity by risk: $500 / $500 = 1
        
        PositionInfo info = sizer.calculatePosition(
            3,      // positionSizeFactor
            2,      // tradeLots (base quantity = 6)
            500.0,  // maxRiskDollars
            10.0    // stopDistancePoints
        );
        
        assertEquals(6, info.getBaseQuantity()); // 3 * 2
        assertEquals(1, info.getFinalQuantity()); // Limited by risk
        assertEquals(500.0, info.getRiskPerUnit());
        assertEquals(500.0, info.getTotalRisk());
        assertTrue(info.wasRiskAdjusted());
    }
    
    @Test
    @DisplayName("No risk limit should use full position size")
    void testNoRiskLimit() {
        PositionInfo info = sizer.calculatePosition(
            2,      // positionSizeFactor
            3,      // tradeLots
            0,      // maxRiskDollars (disabled)
            10.0    // stopDistancePoints
        );
        
        assertEquals(6, info.getFinalQuantity());
        assertEquals(6, info.getBaseQuantity());
        assertFalse(info.wasRiskAdjusted());
    }
    
    @Test
    @DisplayName("WATR-based stop calculation should respect min/max constraints")
    void testWatrStopCalculation() {
        double watr = 3.5;
        double stopMultiplier = 2.0;
        double minStop = 5.0;
        double maxStop = 20.0;
        
        // Test normal case (within bounds)
        PositionInfo info1 = sizer.calculatePositionWithWatr(
            1, 1, 1000.0, watr, stopMultiplier, minStop, maxStop
        );
        assertEquals(7.0, info1.getStopDistancePoints()); // 3.5 * 2 = 7.0
        
        // Test minimum constraint
        PositionInfo info2 = sizer.calculatePositionWithWatr(
            1, 1, 1000.0, 2.0, 2.0, minStop, maxStop
        );
        assertEquals(5.0, info2.getStopDistancePoints()); // 2.0 * 2 = 4.0, but min is 5.0
        
        // Test maximum constraint
        PositionInfo info3 = sizer.calculatePositionWithWatr(
            1, 1, 1000.0, 15.0, 2.0, minStop, maxStop
        );
        assertEquals(20.0, info3.getStopDistancePoints()); // 15.0 * 2 = 30.0, but max is 20.0
    }
    
    @Test
    @DisplayName("Position info calculations should be accurate")
    void testPositionInfoCalculations() {
        PositionInfo info = sizer.calculatePosition(1, 2, 1000.0, 10.0);
        
        // Basic info
        assertEquals(2, info.getFinalQuantity());
        assertEquals(50.0, info.getPointValue());
        assertEquals(10.0, info.getStopDistancePoints());
        
        // Risk calculations
        assertEquals(500.0, info.getRiskPerUnit()); // 10 * 50
        assertEquals(1000.0, info.getTotalRisk());  // 2 * 500
        
        // Target calculations
        assertEquals(30.0, info.getTargetDistance(3.0)); // 10 * 3
        assertEquals(3000.0, info.getPotentialReward(3.0)); // 30 * 50 * 2
        assertEquals(3.0, info.getRiskRewardRatio(3.0)); // 3000 / 1000
    }
    
    @ParameterizedTest
    @DisplayName("Different instruments should have correct point values")
    @CsvSource({
        "NQ, 20.0",     // NQ futures
        "CL, 1000.0",   // Crude oil
        "GC, 100.0",    // Gold
        "6E, 125000.0", // Euro
    })
    void testInstrumentPointValues(String symbol, double pointValue) {
        Instrument instrument = mock(Instrument.class);
        when(instrument.getSymbol()).thenReturn(symbol);
        when(instrument.getPointValue()).thenReturn(pointValue);
        
        PositionSizer instrumentSizer = new PositionSizer(instrument);
        assertEquals(pointValue, instrumentSizer.getPointValue());
        
        // Test risk calculation with different point values
        PositionInfo info = instrumentSizer.calculatePosition(1, 1, 1000.0, 1.0);
        assertEquals(pointValue, info.getRiskPerUnit());
    }
    
    @Test
    @DisplayName("Negative inputs should be handled gracefully")
    void testNegativeInputs() {
        // Negative values should be treated as 0 or minimum valid value
        PositionInfo info = sizer.calculatePosition(-5, -2, -500.0, -10.0);
        
        assertEquals(1, info.getFinalQuantity()); // Max(1, -5) * Max(1, -2) = 1
        assertEquals(0.0, info.getTotalRisk()); // Max(0, -500) = 0
    }
    
    @Test
    @DisplayName("Risk-reward ratio calculation with zero risk")
    void testRiskRewardRatioZeroRisk() {
        PositionInfo info = sizer.calculatePosition(1, 1, 0, 0);
        assertEquals(0.0, info.getRiskRewardRatio(3.0));
    }
    
    @Test
    @DisplayName("ToString should provide readable summary")
    void testToString() {
        PositionInfo info = sizer.calculatePosition(1, 2, 1000.0, 10.0);
        String str = info.toString();
        
        assertTrue(str.contains("qty=2"));
        assertTrue(str.contains("risk=$1000.00"));
        assertTrue(str.contains("stop=10.00 pts"));
        assertTrue(str.contains("RR=3.00"));
    }
}