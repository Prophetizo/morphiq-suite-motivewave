package com.morphiqlabs.wavelets.core;

import com.morphiqlabs.wavelet.api.TransformType;
import com.morphiqlabs.wavelet.api.WaveletName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransformWaveletPairTest {
    
    @Test
    void testPairCreation() {
        TransformWaveletPair pair = new TransformWaveletPair(TransformType.MODWT, WaveletName.DB4);
        assertEquals(TransformType.MODWT, pair.getTransform());
        assertEquals(WaveletName.DB4, pair.getWavelet());
        assertEquals("MODWT: Daubechies 4", pair.toString());
    }
    
    @Test
    void testStorageString() {
        TransformWaveletPair pair = new TransformWaveletPair(TransformType.CWT, WaveletName.MORLET);
        assertEquals("CWT:MORLET", pair.toStorageString());
    }
    
    @Test
    void testFromString() {
        TransformWaveletPair pair = TransformWaveletPair.fromString("MODWT:SYM8");
        assertEquals(TransformType.MODWT, pair.getTransform());
        assertEquals(WaveletName.SYM8, pair.getWavelet());
    }
    
    @Test
    void testFromStringWithInvalidInput() {
        // Test null input
        TransformWaveletPair pair = TransformWaveletPair.fromString(null);
        assertEquals(TransformType.MODWT, pair.getTransform());
        assertEquals(WaveletName.DB4, pair.getWavelet());
        
        // Test invalid format
        pair = TransformWaveletPair.fromString("INVALID");
        assertEquals(TransformType.MODWT, pair.getTransform());
        assertEquals(WaveletName.DB4, pair.getWavelet());
        
        // Test invalid enum values
        pair = TransformWaveletPair.fromString("INVALID:WAVELET");
        assertEquals(TransformType.MODWT, pair.getTransform());
        assertEquals(WaveletName.DB4, pair.getWavelet());
    }
    
    @Test
    void testDisplayNames() {
        // Test MODWT wavelets
        assertEquals("MODWT: Haar", new TransformWaveletPair(TransformType.MODWT, WaveletName.HAAR).toString());
        assertEquals("MODWT: Daubechies 2", new TransformWaveletPair(TransformType.MODWT, WaveletName.DB2).toString());
        assertEquals("MODWT: Symlet 4", new TransformWaveletPair(TransformType.MODWT, WaveletName.SYM4).toString());
        assertEquals("MODWT: Coiflet 1", new TransformWaveletPair(TransformType.MODWT, WaveletName.COIF1).toString());
        
        // Test CWT wavelets
        assertEquals("CWT: Morlet", new TransformWaveletPair(TransformType.CWT, WaveletName.MORLET).toString());
        assertEquals("CWT: Mexican Hat", new TransformWaveletPair(TransformType.CWT, WaveletName.MEXICAN_HAT).toString());
        assertEquals("CWT: Paul", new TransformWaveletPair(TransformType.CWT, WaveletName.PAUL).toString());
    }
    
    @Test
    void testEqualsAndHashCode() {
        TransformWaveletPair pair1 = new TransformWaveletPair(TransformType.MODWT, WaveletName.DB4);
        TransformWaveletPair pair2 = new TransformWaveletPair(TransformType.MODWT, WaveletName.DB4);
        TransformWaveletPair pair3 = new TransformWaveletPair(TransformType.CWT, WaveletName.MORLET);
        
        assertEquals(pair1, pair2);
        assertNotEquals(pair1, pair3);
        assertEquals(pair1.hashCode(), pair2.hashCode());
        assertNotEquals(pair1.hashCode(), pair3.hashCode());
    }
}