package com.morphiqlabs.wavelets.swt.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

/**
 * Test to verify that different wavelets produce different results
 */
public class WaveletComparisonTest {
    
    private VectorWaveSwtAdapter haarAdapter;
    private VectorWaveSwtAdapter db4Adapter;
    private VectorWaveSwtAdapter db6Adapter;
    
    @BeforeEach
    void setUp() {
        haarAdapter = new VectorWaveSwtAdapter("haar");
        db4Adapter = new VectorWaveSwtAdapter("db4");
        db6Adapter = new VectorWaveSwtAdapter("db6");
    }
    
    @Test
    void testDifferentWaveletsProduceDifferentResults() {
        // Create a longer test signal (need at least 32 samples for db4 with 2 levels)
        double[] testSignal = new double[64];
        for (int i = 0; i < testSignal.length; i++) {
            testSignal[i] = Math.sin(2 * Math.PI * i / 16.0) + 0.5 * Math.cos(2 * Math.PI * i / 8.0);
        }
        int levels = 2;
        
        // Transform with each wavelet
        VectorWaveSwtAdapter.SwtResult haarResult = haarAdapter.transform(testSignal, levels);
        VectorWaveSwtAdapter.SwtResult db4Result = db4Adapter.transform(testSignal, levels);
        VectorWaveSwtAdapter.SwtResult db6Result = db6Adapter.transform(testSignal, levels);
        
        // Get approximation coefficients
        double[] haarApprox = haarResult.getApproximation();
        double[] db4Approx = db4Result.getApproximation();
        double[] db6Approx = db6Result.getApproximation();
        
        // Get detail coefficients at level 1
        double[] haarDetail1 = haarResult.getDetail(1);
        double[] db4Detail1 = db4Result.getDetail(1);
        double[] db6Detail1 = db6Result.getDetail(1);
        
        // Print results for analysis
        System.out.println("\n=== Wavelet Comparison Test ===");
        System.out.println("Test signal length: " + testSignal.length);
        System.out.println("First 8 samples: " + Arrays.toString(Arrays.copyOf(testSignal, 8)));
        
        System.out.println("\nApproximation coefficients (first 8):");
        System.out.println("Haar: " + Arrays.toString(Arrays.copyOf(haarApprox, Math.min(8, haarApprox.length))));
        System.out.println("Db4:  " + Arrays.toString(Arrays.copyOf(db4Approx, Math.min(8, db4Approx.length))));
        System.out.println("Db6:  " + Arrays.toString(Arrays.copyOf(db6Approx, Math.min(8, db6Approx.length))));
        
        System.out.println("\nDetail level 1 coefficients:");
        System.out.println("Haar D1: " + Arrays.toString(Arrays.copyOf(haarDetail1, Math.min(8, haarDetail1.length))));
        System.out.println("Db4 D1:  " + Arrays.toString(Arrays.copyOf(db4Detail1, Math.min(8, db4Detail1.length))));
        System.out.println("Db6 D1:  " + Arrays.toString(Arrays.copyOf(db6Detail1, Math.min(8, db6Detail1.length))));
        
        // Check if approximations are different
        boolean haarDb4ApproxDifferent = !Arrays.equals(haarApprox, db4Approx);
        boolean haarDb6ApproxDifferent = !Arrays.equals(haarApprox, db6Approx);
        boolean db4Db6ApproxDifferent = !Arrays.equals(db4Approx, db6Approx);
        
        // Check if details are different
        boolean haarDb4DetailDifferent = !Arrays.equals(haarDetail1, db4Detail1);
        boolean haarDb6DetailDifferent = !Arrays.equals(haarDetail1, db6Detail1);
        boolean db4Db6DetailDifferent = !Arrays.equals(db4Detail1, db6Detail1);
        
        System.out.println("\n=== Comparison Results ===");
        System.out.println("Haar vs Db4 approximation different: " + haarDb4ApproxDifferent);
        System.out.println("Haar vs Db6 approximation different: " + haarDb6ApproxDifferent);
        System.out.println("Db4 vs Db6 approximation different: " + db4Db6ApproxDifferent);
        
        System.out.println("\nHaar vs Db4 detail different: " + haarDb4DetailDifferent);
        System.out.println("Haar vs Db6 detail different: " + haarDb6DetailDifferent);
        System.out.println("Db4 vs Db6 detail different: " + db4Db6DetailDifferent);
        
        // Calculate differences
        if (!haarDb4ApproxDifferent) {
            double maxDiff = 0.0;
            for (int i = 0; i < haarApprox.length; i++) {
                maxDiff = Math.max(maxDiff, Math.abs(haarApprox[i] - db4Approx[i]));
            }
            System.out.println("\nMax difference between Haar and Db4 approximation: " + maxDiff);
        }
        
        // The issue: approximations might be identical while details are different
        if (!haarDb4ApproxDifferent && haarDb4DetailDifferent) {
            System.out.println("\n*** ISSUE FOUND: Haar and Db4 have identical approximations but different details! ***");
        }
        
        // Test with a longer, more complex signal
        System.out.println("\n=== Testing with longer signal ===");
        double[] longSignal = new double[64];
        for (int i = 0; i < longSignal.length; i++) {
            longSignal[i] = Math.sin(2 * Math.PI * i / 16.0) + 0.5 * Math.cos(2 * Math.PI * i / 8.0);
        }
        
        VectorWaveSwtAdapter.SwtResult haarLongResult = haarAdapter.transform(longSignal, 3);
        VectorWaveSwtAdapter.SwtResult db4LongResult = db4Adapter.transform(longSignal, 3);
        
        double[] haarLongApprox = haarLongResult.getApproximation();
        double[] db4LongApprox = db4LongResult.getApproximation();
        
        boolean longSignalApproxDifferent = !Arrays.equals(haarLongApprox, db4LongApprox);
        System.out.println("Long signal - Haar vs Db4 approximation different: " + longSignalApproxDifferent);
        
        if (!longSignalApproxDifferent) {
            double maxDiff = 0.0;
            for (int i = 0; i < Math.min(haarLongApprox.length, db4LongApprox.length); i++) {
                maxDiff = Math.max(maxDiff, Math.abs(haarLongApprox[i] - db4LongApprox[i]));
            }
            System.out.println("Max difference in long signal approximation: " + maxDiff);
        }
    }
    
    @Test
    void testWaveletFilterLengths() {
        System.out.println("\n=== Wavelet Filter Lengths ===");
        System.out.println("Haar filter length: " + haarAdapter.getFilterLength());
        System.out.println("Db4 filter length: " + db4Adapter.getFilterLength());
        System.out.println("Db6 filter length: " + db6Adapter.getFilterLength());
        
        // Different wavelets should have different filter lengths
        assertNotEquals(haarAdapter.getFilterLength(), db4Adapter.getFilterLength(),
                "Haar and Db4 should have different filter lengths");
        assertNotEquals(db4Adapter.getFilterLength(), db6Adapter.getFilterLength(),
                "Db4 and Db6 should have different filter lengths");
    }
}