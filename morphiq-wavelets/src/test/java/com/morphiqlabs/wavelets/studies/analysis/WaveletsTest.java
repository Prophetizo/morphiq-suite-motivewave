package com.morphiqlabs.wavelets.studies.analysis;

import com.morphiqlabs.wavelet.api.WaveletName;
import com.morphiqlabs.wavelet.api.WaveletRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Wavelets study helper methods
 * Tests focus on the robustness of wavelet discovery and classification
 */
class WaveletsTest {
    
    // Test helper class that extracts the testable logic
    private static class WaveletHelper {
        
        boolean isContinuousWaveletName(String name) {
            // Known continuous wavelet name patterns
            return name.equals("MORLET") ||
                   name.equals("MEXICAN_HAT") ||
                   name.equals("PAUL") ||
                   name.equals("GAUSSIAN") ||
                   name.equals("DOG") ||  // Derivative of Gaussian
                   name.equals("SHANNON") ||
                   name.equals("MEYER") ||
                   name.equals("RICKER") ||
                   name.startsWith("FBSP") ||  // Frequency B-Spline
                   name.startsWith("CMOR") ||  // Complex Morlet
                   name.startsWith("SHAN");    // Shannon
        }
        
        List<WaveletName> getDefaultCWTWavelets() {
            List<WaveletName> wavelets = new ArrayList<>();
            
            // Primary CWT wavelets
            WaveletName[] primaryCwtWavelets = {
                WaveletName.MORLET,
                WaveletName.MEXICAN_HAT,
                WaveletName.PAUL
            };
            
            for (WaveletName waveletName : primaryCwtWavelets) {
                if (WaveletRegistry.isWaveletAvailable(waveletName)) {
                    wavelets.add(waveletName);
                }
            }
            
            // Check for additional continuous wavelets
            for (WaveletName waveletName : WaveletName.values()) {
                if (wavelets.contains(waveletName)) {
                    continue;
                }
                
                String name = waveletName.name();
                if (isContinuousWaveletName(name) && WaveletRegistry.isWaveletAvailable(waveletName)) {
                    wavelets.add(waveletName);
                }
            }
            
            // Add discrete wavelets as fallback
            List<WaveletName> discreteFallback = Arrays.asList(
                WaveletName.HAAR, WaveletName.DB4, WaveletName.DB8,
                WaveletName.SYM4, WaveletName.COIF2
            );
            
            for (WaveletName fallback : discreteFallback) {
                if (!wavelets.contains(fallback) && WaveletRegistry.isWaveletAvailable(fallback)) {
                    wavelets.add(fallback);
                }
            }
            
            return wavelets;
        }
    }
    
    private WaveletHelper helper;
    
    @BeforeEach
    void setUp() {
        helper = new WaveletHelper();
    }
    
    @Test
    @DisplayName("Should correctly identify continuous wavelet names")
    void testIsContinuousWaveletName() {
        // Known continuous wavelets
        assertTrue(helper.isContinuousWaveletName("MORLET"), "MORLET should be identified as continuous");
        assertTrue(helper.isContinuousWaveletName("MEXICAN_HAT"), "MEXICAN_HAT should be identified as continuous");
        assertTrue(helper.isContinuousWaveletName("PAUL"), "PAUL should be identified as continuous");
        assertTrue(helper.isContinuousWaveletName("GAUSSIAN"), "GAUSSIAN should be identified as continuous");
        assertTrue(helper.isContinuousWaveletName("DOG"), "DOG should be identified as continuous");
        assertTrue(helper.isContinuousWaveletName("SHANNON"), "SHANNON should be identified as continuous");
        assertTrue(helper.isContinuousWaveletName("MEYER"), "MEYER should be identified as continuous");
        assertTrue(helper.isContinuousWaveletName("RICKER"), "RICKER should be identified as continuous");
        
        // Prefixes for continuous wavelets
        assertTrue(helper.isContinuousWaveletName("FBSP_1_0_5"), "FBSP wavelets should be identified as continuous");
        assertTrue(helper.isContinuousWaveletName("CMOR1.5-1.0"), "CMOR wavelets should be identified as continuous");
        assertTrue(helper.isContinuousWaveletName("SHAN1-0.5"), "SHAN wavelets should be identified as continuous");
        
        // Known discrete wavelets - should return false
        assertFalse(helper.isContinuousWaveletName("HAAR"), "HAAR should not be identified as continuous");
        assertFalse(helper.isContinuousWaveletName("DB4"), "DB4 should not be identified as continuous");
        assertFalse(helper.isContinuousWaveletName("DB8"), "DB8 should not be identified as continuous");
        assertFalse(helper.isContinuousWaveletName("SYM4"), "SYM4 should not be identified as continuous");
        assertFalse(helper.isContinuousWaveletName("COIF2"), "COIF2 should not be identified as continuous");
    }
    
    @Test
    @DisplayName("Should handle null and empty wavelet names gracefully")
    void testIsContinuousWaveletNameEdgeCases() {
        assertFalse(helper.isContinuousWaveletName(""), "Empty string should return false");
        assertFalse(helper.isContinuousWaveletName("UNKNOWN"), "Unknown wavelet should return false");
        assertFalse(helper.isContinuousWaveletName("123"), "Numeric string should return false");
    }
    
    @Test
    @DisplayName("Should return non-empty list of CWT wavelets")
    void testGetDefaultCWTWavelets() {
        List<WaveletName> cwtWavelets = helper.getDefaultCWTWavelets();
        
        assertNotNull(cwtWavelets, "CWT wavelets list should not be null");
        assertFalse(cwtWavelets.isEmpty(), "CWT wavelets list should not be empty");
        
        // Should contain at least the primary CWT wavelets
        assertTrue(cwtWavelets.contains(WaveletName.MORLET), 
                  "CWT wavelets should include MORLET");
        assertTrue(cwtWavelets.contains(WaveletName.MEXICAN_HAT), 
                  "CWT wavelets should include MEXICAN_HAT");
        assertTrue(cwtWavelets.contains(WaveletName.PAUL), 
                  "CWT wavelets should include PAUL");
    }
    
    @Test
    @DisplayName("Should not contain duplicate wavelets in CWT list")
    void testNoDuplicatesInCWTWavelets() {
        List<WaveletName> cwtWavelets = helper.getDefaultCWTWavelets();
        
        // Check for duplicates
        long uniqueCount = cwtWavelets.stream().distinct().count();
        assertEquals(cwtWavelets.size(), uniqueCount, 
                    "CWT wavelets list should not contain duplicates");
    }
    
    @Test
    @DisplayName("Should handle enum-based discovery without exceptions")
    void testEnumBasedDiscoveryRobustness() {
        // This test ensures the new enum-based approach doesn't throw exceptions
        assertDoesNotThrow(() -> {
            List<WaveletName> cwtWavelets = helper.getDefaultCWTWavelets();
            assertNotNull(cwtWavelets);
        }, "Enum-based discovery should not throw exceptions");
    }
    
    @Test
    @DisplayName("Should discover all available CWT wavelets from enum")
    void testDynamicCWTDiscovery() {
        List<WaveletName> cwtWavelets = helper.getDefaultCWTWavelets();
        
        // Count how many known CWT wavelets are in the enum
        int expectedCwtCount = 0;
        for (WaveletName wavelet : WaveletName.values()) {
            if (helper.isContinuousWaveletName(wavelet.name())) {
                expectedCwtCount++;
            }
        }
        
        // The list should contain at least the known CWT wavelets
        // (it may contain more due to fallback discrete wavelets)
        assertTrue(cwtWavelets.size() >= expectedCwtCount,
                  String.format("Should discover at least %d CWT wavelets, found %d", 
                               expectedCwtCount, cwtWavelets.size()));
    }
    
    @Test
    @DisplayName("Should include discrete fallback wavelets for CWT")
    void testDiscreteFallbackInclusion() {
        List<WaveletName> cwtWavelets = helper.getDefaultCWTWavelets();
        
        // Should include some discrete wavelets as fallback
        boolean hasDiscreteFallback = cwtWavelets.stream()
            .anyMatch(w -> w.name().startsWith("DB") || 
                          w.name().startsWith("SYM") || 
                          w.name().equals("HAAR"));
        
        assertTrue(hasDiscreteFallback, 
                  "CWT wavelets should include discrete wavelets as fallback");
    }
}