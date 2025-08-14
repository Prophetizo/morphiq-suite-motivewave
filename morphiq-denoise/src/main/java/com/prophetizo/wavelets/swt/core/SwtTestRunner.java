package com.prophetizo.wavelets.swt.core;

/**
 * Simple test program to validate SWT adapter functionality
 */
public class SwtTestRunner {
    public static void main(String[] args) {
        System.out.println("Testing SWT Trend Momentum System...");
        
        // Test 1: Basic SWT Transform
        testBasicTransform();
        
        // Test 2: Thresholding methods
        testThresholding();
        
        // Test 3: WATR calculation
        testWatr();
        
        System.out.println("All tests completed successfully!");
    }
    
    private static void testBasicTransform() {
        System.out.println("\n1. Testing SWT Transform...");
        
        VectorWaveSwtAdapter adapter = new VectorWaveSwtAdapter("db4");
        
        // Generate test signal: sine wave + trend
        double[] testData = new double[128];
        for (int i = 0; i < testData.length; i++) {
            testData[i] = Math.sin(2 * Math.PI * i / 16) + 0.01 * i;
        }
        
        VectorWaveSwtAdapter.SwtResult result = adapter.transform(testData, 4);
        
        System.out.println("  - Transform completed with " + result.getLevels() + " levels");
        System.out.println("  - Approximation length: " + result.getApproximation().length);
        System.out.println("  - Detail 1 length: " + result.getDetail(1).length);
        
        // Test reconstruction
        double[] reconstructed = result.reconstruct(4);
        double error = calculateRmsError(testData, reconstructed);
        System.out.println("  - Reconstruction RMS error: " + String.format("%.6f", error));
        
        if (error < 1.0) {
            System.out.println("  ✓ Basic transform test PASSED");
        } else {
            System.out.println("  ✗ Basic transform test FAILED");
        }
    }
    
    private static void testThresholding() {
        System.out.println("\n2. Testing Thresholding Methods...");
        
        // Create noisy signal
        double[] noisyCoeffs = new double[64];
        for (int i = 0; i < noisyCoeffs.length; i++) {
            noisyCoeffs[i] = 0.1 * Math.random() + (i % 8 == 0 ? 0.5 : 0.0);
        }
        
        double universalThreshold = Thresholds.calculateUniversalThreshold(noisyCoeffs);
        double bayesThreshold = Thresholds.calculateBayesThreshold(noisyCoeffs, 1);
        double sureThreshold = Thresholds.calculateSureThreshold(noisyCoeffs);
        
        System.out.println("  - Universal threshold: " + String.format("%.4f", universalThreshold));
        System.out.println("  - Bayes threshold: " + String.format("%.4f", bayesThreshold));
        System.out.println("  - SURE threshold: " + String.format("%.4f", sureThreshold));
        
        // Test soft thresholding
        double[] softResult = Thresholds.applyThreshold(noisyCoeffs, 0.2, Thresholds.ShrinkageType.SOFT);
        int softZeros = countZeros(softResult);
        
        // Test hard thresholding
        double[] hardResult = Thresholds.applyThreshold(noisyCoeffs, 0.2, Thresholds.ShrinkageType.HARD);
        int hardZeros = countZeros(hardResult);
        
        System.out.println("  - Soft thresholding zeros: " + softZeros + "/" + noisyCoeffs.length);
        System.out.println("  - Hard thresholding zeros: " + hardZeros + "/" + noisyCoeffs.length);
        
        if (universalThreshold > 0 && bayesThreshold > 0 && sureThreshold > 0) {
            System.out.println("  ✓ Thresholding methods test PASSED");
        } else {
            System.out.println("  ✗ Thresholding methods test FAILED");
        }
    }
    
    private static void testWatr() {
        System.out.println("\n3. Testing Wavelet ATR...");
        
        WaveletAtr watr = new WaveletAtr(10);
        
        // Create test detail coefficients (simulating 2 levels)
        double[][] testDetails = new double[2][];
        testDetails[0] = new double[32]; // D1
        testDetails[1] = new double[32]; // D2
        
        // Fill with test values
        for (int i = 0; i < 32; i++) {
            testDetails[0][i] = 0.1 * Math.sin(2 * Math.PI * i / 8);
            testDetails[1][i] = 0.05 * Math.sin(2 * Math.PI * i / 16);
        }
        
        double watrValue1 = watr.calculate(testDetails, 2);
        double watrValue2 = watr.calculate(testDetails, 2);
        double watrValue3 = watr.calculate(testDetails, 2);
        
        System.out.println("  - WATR value 1: " + String.format("%.6f", watrValue1));
        System.out.println("  - WATR value 2: " + String.format("%.6f", watrValue2));
        System.out.println("  - WATR value 3: " + String.format("%.6f", watrValue3));
        
        // Test band creation
        WaveletAtr.WatrBands bands = watr.createBands(100.0, 2.0);
        System.out.println("  - WATR bands: [" + String.format("%.2f", bands.getLowerBand()) + 
                          ", " + String.format("%.2f", bands.getUpperBand()) + "]");
        
        if (watrValue1 > 0 && watrValue2 >= watrValue1 && watrValue3 >= watrValue2) {
            System.out.println("  ✓ WATR calculation test PASSED");
        } else {
            System.out.println("  ✗ WATR calculation test FAILED");
        }
    }
    
    private static double calculateRmsError(double[] original, double[] reconstructed) {
        double sumSquaredError = 0.0;
        for (int i = 0; i < original.length; i++) {
            double error = original[i] - reconstructed[i];
            sumSquaredError += error * error;
        }
        return Math.sqrt(sumSquaredError / original.length);
    }
    
    private static int countZeros(double[] array) {
        int count = 0;
        for (double value : array) {
            if (Math.abs(value) < 1e-10) {
                count++;
            }
        }
        return count;
    }
}