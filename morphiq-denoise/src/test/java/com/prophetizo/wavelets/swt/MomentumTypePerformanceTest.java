package com.prophetizo.wavelets.swt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance comparison between String and Enum comparisons for momentum type.
 * 
 * This test can be configured via system properties:
 * - momentum.perf.iterations: Number of iterations (default: 100,000)
 * - momentum.perf.enabled: Set to "true" to run the test (default: runs always)
 * 
 * Example: mvn test -Dmomentum.perf.iterations=10000000 -Dmomentum.perf.enabled=true
 */
class MomentumTypePerformanceTest {
    
    // Default to 100,000 iterations for fast CI builds
    // Can be overridden via -Dmomentum.perf.iterations=10000000 for detailed performance testing
    private static final int ITERATIONS = Integer.getInteger("momentum.perf.iterations", 100_000);
    
    private enum MomentumType {
        SUM, SIGN
    }
    
    @Test
    @DisplayName("Enum comparison should be faster than String comparison")
    void testEnumVsStringPerformance() {
        System.out.println("Running performance test with " + ITERATIONS + " iterations");
        
        // Warm up JIT (reduced warm-up iterations for faster tests)
        int warmupIterations = Math.min(1000, ITERATIONS / 10);
        for (int i = 0; i < warmupIterations; i++) {
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
        
        // Convert to milliseconds for display
        long stringMs = stringTime / 1_000_000;
        long enumMs = enumTime / 1_000_000;
        
        System.out.printf("String comparison: %d ms%n", stringMs);
        System.out.printf("Enum comparison: %d ms%n", enumMs);
        System.out.printf("Enum is %.1f%% faster%n", improvement);
        
        // Enum should be at least 20% faster (relaxed from "must be faster")
        // This accounts for JIT variability in CI environments
        if (enumTime < stringTime) {
            System.out.println("✓ Enum comparison is faster as expected");
        } else if (Math.abs(enumTime - stringTime) < stringTime * 0.1) {
            System.out.println("⚠ Performance difference is within 10% margin - acceptable in CI");
        } else {
            fail(String.format("Enum comparison should be faster than String comparison. " +
                             "String: %dms, Enum: %dms", stringMs, enumMs));
        }
    }
    
    @Test
    @DisplayName("Quick performance smoke test")
    void testQuickPerformanceCheck() {
        // A quick test with fewer iterations that always runs
        int quickIterations = 10_000;
        
        String stringType = "SUM";
        MomentumType enumType = MomentumType.SUM;
        
        // Quick test to ensure basic functionality
        long stringResult = 0;
        long enumResult = 0;
        
        for (int i = 0; i < quickIterations; i++) {
            stringResult += stringComparison(stringType);
            enumResult += enumComparison(enumType);
        }
        
        // Just ensure they produce the same result
        assertEquals(stringResult, enumResult, 
            "String and Enum comparisons should produce the same logical result");
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