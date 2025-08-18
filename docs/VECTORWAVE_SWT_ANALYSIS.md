# VectorWave SWT Usage Analysis in SwtTrendMomentumSimple

## Overview
This document analyzes how the SwtTrendMomentumSimple study uses VectorWave's Stationary Wavelet Transform (SWT) implementation and examines whether the observed behavior indicates a potential bug in VectorWave.

## How SwtTrendMomentumSimple Uses VectorWave SWT

### 1. Initialization Flow
```java
// In initializeAdapter() method (line 352-356)
private void initializeAdapter() {
    String waveletType = getSettings().getString(WAVELET_TYPE, "Daubechies4");
    String vectorWaveType = mapWaveletType(waveletType);  // Maps to "db4", "db6", or "haar"
    swtAdapter = new VectorWaveSwtAdapter(vectorWaveType);
}
```

The adapter is initialized with:
- Wavelet type: "haar", "db4", or "db6"
- Default boundary mode: PERIODIC (set in VectorWaveSwtAdapter constructor)

### 2. Data Preparation
```java
// In updateSlidingWindow() method (lines 369-396)
private double[] updateSlidingWindow(DataSeries series, int index, int windowLength, Object input) {
    double[] window = new double[windowLength];
    int startIndex = Math.max(0, index - windowLength + 1);
    
    // Fill the window with the last 'windowLength' bars
    for (int i = 0; i < windowLength; i++) {
        int dataIndex = startIndex + i;
        if (dataIndex <= index && dataIndex < series.size()) {
            Double value = series.getDouble(dataIndex, input);
            if (value != null) {
                window[i] = value;
            } else {
                window[i] = series.getClose(dataIndex);
            }
        }
    }
    return window;
}
```

**Key Points:**
- Creates a sliding window of size `windowLength` (128, 256, 512, or 1024 bars)
- Window contains the most recent `windowLength` price values
- Input is typically CLOSE price but configurable

### 3. SWT Transform Execution
```java
// In calculate() method (line 284)
VectorWaveSwtAdapter.SwtResult swtResult = swtAdapter.transform(prices, levels);
```

This calls VectorWave's transform which:
1. Performs forward MODWT (Maximum Overlap Discrete Wavelet Transform)
2. Decomposes signal into `levels` (default 4) decomposition levels
3. Returns approximation coefficients and detail coefficients for each level

### 4. Result Processing

#### For Trend (Approximation):
```java
// Lines 303-327
if (useDenoised) {
    // Reconstruct denoised signal
    double[] reconstructed = swtResult.reconstruct(levels);
    currentTrend = reconstructed[reconstructed.length - 1];
} else {
    double[] approx = swtResult.getApproximation();
    currentTrend = approx[approx.length - 1];
}
series.setDouble(index, Values.AJ, currentTrend);
```

#### For Momentum:
```java
// In calculateMomentum() method (lines 398-422)
private double calculateMomentum(VectorWaveSwtAdapter.SwtResult swtResult) {
    double momentum = 0.0;
    int levelsToUse = Math.min(2, swtResult.getLevels());
    for (int level = 1; level <= levelsToUse; level++) {
        double[] detail = swtResult.getDetail(level);
        if (detail != null && detail.length > 0) {
            momentum += detail[detail.length - 1];
        }
    }
    // Apply smoothing...
    return smoothedMomentum;
}
```

## Expected Behavior vs Actual Behavior

### Expected Behavior

1. **Window Length Impact**: 
   - Smaller windows (128 bars) should produce more responsive, noisier approximations
   - Larger windows (1024 bars) should produce smoother, less responsive approximations
   - The visual difference should be significant and immediately noticeable

2. **SWT Properties**:
   - Output length should equal input length (this is correct)
   - Each decomposition level should capture different frequency components
   - Approximation at level J should represent the smoothest (lowest frequency) component

3. **Mathematical Expectation**:
   - For a window of 128 samples with 4 levels: A4 represents smoothing over ~16 samples (128/2^4)
   - For a window of 1024 samples with 4 levels: A4 represents smoothing over ~64 samples (1024/2^4)
   - These should produce visually different results

### Actual Behavior

1. **No Visual Difference**: Changing window length from 128 to 1024 produces no visible change in the AJ (trend) plot
2. **Logs Show Correct Processing**: Debug logs confirm the correct window size is being used
3. **Values Are Similar**: The approximation coefficients' last value is nearly identical regardless of window size

## Potential Issues

### Issue 1: Only Using Last Coefficient
The study only uses the last value from the coefficient arrays:
```java
currentTrend = approx[approx.length - 1];  // Only last value used
```

**Problem**: The SWT produces N coefficients for N input samples. We're discarding N-1 values and only using the last one.

**Why This Might Not Matter**: In SWT, each coefficient represents the transform at that time point. The last coefficient should represent the most recent smoothed value.

### Issue 2: Fixed Decomposition Levels
The study uses 4 decomposition levels regardless of window size:
```java
private static final int DEFAULT_LEVELS = 4;
```

**Problem**: The effective smoothing at level 4 depends on the input size:
- 128 samples → 2^4 = 16 sample effective window
- 1024 samples → 2^4 = 16 sample effective window (in wavelet scale)

**Key Insight**: The SWT decomposition level represents frequency bands, not absolute sample counts. Level 4 always represents the same frequency band regardless of input length.

### Issue 3: Potential VectorWave Bug

**Hypothesis**: VectorWave's MODWT implementation might be:

1. **Not properly utilizing the full input window**: The transform might be internally limiting the effective window size or only processing a subset of the input.

2. **Boundary handling issue**: With PERIODIC boundary mode, the transform might be wrapping in a way that makes different window sizes produce similar results.

3. **Coefficient calculation issue**: The approximation coefficients at level J might not be properly incorporating all input samples.

## Test to Verify VectorWave Behavior

To determine if VectorWave has a bug, we should test:

1. **Direct coefficient inspection**: Log all approximation coefficients, not just the last one
2. **Synthetic test signal**: Use a known signal (sine wave + noise) with different window sizes
3. **Compare with reference implementation**: Compare VectorWave results with a known-good wavelet library

## Recommended Next Steps

1. **Add comprehensive logging**:
```java
// Add this after line 317
if (index % 100 == 0) {
    double[] approx = swtResult.getApproximation();
    logger.info("Window={}, Approx length={}, First={:.2f}, Last={:.2f}, Mean={:.2f}, StdDev={:.2f}",
                windowLength, approx.length, approx[0], approx[approx.length-1],
                Arrays.stream(approx).average().orElse(0),
                Math.sqrt(Arrays.stream(approx).map(x -> x*x).average().orElse(0)));
}
```

2. **Test with synthetic data**:
   - Create a test that feeds known signals with different window sizes
   - Verify the approximation changes as expected

3. **Try alternative coefficient usage**:
   - Instead of using just the last coefficient, try using a weighted average of recent coefficients
   - This might reveal if the coefficients are actually different but we're not using them correctly

## Conclusion

The issue appears to be that **the SWT approximation at a fixed decomposition level produces similar values regardless of input window size**. This could be:

1. **Expected behavior**: The approximation at level J represents a specific frequency band, and the last coefficient represents the current smoothed value at that frequency. The window size might not significantly affect this value.

2. **Implementation issue**: VectorWave might not be properly utilizing the full input window in its MODWT calculation.

3. **Usage issue**: We might need to adjust the decomposition levels based on window size to see meaningful differences.

The most likely explanation is that **using a fixed decomposition level (4) with different window sizes doesn't produce the expected visual difference** because the wavelet transform's frequency decomposition is relative to the signal, not absolute in terms of samples.

## Recommended Fix

Instead of using fixed levels, scale the decomposition levels with window size:
```java
// Adaptive levels based on window size
int levels = (int) Math.min(6, Math.max(2, Math.log(windowLength) / Math.log(2) - 3));
// 128 bars → 3 levels
// 256 bars → 4 levels  
// 512 bars → 5 levels
// 1024 bars → 6 levels
```

This would ensure that larger windows use more decomposition levels, producing more smoothing and a visible difference in the trend line.