package com.morphiqlabs.wavelets.swt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StateChangeSignal class to ensure proper state change detection
 * and signal object creation.
 */
class StateChangeSignalTest {
    
    @Test
    @DisplayName("StateChangeSignal creation and basic properties")
    void testStateChangeSignalCreation() {
        long timestamp = System.currentTimeMillis();
        StateChangeSignal signal = new StateChangeSignal(
            StateChangeSignal.SignalType.SLOPE_TURNED_POSITIVE,
            -0.05, 0.10, 0.15, timestamp
        );
        
        assertEquals(StateChangeSignal.SignalType.SLOPE_TURNED_POSITIVE, signal.getType());
        assertEquals(-0.05, signal.getOldValue(), 0.001);
        assertEquals(0.10, signal.getNewValue(), 0.001);
        assertEquals(0.15, signal.getMagnitude(), 0.001);
        assertEquals(timestamp, signal.getTimestamp());
    }
    
    @Test
    @DisplayName("StateChangeSignal convenience constructor")
    void testConvenienceConstructor() {
        long timestamp = System.currentTimeMillis();
        StateChangeSignal signal = new StateChangeSignal(
            StateChangeSignal.SignalType.MOMENTUM_CROSSED_POSITIVE,
            -2.0, 1.5, timestamp
        );
        
        assertEquals(StateChangeSignal.SignalType.MOMENTUM_CROSSED_POSITIVE, signal.getType());
        assertEquals(-2.0, signal.getOldValue(), 0.001);
        assertEquals(1.5, signal.getNewValue(), 0.001);
        assertEquals(3.5, signal.getMagnitude(), 0.001); // |1.5 - (-2.0)|
        assertEquals(timestamp, signal.getTimestamp());
    }
    
    @ParameterizedTest
    @DisplayName("Signal type classification tests")
    @CsvSource({
        "SLOPE_TURNED_POSITIVE, true, false, true, false",
        "SLOPE_TURNED_NEGATIVE, true, false, false, true",
        "MOMENTUM_CROSSED_POSITIVE, false, true, true, false",
        "MOMENTUM_CROSSED_NEGATIVE, false, true, false, true",
        "MOMENTUM_THRESHOLD_EXCEEDED, false, true, true, false",
        "MOMENTUM_THRESHOLD_LOST, false, true, false, true"
    })
    void testSignalClassification(StateChangeSignal.SignalType type, 
                                 boolean expectedSlope, boolean expectedMomentum,
                                 boolean expectedPositive, boolean expectedNegative) {
        StateChangeSignal signal = new StateChangeSignal(type, 0.0, 1.0, System.currentTimeMillis());
        
        assertEquals(expectedSlope, signal.isSlopeSignal());
        assertEquals(expectedMomentum, signal.isMomentumSignal());
        assertEquals(expectedPositive, signal.isPositiveChange());
        assertEquals(expectedNegative, signal.isNegativeChange());
    }
    
    @Test
    @DisplayName("ToString format validation")
    void testToStringFormat() {
        StateChangeSignal signal = new StateChangeSignal(
            StateChangeSignal.SignalType.SLOPE_TURNED_POSITIVE,
            -0.0500, 0.1234, System.currentTimeMillis()
        );
        
        String expected = "SLOPE_TURNED_POSITIVE[-0.0500 -> 0.1234, magnitude=0.1734]";
        assertEquals(expected, signal.toString());
    }
    
    @Test
    @DisplayName("Equals and hashCode contract")
    void testEqualsAndHashCode() {
        long timestamp = 1234567890L;
        StateChangeSignal signal1 = new StateChangeSignal(
            StateChangeSignal.SignalType.MOMENTUM_CROSSED_POSITIVE,
            -1.0, 1.0, 2.0, timestamp
        );
        StateChangeSignal signal2 = new StateChangeSignal(
            StateChangeSignal.SignalType.MOMENTUM_CROSSED_POSITIVE,
            -1.0, 1.0, 2.0, timestamp
        );
        StateChangeSignal signal3 = new StateChangeSignal(
            StateChangeSignal.SignalType.MOMENTUM_CROSSED_NEGATIVE,
            -1.0, 1.0, 2.0, timestamp
        );
        
        assertEquals(signal1, signal2);
        assertEquals(signal1.hashCode(), signal2.hashCode());
        assertNotEquals(signal1, signal3);
        assertNotEquals(signal1.hashCode(), signal3.hashCode());
    }
    
    @Test
    @DisplayName("Magnitude calculation with negative values")
    void testMagnitudeCalculation() {
        // Test convenience constructor - magnitude calculated automatically as absolute difference
        StateChangeSignal signal1 = new StateChangeSignal(
            StateChangeSignal.SignalType.SLOPE_TURNED_NEGATIVE,
            0.05, -0.10, System.currentTimeMillis()
        );
        assertEquals(0.15, signal1.getMagnitude(), 0.001); // |(-0.10) - 0.05| = 0.15
        
        StateChangeSignal signal2 = new StateChangeSignal(
            StateChangeSignal.SignalType.MOMENTUM_CROSSED_NEGATIVE,
            2.0, -3.0, System.currentTimeMillis()
        );
        assertEquals(5.0, signal2.getMagnitude(), 0.001); // |(-3.0) - 2.0| = 5.0
        
        // Test explicit positive magnitude (should remain unchanged)
        StateChangeSignal signal3 = new StateChangeSignal(
            StateChangeSignal.SignalType.MOMENTUM_THRESHOLD_EXCEEDED,
            0.5, 2.0, 1.5, System.currentTimeMillis()
        );
        assertEquals(1.5, signal3.getMagnitude(), 0.001);
        
        // Test explicit negative magnitude (should be converted to positive)
        StateChangeSignal signal4 = new StateChangeSignal(
            StateChangeSignal.SignalType.MOMENTUM_THRESHOLD_EXCEEDED,
            0.5, 2.0, -2.5, System.currentTimeMillis()
        );
        assertEquals(2.5, signal4.getMagnitude(), 0.001); // Math.abs(-2.5) = 2.5
        
        // Test zero magnitude
        StateChangeSignal signal5 = new StateChangeSignal(
            StateChangeSignal.SignalType.MOMENTUM_THRESHOLD_LOST,
            1.0, 1.0, 0.0, System.currentTimeMillis()
        );
        assertEquals(0.0, signal5.getMagnitude(), 0.001);
    }
}