package com.prophetizo.wavelets;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WaveletType enum.
 */
public class WaveletTypeTest {


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

}