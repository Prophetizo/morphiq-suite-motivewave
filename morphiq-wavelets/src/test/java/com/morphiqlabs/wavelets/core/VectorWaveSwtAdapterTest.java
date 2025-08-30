package com.morphiqlabs.wavelets.core;

import com.morphiqlabs.wavelet.api.BoundaryMode;
import com.morphiqlabs.wavelet.api.WaveletName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VectorWaveSwtAdapter
 * 
 * @author Stephen Campbell
 */
class VectorWaveSwtAdapterTest {
    
    private VectorWaveSwtAdapter adapter;
    private WaveletName waveletName;
    private double[] testSignal;
    private Random random;
    
    @BeforeEach
    void setUp() {
        // Use a seeded random for reproducible tests
        random = new Random(42);
        
        // Create adapter with DB4 wavelet
        waveletName = WaveletName.DB4;
        adapter = new VectorWaveSwtAdapter(waveletName);
        
        // Create a test signal
        int n = 256;
        testSignal = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            // Combination of trend and oscillation
            testSignal[i] = 10 * t + Math.sin(2 * Math.PI * 10 * t) + 0.1 * random.nextGaussian();
        }
    }
    
    @Test
    void testConstructors() {
        // Test constructor with wavelet name only
        VectorWaveSwtAdapter defaultAdapter = new VectorWaveSwtAdapter(WaveletName.HAAR);
        assertNotNull(defaultAdapter);
        
        // Test constructor with boundary mode
        VectorWaveSwtAdapter customAdapter = new VectorWaveSwtAdapter(WaveletName.DB2, BoundaryMode.SYMMETRIC);
        assertNotNull(customAdapter);
    }
    
    @Test
    void testTransform() {
        int levels = 4;
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(testSignal, levels);
        
        assertNotNull(result);
        assertEquals(levels, result.getLevels());
        
        // Check detail coefficients
        for (int level = 1; level <= levels; level++) {
            double[] details = result.getDetail(level);
            assertNotNull(details, "Detail coefficients at level " + level + " should not be null");
            assertEquals(testSignal.length, details.length, 
                        "Detail coefficients length should match signal length");
        }
        
        // Check approximation coefficients
        double[] approx = result.getApproximation();
        assertNotNull(approx);
        assertEquals(testSignal.length, approx.length);
    }
    
    @Test
    void testTransformWithInvalidInput() {
        // Test with null signal - expect exception
        assertThrows(Exception.class, () -> adapter.transform(null, 3));
        
        // Test with empty signal - expect exception
        assertThrows(Exception.class, () -> adapter.transform(new double[0], 3));
        
        // Test with zero levels - expect exception (levels must be >= 1)
        assertThrows(Exception.class, () -> adapter.transform(testSignal, 0));
        
        // Test with negative levels - expect exception
        assertThrows(Exception.class, () -> adapter.transform(testSignal, -1));
    }
    
    @Test
    void testReconstruct() {
        int levels = 3;
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(testSignal, levels);
        assertNotNull(result);
        
        // Test reconstruction at different levels
        for (int level = 1; level <= levels; level++) {
            double[] reconstructed = result.reconstruct(level);
            assertNotNull(reconstructed, "Reconstruction at level " + level + " should not be null");
            assertEquals(testSignal.length, reconstructed.length,
                        "Reconstructed signal length should match original");
            
            // Check that reconstruction contains non-zero values
            boolean hasNonZero = false;
            for (double val : reconstructed) {
                if (val != 0.0) {
                    hasNonZero = true;
                    break;
                }
            }
            assertTrue(hasNonZero, "Reconstructed signal at level " + level + " should have non-zero values");
        }
    }
    
    @Test
    void testReconstructApproximation() {
        int levels = 3;
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(testSignal, levels);
        assertNotNull(result);
        
        double[] reconstructedApprox = result.reconstructApproximation();
        assertNotNull(reconstructedApprox);
        assertEquals(testSignal.length, reconstructedApprox.length);
        
        // Approximation should capture the trend
        boolean hasNonZero = false;
        for (double val : reconstructedApprox) {
            if (val != 0.0) {
                hasNonZero = true;
                break;
            }
        }
        assertTrue(hasNonZero, "Reconstructed approximation should have non-zero values");
    }
    
    @Test
    void testGetDetailAndApproximation() {
        int levels = 4;
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(testSignal, levels);
        assertNotNull(result);
        
        // Test detail coefficients
        for (int level = 1; level <= levels; level++) {
            double[] detail = result.getDetail(level);
            assertNotNull(detail, "Detail at level " + level + " should not be null");
            assertEquals(testSignal.length, detail.length);
        }
        
        // Test approximation coefficients
        double[] approx = result.getApproximation();
        assertNotNull(approx);
        assertEquals(testSignal.length, approx.length);
    }
    
    @Test
    void testDifferentWavelets() {
        // Test with different discrete wavelets
        WaveletName[] discreteWavelets = {
            WaveletName.HAAR,
            WaveletName.DB2,
            WaveletName.DB4,
            WaveletName.DB8,
            WaveletName.SYM4,
            WaveletName.SYM8,
            WaveletName.COIF1,
            WaveletName.COIF2
        };
        
        int levels = 3;
        for (WaveletName wavelet : discreteWavelets) {
            VectorWaveSwtAdapter testAdapter = new VectorWaveSwtAdapter(wavelet);
            VectorWaveSwtAdapter.SwtResult result = testAdapter.transform(testSignal, levels);
            
            assertNotNull(result, "Result should not be null for " + wavelet);
            assertEquals(levels, result.getLevels());
        }
    }
    
    @Test
    void testMultipleTransforms() {
        // Test that multiple transforms work correctly
        int levels = 3;
        
        VectorWaveSwtAdapter.SwtResult result1 = adapter.transform(testSignal, levels);
        VectorWaveSwtAdapter.SwtResult result2 = adapter.transform(testSignal, levels);
        
        assertNotNull(result1);
        assertNotNull(result2);
        
        // Results should be identical for same input
        for (int level = 1; level <= levels; level++) {
            double[] detail1 = result1.getDetail(level);
            double[] detail2 = result2.getDetail(level);
            assertArrayEquals(detail1, detail2, 1e-10, 
                            "Detail coefficients should be identical for same input");
        }
    }
    
    @Test
    void testLargeLevelCount() {
        // Test with maximum reasonable levels - DB4 with 256 samples allows max 6 levels
        int maxLevels = 6;
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(testSignal, maxLevels);
        
        assertNotNull(result);
        assertEquals(maxLevels, result.getLevels());
        
        // All levels should have valid coefficients
        for (int level = 1; level <= maxLevels; level++) {
            double[] details = result.getDetail(level);
            assertNotNull(details);
            assertEquals(testSignal.length, details.length);
        }
    }
    
    @Test
    void testSmallSignal() {
        // Test with small signal - use Haar which has shorter filter length
        VectorWaveSwtAdapter haarAdapter = new VectorWaveSwtAdapter(WaveletName.HAAR);
        double[] smallSignal = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0};
        int levels = 2;
        
        VectorWaveSwtAdapter.SwtResult result = haarAdapter.transform(smallSignal, levels);
        assertNotNull(result);
        assertEquals(levels, result.getLevels());
        
        // Check coefficients
        for (int level = 1; level <= levels; level++) {
            double[] details = result.getDetail(level);
            assertNotNull(details);
            assertEquals(smallSignal.length, details.length);
        }
    }
    
    @Test
    void testConstantSignal() {
        // Test with constant signal (should have zero detail coefficients)
        double[] constantSignal = new double[128];
        for (int i = 0; i < constantSignal.length; i++) {
            constantSignal[i] = 5.0;
        }
        
        int levels = 3;
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(constantSignal, levels);
        assertNotNull(result);
        
        // Detail coefficients should be near zero for constant signal
        for (int level = 1; level <= levels; level++) {
            double[] details = result.getDetail(level);
            double maxAbs = 0;
            for (double val : details) {
                maxAbs = Math.max(maxAbs, Math.abs(val));
            }
            assertTrue(maxAbs < 1e-10, 
                      "Detail coefficients should be near zero for constant signal at level " + level);
        }
        
        // Approximation should be close to the constant value
        double[] approx = result.getApproximation();
        for (double val : approx) {
            assertEquals(5.0, val, 1e-6, "Approximation should preserve constant value within tolerance");
        }
    }
    
    private double calculateVariance(double[] signal) {
        double mean = 0;
        for (double val : signal) {
            mean += val;
        }
        mean /= signal.length;
        
        double variance = 0;
        for (double val : signal) {
            variance += (val - mean) * (val - mean);
        }
        return variance / signal.length;
    }
}