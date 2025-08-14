package com.prophetizo.wavelets.swt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;

/**
 * Test for MomentumType.fromString() method to ensure proper null handling
 * and case-insensitive parsing.
 */
class MomentumTypeFromStringTest {
    
    // Use reflection to access the private enum
    private Object callFromString(String value) throws Exception {
        Class<?> outerClass = Class.forName("com.prophetizo.wavelets.swt.SwtTrendMomentumStudy");
        Class<?> enumClass = null;
        
        // Find the MomentumType enum among inner classes
        for (Class<?> innerClass : outerClass.getDeclaredClasses()) {
            if (innerClass.getSimpleName().equals("MomentumType")) {
                enumClass = innerClass;
                break;
            }
        }
        
        assertNotNull(enumClass, "MomentumType enum not found");
        
        Method fromStringMethod = enumClass.getDeclaredMethod("fromString", String.class);
        fromStringMethod.setAccessible(true);
        
        return fromStringMethod.invoke(null, value);
    }
    
    @Test
    @DisplayName("fromString should handle null input safely")
    void testNullInput() throws Exception {
        Object result = callFromString(null);
        assertNotNull(result);
        assertEquals("SUM", result.toString());
    }
    
    @ParameterizedTest
    @DisplayName("fromString should be case-insensitive for valid values")
    @CsvSource({
        "SUM,SUM",
        "sum,SUM",
        "Sum,SUM",
        "sUm,SUM",
        "SIGN,SIGN",
        "sign,SIGN",
        "Sign,SIGN",
        "sIgN,SIGN"
    })
    void testCaseInsensitive(String input, String expected) throws Exception {
        Object result = callFromString(input);
        assertEquals(expected, result.toString());
    }
    
    @ParameterizedTest
    @DisplayName("fromString should handle whitespace")
    @CsvSource({
        "' SUM ',SUM",
        "'  sum  ',SUM",
        "' SIGN ',SIGN",
        "'  sign  ',SIGN"
    })
    void testWhitespaceHandling(String input, String expected) throws Exception {
        Object result = callFromString(input);
        assertEquals(expected, result.toString());
    }
    
    @ParameterizedTest
    @DisplayName("fromString should default to SUM for invalid values")
    @ValueSource(strings = {
        "INVALID",
        "unknown",
        "123",
        "sum_of_details",
        "SIGN_COUNT",
        ""
    })
    void testInvalidValues(String input) throws Exception {
        Object result = callFromString(input);
        assertEquals("SUM", result.toString());
    }
    
    @Test
    @DisplayName("fromString should handle empty string")
    void testEmptyString() throws Exception {
        Object result = callFromString("");
        assertNotNull(result);
        assertEquals("SUM", result.toString());
    }
}