package com.prophetizo.wavelets;

//import jwave.transforms.wavelets.Wavelet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WaveletType enum.
 */
public class WaveletTypeTest {

    /*@Test
    public void testAllWaveletsCanBeCreated() {
        for (WaveletType type : WaveletType.values()) {
            Wavelet wavelet = type.createWavelet();
            assertNotNull(wavelet, "Wavelet creation failed for " + type);
            
            // Verify wavelet has a name
            assertNotNull(wavelet.getName(), "Wavelet name is null for " + type);
            assertFalse(wavelet.getName().isEmpty(), "Wavelet name is empty for " + type);
        }
    }*/

    @Test
    public void testWaveletTypeDisplayNames() {
        assertEquals("Daubechies4", WaveletType.DAUBECHIES4.getDisplayName());
        assertEquals("Daubechies6", WaveletType.DAUBECHIES6.getDisplayName());
        assertEquals("Haar", WaveletType.HAAR.getDisplayName());
        assertEquals("Daubechies2", WaveletType.DAUBECHIES2.getDisplayName());
    }

    @Test
    public void testWaveletTypeParsing() {
        // Test exact match
        assertEquals(WaveletType.DAUBECHIES4, WaveletType.parse("Daubechies4"));
        assertEquals(WaveletType.DAUBECHIES6, WaveletType.parse("Daubechies6"));
        assertEquals(WaveletType.HAAR, WaveletType.parse("Haar"));
        
        // Test case insensitive enum name match
        assertEquals(WaveletType.HAAR, WaveletType.parse("HAAR"));
        assertEquals(WaveletType.DAUBECHIES2, WaveletType.parse("daubechies2"));
        
        // Test invalid input
        assertEquals(WaveletType.DAUBECHIES4, WaveletType.parse("InvalidWavelet"));
        assertEquals(WaveletType.DAUBECHIES4, WaveletType.parse(""));
        assertEquals(WaveletType.DAUBECHIES4, WaveletType.parse(null));
    }

    @Test
    public void testComputationalCost() {
        // Haar should be the fastest
        assertEquals(1.0, WaveletType.HAAR.getComputationalCost());
        
        // Others should have higher cost
        assertTrue(WaveletType.DAUBECHIES2.getComputationalCost() > WaveletType.HAAR.getComputationalCost());
        assertTrue(WaveletType.DAUBECHIES4.getComputationalCost() > WaveletType.DAUBECHIES2.getComputationalCost());
        assertTrue(WaveletType.DAUBECHIES6.getComputationalCost() > WaveletType.DAUBECHIES4.getComputationalCost());
    }

    @Test
    public void testRecommendedUse() {
        // Each wavelet should have a non-empty recommended use description
        for (WaveletType type : WaveletType.values()) {
            String recommendedUse = type.getRecommendedUse();
            assertNotNull(recommendedUse, "Recommended use is null for " + type);
            assertFalse(recommendedUse.isEmpty(), "Recommended use is empty for " + type);
        }
    }

    /*@Test
    public void testWaveletFactoryIntegration() {
        // Test that factory can create analyzers for all wavelet types
        for (WaveletType type : WaveletType.values()) {
            WaveletAnalyzer analyzer = WaveletAnalyzerFactory.create(type);
            assertNotNull(analyzer, "Failed to create analyzer for " + type);
        }
    }*/

    /*@Test
    public void testHaarWaveletSpecifics() {
        WaveletType haar = WaveletType.HAAR;
        Wavelet wavelet = haar.createWavelet();
        
        // Haar wavelet should have specific properties
        assertEquals("Haar", wavelet.getName());
        
        // Test that it can be used in analysis
        WaveletAnalyzer analyzer = WaveletAnalyzerFactory.create(haar);
        double[] testData = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0};
        
        // Should not throw exception
        assertDoesNotThrow(() -> {
            double[] transformed = analyzer.forwardTransform(testData);
            assertNotNull(transformed);
            assertEquals(testData.length, transformed.length);
        });
    }*/

    /*@Test
    public void testDaubechies2WaveletSpecifics() {
        WaveletType db2 = WaveletType.DAUBECHIES2;
        Wavelet wavelet = db2.createWavelet();
        
        // Daubechies2 should have specific properties
        assertEquals("Daubechies 2", wavelet.getName());
        
        // Test transform
        WaveletAnalyzer analyzer = WaveletAnalyzerFactory.create(db2);
        double[] testData = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0};
        
        assertDoesNotThrow(() -> {
            double[] transformed = analyzer.forwardTransform(testData);
            assertNotNull(transformed);
            
            // Test reverse transform
            double[] reconstructed = analyzer.reverseTransform(transformed);
            assertNotNull(reconstructed);
            assertEquals(testData.length, reconstructed.length);
            
            // Check reconstruction accuracy
            for (int i = 0; i < testData.length; i++) {
                assertEquals(testData[i], reconstructed[i], 1e-10, 
                    "Reconstruction error at index " + i);
            }
        });
    }*/
}