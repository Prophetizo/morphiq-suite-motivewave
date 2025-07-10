# Claude Code Prompt for JWave Inverse MODWT Implementation

## Background Summary

JWave is a Java library for wavelet transforms that includes support for MODWT (Maximal Overlap Discrete Wavelet Transform). However, the current implementation in JWave 1.0.7-SNAPSHOT only supports the forward MODWT transform. The inverse MODWT (iMODWT) is not implemented, which prevents users from reconstructing signals after coefficient manipulation - a critical feature for applications like denoising, compression, and signal analysis.

When calling `MODWTTransform.reverse()` or `MODWTTransform.inverse()`, the method returns an empty array with a comment stating "Reverse MODWT (iMODWT) is not implemented."

This missing functionality forces users to either:
1. Switch to standard DWT (losing MODWT's translation-invariance property)
2. Implement their own inverse MODWT (often incorrectly)
3. Use a different library

## Steps to Reproduce the Issue

1. Create a simple test using JWave's MODWT:

```java
import jwave.transforms.MODWTTransform;
import jwave.transforms.wavelets.daubechies.Daubechies4;

public class MODWTTest {
    public static void main(String[] args) {
        // Create MODWT transform
        MODWTTransform modwt = new MODWTTransform(new Daubechies4());
        
        // Create test signal
        double[] signal = new double[128];
        for (int i = 0; i < signal.length; i++) {
            signal[i] = Math.sin(2 * Math.PI * i / 32.0);
        }
        
        // Forward transform works fine
        double[][] coefficients = modwt.forwardMODWT(signal, 3);
        System.out.println("Forward MODWT levels: " + coefficients.length);
        
        // Attempt inverse transform - returns empty array!
        double[] reconstructed = modwt.reverse(flattenCoefficients(coefficients));
        System.out.println("Reconstructed length: " + reconstructed.length); // Prints: 0
    }
    
    private static double[] flattenCoefficients(double[][] coeffs) {
        // Flatten 2D coefficient array for reverse() method
        int totalLength = 0;
        for (double[] level : coeffs) {
            totalLength += level.length;
        }
        double[] flat = new double[totalLength];
        int offset = 0;
        for (double[] level : coeffs) {
            System.arraycopy(level, 0, flat, offset, level.length);
            offset += level.length;
        }
        return flat;
    }
}
```

2. The output will show:
```
Forward MODWT levels: 4
Reconstructed length: 0
```

## Technical Requirements for Inverse MODWT

The inverse MODWT algorithm should implement the following mathematical operation:

For a J-level MODWT decomposition, the reconstruction formula is:

```
x[t] = Σ(j=1 to J) Σ(k) d[j,k] * ψ̃[j,k,t] + Σ(k) a[J,k] * φ̃[J,k,t]
```

Where:
- `d[j,k]` are the detail coefficients at level j
- `a[J,k]` are the approximation coefficients at level J
- `ψ̃[j,k,t]` are the reconstruction wavelets
- `φ̃[J,k,t]` are the reconstruction scaling functions

Key implementation details:
1. Use the reconstruction filters (not decomposition filters)
2. Handle circular convolution properly
3. Apply correct normalization factors for each level
4. Maintain translation-invariance property
5. Support both the `reverse(double[])` method and a new `inverseMODWT(double[][])` method

## Proposed Implementation Structure

```java
public class MODWTTransform extends BasicTransform {
    
    // Existing forward transform
    public double[][] forwardMODWT(double[] signal, int level) {
        // Current implementation
    }
    
    // New inverse transform - primary method
    public double[] inverseMODWT(double[][] coefficients) {
        // TODO: Implement proper inverse MODWT
        // 1. Validate input
        // 2. Initialize reconstruction with approximation coefficients
        // 3. Recursively add detail levels using synthesis filters
        // 4. Handle boundary conditions with circular convolution
        // 5. Apply proper scaling
    }
    
    // Override reverse() to use inverseMODWT
    @Override
    public double[] reverse(double[] coeffs) {
        // TODO: Convert flat array to 2D structure and call inverseMODWT
    }
}
```

## Test Cases to Implement

1. **Perfect Reconstruction Test**: Forward MODWT followed by inverse should recover the original signal (within numerical precision)

2. **Energy Conservation Test**: The energy (sum of squares) should be preserved

3. **Non-Power-of-2 Length Test**: MODWT should work correctly for signals of any length (e.g., 100, 288, 1000)

4. **Coefficient Modification Test**: Zeroing detail coefficients should produce a smoothed reconstruction

5. **Comparison with MATLAB/R**: Results should match established implementations in MATLAB's Wavelet Toolbox or R's wavelets package

## Example Test Code

```java
@Test
public void testPerfectReconstruction() {
    MODWTTransform modwt = new MODWTTransform(new Daubechies4());
    double[] signal = generateTestSignal(256);
    
    // Forward transform
    double[][] coeffs = modwt.forwardMODWT(signal, 4);
    
    // Inverse transform
    double[] reconstructed = modwt.inverseMODWT(coeffs);
    
    // Check reconstruction accuracy
    double mse = calculateMSE(signal, reconstructed);
    assertTrue(mse < 1e-10, "MSE should be negligible but was: " + mse);
}

@Test
public void testNonPowerOfTwoLength() {
    MODWTTransform modwt = new MODWTTransform(new Daubechies6());
    
    int[] testLengths = {100, 288, 500, 1000};
    for (int length : testLengths) {
        double[] signal = generateTestSignal(length);
        double[][] coeffs = modwt.forwardMODWT(signal, 3);
        double[] reconstructed = modwt.inverseMODWT(coeffs);
        
        assertEquals(length, reconstructed.length);
        double mse = calculateMSE(signal, reconstructed);
        assertTrue(mse < 1e-10, "Failed for length " + length);
    }
}
```

## Implementation Resources

1. **Algorithm Reference**: 
   - Percival, D. B., & Walden, A. T. (2000). Wavelet Methods for Time Series Analysis. Cambridge University Press. (Chapter 5)
   - http://faculty.washington.edu/dbp/PDFFILES/4-Lec-MODWT.pdf

2. **Existing Implementations to Reference**:
   - MATLAB: `imodwt()` function in Wavelet Toolbox
   - R: `imodwt()` in wavelets package
   - Python: PyWavelets `imodwt()` (for algorithm verification)

3. **Key Formulas**:
   - Synthesis filters: g̃[l] = g[L-1-l] (time-reversed analysis filters)
   - Scaling: Each level j has scaling factor 2^(j/2)
   - Circular convolution: Use modulo arithmetic for indices

## Expected Outcome

After implementing inverse MODWT:
1. Users can perform full wavelet analysis workflows (decompose → modify → reconstruct)
2. Denoising applications will work correctly
3. Perfect reconstruction property ensures no information loss
4. Support for arbitrary signal lengths improves practical usability

## Additional Context

This implementation is critical for financial time series analysis where:
- Signal lengths are often not powers of 2 (e.g., 390 minutes in a trading day)
- Translation-invariance is crucial for consistent results
- Coefficient thresholding is used for denoising market data
- Multi-resolution analysis helps identify trends at different time scales

The implementation should maintain backward compatibility and follow JWave's existing code style and patterns.