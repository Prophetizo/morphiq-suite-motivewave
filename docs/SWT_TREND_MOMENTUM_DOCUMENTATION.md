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
- **State-Based Signals**: Study reports market state (LONG/SHORT), strategy decides actions
- **Mandatory Bracket Orders**: All entries include stop loss and take profit orders
- **Automatic Position Management**: Uses OrderContext for reliable position tracking

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
The study generates state signals when BOTH conditions are met:
- **Long State**: Positive slope AND momentum > threshold
- **Short State**: Negative slope AND momentum < -threshold

The strategy then decides whether to act on these states:
- Enters new positions when flat
- Reverses positions when receiving opposite state signal
- Ignores signals when already in same direction

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
| **Momentum Threshold** | 1.0 | 0-100 | Minimum momentum for signals (scaled by 100x) |
| **Min Slope Threshold (Points)** | 0.05 | 0-5.0 | Minimum trend slope in absolute price points |
| **Momentum Smoothing (α)** | 0.5 | 0.1-0.9 | EMA smoothing factor |
| **Momentum Window (bars)** | 10 | 5-50 | Number of bars used for RMS energy calculation |
| **Momentum Scaling Factor** | 100.0 | 1.0-1000.0 | Scaling factor to improve momentum visibility |
| **Enable Trading Signals** | ✓ | On/Off | Generate trading signals |

**Important Note on Min Slope Threshold**:
- This value is in **absolute price points**, NOT percentage
- Example for ES futures: 0.05 means the trend must move at least 0.05 points between bars
- Set to 0.00 to disable slope filtering entirely
- Typical values:
  - ES/NQ: 0.02-0.10 points
  - Stocks: 0.01-0.05 points (adjust for price level)
  - Forex: 0.0001-0.0005 points (4-5 decimal places)
  - Crypto: 1-50 points (depends on asset price)

**New Configurable Settings** (Previously Hardcoded):
- **Momentum Window**: Controls the number of recent bars used for RMS energy calculation. Smaller values (5-8) are more responsive but noisier; larger values (15-25) are smoother but less responsive.
- **Momentum Scaling Factor**: Amplifies momentum values for better visibility. The default 100.0 makes small momentum values (0.01) visible as larger values (1.0). Adjust based on your chart scaling preferences.
- **Level Weight Decay**: Shared with WATR calculation, controls how much weight is given to coarser wavelet scales. Lower values (0.3-0.4) emphasize coarser scales (trending markets), higher values (0.7-0.8) emphasize finer scales (volatile markets).

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

## Settings Interactions

### Critical Relationships

1. **Window Length × Decomposition Levels**
   - More levels require longer windows
   - Rule: Window ≥ 2^(levels+3)
   - Example: 4 levels needs minimum 128 bars

2. **Window Length × Min Slope Threshold** ⚠️ **CRITICAL**
   - **Longer windows = Smoother trend = Lower slope threshold needed**
   - **Shorter windows = More responsive = Higher slope threshold needed**
   
   | Window Length | Trend Smoothness | Recommended Slope Threshold |
   |---------------|------------------|----------------------------|
   | 256 bars | Responsive | 0.03-0.05% |
   | 512 bars | Balanced | 0.02-0.03% |
   | 1024 bars | Smooth | 0.015-0.02% |
   | 2048 bars | Very Smooth | 0.01-0.015% |
   | 4096 bars | Ultra Smooth | 0.008-0.012% |

3. **Momentum Threshold × Detail Confirmation**
   - More detail levels (k) = higher momentum values
   - Adjust threshold when changing k
   - k=1: Threshold ~0.5-2.0
   - k=2: Threshold ~1.0-5.0
   - k=3: Threshold ~2.0-10.0
   - **Note**: Momentum is scaled 100x for visibility

4. **Slope Threshold × Wavelet Type**
   - Smoother wavelets need lower thresholds
   - Haar: Higher threshold (sharp transitions)
   - Daubechies: Medium threshold (balanced)
   - Symlets: Lower threshold (very smooth)

5. **Thresholding × Signal Quality**
   - Hard thresholding: More signals, some noise
   - Soft thresholding: Fewer signals, cleaner
   - Universal: Most conservative
   - SURE: Most aggressive

6. **Momentum Smoothing × Market Conditions**
   - **Choppy/Noisy Markets**: Use lower α (0.1-0.3) for heavy smoothing
   - **Trending Markets**: Use balanced α (0.4-0.6) for standard response
   - **Fast Markets**: Use higher α (0.7-0.9) for quick response
   - **Scalping**: Higher α for minimal lag
   - **Swing Trading**: Lower α for stable signals

## Market-Specific Configuration

### ES (E-mini S&P 500) - 1 Minute

#### Quick Setup (Recommended)
```yaml
Wavelet Type: Daubechies 4
Decomposition Levels: 5
Window Length: 512-1024
Threshold Method: Universal
Shrinkage Type: Hard
Use Denoised: No
Detail Confirmation: 2
Momentum Threshold: 1.0
Min Slope Threshold: 0.02-0.03 points
Stop Multiplier: 2.0
Target Multiplier: 3.0
Min Stop: 5 points
Max Stop: 20 points
```

#### Smooth Trend Setup (Long Window)
```yaml
Wavelet Type: Daubechies 4
Decomposition Levels: 5
Window Length: 4096
Threshold Method: Universal
Shrinkage Type: Hard
Use Denoised: No
Detail Confirmation: 2
Momentum Threshold: 1.0
Min Slope Threshold: 0.01 points  # Lower due to smoother trend
Stop Multiplier: 2.0
Target Multiplier: 3.0
Min Stop: 5 points
Max Stop: 20 points
```

#### Scalping Setup (Fast Response)
```yaml
Wavelet Type: Haar or db2
Decomposition Levels: 4
Window Length: 256
Threshold Method: Universal
Shrinkage Type: Hard
Use Denoised: No
Detail Confirmation: 1
Momentum Threshold: 0.5-1.0
Min Slope Threshold: 0.03-0.05 points  # Higher for shorter window
Stop Multiplier: 1.5
Target Multiplier: 2.0
Min Stop: 3 points
Max Stop: 10 points
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
Min Slope Threshold: 0.07 points
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
Min Slope Threshold: 0.0001 points  # For forex pairs
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
Min Slope Threshold: 0.02 points  # Adjust based on stock price
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
Min Slope Threshold: 10 points  # Higher for crypto volatility
Stop Multiplier: 3.0
Target Multiplier: 2.5
Min Stop: 100 points
Max Stop: 500 points
```

## Signal Generation Logic

### State Signal Generation (Study)

**Long State**:
```java
slope > minSlopeThreshold AND  // Slope in absolute price points
momentum > momentumThreshold
```

**Short State**:
```java
slope < -minSlopeThreshold AND  // Negative slope in absolute price points
momentum < -momentumThreshold
```

### Entry/Exit Logic (Strategy)

**Strategy Actions Based on State**:
1. **Long State Received**:
   - If flat → Enter long with bracket order
   - If short → Exit short, then enter long
   - If already long → Do nothing

2. **Short State Received**:
   - If flat → Enter short with bracket order
   - If long → Exit long, then enter short
   - If already short → Do nothing

### Bracket Order Structure

All entries include three orders submitted together:
1. **Market Order**: Entry at current price
2. **Stop Loss**: Protective stop at calculated level
3. **Take Profit**: Target at risk/reward multiple

**No Flat Exit Signals**: The strategy automatically manages exits through opposite signals or stop/target hits

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
**Root Cause**: Prior to v1.0.0, momentum wasn't scaled properly
**Solutions**:
1. **Verify version**: Ensure you have the latest build with 100x scaling
2. **Check Momentum Threshold**: Should be 1.0-10.0 range (not 0.01)
3. **Increase Detail Confirmation (k)**: Try 2 or 3 for stronger signals
4. **Adjust thresholding**: Use Hard instead of Soft for more detail preservation
5. **Expected ranges after scaling**:
   - Quiet market: ±5-10
   - Normal market: ±10-20
   - Volatile market: ±20-40

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

### Issue: Unwanted Exit Signals

**Symptoms**: Getting FLAT_EXIT signals at inappropriate times
**Solution**: Fixed in v1.1.0 - FLAT_EXIT signals removed. Strategy now only exits on opposite signals or stop/target hits

### Issue: Position Not Reversing

**Symptoms**: Strategy exits but doesn't enter opposite position
**Solution**: Fixed in v1.1.0 - Strategy now automatically reverses positions when receiving opposite state signals

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

### 1. Understanding Your Trading Style

#### For Scalpers (Quick In/Out)
- **Window**: 256-512 bars (responsive to price changes)
- **Wavelet**: Haar or db2 (captures sharp moves)
- **Levels**: 3-4 (focus on high-frequency details)
- **Slope Threshold**: 0.03-0.05% (filter noise but catch moves)
- **Momentum Threshold**: 0.5-1.0 (quick signals)

#### For Day Traders (Intraday Swings)
- **Window**: 512-1024 bars (balanced smoothing)
- **Wavelet**: db4 or db6 (good trend extraction)
- **Levels**: 4-5 (multi-scale analysis)
- **Slope Threshold**: 0.02-0.03% (moderate filtering)
- **Momentum Threshold**: 1.0-3.0 (confirmed moves)

#### For Swing Traders (Multi-Day Holds)
- **Window**: 2048-4096 bars (smooth trends)
- **Wavelet**: sym8 or coif3 (maximum smoothing)
- **Levels**: 5-6 (capture larger structures)
- **Slope Threshold**: 0.01-0.015% (patient entries)
- **Momentum Threshold**: 3.0-5.0 (strong confirmation)

### 2. Optimal Settings by Market Condition

#### Trending Markets (VIX < 20)
```yaml
Use Denoised: Yes
Shrinkage Type: Soft
Threshold Method: SURE
Detail Confirmation: 1-2
Lower all thresholds by 20%
```

#### Choppy/Range Markets (VIX 20-30)
```yaml
Use Denoised: No
Shrinkage Type: Hard
Threshold Method: Universal
Detail Confirmation: 2-3
Use standard thresholds
```

#### Volatile Markets (VIX > 30)
```yaml
Use Denoised: No
Shrinkage Type: Hard
Threshold Method: Universal
Detail Confirmation: 3
Increase all thresholds by 50%
```

### 3. The Golden Rules

1. **Window Length Determines Slope Threshold**
   - Long window → Lower slope threshold
   - Short window → Higher slope threshold

2. **Match Wavelet to Market Character**
   - Trending: Daubechies (db4-db8)
   - Volatile: Haar or db2
   - Smooth: Symlets or Coiflets

3. **Momentum Scaling is Critical**
   - Always use 1.0+ thresholds (post v1.0.0)
   - Adjust based on visual feedback
   - Target ±10-20 range for normal markets

4. **Never Override These Relationships**
   - Window ≥ 2^(levels+3)
   - Slope threshold inversely proportional to window
   - Detail confirmation affects momentum magnitude

### 4. Settings Validation Checklist

Before going live, verify:
- [ ] Momentum oscillator shows ±10-20 range in normal conditions
- [ ] 5-15 signals per day on 1-min chart (not too many/few)
- [ ] Trend line smoothly follows price without excessive lag
- [ ] Signals align with obvious visual trends
- [ ] Stop distances are reasonable for the instrument
- [ ] Win rate > 40% in backtesting

### 5. Position Management Best Practices

1. **Let the Strategy Manage Positions**
   - Don't manually close positions while strategy is active
   - Strategy tracks positions via OrderContext
   - Manual intervention can desync position tracking

2. **Bracket Orders are Mandatory**
   - Every entry includes stop loss and take profit
   - Cannot be disabled - this is by design
   - Ensures consistent risk management

3. **Position Reversal is Automatic**
   - Long state while short → Exits short, enters long
   - Short state while long → Exits long, enters short
   - No manual intervention needed

4. **Trade Lots Integration**
   - Final quantity = Position Size Factor × Trade Lots
   - Set Position Size Factor to 1 to use only Trade Lots
   - Both settings multiply together

### 6. Quick Start Guide

#### Step 1: Choose Your Profile
Pick ONE based on your style:

**Conservative (Recommended for Beginners)**
```yaml
Window: 1024, Levels: 5, Wavelet: db4
Slope: 0.02%, Momentum: 2.0, Detail K: 2
```

**Balanced (Most Popular)**
```yaml
Window: 512, Levels: 4, Wavelet: db4
Slope: 0.025%, Momentum: 1.5, Detail K: 2
```

**Aggressive (Experienced Only)**
```yaml
Window: 256, Levels: 4, Wavelet: haar
Slope: 0.04%, Momentum: 1.0, Detail K: 1
```

#### Step 2: Fine-Tune Based on Results
- Too many signals? → Increase thresholds by 20%
- Missing trends? → Decrease thresholds by 20%
- Momentum flat? → Check scaling (should see ±10-20)
- Choppy signals? → Increase window length

#### Step 3: Validate Performance
Run for 100+ trades and check:
- Win Rate > 45%
- Average Win > Average Loss
- Maximum Drawdown < 15%
- Profit Factor > 1.3

### 6. Position Sizing & Risk Rules
- **Never risk more than 2% per trade**
- **Reduce size by 50% after 3 consecutive losses**
- **Scale up by 25% after 5 consecutive wins**
- **Always respect the Trade Lots setting**
- **Use bracket orders for automatic stop/target**

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
- Configurable EMA smoothing with α (default 0.5)
  - 0.1-0.3: Heavy smoothing (slow response, fewer false signals)
  - 0.4-0.6: Balanced smoothing (default range)
  - 0.7-0.9: Minimal smoothing (fast response, more noise)
- Configurable window size (default 10 bars) for RMS calculation
- Configurable scaling factor (default 100.0) for visibility
- Level weight decay (shared with WATR, default 0.5) controls scale contribution
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

### Latest Updates (2025)

8. **Configurable Values**: Made previously hardcoded values configurable:
   - Momentum Window Size (default: 10 bars, range: 5-50)
   - Momentum Scaling Factor (default: 100.0, range: 1.0-1000.0)
   - Level Weight Decay (uses existing WATR setting, default: 0.5, range: 0.1-1.0)
9. **Enhanced Flexibility**: Users can now fine-tune momentum calculation parameters
10. **Backward Compatibility**: Default values preserve existing behavior

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