package com.prophetizo.wavelets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests specifically for the new wavelets added with JWave 2.0.0
 */
public class NewWaveletsTest {

    @Test
    @DisplayName("Test Symlet4 wavelet transform and reconstruction")
    public void testSymlet4() {
        WaveletAnalyzer analyzer = WaveletAnalyzerFactory.create(WaveletType.SYMLET4);
        
        // Test data with known pattern
        double[] testData = createTestSignal(128);
        
        // Forward transform
        double[] transformed = analyzer.forwardTransform(testData);
        assertNotNull(transformed);
        assertEquals(testData.length, transformed.length);
        
        // Reverse transform
        double[] reconstructed = analyzer.reverseTransform(transformed);
        assertNotNull(reconstructed);
        assertEquals(testData.length, reconstructed.length);
        
        // Check reconstruction accuracy
        for (int i = 0; i < testData.length; i++) {
            assertEquals(testData[i], reconstructed[i], 1e-10, 
                "Symlet4 reconstruction error at index " + i);
        }
    }

    @Test
    @DisplayName("Test Symlet8 wavelet transform and reconstruction")
    public void testSymlet8() {
        WaveletAnalyzer analyzer = WaveletAnalyzerFactory.create(WaveletType.SYMLET8);
        
        double[] testData = createTestSignal(256);
        
        // Forward transform
        double[] transformed = analyzer.forwardTransform(testData);
        assertNotNull(transformed);
        assertEquals(testData.length, transformed.length);
        
        // Reverse transform
        double[] reconstructed = analyzer.reverseTransform(transformed);
        assertNotNull(reconstructed);
        assertEquals(testData.length, reconstructed.length);
        
        // Check reconstruction accuracy
        for (int i = 0; i < testData.length; i++) {
            assertEquals(testData[i], reconstructed[i], 1e-10, 
                "Symlet8 reconstruction error at index " + i);
        }
    }

    @Test
    @DisplayName("Test Coiflet3 wavelet transform and reconstruction")
    public void testCoiflet3() {
        WaveletAnalyzer analyzer = WaveletAnalyzerFactory.create(WaveletType.COIFLET3);
        
        double[] testData = createTestSignal(512);
        
        // Forward transform
        double[] transformed = analyzer.forwardTransform(testData);
        assertNotNull(transformed);
        assertEquals(testData.length, transformed.length);
        
        // Reverse transform
        double[] reconstructed = analyzer.reverseTransform(transformed);
        assertNotNull(reconstructed);
        assertEquals(testData.length, reconstructed.length);
        
        // Check reconstruction accuracy
        for (int i = 0; i < testData.length; i++) {
            assertEquals(testData[i], reconstructed[i], 1e-10, 
                "Coiflet3 reconstruction error at index " + i);
        }
    }

    @Test
    @DisplayName("Test MODWT with Symlet4")
    public void testMODWTSymlet4() {
        WaveletAnalyzer analyzer = WaveletAnalyzerFactory.create(WaveletType.SYMLET4);
        
        // Test with non-power-of-two length
        double[] testData = createTestSignal(300);
        int levels = 4;
        
        double[][] coefficients = analyzer.performForwardMODWT(testData, levels);
        assertNotNull(coefficients);
        assertEquals(levels + 1, coefficients.length); // levels + approximation
        
        for (int i = 0; i < coefficients.length; i++) {
            assertNotNull(coefficients[i]);
            assertEquals(testData.length, coefficients[i].length);
        }
    }

    @Test
    @DisplayName("Test MODWT with Coiflet3")
    public void testMODWTCoiflet3() {
        WaveletAnalyzer analyzer = WaveletAnalyzerFactory.create(WaveletType.COIFLET3);
        
        // Test with typical financial data length
        double[] testData = createFinancialTestSignal(390); // Trading day minutes
        int levels = 5;
        
        double[][] coefficients = analyzer.performForwardMODWT(testData, levels);
        assertNotNull(coefficients);
        assertEquals(levels + 1, coefficients.length);
        
        // Verify energy preservation
        double signalEnergy = calculateEnergy(testData);
        double coeffEnergy = 0;
        for (double[] level : coefficients) {
            coeffEnergy += calculateEnergy(level);
        }
        
        assertEquals(signalEnergy, coeffEnergy, signalEnergy * 0.001, 
            "Energy not preserved in Coiflet3 MODWT");
    }

    @Test
    @DisplayName("Test wavelet characteristics match documentation")
    public void testWaveletCharacteristics() {
        // Symlet4 should be good for day trading
        assertEquals("Day trading, momentum detection, minimal phase distortion", 
            WaveletType.SYMLET4.getRecommendedUse());
        
        // Symlet8 should be good for swing trading
        assertEquals("Swing trading, trend following, pattern recognition", 
            WaveletType.SYMLET8.getRecommendedUse());
        
        // Coiflet3 should be good for position trading
        assertEquals("Position trading, smooth trends, volatility analysis", 
            WaveletType.COIFLET3.getRecommendedUse());
    }

    @Test
    @DisplayName("Test computational cost ordering")
    public void testComputationalCostOrdering() {
        // Verify computational costs are in expected order
        assertTrue(WaveletType.HAAR.getComputationalCost() < 
                  WaveletType.SYMLET4.getComputationalCost());
        assertTrue(WaveletType.SYMLET4.getComputationalCost() < 
                  WaveletType.SYMLET8.getComputationalCost());
        assertTrue(WaveletType.COIFLET3.getComputationalCost() > 
                  WaveletType.DAUBECHIES6.getComputationalCost());
    }

    @Test
    @DisplayName("Test factory use case recommendations")
    public void testFactoryUseCaseRecommendations() {
        // Day trading should recommend Symlet4
        WaveletAnalyzer dayTrading = WaveletAnalyzerFactory.createForUseCase(
            WaveletAnalyzerFactory.UseCase.DAY_TRADING);
        assertNotNull(dayTrading);
        
        // Swing trading should recommend Symlet8
        WaveletAnalyzer swingTrading = WaveletAnalyzerFactory.createForUseCase(
            WaveletAnalyzerFactory.UseCase.SWING_TRADING);
        assertNotNull(swingTrading);
        
        // Position trading should recommend Coiflet3
        WaveletAnalyzer positionTrading = WaveletAnalyzerFactory.createForUseCase(
            WaveletAnalyzerFactory.UseCase.POSITION_TRADING);
        assertNotNull(positionTrading);
    }

    // Helper methods
    
    private double[] createTestSignal(int length) {
        double[] signal = new double[length];
        for (int i = 0; i < length; i++) {
            // Combination of trend and oscillation
            signal[i] = i * 0.01 + Math.sin(2 * Math.PI * i / 32.0) 
                      + 0.5 * Math.sin(2 * Math.PI * i / 8.0);
        }
        return signal;
    }
    
    private double[] createFinancialTestSignal(int length) {
        double[] signal = new double[length];
        double price = 100.0;
        double volatility = 0.02;
        
        for (int i = 0; i < length; i++) {
            // Random walk with drift
            double drift = 0.0001;
            double randomShock = (Math.random() - 0.5) * volatility;
            price = price * (1 + drift + randomShock);
            signal[i] = price;
        }
        return signal;
    }
    
    private double calculateEnergy(double[] signal) {
        double energy = 0;
        for (double value : signal) {
            energy += value * value;
        }
        return energy;
    }
}