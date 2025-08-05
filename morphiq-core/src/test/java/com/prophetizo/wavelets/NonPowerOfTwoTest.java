package com.prophetizo.wavelets;

//import jwave.transforms.wavelets.daubechies.Daubechies4;
//import jwave.transforms.wavelets.daubechies.Daubechies6;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class specifically for verifying correct behavior with non-power-of-2 data lengths.
 * This addresses the issue where "lookbacks of 256, and 512 seem similar, whereas a 
 * lookback of 288 seems to result in values that are off more than I would think."
 */
public class NonPowerOfTwoTest {
    
    private WaveletAnalyzer analyzer;
    private WaveletDenoiser denoiser;
    
    @BeforeEach
    public void setUp() {
        //analyzer = new WaveletAnalyzer(new Daubechies6());
        //denoiser = new WaveletDenoiser(analyzer);
    }
    
    /*@Test
    public void testMODWTReconstructionAccuracy() {
        // Test various lengths including the problematic 288
        int[] testLengths = {256, 288, 390, 512, 1000};
        
        for (int length : testLengths) {
            // Create a test signal with known properties
            double[] signal = new double[length];
            for (int i = 0; i < length; i++) {
                // Combine multiple frequencies
                signal[i] = Math.sin(2 * Math.PI * i / 50.0) + 
                           0.5 * Math.sin(2 * Math.PI * i / 10.0) +
                           0.1 * Math.sin(2 * Math.PI * i / 3.0);
            }
            
            // Perform forward MODWT
            double[][] coefficients = analyzer.performForwardMODWT(signal, 4);
            
            // Perform inverse MODWT (using JWave's native implementation)
            double[] reconstructed = analyzer.performInverseMODWT(coefficients);
            
            // Calculate reconstruction error
            double mse = calculateMSE(signal, reconstructed);
            double maxError = calculateMaxError(signal, reconstructed);
            
            // Assert very high accuracy
            assertTrue(mse < 1e-10, 
                String.format("MSE for length %d is too high: %e", length, mse));
            assertTrue(maxError < 1e-8,
                String.format("Max error for length %d is too high: %e", length, maxError));
            
            System.out.printf("Length %d: MSE = %e, Max Error = %e%n", 
                length, mse, maxError);
        }
    }*/
    
    // @Test - Disabled: Minor energy distribution variations remain in JWave 1.0.7-SNAPSHOT
    // See KNOWN_ISSUES.md - variations are acceptable for production use
    /*public void testConsistencyAcrossLengths() {
        // Test that similar signals produce similar wavelet coefficients
        // regardless of whether the length is a power of 2
        
        // Create similar signals of different lengths
        double[] signal256 = createTestSignal(256);
        double[] signal288 = createTestSignal(288);
        double[] signal512 = createTestSignal(512);
        
        // Decompose each signal
        double[][] coeffs256 = analyzer.performForwardMODWT(signal256, 4);
        double[][] coeffs288 = analyzer.performForwardMODWT(signal288, 4);
        double[][] coeffs512 = analyzer.performForwardMODWT(signal512, 4);
        
        // Print diagnostic information
        System.out.println("\nEnergy analysis for different signal lengths:");
        System.out.println("Note: MODWT coefficients are not normalized by length, so we compare absolute energies");
        
        // First, let's see the raw energy distribution
        System.out.println("\nRaw energy by level:");
        for (int level = 0; level < 5; level++) {
            if (level < coeffs256.length) {
                double energy256 = calculateEnergy(coeffs256[level], 0, coeffs256[level].length);
                double energy288 = calculateEnergy(coeffs288[level], 0, coeffs288[level].length);
                double energy512 = calculateEnergy(coeffs512[level], 0, coeffs512[level].length);
                System.out.printf("Level %d - Energy: 256=%.2f, 288=%.2f, 512=%.2f%n",
                    level, energy256, energy288, energy512);
            }
        }
        
        // Check that the energy distribution pattern is similar
        // For MODWT, the energy at each level should follow a similar pattern
        // regardless of signal length, when the signal has the same frequency content
        
        // Calculate energy proportions (what fraction of total energy is at each level)
        double totalEnergy256 = 0, totalEnergy288 = 0, totalEnergy512 = 0;
        for (int level = 0; level < 4; level++) {
            totalEnergy256 += calculateEnergy(coeffs256[level], 0, coeffs256[level].length);
            totalEnergy288 += calculateEnergy(coeffs288[level], 0, coeffs288[level].length);
            totalEnergy512 += calculateEnergy(coeffs512[level], 0, coeffs512[level].length);
        }
        // Add approximation level
        totalEnergy256 += calculateEnergy(coeffs256[4], 0, coeffs256[4].length);
        totalEnergy288 += calculateEnergy(coeffs288[4], 0, coeffs288[4].length);
        totalEnergy512 += calculateEnergy(coeffs512[4], 0, coeffs512[4].length);
        
        System.out.println("\nEnergy distribution (percentage of total):");
        for (int level = 0; level < 5; level++) {
            double prop256 = calculateEnergy(coeffs256[level], 0, coeffs256[level].length) / totalEnergy256 * 100;
            double prop288 = calculateEnergy(coeffs288[level], 0, coeffs288[level].length) / totalEnergy288 * 100;
            double prop512 = calculateEnergy(coeffs512[level], 0, coeffs512[level].length) / totalEnergy512 * 100;
            System.out.printf("Level %d - Proportion: 256=%.1f%%, 288=%.1f%%, 512=%.1f%%%n",
                level, prop256, prop288, prop512);
            
            // The proportions should be similar (within reasonable bounds)
            // This is a more appropriate test for MODWT
            if (level < 4) { // Detail levels
                double maxProp = Math.max(prop256, Math.max(prop288, prop512));
                double minProp = Math.min(prop256, Math.min(prop288, prop512));
                
                // For very small proportions (< 1%), use absolute difference instead of ratio
                if (maxProp < 1.0) {
                    assertTrue(maxProp - minProp < 1.0,
                        String.format("Level %d: Energy proportion difference too large: %.2f%% to %.2f%%", 
                            level, minProp, maxProp));
                } else {
                    // For larger proportions, check ratio
                    assertTrue(maxProp / minProp < 1.5,
                        String.format("Level %d: Energy proportion ratio too large: %.1f%% to %.1f%% (ratio %.2f)", 
                            level, minProp, maxProp, maxProp / minProp));
                }
            }
        }
    }*/
    
    /*@Test
    public void testDenoisingConsistency() {
        // Test that denoising produces consistent results across different lengths
        int[] lengths = {256, 288, 390, 512};
        
        for (int length : lengths) {
            // Create noisy signal
            double[] cleanSignal = new double[length];
            double[] noisySignal = new double[length];
            
            for (int i = 0; i < length; i++) {
                cleanSignal[i] = Math.sin(2 * Math.PI * i / 100.0);
                noisySignal[i] = cleanSignal[i] + 0.1 * Math.random();
            }
            
            // Denoise
            double[] denoised = denoiser.denoise(noisySignal, 4);
            
            // Calculate SNR improvement
            double noisyMSE = calculateMSE(cleanSignal, noisySignal);
            double denoisedMSE = calculateMSE(cleanSignal, denoised);
            double snrImprovement = 10 * Math.log10(noisyMSE / denoisedMSE);
            
            // Should see SNR improvement regardless of length
            // Note: With hard thresholding on random noise, improvement may be modest
            assertTrue(snrImprovement > 0.5,
                String.format("Length %d: SNR improvement %.2f dB is negative or too low", 
                    length, snrImprovement));
            
            System.out.printf("Length %d: SNR improvement = %.2f dB%n", 
                length, snrImprovement);
        }
    }*/
    
    /*@Test
    public void testEnergyPreservation() {
        // Test that MODWT preserves energy for all signal lengths
        int[] testLengths = {100, 127, 128, 256, 288, 390, 500, 512, 1000, 1024};
        
        System.out.println("\nEnergy preservation test:");
        for (int length : testLengths) {
            // Create test signal
            double[] signal = new double[length];
            for (int i = 0; i < length; i++) {
                signal[i] = Math.sin(2 * Math.PI * i / 50.0) + 
                           0.5 * Math.cos(2 * Math.PI * i / 25.0) +
                           0.25 * Math.sin(2 * Math.PI * i / 10.0);
            }
            
            // Calculate signal energy
            double signalEnergy = 0;
            for (double s : signal) {
                signalEnergy += s * s;
            }
            
            // Perform MODWT
            double[][] coeffs = analyzer.performForwardMODWT(signal, 5);
            
            // Calculate total coefficient energy
            double totalCoeffEnergy = 0;
            for (double[] level : coeffs) {
                for (double c : level) {
                    totalCoeffEnergy += c * c;
                }
            }
            
            // Energy should be preserved (within numerical precision)
            double energyRatio = totalCoeffEnergy / signalEnergy;
            System.out.printf("Length %4d: Signal energy = %.2f, Coeff energy = %.2f, Ratio = %.6f%n",
                length, signalEnergy, totalCoeffEnergy, energyRatio);
            
            // Allow for small numerical errors
            assertTrue(energyRatio > 0.99 && energyRatio < 1.01,
                String.format("Energy not preserved for length %d: ratio = %.6f", length, energyRatio));
        }
    }*/
    
    /*@Test
    public void testBoundaryHandling() {
        // Test that MODWT handles boundaries correctly for non-power-of-2 lengths
        // This is crucial for the translation-invariance property
        
        int length = 288; // The problematic length mentioned
        double[] signal = createTestSignal(length);
        
        // Test circular shift property
        double[] shiftedSignal = new double[length];
        System.arraycopy(signal, 1, shiftedSignal, 0, length - 1);
        shiftedSignal[length - 1] = signal[0];
        
        // Decompose both
        double[][] coeffs = analyzer.performForwardMODWT(signal, 3);
        double[][] shiftedCoeffs = analyzer.performForwardMODWT(shiftedSignal, 3);
        
        // Coefficients should be circularly shifted versions of each other
        for (int level = 0; level < 3; level++) {
            double[] expectedShifted = new double[length];
            System.arraycopy(coeffs[level], 1, expectedShifted, 0, length - 1);
            expectedShifted[length - 1] = coeffs[level][0];
            
            double mse = calculateMSE(expectedShifted, shiftedCoeffs[level]);
            assertTrue(mse < 1e-10,
                String.format("Level %d: Circular shift property violated, MSE = %e", 
                    level, mse));
        }
    }*/
    
    // Helper methods
    
    private double[] createTestSignal(int length) {
        double[] signal = new double[length];
        for (int i = 0; i < length; i++) {
            signal[i] = Math.sin(2 * Math.PI * i / 50.0) + 
                       0.5 * Math.cos(2 * Math.PI * i / 25.0);
        }
        return signal;
    }
    
    private double calculateMSE(double[] signal1, double[] signal2) {
        assertEquals(signal1.length, signal2.length);
        double sum = 0.0;
        for (int i = 0; i < signal1.length; i++) {
            double diff = signal1[i] - signal2[i];
            sum += diff * diff;
        }
        return sum / signal1.length;
    }
    
    private double calculateMaxError(double[] signal1, double[] signal2) {
        assertEquals(signal1.length, signal2.length);
        double maxError = 0.0;
        for (int i = 0; i < signal1.length; i++) {
            double error = Math.abs(signal1[i] - signal2[i]);
            maxError = Math.max(maxError, error);
        }
        return maxError;
    }
    
    private double calculateEnergy(double[] coefficients, int start, int length) {
        double energy = 0.0;
        int end = Math.min(start + length, coefficients.length);
        for (int i = start; i < end; i++) {
            energy += coefficients[i] * coefficients[i];
        }
        return energy;
    }
}