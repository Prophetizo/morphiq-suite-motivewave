# SWT Trend + Momentum Strategy Documentation

## Table of Contents
1. [Overview](#overview)
2. [How It Works](#how-it-works)
3. [Settings Guide](#settings-guide)
4. [Settings Interactions](#settings-interactions)
5. [Market-Specific Configuration](#market-specific-configuration)
6. [Signal Generation Logic](#signal-generation-logic)
7. [Risk Management](#risk-management)
8. [Troubleshooting](#troubleshooting)
9. [Performance Optimization](#performance-optimization)
10. [Best Practices](#best-practices)

## Overview

The SWT Trend + Momentum Strategy is a sophisticated trading system that combines Stationary Wavelet Transform (SWT/MODWT) trend extraction with cross-scale momentum analysis. It provides both visual indicators and automated trading signals.

### Key Features
- **Undecimated Wavelet Transform**: Shift-invariant decomposition for consistent signals
- **Cross-Scale Momentum**: Combines multiple wavelet detail levels for confirmation
- **Adaptive Noise Reduction**: Three thresholding methods to filter market noise
- **WATR-Based Stops**: Wavelet Average True Range for dynamic risk management
- **Trade Lots Integration**: Properly respects MotiveWave's Trade Lots setting

### Components
1. **Study (Indicator)**: Visual overlay with trend line and momentum oscillator
2. **Strategy**: Automated trading with position management and risk controls

## How It Works

### 1. Wavelet Decomposition
The strategy performs a Stationary Wavelet Transform on price data:
```
Price → SWT → Approximation (smooth trend) + Details (D₁, D₂, D₃, ...)
```

### 2. Trend Extraction
- **Approximation**: Provides the smooth underlying trend
- **Denoised Signal**: Optional reconstruction with thresholded details

### 3. Momentum Calculation
Cross-scale momentum combines detail coefficients from multiple levels:
```
Momentum = Σ(weight[i] × detail[i]) × 100
```
- Level 1 (D₁): 100% weight (high-frequency)
- Level 2 (D₂): 67% weight
- Level 3 (D₃): 50% weight

**Note**: As of v1.0.0, momentum values are scaled by 100x for better visibility

### 4. Signal Generation
Entry signals require BOTH conditions:
- **Long**: Positive slope AND momentum > threshold
- **Short**: Negative slope AND momentum < -threshold

## Settings Guide

### General Tab

#### Wavelet Configuration

| Setting | Default | Range | Description |
|---------|---------|-------|-------------|
| **Wavelet Type** | Haar | Multiple | Wavelet family for decomposition |
| **Decomposition Levels** | 4 | 2-8 | Number of decomposition scales |
| **Window Length** | 256 | 256-4096 | Bars analyzed per calculation |

**Wavelet Type Selection**:
- **Haar**: Sharp, good for breakouts and sudden moves
- **Daubechies (db4-db20)**: Smooth, excellent for trend following
- **Symlets (sym4-sym20)**: Balanced, good general purpose
- **Coiflets (coif1-coif5)**: Compact, good for range-bound markets

#### Thresholding

| Setting | Default | Range | Description |
|---------|---------|-------|-------------|
| **Threshold Method** | Universal | 3 options | Noise reduction algorithm |
| **Shrinkage Type** | Hard | Soft/Hard | How coefficients are thresholded |
| **Use Denoised Signal** | ✓ | On/Off | Use denoised vs raw approximation |

**Threshold Methods**:
- **Universal**: Conservative, robust (best for volatile markets)
- **BayesShrink**: Adaptive, balanced (good general purpose)
- **SURE**: Optimal MSE (best for trending markets)

#### Signal Configuration

| Setting | Default | Range | Description |
|---------|---------|-------|-------------|
| **Detail Confirmation (k)** | 1 | 1-3 | Detail levels for momentum |
| **Momentum Calculation** | SUM | SUM/SIGN | How to combine details |
| **Momentum Threshold** | 1.0 | 0-100 | Minimum momentum for signals (scaled) |
| **Min Slope Threshold (%)** | 0.05 | 0-0.1 | Minimum trend slope |
| **Enable Trading Signals** | ✓ | On/Off | Generate trading signals |

### Display Tab

#### Plot Configuration

| Setting | Default | Description |
|---------|---------|-------------|
| **Trend Color** | Blue | Color of denoised trend line |
| **Trend Width** | 2.0 | Line thickness |
| **Show WATR Bands** | Off | Display volatility bands |
| **WATR Color** | Gray | Color of WATR bands |
| **WATR Levels (k)** | 2 | Detail levels for WATR calculation |
| **WATR Multiplier** | 2.0 | Band distance from trend |
| **WATR Scale Method** | LINEAR | Scaling approach (LINEAR/SQRT/LOG/ADAPTIVE) |
| **WATR Scale Factor** | 100.0 | Scaling multiplier |

### Markers Tab

| Marker | Default | Description |
|--------|---------|-------------|
| **Long Entry** | Green Triangle | Buy signal marker |
| **Short Entry** | Red Triangle | Sell signal marker |
| **Flat Exit** | Gray Square | Exit signal marker |

### Strategy Tab (Strategy Version Only)

#### Position Management

| Setting | Default | Range | Description |
|---------|---------|-------|-------------|
| **Position Size Factor** | 1 | 1-100 | Base position size multiplier |
| **Max Risk Per Trade ($)** | 500 | 50-5000 | Maximum dollar risk per trade |

**Important**: Final position = Position Size Factor × Trade Lots (from Trading Options panel)

#### Risk Management

| Setting | Default | Range | Description |
|---------|---------|-------|-------------|
| **Use WATR-based Stops** | ✓ | On/Off | Dynamic vs fixed stops |
| **Stop Loss Multiplier** | 2.0 | 1-5 | WATR multiplier for stops |
| **Target Multiplier** | 3.0 | 1.5-10 | Risk/reward ratio |
| **Min Stop Distance** | 5 | 2-20 | Minimum points for stop |
| **Max Stop Distance** | 25 | 10-100 | Maximum points for stop |
| **Enable Bracket Orders** | ✓ | On/Off | Use bracket vs market orders |

## Settings Interactions

### Critical Relationships

1. **Window Length × Decomposition Levels**
   - More levels require longer windows
   - Rule: Window ≥ 2^(levels+3)
   - Example: 4 levels needs minimum 128 bars

2. **Momentum Threshold × Detail Confirmation**
   - More detail levels (k) = higher momentum values
   - Adjust threshold when changing k
   - k=1: Threshold ~0.5-2.0
   - k=2: Threshold ~1.0-5.0
   - k=3: Threshold ~2.0-10.0

3. **Slope Threshold × Wavelet Type**
   - Smoother wavelets need lower thresholds
   - Haar: 0.05-0.10%
   - Daubechies: 0.03-0.07%
   - Symlets: 0.04-0.08%

4. **Thresholding × Signal Quality**
   - Hard thresholding: More signals, some noise
   - Soft thresholding: Fewer signals, cleaner
   - Universal: Most conservative
   - SURE: Most aggressive

## Market-Specific Configuration

### ES (E-mini S&P 500) - 1 Minute

```yaml
Wavelet Type: Daubechies 4
Decomposition Levels: 4
Window Length: 256
Threshold Method: Universal
Shrinkage Type: Hard
Use Denoised: Yes
Detail Confirmation: 1
Momentum Threshold: 1.0
Min Slope Threshold: 0.05%
Stop Multiplier: 2.0
Target Multiplier: 3.0
Min Stop: 5 points
Max Stop: 20 points
```

### NQ (E-mini NASDAQ) - 1 Minute

```yaml
Wavelet Type: Daubechies 6
Decomposition Levels: 4
Window Length: 512
Threshold Method: BayesShrink
Shrinkage Type: Soft
Use Denoised: Yes
Detail Confirmation: 2
Momentum Threshold: 2.0
Min Slope Threshold: 0.07%
Stop Multiplier: 2.5
Target Multiplier: 3.0
Min Stop: 10 points
Max Stop: 40 points
```

### Forex (EUR/USD) - 5 Minute

```yaml
Wavelet Type: Symlet 8
Decomposition Levels: 5
Window Length: 512
Threshold Method: Universal
Shrinkage Type: Soft
Use Denoised: Yes
Detail Confirmation: 2
Momentum Threshold: 1.5
Min Slope Threshold: 0.01%
Stop Multiplier: 2.0
Target Multiplier: 2.5
Min Stop: 10 pips
Max Stop: 30 pips
```

### Stocks - 15 Minute

```yaml
Wavelet Type: Coiflet 3
Decomposition Levels: 5
Window Length: 1024
Threshold Method: SURE
Shrinkage Type: Soft
Use Denoised: Yes
Detail Confirmation: 3
Momentum Threshold: 3.0
Min Slope Threshold: 0.02%
Stop Multiplier: 1.5
Target Multiplier: 2.0
Min Stop: 0.20
Max Stop: 1.00
```

### Crypto (BTC/USD) - 1 Hour

```yaml
Wavelet Type: Symlet 10
Decomposition Levels: 6
Window Length: 1024
Threshold Method: BayesShrink
Shrinkage Type: Soft
Use Denoised: Yes
Detail Confirmation: 2
Momentum Threshold: 5.0
Min Slope Threshold: 0.10%
Stop Multiplier: 3.0
Target Multiplier: 2.5
Min Stop: 100 points
Max Stop: 500 points
```

## Signal Generation Logic

### Entry Conditions

**Long Entry**:
```java
slope > (currentTrend × minSlopeThreshold) AND
momentum > momentumThreshold AND
NOT currently in position
```

**Short Entry**:
```java
slope < -(currentTrend × minSlopeThreshold) AND
momentum < -momentumThreshold AND
NOT currently in position
```

### Exit Conditions

**Exit Signal Generated When**:
1. Slope reverses (loses minimum threshold)
2. Momentum crosses zero
3. Opposite entry signal appears

### Position Sizing Formula

```java
// Auto-detect point value from instrument
pointValue = instrument.getPointValue()
// ES = $50, NQ = $20, CL = $1000, etc.

// Calculate risk in points
riskInPoints = min(max(WATR × stopMultiplier, minStop), maxStop)

// Calculate position size factor
positionSizeFactor = maxRisk / (riskInPoints × pointValue)

// Apply Trade Lots multiplier
finalQuantity = positionSizeFactor × tradeLots
```

## Risk Management

### WATR (Wavelet ATR) Calculation

WATR uses RMS energy from detail coefficients:
```java
WATR = sqrt(Σ(detail[i]² × weight[i]) / n) × scalingFactor
```

Level weights:
- Level 1: 100% (weight = 1.0)
- Level 2: 67% (weight = 0.67)
- Level 3: 50% (weight = 0.50)

### Stop Loss Placement

**Dynamic (WATR-based)**:
- Long: Entry - (WATR × stopMultiplier)
- Short: Entry + (WATR × stopMultiplier)

**Constraints**:
- Minimum: minStopPoints
- Maximum: maxStopPoints

### Target Placement

```java
targetDistance = stopDistance × targetMultiplier
Long Target = Entry + targetDistance
Short Target = Entry - targetDistance
```

## Troubleshooting

### Issue: Momentum Oscillator Too Quiet/Flat

**Symptoms**: Flat line or barely visible momentum
**Solutions**:
1. Check Momentum Threshold - should be 1.0 or higher (not 0.01)
2. Increase Detail Confirmation (k) to 2 or 3
3. Check if thresholding is too aggressive (try Hard instead of Soft)
4. Switch Threshold Method to SURE for less aggressive filtering
5. Verify Window Length is appropriate for timeframe

### Issue: Too Many False Signals

**Symptoms**: Excessive whipsaws, poor win rate
**Solutions**:
1. Increase Min Slope Threshold to 0.08-0.10%
2. Increase Momentum Threshold to 2.0-3.0
3. Use Soft thresholding instead of Hard
4. Increase Window Length to 512 or 1024
5. Enable Use Denoised Signal

### Issue: Missing Good Trends

**Symptoms**: Strategy misses obvious trends
**Solutions**:
1. Decrease Min Slope Threshold to 0.02-0.03%
2. Decrease Momentum Threshold to 0.5-1.0
3. Reduce Decomposition Levels to 3
4. Use Hard thresholding instead of Soft
5. Check Detail Confirmation (k) - try 1 for faster response

### Issue: Stops Too Tight/Wide

**Symptoms**: Premature exits or excessive losses
**Solutions**:
1. Adjust Stop Multiplier (1.5-3.0 typical)
2. Modify Min/Max Stop constraints
3. Check WATR Scale Factor (default 100)
4. Consider different WATR Scale Method

### Issue: JavaFX UI Exceptions

**Symptoms**: NullPointerExceptions in console
**Solution**: Fixed in latest version - markers are now properly managed

### Issue: Trade Lots Not Respected

**Symptoms**: Position size doesn't match Trade Lots setting
**Solution**: Fixed in latest version - now properly multiplies by Trade Lots

## Performance Optimization

### CPU Usage Optimization

1. **Reduce Window Length**: 
   - 256 bars: Fast updates, good for scalping
   - 512 bars: Balanced
   - 1024+ bars: Slower but more stable

2. **Optimize Decomposition Levels**:
   - 3-4 levels: Fast, good for intraday
   - 5-6 levels: Balanced
   - 7-8 levels: Slow, position trading only

3. **Disable Unused Features**:
   - Turn off WATR bands if not needed
   - Disable markers you don't use

### Memory Usage

- **256 bars, 4 levels**: ~10MB
- **512 bars, 5 levels**: ~15MB
- **1024 bars, 6 levels**: ~25MB
- **2048 bars, 7 levels**: ~40MB

### Calculation Speed

- **Fast (< 10ms)**: 256 bars, 3-4 levels
- **Medium (10-25ms)**: 512 bars, 5 levels
- **Slow (25-50ms)**: 1024 bars, 6-7 levels

### Threading

The strategy is fully thread-safe with:
- Synchronized buffer operations
- Volatile momentum state
- Thread-safe WATR calculations
- Atomic marker management

## Best Practices

### 1. Start Conservative
- Begin with default settings
- Use paper trading for at least 100 trades
- Gradually adjust one parameter at a time

### 2. Match Settings to Timeframe

| Timeframe | Window | Levels | Momentum Threshold |
|-----------|--------|--------|--------------------|
| 1-min | 256 | 3-4 | 0.5-1.0 |
| 5-min | 512 | 4-5 | 1.0-2.0 |
| 15-min | 512 | 5 | 2.0-3.0 |
| 1-hour | 1024 | 5-6 | 3.0-5.0 |
| 4-hour | 2048 | 6-7 | 5.0-10.0 |
| Daily | 2048 | 7-8 | 10.0-20.0 |

### 3. Validate with Metrics
- **Win Rate**: Target 45-55%
- **Risk/Reward**: Minimum 1:1.5
- **Profit Factor**: Above 1.3
- **Max Drawdown**: Under 20%
- **Sharpe Ratio**: Above 0.8

### 4. Market Session Adjustments

**Asian Session**: Increase thresholds by 50%
**London Open**: Reduce thresholds by 20%
**US Open**: Normal settings
**US Close**: Increase thresholds by 30%

### 5. Volatility Adjustments

Monitor VIX and adjust:
- **VIX < 15**: Reduce all thresholds by 20%
- **VIX 15-25**: Use normal settings
- **VIX 25-35**: Increase thresholds by 30%
- **VIX > 35**: Increase thresholds by 50%

### 6. Position Sizing Rules
- Never risk more than 2% per trade
- Reduce size by 50% after 3 consecutive losses
- Increase size by 25% after 5 consecutive wins
- Always respect the Trade Lots setting

## Advanced Features

### Custom Wavelet Selection by Market

**Futures (ES, NQ, CL)**:
- Intraday: db4 or db6
- Swing: sym8 or sym10
- Position: coif3 or coif5

**Forex Majors**:
- Scalping: haar or db2
- Day Trading: db4 or sym6
- Swing: sym8 or db10

**Stocks/ETFs**:
- Day Trading: db6
- Swing: sym8
- Investment: coif5

**Crypto**:
- Scalping: haar
- Day Trading: db4
- Swing: sym8
- HODL: coif5

### Multi-Timeframe Confirmation

Run on multiple timeframes:
1. **Primary**: Entry signals
2. **Higher**: Trend confirmation
3. **Lower**: Fine-tune entries

Example for ES:
- 15-min: Primary signals
- 1-hour: Trend direction
- 5-min: Entry timing

### Momentum Calculation Methods

**SUM Mode** (Default):
- Uses weighted average of coefficients
- Preserves magnitude information
- Better for trending markets
- Values typically 0-10 after scaling

**SIGN Mode**:
- Counts positive/negative coefficients
- Discrete signals (-k to +k)
- Better for ranging markets
- Cleaner but less nuanced

## Technical Implementation Details

### Sliding Window Buffer
- Efficient O(1) updates for new bars
- Circular buffer implementation
- Minimal memory allocation
- Thread-safe with synchronized blocks

### Momentum Smoothing
- EMA smoothing with α = 0.5
- Window size = 10 bars
- 100x scaling for visibility
- Volatile state for thread safety

### Signal State Machine
```
FLAT → LONG_ENTER → LONG → FLAT_EXIT → FLAT
FLAT → SHORT_ENTER → SHORT → FLAT_EXIT → FLAT
```

### Recent Updates (August 2024)

1. **Trade Lots Integration**: Properly multiplies position by Trade Lots
2. **Momentum Scaling**: 100x scaling for better visibility
3. **Thread Safety**: Enhanced synchronization
4. **Point Value**: Auto-detection from instrument
5. **JavaFX Fixes**: Resolved UI exceptions
6. **Logging**: Comprehensive position sizing logs
7. **Maven Shade**: Fixed overlapping class warnings

## Version Information

**Current Version**: 1.0.0-SNAPSHOT
**Last Updated**: August 13, 2024
**Java Version**: 21+
**MotiveWave SDK**: 20230627
**VectorWave**: 1.0-SNAPSHOT

## Support Resources

- **GitHub Issues**: Report bugs and request features
- **CLAUDE.md**: Development guide for AI assistants
- **API Reference**: Detailed class documentation
- **Test Suite**: Comprehensive unit and integration tests

## Legal Disclaimer

Trading financial instruments involves substantial risk of loss and is not suitable for all investors. Past performance does not guarantee future results. This strategy is provided for educational purposes only. Always test thoroughly in a demo environment before live trading.

---

*Copyright © 2024 Prophetizo, LLC. All rights reserved.*