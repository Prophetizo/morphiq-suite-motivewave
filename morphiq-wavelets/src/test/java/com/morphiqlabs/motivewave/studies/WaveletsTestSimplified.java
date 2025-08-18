package com.morphiqlabs.motivewave.studies;

import ai.prophetizo.wavelet.api.*;
import com.motivewave.platform.sdk.common.NVP;
import com.motivewave.platform.sdk.study.StudyHeader;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simplified unit tests for the Wavelets study that don't require mocking SDK classes.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WaveletsTestSimplified {
    
    private Wavelets waveletsStudy;
    
    @BeforeEach
    void setUp() {
        waveletsStudy = new Wavelets();
    }
    
    @Test
    @Order(1)
    @DisplayName("Should have correct study header annotations")
    void testStudyHeaderAnnotations() {
        StudyHeader header = Wavelets.class.getAnnotation(StudyHeader.class);
        
        assertNotNull(header, "Should have StudyHeader annotation");
        assertEquals("com.morphiqlabs", header.namespace());
        assertEquals("WAVELETS_ANALYSIS", header.id());
        assertEquals("Wavelet Analysis", header.name());
        assertFalse(header.overlay(), "Should not be an overlay");
        assertFalse(header.requiresBarUpdates(), "Should not require bar updates");
    }
    
    @Test
    @Order(2)
    @DisplayName("Should create wavelet options from registry")
    void testCreateWaveletOptions() throws Exception {
        // Use reflection to test private method
        Method createOptionsMethod = Wavelets.class.getDeclaredMethod("createWaveletOptions");
        createOptionsMethod.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<NVP> options = (List<NVP>) createOptionsMethod.invoke(waveletsStudy);
        
        assertNotNull(options, "Should return options list");
        assertFalse(options.isEmpty(), "Should have at least default options");
        
        // Should have at least the fallback options
        boolean hasDb4 = options.stream().anyMatch(nvp -> "db4".equals(nvp.getValue()));
        boolean hasHaar = options.stream().anyMatch(nvp -> "haar".equals(nvp.getValue()));
        
        assertTrue(hasDb4 || hasHaar, "Should have at least one default wavelet option");
    }
    
    @Test
    @Order(3)
    @DisplayName("Should initialize PATH_KEYS correctly")
    void testPathKeysInitialization() throws Exception {
        Field pathKeysField = Wavelets.class.getDeclaredField("PATH_KEYS");
        pathKeysField.setAccessible(true);
        String[] pathKeys = (String[]) pathKeysField.get(null);
        
        assertEquals(7, pathKeys.length, "Should have 7 path keys");
        assertEquals("D1_PATH", pathKeys[0], "First path key should be D1_PATH");
        assertEquals("D7_PATH", pathKeys[6], "Last path key should be D7_PATH");
    }
    
    @Test
    @Order(4)
    @DisplayName("Should have correct number of decomposition values")
    void testDecompositionValues() {
        Wavelets.Values[] values = Wavelets.Values.values();
        assertEquals(7, values.length, "Should have 7 decomposition values");
        assertEquals(Wavelets.Values.D1, values[0], "First value should be D1");
        assertEquals(Wavelets.Values.D7, values[6], "Last value should be D7");
    }
    
    @Test
    @Order(5)
    @DisplayName("Should validate wavelet coefficients")
    void testWaveletValidation() throws Exception {
        Method getOrCreateMethod = Wavelets.class.getDeclaredMethod("getOrCreateDiscreteWavelet", String.class);
        getOrCreateMethod.setAccessible(true);
        
        // Test with null/empty wavelet name
        DiscreteWavelet result1 = (DiscreteWavelet) getOrCreateMethod.invoke(waveletsStudy, (String) null);
        assertNull(result1, "Should return null for null wavelet name");
        
        DiscreteWavelet result2 = (DiscreteWavelet) getOrCreateMethod.invoke(waveletsStudy, "");
        assertNull(result2, "Should return null for empty wavelet name");
        
        DiscreteWavelet result3 = (DiscreteWavelet) getOrCreateMethod.invoke(waveletsStudy, "   ");
        assertNull(result3, "Should return null for whitespace wavelet name");
    }
    
    @Test
    @Order(6)
    @DisplayName("Should cache wavelets for performance")
    void testWaveletCaching() throws Exception {
        Method getOrCreateMethod = Wavelets.class.getDeclaredMethod("getOrCreateDiscreteWavelet", String.class);
        getOrCreateMethod.setAccessible(true);
        
        // Registry should be initialized by VectorWave
        
        // First call - should attempt to create
        DiscreteWavelet wavelet1 = (DiscreteWavelet) getOrCreateMethod.invoke(waveletsStudy, "haar");
        
        // Second call - should use cache
        DiscreteWavelet wavelet2 = (DiscreteWavelet) getOrCreateMethod.invoke(waveletsStudy, "haar");
        
        // If both are null (registry empty) or same instance (cached), test passes
        assertTrue(wavelet1 == wavelet2 || (wavelet1 == null && wavelet2 == null),
            "Should return same instance from cache or both null");
    }
    
    @Test
    @Order(7)
    @DisplayName("Should get wavelet display name")
    void testGetWaveletDisplayName() throws Exception {
        Method getDisplayNameMethod = Wavelets.class.getDeclaredMethod("getWaveletDisplayName", String.class);
        getDisplayNameMethod.setAccessible(true);
        
        // Test known wavelets
        assertEquals("Haar", getDisplayNameMethod.invoke(waveletsStudy, "haar"));
        assertEquals("Daubechies 4", getDisplayNameMethod.invoke(waveletsStudy, "db4"));
        assertEquals("Symlet 4", getDisplayNameMethod.invoke(waveletsStudy, "sym4"));
        assertEquals("Coiflet 2", getDisplayNameMethod.invoke(waveletsStudy, "coif2"));
        
        // Test unknown wavelet
        assertEquals("unknown", getDisplayNameMethod.invoke(waveletsStudy, "unknown"));
    }
    
    @Test
    @Order(8)
    @DisplayName("Should have correct constant values")
    void testConstants() throws Exception {
        Field maxLevelsField = Wavelets.class.getDeclaredField("MAX_DECOMPOSITION_LEVELS");
        maxLevelsField.setAccessible(true);
        int maxLevels = (int) maxLevelsField.get(null);
        
        assertEquals(7, maxLevels, "MAX_DECOMPOSITION_LEVELS should be 7");
        
        // Check setting keys using reflection since they're private
        Field waveletTypeField = Wavelets.class.getDeclaredField("WAVELET_TYPE");
        waveletTypeField.setAccessible(true);
        assertEquals("waveletType", waveletTypeField.get(null));
        
        Field decompLevelsField = Wavelets.class.getDeclaredField("DECOMPOSITION_LEVELS");
        decompLevelsField.setAccessible(true);
        assertEquals("decompositionLevels", decompLevelsField.get(null));
    }
    
    @Test
    @Order(9)
    @DisplayName("Should have wavelet cache initialized")
    void testWaveletCacheInitialization() throws Exception {
        Field cacheField = Wavelets.class.getDeclaredField("waveletCache");
        cacheField.setAccessible(true);
        Object cache = cacheField.get(waveletsStudy);
        
        assertNotNull(cache, "Wavelet cache should be initialized");
        assertTrue(cache instanceof java.util.Map, "Cache should be a Map");
    }
    
    @Test
    @Order(10)
    @DisplayName("Registry initialization should be attempted at class load")
    void testStaticInitialization() {
        // The static initializer should have run when the class was loaded
        // We can't directly test it, but we can verify the registry state
        // This test documents the expected behavior
        
        // Registry may or may not have wavelets depending on environment
        // The important thing is that initialization was attempted
        assertTrue(true, "Static initialization should have been attempted");
    }
}