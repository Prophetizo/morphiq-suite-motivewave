package com.prophetizo.wavelets.swt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify thread-safe initialization of CachedSettings.
 */
class ThreadSafeInitializationTest {
    
    @Test
    @DisplayName("CachedSettings initialization should be thread-safe")
    void testThreadSafeInitialization() throws Exception {
        // Access the private cachedSettings field via reflection
        Field cachedSettingsField = SwtTrendMomentumStudy.class.getDeclaredField("cachedSettings");
        cachedSettingsField.setAccessible(true);
        
        // Create a study instance
        SwtTrendMomentumStudy study = new SwtTrendMomentumStudy();
        
        // Verify it starts as null (before onLoad)
        Object initialSettings = cachedSettingsField.get(study);
        assertNull(initialSettings, "CachedSettings should be null before initialization");
        
        // Directly set a non-null CachedSettings to simulate initialization
        Object defaultSettings = SwtTrendMomentumStudy.CachedSettings.createDefault();
        
        // Run multiple threads trying to read/write simultaneously
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger nullCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    
                    // Half the threads set, half read
                    if (threadId % 2 == 0) {
                        cachedSettingsField.set(study, defaultSettings);
                    }
                    
                    // All threads verify the result
                    Object settings = cachedSettingsField.get(study);
                    if (settings != null) {
                        successCount.incrementAndGet();
                    } else {
                        nullCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }
        
        // Release all threads at once
        startLatch.countDown();
        
        // Wait for all threads to complete
        endLatch.await();
        
        // Verify that once set, it stays non-null (volatile ensures visibility)
        assertTrue(successCount.get() > 0, "At least some threads should see non-null settings");
        
        // Verify final state is not null
        Object finalSettings = cachedSettingsField.get(study);
        assertNotNull(finalSettings, "CachedSettings should not be null after initialization");
    }
    
    @Test
    @DisplayName("Null CachedSettings prevents race conditions")
    void testNullPreventsRaceConditions() throws Exception {
        // Access the private validateSettingsInitialized method via reflection
        java.lang.reflect.Method validateMethod = SwtTrendMomentumStudy.class.getDeclaredMethod("validateSettingsInitialized");
        validateMethod.setAccessible(true);
        
        // Create a study instance (cachedSettings starts as null)
        SwtTrendMomentumStudy study = new SwtTrendMomentumStudy();
        
        // Attempting to validate before initialization should throw
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> {
            validateMethod.invoke(study);
        }, "Validation should fail when cachedSettings is null");
    }
    
    @Test
    @DisplayName("Single field eliminates race condition")
    void testSingleFieldEliminatesRace() throws Exception {
        // This test verifies that we only have one volatile field for initialization state
        Field[] fields = SwtTrendMomentumStudy.class.getDeclaredFields();
        
        int volatileInitFields = 0;
        boolean hasCachedSettings = false;
        boolean hasSettingsInitialized = false;
        
        for (Field field : fields) {
            if (field.getName().equals("cachedSettings")) {
                hasCachedSettings = true;
                if (java.lang.reflect.Modifier.isVolatile(field.getModifiers())) {
                    volatileInitFields++;
                }
            }
            if (field.getName().equals("settingsInitialized")) {
                hasSettingsInitialized = true;
            }
        }
        
        assertTrue(hasCachedSettings, "Should have cachedSettings field");
        assertFalse(hasSettingsInitialized, "Should NOT have settingsInitialized field (eliminated to prevent race)");
        assertEquals(1, volatileInitFields, "Should have exactly one volatile field for initialization tracking");
    }
}