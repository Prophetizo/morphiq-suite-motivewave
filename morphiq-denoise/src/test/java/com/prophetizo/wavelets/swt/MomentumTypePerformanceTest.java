package com.prophetizo.wavelets.swt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance comparison between String and Enum comparisons for momentum type.
 * 
 * <p>This test verifies that enum comparison is faster than string comparison,
 * which validates our design choice for the momentum type field.
 * 
 * <p><b>Configuration:</b>
 * <ul>
 *   <li>{@code momentum.perf.iterations} - Number of iterations (default: 100,000)
 *       <ul>
 *         <li>10,000: Quick smoke test, may have JIT warmup noise</li>
 *         <li>100,000: Default for CI, runs in ~10ms, balances speed vs accuracy</li>
 *         <li>1,000,000+: For detailed local testing, more stable results</li>
 *       </ul>
 *   </li>
 * </ul>
 * 
 * <p><b>Example:</b>
 * {@code mvn test -Dtest=MomentumTypePerformanceTest -Dmomentum.perf.iterations=1000000}
 * 
 * @see <a href="../../../../../../../../CLAUDE.md#test-system-properties">Test System Properties Documentation</a>
 */
class MomentumTypePerformanceTest {
    
    /**
     * Number of iterations for performance tests.
     * Configurable via system property: {@code momentum.perf.iterations}
     * Default: 100,000 iterations (optimized for CI environments)
     */
    private static final int ITERATIONS = Math.max(1_000, Math.min(10_000_000, Integer.getInteger("momentum.perf.iterations", 100_000)));
    
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
        
        // Enum should typically be faster, but allow some tolerance for CI variability
        if (enumTime < stringTime) {
            System.out.println("✓ Enum comparison is faster as expected");
        } else {
            // Enum is slower or equal - check if difference is acceptable
            double percentSlower = ((double)(enumTime - stringTime) / stringTime) * 100;
            if (percentSlower <= 10.0) {
                System.out.println("⚠ Performance difference is within 10% margin - acceptable in CI");
            } else {
                fail(String.format("Enum comparison should be faster than String comparison. " +
                                 "String: %dms, Enum: %dms (%.1f%% slower)", 
                                 stringMs, enumMs, percentSlower));
            }
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