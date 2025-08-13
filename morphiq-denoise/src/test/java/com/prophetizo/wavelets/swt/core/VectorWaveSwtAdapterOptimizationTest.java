package com.prophetizo.wavelets.swt.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for VectorWaveSwtAdapter reconstruction optimization.
 * Verifies that the caching strategy works correctly and doesn't break functionality.
 */
class VectorWaveSwtAdapterOptimizationTest {
    
    @Test
    @DisplayName("Reconstruction caching should handle same level efficiently")
    void testReconstructionCachingSameLevel() {
        // Create test data
        double[] data = new double[64];
        for (int i = 0; i < data.length; i++) {
            data[i] = Math.sin(2 * Math.PI * i / 32.0) + 0.5 * Math.sin(8 * Math.PI * i / 32.0);
        }
        
        VectorWaveSwtAdapter adapter = new VectorWaveSwtAdapter("db4");
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(data, 3);
        
        // Call reconstruct multiple times with same level
        double[] recon1 = result.reconstruct(2);
        double[] recon2 = result.reconstruct(2);
        double[] recon3 = result.reconstruct(2);
        
        // Results should be identical
        assertArrayEquals(recon1, recon2, 1e-10, "Same level reconstructions should be identical");
        assertArrayEquals(recon2, recon3, 1e-10, "Same level reconstructions should be identical");
        
        // Verify reconstruction quality
        assertEquals(data.length, recon1.length, "Reconstruction should have same length");
    }
    
    @Test
    @DisplayName("Reconstruction caching should handle level changes correctly")
    void testReconstructionCachingLevelChanges() {
        // Create test data with multiple frequency components
        double[] data = new double[128];
        for (int i = 0; i < data.length; i++) {
            data[i] = Math.sin(2 * Math.PI * i / 64.0)     // Low frequency
                    + 0.5 * Math.sin(8 * Math.PI * i / 64.0)  // Medium frequency
                    + 0.2 * Math.sin(32 * Math.PI * i / 64.0); // High frequency
        }
        
        VectorWaveSwtAdapter adapter = new VectorWaveSwtAdapter("db4");
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(data, 4);
        
        // Test different reconstruction levels
        double[] recon1 = result.reconstruct(1); // Only first detail level
        double[] recon2 = result.reconstruct(3); // More detail levels
        double[] recon3 = result.reconstruct(2); // Back to fewer levels
        double[] recon4 = result.reconstruct(3); // Forward again
        double[] recon5 = result.reconstruct(1); // Back to first level
        
        // Verify lengths
        assertEquals(data.length, recon1.length, "All reconstructions should have same length");
        assertEquals(data.length, recon2.length, "All reconstructions should have same length");
        assertEquals(data.length, recon3.length, "All reconstructions should have same length");
        
        // Verify that switching back produces same results
        assertArrayEquals(recon2, recon4, 1e-10, "Level 3 reconstructions should be identical");
        assertArrayEquals(recon1, recon5, 1e-10, "Level 1 reconstructions should be identical");
        
        // Verify that different levels produce different results
        assertFalse(arraysEqual(recon1, recon2, 1e-6), "Different levels should produce different results");
        assertFalse(arraysEqual(recon2, recon3, 1e-6), "Different levels should produce different results");
    }
    
    @Test
    @DisplayName("Reconstruction should handle edge cases")
    void testReconstructionEdgeCases() {
        double[] data = new double[64];
        for (int i = 0; i < data.length; i++) {
            data[i] = Math.random() * 10;
        }
        
        VectorWaveSwtAdapter adapter = new VectorWaveSwtAdapter("db4");
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(data, 4);
        
        // Test reconstruction with max level
        double[] reconMax = result.reconstruct(4);
        assertNotNull(reconMax, "Max level reconstruction should work");
        assertEquals(data.length, reconMax.length, "Reconstruction length should match");
        
        // Test reconstruction with level 0 (only approximation)
        double[] reconZero = result.reconstruct(0);
        assertNotNull(reconZero, "Level 0 reconstruction should work");
        assertEquals(data.length, reconZero.length, "Reconstruction length should match");
        
        // Verify that level 0 is smoothest (least detail)
        double variance0 = calculateVariance(reconZero);
        double variance4 = calculateVariance(reconMax);
        assertTrue(variance0 < variance4, "Level 0 should be smoother than max level");
    }
    
    @Test
    @DisplayName("Reconstruction optimization should not affect denoising")
    void testReconstructionWithThresholding() {
        // Create noisy signal
        double[] data = new double[128];
        for (int i = 0; i < data.length; i++) {
            data[i] = Math.sin(2 * Math.PI * i / 32.0) + 0.1 * Math.random();
        }
        
        VectorWaveSwtAdapter adapter = new VectorWaveSwtAdapter("db4");
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(data, 3);
        
        // Apply thresholding
        for (int level = 1; level <= 3; level++) {
            double[] detail = result.getDetail(level);
            double threshold = Thresholds.calculateUniversalThreshold(detail);
            result.applyShrinkage(level, threshold, true); // Soft thresholding
        }
        
        // Test reconstruction after thresholding
        double[] denoised1 = result.reconstruct(3);
        double[] denoised2 = result.reconstruct(3); // Should use cached result
        
        assertArrayEquals(denoised1, denoised2, 1e-10, "Denoised reconstructions should be identical");
        
        // Change level and back
        double[] denoised3 = result.reconstruct(2);
        double[] denoised4 = result.reconstruct(3);
        
        assertArrayEquals(denoised1, denoised4, 1e-10, "Returning to level 3 should give same result");
    }
    
    // Helper methods
    private boolean arraysEqual(double[] a, double[] b, double tolerance) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > tolerance) return false;
        }
        return true;
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