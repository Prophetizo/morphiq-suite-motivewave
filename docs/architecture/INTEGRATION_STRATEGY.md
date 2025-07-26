# MotiveWave-VectorWave Integration Strategy

## Overview

This document outlines the integration layer between MotiveWave SDK and VectorWave, focusing on efficient data handling, real-time performance, and seamless study development.

## Core Integration Architecture

### 1. Data Bridge Layer

The primary challenge is efficiently moving data between MotiveWave's `DataSeries` and VectorWave's array-based transforms.

```java
package com.prophetizo.morphiq.integration;

import com.motivewave.platform.sdk.common.DataSeries;
import com.motivewave.platform.sdk.common.Enums.Values;
import com.prophetizo.vectorwave.core.*;

/**
 * Efficient bridge between MotiveWave DataSeries and VectorWave transforms
 */
public class DataSeriesBridge {
    
    // Pre-allocated buffers for common window sizes
    private static final ThreadLocal<BufferPool> BUFFER_POOL = 
        ThreadLocal.withInitial(() -> new BufferPool(64, 128, 256, 512, 1024));
    
    /**
     * Extract price data from DataSeries with minimal allocation
     */
    public static double[] extractPrices(DataSeries series, Values value, 
                                       int startIdx, int length) {
        double[] buffer = BUFFER_POOL.get().acquire(length);
        
        for (int i = 0; i < length; i++) {
            buffer[i] = series.getDouble(startIdx + i, value);
        }
        
        return buffer;
    }
    
    /**
     * Streaming extraction for large datasets
     */
    public static DoubleStream streamPrices(DataSeries series, Values value,
                                          int startIdx, int endIdx) {
        return IntStream.range(startIdx, endIdx)
            .mapToDouble(i -> series.getDouble(i, value));
    }
    
    /**
     * Direct coefficient writing without intermediate arrays
     */
    public static void writeCoefficients(DataSeries series, int index,
                                       TransformResult result,
                                       Values approxValue, Values detailValue) {
        // Get last coefficient (most recent)
        double[] approx = result.approximationCoeffs();
        double[] detail = result.detailCoeffs();
        
        series.setDouble(index, approxValue, approx[approx.length - 1]);
        series.setDouble(index, detailValue, detail[detail.length - 1]);
    }
}
```

### 2. Real-time Transform Manager

Optimized for MotiveWave's tick-by-tick calculation model:

```java
package com.prophetizo.morphiq.integration;

/**
 * Manages wavelet transforms optimized for MotiveWave's calculation model
 */
public class RealtimeTransformManager {
    private final Map<String, SlidingWindowState> windowStates;
    private final WaveletTransform transform;
    
    /**
     * State for each DataSeries + Value combination
     */
    private static class SlidingWindowState {
        final CircularBuffer buffer;
        final int windowSize;
        TransformResult lastResult;
        int lastCalculatedIndex = -1;
        
        boolean needsFullRecalc(int currentIndex) {
            return currentIndex != lastCalculatedIndex + 1;
        }
    }
    
    /**
     * Optimized for MotiveWave's forward-only calculation pattern
     */
    public TransformResult calculate(DataSeries series, int index, 
                                   Values priceValue, int windowSize) {
        String key = series.getInstrument().getSymbol() + ":" + priceValue;
        SlidingWindowState state = windowStates.computeIfAbsent(key, 
            k -> new SlidingWindowState(windowSize));
        
        if (state.needsFullRecalc(index)) {
            // Gap in calculation - need full window
            return fullTransform(series, index, priceValue, windowSize);
        } else {
            // Incremental update - O(1) operation
            double newValue = series.getDouble(index, priceValue);
            return incrementalTransform(state, newValue);
        }
    }
    
    /**
     * Batch calculation for historical data
     */
    public void calculateRange(DataSeries series, int startIdx, int endIdx,
                             Values priceValue, Values approxOut, Values detailOut) {
        // Use parallel processing for historical calculations
        IntStream.range(startIdx, endIdx).parallel().forEach(idx -> {
            TransformResult result = calculate(series, idx, priceValue, windowSize);
            DataSeriesBridge.writeCoefficients(series, idx, result, approxOut, detailOut);
        });
    }
}
```

### 3. MotiveWave Study Base Classes

Abstract base classes that handle VectorWave integration:

```java
package com.prophetizo.morphiq.studies;

import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyDescriptor;

/**
 * Base class for wavelet-based studies using VectorWave
 */
public abstract class WaveletStudy extends Study {
    
    protected RealtimeTransformManager transformManager;
    protected Wavelet wavelet;
    protected int windowSize;
    
    @Override
    public void initialize(Defaults defaults) {
        // Study configuration
        StudyDescriptor desc = createDescriptor();
        
        // Wavelet selection
        desc.addParameter(new Parameter("Wavelet", "wavelet", 
            WaveletType.values(), WaveletType.DAUBECHIES4));
            
        // Window size with smart defaults
        desc.addParameter(new IntegerParameter("Window Size", "windowSize", 
            getDefaultWindowSize(), 32, 2048));
            
        // Output configuration
        configureOutputs(desc);
    }
    
    @Override
    public void onBarUpdate(DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        int index = series.size() - 1;
        
        // Efficient calculation using transform manager
        TransformResult result = transformManager.calculate(
            series, index, getInputValue(), windowSize);
            
        // Let subclass handle the results
        processTransformResult(ctx, index, result);
    }
    
    protected abstract void configureOutputs(StudyDescriptor desc);
    protected abstract void processTransformResult(DataContext ctx, 
                                                 int index, 
                                                 TransformResult result);
    protected abstract int getDefaultWindowSize();
}
```

### 4. Coefficient Storage Strategy

Optimized mapping between VectorWave coefficients and MotiveWave Values:

```java
package com.prophetizo.morphiq.integration;

/**
 * Maps wavelet coefficients to MotiveWave's Values enum efficiently
 */
public class CoefficientMapper {
    
    // Standard mappings for multi-level decomposition
    public static class StandardMappings {
        // Detail coefficients: D1-D8
        public static final Values[] DETAILS = {
            Values.D1, Values.D2, Values.D3, Values.D4,
            Values.D5, Values.D6, Values.D7, Values.D8
        };
        
        // Approximation coefficients: A1-A4  
        public static final Values[] APPROXIMATIONS = {
            Values.A1, Values.A2, Values.A3, Values.A4
        };
        
        // For studies needing more outputs
        public static final Values[] EXTENDED = {
            Values.VAL1, Values.VAL2, Values.VAL3, Values.VAL4,
            Values.VAL5, Values.VAL6, Values.VAL7, Values.VAL8
        };
    }
    
    /**
     * Multi-level decomposition storage
     */
    public static void storeMultiLevel(DataSeries series, int index,
                                     MultiLevelTransformResult result) {
        int levels = Math.min(result.getLevels(), StandardMappings.DETAILS.length);
        
        // Store detail coefficients by level
        for (int level = 0; level < levels; level++) {
            double[] details = result.getDetailCoeffsAtLevel(level);
            double lastCoeff = details[details.length - 1];
            series.setDouble(index, StandardMappings.DETAILS[level], lastCoeff);
        }
        
        // Store final approximation
        double[] approx = result.getApproximationCoeffs();
        series.setDouble(index, StandardMappings.APPROXIMATIONS[0], 
                       approx[approx.length - 1]);
    }
    
    /**
     * Store with custom magnitude scaling for visualization
     */
    public static void storeWithScaling(DataSeries series, int index,
                                      TransformResult result,
                                      double scaleFactor) {
        double[] approx = result.approximationCoeffs();
        double[] detail = result.detailCoeffs();
        
        // Scale for better visualization in MotiveWave
        double scaledApprox = approx[approx.length - 1] * scaleFactor;
        double scaledDetail = detail[detail.length - 1] * scaleFactor;
        
        series.setDouble(index, Values.A1, scaledApprox);
        series.setDouble(index, Values.D1, scaledDetail);
    }
}
```

### 5. Performance Optimizations

```java
package com.prophetizo.morphiq.integration;

/**
 * Performance optimizations specific to MotiveWave environment
 */
public class PerformanceOptimizer {
    
    /**
     * Adaptive processing based on data characteristics
     */
    public static class AdaptiveProcessor {
        private static final int REALTIME_THRESHOLD = 1000;
        private static final int BATCH_SIZE = 256;
        
        public void processAdaptive(DataContext ctx, WaveletProcessor processor) {
            DataSeries series = ctx.getDataSeries();
            int totalBars = series.size();
            int calculated = ctx.getCalculatedBars();
            int toProcess = totalBars - calculated;
            
            if (toProcess < REALTIME_THRESHOLD) {
                // Process incrementally for recent data
                processIncremental(ctx, processor, calculated, totalBars);
            } else {
                // Batch process historical data
                processBatch(ctx, processor, calculated, totalBars);
            }
        }
        
        private void processBatch(DataContext ctx, WaveletProcessor processor,
                                int start, int end) {
            // Process in parallel batches
            int numBatches = (end - start + BATCH_SIZE - 1) / BATCH_SIZE;
            
            IntStream.range(0, numBatches).parallel().forEach(batch -> {
                int batchStart = start + batch * BATCH_SIZE;
                int batchEnd = Math.min(batchStart + BATCH_SIZE, end);
                processor.processBatch(ctx, batchStart, batchEnd);
            });
        }
    }
    
    /**
     * Memory-efficient buffer management
     */
    public static class BufferPool {
        private final Map<Integer, Queue<double[]>> pools = new ConcurrentHashMap<>();
        private final int[] sizes;
        
        public BufferPool(int... commonSizes) {
            this.sizes = commonSizes;
            for (int size : sizes) {
                pools.put(size, new ConcurrentLinkedQueue<>());
            }
        }
        
        public double[] acquire(int size) {
            // Try to get exact size first
            Queue<double[]> pool = pools.get(size);
            if (pool != null) {
                double[] buffer = pool.poll();
                if (buffer != null) return buffer;
            }
            
            // Find next larger size
            for (int poolSize : sizes) {
                if (poolSize >= size) {
                    pool = pools.get(poolSize);
                    double[] buffer = pool.poll();
                    if (buffer != null) return buffer;
                    return new double[poolSize];
                }
            }
            
            // Allocate new if no suitable size
            return new double[size];
        }
        
        public void release(double[] buffer) {
            if (buffer == null) return;
            
            int size = buffer.length;
            Queue<double[]> pool = pools.get(size);
            if (pool != null && pool.size() < 10) {
                Arrays.fill(buffer, 0); // Clear for reuse
                pool.offer(buffer);
            }
        }
    }
}
```

## Implementation Examples

### Example 1: Simple Wavelet Indicator

```java
public class WaveletTrendIndicator extends WaveletStudy {
    
    @Override
    protected void configureOutputs(StudyDescriptor desc) {
        desc.addOutput(Values.A1, "Wavelet Trend", Colors.BLUE, Plot.LINE, 2);
        desc.addOutput(Values.D1, "Wavelet Detail", Colors.RED, Plot.LINE, 1);
    }
    
    @Override
    protected void processTransformResult(DataContext ctx, int index, 
                                        TransformResult result) {
        // Direct storage of coefficients
        CoefficientMapper.storeWithScaling(
            ctx.getDataSeries(), index, result, 1.0);
    }
    
    @Override
    protected int getDefaultWindowSize() {
        return 64; // Good for intraday trading
    }
}
```

### Example 2: Multi-Level Decomposition

```java
public class MultiLevelWaveletStudy extends Study {
    private MultiLevelWaveletTransform mlTransform;
    
    @Override
    public void calculate(int index, DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        
        // Efficient multi-level calculation
        double[] prices = DataSeriesBridge.extractPrices(
            series, Values.CLOSE, Math.max(0, index - 255), 256);
            
        MultiLevelTransformResult result = mlTransform.decompose(prices, 4);
        
        // Store all levels
        CoefficientMapper.storeMultiLevel(series, index, result);
    }
}
```

## Next Steps

1. **Implement core integration classes** in VectorWave
2. **Create MotiveWave-specific optimizations** (buffer pools, incremental updates)
3. **Build example studies** demonstrating the integration
4. **Performance benchmarking** vs JWave implementation
5. **Documentation and tutorials** for study developers

This approach gives you a clean, efficient integration that leverages VectorWave's performance while working seamlessly with MotiveWave's architecture.