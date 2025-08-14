package com.prophetizo.wavelets.swt.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance test to demonstrate the efficiency of the optimized reconstruction method.
 * The optimization eliminates deep copy operations by working directly with the mutable result.
 */
class ReconstructionPerformanceTest {
    
    @Test
    @DisplayName("Reconstruction performance with repeated calls")
    void testReconstructionPerformance() {
        // Create test data
        int dataSize = 1024;
        double[] data = new double[dataSize];
        for (int i = 0; i < dataSize; i++) {
            data[i] = Math.sin(2 * Math.PI * i / 64.0) + 
                      0.5 * Math.sin(8 * Math.PI * i / 64.0);
        }
        
        VectorWaveSwtAdapter adapter = new VectorWaveSwtAdapter("db4");
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(data, 5);
        
        // Warm up JVM
        for (int i = 0; i < 100; i++) {
            result.reconstruct(3);
        }
        
        // Measure performance
        long startTime = System.nanoTime();
        int iterations = 1000;
        
        for (int i = 0; i < iterations; i++) {
            double[] reconstructed = result.reconstruct(3);
            assertNotNull(reconstructed);
            assertEquals(dataSize, reconstructed.length);
        }
        
        long endTime = System.nanoTime();
        double avgTimeMs = (endTime - startTime) / (iterations * 1_000_000.0);
        
        // Log performance
        System.out.printf("Average reconstruction time: %.3f ms%n", avgTimeMs);
        
        // Performance assertion - should be very fast
        assertTrue(avgTimeMs < 10.0, 
            "Reconstruction should be fast (< 10ms per call), was: " + avgTimeMs);
    }
    
    @Test
    @DisplayName("Memory efficiency test")
    void testMemoryEfficiency() {
        // Create larger test data
        int dataSize = 4096;
        double[] data = new double[dataSize];
        for (int i = 0; i < dataSize; i++) {
            data[i] = Math.random() * 10;
        }
        
        VectorWaveSwtAdapter adapter = new VectorWaveSwtAdapter("db6");
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(data, 6);
        
        // Get initial memory usage
        System.gc();
        long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        // Perform many reconstructions
        for (int i = 0; i < 100; i++) {
            result.reconstruct(i % 6 + 1); // Vary the level
        }
        
        // Get final memory usage
        System.gc();
        long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        // Memory increase should be minimal (no accumulating objects)
        long memIncrease = memAfter - memBefore;
        
        // Log memory usage
        System.out.printf("Memory increase after 100 reconstructions: %.2f MB%n", 
                         memIncrease / (1024.0 * 1024.0));
        
        // Should not accumulate significant memory (< 50 MB increase)
        assertTrue(memIncrease < 50 * 1024 * 1024, 
            "Memory usage should be stable, increase was: " + (memIncrease / (1024.0 * 1024.0)) + " MB");
    }
    
    @Test
    @DisplayName("Correctness after optimization")
    void testCorrectnessAfterOptimization() {
        // Create test signal
        double[] data = new double[256];
        for (int i = 0; i < data.length; i++) {
            data[i] = Math.sin(2 * Math.PI * i / 32.0);
        }
        
        VectorWaveSwtAdapter adapter = new VectorWaveSwtAdapter("db4");
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(data, 4);
        
        // Test multiple reconstructions at different levels
        double[] recon1 = result.reconstruct(1);
        double[] recon2 = result.reconstruct(2);
        double[] recon3 = result.reconstruct(3);
        double[] recon4 = result.reconstruct(4);
        
        // Go back to previous levels - should produce same results
        double[] recon1Again = result.reconstruct(1);
        double[] recon3Again = result.reconstruct(3);
        
        // Verify consistency
        assertArrayEquals(recon1, recon1Again, 1e-10, 
            "Reconstruction should be consistent when returning to same level");
        assertArrayEquals(recon3, recon3Again, 1e-10, 
            "Reconstruction should be consistent when returning to same level");
        
        // Verify smoothness increases with fewer levels
        double var1 = calculateVariance(recon1);
        double var2 = calculateVariance(recon2);
        double var3 = calculateVariance(recon3);
        double var4 = calculateVariance(recon4);
        
        assertTrue(var1 < var2, "Level 1 should be smoother than level 2");
        assertTrue(var2 < var3, "Level 2 should be smoother than level 3");
        assertTrue(var3 < var4, "Level 3 should be smoother than level 4");
    }
    
    private double calculateVariance(double[] data) {
        double mean = 0;
        for (double v : data) mean += v;
        mean /= data.length;
        
        double variance = 0;
        for (double v : data) {
            variance += (v - mean) * (v - mean);
        }
        return variance / data.length;
    }
}