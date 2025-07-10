# Wavelet Trading Strategies

This document details the planned wavelet-based trading strategies for the MotiveWave platform. Each strategy leverages different aspects of wavelet analysis to provide traders with actionable insights.

## Overview

Wavelet transforms decompose price data into multiple time-frequency components, revealing hidden patterns and relationships that traditional indicators miss. Our strategies exploit these multi-scale insights for improved trading decisions.

## Implemented Strategies

### 1. AutoWavelets (✅ Completed)
**Purpose**: Provide adaptive multi-scale price decomposition

**Key Features**:
- MODWT (Maximal Overlap Discrete Wavelet Transform) using Daubechies4
- Dynamic decomposition levels (1-7) based on chart timeframe
- Auto-scaling to target ~390 minute trading sessions
- Real-time path management - only active levels displayed

**Trading Applications**:
- Identify trend direction at multiple timeframes simultaneously
- Filter out market noise to see underlying price movement
- Spot divergences between short-term and long-term trends

## Core Wavelet Theory

### Transform Types
1. **DWT (Discrete Wavelet Transform)**
   - Pros: Computationally efficient, excellent for denoising
   - Cons: Sensitive to starting point (not shift-invariant)
   - Use case: Offline analysis, feature extraction

2. **MODWT (Maximal Overlap DWT)** ✅ Currently Used
   - Pros: Shift-invariant, stable for real-time analysis
   - Cons: More computationally intensive than DWT
   - Use case: Real-time trading signals (our primary choice)

3. **CWT (Continuous Wavelet Transform)**
   - Pros: Highly detailed time-frequency representation
   - Cons: Computationally demanding, redundant information
   - Use case: Visual analysis, pattern identification

### Wavelet Selection Guide
- **Haar**: Step function, excellent for detecting sharp price jumps
- **Daubechies (db4, db6)**: Balanced smoothness and structure capture ✅ Currently Used
- **Symlets (sym4)**: Similar to Daubechies but more symmetric
- **Morlet**: Complex wavelet, ideal for oscillatory/cyclical patterns

### Decomposition Level Interpretation (1-minute bars)
- **Level 1 (D1)**: Tick-level noise, ultra-short volatility
- **Level 2 (D2)**: Short-term momentum bursts (2-4 minutes)
- **Level 3 (D3)**: Established short-term trends (5-10 minutes)
- **Level 4 (D4)**: Medium-term intraday trends (15-30 minutes)
- **Level 5+ (D5-D7)**: Longer intraday to daily trends
- **Approximation (A7)**: Underlying smoothed trend

## Planned Strategies

### 2. Denoised Trend Following ⭐ Priority
**Purpose**: Filter high-frequency noise for cleaner trend signals

**Implementation Plan**:
- Perform multi-level MODWT decomposition
- Zero out D1 and optionally D2 coefficients (noise removal)
- Reconstruct denoised price via inverse transform
- Apply traditional indicators (MA crossover, etc.) to clean signal

**Trading Logic**:
- Entry: Standard trend signals on denoised price
- Exit: Trend reversal on denoised price
- Stop Loss: Based on denoised price volatility

**Advantages**:
- Fewer false signals from market microstructure noise
- More robust trend identification
- Better risk/reward ratios

**MotiveWave Implementation**:
- Overlay original and denoised price
- Show removed noise component separately
- Color-code based on trend strength

### 3. Multi-Timeframe Energy Burst Detection
**Purpose**: Identify momentum injections at specific time scales

**Implementation Plan**:
- Calculate energy for each detail level: Σ(coefficient²) over recent window
- Monitor energy spikes relative to baseline
- Confirm with broader trend from approximation coefficients
- Generate signals when energy aligns with trend

**Trading Logic**:
- Entry: Energy spike in D2/D3 + trend alignment from A4
- Exit: Energy subsides or trend flattens
- Position Size: Scale with energy magnitude

**Energy Calculation**:
```java
double energy = 0;
for (int i = 0; i < windowSize; i++) {
    energy += coefficients[level][index-i] * coefficients[level][index-i];
}
```

**MotiveWave Visualization**:
- Energy meter for each decomposition level
- Heat map showing energy distribution
- Alert arrows on energy spikes

### 4. Wavelet Coherence Pairs Trading (ES vs NQ)
**Purpose**: Trade correlation breakdowns between correlated instruments

**Implementation Plan**:
- Implement wavelet cross-coherence analysis
- Monitor correlation strength at each frequency
- Detect divergences from historical coherence patterns
- Generate mean-reversion signals

**Trading Logic**:
- Entry: Coherence breakdown after sustained correlation
- Direction: Fade the instrument that diverged
- Exit: Coherence restoration or stop loss
- Risk: Position size inversely proportional to coherence

**Advanced Features**:
- Lead-lag analysis via phase relationships
- Dynamic hedge ratios from coherence strength
- Multi-pair basket trading extension

**MotiveWave Visualization**:
- 2D coherence plot (time × frequency × strength)
- Phase arrows showing lead-lag
- Divergence alerts

### 5. Wavelet Support/Resistance Detector
**Purpose**: Identify key price levels using wavelet coefficient analysis

**Implementation Plan**:
- Monitor zero-crossings in detail coefficients (D1-D7)
- Track coefficient magnitude peaks as potential reversal zones
- Create horizontal lines at price levels with coefficient confluence
- Weight importance by decomposition level (higher levels = stronger S/R)

**Trading Edge**:
- Dynamic S/R levels that adapt to market conditions
- Multi-timeframe confirmation of key levels
- Early detection of level breaks through coefficient behavior

### 3. Wavelet Cycle Analyzer
**Purpose**: Extract and predict dominant market cycles

**Implementation Plan**:
- Perform Hilbert transform on wavelet coefficients for phase analysis
- Calculate instantaneous period for each decomposition level
- Project future cycle turns based on historical phase progression
- Generate cycle confluence zones where multiple scales align

**Trading Edge**:
- Time entries/exits with cycle lows/highs
- Identify when multiple cycles reinforce each other
- Adapt to changing market rhythms automatically

### 4. Volatility Regime Monitor
**Purpose**: Detect changes in market volatility structure

**Implementation Plan**:
- Calculate wavelet packet energy at each scale
- Monitor energy distribution shifts across decomposition levels
- Detect regime changes through threshold crossings
- Color-code background based on volatility state

**Trading Edge**:
- Adjust position sizing based on volatility regime
- Switch between trend-following and mean-reversion strategies
- Early warning of volatility expansions/contractions

### 5. Multi-Scale Trend Alignment
**Purpose**: Generate high-probability signals from multi-timeframe agreement

**Implementation Plan**:
- Calculate trend direction for each wavelet level
- Weight signals by decomposition scale
- Generate confluence score (0-100%)
- Alert when alignment exceeds threshold

**Trading Edge**:
- Trade only when multiple timeframes agree
- Avoid conflicting signals from different scales
- Higher win rate through multi-scale confirmation

### 6. Wavelet Denoiser
**Purpose**: Remove market noise while preserving trend information

**Implementation Plan**:
- Apply soft/hard thresholding to wavelet coefficients
- Adaptive threshold based on local volatility estimation
- Reconstruct denoised price from filtered coefficients
- Overlay smoothed price with confidence bands

**Trading Edge**:
- Clearer trend identification
- Reduced false signals from market noise
- Better stop-loss placement using denoised levels

### 7. Wavelet Momentum Oscillator
**Purpose**: Create momentum indicators from wavelet coefficients

**Implementation Plan**:
- Calculate rate of change of wavelet coefficients
- Normalize across multiple scales
- Detect divergences between price and wavelet momentum
- Generate overbought/oversold zones per timeframe

**Trading Edge**:
- Early momentum shifts before price confirmation
- Scale-specific divergence detection
- Adaptive oscillator bounds based on volatility

## Technical Architecture

### Base Components
1. **WaveletAnalyzer**: Core wavelet computation engine
2. **ScaleManager**: Handles dynamic level selection
3. **CoefficientProcessor**: Common coefficient analysis functions
4. **SignalGenerator**: Converts analysis into trading signals

### Shared Utilities
- Coefficient zero-crossing detector
- Energy distribution calculator
- Phase analyzer (Hilbert transform)
- Multi-scale aggregator

### Integration Points
- All strategies extend MotiveWave Study class
- Shared configuration for wavelet parameters
- Common visualization themes (color progression)
- Unified alert/notification system

## Development Priorities

1. **Phase 1**: Foundation (Current)
   - ✅ AutoWavelets with dynamic scaling
   - Denoised Trend Following
   - Multi-timeframe Energy Burst Detection

2. **Phase 2**: Enhanced Analysis
   - Support/Resistance Detection
   - Volatility regime detection
   - Cycle extraction and prediction

3. **Phase 3**: Advanced Strategies
   - Wavelet coherence pairs trading
   - Multi-scale momentum oscillators
   - Integrated signal generation

4. **Phase 4**: Flagship Achievement 🏆
   - **Custom ES/NQ Microstructure-Optimized Wavelet**
   - The penultimate feature combining AI, market microstructure knowledge, and advanced signal processing
   - Represents the culmination of the entire wavelet trading package

## Performance Considerations

- MODWT computation is O(n log n) - efficient for real-time
- Cache coefficient calculations where possible
- Use circular buffers for streaming data
- Implement level-of-detail for large datasets

## Research Notes

### Wavelet Selection
- **Daubechies4**: Good balance for financial data
- **Symlets**: Consider for better symmetry
- **Coiflets**: Test for smoother reconstructions
- **Custom wavelets**: Explore market-specific designs

### Alternative Transforms
- Wavelet packets for finer frequency resolution
- Continuous wavelet transform for specific frequencies
- Dual-tree complex wavelets for better directional analysis

### Market-Specific Adaptations
- Adjust decomposition levels for different asset classes
- Crypto: Higher frequency components important
- Forex: Focus on medium-term cycles
- Equities: Session-based scaling optimal

## Practical Implementation Considerations

### Data Management
- **Rolling Buffer**: Maintain 256-512 bars for efficient wavelet computation
- **Circular Arrays**: Use for streaming data to minimize memory allocation
- **Coefficient Caching**: Store frequently accessed decompositions

### Key Parameters to Expose
```java
// MotiveWave Study Parameters
@SettingDescriptor
public class WaveletSettings {
    String motherWavelet = "db4";      // Wavelet type selection
    int decompositionLevels = 5;       // Number of levels
    int lookbackPeriod = 256;          // Data window size
    double energyThreshold = 2.0;      // Energy spike detection
    int energyWindow = 10;             // Energy calculation window
    boolean showDenoised = true;       // Display options
}
```

### Performance Optimization
- **Batch Processing**: Update coefficients only on bar close for efficiency
- **Level-of-Detail**: Skip higher decomposition levels on faster timeframes
- **Parallel Computation**: Process multiple instruments simultaneously
- **Memory Pool**: Pre-allocate arrays for coefficient storage

### Implementation Roadmap
1. **Phase 1 - Foundation** ⭐ Start Here
   - ✅ AutoWavelets (basic decomposition)
   - Denoised Trend Following (recommended first strategy)
   - Basic energy calculations

2. **Phase 2 - Enhanced Analysis**
   - Multi-timeframe energy burst detection
   - Support/resistance from coefficients
   - Volatility regime monitoring

3. **Phase 3 - Advanced Strategies**
   - Wavelet coherence for pairs trading
   - Custom wavelets for specific markets
   - Machine learning integration

### Market-Specific Tuning
- **ES/NQ (Index Futures)**:
  - Focus on D2-D4 for intraday momentum
  - 3-5 decomposition levels optimal
  - Energy thresholds: 1.5-2.5× baseline

- **Forex Majors**:
  - Emphasize D3-D5 for trend stability
  - Consider Morlet for cyclical patterns
  - Coherence analysis for correlated pairs

- **Cryptocurrencies**:
  - Include D1 for high-frequency volatility
  - 5-7 decomposition levels
  - Adaptive thresholds for 24/7 markets

## References

1. Percival, D. B., & Walden, A. T. (2000). Wavelet Methods for Time Series Analysis
2. Gençay, R., Selçuk, F., & Whitcher, B. (2001). An Introduction to Wavelets and Other Filtering Methods in Finance and Economics
3. Ramsey, J. B. (2002). Wavelets in Economics and Finance: Past and Future
4. In, F., & Kim, S. (2013). An Introduction to Wavelet Theory in Finance: A Wavelet Multiscale Approach