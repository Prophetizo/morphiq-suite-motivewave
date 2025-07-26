# Morphiq Suite Migration Guide: JWave to VectorWave

## Overview

This guide provides step-by-step instructions for migrating the Morphiq Suite from JWave-Pro to VectorWave. Since this is an early-stage project, we'll focus on a clean migration that leverages VectorWave's MotiveWave-optimized features.

## Migration Strategy

### Approach
1. **Direct replacement** - No compatibility layer needed
2. **Leverage VectorWave's MotiveWave module** - Purpose-built integration
3. **Improve performance** - Use incremental transforms and buffer pooling
4. **Simplify code** - Remove workarounds needed for JWave

## Step-by-Step Migration

### Step 1: Update Dependencies

#### Parent POM (`pom.xml`)
```xml
<!-- Remove JWave -->
<!-- DELETE THIS:
<dependency>
    <groupId>com.prophetizo</groupId>
    <artifactId>jwave-pro</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
-->

<!-- Add VectorWave -->
<dependency>
    <groupId>com.prophetizo</groupId>
    <artifactId>vectorwave-motivewave</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Step 2: Migrate WaveletType Enum

#### Current (`morphiq-core/src/main/java/com/prophetizo/wavelets/WaveletType.java`)
```java
package com.prophetizo.wavelets;

import jwave.transforms.wavelets.Wavelet;
import jwave.transforms.wavelets.haar.Haar1;
// ... other JWave imports

public enum WaveletType {
    HAAR("Haar", "Simplest wavelet, good for breakout detection"),
    DAUBECHIES2("Daubechies 2", "Fast response, good for HFT"),
    // ...
}
```

#### Migrated Version
```java
package com.prophetizo.wavelets;

import com.prophetizo.vectorwave.core.Wavelet;
import com.prophetizo.vectorwave.core.wavelets.*;

public enum WaveletType {
    HAAR("Haar", "Simplest wavelet, good for breakout detection") {
        @Override
        public Wavelet getWavelet() {
            return Haar.INSTANCE;
        }
    },
    DAUBECHIES2("Daubechies 2", "Fast response, good for HFT") {
        @Override
        public Wavelet getWavelet() {
            return Daubechies.DB2;
        }
    },
    DAUBECHIES4("Daubechies 4", "General purpose, good balance") {
        @Override
        public Wavelet getWavelet() {
            return Daubechies.DB4;
        }
    },
    DAUBECHIES6("Daubechies 6", "Smoother, better noise reduction") {
        @Override
        public Wavelet getWavelet() {
            return Daubechies.DB6; // Now available in VectorWave
        }
    },
    SYMLET4("Symlet 4", "Minimal phase distortion") {
        @Override
        public Wavelet getWavelet() {
            return Symlets.SYM4;
        }
    },
    SYMLET8("Symlet 8", "Better trend preservation") {
        @Override
        public Wavelet getWavelet() {
            return Symlets.SYM8; // Now available in VectorWave
        }
    },
    COIFLET3("Coiflet 3", "Excellent for position trading") {
        @Override
        public Wavelet getWavelet() {
            return Coiflets.COIF3;
        }
    };
    
    private final String displayName;
    private final String description;
    
    WaveletType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public abstract Wavelet getWavelet();
    
    // Rest of the enum remains the same
}
```

### Step 3: Migrate WaveletAnalyzer

#### Current (`morphiq-core/src/main/java/com/prophetizo/wavelets/WaveletAnalyzer.java`)
```java
package com.prophetizo.wavelets;

import jwave.transforms.FastWaveletTransform;
import jwave.transforms.wavelets.Wavelet;
import jwave.transforms.MODWTTransform;

public class WaveletAnalyzer {
    private final FastWaveletTransform fwtTransform;
    private final MODWTTransform modwtTransform;
    
    public double[] performForwardDWT(double[] prices) {
        return fwtTransform.forward(prices);
    }
    
    public double[][] performForwardMODWT(double[] prices, int maxLevel) {
        return modwtTransform.forwardMODWT(prices, maxLevel);
    }
}
```

#### Migrated Version
```java
package com.prophetizo.wavelets;

import com.prophetizo.vectorwave.core.*;
import com.prophetizo.vectorwave.motivewave.realtime.TransformState;

public class WaveletAnalyzer {
    private final WaveletTransform transform;
    private final MultiLevelWaveletTransform mlTransform;
    private final TransformState realtimeState;
    
    public WaveletAnalyzer(WaveletType waveletType) {
        Wavelet wavelet = waveletType.getWavelet();
        this.transform = new WaveletTransform(wavelet, BoundaryMode.PERIODIC);
        this.mlTransform = new MultiLevelWaveletTransform(wavelet, BoundaryMode.PERIODIC);
        this.realtimeState = new TransformState(transform);
    }
    
    /**
     * Single-level DWT with VectorWave's improved API
     */
    public TransformResult performForwardDWT(double[] prices) {
        return transform.forward(prices);
    }
    
    /**
     * Multi-level decomposition (replacement for MODWT)
     */
    public MultiLevelTransformResult performMultiLevel(double[] prices, int maxLevel) {
        return mlTransform.decompose(prices, maxLevel);
    }
    
    /**
     * New: Incremental transform for real-time tick data
     */
    public TransformResult updateRealtime(String instrumentKey, int index, 
                                         double newPrice, long timestamp) {
        return realtimeState.updateIncremental(instrumentKey, index, newPrice, timestamp);
    }
    
    /**
     * Backward compatibility helper - converts to old format
     */
    @Deprecated
    public double[][] performForwardMODWT(double[] prices, int maxLevel) {
        MultiLevelTransformResult result = performMultiLevel(prices, maxLevel);
        return convertToLegacyFormat(result);
    }
    
    private double[][] convertToLegacyFormat(MultiLevelTransformResult result) {
        int levels = result.getLevels();
        double[][] legacy = new double[levels + 1][];
        
        for (int i = 0; i < levels; i++) {
            legacy[i] = result.getDetailCoeffsAtLevel(i);
        }
        legacy[levels] = result.getApproximationCoeffs();
        
        return legacy;
    }
}
```

### Step 4: Migrate WaveletDenoiser

#### Current (`morphiq-core/src/main/java/com/prophetizo/wavelets/WaveletDenoiser.java`)
```java
package com.prophetizo.wavelets;

public class WaveletDenoiser {
    private final WaveletAnalyzer analyzer;
    
    public double[] denoise(double[] signal, ThresholdType thresholdType, 
                          NoiseLevel noiseLevel) {
        // Complex denoising logic
    }
}
```

#### Migrated Version
```java
package com.prophetizo.wavelets;

import com.prophetizo.vectorwave.core.*;
import com.prophetizo.vectorwave.motivewave.storage.MarketProfile;

public class WaveletDenoiser {
    private final com.prophetizo.vectorwave.core.WaveletDenoiser coreDenoiser;
    private final MarketAwareDenoiser marketDenoiser;
    
    public WaveletDenoiser(WaveletType waveletType) {
        Wavelet wavelet = waveletType.getWavelet();
        this.coreDenoiser = new com.prophetizo.vectorwave.core.WaveletDenoiser(
            wavelet, 
            ThresholdType.SOFT,
            ThresholdRule.SURE
        );
        this.marketDenoiser = new MarketAwareDenoiser(wavelet);
    }
    
    /**
     * Simple denoising
     */
    public double[] denoise(double[] signal) {
        return coreDenoiser.denoise(signal);
    }
    
    /**
     * Market-aware denoising (new capability)
     */
    public double[] denoiseForMarket(double[] signal, String symbol) {
        MarketProfile profile = detectMarketProfile(symbol);
        return marketDenoiser.denoise(signal, profile);
    }
    
    private MarketProfile detectMarketProfile(String symbol) {
        if (symbol.startsWith("ES") || symbol.startsWith("NQ")) {
            return MarketProfile.FUTURES_INDEX;
        } else if (symbol.contains("BTC") || symbol.contains("ETH")) {
            return MarketProfile.CRYPTO_SPOT;
        }
        return MarketProfile.DEFAULT;
    }
}
```

### Step 5: Migrate MotiveWave Studies

#### Current AutoWavelets Study
```java
public class AutoWavelets extends Study {
    
    @Override
    public void calculate(int index, DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        
        // Collect price data
        double[] prices = new double[windowSize];
        for (int i = 0; i < windowSize; i++) {
            prices[i] = series.getDouble(index - windowSize + i + 1, Values.CLOSE);
        }
        
        // Perform MODWT
        double[][] coefficients = analyzer.performForwardMODWT(prices, levels);
        
        // Store coefficients
        for (int level = 0; level < levels; level++) {
            series.setDouble(index, Values.valueOf("D" + (level + 1)), 
                           coefficients[level][coefficients[level].length - 1]);
        }
    }
}
```

#### Migrated Version Using VectorWaveStudy Base Class
```java
import com.prophetizo.vectorwave.motivewave.studies.VectorWaveStudy;
import com.prophetizo.vectorwave.motivewave.storage.CoefficientStore;

@StudyHeader(
    namespace = "com.prophetizo.studies",
    id = "AUTO_WAVELETS",
    name = "Auto Wavelets (VectorWave)"
)
public class AutoWavelets extends VectorWaveStudy {
    
    enum Parameters { LEVELS }
    
    @Override
    protected Values getInputValue() {
        return Values.CLOSE;
    }
    
    @Override
    protected void setupStudyParameters(StudyDescriptor desc) {
        desc.addIntegerParameter(Parameters.LEVELS, "Decomposition Levels", 4, 1, 8);
        
        // Add outputs for each level
        for (int i = 1; i <= 8; i++) {
            desc.addOutput(Values.valueOf("D" + i), 
                         "Detail Level " + i, 
                         getColorForLevel(i));
        }
        desc.addOutput(Values.A1, "Approximation", Colors.BLUE);
    }
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        if (index < getWindowSize()) return;
        
        DataSeries series = ctx.getDataSeries();
        int levels = getSettings().getInteger(Parameters.LEVELS);
        
        // VectorWave handles data extraction efficiently
        double[] data = DataSeriesBridge.extractWindow(
            series, getInputValue(), index, getWindowSize());
        
        try {
            // Multi-level decomposition
            MultiLevelTransformResult result = 
                mlTransform.decompose(data, levels);
            
            // Store using built-in mapper
            CoefficientStore.storeMultiLevel(series, index, result);
            
        } finally {
            DataSeriesBridge.release(data);
        }
    }
    
    private Color getColorForLevel(int level) {
        // Color gradient from red (high freq) to blue (low freq)
        float hue = 0.0f + (240.0f / 8) * (level - 1);
        return Color.getHSBColor(hue / 360.0f, 0.8f, 0.8f);
    }
}
```

### Step 6: Update Build Configuration

#### Add VectorWave Profile to Parent POM
```xml
<profiles>
    <profile>
        <id>vectorwave-optimization</id>
        <activation>
            <activeByDefault>true</activeByDefault>
        </activation>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <configuration>
                        <compilerArgs>
                            <arg>--add-modules</arg>
                            <arg>jdk.incubator.vector</arg>
                            <arg>--enable-preview</arg>
                        </compilerArgs>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

## Testing the Migration

### 1. Unit Test Updates

```java
@Test
public void testWaveletTransform() {
    // Old JWave test
    FastWaveletTransform fwt = new FastWaveletTransform(new Daubechies4());
    double[] result = fwt.forward(testData);
    
    // New VectorWave test
    WaveletTransform transform = new WaveletTransform(Daubechies.DB4);
    TransformResult result = transform.forward(testData);
    
    // Verify results are equivalent
    assertArrayEquals(
        extractCoefficients(result), 
        oldResult, 
        1e-10
    );
}
```

### 2. Performance Comparison

```java
@Benchmark
public void benchmarkJWave() {
    fwt.forward(data);
}

@Benchmark  
public void benchmarkVectorWave() {
    transform.forward(data);
}

// Expected results:
// JWave:      ~1200 ns/op
// VectorWave: ~294 ns/op (4x faster)
```

## Migration Checklist

- [ ] Update Maven dependencies
- [ ] Migrate WaveletType enum
- [ ] Update WaveletAnalyzer class
- [ ] Update WaveletDenoiser class
- [ ] Migrate AutoWavelets study
- [ ] Migrate DenoisedTrendFollowing study
- [ ] Update unit tests
- [ ] Run performance benchmarks
- [ ] Test with live data in MotiveWave
- [ ] Update documentation

## Benefits After Migration

1. **Performance**: 4x faster transforms
2. **Real-time**: Incremental updates for tick data
3. **Memory**: Zero-copy operations with DataSeries
4. **Features**: Market-aware denoising
5. **Simplicity**: Less boilerplate with base classes

## Troubleshooting

### Issue: Non-power-of-two data lengths
```java
// VectorWave handles this automatically
// No need for manual padding like with JWave
```

### Issue: Different coefficient ordering
```java
// Use the compatibility helper during transition
double[][] legacy = analyzer.performForwardMODWT(prices, levels);
```

### Issue: Missing wavelets
```java
// DB6 and SYM8 are now available in VectorWave
// No need for fallbacks
```

## Next Steps

1. Complete the migration following this guide
2. Run comprehensive tests
3. Profile performance improvements
4. Consider adding new features:
   - Real-time incremental transforms
   - Market-specific denoising
   - Multi-timeframe analysis
5. Update user documentation