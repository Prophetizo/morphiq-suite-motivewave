package com.prophetizo.wavelets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;

/**
 * Tests specifically for the new wavelets added with JWave 2.0.0
 */
public class NewWaveletsTest {
    
    // Test constants
    private static final double RECONSTRUCTION_TOLERANCE = 1e-10;
    private static final double ENERGY_TOLERANCE_FACTOR = 0.001; // 0.1% tolerance
    private static final double TEST_DRIFT = 0.0001;
    private static final double TEST_VOLATILITY = 0.02;
    private static final double INITIAL_PRICE = 100.0;
    private static final int TEST_SIGNAL_PERIOD = 32;
    private static final int TEST_SIGNAL_PERIOD_SHORT = 8;
    private static final long RANDOM_SEED = 42L; // Fixed seed for reproducibility

    /*@Test
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
            assertEquals(testData[i], reconstructed[i], RECONSTRUCTION_TOLERANCE, 
                "Symlet4 reconstruction error at index " + i);
        }
    }*/

    /*@Test
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
            assertEquals(testData[i], reconstructed[i], RECONSTRUCTION_TOLERANCE, 
                "Symlet8 reconstruction error at index " + i);
        }
    }*/

    /*@Test
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
            assertEquals(testData[i], reconstructed[i], RECONSTRUCTION_TOLERANCE, 
                "Coiflet3 reconstruction error at index " + i);
        }
    }*/

    /*@Test
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
    }*/

    /*@Test
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
        
        assertEquals(signalEnergy, coeffEnergy, signalEnergy * ENERGY_TOLERANCE_FACTOR, 
            "Energy not preserved in Coiflet3 MODWT");
    }*/

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

    /*@Test
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
    }*/

    // Helper methods
    
    private double[] createTestSignal(int length) {
        double[] signal = new double[length];
        for (int i = 0; i < length; i++) {
            // Combination of trend and oscillation
            double trend = i * 0.01;
            double longCycle = Math.sin(2 * Math.PI * i / TEST_SIGNAL_PERIOD);
            double shortCycle = 0.5 * Math.sin(2 * Math.PI * i / TEST_SIGNAL_PERIOD_SHORT);
            signal[i] = trend + longCycle + shortCycle;
        }
        return signal;
    }
    
    private double[] createFinancialTestSignal(int length) {
        double[] signal = new double[length];
        double price = INITIAL_PRICE;
        Random random = new Random(RANDOM_SEED);
        
        for (int i = 0; i < length; i++) {
            // Random walk with drift
            double randomShock = (random.nextDouble() - 0.5) * TEST_VOLATILITY;
            price = price * (1 + TEST_DRIFT + randomShock);
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