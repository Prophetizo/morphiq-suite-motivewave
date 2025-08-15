package com.morphiqlabs.wavelets.swt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify the getCachedSettingsOrDefault helper method works correctly.
 * This ensures the centralized defensive null handling is properly implemented.
 */
class CachedSettingsHelperTest {
    
    @Test
    @DisplayName("getCachedSettingsOrDefault returns defaults when cachedSettings is null")
    void testGetCachedSettingsOrDefaultWithNull() throws Exception {
        // Create a study instance (cachedSettings starts as null)
        SwtTrendMomentumStudy study = new SwtTrendMomentumStudy();
        
        // Access the private getCachedSettingsOrDefault method via reflection
        Method helperMethod = SwtTrendMomentumStudy.class.getDeclaredMethod(
            "getCachedSettingsOrDefault", String.class);
        helperMethod.setAccessible(true);
        
        // Call the helper method with null cachedSettings
        Object result = helperMethod.invoke(study, "testContext");
        
        // Verify it returns a valid default settings object
        assertNotNull(result, "Helper should return non-null settings");
        assertTrue(result instanceof SwtTrendMomentumStudy.CachedSettings,
                  "Helper should return CachedSettings instance");
        
        SwtTrendMomentumStudy.CachedSettings settings = 
            (SwtTrendMomentumStudy.CachedSettings) result;
        
        // Verify default values
        assertEquals(SwtTrendMomentumStudy.MomentumType.SUM, settings.momentumType,
                    "Default momentum type should be SUM");
        assertTrue(settings.momentumWindow > 0,
                  "Default momentum window should be positive");
        assertTrue(settings.levelWeightDecay > 0 && settings.levelWeightDecay <= 1,
                  "Default level weight decay should be between 0 and 1");
        assertTrue(settings.momentumScalingFactor > 0,
                  "Default momentum scaling factor should be positive");
    }
    
    @Test
    @DisplayName("getCachedSettingsOrDefault returns existing settings when not null")
    void testGetCachedSettingsOrDefaultWithExistingSettings() throws Exception {
        // Create a study instance
        SwtTrendMomentumStudy study = new SwtTrendMomentumStudy();
        
        // Set up cachedSettings using reflection
        java.lang.reflect.Field cachedSettingsField = 
            SwtTrendMomentumStudy.class.getDeclaredField("cachedSettings");
        cachedSettingsField.setAccessible(true);
        
        // Create custom settings
        SwtTrendMomentumStudy.CachedSettings customSettings = 
            new SwtTrendMomentumStudy.CachedSettings(
                SwtTrendMomentumStudy.MomentumType.SIGN,
                25,
                0.75,
                150.0
            );
        
        // Set the custom settings
        cachedSettingsField.set(study, customSettings);
        
        // Access the helper method
        Method helperMethod = SwtTrendMomentumStudy.class.getDeclaredMethod(
            "getCachedSettingsOrDefault", String.class);
        helperMethod.setAccessible(true);
        
        // Call the helper method
        Object result = helperMethod.invoke(study, "testContext");
        
        // Verify it returns the existing settings (same instance)
        assertSame(customSettings, result,
                  "Helper should return existing settings when not null");
        
        // Verify values match
        SwtTrendMomentumStudy.CachedSettings returnedSettings = 
            (SwtTrendMomentumStudy.CachedSettings) result;
        assertEquals(SwtTrendMomentumStudy.MomentumType.SIGN, returnedSettings.momentumType);
        assertEquals(25, returnedSettings.momentumWindow);
        assertEquals(0.75, returnedSettings.levelWeightDecay, 0.001);
        assertEquals(150.0, returnedSettings.momentumScalingFactor, 0.001);
    }
    
    @Test
    @DisplayName("Helper method provides different context in error messages")
    void testContextParameter() throws Exception {
        // This test verifies that the context parameter is used correctly
        // We can't easily test the logging output, but we can verify
        // the method accepts and processes different context strings
        
        SwtTrendMomentumStudy study = new SwtTrendMomentumStudy();
        
        Method helperMethod = SwtTrendMomentumStudy.class.getDeclaredMethod(
            "getCachedSettingsOrDefault", String.class);
        helperMethod.setAccessible(true);
        
        // Test with different context strings
        String[] contexts = {"ensureInitialized", "calculateMomentumSum", "testMethod"};
        
        for (String context : contexts) {
            Object result = helperMethod.invoke(study, context);
            assertNotNull(result, "Helper should always return non-null for context: " + context);
        }
    }
}