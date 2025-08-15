# Wavelet-Based Studies for MotiveWave Platform

## Current Implementation Status

This document describes the wavelet-based studies currently implemented in the Morphiq Suite and their design principles.

---

## Implemented Studies

### 1. **SWT Trend + Momentum Study**

#### Status: ✅ COMPLETE

#### Purpose
Complete trading system using Stationary Wavelet Transform (SWT/MODWT) with cross-scale momentum confirmation.

#### Implementation Details
- **Class**: `com.morphiqlabs.wavelets.swt.SwtTrendMomentumStudy`
- **Type**: Overlay study with momentum subplot
- **Menu**: MorphIQ | Wavelet Analysis

#### Wavelets Supported
- **Daubechies** (db2-db20): General purpose, good smoothing
- **Symlets** (sym2-sym20): Near-symmetric, balanced
- **Coiflets** (coif1-coif5): Compact support, good for scalping
- **Haar**: Simple, immediate response to breakouts

#### Visual Output
```
Main Panel:
- Blue line: Wavelet approximation (A_J) - smoothed trend
- Green triangles: Long entry markers
- Red triangles: Short entry markers  
- Gray squares: Exit markers
- Optional: WATR bands (volatility envelope)

Bottom Panel:
- Green line: Cross-scale momentum (RMS energy)
- Orange line: Trend slope
- Zero line reference
```

#### Key Features
- **Undecimated Transform**: Shift-invariant for consistent signals
- **Multi-Scale Analysis**: Decomposes price into J levels
- **Adaptive Thresholding**: Universal, BayesShrink, SURE methods
- **RMS Energy Momentum**: Weighted multi-scale momentum
- **Sliding Window Buffer**: Efficient O(1) updates
- **Signal Generation**: Balanced entry/exit logic

#### Configuration Parameters
- Wavelet type and decomposition levels
- Window length (256-4096 bars)
- Threshold method and shrinkage type
- Momentum calculation (SUM/SIGN)
- WATR bands display

---

### 2. **SWT Trend + Momentum Strategy**

#### Status: ✅ COMPLETE

#### Purpose
Automated trading strategy extending the study with position management and risk control.

#### Implementation Details
- **Class**: `com.morphiqlabs.wavelets.swt.SwtTrendMomentumStrategy`
- **Type**: Trading strategy (extends Study)
- **Inherits**: All features from SwtTrendMomentumStudy

#### Additional Features
- **Position Sizing**: Risk-based or fixed size
- **Stop Management**: WATR-based or fixed percentage
- **Bracket Orders**: Automated stop and target placement
- **Risk Controls**: Maximum risk per trade limits

#### Strategy Parameters
- Position size (shares/contracts)
- Max risk per trade ($)
- Stop/target multipliers
- WATR-based stops toggle

---

### 3. **AutoWavelets Study**

#### Status: ✅ COMPLETE

#### Purpose
Multi-level wavelet decomposition visualization for market structure analysis.

#### Implementation Details
- **Class**: `com.morphiqlabs.wavelets.Wavelets`
- **Type**: Multi-panel decomposition display
- **Location**: morphiq-wavelets module

#### Visual Output
```
- Multiple panels showing decomposition levels
- Each level in different color
- Automatic scaling per level
- Real-time updates
```

#### Key Features
- Dynamic level adjustment
- Visual market structure representation
- Parallel processing for performance

---

### 4. **Denoised Trend Following**

#### Status: ✅ COMPLETE

#### Purpose
Classic wavelet denoising for cleaner price action and reduced false signals.

#### Implementation Details
- **Class**: `com.morphiqlabs.wavelets.DenoisedTrendFollowing`
- **Type**: Overlay with denoised price
- **Location**: morphiq-wavelets module

#### Key Features
- Adaptive noise reduction
- Preserves important market moves
- Multiple threshold methods
- Reduced whipsaws in choppy markets

---

## Core Components

### VectorWaveSwtAdapter
**Status**: ✅ COMPLETE

Bridge to VectorWave library providing:
- Forward/inverse SWT transforms
- Multiple boundary modes
- Perfect reconstruction
- In-place coefficient updates

### Thresholds
**Status**: ✅ COMPLETE

Advanced thresholding methods:
- **Universal**: σ√(2 log N)
- **BayesShrink**: Adaptive based on SNR
- **SURE**: Stein's Unbiased Risk Estimate
- Soft and hard shrinkage functions

### WaveletAtr
**Status**: ✅ COMPLETE

Wavelet-based volatility estimation:
- RMS energy from detail coefficients
- Level-weighted contributions
- EMA smoothing
- Band calculation for stops

---

## Architecture Principles

### 1. Efficient Data Handling
- Sliding window buffers for streaming
- Pre-allocated memory pools
- In-place coefficient updates
- O(1) incremental updates

### 2. MotiveWave Integration
- Extends Study base class
- Uses DataSeries for storage
- Follows marker patterns
- Settings/Runtime descriptors

### 3. Performance Optimization
- Parallel processing (≥512 points)
- Logging guards
- Adapter instance reuse
- Minimal allocations

### 4. Signal Quality
- Cross-scale confirmation
- Adaptive thresholding
- Momentum smoothing
- Balanced entry/exit logic

---

## Future Study Concepts

### Planned Enhancements

#### 1. Custom ES/NQ Wavelets
- Microstructure-optimized filters
- Tick volume integration
- Order flow wavelets

#### 2. Multi-Timeframe Synchronization
- Aligned decomposition across timeframes
- Cross-timeframe momentum
- Hierarchical signal generation

#### 3. Machine Learning Integration
- Adaptive coefficient optimization
- Market regime detection
- Dynamic parameter adjustment

#### 4. Portfolio-Level Analysis
- Cross-asset correlation wavelets
- Risk decomposition
- Sector rotation signals

---

## Implementation Guidelines

### Creating New Studies

1. **Extend Study Base Class**
```java
@StudyHeader(
    namespace = "com.morphiqlabs.wavelets",
    id = "UNIQUE_ID",
    name = "Study Name",
    overlay = true
)
public class MyWaveletStudy extends Study {
    // Implementation
}
```

2. **Use VectorWaveSwtAdapter**
```java
VectorWaveSwtAdapter adapter = new VectorWaveSwtAdapter("db4");
SwtResult result = adapter.transform(prices, levels);
```

3. **Apply Thresholding**
```java
double threshold = Thresholds.calculateThreshold(coeffs, method, level);
result.applyShrinkage(level, threshold, soft);
```

4. **Store Results**
```java
series.setDouble(index, Values.CUSTOM, result.getApproximation()[last]);
```

---

## Testing Requirements

### Unit Tests
- Transform accuracy
- Perfect reconstruction
- Threshold calculations
- Signal generation logic

### Integration Tests
- MotiveWave data flow
- Marker persistence
- Settings updates
- Memory management

### Performance Tests
- Sliding window efficiency
- Parallel processing
- Memory usage
- Update latency

---

## Documentation

Each study should include:
1. Mathematical foundation
2. Parameter descriptions
3. Usage examples
4. Performance characteristics
5. Recommended settings by market/timeframe

---

This document reflects the current state of wavelet studies in the Morphiq Suite. Future enhancements will extend these foundations while maintaining backward compatibility.