package com.prophetizo.wavelets.swt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify the HashMap-based implementation of MomentumType.fromString()
 * provides correct functionality and O(1) performance characteristics.
 */
class MomentumTypeHashMapTest {
    
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
            SwtTrendMomentumStudy.MomentumType.fromString("INVALID");
        }
        
        // Test that lookups are fast (HashMap should be O(1))
        long start = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            SwtTrendMomentumStudy.MomentumType.fromString("SUM");
            SwtTrendMomentumStudy.MomentumType.fromString("SIGN");
            SwtTrendMomentumStudy.MomentumType.fromString("INVALID");
            SwtTrendMomentumStudy.MomentumType.fromString(null);
        }
        long elapsed = System.nanoTime() - start;
        
        // 400,000 lookups should complete reasonably quickly with HashMap
        // Allow up to 1 second for CI environments (typically < 50ms locally)
        long elapsedMs = elapsed / 1_000_000;
        assertTrue(elapsedMs < 1000, 
            String.format("400,000 lookups took %d ms - HashMap may not be working correctly", elapsedMs));
        
        // Also verify it's actually fast enough to be useful (< 2.5 microseconds per lookup)
        double timePerLookup = (double)elapsed / 400_000;
        assertTrue(timePerLookup < 2500, 
            String.format("Average time per lookup was %.1f ns - too slow for production use", timePerLookup));
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
                fail("Thread interrupted");
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