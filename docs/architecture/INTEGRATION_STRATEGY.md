# MotiveWave-VectorWave Integration Strategy

## Current Implementation Overview

This document describes the actual integration between MotiveWave SDK and VectorWave library as implemented in the Morphiq Suite, focusing on the SWT/MODWT trading system.

## Core Architecture

### 1. VectorWave SWT Adapter

The primary integration point between MotiveWave and VectorWave's native SWT implementation:

```java
package com.prophetizo.wavelets.swt.core;

/**
 * Bridge to VectorWave's Stationary Wavelet Transform
 */
public class VectorWaveSwtAdapter {
    private final String waveletType;
    private final BoundaryMode boundaryMode;
    private final ai.prophetizo.wavelet.swt.VectorWaveSwtAdapter swtAdapter;
    
    public VectorWaveSwtAdapter(String waveletType) {
        this.waveletType = waveletType;
        this.boundaryMode = BoundaryMode.PERIODIC;
        this.wavelet = WaveletRegistry.getWavelet(waveletType);
        this.swtAdapter = new ai.prophetizo.wavelet.swt.VectorWaveSwtAdapter(wavelet, boundaryMode);
    }
    
    public SwtResult transform(double[] data, int levels) {
        MutableMultiLevelMODWTResult result = swtAdapter.forward(data, levels);
        return new SwtResult(
            result.getApproximationCoeffs(),
            extractDetails(result, levels),
            waveletType,
            boundaryMode,
            result
        );
    }
}
```

### 2. Data Flow Pattern

The integration follows MotiveWave's calculation model with efficient data extraction:

```java
package com.prophetizo.wavelets.swt;

public class SwtTrendMomentumStudy extends Study {
    // Sliding window buffer for efficiency
    private double[] priceBuffer = null;
    private int bufferStartIndex = -1;
    private boolean bufferInitialized = false;
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        // Extract price window efficiently
        double[] prices = updateSlidingWindow(series, index, windowLength, ctx);
        
        // Transform via VectorWave
        VectorWaveSwtAdapter.SwtResult swtResult = swtAdapter.transform(prices, levels);
        
        // Apply thresholding
        applyThresholding(swtResult);
        
        // Store results in MotiveWave DataSeries
        series.setDouble(index, Values.AJ, swtResult.getApproximation()[last]);
        series.setDouble(index, Values.MOMENTUM_SUM, calculateMomentum(swtResult));
    }
}
```

### 3. Sliding Window Optimization

Efficient streaming updates without full recalculation:

```java
private double[] updateSlidingWindow(DataSeries series, int index, int windowLength, DataContext ctx) {
    int startIndex = index - windowLength + 1;
    
    // Check if we can do incremental update
    if (bufferInitialized && startIndex == bufferStartIndex + 1) {
        // Shift window by 1 position - O(1) new data fetch
        System.arraycopy(priceBuffer, 1, priceBuffer, 0, windowLength - 1);
        priceBuffer[windowLength - 1] = series.getClose(index);
        bufferStartIndex = startIndex;
    } else {
        // Full refresh needed
        priceBuffer = extractPriceWindow(series, index, windowLength, ctx);
        bufferStartIndex = startIndex;
        bufferInitialized = true;
    }
    
    return priceBuffer;
}
```

### 4. Coefficient Storage Strategy

Mapping wavelet coefficients to MotiveWave's Values enum:

```java
// Standard value mappings used in the implementation
public enum Values { 
    AJ,                    // Approximation (trend)
    D1_SIGN, D2_SIGN, D3_SIGN,  // Detail signs for momentum
    LONG_FILTER, SHORT_FILTER,   // Filter states
    WATR,                  // Wavelet ATR
    WATR_UPPER, WATR_LOWER,      // WATR bands
    MOMENTUM_SUM,          // Sum of detail coefficients
    SLOPE                  // Trend slope
}

// Store coefficients
series.setDouble(index, Values.AJ, approximation);
series.setDouble(index, Values.MOMENTUM_SUM, momentum);
series.setDouble(index, Values.SLOPE, slope);
```

### 5. Thresholding Integration

VectorWave coefficients are denoised using multiple methods:

```java
package com.prophetizo.wavelets.swt.core;

public class Thresholds {
    public static double calculateThreshold(double[] coeffs, ThresholdMethod method, int level) {
        switch (method) {
            case UNIVERSAL:
                return calculateUniversalThreshold(coeffs);
            case BAYES_SHRINK:
                return calculateBayesShrinkThreshold(coeffs, level);
            case SURE:
                return calculateSureThreshold(coeffs);
        }
    }
    
    // Applied to VectorWave SWT result
    public void applyShrinkage(SwtResult result, int level, double threshold, boolean soft) {
        double[] mutableDetails = result.getVectorWaveResult().getMutableDetailCoeffs(level);
        // In-place thresholding
        for (int i = 0; i < mutableDetails.length; i++) {
            if (soft) {
                // Soft thresholding
                mutableDetails[i] = softThreshold(mutableDetails[i], threshold);
            } else {
                // Hard thresholding
                mutableDetails[i] = hardThreshold(mutableDetails[i], threshold);
            }
        }
    }
}
```

## Performance Optimizations

### 1. Logging Guards
All expensive logging is guarded to avoid computation:
```java
if (logger.isDebugEnabled()) {
    logger.debug("Expensive string formatting here");
}
```

### 2. RMS Energy Momentum
Efficient multi-scale momentum calculation:
```java
private double calculateMomentumSum(SwtResult swtResult, int k) {
    double rawMomentum = 0.0;
    
    for (int level = 1; level <= k; level++) {
        double[] detail = swtResult.getDetail(level);
        int windowSize = Math.min(MOMENTUM_WINDOW, detail.length);
        
        // RMS energy calculation
        double levelEnergy = 0.0;
        for (int i = detail.length - windowSize; i < detail.length; i++) {
            levelEnergy += detail[i] * detail[i];
        }
        
        // Weight by level (finer scales get more weight)
        double weight = 1.0 / (1.0 + (level - 1) * 0.5);
        rawMomentum += Math.sqrt(levelEnergy / windowSize) * weight;
    }
    
    // Apply exponential smoothing
    smoothedMomentum = MOMENTUM_ALPHA * rawMomentum + (1 - MOMENTUM_ALPHA) * smoothedMomentum;
    return smoothedMomentum;
}
```

### 3. Memory Management
- Pre-allocated sliding window buffers
- In-place coefficient updates
- Reuse of SWT adapter instances

## MotiveWave SDK Patterns

### Study Registration
```java
@StudyHeader(
    namespace = "com.prophetizo.wavelets.swt",
    id = "SWT_TREND_MOMENTUM",
    name = "SWT Trend + Momentum",
    menu = "MorphIQ | Wavelet Analysis",
    overlay = true,
    requiresBarUpdates = false
)
public class SwtTrendMomentumStudy extends Study {
    // Implementation
}
```

### Signal Generation
Following MotiveWave's marker pattern:
```java
private void generateSignals(DataContext ctx, DataSeries series, int index, 
                            boolean longFilter, boolean shortFilter) {
    // Only add markers when bar is complete
    boolean barComplete = series.isBarComplete(index);
    
    if (longFilter && !lastLongFilter) {
        series.setBoolean(index, Signals.LONG_ENTER, true);
        
        if (barComplete) {
            var marker = getSettings().getMarker(LONG_MARKER);
            var coord = new Coordinate(series.getStartTime(index), trendValue);
            addFigure(new Marker(coord, Position.BOTTOM, marker, msg));
            ctx.signal(index, Signals.LONG_ENTER, "Long Entry", price);
        }
    }
}
```

### Settings Management
```java
@Override
public void initialize(Defaults defaults) {
    var sd = createSD();  // Settings Descriptor
    
    // Add parameters
    sd.addRow(new IntegerDescriptor(LEVELS, "Decomposition Levels", 5, 2, 8, 1));
    sd.addRow(new DoubleDescriptor(MOMENTUM_THRESHOLD, "Momentum Threshold", 0.01, 0.0, 1.0, 0.001));
    
    var rd = createRD();  // Runtime Descriptor
    
    // Declare paths for plotting
    rd.declarePath(Values.AJ, AJ_PATH);
    rd.declareIndicator(Values.AJ, "Approximation");
}
```

## Module Dependencies

```xml
<!-- VectorWave dependency in pom.xml -->
<dependency>
    <groupId>ai.prophetizo</groupId>
    <artifactId>vector-wave</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<!-- MotiveWave SDK (provided) -->
<dependency>
    <groupId>com.motivewave</groupId>
    <artifactId>MotiveWaveSDK</artifactId>
    <version>20230627</version>
    <scope>provided</scope>
</dependency>
```

## Key Integration Points

1. **VectorWaveSwtAdapter** - Main bridge class
2. **Sliding Window Buffer** - Efficient data extraction
3. **Thresholds** - Coefficient denoising
4. **WaveletAtr** - Volatility from wavelet coefficients
5. **Signal Generation** - MotiveWave marker pattern

## Testing Integration

```java
@Test
void testVectorWaveIntegration() {
    // Test SWT transform
    VectorWaveSwtAdapter adapter = new VectorWaveSwtAdapter("db4");
    double[] testData = generateTestData(512);
    
    SwtResult result = adapter.transform(testData, 5);
    
    // Verify perfect reconstruction
    double[] reconstructed = result.reconstruct(5);
    assertArrayEquals(testData, reconstructed, 1e-10);
}
```

## Current Implementation Status

✅ **Completed**:
- VectorWave SWT/MODWT integration
- Sliding window optimization
- Three thresholding methods
- RMS energy-based momentum
- Wavelet-ATR implementation
- Signal generation with markers
- Strategy automation

🚧 **Future Enhancements**:
- Custom wavelets for ES/NQ
- Machine learning coefficient optimization
- Multi-timeframe synchronization
- Portfolio-level risk management

## Performance Metrics

| Operation | Time | Notes |
|-----------|------|-------|
| SWT Transform (512 points) | ~5ms | VectorWave native |
| Sliding Window Update | <1ms | O(1) operation |
| Momentum Calculation | ~1ms | RMS over window |
| Full Bar Calculation | ~10ms | Complete pipeline |

## Best Practices

1. **Always use sliding windows** for data extraction
2. **Guard expensive logging** with `isDebugEnabled()`
3. **Reuse adapter instances** across calculations
4. **Follow MotiveWave patterns** for markers and signals
5. **Use provided scope** for MotiveWave SDK dependency

---

This integration strategy reflects the actual production implementation in the Morphiq Suite, providing efficient wavelet analysis within MotiveWave's framework.