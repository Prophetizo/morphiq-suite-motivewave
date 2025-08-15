package com.morphiqlabs.wavelets.swt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that CachedSettings.createDefault() uses the correct constants
 * and avoids magic numbers.
 * 
 * Note: Both CachedSettings and the default constants are package-private to allow 
 * direct testing without reflection, while keeping them encapsulated from external packages.
 */
class CachedSettingsDefaultsTest {
    
    @Test
    @DisplayName("CachedSettings.createDefault() should use defined constants")
    void testDefaultsUseConstants() {
        // Access the default constants directly (they're package-private)
        int expectedMomentumWindow = SwtTrendMomentumStudy.DEFAULT_MOMENTUM_WINDOW;
        double expectedScalingFactor = SwtTrendMomentumStudy.DEFAULT_MOMENTUM_SCALING_FACTOR;
        double expectedLevelDecay = SwtTrendMomentumStudy.DEFAULT_LEVEL_WEIGHT_DECAY;
        
        // Verify the constants have the expected values
        assertEquals(10, expectedMomentumWindow, "DEFAULT_MOMENTUM_WINDOW should be 10");
        assertEquals(100.0, expectedScalingFactor, "DEFAULT_MOMENTUM_SCALING_FACTOR should be 100.0");
        assertEquals(0.5, expectedLevelDecay, "DEFAULT_LEVEL_WEIGHT_DECAY should be 0.5");
        
        // Call createDefault() directly without reflection
        SwtTrendMomentumStudy.CachedSettings defaultSettings = 
            SwtTrendMomentumStudy.CachedSettings.createDefault();
        assertNotNull(defaultSettings, "createDefault() should return a non-null instance");
        
        // Verify the fields in the created instance
        assertEquals(expectedMomentumWindow, defaultSettings.momentumWindow, 
            "createDefault() should use DEFAULT_MOMENTUM_WINDOW");
        
        assertEquals(expectedScalingFactor, defaultSettings.momentumScalingFactor, 0.0001,
            "createDefault() should use DEFAULT_MOMENTUM_SCALING_FACTOR");
        
        assertEquals(expectedLevelDecay, defaultSettings.levelWeightDecay, 0.0001,
            "createDefault() should use DEFAULT_LEVEL_WEIGHT_DECAY");
        
        assertEquals(SwtTrendMomentumStudy.MomentumType.SUM, defaultSettings.momentumType, 
            "createDefault() should default to MomentumType.SUM");
    }
    
    @Test
    @DisplayName("No magic numbers should exist in initialization")
    void testNoMagicNumbers() {
        // Create default settings
        SwtTrendMomentumStudy.CachedSettings defaultSettings = 
            SwtTrendMomentumStudy.CachedSettings.createDefault();
        
        // Verify no magic numbers - all values should come from constants
        assertNotNull(defaultSettings);
        
        // The important thing is that DEFAULT_LEVEL_WEIGHT_DECAY exists and equals 0.5
        double levelDecay = SwtTrendMomentumStudy.DEFAULT_LEVEL_WEIGHT_DECAY;
        
        assertEquals(0.5, levelDecay, 0.0001, 
            "DEFAULT_LEVEL_WEIGHT_DECAY should be defined as 0.5 to replace magic number");
        
        // Verify the created settings use this constant
        assertEquals(levelDecay, defaultSettings.levelWeightDecay, 0.0001,
            "Default settings should use DEFAULT_LEVEL_WEIGHT_DECAY constant");
    }
    
    @Test
    @DisplayName("CachedSettings should be immutable")
    void testImmutability() {
        // Create default settings
        SwtTrendMomentumStudy.CachedSettings settings = 
            SwtTrendMomentumStudy.CachedSettings.createDefault();
        
        // All fields should be final (checked at compile time)
        // Values should not change after construction
        SwtTrendMomentumStudy.MomentumType originalType = settings.momentumType;
        int originalWindow = settings.momentumWindow;
        double originalDecay = settings.levelWeightDecay;
        double originalScaling = settings.momentumScalingFactor;
        
        // Create another instance and verify the first hasn't changed
        SwtTrendMomentumStudy.CachedSettings settings2 = 
            new SwtTrendMomentumStudy.CachedSettings(
                SwtTrendMomentumStudy.MomentumType.SIGN, 20, 0.7, 200.0);
        
        // Original should be unchanged
        assertEquals(originalType, settings.momentumType);
        assertEquals(originalWindow, settings.momentumWindow);
        assertEquals(originalDecay, settings.levelWeightDecay, 0.0001);
        assertEquals(originalScaling, settings.momentumScalingFactor, 0.0001);
    }
    
    @Test
    @DisplayName("Default constants should be accessible without reflection")
    void testConstantsAccessibility() {
        // Verify we can access all default constants directly
        assertDoesNotThrow(() -> {
            double smoothing = SwtTrendMomentumStudy.DEFAULT_MOMENTUM_SMOOTHING;
            int window = SwtTrendMomentumStudy.DEFAULT_MOMENTUM_WINDOW;
            double scaling = SwtTrendMomentumStudy.DEFAULT_MOMENTUM_SCALING_FACTOR;
            double decay = SwtTrendMomentumStudy.DEFAULT_LEVEL_WEIGHT_DECAY;
            double slope = SwtTrendMomentumStudy.DEFAULT_MIN_SLOPE_THRESHOLD;
            
            // Verify expected values
            assertEquals(0.5, smoothing, 0.0001, "DEFAULT_MOMENTUM_SMOOTHING");
            assertEquals(10, window, "DEFAULT_MOMENTUM_WINDOW");
            assertEquals(100.0, scaling, 0.0001, "DEFAULT_MOMENTUM_SCALING_FACTOR");
            assertEquals(0.5, decay, 0.0001, "DEFAULT_LEVEL_WEIGHT_DECAY");
            assertEquals(0.05, slope, 0.0001, "DEFAULT_MIN_SLOPE_THRESHOLD");
        });
    }
}