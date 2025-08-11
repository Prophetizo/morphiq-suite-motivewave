# VectorWave MotiveWave Module Specification

## Module Structure

Add a new `vectorwave-motivewave` module to VectorWave that provides seamless integration with MotiveWave SDK.

```
vectorwave/
├── vectorwave-core/          # Existing core module
├── vectorwave-benchmark/     # Existing benchmark module
└── vectorwave-motivewave/    # New MotiveWave integration module
    ├── pom.xml
    └── src/main/java/com/prophetizo/vectorwave/motivewave/
        ├── bridge/           # Data bridge layer
        ├── realtime/         # Real-time optimizations
        ├── storage/          # Coefficient storage strategies
        ├── studies/          # Base study classes
        └── utils/            # Utilities and helpers
```

## Core Components

### 1. Data Bridge (`bridge/DataSeriesBridge.java`)

```java
package com.prophetizo.vectorwave.motivewave.bridge;

import com.motivewave.platform.sdk.common.DataSeries;
import com.motivewave.platform.sdk.common.Enums.Values;
import com.prophetizo.vectorwave.core.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance bridge between MotiveWave DataSeries and VectorWave
 */
public class DataSeriesBridge {
    
    // Thread-local buffer pools for zero allocation in hot paths
    private static final ThreadLocal<BufferPool> BUFFER_POOLS = 
        ThreadLocal.withInitial(BufferPool::new);
    
    /**
     * Extract data with automatic buffer management
     */
    public static double[] extract(DataSeries series, Values value, 
                                 int start, int length) {
        BufferPool pool = BUFFER_POOLS.get();
        double[] buffer = pool.acquire(length);
        
        // Bulk extraction with bounds checking
        for (int i = 0; i < length; i++) {
            buffer[i] = series.getDouble(start + i, value);
        }
        
        return buffer;
    }
    
    /**
     * Auto-release buffer after use
     */
    public static void release(double[] buffer) {
        BUFFER_POOLS.get().release(buffer);
    }
    
    /**
     * Extract with automatic windowing for real-time
     */
    public static double[] extractWindow(DataSeries series, Values value,
                                       int currentIndex, int windowSize) {
        int start = Math.max(0, currentIndex - windowSize + 1);
        int actualSize = currentIndex - start + 1;
        return extract(series, value, start, actualSize);
    }
}
```

### 2. Real-time State Management (`realtime/TransformState.java`)

```java
package com.prophetizo.vectorwave.motivewave.realtime;

import com.prophetizo.vectorwave.core.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages transform state for incremental calculations
 */
public class TransformState {
    private final Map<String, WindowState> states = new ConcurrentHashMap<>();
    private final WaveletTransform transform;
    
    private static class WindowState {
        final CircularBuffer buffer;
        TransformResult lastResult;
        int lastIndex = -1;
        long lastTimestamp = -1;
        
        WindowState(int size) {
            this.buffer = new CircularBuffer(size);
        }
    }
    
    /**
     * Get or create state for a specific series/value combination
     */
    public TransformResult updateIncremental(String key, int index, 
                                           double newValue, long timestamp) {
        WindowState state = states.computeIfAbsent(key, 
            k -> new WindowState(defaultWindowSize));
        
        // Check if we can do incremental update
        if (state.lastIndex == index - 1 && 
            timestamp - state.lastTimestamp < MAX_GAP_MS) {
            // Incremental update
            state.buffer.add(newValue);
            state.lastResult = transform.incrementalForward(
                state.buffer.toArray(), state.lastResult);
        } else {
            // Full recalculation needed
            state.buffer.clear();
            state.buffer.fill(/* get historical data */);
            state.lastResult = transform.forward(state.buffer.toArray());
        }
        
        state.lastIndex = index;
        state.lastTimestamp = timestamp;
        return state.lastResult;
    }
}
```

### 3. Coefficient Storage (`storage/CoefficientStore.java`)

```java
package com.prophetizo.vectorwave.motivewave.storage;

import com.motivewave.platform.sdk.common.DataSeries;
import com.motivewave.platform.sdk.common.Enums.Values;

/**
 * Efficient coefficient storage strategies for MotiveWave
 */
public class CoefficientStore {
    
    /**
     * Pre-defined mapping strategies
     */
    public enum MappingStrategy {
        STANDARD {
            @Override
            public void store(DataSeries series, int index, 
                            MultiLevelTransformResult result) {
                // D1-D8 for details, A1-A4 for approximations
                for (int i = 0; i < Math.min(result.getLevels(), 8); i++) {
                    double[] detail = result.getDetailCoeffsAtLevel(i);
                    series.setDouble(index, Values.valueOf("D" + (i+1)), 
                                   detail[detail.length - 1]);
                }
            }
        },
        
        COMPACT {
            @Override
            public void store(DataSeries series, int index,
                            MultiLevelTransformResult result) {
                // Store only most significant coefficients
                double[] approx = result.getApproximationCoeffs();
                double[] d1 = result.getDetailCoeffsAtLevel(0);
                series.setDouble(index, Values.VAL1, approx[approx.length - 1]);
                series.setDouble(index, Values.VAL2, d1[d1.length - 1]);
            }
        },
        
        ENERGY {
            @Override
            public void store(DataSeries series, int index,
                            MultiLevelTransformResult result) {
                // Store energy at each level
                for (int i = 0; i < result.getLevels(); i++) {
                    double energy = calculateEnergy(result.getDetailCoeffsAtLevel(i));
                    series.setDouble(index, Values.valueOf("VAL" + (i+1)), energy);
                }
            }
        };
        
        public abstract void store(DataSeries series, int index,
                                 MultiLevelTransformResult result);
    }
    
    /**
     * Smart storage based on study requirements
     */
    public static void storeAdaptive(DataSeries series, int index,
                                   TransformResult result,
                                   StudyRequirements requirements) {
        if (requirements.needsAllCoefficients()) {
            storeComplete(series, index, result);
        } else if (requirements.needsEnergyOnly()) {
            storeEnergy(series, index, result);
        } else {
            storeCompact(series, index, result);
        }
    }
}
```

### 4. Base Study Classes (`studies/VectorWaveStudy.java`)

```java
package com.prophetizo.vectorwave.motivewave.studies;

import com.motivewave.platform.sdk.study.Study;
import com.prophetizo.vectorwave.core.*;

/**
 * Base class for all VectorWave-based studies
 */
public abstract class VectorWaveStudy extends Study {
    
    protected TransformState transformState;
    protected DataSeriesBridge dataBridge;
    protected CoefficientStore coeffStore;
    
    // Common parameters
    protected Wavelet wavelet;
    protected int windowSize;
    protected BoundaryMode boundaryMode;
    
    @Override
    public void initialize(Defaults defaults) {
        // Initialize components
        transformState = new TransformState(createTransform());
        dataBridge = new DataSeriesBridge();
        coeffStore = new CoefficientStore();
        
        // Setup common parameters
        setupCommonParameters(getDescriptor());
        
        // Let subclass add specific parameters
        setupStudyParameters(getDescriptor());
    }
    
    @Override
    public void calculate(int index, DataContext ctx) {
        // Efficient calculation pattern
        DataSeries series = ctx.getDataSeries();
        
        // Skip if already calculated
        if (series.isCalculated(index, getSettings())) return;
        
        // Extract data efficiently
        double[] data = dataBridge.extractWindow(
            series, getInputValue(), index, windowSize);
        
        try {
            // Perform transform
            TransformResult result = calculateTransform(data, index);
            
            // Store results
            storeResults(series, index, result);
            
        } finally {
            // Always release buffer
            dataBridge.release(data);
        }
    }
    
    /**
     * Optimized batch calculation for historical data
     */
    @Override
    public void calculateBatch(DataContext ctx, int startIndex, int endIndex) {
        // Parallel processing for large ranges
        if (endIndex - startIndex > PARALLEL_THRESHOLD) {
            IntStream.range(startIndex, endIndex)
                .parallel()
                .forEach(i -> calculate(i, ctx));
        } else {
            // Sequential for small ranges
            super.calculateBatch(ctx, startIndex, endIndex);
        }
    }
    
    protected abstract Values getInputValue();
    protected abstract void setupStudyParameters(StudyDescriptor desc);
    protected abstract void storeResults(DataSeries series, int index, 
                                       TransformResult result);
}
```

### 5. Utilities (`utils/MotiveWaveUtils.java`)

```java
package com.prophetizo.vectorwave.motivewave.utils;

/**
 * MotiveWave-specific utilities
 */
public class MotiveWaveUtils {
    
    /**
     * Convert MotiveWave time to milliseconds
     */
    public static long toMillis(long motiveWaveTime) {
        return motiveWaveTime / 1000;
    }
    
    /**
     * Smart window size selection based on bar size
     */
    public static int getOptimalWindowSize(BarSize barSize) {
        return switch (barSize.getIntervalMinutes()) {
            case 1 -> 64;      // 1 hour of 1-min bars
            case 5 -> 60;      // 5 hours of 5-min bars
            case 15 -> 96;     // 1 day of 15-min bars
            case 60 -> 120;    // 5 days of hourly bars
            case 240 -> 180;   // 30 days of 4-hour bars
            case 1440 -> 252;  // 1 year of daily bars
            default -> 100;    // Generic default
        };
    }
    
    /**
     * Wavelet selection helper
     */
    public static Wavelet getWaveletForUseCase(UseCase useCase) {
        return switch (useCase) {
            case SCALPING -> Haar.INSTANCE;        // Fast response
            case DAY_TRADING -> Daubechies.DB2;    // Balanced
            case SWING_TRADING -> Daubechies.DB4;  // Smoother
            case POSITION_TRADING -> Coiflets.COIF3; // Very smooth
            case NOISE_REDUCTION -> Symlets.SYM4;  // Good denoising
        };
    }
}
```

## Maven Configuration

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>com.prophetizo</groupId>
        <artifactId>vectorwave-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    
    <artifactId>vectorwave-motivewave</artifactId>
    <name>VectorWave MotiveWave Integration</name>
    
    <dependencies>
        <!-- VectorWave Core -->
        <dependency>
            <groupId>com.prophetizo</groupId>
            <artifactId>vectorwave-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        
        <!-- MotiveWave SDK -->
        <dependency>
            <groupId>com.motivewave</groupId>
            <artifactId>motivewave-sdk</artifactId>
            <version>20230627</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <!-- Shade plugin for fat JAR -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <configuration>
                    <createDependencyReducedPom>false</createDependencyReducedPom>
                    <filters>
                        <filter>
                            <artifact>*:*</artifact>
                            <excludes>
                                <exclude>META-INF/*.SF</exclude>
                                <exclude>META-INF/*.DSA</exclude>
                                <exclude>META-INF/*.RSA</exclude>
                            </excludes>
                        </filter>
                    </filters>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

## Usage Example

```java
import com.prophetizo.vectorwave.motivewave.studies.VectorWaveStudy;

public class SimpleWaveletIndicator extends VectorWaveStudy {
    
    @Override
    protected Values getInputValue() {
        return Values.CLOSE;
    }
    
    @Override
    protected void setupStudyParameters(StudyDescriptor desc) {
        desc.setLabelSettings(InputLabels.INPUT);
        desc.addOutput(Values.VAL1, "Wavelet Trend", Colors.BLUE);
        desc.addOutput(Values.VAL2, "Wavelet Detail", Colors.RED);
    }
    
    @Override
    protected void storeResults(DataSeries series, int index, 
                              TransformResult result) {
        // Use compact storage
        CoefficientStore.MappingStrategy.COMPACT.store(series, index, result);
    }
}
```

This module design provides:
1. **Zero-copy operations** where possible
2. **Incremental updates** for real-time performance
3. **Flexible storage strategies** for different study types
4. **Built-in optimizations** for MotiveWave's patterns
5. **Easy-to-use base classes** for rapid development