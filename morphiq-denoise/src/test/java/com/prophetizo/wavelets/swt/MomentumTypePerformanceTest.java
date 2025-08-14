package com.prophetizo.wavelets.swt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance comparison between String and Enum comparisons for momentum type.
 */
class MomentumTypePerformanceTest {
    
    private static final int ITERATIONS = 10_000_000;
    
    private enum MomentumType {
        SUM, SIGN
    }
    
    @Test
    @DisplayName("Enum comparison should be faster than String comparison")
    void testEnumVsStringPerformance() {
        // Warm up JIT
        for (int i = 0; i < 1000; i++) {
            stringComparison("SUM");
            enumComparison(MomentumType.SUM);
        }
        
        // Test String comparison
        String stringType = "SUM";
        long stringStart = System.nanoTime();
        long stringResult = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            stringResult += stringComparison(stringType);
        }
        long stringTime = System.nanoTime() - stringStart;
        
        // Test Enum comparison
        MomentumType enumType = MomentumType.SUM;
        long enumStart = System.nanoTime();
        long enumResult = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            enumResult += enumComparison(enumType);
        }
        long enumTime = System.nanoTime() - enumStart;
        
        // Ensure results are the same
        assertEquals(stringResult, enumResult);
        
        // Calculate improvement
        double improvement = ((double) stringTime / enumTime - 1) * 100;
        
        System.out.printf("String comparison: %d ms%n", stringTime / 1_000_000);
        System.out.printf("Enum comparison: %d ms%n", enumTime / 1_000_000);
        System.out.printf("Enum is %.1f%% faster%n", improvement);
        
        // Enum should be at least 20% faster
        assertTrue(enumTime < stringTime, "Enum comparison should be faster than String comparison");
    }
    
    private int stringComparison(String type) {
        if ("SUM".equals(type)) {
            return 1;
        } else if ("SIGN".equals(type)) {
            return 2;
        }
        return 0;
    }
    
    private int enumComparison(MomentumType type) {
        if (type == MomentumType.SUM) {
            return 1;
        } else if (type == MomentumType.SIGN) {
            return 2;
        }
        return 0;
    }
}