package com.prophetizo.wavelets.swt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for MomentumType.fromString() method to ensure proper null handling
 * and case-insensitive parsing.
 * 
 * Note: MomentumType is package-private to allow direct testing without reflection,
 * while keeping it encapsulated from external packages.
 */
class MomentumTypeFromStringTest {
    
    @Test
    @DisplayName("fromString should handle null input safely")
    void testNullInput() {
        SwtTrendMomentumStudy.MomentumType result = SwtTrendMomentumStudy.MomentumType.fromString(null);
        assertNotNull(result);
        assertEquals(SwtTrendMomentumStudy.MomentumType.SUM, result);
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
    void testCaseInsensitive(String input, String expectedName) {
        SwtTrendMomentumStudy.MomentumType result = SwtTrendMomentumStudy.MomentumType.fromString(input);
        assertEquals(expectedName, result.name());
    }
    
    @ParameterizedTest
    @DisplayName("fromString should handle whitespace")
    @CsvSource({
        "' SUM ',SUM",
        "'  sum  ',SUM",
        "' SIGN ',SIGN",
        "'  sign  ',SIGN"
    })
    void testWhitespaceHandling(String input, String expectedName) {
        SwtTrendMomentumStudy.MomentumType result = SwtTrendMomentumStudy.MomentumType.fromString(input);
        assertEquals(expectedName, result.name());
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
    void testInvalidValues(String input) {
        SwtTrendMomentumStudy.MomentumType result = SwtTrendMomentumStudy.MomentumType.fromString(input);
        assertEquals(SwtTrendMomentumStudy.MomentumType.SUM, result);
    }
    
    @Test
    @DisplayName("fromString should handle empty string")
    void testEmptyString() {
        SwtTrendMomentumStudy.MomentumType result = SwtTrendMomentumStudy.MomentumType.fromString("");
        assertNotNull(result);
        assertEquals(SwtTrendMomentumStudy.MomentumType.SUM, result);
    }
    
    @Test
    @DisplayName("Enum values should be accessible")
    void testEnumValues() {
        // Verify we can access the enum values directly
        SwtTrendMomentumStudy.MomentumType[] values = SwtTrendMomentumStudy.MomentumType.values();
        assertEquals(2, values.length);
        
        // Verify the specific values exist
        assertEquals(SwtTrendMomentumStudy.MomentumType.SUM, values[0]);
        assertEquals(SwtTrendMomentumStudy.MomentumType.SIGN, values[1]);
    }
    
    @Test
    @DisplayName("Direct enum comparison should work")
    void testDirectEnumComparison() {
        SwtTrendMomentumStudy.MomentumType sum1 = SwtTrendMomentumStudy.MomentumType.fromString("SUM");
        SwtTrendMomentumStudy.MomentumType sum2 = SwtTrendMomentumStudy.MomentumType.fromString("sum");
        SwtTrendMomentumStudy.MomentumType sign = SwtTrendMomentumStudy.MomentumType.fromString("SIGN");
        
        // Same enum instance should be returned
        assertSame(sum1, sum2);
        assertNotSame(sum1, sign);
        
        // Direct comparison should work
        assertTrue(sum1 == SwtTrendMomentumStudy.MomentumType.SUM);
        assertTrue(sign == SwtTrendMomentumStudy.MomentumType.SIGN);
    }
}