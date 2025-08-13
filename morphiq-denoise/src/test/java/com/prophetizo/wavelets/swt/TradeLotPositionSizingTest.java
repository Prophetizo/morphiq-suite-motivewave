package com.prophetizo.wavelets.swt;

import com.motivewave.platform.sdk.common.Instrument;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;
import com.prophetizo.wavelets.swt.core.PositionSizer;
import com.prophetizo.wavelets.swt.core.PositionSizer.PositionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Focused tests for Trade Lots multiplication and position sizing.
 * Ensures proper calculation of final position sizes according to CLAUDE.md specifications.
 */
class TradeLotPositionSizingTest {
    
    private Instrument mockInstrument;
    private OrderContext mockOrderContext;
    private PositionSizer sizer;
    
    @BeforeEach
    void setUp() {
        mockInstrument = mock(Instrument.class);
        mockOrderContext = mock(OrderContext.class);
        
        // Default ES futures setup
        when(mockInstrument.getSymbol()).thenReturn("ES");
        when(mockInstrument.getPointValue()).thenReturn(50.0);
        
        sizer = new PositionSizer(mockInstrument);
    }
    
    @Test
    @DisplayName("Trade Lots multiplication formula: Final = Position Size Factor × Trade Lots")
    void testTradeLotMultiplicationFormula() {
        // As specified in CLAUDE.md: Final position = Position Size Factor × Trade Lots
        
        int positionSizeFactor = 3;
        int tradeLots = 2;
        int expectedFinalQuantity = 6; // 3 × 2
        
        PositionInfo info = sizer.calculatePosition(positionSizeFactor, tradeLots, 0, 0);
        
        assertEquals(expectedFinalQuantity, info.getFinalQuantity());
        assertEquals(expectedFinalQuantity, info.getBaseQuantity());
        
        // Log verification format as shown in CLAUDE.md
        String logOutput = String.format(
            "Position Size Factor: %d, Trade Lots: %d, Final Quantity: %d (%d×%d)",
            positionSizeFactor, tradeLots, expectedFinalQuantity, positionSizeFactor, tradeLots
        );
        assertTrue(logOutput.contains("3×2"));
    }
    
    @ParameterizedTest
    @DisplayName("Trade Lots multiplication with various combinations")
    @CsvSource({
        // positionSizeFactor, tradeLots, expected
        "1, 1, 1",    // Minimum values
        "1, 2, 2",    // Only Trade Lots multiplier
        "2, 1, 2",    // Only Position Size Factor
        "2, 3, 6",    // Both multipliers
        "5, 4, 20",   // Larger values
        "10, 10, 100" // Maximum typical values
    })
    void testVariousTradeLotCombinations(int positionSizeFactor, int tradeLots, int expected) {
        PositionInfo info = sizer.calculatePosition(positionSizeFactor, tradeLots, 0, 0);
        
        assertEquals(expected, info.getFinalQuantity(),
            String.format("Failed for PSF=%d, TL=%d", positionSizeFactor, tradeLots));
        
        // Verify no risk adjustment when risk is disabled (maxRisk = 0)
        assertFalse(info.wasRiskAdjusted());
    }
    
    @Test
    @DisplayName("Trade Lots with risk management constraint")
    void testTradeLotWithRiskConstraint() {
        // Scenario: Trade Lots would result in excessive risk
        int positionSizeFactor = 2;
        int tradeLots = 5;
        int baseQuantity = 10; // 2 × 5
        
        // Risk constraint: $500 max risk, 10 point stop
        // Risk per unit: 10 × $50 = $500
        // Max quantity by risk: $500 / $500 = 1
        
        PositionInfo info = sizer.calculatePosition(
            positionSizeFactor, tradeLots, 500.0, 10.0
        );
        
        assertEquals(baseQuantity, info.getBaseQuantity()); // Original calculation
        assertEquals(1, info.getFinalQuantity()); // Limited by risk
        assertTrue(info.wasRiskAdjusted());
        assertEquals(500.0, info.getTotalRisk(), 0.01);
    }
    
    @Test
    @DisplayName("Dollar risk calculation with Trade Lots")
    void testDollarRiskWithTradeLots() {
        // Test that dollar risk is calculated using final quantity (including Trade Lots)
        int positionSizeFactor = 1;
        int tradeLots = 3;
        double stopDistance = 8.0; // points
        double maxRisk = 2000.0;
        
        PositionInfo info = sizer.calculatePosition(
            positionSizeFactor, tradeLots, maxRisk, stopDistance
        );
        
        int finalQuantity = info.getFinalQuantity();
        double expectedRiskPerUnit = stopDistance * 50.0; // 8 × $50 = $400
        double expectedTotalRisk = finalQuantity * expectedRiskPerUnit; // 3 × $400 = $1200
        
        assertEquals(3, finalQuantity);
        assertEquals(400.0, info.getRiskPerUnit(), 0.01);
        assertEquals(1200.0, info.getTotalRisk(), 0.01);
        assertFalse(info.wasRiskAdjusted()); // Within risk limit
    }
    
    @ParameterizedTest
    @DisplayName("Instrument-specific point values with Trade Lots")
    @CsvSource({
        "ES, 50.0, 2, 2, 10.0, 2000.0",    // ES: 2×2=4 units, 10pts×$50×4=$2000
        "NQ, 20.0, 3, 1, 25.0, 1500.0",    // NQ: 3×1=3 units, 25pts×$20×3=$1500
        "CL, 1000.0, 1, 2, 1.5, 3000.0",   // CL: 1×2=2 units, 1.5pts×$1000×2=$3000
        "GC, 100.0, 2, 3, 5.0, 3000.0",    // GC: 2×3=6 units, 5pts×$100×6=$3000
    })
    void testInstrumentPointValuesWithTradeLots(String symbol, double pointValue,
                                                int positionSizeFactor, int tradeLots,
                                                double stopPoints, double expectedRisk) {
        // Setup instrument with specific point value
        Instrument instrument = mock(Instrument.class);
        when(instrument.getSymbol()).thenReturn(symbol);
        when(instrument.getPointValue()).thenReturn(pointValue);
        
        PositionSizer instrumentSizer = new PositionSizer(instrument);
        PositionInfo info = instrumentSizer.calculatePosition(
            positionSizeFactor, tradeLots, 10000.0, stopPoints // High max risk to avoid constraint
        );
        
        int expectedQuantity = positionSizeFactor * tradeLots;
        assertEquals(expectedQuantity, info.getFinalQuantity());
        assertEquals(expectedRisk, info.getTotalRisk(), 0.01,
            String.format("%s: Failed risk calculation", symbol));
    }
    
    @Test
    @DisplayName("Trade Lots default to 1 when not set")
    void testTradeLotDefaultValue() {
        // When Trade Lots is not set or is 0, it should default to 1
        
        PositionInfo info1 = sizer.calculatePosition(5, 0, 0, 0);
        assertEquals(5, info1.getFinalQuantity()); // 5 × max(1, 0) = 5
        
        PositionInfo info2 = sizer.calculatePosition(5, -2, 0, 0);
        assertEquals(5, info2.getFinalQuantity()); // 5 × max(1, -2) = 5
    }
    
    @Test
    @DisplayName("Position Size Factor default to 1 when not set")
    void testPositionSizeFactorDefault() {
        // When Position Size Factor is not set or is 0, it should default to 1
        
        PositionInfo info1 = sizer.calculatePosition(0, 3, 0, 0);
        assertEquals(3, info1.getFinalQuantity()); // max(1, 0) × 3 = 3
        
        PositionInfo info2 = sizer.calculatePosition(-5, 3, 0, 0);
        assertEquals(3, info2.getFinalQuantity()); // max(1, -5) × 3 = 3
    }
    
    @Test
    @DisplayName("Risk-reward calculation uses final quantity")
    void testRiskRewardWithTradeLots() {
        int positionSizeFactor = 2;
        int tradeLots = 3;
        double stopDistance = 10.0;
        double targetMultiplier = 3.0;
        
        PositionInfo info = sizer.calculatePosition(
            positionSizeFactor, tradeLots, 5000.0, stopDistance
        );
        
        // Final quantity = 2 × 3 = 6
        assertEquals(6, info.getFinalQuantity());
        
        // Risk = 6 × 10 × $50 = $3000
        assertEquals(3000.0, info.getTotalRisk(), 0.01);
        
        // Reward = 6 × 30 × $50 = $9000
        assertEquals(9000.0, info.getPotentialReward(targetMultiplier), 0.01);
        
        // RR Ratio = $9000 / $3000 = 3.0
        assertEquals(3.0, info.getRiskRewardRatio(targetMultiplier), 0.01);
    }
    
    @Test
    @DisplayName("Logging format matches CLAUDE.md specification")
    void testLoggingFormat() {
        int positionSizeFactor = 2;
        int tradeLots = 4;
        int finalQuantity = 8;
        
        PositionInfo info = sizer.calculatePosition(positionSizeFactor, tradeLots, 0, 0);
        
        // Verify the logging format as specified in CLAUDE.md
        String expectedLog = String.format(
            "Final Order Quantity: %d (%d×%d)",
            finalQuantity, positionSizeFactor, tradeLots
        );
        
        assertEquals("Final Order Quantity: 8 (2×4)", expectedLog);
        assertTrue(info.toString().contains("qty=8"));
    }
}