package com.morphiqlabs.wavelets.swt.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

/**
 * Test wavelet switching behavior - simulating what happens in SwtTrendMomentumSimple
 */
public class WaveletSwitchingTest {
    
    @Test
    void testWaveletSwitchingBehavior() {
        // Simulate real market data
        double[] marketData = new double[256];
        for (int i = 0; i < marketData.length; i++) {
            // Simulate price with trend and noise
            marketData[i] = 100.0 + i * 0.01 + Math.sin(2 * Math.PI * i / 32.0) * 2.0 + Math.random() * 0.5;
        }
        
        // Test switching between wavelets like in the study
        System.out.println("\n=== Simulating Wavelet Switching in Study ===");
        
        // First use Haar
        VectorWaveSwtAdapter adapter = new VectorWaveSwtAdapter("haar");
        VectorWaveSwtAdapter.SwtResult haarResult = adapter.transform(marketData, 4);
        double[] haarApprox = haarResult.getApproximation();
        double[] haarDetail1 = haarResult.getDetail(1);
        double[] haarDetail2 = haarResult.getDetail(2);
        
        // Calculate momentum like in the study (sum of D1 and D2)
        double haarMomentum = haarDetail1[haarDetail1.length - 1] + haarDetail2[haarDetail2.length - 1];
        double haarTrend = haarApprox[haarApprox.length - 1];
        
        System.out.println("Haar:");
        System.out.println("  Trend (last A_j): " + haarTrend);
        System.out.println("  Momentum (D1+D2): " + haarMomentum);
        System.out.println("  First 5 approx: " + Arrays.toString(Arrays.copyOf(haarApprox, 5)));
        
        // Now switch to Db4 (recreating adapter like in the study)
        adapter = new VectorWaveSwtAdapter("db4");
        VectorWaveSwtAdapter.SwtResult db4Result = adapter.transform(marketData, 4);
        double[] db4Approx = db4Result.getApproximation();
        double[] db4Detail1 = db4Result.getDetail(1);
        double[] db4Detail2 = db4Result.getDetail(2);
        
        double db4Momentum = db4Detail1[db4Detail1.length - 1] + db4Detail2[db4Detail2.length - 1];
        double db4Trend = db4Approx[db4Approx.length - 1];
        
        System.out.println("\nDb4:");
        System.out.println("  Trend (last A_j): " + db4Trend);
        System.out.println("  Momentum (D1+D2): " + db4Momentum);
        System.out.println("  First 5 approx: " + Arrays.toString(Arrays.copyOf(db4Approx, 5)));
        
        // Now Db6
        adapter = new VectorWaveSwtAdapter("db6");
        VectorWaveSwtAdapter.SwtResult db6Result = adapter.transform(marketData, 4);
        double[] db6Approx = db6Result.getApproximation();
        double[] db6Detail1 = db6Result.getDetail(1);
        double[] db6Detail2 = db6Result.getDetail(2);
        
        double db6Momentum = db6Detail1[db6Detail1.length - 1] + db6Detail2[db6Detail2.length - 1];
        double db6Trend = db6Approx[db6Approx.length - 1];
        
        System.out.println("\nDb6:");
        System.out.println("  Trend (last A_j): " + db6Trend);
        System.out.println("  Momentum (D1+D2): " + db6Momentum);
        System.out.println("  First 5 approx: " + Arrays.toString(Arrays.copyOf(db6Approx, 5)));
        
        // Check differences
        System.out.println("\n=== Differences ===");
        double haarDb4TrendDiff = Math.abs(haarTrend - db4Trend);
        double haarDb6TrendDiff = Math.abs(haarTrend - db6Trend);
        double db4Db6TrendDiff = Math.abs(db4Trend - db6Trend);
        
        double haarDb4MomDiff = Math.abs(haarMomentum - db4Momentum);
        double haarDb6MomDiff = Math.abs(haarMomentum - db6Momentum);
        double db4Db6MomDiff = Math.abs(db4Momentum - db6Momentum);
        
        System.out.println("Trend differences:");
        System.out.println("  |Haar - Db4|: " + haarDb4TrendDiff);
        System.out.println("  |Haar - Db6|: " + haarDb6TrendDiff);
        System.out.println("  |Db4 - Db6|: " + db4Db6TrendDiff);
        
        System.out.println("\nMomentum differences:");
        System.out.println("  |Haar - Db4|: " + haarDb4MomDiff);
        System.out.println("  |Haar - Db6|: " + haarDb6MomDiff);
        System.out.println("  |Db4 - Db6|: " + db4Db6MomDiff);
        
        // Check if arrays are actually different
        boolean haarDb4ApproxSame = Arrays.equals(haarApprox, db4Approx);
        boolean haarDb6ApproxSame = Arrays.equals(haarApprox, db6Approx);
        boolean db4Db6ApproxSame = Arrays.equals(db4Approx, db6Approx);
        
        System.out.println("\nArray equality checks:");
        System.out.println("  Haar == Db4 approx: " + haarDb4ApproxSame);
        System.out.println("  Haar == Db6 approx: " + haarDb6ApproxSame);
        System.out.println("  Db4 == Db6 approx: " + db4Db6ApproxSame);
        
        // Assertions
        assertFalse(haarDb4ApproxSame, "Haar and Db4 should produce different approximations");
        assertFalse(haarDb6ApproxSame, "Haar and Db6 should produce different approximations");
        assertFalse(db4Db6ApproxSame, "Db4 and Db6 should produce different approximations");
        
        assertTrue(haarDb4TrendDiff > 0.01, "Trend should differ by more than 0.01");
        assertTrue(haarDb6TrendDiff > 0.01, "Trend should differ by more than 0.01");
        
        System.out.println("\n✅ All wavelets produce different results as expected!");
    }
    
    @Test
    void testRecreatingAdapterWithSameWavelet() {
        double[] data = new double[128];
        for (int i = 0; i < data.length; i++) {
            data[i] = Math.sin(2 * Math.PI * i / 32.0);
        }
        
        // Create first adapter
        VectorWaveSwtAdapter adapter1 = new VectorWaveSwtAdapter("db4");
        VectorWaveSwtAdapter.SwtResult result1 = adapter1.transform(data, 3);
        double trend1 = result1.getApproximation()[result1.getApproximation().length - 1];
        
        // Create second adapter with same wavelet
        VectorWaveSwtAdapter adapter2 = new VectorWaveSwtAdapter("db4");
        VectorWaveSwtAdapter.SwtResult result2 = adapter2.transform(data, 3);
        double trend2 = result2.getApproximation()[result2.getApproximation().length - 1];
        
        // Should produce identical results
        assertEquals(trend1, trend2, 1e-10, "Same wavelet should produce identical results");
        
        // Now switch to different wavelet
        VectorWaveSwtAdapter adapter3 = new VectorWaveSwtAdapter("haar");
        VectorWaveSwtAdapter.SwtResult result3 = adapter3.transform(data, 3);
        double trend3 = result3.getApproximation()[result3.getApproximation().length - 1];
        
        // Should produce different results
        assertNotEquals(trend1, trend3, "Different wavelets should produce different results");
        
        System.out.println("\n=== Adapter Recreation Test ===");
        System.out.println("Db4 (first):  " + trend1);
        System.out.println("Db4 (second): " + trend2);
        System.out.println("Haar:         " + trend3);
        System.out.println("Difference Db4-Haar: " + Math.abs(trend1 - trend3));
    }
}