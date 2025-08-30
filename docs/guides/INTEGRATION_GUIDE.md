# VectorWave MotiveWave Integration Guide

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Getting Started](#getting-started)
4. [Core Components](#core-components)
5. [Study Development](#study-development)
6. [Advanced Patterns](#advanced-patterns)
7. [Performance Optimization](#performance-optimization)
8. [Troubleshooting](#troubleshooting)
9. [API Reference](#api-reference)

## Overview

VectorWave's MotiveWave integration provides a high-performance bridge between MotiveWave's trading platform and VectorWave's wavelet transform engine. This guide covers everything you need to build professional-grade wavelet-based indicators.

### Why VectorWave for MotiveWave?

- **Zero-Copy Operations**: Direct memory access to DataSeries
- **Real-Time Optimized**: Incremental transforms for tick data
- **Trading-Focused**: Pre-tuned for common timeframes and markets
- **Developer Friendly**: Simple base classes hide complexity

## Architecture

### Component Overview

```
MotiveWave Platform
    ↓
DataSeries (price/volume data)
    ↓
VectorWave Integration Layer
├── DataSeriesBridge (zero-copy extraction)
├── TransformState (incremental updates)
├── CoefficientStore (efficient storage)
└── VectorWaveStudy (base class)
    ↓
Your Custom Study
```

### Data Flow

1. **Tick arrives** → MotiveWave calls `calculate()`
2. **Extract data** → DataSeriesBridge gets window
3. **Transform** → VectorWave processes (incremental if possible)
4. **Store** → CoefficientStore writes to DataSeries
5. **Display** → MotiveWave renders the indicator

## Getting Started

### 1. Add VectorWave Dependency

```xml

<dependency>
    <groupId>com.morphiqlabs</groupId>
    <artifactId>vectorwave-motivewave</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Create Your First Study

```java
package com.mycompany.studies;

import com.morphiqlabs.vectorwave.motivewave.studies.VectorWaveStudy;
import com.motivewave.platform.sdk.common.Enums.*;
import com.motivewave.platform.sdk.study.StudyDescriptor;

@StudyHeader(
        namespace = "com.mycompany",
        id = "WAVELET_TREND",
        name = "Wavelet Trend",
        label = "Wavelet Trend",
        desc = "Extracts trend using wavelet decomposition",
        menu = "Wavelets",
        overlay = true,
        signals = true
)
public class WaveletTrend extends VectorWaveStudy {

    @Override
    protected Values getInputValue() {
        return Values.CLOSE;  // Process close prices
    }

    @Override
    protected void setupStudyParameters(StudyDescriptor desc) {
        // Configure study
        desc.setLabelSettings(InputLabels.INPUT);

        // Add outputs
        desc.addOutput(Values.A1, "Trend", Colors.BLUE, Plot.LINE, 2);
        desc.addOutput(Values.D1, "Noise", Colors.GRAY, Plot.LINE, 1);

        // Optional: signals
        desc.addSignal(Signals.CROSS_ABOVE, "Price crosses above trend");
        desc.addSignal(Signals.CROSS_BELOW, "Price crosses below trend");
    }

    @Override
    protected void storeResults(DataSeries series, int index,
                                TransformResult result) {
        // Store using compact strategy
        CoefficientStore.MappingStrategy.COMPACT.store(series, index, result);

        // Generate signals
        checkSignals(series, index);
    }

    private void checkSignals(DataSeries series, int index) {
        if (index < 1) return;

        double price = series.getDouble(index, Values.CLOSE);
        double trend = series.getDouble(index, Values.A1);
        double prevPrice = series.getDouble(index - 1, Values.CLOSE);
        double prevTrend = series.getDouble(index - 1, Values.A1);

        // Check for crossovers
        if (prevPrice <= prevTrend && price > trend) {
            series.signal(index, Signals.CROSS_ABOVE);
        } else if (prevPrice >= prevTrend && price < trend) {
            series.signal(index, Signals.CROSS_BELOW);
        }
    }
}
```

### 3. Build and Deploy

```bash
# Build with dependencies
mvn clean package

# Deploy to MotiveWave
cp target/my-study.jar ~/Documents/MotiveWave/studies/
```

## Core Components

### DataSeriesBridge

Efficiently extracts data from MotiveWave DataSeries:

```java
// Extract with automatic buffer management
double[] prices = DataSeriesBridge.extractWindow(
    series, Values.CLOSE, currentIndex, windowSize);

// Use in try-finally to ensure cleanup
try {
    TransformResult result = transform.forward(prices);
    // Process result
} finally {
    DataSeriesBridge.release(prices);
}

// Extract multiple values efficiently
double[][] ohlc = DataSeriesBridge.extractOHLC(
    series, currentIndex, windowSize);
```

### TransformState

Manages incremental transforms for real-time performance:

```java
public class IncrementalStudy extends VectorWaveStudy {
    private final TransformState state = new TransformState();
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        // Automatically handles incremental vs full calculation
        TransformResult result = state.update(
            ctx.getDataSeries(), 
            index, 
            Values.CLOSE
        );
        
        // State tracks:
        // - Last calculated index
        // - Circular buffer of values
        // - Previous transform results
    }
}
```

### CoefficientStore

Multiple strategies for storing wavelet coefficients:

```java
// Standard: Full decomposition (D1-D8, A1-A4)
CoefficientStore.MappingStrategy.STANDARD.store(series, index, mlResult);

// Compact: Just trend and noise (VAL1, VAL2)
CoefficientStore.MappingStrategy.COMPACT.store(series, index, result);

// Energy: Energy at each level
CoefficientStore.MappingStrategy.ENERGY.store(series, index, mlResult);

// Custom mapping
CoefficientStore.storeCustom(series, index, result, mapping -> {
    mapping.put(Values.VAL1, result.getApproximation());
    mapping.put(Values.VAL2, Math.abs(result.getDetail()));
});
```

## Study Development

### Basic Study Template

```java
@StudyHeader(namespace = "com.mycompany", id = "MY_WAVELET")
public class MyWaveletStudy extends VectorWaveStudy {
    
    enum Inputs { PRICE }
    enum Values { SMOOTH, ROUGH }
    
    @Override
    protected void initialize(Defaults defaults) {
        super.initialize(defaults);
        
        // Study-specific initialization
        setMinBars(getWindowSize() * 2);
    }
    
    @Override
    protected Values getInputValue() {
        return getSettings().getInput(Inputs.PRICE, Values.CLOSE);
    }
    
    @Override
    protected void setupStudyParameters(StudyDescriptor desc) {
        desc.addInput(Inputs.PRICE, "Price", Values.CLOSE);
        desc.addOutput(Values.SMOOTH, "Smooth", Colors.BLUE);
        desc.addOutput(Values.ROUGH, "Rough", Colors.RED);
    }
    
    @Override
    protected void storeResults(DataSeries series, int index, 
                              TransformResult result) {
        series.setDouble(index, Values.SMOOTH, result.getApproximation());
        series.setDouble(index, Values.ROUGH, Math.abs(result.getDetail()));
    }
}
```

### Multi-Level Decomposition

```java
public class MultiLevelStudy extends VectorWaveStudy {
    
    @Override
    protected void setupStudyParameters(StudyDescriptor desc) {
        // Add parameter for decomposition levels
        desc.addIntegerParameter(
            Parameters.LEVELS, "Levels", 4, 1, 8);
            
        // Outputs for each level
        for (int i = 1; i <= 8; i++) {
            desc.addOutput(
                Values.valueOf("D" + i), 
                "Detail " + i, 
                getColorForLevel(i)
            );
        }
    }
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        int levels = getSettings().getInteger(Parameters.LEVELS);
        
        // Multi-level decomposition
        MultiLevelTransformResult result = 
            mlTransform.decompose(data, levels);
            
        // Store all levels
        CoefficientStore.storeMultiLevel(
            ctx.getDataSeries(), index, result);
    }
}
```

### Adaptive Wavelet Selection

```java
// Adaptive wavelet selection based on market conditions
public class AdaptiveWaveletStudy extends VectorWaveStudy {
    
    @Override
    protected String selectWavelet(DataSeries series, int index) {
        double volatility = calculateVolatility(series, index);
        
        if (volatility > HIGH_VOLATILITY_THRESHOLD) {
            return "db8"; // More coefficients for complex patterns
        } else {
            return "db4"; // Fewer coefficients for simpler patterns
        }
    }
}
```

## State-Based Signal System

The new state-based signal system provides rich market information to strategies while maintaining proper separation of concerns.

### Signal Generation (Study Side)

```java
public class StateAwareStudy extends Study {
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        // Calculate market metrics
        double currentSlope = calculateSlope(index);
        double currentMomentum = calculateMomentum(index);
        
        // Detect state changes
        List<StateChangeSignal> stateChanges = new ArrayList<>();
        
        // Check for slope direction changes
        if (currentSlope > 0 && lastSlope <= 0) {
            stateChanges.add(new StateChangeSignal(
                SignalType.SLOPE_TURNED_POSITIVE,
                lastSlope, currentSlope, 
                ctx.getDataSeries().getStartTime(index)
            ));
        }
        
        // Check for momentum threshold crosses
        if (Math.abs(currentMomentum) > threshold && 
            Math.abs(lastMomentum) <= threshold) {
            stateChanges.add(new StateChangeSignal(
                SignalType.MOMENTUM_THRESHOLD_EXCEEDED,
                lastMomentum, currentMomentum, 
                Math.abs(currentMomentum),
                ctx.getDataSeries().getStartTime(index)
            ));
        }
        
        // Emit state changes if any occurred
        if (!stateChanges.isEmpty() && ctx.getDataSeries().isBarComplete(index)) {
            ctx.signal(index, stateChanges);
        }
        
        // Update state for next iteration
        lastSlope = currentSlope;
        lastMomentum = currentMomentum;
    }
}
```

### Signal Handling (Strategy Side)

```java
public class StateAwareStrategy extends Study {
    
    @Override
    public void onSignal(OrderContext ctx, Object signal) {
        if (signal instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<StateChangeSignal> stateChanges = (List<StateChangeSignal>) signal;
            
            // Analyze state changes for trading decisions
            boolean slopePositive = stateChanges.stream()
                .anyMatch(s -> s.getType() == SignalType.SLOPE_TURNED_POSITIVE);
            boolean momentumStrong = stateChanges.stream()
                .anyMatch(s -> s.getType() == SignalType.MOMENTUM_THRESHOLD_EXCEEDED);
            
            // Conservative strategy: require both slope and momentum alignment
            if (slopePositive && momentumStrong && !hasPosition(ctx)) {
                enterLong(ctx);
            }
            
            // Aggressive strategy might act on slope change alone
            // Scalping strategy might focus only on momentum crosses
        }
    }
    
    private void enterLong(OrderContext ctx) {
        // Strategy-specific entry logic
        // Calculate position size, stops, targets
        // Submit bracket orders
    }
}
```

### Benefits of State-Based Signals

1. **Separation of Concerns**: Study reports what happened, strategy decides what to do
2. **Strategy Flexibility**: Different strategies can interpret the same signals differently
3. **Rich Information**: Strategies get magnitude, direction, and timing data
4. **Multiple Signals**: Can detect simultaneous state changes on one bar
5. **Better Testing**: Can unit test state detection separately from trading logic

```java
public class AdaptiveWaveletStudy extends VectorWaveStudy {
    
    enum MarketRegime { TRENDING, RANGING, VOLATILE }
    
    @Override
    protected Wavelet selectWavelet(DataContext ctx, int index) {
        MarketRegime regime = detectRegime(ctx, index);
        
        return switch (regime) {
            case TRENDING -> Daubechies.DB4;   // Smooth
            case RANGING -> Haar.INSTANCE;      // Responsive
            case VOLATILE -> Symlets.SYM4;      // Balanced
        };
    }
    
    private MarketRegime detectRegime(DataContext ctx, int index) {
        // Analyze recent price action
        double atr = ctx.getDataSeries().atr(index, 14);
        double adx = ctx.getDataSeries().adx(index, 14);
        
        if (adx > 25) return MarketRegime.TRENDING;
        if (atr > threshold) return MarketRegime.VOLATILE;
        return MarketRegime.RANGING;
    }
}
```

## Advanced Patterns

### 1. Volume-Weighted Transforms

```java
public class VolumeWaveletStudy extends VectorWaveStudy {
    
    @Override
    protected double[] extractData(DataSeries series, int start, int length) {
        double[] prices = new double[length];
        double[] volumes = new double[length];
        
        // Extract price and volume
        for (int i = 0; i < length; i++) {
            prices[i] = series.getDouble(start + i, Values.CLOSE);
            volumes[i] = series.getDouble(start + i, Values.VOLUME);
        }
        
        // Volume-weight the prices
        return volumeWeight(prices, volumes);
    }
    
    private double[] volumeWeight(double[] prices, double[] volumes) {
        double totalVolume = Arrays.stream(volumes).sum();
        double[] weighted = new double[prices.length];
        
        for (int i = 0; i < prices.length; i++) {
            weighted[i] = prices[i] * (volumes[i] / totalVolume);
        }
        
        return weighted;
    }
}
```

### 2. Multi-Timeframe Analysis

```java
public class MTFWaveletStudy extends Study {
    private final Map<BarSize, VectorWaveStudy> studies = new HashMap<>();
    
    @Override
    public void initialize(Defaults defaults) {
        // Create sub-studies for each timeframe
        for (BarSize barSize : getSelectedTimeframes()) {
            VectorWaveStudy study = new WaveletTrend();
            study.initialize(defaults);
            studies.put(barSize, study);
        }
    }
    
    @Override
    public void calculate(int index, DataContext ctx) {
        // Process each timeframe
        studies.forEach((barSize, study) -> {
            DataSeries mtfSeries = ctx.getDataSeries(barSize);
            study.calculate(mtfSeries.size() - 1, 
                          new MTFContext(mtfSeries));
        });
        
        // Combine results
        combineTimeframes(ctx, index);
    }
}
```

### 3. Denoising with Market Profile

```java
public class MarketAwareDenoiser extends VectorWaveStudy {
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        // Detect market profile
        MarketProfile profile = detectMarket(ctx);
        
        // Configure denoiser
        WaveletDenoiser denoiser = new WaveletDenoiser(
            getWavelet(),
            profile.getThresholdType(),
            profile.getThresholdRule()
        );
        
        // Denoise with market-specific settings
        double[] clean = denoiser.denoise(prices);
        
        // Store denoised prices
        storeDenoised(ctx.getDataSeries(), index, clean);
    }
    
    private MarketProfile detectMarket(DataContext ctx) {
        String symbol = ctx.getInstrument().getSymbol();
        
        if (symbol.startsWith("ES") || symbol.startsWith("NQ")) {
            return MarketProfile.FUTURES_INDEX;
        } else if (symbol.contains("BTC") || symbol.contains("ETH")) {
            return MarketProfile.CRYPTO_SPOT;
        } else if (symbol.contains("EUR") || symbol.contains("GBP")) {
            return MarketProfile.FOREX_MAJOR;
        }
        
        return MarketProfile.DEFAULT;
    }
}
```

## Performance Optimization

### 1. Buffer Pool Configuration

```java
public class OptimizedStudy extends VectorWaveStudy {
    
    @Override
    protected void initialize(Defaults defaults) {
        super.initialize(defaults);
        
        // Configure buffer pool for expected window sizes
        DataSeriesBridge.configurePool(
            64,   // 1-min scalping
            256,  // 5-min day trading
            1024  // Daily analysis
        );
    }
}
```

### 2. Parallel Historical Processing

```java
@Override
public void calculateHistorical(DataContext ctx) {
    int totalBars = ctx.getDataSeries().size();
    int calculated = ctx.getCalculatedBars();
    
    if (totalBars - calculated > 1000) {
        // Use parallel processing for large datasets
        ParallelProcessor.processRange(
            ctx, calculated, totalBars, 
            Runtime.getRuntime().availableProcessors()
        );
    } else {
        // Sequential for small datasets
        super.calculateHistorical(ctx);
    }
}
```

### 3. Caching Strategies

```java
public class CachedStudy extends VectorWaveStudy {
    private final Cache<Integer, TransformResult> cache = 
        Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();
    
    @Override
    protected TransformResult computeTransform(int index, double[] data) {
        return cache.get(index, k -> super.computeTransform(index, data));
    }
}
```

## Troubleshooting

### Common Issues

#### 1. Insufficient Data
```java
@Override
protected void calculate(int index, DataContext ctx) {
    if (index < getMinimumBars()) {
        // Not enough data - set NaN
        ctx.getDataSeries().setDouble(index, Values.VAL, Double.NaN);
        return;
    }
    // Normal calculation
}
```

#### 2. Memory Issues
```java
// Monitor buffer pool usage
DataSeriesBridge.getPoolStats().forEach((size, stats) -> {
    log.debug("Pool {}: {} active, {} idle", 
        size, stats.active, stats.idle);
});

// Force cleanup if needed
DataSeriesBridge.clearPools();
```

#### 3. Performance Degradation
```java
// Add performance monitoring
@Override
protected void calculate(int index, DataContext ctx) {
    long start = System.nanoTime();
    
    super.calculate(index, ctx);
    
    long elapsed = System.nanoTime() - start;
    if (elapsed > 1_000_000) { // > 1ms
        log.warn("Slow calculation at {}: {}ms", 
            index, elapsed / 1_000_000);
    }
}
```

### Debug Mode

Enable detailed logging:

```java
// In your study
@Override
protected void initialize(Defaults defaults) {
    super.initialize(defaults);
    
    // Enable debug mode
    if (getSettings().getBoolean(Parameters.DEBUG, false)) {
        VectorWaveStudy.setDebugMode(true);
        DataSeriesBridge.setDebugMode(true);
    }
}
```

## API Reference

### VectorWaveStudy Methods

```java
// Core methods to override
protected abstract Values getInputValue();
protected abstract void setupStudyParameters(StudyDescriptor desc);
protected abstract void storeResults(DataSeries series, int index, TransformResult result);

// Optional overrides
protected Wavelet selectWavelet(DataContext ctx, int index);
protected int getWindowSize();
protected BoundaryMode getBoundaryMode();
protected void onBarClose(DataContext ctx, int index);
protected void onTickUpdate(DataContext ctx, int index);
```

### DataSeriesBridge Methods

```java
// Data extraction
static double[] extract(DataSeries series, Values value, int start, int length);
static double[] extractWindow(DataSeries series, Values value, int index, int window);
static double[][] extractOHLC(DataSeries series, int start, int length);

// Buffer management  
static void release(double[] buffer);
static void configurePool(int... sizes);
static PoolStats getPoolStats();
```

### CoefficientStore Methods

```java
// Standard strategies
MappingStrategy.STANDARD.store(series, index, result);
MappingStrategy.COMPACT.store(series, index, result);
MappingStrategy.ENERGY.store(series, index, result);

// Custom storage
static void storeCustom(series, index, result, mapper);
static void storeWithScaling(series, index, result, scale);
static void storeMultiLevel(series, index, mlResult);
```

## Best Practices Summary

1. **Always release buffers** in finally blocks
2. **Use incremental updates** for real-time data
3. **Configure buffer pools** for your window sizes
4. **Handle insufficient data** gracefully
5. **Use appropriate storage strategies**
6. **Profile performance** in development
7. **Test with different market conditions**
8. **Document your wavelet choices**

## Next Steps

- Explore the [examples](examples/motivewave/) directory
- Read the [performance guide](PERFORMANCE.md)
- Join our [community forum](https://forum.prophetizo.com)
- Contribute your studies back to the community!