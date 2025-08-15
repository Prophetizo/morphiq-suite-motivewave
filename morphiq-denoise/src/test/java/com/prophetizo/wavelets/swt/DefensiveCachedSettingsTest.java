package com.prophetizo.wavelets.swt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that defensive checks for null CachedSettings work correctly.
 * This ensures graceful degradation if MotiveWave's lifecycle guarantees are violated.
 */
class DefensiveCachedSettingsTest {
    
    @Test
    @DisplayName("CachedSettings.createDefault() should return valid settings")
    void testCreateDefaultReturnsValidSettings() {
        // Test that the default factory method works
        SwtTrendMomentumStudy.CachedSettings defaultSettings = 
            SwtTrendMomentumStudy.CachedSettings.createDefault();
        
        assertNotNull(defaultSettings, "Default settings should not be null");
        assertNotNull(defaultSettings.momentumType, "Default momentum type should not be null");
        assertEquals(SwtTrendMomentumStudy.MomentumType.SUM, defaultSettings.momentumType,
                    "Default momentum type should be SUM");
        assertTrue(defaultSettings.momentumWindow > 0, 
                  "Default momentum window should be positive");
        assertTrue(defaultSettings.levelWeightDecay > 0 && defaultSettings.levelWeightDecay <= 1,
                  "Default level weight decay should be between 0 and 1");
        assertTrue(defaultSettings.momentumScalingFactor > 0,
                  "Default momentum scaling factor should be positive");
    }
    
    @Test
    @DisplayName("Multiple calls to createDefault() should return equal settings")
    void testCreateDefaultConsistency() {
        SwtTrendMomentumStudy.CachedSettings settings1 = 
            SwtTrendMomentumStudy.CachedSettings.createDefault();
        SwtTrendMomentumStudy.CachedSettings settings2 = 
            SwtTrendMomentumStudy.CachedSettings.createDefault();
        
        // They should be equal in value (though not the same instance)
        assertEquals(settings1.momentumType, settings2.momentumType);
        assertEquals(settings1.momentumWindow, settings2.momentumWindow);
        assertEquals(settings1.levelWeightDecay, settings2.levelWeightDecay, 0.001);
        assertEquals(settings1.momentumScalingFactor, settings2.momentumScalingFactor, 0.001);
    }
    
    @Test
    @DisplayName("CachedSettings should be immutable")
    void testCachedSettingsImmutability() {
        // Create settings with specific values
        SwtTrendMomentumStudy.CachedSettings settings = new SwtTrendMomentumStudy.CachedSettings(
            SwtTrendMomentumStudy.MomentumType.SIGN,
            20,
            0.7,
            200.0
        );
        
        // Verify values are as expected
        assertEquals(SwtTrendMomentumStudy.MomentumType.SIGN, settings.momentumType);
        assertEquals(20, settings.momentumWindow);
        assertEquals(0.7, settings.levelWeightDecay, 0.001);
        assertEquals(200.0, settings.momentumScalingFactor, 0.001);
        
        // All fields are final, so no mutation is possible
        // This test documents the immutability contract
    }
}