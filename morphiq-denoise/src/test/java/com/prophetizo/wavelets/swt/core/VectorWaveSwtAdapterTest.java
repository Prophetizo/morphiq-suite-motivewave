package com.prophetizo.wavelets.swt.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Random;

/**
 * Unit tests for VectorWaveSwtAdapter fallback implementation.
 * Tests the mathematical correctness of SWT reconstruction and thresholding.
 */
public class VectorWaveSwtAdapterTest {
    
    private static final long TEST_SEED = 42L;
    private VectorWaveSwtAdapter adapter;
    private Random random;
    
    @BeforeEach
    void setUp() {
        adapter = new VectorWaveSwtAdapter("db4");
        random = new Random(TEST_SEED);
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
        
        // Store original detail coefficients
        double[] originalDetail = result.getDetail(1).clone();
        
        // Apply soft thresholding to first detail level
        double threshold = 0.1;
        result.applyShrinkage(1, threshold, true);
        
        double[] detail1 = result.getDetail(1);
        
        // Check that soft thresholding was applied correctly
        for (int i = 0; i < detail1.length; i++) {
            double origCoeff = originalDetail[i];
            double thresholdedCoeff = detail1[i];
            
            if (Math.abs(origCoeff) <= threshold) {
                // Small coefficients should be zeroed
                assertEquals(0.0, thresholdedCoeff, 1e-10, 
                    "Coefficient below threshold should be zero");
            } else {
                // Large coefficients should be shrunk by threshold amount
                double expected = Math.signum(origCoeff) * (Math.abs(origCoeff) - threshold);
                assertEquals(expected, thresholdedCoeff, 1e-10,
                    "Coefficient above threshold should be shrunk");
            }
        }
    }
    
    @Test
    void testHardThresholding() {
        // Create a simple test signal with deterministic noise
        Random localRandom = new Random(TEST_SEED);
        double[] testSignal = new double[32];
        for (int i = 0; i < testSignal.length; i++) {
            testSignal[i] = Math.sin(2 * Math.PI * i / 32) + 0.1 * localRandom.nextDouble();
        }
        
        // Perform SWT transform
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(testSignal, 2);
        
        // Get original detail coefficients
        double[] originalDetail = result.getDetail(1).clone();
        
        // Apply hard thresholding
        double threshold = 0.1;
        result.applyShrinkage(1, threshold, false);
        
        double[] thresholded = result.getDetail(1);
        
        // Verify hard thresholding behavior
        for (int i = 0; i < originalDetail.length; i++) {
            if (Math.abs(originalDetail[i]) <= threshold) {
                assertEquals(0.0, thresholded[i], 1e-10, 
                    "Coefficients below threshold should be zeroed");
            } else {
                assertEquals(originalDetail[i], thresholded[i], 1e-10,
                    "Coefficients above threshold should be preserved");
            }
        }
    }
    
    @Test
    void testSoftThresholding() {
        // Create a simple test signal with deterministic noise
        Random localRandom = new Random(TEST_SEED);
        double[] testSignal = new double[32];
        for (int i = 0; i < testSignal.length; i++) {
            testSignal[i] = Math.sin(2 * Math.PI * i / 32) + 0.1 * localRandom.nextDouble();
        }
        
        // Perform SWT transform
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(testSignal, 2);
        
        // Get original detail coefficients
        double[] originalDetail = result.getDetail(1).clone();
        
        // Apply soft thresholding
        double threshold = 0.1;
        result.applyShrinkage(1, threshold, true);
        
        double[] thresholded = result.getDetail(1);
        
        // Verify soft thresholding behavior
        for (int i = 0; i < originalDetail.length; i++) {
            if (Math.abs(originalDetail[i]) <= threshold) {
                assertEquals(0.0, thresholded[i], 1e-10,
                    "Coefficients below threshold should be zeroed");
            } else {
                double expected = Math.signum(originalDetail[i]) * 
                                 (Math.abs(originalDetail[i]) - threshold);
                assertEquals(expected, thresholded[i], 1e-10,
                    "Coefficients above threshold should be shrunk by threshold amount");
            }
        }
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
            signal[i] += 0.1 * (random.nextDouble() - 0.5);
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