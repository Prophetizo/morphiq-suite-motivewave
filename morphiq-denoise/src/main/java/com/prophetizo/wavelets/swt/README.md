# SWT Trend Momentum Implementation

This directory contains the complete implementation of the SWT (Stationary Wavelet Transform) / MODWT trend following system with cross-scale momentum confirmation for MotiveWave.

## Architecture Overview

### Core Classes

#### VectorWaveSwtAdapter
- **Purpose**: Bridge to VectorWave library for SWT/MODWT transforms
- **Features**: 
  - Reflection-based VectorWave integration (avoids compile-time dependency)
  - Robust fallback implementation when VectorWave unavailable
  - Per-level thresholding with configurable methods
  - Perfect reconstruction validation

#### Thresholds
- **Purpose**: Advanced wavelet coefficient thresholding methods
- **Methods Implemented**:
  - **Universal**: σ√(2 log N) - robust for high noise
  - **BayesShrink**: Adaptive based on signal-to-noise ratio
  - **SURE**: Stein's Unbiased Risk Estimate - optimal risk minimization
- **Shrinkage Types**: Soft and hard thresholding

#### WaveletAtr
- **Purpose**: Wavelet-based volatility estimation
- **Features**:
  - RMS energy calculation from detail coefficients
  - Level-weighted contribution (finer details contribute more)
  - EMA smoothing for stable estimates
  - Band calculation for stop/target placement

### MotiveWave Integration

#### SwtTrendMomentumStudy
- **Type**: MotiveWave Study (overlay)
- **Key Features**:
  - Configurable wavelet parameters (type, levels, window size)
  - Multiple thresholding methods and shrinkage types
  - Cross-scale momentum confirmation (k detail levels)
  - Real-time trend line (A_J approximation) plotting
  - Optional WATR band display
  - Signal generation with visual markers

#### SwtTrendMomentumStrategy
- **Type**: MotiveWave Strategy (extends Study)
- **Features**:
  - Automated order management based on signals
  - Risk-based position sizing
  - WATR-based stop loss and target calculation
  - Bracket order support
  - Configurable risk parameters

## Algorithm Flow

### Per Bar Calculation (calculate method):

1. **Data Collection**: Extract rolling window of price data (default 4096 bars)

2. **SWT Transform**: 
   ```
   {A_J, D_1, D_2, ..., D_J} = SWT(prices, levels)
   ```

3. **Thresholding**: Apply per-level shrinkage
   ```
   For j = 1 to J:
     T_j = calculateThreshold(D_j, method, j)
     D_j = applyShrinkage(D_j, T_j, soft/hard)
   ```

4. **Trend Extraction**: 
   ```
   trend = A_J[last]
   slope = A_J[last] - A_J[last-1]
   ```

5. **Cross-Scale Momentum**:
   ```
   momentum_sum = Σ(sign(D_j[last])) for j=1 to k
   ```

6. **Filter States**:
   ```
   long_filter = slope > 0 AND momentum_sum > 0
   short_filter = slope < 0 AND momentum_sum < 0
   ```

7. **Signal Generation**:
   - Flat → Long: LONG_ENTER
   - Flat → Short: SHORT_ENTER  
   - Position → Flat: FLAT_EXIT

8. **WATR Calculation** (optional):
   ```
   WATR = RMS_energy(D_1, ..., D_k) with EMA smoothing
   ```

## Configuration Parameters

### Wavelet Settings
- **Wavelet Type**: db4, haar, sym4, coif2, etc.
- **Levels (J)**: 2-8 (default: 5 for 1-min, 6 for 2-min)
- **Window Length**: 512-8192 bars (default: 4096)

### Thresholding
- **Method**: Universal (robust) | BayesShrink (adaptive) | SURE (optimal)
- **Shrinkage**: Soft (gradual) | Hard (binary)

### Signal Configuration
- **Detail Confirm (k)**: 1-3 levels for momentum confirmation (default: 2)
- **Enable Signals**: Toggle for trading signal generation

### WATR Settings
- **WATR Detail Levels**: 1-3 (default: 2)
- **WATR Multiplier**: 1.0-5.0 (default: 2.0)
- **Show WATR Bands**: Toggle for volatility band display

### Strategy Settings
- **Position Size**: Base quantity (shares/contracts)
- **Max Risk Per Trade**: Dollar risk limit per position
- **Use WATR Stops**: Toggle for volatility-based vs fixed stops
- **Stop/Target Multipliers**: Risk/reward ratio configuration

## Key Advantages

### vs Traditional Indicators
1. **Translation Invariance**: SWT avoids downsampling artifacts
2. **Multi-Scale Analysis**: Captures patterns across different time scales
3. **Adaptive Thresholding**: Automatically adjusts to market conditions
4. **Low Lag**: Better noise suppression for same lag as large moving averages

### vs Basic Wavelet Methods
1. **Undecimated Transform**: Stable output regardless of bar alignment
2. **Per-Level Thresholding**: Optimal noise removal at each scale
3. **Cross-Scale Confirmation**: Reduces false signals from single-scale noise
4. **Financial Optimization**: Specialized for price data characteristics

## Testing Results

The implementation includes comprehensive unit tests:

- **SWT Reconstruction**: Perfect reconstruction within numerical precision
- **Thresholding Accuracy**: All methods produce reasonable thresholds
- **Shift Invariance**: Energy preservation under signal shifts
- **WATR Calculation**: Stable volatility estimation

## Usage Notes

### VectorWave Dependency
- Code compiles and runs without VectorWave (uses fallback implementation)
- For production use, place VectorWave JAR in `MotiveWave/Extensions/ext/`
- Fallback provides reasonable approximation for testing/development

### Performance Optimization
- Rolling window limits memory usage and computation time
- Caching prevents recalculation on unchanged data
- Parallel processing in VectorWave for large datasets

### Market Application
- Works best on liquid instruments with sufficient tick data
- Recommended timeframes: 1-5 minute bars
- Ideal for trend-following strategies in trending markets
- Risk management essential due to whipsaw potential in ranging markets

## Future Enhancements

1. **Custom Wavelets**: ES/NQ microstructure-optimized filters
2. **Machine Learning Integration**: Adaptive coefficient optimization
3. **Real-Time Regime Detection**: Dynamic parameter adjustment
4. **Multi-Scale Stop Management**: Different stops for different time horizons