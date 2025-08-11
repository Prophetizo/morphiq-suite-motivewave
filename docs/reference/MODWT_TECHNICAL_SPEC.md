# MODWT Technical Specification for VectorWave

## Overview

The Maximal Overlap Discrete Wavelet Transform (MODWT) is critical for the Morphiq trading indicators. Unlike the standard DWT, MODWT provides:

1. **Translation invariance** - shifting the input signal doesn't drastically change coefficients
2. **Works with any length data** - no power-of-two restriction
3. **Redundant representation** - better for denoising and feature extraction
4. **Preserves time alignment** - crucial for financial time series analysis

## Mathematical Foundation

### Forward MODWT

For a signal x of length N and decomposition level j:

```
W[j,t] = Σ(l=0 to L-1) h~[j,l] * x[(t-2^(j-1)*l) mod N]
V[j,t] = Σ(l=0 to L-1) g~[j,l] * x[(t-2^(j-1)*l) mod N]
```

Where:
- W[j,t] = wavelet coefficients at level j, time t
- V[j,t] = scaling coefficients at level j, time t  
- h~[j,l] = scaled wavelet filter: h[l] / sqrt(2^j)
- g~[j,l] = scaled scaling filter: g[l] / sqrt(2^j)
- L = filter length

### Inverse MODWT

The reconstruction formula:

```
x[t] = Σ(j=1 to J) W~[j,t] + V~[J,t]
```

Where W~ and V~ are obtained by upsampling and filtering.

## Implementation Requirements

### Data Structure

```java
public class MODWTResult {
    private double[][] waveletCoefficients;  // [level][time]
    private double[] scalingCoefficients;    // Final approximation
    private int levels;
    private int signalLength;
}
```

### Algorithm Steps

#### Forward Transform

1. **Initialize**
   ```java
   double[] currentLevel = inputSignal.clone();
   double[][] W = new double[maxLevel][signalLength];
   double[][] V = new double[maxLevel][signalLength];
   ```

2. **For each level j = 1 to maxLevel**
   ```java
   // Apply circular convolution with modulated filters
   for (int t = 0; t < signalLength; t++) {
       W[j-1][t] = circularConvolve(currentLevel, waveletFilter, t, j);
       V[j-1][t] = circularConvolve(currentLevel, scalingFilter, t, j);
   }
   currentLevel = V[j-1];  // Use scaling coeffs for next level
   ```

3. **Circular Convolution with Modulation**
   ```java
   double circularConvolve(double[] signal, double[] filter, int t, int level) {
       double sum = 0.0;
       int modulation = (int) Math.pow(2, level - 1);
       double scale = Math.sqrt(Math.pow(2, level));
       
       for (int l = 0; l < filter.length; l++) {
           int index = (t - modulation * l) % signal.length;
           if (index < 0) index += signal.length;  // Handle negative modulo
           sum += (filter[l] / scale) * signal[index];
       }
       return sum;
   }
   ```

#### Inverse Transform

1. **Start with deepest level approximation**
   ```java
   double[] reconstruction = V[maxLevel-1].clone();
   ```

2. **For each level j = maxLevel down to 1**
   ```java
   // Upsample and filter both W and V coefficients
   double[] details = inverseMODWTLevel(W[j-1], waveletFilter, j);
   double[] smooth = inverseMODWTLevel(reconstruction, scalingFilter, j);
   
   // Combine for reconstruction
   for (int t = 0; t < signalLength; t++) {
       reconstruction[t] = details[t] + smooth[t];
   }
   ```

## Optimizations for Trading

### 1. Sliding Window Implementation

For real-time trading, implement a sliding window approach:

```java
public class SlidingMODWT {
    private CircularBuffer buffer;
    private double[][] coefficientCache;
    
    public void addNewPrice(double price) {
        buffer.add(price);
        updateCoefficients();  // Only recalculate affected coefficients
    }
}
```

### 2. Parallel Processing

For large datasets (≥ 512 points), parallelize across time points:

```java
IntStream.range(0, signalLength)
    .parallel()
    .forEach(t -> {
        W[level][t] = circularConvolve(signal, waveletFilter, t, level);
    });
```

### 3. Memory Efficiency

For denoising operations where only certain levels are needed:

```java
public class SelectiveMODWT {
    private BitSet levelsToCompute;
    
    public double[][] forward(double[] signal, int maxLevel) {
        // Only compute and store required levels
    }
}
```

## Specific Requirements for Morphiq

### AutoWavelets Study

- Needs levels 1 through 8 (configurable)
- Returns only the detail coefficients at each level
- Must handle variable-length lookback periods (100-5000 bars)

### DenoisedTrendFollowing Study  

- Needs full reconstruction capability
- Supports zeroing specific detail levels
- Requires approximation extraction
- Must implement both hard and soft thresholding

## Validation Tests

### 1. Perfect Reconstruction
```java
@Test
public void testPerfectReconstruction() {
    double[] signal = generateTestSignal(1000);
    MODWTResult forward = modwt.forward(signal, 5);
    double[] reconstructed = modwt.inverse(forward);
    
    assertArrayEquals(signal, reconstructed, 1e-10);
}
```

### 2. Shift Invariance
```java
@Test  
public void testShiftInvariance() {
    double[] signal = generateTestSignal(1000);
    double[] shifted = shiftSignal(signal, 5);
    
    MODWTResult result1 = modwt.forward(signal, 4);
    MODWTResult result2 = modwt.forward(shifted, 4);
    
    // Coefficients should be shifted, not completely different
    assertShiftedEquals(result1.getWaveletCoefficients(), 
                       result2.getWaveletCoefficients(), 5);
}
```

### 3. Energy Preservation
```java
@Test
public void testEnergyPreservation() {
    double[] signal = generateTestSignal(1000);
    double signalEnergy = calculateEnergy(signal);
    
    MODWTResult result = modwt.forward(signal, 5);
    double coeffEnergy = calculateCoefficientEnergy(result);
    
    assertEquals(signalEnergy, coeffEnergy, signalEnergy * 0.0001);
}
```

## Performance Benchmarks

Target performance for production:

| Operation | Data Points | Target Time | Parallel Time |
|-----------|------------|-------------|---------------|
| Forward MODWT (5 levels) | 1,000 | < 5ms | < 2ms |
| Forward MODWT (5 levels) | 10,000 | < 50ms | < 15ms |
| Inverse MODWT (5 levels) | 1,000 | < 5ms | < 2ms |
| Denoising (full pipeline) | 1,000 | < 10ms | < 5ms |

## Error Handling

```java
public class MODWTException extends Exception {
    public enum ErrorType {
        INVALID_LEVEL,
        SIGNAL_TOO_SHORT,
        NULL_FILTER,
        DIMENSION_MISMATCH
    }
}
```

## References

1. Percival, D. B., & Walden, A. T. (2000). Wavelet methods for time series analysis.
2. Gençay, R., Selçuk, F., & Whitcher, B. (2001). An introduction to wavelets and other filtering methods in finance and economics.
3. Implementation must match behavior of JWave's MODWT for compatibility.