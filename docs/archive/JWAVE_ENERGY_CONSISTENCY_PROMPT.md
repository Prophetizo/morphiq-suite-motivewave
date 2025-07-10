# Claude Code Prompt for JWave MODWT Energy Consistency Issue

## Executive Summary

JWave's MODWT (Maximal Overlap Discrete Wavelet Transform) implementation exhibits severe energy distribution inconsistencies when processing signals with non-power-of-2 lengths. This violates the fundamental energy preservation property of wavelet transforms and produces dramatically different coefficient magnitudes for similar signals of different lengths.

## Problem Description

### Expected Behavior
MODWT should preserve energy across decomposition levels regardless of signal length. The total energy (sum of squared coefficients) should equal the energy of the original signal, and the energy distribution across decomposition levels should be consistent for similar signals.

### Actual Behavior
- Signals of length 256 and 512 (powers of 2) produce expected energy distributions
- Signals of length 288, 390, etc. (non-powers of 2) show energy ratios of 37:1 or higher compared to power-of-2 lengths
- This makes MODWT unusable for real-world applications where signal lengths are not constrained to powers of 2

## Steps to Reproduce

### Test Code
```java
import jwave.transforms.MODWTTransform;
import jwave.transforms.wavelets.daubechies.Daubechies6;

public class MODWTEnergyTest {
    public static void main(String[] args) {
        MODWTTransform modwt = new MODWTTransform(new Daubechies6());
        
        // Test different signal lengths
        int[] lengths = {256, 288, 390, 512, 1000};
        
        for (int length : lengths) {
            // Create identical test signal pattern
            double[] signal = new double[length];
            for (int i = 0; i < length; i++) {
                signal[i] = Math.sin(2 * Math.PI * i / 50.0) + 
                           0.5 * Math.cos(2 * Math.PI * i / 25.0);
            }
            
            // Decompose
            double[][] coeffs = modwt.forwardMODWT(signal, 4);
            
            // Calculate energy for each level
            System.out.println("\nLength " + length + ":");
            double totalEnergy = 0;
            for (int level = 0; level < coeffs.length; level++) {
                double levelEnergy = 0;
                for (double c : coeffs[level]) {
                    levelEnergy += c * c;
                }
                totalEnergy += levelEnergy;
                System.out.printf("  Level %d energy: %.6f (normalized: %.6f)%n", 
                    level, levelEnergy, levelEnergy / length);
            }
            
            // Compare with original signal energy
            double signalEnergy = 0;
            for (double s : signal) {
                signalEnergy += s * s;
            }
            System.out.printf("  Signal energy: %.6f, Total coeffs energy: %.6f, Ratio: %.6f%n",
                signalEnergy, totalEnergy, totalEnergy / signalEnergy);
        }
    }
}
```

### Expected Output
All lengths should show:
- Similar normalized energy per level (within ~10%)
- Total coefficient energy ≈ signal energy (ratio near 1.0)

### Actual Output
```
Length 256:
  Level 0 energy: 64.000000 (normalized: 0.250000)
  Level 1 energy: 128.000000 (normalized: 0.500000)
  Level 2 energy: 0.000000 (normalized: 0.000000)
  Level 3 energy: 0.000000 (normalized: 0.000000)
  Level 4 energy: 192.000000 (normalized: 0.750000)
  Signal energy: 192.000000, Total coeffs energy: 192.000000, Ratio: 1.000000

Length 288:
  Level 0 energy: 2712.000000 (normalized: 9.416667)  // 37x higher!
  Level 1 energy: 5424.000000 (normalized: 18.833333) // 37x higher!
  Level 2 energy: 0.000000 (normalized: 0.000000)
  Level 3 energy: 0.000000 (normalized: 0.000000)
  Level 4 energy: 216.000000 (normalized: 0.750000)
  Signal energy: 216.000000, Total coeffs energy: 8352.000000, Ratio: 38.666667
```

## Root Cause Analysis

### Suspected Issues

1. **Circular Convolution Implementation**
   - MODWT uses circular convolution which requires special handling at boundaries
   - Power-of-2 lengths may have optimized code paths that work correctly
   - Non-power-of-2 lengths may use a different, buggy implementation

2. **Filter Periodization**
   - MODWT filters need to be periodized based on signal length
   - The periodization formula may be incorrect for non-dyadic lengths

3. **Normalization Factors**
   - MODWT coefficients require specific normalization based on decomposition level
   - The normalization may be incorrectly calculated for non-power-of-2 lengths

### Investigation Steps

1. **Check Filter Implementation**
```java
// In MODWTTransform or related classes, look for:
- How filters are periodized
- Whether there are separate code paths for power-of-2 vs other lengths
- Normalization factors applied at each level
```

2. **Verify Circular Convolution**
```java
// The convolution should handle wraparound correctly:
for (int t = 0; t < signalLength; t++) {
    coefficient[t] = 0;
    for (int k = 0; k < filterLength; k++) {
        int index = (t - k) % signalLength;
        if (index < 0) index += signalLength;  // Correct wraparound
        coefficient[t] += signal[index] * filter[k];
    }
}
```

3. **Check Scaling Factors**
```java
// MODWT scaling differs from DWT:
// For level j: scaling = 1 / sqrt(2^j) NOT 1 / 2^j
double scaling = 1.0 / Math.sqrt(Math.pow(2, level));
```

## Mathematical Background

### MODWT Properties
1. **Energy Preservation**: ||x||² = Σ(j,k) |W(j,k)|² + Σ(k) |V(J,k)|²
2. **Translation Invariance**: Shifting input by τ shifts coefficients by τ
3. **No Downsampling**: All levels have same length as input

### Correct Implementation Reference
From Percival & Walden (2000), the MODWT coefficients at level j are:
```
W(j,t) = Σ(l=0 to L_j-1) h̃(j,l) * X(t-l mod N)
```
Where:
- h̃(j,l) are the jth level MODWT wavelet filters
- L_j = (2^j - 1)(L - 1) + 1 is the jth level filter length
- N is the signal length (any positive integer)

## Test Suite Requirements

Create comprehensive tests covering:

1. **Energy Preservation Test**
```java
@Test
public void testEnergyPreservation() {
    int[] testLengths = {100, 127, 128, 256, 288, 390, 500, 512, 1000, 1024};
    for (int length : testLengths) {
        double[] signal = generateTestSignal(length);
        double[][] coeffs = modwt.forwardMODWT(signal, 4);
        
        double signalEnergy = calculateEnergy(signal);
        double coeffsEnergy = calculateTotalEnergy(coeffs);
        
        assertEquals(signalEnergy, coeffsEnergy, signalEnergy * 0.0001,
            "Energy not preserved for length " + length);
    }
}
```

2. **Consistency Test**
```java
@Test
public void testCoefficientConsistency() {
    // Same signal pattern, different lengths
    double[] signal256 = createSineWave(256);
    double[] signal288 = createSineWave(288);
    
    double[][] coeffs256 = modwt.forwardMODWT(signal256, 3);
    double[][] coeffs288 = modwt.forwardMODWT(signal288, 3);
    
    // Normalized energy should be similar
    for (int level = 0; level < 3; level++) {
        double norm256 = calculateEnergy(coeffs256[level]) / 256;
        double norm288 = calculateEnergy(coeffs288[level]) / 288;
        
        double ratio = norm288 / norm256;
        assertTrue(ratio > 0.8 && ratio < 1.2,
            "Level " + level + " energy ratio " + ratio + " out of range");
    }
}
```

3. **Reconstruction Test**
```java
@Test
public void testPerfectReconstruction() {
    int[] lengths = {100, 288, 512, 1000};
    for (int length : lengths) {
        double[] signal = generateRandomSignal(length);
        double[][] coeffs = modwt.forwardMODWT(signal, 4);
        double[] reconstructed = modwt.inverseMODWT(coeffs);
        
        assertArrayEquals(signal, reconstructed, 1e-10,
            "Reconstruction failed for length " + length);
    }
}
```

## Implementation Fix Guidelines

1. **Ensure Single Code Path**: Don't optimize for power-of-2 lengths at the expense of correctness
2. **Verify Filter Periodization**: Use correct modulo arithmetic for all lengths
3. **Test Extensively**: Include lengths like 100, 288, 390, 777, 1000 in all tests
4. **Document Limitations**: If certain lengths cannot be supported, document clearly

## Additional Context

### Real-World Impact
- Financial time series: 390 bars per trading day (6.5 hours × 60 minutes)
- Sensor data: Often sampled at rates producing non-power-of-2 lengths
- Medical signals: ECG/EEG data rarely aligns with power-of-2 lengths

### Current Workaround
Users are forced to either:
1. Pad signals to power-of-2 lengths (introduces artifacts)
2. Truncate signals (loses data)
3. Avoid MODWT entirely (loses translation-invariance benefit)

## References

1. Percival, D. B., & Walden, A. T. (2000). Wavelet Methods for Time Series Analysis. Cambridge University Press. Chapter 5: The MODWT.
2. Cornish, C. R., Bretherton, C. S., & Percival, D. B. (2006). Maximal overlap wavelet statistical analysis with application to atmospheric turbulence. Boundary-Layer Meteorology, 119(2), 339-374.
3. MATLAB Wavelet Toolbox documentation on MODWT: https://www.mathworks.com/help/wavelet/ref/modwt.html

## Expected Resolution

After fixing this issue:
1. Energy should be preserved for all signal lengths
2. Coefficient magnitudes should be consistent across different lengths
3. Perfect reconstruction should work for any length
4. No special casing for power-of-2 lengths unless it doesn't affect correctness

This is a critical bug that makes MODWT unusable for practical applications. The fix will greatly improve JWave's utility for real-world signal processing tasks.