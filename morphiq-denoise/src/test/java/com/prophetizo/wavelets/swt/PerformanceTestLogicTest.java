package com.prophetizo.wavelets.swt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify the performance comparison logic handles all cases correctly.
 */
class PerformanceTestLogicTest {
    
    @Test
    @DisplayName("Performance comparison logic should handle all cases")
    void testPerformanceComparisonLogic() {
        // Test case 1: Enum is faster (expected case)
        assertDoesNotThrow(() -> checkPerformance(100, 50)); // Enum 50% faster
        
        // Test case 2: Enum is slightly slower but within tolerance
        assertDoesNotThrow(() -> checkPerformance(100, 105)); // Enum 5% slower - acceptable
        assertDoesNotThrow(() -> checkPerformance(100, 110)); // Enum 10% slower - acceptable
        
        // Test case 3: Enum is too slow
        assertThrows(AssertionError.class, () -> checkPerformance(100, 115)); // Enum 15% slower - fail
        assertThrows(AssertionError.class, () -> checkPerformance(100, 200)); // Enum 100% slower - fail
        
        // Test case 4: Equal performance
        assertDoesNotThrow(() -> checkPerformance(100, 100)); // Same time - acceptable
    }
    
    @Test
    @DisplayName("Percent calculation should be correct")
    void testPercentCalculation() {
        // When enum takes 110ns and string takes 100ns, enum is 10% slower
        double percentSlower = ((double)(110 - 100) / 100) * 100;
        assertEquals(10.0, percentSlower, 0.01);
        
        // When enum takes 100ns and string takes 100ns, enum is 0% slower
        percentSlower = ((double)(100 - 100) / 100) * 100;
        assertEquals(0.0, percentSlower, 0.01);
        
        // When enum takes 90ns and string takes 100ns, enum is -10% slower (10% faster)
        percentSlower = ((double)(90 - 100) / 100) * 100;
        assertEquals(-10.0, percentSlower, 0.01);
    }
    
    /**
     * Simulates the performance check logic from MomentumTypePerformanceTest
     */
    private void checkPerformance(long stringTime, long enumTime) {
        if (enumTime < stringTime) {
            // Enum is faster - pass
            return;
        } else {
            // Enum is slower or equal - check if difference is acceptable
            double percentSlower = ((double)(enumTime - stringTime) / stringTime) * 100;
            if (percentSlower <= 10.0) {
                // Within tolerance - pass
                return;
            } else {
                throw new AssertionError(String.format(
                    "Enum comparison should be faster than String comparison. " +
                    "String: %d, Enum: %d (%.1f%% slower)", 
                    stringTime, enumTime, percentSlower));
            }
        }
    }
}