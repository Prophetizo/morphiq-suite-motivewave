# Multi-Wavelet Implementation Plan

## Overview
This document outlines the implementation plan for adding comprehensive wavelet support to the Morphiq Suite, expanding from the current 2 wavelets to the full 74+ available in JWave.

## Current Status
- **Implemented**: Daubechies4, Daubechies6 in AutoWavelets and DenoisedTrendFollowing
- **Available**: 68 discrete wavelets + 6 continuous wavelets (in develop branch)
- **Utilization**: 2.7% of available capabilities

## Phase 1: Infrastructure Setup

### 1.1 Wavelet Factory Pattern
Create a flexible factory system for wavelet selection:

```java
public interface WaveletFactory {
    Wavelet createWavelet(WaveletType type, int vanishingMoments);
    boolean supportsType(WaveletType type);
    WaveletCharacteristics getCharacteristics(WaveletType type);
}

public enum WaveletType {
    HAAR("Haar", "Sharp edge detection"),
    DAUBECHIES("Daubechies", "General purpose"),
    SYMLET("Symlet", "Near-symmetric"),
    COIFLET("Coiflet", "Smooth reconstruction"),
    MORLET("Morlet", "Cycle analysis"),
    MEXICAN_HAT("Mexican Hat", "Spike detection"),
    MEYER("Meyer", "Frequency separation"),
    BIORTHOGONAL("Biorthogonal", "Perfect reconstruction");
    
    private final String displayName;
    private final String description;
}
```

### 1.2 Wavelet Characteristics
Define metadata for intelligent wavelet selection:

```java
public class WaveletCharacteristics {
    private final int minVanishingMoments;
    private final int maxVanishingMoments;
    private final double typicalLag; // in bars
    private final boolean isComplex;
    private final boolean isOrthogonal;
    private final double computationalCost; // relative scale 1-10
    private final Set<TradingStrategy> recommendedStrategies;
}
```

### 1.3 Enhanced WaveletAnalyzer
Extend current implementation to support multiple wavelets:

```java
public class EnhancedWaveletAnalyzer {
    private final Map<WaveletType, Wavelet> waveletCache;
    private final WaveletFactory factory;
    
    public double[][] performMODWT(double[] data, int levels, WaveletType type) {
        Wavelet wavelet = waveletCache.computeIfAbsent(type, 
            t -> factory.createWavelet(t, getDefaultVanishingMoments(t)));
        return performTransform(data, levels, wavelet);
    }
    
    // Support for complex wavelets (Morlet)
    public Complex[][] performComplexMODWT(double[] data, int levels, ComplexWavelet wavelet) {
        // Implementation for complex-valued transforms
    }
}
```

## Phase 2: MotiveWave Integration

### 2.1 Settings Descriptor Enhancement
```java
public void initialize(Defaults defaults) {
    SettingsDescriptor settings = new SettingsDescriptor();
    
    // Wavelet Selection Tab
    SettingTab waveletTab = settings.addTab("Wavelet");
    
    // Primary wavelet selection
    EnumDescriptor<WaveletType> waveletType = new EnumDescriptor<>(
        "WAVELET_TYPE", "Wavelet Type", WaveletType.DAUBECHIES);
    waveletTab.addRow(waveletType);
    
    // Vanishing moments (dynamic based on wavelet type)
    IntegerDescriptor vanishingMoments = new IntegerDescriptor(
        "VANISHING_MOMENTS", "Vanishing Moments", 4, 1, 10);
    waveletTab.addRow(vanishingMoments);
    
    // Strategy-specific optimization
    BooleanDescriptor autoSelect = new BooleanDescriptor(
        "AUTO_SELECT", "Auto-Select Wavelet", false);
    waveletTab.addRow(autoSelect);
    
    // Add dependency to show/hide vanishing moments
    settings.addDependency(new WaveletDependency());
}
```

### 2.2 Dynamic UI Updates
```java
public class WaveletDependency extends InputDependency {
    @Override
    public boolean areConditionsMet(Settings settings) {
        WaveletType type = settings.getEnum("WAVELET_TYPE", WaveletType.class);
        // Hide vanishing moments for wavelets that don't use them
        return type != WaveletType.HAAR && type != WaveletType.MEXICAN_HAT;
    }
    
    @Override
    public void onSourceUpdated(Settings settings, Map<String, Object> updates) {
        WaveletType type = settings.getEnum("WAVELET_TYPE", WaveletType.class);
        WaveletCharacteristics chars = factory.getCharacteristics(type);
        
        // Update UI hints
        settings.setHint("VANISHING_MOMENTS", 
            String.format("Range: %d-%d, Recommended: %d", 
            chars.getMinVanishingMoments(), 
            chars.getMaxVanishingMoments(),
            chars.getDefaultVanishingMoments()));
    }
}
```

## Phase 3: JWave Fork Enhancements

### 3.1 Morlet Wavelet Implementation
```java
package jwave.transforms.wavelets.other;

public class Morlet extends Wavelet {
    private final double centerFrequency;
    private final double bandwidth;
    
    public Morlet(double centerFrequency, double bandwidth) {
        this.centerFrequency = centerFrequency;
        this.bandwidth = bandwidth;
        _name = "Morlet";
        _transformWavelength = 2; // Minimum length
        _motherWavelength = calculateMotherWavelength();
    }
    
    @Override
    public double[] forward(double[] values) {
        // Complex Morlet requires special handling
        return complexToRealTransform(values);
    }
    
    // Morlet wavelet function: exp(i*2*pi*f*t) * exp(-t^2/2*sigma^2)
    private Complex morletFunction(double t) {
        double gaussian = Math.exp(-t * t / (2 * bandwidth * bandwidth));
        double real = gaussian * Math.cos(2 * Math.PI * centerFrequency * t);
        double imag = gaussian * Math.sin(2 * Math.PI * centerFrequency * t);
        return new Complex(real, imag);
    }
}
```

### 3.2 Mexican Hat Implementation
```java
public class MexicanHat extends Wavelet {
    public MexicanHat() {
        _name = "Mexican Hat (Ricker)";
        _transformWavelength = 2;
        _motherWavelength = 5; // Typical support
        
        // Mexican Hat is the negative normalized second derivative of Gaussian
        buildBaseSystem();
    }
    
    @Override
    protected void buildBaseSystem() {
        _scalingCoefficients = new double[] {
            0.0, // No scaling function for CWT wavelets
        };
        
        _waveletCoefficients = generateRickerCoefficients(_motherWavelength);
    }
    
    private double[] generateRickerCoefficients(int length) {
        double[] coeffs = new double[length];
        double sigma = 1.0;
        
        for (int i = 0; i < length; i++) {
            double t = (i - length/2.0) / (length/4.0);
            coeffs[i] = (1 - t*t/(sigma*sigma)) * 
                       Math.exp(-t*t/(2*sigma*sigma)) / 
                       (Math.sqrt(3*sigma) * Math.pow(Math.PI, 0.25));
        }
        
        return normalize(coeffs);
    }
}
```

### 3.3 Trading-Optimized Custom Wavelets
```java
// Custom wavelet optimized for futures trading patterns
public class FuturesWavelet extends CustomWavelet {
    public FuturesWavelet() {
        _name = "Futures Trading Wavelet";
        
        // Design based on typical futures session patterns
        // Morning volatility -> midday calm -> afternoon activity
        _waveletCoefficients = designTradingSessionWavelet();
    }
    
    private double[] designTradingSessionWavelet() {
        // Use machine learning or empirical analysis of ES/NQ patterns
        // to design optimal wavelet shape
        return optimizeForMarketMicrostructure();
    }
}
```

## Phase 4: Strategy-Specific Implementations

### 4.1 Wavelet Selection Matrix
```java
public class WaveletSelector {
    private static final Map<TradingStrategy, List<WaveletConfig>> RECOMMENDATIONS = 
        Map.of(
            TradingStrategy.DENOISED_TREND, List.of(
                new WaveletConfig(WaveletType.SYMLET, 6),
                new WaveletConfig(WaveletType.COIFLET, 4)
            ),
            TradingStrategy.ENERGY_BURST, List.of(
                new WaveletConfig(WaveletType.DAUBECHIES, 4),
                new WaveletConfig(WaveletType.HAAR, 0)
            ),
            TradingStrategy.CYCLE_EXTRACTION, List.of(
                new WaveletConfig(WaveletType.MORLET, 0)
            ),
            TradingStrategy.PAIRS_COHERENCE, List.of(
                new WaveletConfig(WaveletType.MORLET, 0),
                new WaveletConfig(WaveletType.SYMLET, 8)
            )
        );
    
    public WaveletConfig recommendWavelet(TradingStrategy strategy, 
                                         MarketConditions conditions) {
        List<WaveletConfig> candidates = RECOMMENDATIONS.get(strategy);
        return selectOptimal(candidates, conditions);
    }
}
```

### 4.2 Adaptive Wavelet Selection
```java
public class AdaptiveWaveletStudy extends Study {
    private WaveletPerformanceTracker tracker;
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        // Measure current market conditions
        MarketConditions conditions = analyzeMarket(ctx, index);
        
        // Select optimal wavelet based on recent performance
        WaveletType optimalType = tracker.getBestPerformer(conditions);
        
        // Use selected wavelet for analysis
        double[][] coefficients = analyzer.performMODWT(
            getPriceData(ctx, index), 
            getDecompositionLevels(), 
            optimalType
        );
        
        // Track performance for continuous improvement
        tracker.recordPerformance(optimalType, conditions, coefficients);
    }
}
```

## Phase 5: Performance Optimization

### 5.1 Caching Strategy
```java
public class WaveletCacheManager {
    private final LoadingCache<WaveletCacheKey, double[][]> cache;
    
    public WaveletCacheManager() {
        this.cache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build(key -> computeCoefficients(key));
    }
    
    // Cache partial transforms for sliding window efficiency
    public double[][] getCoefficients(double[] data, int levels, 
                                     WaveletType type, int offset) {
        WaveletCacheKey key = new WaveletCacheKey(
            Arrays.hashCode(data), levels, type, offset);
        return cache.get(key);
    }
}
```

### 5.2 GPU Acceleration (Optional)
```java
// For high-frequency trading or multiple instrument analysis
public class GPUWaveletTransform {
    static {
        System.loadLibrary("wavelet_cuda");
    }
    
    public native double[][] performMODWTGPU(
        double[] data, int levels, int waveletId);
    
    public double[][] performParallelTransform(
        List<double[]> multipleInstruments, int levels, WaveletType type) {
        // Batch process multiple instruments on GPU
        return gpuBatchTransform(multipleInstruments, levels, type);
    }
}
```

## Phase 6: Testing and Validation

### 6.1 Wavelet Comparison Framework
```java
public class WaveletComparisonStudy extends Study {
    @Override
    public void initialize(Defaults defaults) {
        // Allow selection of multiple wavelets for comparison
        SettingTab comparisonTab = settings.addTab("Comparison");
        
        for (int i = 1; i <= 3; i++) {
            comparisonTab.addRow(new EnumDescriptor<>(
                "WAVELET_" + i, "Wavelet " + i, WaveletType.DAUBECHIES));
        }
        
        // Add performance metrics
        comparisonTab.addRow(new BooleanDescriptor(
            "SHOW_LAG", "Show Lag Analysis", true));
        comparisonTab.addRow(new BooleanDescriptor(
            "SHOW_ENERGY", "Show Energy Distribution", true));
    }
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        // Run multiple wavelets in parallel
        Map<WaveletType, double[][]> results = compareWavelets(ctx, index);
        
        // Display comparative metrics
        displayComparison(results, ctx, index);
    }
}
```

### 6.2 Performance Metrics
```java
public class WaveletMetrics {
    public static class PerformanceReport {
        double signalLag;          // Average lag in bars
        double noiseReduction;     // SNR improvement
        double computationTime;    // Milliseconds per transform
        double reconstructionError; // For orthogonal wavelets
        double frequencySeparation; // Quality of scale separation
    }
    
    public PerformanceReport evaluate(
        double[] originalData, 
        double[][] coefficients, 
        WaveletType type) {
        // Comprehensive performance analysis
        return calculateMetrics(originalData, coefficients, type);
    }
}
```

## Implementation Timeline

### Phase 1: Foundation (Weeks 1-2) ✅ COMPLETED
- [x] Basic WaveletAnalyzer implemented
- [x] MODWT support with Daubechies wavelets
- [x] AutoWavelets and DenoisedTrendFollowing studies
- [x] Parallel processing for performance

### Phase 2: Expand Discrete Wavelets (Weeks 3-4) 🚧 IN PROGRESS
- [ ] Implement WaveletFactory pattern
- [ ] Add Haar wavelet support
- [ ] Add Symlet family (Sym4, Sym8)
- [ ] Add Coiflet3 for smooth trends
- [ ] Create wavelet selection UI in studies

### Phase 3: JWave Develop Integration (Weeks 5-6) ⏳ PLANNED
- [ ] Upgrade to JWave develop branch (v250105)
- [ ] Implement CWT support
- [ ] Add Morlet wavelet for cycles
- [ ] Add Mexican Hat for spikes
- [ ] Create scalogram visualization

### Phase 4: Advanced Features (Weeks 7-8) ⏳ PLANNED
- [ ] Implement adaptive wavelet selection
- [ ] Add cross-wavelet coherence
- [ ] Create comparison framework
- [ ] Performance optimization with caching

### Phase 5: Testing & Documentation (Weeks 9-10) ⏳ PLANNED
- [ ] Comprehensive testing suite
- [ ] Performance benchmarks
- [ ] User documentation
- [ ] Trading strategy guides

## Success Metrics

### Already Achieved ✅
1. **Performance**: MODWT completes in <10ms for 512 points with parallel processing
2. **Accuracy**: Perfect reconstruction verified (MSE < 1e-24)
3. **Architecture**: Modular design allows easy wavelet addition

### Target Metrics 🎯
1. **Coverage**: Implement at least 20 of 74 available wavelets (27%)
2. **Usability**: Auto-selection correctly picks optimal wavelet 80%+ of time
3. **Performance**: Maintain <10ms transforms with wavelet caching
4. **CWT Support**: Full time-frequency analysis capabilities
5. **Documentation**: Complete user guide for wavelet selection

## Next Steps

### Immediate Actions
1. Add Haar wavelet to `WaveletType` enum in morphiq-core
2. Extend `WaveletAnalyzer` to support multiple wavelet types
3. Update study UIs to include wavelet selection dropdown
4. Create unit tests for new wavelets

### Code Location References
- Wavelet types: `morphiq-core/src/main/java/com/prophetizo/wavelets/WaveletType.java`
- Analyzer: `morphiq-core/src/main/java/com/prophetizo/wavelets/WaveletAnalyzer.java`
- Studies: `morphiq-autowave/src/main/java/com/prophetizo/studies/AutoWavelets.java`