# Morphiq Suite API Reference

## Core Classes

### SwtTrendMomentumStudy
**Package**: `com.prophetizo.wavelets.swt`

Main wavelet indicator that overlays price charts with trend and momentum analysis.

#### Key Methods
- `calculate(int index, DataContext ctx)` - Per-bar calculation
- `onLoad(Defaults defaults)` - Initialize study
- `onSettingsUpdated(DataContext ctx)` - Handle setting changes

#### Configuration Parameters
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `WAVELET_TYPE` | String | "db4" | Wavelet family (db2-db20, sym2-sym20, coif1-coif5, haar) |
| `LEVELS` | Integer | 5 | Decomposition levels (2-8) |
| `WINDOW_LENGTH` | Integer | 512 | Analysis window size (256-4096) |
| `THRESHOLD_METHOD` | String | "Universal" | Universal, Bayes, or SURE |
| `MOMENTUM_TYPE` | String | "SUM" | SUM or SIGN calculation |
| `MOMENTUM_THRESHOLD` | Double | 0.01 | Minimum momentum for signals |

#### Signals
- `LONG_ENTER` - Long entry signal
- `SHORT_ENTER` - Short entry signal  
- `FLAT_EXIT` - Exit position signal

---

### SwtTrendMomentumStrategy
**Package**: `com.prophetizo.wavelets.swt`

Automated trading strategy extending the study with order management.

#### Additional Methods
- `onActivate(OrderContext ctx)` - Strategy activation
- `onDeactivate(OrderContext ctx)` - Strategy deactivation
- `onOrderFilled(OrderContext ctx, Order order)` - Order fill handling
- `onSignal(OrderContext ctx, Object signal)` - Signal processing

#### Strategy Parameters
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `POSITION_SIZE` | Integer | 100 | Base position size |
| `MAX_RISK_PER_TRADE` | Double | 500.0 | Maximum dollar risk |
| `USE_WATR_STOPS` | Boolean | true | Use volatility-based stops |
| `STOP_MULTIPLIER` | Double | 2.0 | Stop distance multiplier |

---

### VectorWaveSwtAdapter
**Package**: `com.prophetizo.wavelets.swt.core`

Bridge to VectorWave library for SWT/MODWT transforms.

#### Methods
- `transform(double[] data, int levels)` - Forward SWT transform
- `denoise(double[] data, int levels)` - Apply denoising
- `denoise(double[] data, int levels, double threshold, boolean soft)` - Custom threshold

#### Inner Class: SwtResult
- `getApproximation()` - Get smooth trend (A_J)
- `getDetail(int level)` - Get detail coefficients (D_j)
- `applyShrinkage(int level, double threshold, boolean soft)` - Apply thresholding
- `reconstruct(int maxLevel)` - Inverse transform

---

### Thresholds
**Package**: `com.prophetizo.wavelets.swt.core`

Wavelet coefficient thresholding methods.

#### Static Methods
- `calculateThreshold(double[] coeffs, ThresholdMethod method, int level)` - Calculate threshold
- `calculateUniversalThreshold(double[] coeffs)` - Universal threshold
- `calculateBayesShrinkThreshold(double[] coeffs, int level)` - BayesShrink
- `calculateSureThreshold(double[] coeffs)` - SURE threshold

#### Enums
- `ThresholdMethod`: UNIVERSAL, BAYES_SHRINK, SURE
- `ShrinkageType`: SOFT, HARD

---

### WaveletAtr
**Package**: `com.prophetizo.wavelets.swt.core`

Wavelet-based Average True Range calculation.

#### Methods
- `calculate(SwtResult swtResult, int k)` - Calculate WATR from SWT
- `reset()` - Reset smoothing state
- `getCurrentValue()` - Get current smoothed value
- `createBands(double centerPrice, double multiplier)` - Create volatility bands

#### Inner Class: WatrBands
- `getUpperBand()` - Upper volatility band
- `getLowerBand()` - Lower volatility band
- `isInsideBands(double price)` - Check if price within bands

---

## MotiveWave Integration

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
```

### Data Access
```java
DataSeries series = ctx.getDataSeries();
double close = series.getClose(index);
series.setDouble(index, Values.AJ, trendValue);
```

### Signal Generation
```java
ctx.signal(index, Signals.LONG_ENTER, "Long Entry", price);
addFigure(new Marker(coord, Position.BOTTOM, marker, msg));
```

---

## Usage Examples

### Basic Study Usage
```java
// In calculate method
VectorWaveSwtAdapter adapter = new VectorWaveSwtAdapter("db4");
SwtResult result = adapter.transform(prices, 5);

// Apply thresholding
result.applyShrinkage(1, threshold, true); // Soft threshold level 1

// Get trend
double[] trend = result.getApproximation();
```

### Momentum Calculation
```java
// RMS energy-based momentum
double momentum = 0.0;
for (int level = 1; level <= k; level++) {
    double[] detail = result.getDetail(level);
    double rms = calculateRMS(detail);
    momentum += rms * weight;
}
```

### WATR Bands
```java
WaveletAtr watr = new WaveletAtr(14);
double volatility = watr.calculate(swtResult, 2);
WatrBands bands = watr.createBands(price, 2.0);
```

---

## Performance Notes

- Use sliding window buffers for streaming updates
- Guard expensive logging with `logger.isDebugEnabled()`
- Parallel processing activates for data ≥ 512 points
- Cache SWT results when possible
- Reuse adapter instances

---

## Version History

### v1.0.0 (Current)
- Initial SWT/MODWT implementation
- RMS energy-based momentum
- Three thresholding methods
- Wavelet-ATR risk management
- Balanced signal generation