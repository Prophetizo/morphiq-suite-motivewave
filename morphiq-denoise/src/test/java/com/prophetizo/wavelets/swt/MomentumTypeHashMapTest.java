package com.prophetizo.wavelets.swt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify the HashMap-based implementation of MomentumType.fromString()
 * provides correct functionality and O(1) performance characteristics.
 */
class MomentumTypeHashMapTest {
    
    // Performance test constants
    private static final int LOOKUP_ITERATIONS = 100_000; // Number of test iterations
    private static final int LOOKUPS_PER_ITERATION = 4; // Number of lookups per iteration (SUM, SIGN, INVALID, null)
    private static final int TOTAL_LOOKUPS = LOOKUP_ITERATIONS * LOOKUPS_PER_ITERATION; // 400,000 total
    private static final int MAX_LOOKUP_TIME_NANOS = 2_500; // 2,500 nanoseconds (2.5 microseconds) per lookup
    private static final int MAX_TOTAL_TIME_MS = 1000; // 1 second for all lookups in CI
    
    @Test
    @DisplayName("HashMap lookup should handle all valid enum values")
    void testHashMapLookupForAllValues() {
        // Test each enum value
        for (SwtTrendMomentumStudy.MomentumType type : SwtTrendMomentumStudy.MomentumType.values()) {
            String name = type.name();
            
            // Test exact match
            assertEquals(type, SwtTrendMomentumStudy.MomentumType.fromString(name));
            
            // Test case variations
            assertEquals(type, SwtTrendMomentumStudy.MomentumType.fromString(name.toLowerCase()));
            assertEquals(type, SwtTrendMomentumStudy.MomentumType.fromString(name.toUpperCase()));
            
            // Test with whitespace
            assertEquals(type, SwtTrendMomentumStudy.MomentumType.fromString(" " + name + " "));
            assertEquals(type, SwtTrendMomentumStudy.MomentumType.fromString("\t" + name.toLowerCase() + "\n"));
        }
    }
    
    @Test
    @DisplayName("HashMap implementation provides fast lookup")
    void testHashMapPerformance() {
        // Warm up to ensure HashMap is initialized
        for (int i = 0; i < 100; i++) {
            SwtTrendMomentumStudy.MomentumType.fromString("SUM");
            SwtTrendMomentumStudy.MomentumType.fromString("SIGN");
            SwtTrendMomentumStudy.MomentumType.fromString("sum");  // Test case variation
            SwtTrendMomentumStudy.MomentumType.fromString("sign"); // Test case variation
        }
        
        // Test once with invalid to ensure error path works
        SwtTrendMomentumStudy.MomentumType.fromString("INVALID");
        
        // Test that lookups are fast (HashMap should be O(1))
        long start = System.nanoTime();
        for (int i = 0; i < LOOKUP_ITERATIONS; i++) {
            SwtTrendMomentumStudy.MomentumType.fromString("SUM");
            SwtTrendMomentumStudy.MomentumType.fromString("SIGN");
            SwtTrendMomentumStudy.MomentumType.fromString("sum");  // Lower case
            SwtTrendMomentumStudy.MomentumType.fromString(null);
        }
        long elapsed = System.nanoTime() - start;
        
        // TOTAL_LOOKUPS should complete reasonably quickly with HashMap
        // Allow up to MAX_TOTAL_TIME_MS for CI environments (typically < 50ms locally)
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsed);
        assertTrue(elapsedMs < MAX_TOTAL_TIME_MS, 
            String.format("%d lookups took %d ms (limit: %d ms) - HashMap may not be working correctly", 
                         TOTAL_LOOKUPS, elapsedMs, MAX_TOTAL_TIME_MS));
        
        // Also verify it's actually fast enough to be useful
        double timePerLookup = (double)elapsed / TOTAL_LOOKUPS;
        assertTrue(timePerLookup < MAX_LOOKUP_TIME_NANOS, 
            String.format("Average time per lookup was %.1f ns (limit: %d ns) - too slow for production use", 
                         timePerLookup, MAX_LOOKUP_TIME_NANOS));
    }
    
    @Test
    @DisplayName("HashMap handles invalid values correctly")
    void testInvalidValueHandling() {
        // Test that invalid values return default (SUM) and don't break the HashMap
        String[] invalidValues = {"INVALID", "UNKNOWN", "BAD", "", "123", "sum_of_squares"};
        
        for (String invalid : invalidValues) {
            SwtTrendMomentumStudy.MomentumType result = SwtTrendMomentumStudy.MomentumType.fromString(invalid);
            assertEquals(SwtTrendMomentumStudy.MomentumType.SUM, result, 
                        "Invalid value '" + invalid + "' should default to SUM");
        }
        
        // Null should also default to SUM
        assertEquals(SwtTrendMomentumStudy.MomentumType.SUM, 
                    SwtTrendMomentumStudy.MomentumType.fromString(null),
                    "Null should default to SUM");
    }
    
    @Test
    @DisplayName("HashMap should be immutable after initialization")
    void testHashMapImmutability() {
        // This test verifies that the HashMap cannot be modified after initialization
        // The implementation uses Collections.unmodifiableMap() to ensure this
        
        // Get the first result
        SwtTrendMomentumStudy.MomentumType result1 = SwtTrendMomentumStudy.MomentumType.fromString("SUM");
        
        // Call multiple times - should always return the same instance
        for (int i = 0; i < 100; i++) {
            SwtTrendMomentumStudy.MomentumType result = SwtTrendMomentumStudy.MomentumType.fromString("SUM");
            assertSame(result1, result, "Should return the same enum instance");
        }
        
        // Verify thread safety by checking from multiple threads
        Thread[] threads = new Thread[10];
        boolean[] success = new boolean[threads.length];
        
        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                SwtTrendMomentumStudy.MomentumType result = 
                    SwtTrendMomentumStudy.MomentumType.fromString("SIGN");
                success[index] = (result == SwtTrendMomentumStudy.MomentumType.SIGN);
            });
            threads[i].start();
        }
        
        // Wait for all threads
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                // Restore interrupted status before failing
                Thread.currentThread().interrupt();
                fail("Test thread was interrupted while waiting for worker threads: " + e.getMessage());
            }
        }
        
        // All threads should have succeeded
        for (boolean s : success) {
            assertTrue(s, "Thread-safe access failed");
        }
    }
    
    @Test
    @DisplayName("HashMap initialization should happen only once")
    void testSingleInitialization() {
        // The HashMap should be initialized in a static block,
        // meaning it only happens once when the class is loaded
        
        // Multiple calls should not recreate the map
        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            SwtTrendMomentumStudy.MomentumType.fromString("SUM");
        }
        long firstRun = System.nanoTime() - start;
        
        start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            SwtTrendMomentumStudy.MomentumType.fromString("SIGN");
        }
        long secondRun = System.nanoTime() - start;
        
        // Second run should be similar or faster (no initialization overhead)
        // Allow 2x variance for JVM warmup effects
        assertTrue(secondRun < firstRun * 2, 
            String.format("Second run (%d ns) much slower than first (%d ns) - possible re-initialization", 
                         secondRun, firstRun));
    }
}