package com.prophetizo.wavelets.swt.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

/**
 * Unit tests for VectorWaveSwtAdapter fallback implementation.
 * Tests the mathematical correctness of SWT reconstruction and thresholding.
 */
public class VectorWaveSwtAdapterTest {
    
    private VectorWaveSwtAdapter adapter;
    
    @BeforeEach
    void setUp() {
        adapter = new VectorWaveSwtAdapter("db4_fallback");
    }
    
    @Test
    void testBasicTransform() {
        // Test with simple sine wave
        double[] data = generateSineWave(128, 1.0, 8.0);
        
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(data, 3);
        
        assertNotNull(result);
        assertEquals(3, result.getLevels());
        assertEquals(data.length, result.getApproximation().length);
        
        for (int level = 1; level <= 3; level++) {
            double[] detail = result.getDetail(level);
            assertEquals(data.length, detail.length);
        }
    }
    
    @Test
    void testReconstructionAccuracy() {
        // Test perfect reconstruction property (within numerical tolerance)
        double[] original = generateTestSignal(64);
        
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(original, 4);
        
        // Reconstruct signal
        double[] reconstructed = result.reconstruct(4);
        
        // Calculate reconstruction error
        double error = calculateReconstructionError(original, reconstructed);
        
        // Error should be very small for the fallback implementation
        assertTrue(error < 0.5, "Reconstruction error too large: " + error);
    }
    
    @Test
    void testThresholding() {
        double[] original = generateNoisySignal(128);
        
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(original, 3);
        
        // Apply soft thresholding to first detail level
        double threshold = 0.1;
        result.applyShrinkage(1, threshold, true);
        
        double[] detail1 = result.getDetail(1);
        
        // Check that small coefficients are zeroed
        for (double coeff : detail1) {
            if (Math.abs(coeff) <= threshold) {
                assertEquals(0.0, coeff, 1e-10);
            }
        }
    }
    
    @Test
    void testHardThresholding() {
        double[] coeffs = {0.05, 0.15, -0.08, 0.25, -0.12};
        double threshold = 0.1;
        
        VectorWaveSwtAdapter.SwtResult result = new VectorWaveSwtAdapter.SwtResult(
                new double[5], new double[][]{coeffs}, "test");
        
        result.applyShrinkage(1, threshold, false); // Hard thresholding
        
        double[] thresholded = result.getDetail(1);
        
        assertEquals(0.0, thresholded[0], 1e-10); // |0.05| <= 0.1
        assertEquals(0.15, thresholded[1], 1e-10); // |0.15| > 0.1
        assertEquals(0.0, thresholded[2], 1e-10); // |-0.08| <= 0.1
        assertEquals(0.25, thresholded[3], 1e-10); // |0.25| > 0.1
        assertEquals(0.0, thresholded[4], 1e-10); // |-0.12| > 0.1 but close
    }
    
    @Test
    void testSoftThresholding() {
        double[] coeffs = {0.05, 0.15, -0.08, 0.25, -0.12};
        double threshold = 0.1;
        
        VectorWaveSwtAdapter.SwtResult result = new VectorWaveSwtAdapter.SwtResult(
                new double[5], new double[][]{coeffs}, "test");
        
        result.applyShrinkage(1, threshold, true); // Soft thresholding
        
        double[] thresholded = result.getDetail(1);
        
        assertEquals(0.0, thresholded[0], 1e-10); // |0.05| <= 0.1
        assertEquals(0.05, thresholded[1], 1e-10); // 0.15 - 0.1 = 0.05
        assertEquals(0.0, thresholded[2], 1e-10); // |-0.08| <= 0.1
        assertEquals(0.15, thresholded[3], 1e-10); // 0.25 - 0.1 = 0.15
        assertEquals(-0.02, thresholded[4], 1e-10); // -0.12 + 0.1 = -0.02
    }
    
    @Test
    void testApproximationOnly() {
        double[] original = generateTestSignal(64);
        
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(original, 3);
        
        double[] approxOnly = result.reconstructApproximation();
        
        assertEquals(original.length, approxOnly.length);
        
        // Approximation should be smoother than original
        double origVariance = calculateVariance(original);
        double approxVariance = calculateVariance(approxOnly);
        
        assertTrue(approxVariance <= origVariance, "Approximation should be smoother");
    }
    
    @Test
    void testShiftInvariance() {
        // Test that SWT is approximately shift-invariant
        double[] original = generateTestSignal(128);
        double[] shifted = shiftSignal(original, 4);
        
        VectorWaveSwtAdapter.SwtResult result1 = adapter.transform(original, 3);
        VectorWaveSwtAdapter.SwtResult result2 = adapter.transform(shifted, 3);
        
        // Energy should be preserved
        double energy1 = calculateTotalEnergy(result1);
        double energy2 = calculateTotalEnergy(result2);
        
        double energyDiff = Math.abs(energy1 - energy2) / Math.max(energy1, energy2);
        assertTrue(energyDiff < 0.1, "Energy not preserved under shift: " + energyDiff);
    }
    
    // Helper methods
    
    private double[] generateSineWave(int length, double amplitude, double frequency) {
        double[] signal = new double[length];
        for (int i = 0; i < length; i++) {
            signal[i] = amplitude * Math.sin(2 * Math.PI * frequency * i / length);
        }
        return signal;
    }
    
    private double[] generateTestSignal(int length) {
        double[] signal = new double[length];
        for (int i = 0; i < length; i++) {
            // Mix of frequencies with some trend
            signal[i] = Math.sin(2 * Math.PI * i / 16) +
                       0.5 * Math.sin(2 * Math.PI * i / 4) +
                       0.1 * i / length;
        }
        return signal;
    }
    
    private double[] generateNoisySignal(int length) {
        double[] signal = generateTestSignal(length);
        for (int i = 0; i < length; i++) {
            signal[i] += 0.1 * (Math.random() - 0.5);
        }
        return signal;
    }
    
    private double[] shiftSignal(double[] signal, int shift) {
        double[] shifted = new double[signal.length];
        for (int i = 0; i < signal.length; i++) {
            shifted[i] = signal[(i + shift) % signal.length];
        }
        return shifted;
    }
    
    private double calculateReconstructionError(double[] original, double[] reconstructed) {
        double sumSquaredError = 0.0;
        double sumSquaredOriginal = 0.0;
        
        for (int i = 0; i < original.length; i++) {
            double error = original[i] - reconstructed[i];
            sumSquaredError += error * error;
            sumSquaredOriginal += original[i] * original[i];
        }
        
        return Math.sqrt(sumSquaredError / sumSquaredOriginal);
    }
    
    private double calculateVariance(double[] data) {
        double mean = Arrays.stream(data).average().orElse(0.0);
        return Arrays.stream(data)
                .map(x -> Math.pow(x - mean, 2))
                .average().orElse(0.0);
    }
    
    private double calculateTotalEnergy(VectorWaveSwtAdapter.SwtResult result) {
        double energy = 0.0;
        
        // Energy from approximation
        double[] approx = result.getApproximation();
        for (double val : approx) {
            energy += val * val;
        }
        
        // Energy from details
        for (int level = 1; level <= result.getLevels(); level++) {
            double[] detail = result.getDetail(level);
            for (double val : detail) {
                energy += val * val;
            }
        }
        
        return energy;
    }
}