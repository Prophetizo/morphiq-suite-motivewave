# Custom ES/NQ Microstructure-Optimized Wavelet Design

## Overview

This document details the design and implementation of a custom wavelet specifically optimized for ES (E-mini S&P 500) and NQ (E-mini NASDAQ) futures microstructure patterns. This represents the penultimate feature of the wavelet trading package, combining deep market knowledge with advanced signal processing.

## Market Microstructure Analysis

### ES/NQ Unique Characteristics

#### 1. **Time-of-Day Patterns**
```java
public enum TradingSession {
    OPENING(LocalTime.of(9, 30), LocalTime.of(10, 30)) {
        @Override
        public MicrostructureProfile getProfile() {
            return new MicrostructureProfile()
                .withVolatilityMultiplier(2.5)
                .withMomentumPersistence(0.7)
                .withRangeBreakoutProbability(0.6)
                .withMeanReversionTime(15); // minutes
        }
    },
    
    LUNCH_CHOP(LocalTime.of(11, 30), LocalTime.of(13, 30)) {
        @Override
        public MicrostructureProfile getProfile() {
            return new MicrostructureProfile()
                .withVolatilityMultiplier(0.6)
                .withMomentumPersistence(0.3)
                .withRangeBreakoutProbability(0.2)
                .withOscillationFrequency(8); // cycles per hour
        }
    },
    
    AFTERNOON_TREND(LocalTime.of(14, 0), LocalTime.of(15, 30)) {
        @Override
        public MicrostructureProfile getProfile() {
            return new MicrostructureProfile()
                .withVolatilityMultiplier(1.8)
                .withMomentumPersistence(0.8)
                .withTrendAcceleration(0.4)
                .withInstitutionalFlow(true);
        }
    },
    
    CLOSE_RAMP(LocalTime.of(15, 30), LocalTime.of(16, 0)) {
        @Override
        public MicrostructureProfile getProfile() {
            return new MicrostructureProfile()
                .withVolatilityMultiplier(3.0)
                .withDirectionalBias(0.6)
                .withVolumeSpike(2.2)
                .withGammaEffects(true);
        }
    };
    
    public abstract MicrostructureProfile getProfile();
}
```

#### 2. **Price Level Clustering**
```java
public class ESNQPriceLevels {
    // ES moves in quarters, with psychological levels
    public static final double[] ES_CLUSTERING = {
        0.25, 0.50, 0.75,  // Quarter points
        5.0, 10.0, 25.0,   // Round numbers
        50.0, 100.0        // Major psychological levels
    };
    
    // NQ has wider spreads, different clustering
    public static final double[] NQ_CLUSTERING = {
        1.0, 2.5, 5.0,     // Basic increments
        25.0, 50.0, 100.0, // Round numbers
        250.0, 500.0       // Major levels
    };
    
    // VWAP clustering patterns
    public static final VWAPPattern VWAP_BEHAVIOR = new VWAPPattern()
        .withMeanReversionStrength(0.7)
        .withBreakoutThreshold(0.3) // % of daily range
        .withSessionResetEffect(true);
}
```

#### 3. **Order Flow Patterns**
```java
public class OrderFlowSignatures {
    // Institutional sweep patterns
    public static final double[] SWEEP_SIGNATURE = {
        0.1, 0.3, 0.6, 1.0, 0.8, 0.5, 0.2  // Quick accumulation then fade
    };
    
    // Retail stop hunting
    public static final double[] STOP_HUNT_SIGNATURE = {
        0.2, 0.5, 0.9, 1.0, -0.8, -0.3, 0.1  // Spike and reverse
    };
    
    // Algorithmic iceberg execution
    public static final double[] ICEBERG_SIGNATURE = {
        0.3, 0.3, 0.3, 0.3, 0.3, 0.3, 0.3  // Steady, consistent
    };
}
```

## Custom Wavelet Design Methodology

### 1. **Feature Extraction Framework**

```java
public class ESNQFeatureExtractor {
    
    public FeatureSet extractMicrostructureFeatures(PriceVolumeData data) {
        return FeatureSet.builder()
            // Temporal features
            .addFeature("opening_range_energy", calculateOpeningRangeEnergy(data))
            .addFeature("lunch_oscillation", detectLunchOscillation(data))
            .addFeature("afternoon_momentum", measureAfternoonMomentum(data))
            .addFeature("closing_ramp", identifyClosingRamp(data))
            
            // Microstructure features
            .addFeature("bid_ask_clustering", analyzeBidAskClustering(data))
            .addFeature("volume_profile", extractVolumeProfile(data))
            .addFeature("tape_reading", analyzeOrderFlow(data))
            
            // Cross-market features (ES vs NQ)
            .addFeature("correlation_breakdown", detectCorrelationBreakdown(data))
            .addFeature("lead_lag_relationship", measureLeadLag(data))
            .addFeature("relative_strength", calculateRelativeStrength(data))
            
            .build();
    }
    
    private double calculateOpeningRangeEnergy(PriceVolumeData data) {
        // Energy calculation specific to opening 30 minutes
        TimeWindow opening = data.getTimeWindow(MARKET_OPEN, MARKET_OPEN.plusMinutes(30));
        
        double[] prices = opening.getPrices();
        double range = opening.getHigh() - opening.getLow();
        double volume = opening.getTotalVolume();
        
        // Energy = (range/ATR) * (volume/avg_volume) * momentum_factor
        return (range / data.getATR(20)) * 
               (volume / data.getAverageVolume(20)) *
               calculateMomentumFactor(prices);
    }
}
```

### 2. **Multi-Objective Optimization**

```java
public class WaveletOptimizer {
    
    public OptimizedWavelet optimize(HistoricalData data, OptimizationGoals goals) {
        
        // Define optimization objectives
        MultiObjectiveFunction objectives = new MultiObjectiveFunction()
            .addObjective("pattern_detection", 0.35, this::evaluatePatternDetection)
            .addObjective("noise_rejection", 0.25, this::evaluateNoiseRejection)
            .addObjective("lag_minimization", 0.20, this::evaluateLag)
            .addObjective("reconstruction_quality", 0.20, this::evaluateReconstruction);
        
        // Genetic Algorithm with ES/NQ specific constraints
        GeneticAlgorithm ga = new GeneticAlgorithm()
            .withPopulationSize(100)
            .withGenerations(500)
            .withMutationRate(0.05)
            .withCrossoverRate(0.8)
            .withConstraints(new WaveletConstraints())
            .withElitism(0.1);
        
        // Run optimization
        return ga.evolve(objectives, data);
    }
    
    private double evaluatePatternDetection(double[] waveletCoeffs, HistoricalData data) {
        Wavelet testWavelet = new CustomWavelet(waveletCoeffs);
        
        double openingRangeScore = 0;
        double reversalScore = 0;
        double breakoutScore = 0;
        
        for (TradingSession session : data.getSessions()) {
            double[][] transform = testWavelet.transform(session.getPrices());
            
            // Test opening range breakout detection
            if (session.hasOpeningRangeBreakout()) {
                openingRangeScore += detectOpeningRange(transform, session);
            }
            
            // Test reversal detection
            for (ReversalPoint reversal : session.getReversals()) {
                reversalScore += detectReversal(transform, reversal);
            }
            
            // Test breakout detection
            for (Breakout breakout : session.getBreakouts()) {
                breakoutScore += detectBreakout(transform, breakout);
            }
        }
        
        return (openingRangeScore + reversalScore + breakoutScore) / data.getTotalSessions();
    }
}
```

### 3. **Adaptive Wavelet Architecture**

```java
public class AdaptiveESNQWavelet extends Wavelet {
    
    private final Map<TradingSession, double[]> sessionCoefficients;
    private final Map<VolatilityRegime, double[]> volatilityCoefficients;
    private final RealTimeAdapter adapter;
    
    public AdaptiveESNQWavelet() {
        _name = "Adaptive ES/NQ Microstructure Wavelet";
        _transformWavelength = 2;
        _motherWavelength = 12; // Optimal length from research
        
        // Initialize session-specific coefficients
        initializeSessionCoefficients();
        initializeVolatilityCoefficients();
        
        adapter = new RealTimeAdapter();
    }
    
    private void initializeSessionCoefficients() {
        // Opening session: Sharp, responsive
        sessionCoefficients.put(TradingSession.OPENING, new double[] {
            -0.0312, 0.0156, 0.0938, -0.4688, -0.7031, 0.5156, 0.0938, -0.0312
        });
        
        // Lunch session: Oscillation-detecting
        sessionCoefficients.put(TradingSession.LUNCH_CHOP, new double[] {
            0.0625, -0.1250, 0.2500, -0.5000, 0.5000, -0.2500, 0.1250, -0.0625
        });
        
        // Afternoon: Trend-following
        sessionCoefficients.put(TradingSession.AFTERNOON_TREND, new double[] {
            0.0156, 0.0312, 0.0625, 0.1250, 0.2500, 0.5000, 0.7500, 0.9375
        });
        
        // Close: High sensitivity
        sessionCoefficients.put(TradingSession.CLOSE_RAMP, new double[] {
            -0.0625, 0.1250, -0.2500, 0.5000, -1.0000, 0.5000, -0.2500, 0.1250
        });
    }
    
    @Override
    public double[] forward(double[] values) {
        // Real-time adaptation based on current market conditions
        MarketConditions current = adapter.getCurrentConditions();
        
        // Select appropriate coefficients
        double[] coeffs = selectCoefficients(current);
        
        // Apply transform with selected coefficients
        return applyTransform(values, coeffs);
    }
    
    private double[] selectCoefficients(MarketConditions conditions) {
        // Multi-factor coefficient selection
        TradingSession session = conditions.getCurrentSession();
        VolatilityRegime regime = conditions.getVolatilityRegime();
        double correlation = conditions.getESNQCorrelation();
        
        // Base coefficients from session
        double[] base = sessionCoefficients.get(session);
        
        // Adjust for volatility regime
        double[] volAdjusted = adjustForVolatility(base, regime);
        
        // Fine-tune for correlation environment
        return adjustForCorrelation(volAdjusted, correlation);
    }
}
```

## Implementation Architecture

### 1. **Multi-Scale Wavelet Family**

```java
public class ESNQWaveletFamily {
    
    public static class ScalpingWavelet extends Wavelet {
        // Optimized for 1-5 minute patterns
        public ScalpingWavelet() {
            _name = "ES/NQ Scalping Wavelet";
            _motherWavelength = 4;
            
            // Ultra-short, sharp for rapid detection
            _scalingCoefficients = new double[] {
                0.7071, 0.7071, -0.7071, -0.7071
            };
            _waveletCoefficients = new double[] {
                -0.7071, 0.7071, 0.7071, -0.7071
            };
        }
        
        @Override
        protected double[] forward(double[] values) {
            // Enhanced with bid-ask spread analysis
            return incorporateBidAskDynamics(super.forward(values));
        }
    }
    
    public static class SwingWavelet extends Wavelet {
        // Optimized for 30+ minute patterns
        public SwingWavelet() {
            _name = "ES/NQ Swing Wavelet";
            _motherWavelength = 16;
            
            // Smooth, trend-following characteristics
            _scalingCoefficients = OPTIMIZED_SWING_SCALING;
            _waveletCoefficients = OPTIMIZED_SWING_WAVELET;
        }
        
        @Override
        protected double[] forward(double[] values) {
            // Enhanced with volume profile analysis
            return incorporateVolumeProfile(super.forward(values));
        }
    }
    
    public static class NewsEventWavelet extends Wavelet {
        // Specialized for news/event detection
        public NewsEventWavelet() {
            _name = "ES/NQ News Event Wavelet";
            _motherWavelength = 8;
            
            // Spike detection optimized
            _scalingCoefficients = SPIKE_DETECTION_SCALING;
            _waveletCoefficients = SPIKE_DETECTION_WAVELET;
        }
    }
}
```

### 2. **Machine Learning Integration**

```java
public class MLWaveletTrainer {
    
    public TrainedWavelet trainOnHistoricalData(Dataset data, TrainingConfig config) {
        
        // 1. Feature Engineering
        FeatureExtractor extractor = new ESNQFeatureExtractor();
        FeatureMatrix features = extractor.extract(data);
        
        // 2. Label Creation (profitable patterns)
        LabelCreator labeler = new LabelCreator()
            .withMinimumProfit(config.getMinProfit())
            .withMaximumRisk(config.getMaxRisk())
            .withHoldingPeriod(config.getHoldingPeriod());
        
        Labels labels = labeler.createLabels(data);
        
        // 3. Neural Network Architecture
        NeuralNetwork network = NeuralNetwork.builder()
            .inputLayer(features.getWidth())
            .hiddenLayer(128, ActivationFunction.RELU)
            .hiddenLayer(64, ActivationFunction.RELU)
            .hiddenLayer(32, ActivationFunction.RELU)
            .outputLayer(12, ActivationFunction.TANH) // 12 wavelet coefficients
            .build();
        
        // 4. Training with custom loss function
        TrainingResult result = network.train(features, labels, new WaveletLoss());
        
        // 5. Extract learned wavelet
        double[] learnedCoefficients = result.getOutputLayer().getWeights();
        
        return new TrainedWavelet(learnedCoefficients, result.getValidationScore());
    }
    
    private class WaveletLoss implements LossFunction {
        @Override
        public double calculateLoss(double[] predicted, double[] actual) {
            // Custom loss that balances:
            // - Pattern detection accuracy
            // - False positive rate
            // - Computational efficiency
            // - Orthogonality constraints
            
            double detectionLoss = calculateDetectionLoss(predicted, actual);
            double falsePositivePenalty = calculateFalsePositivePenalty(predicted);
            double orthogonalityConstraint = enforceOrthogonality(predicted);
            
            return detectionLoss + 0.3 * falsePositivePenalty + 0.2 * orthogonalityConstraint;
        }
    }
}
```

### 3. **Real-Time Adaptation System**

```java
public class RealTimeWaveletAdapter {
    
    private final PerformanceTracker tracker;
    private final MarketRegimeDetector regimeDetector;
    private final WaveletCache cache;
    
    public AdaptedWavelet adaptForCurrentConditions(MarketData currentData) {
        
        // 1. Detect current market regime
        MarketRegime regime = regimeDetector.detectRegime(currentData);
        
        // 2. Analyze recent performance
        PerformanceMetrics metrics = tracker.getRecentPerformance();
        
        // 3. Select optimal base wavelet
        Wavelet baseWavelet = selectBaseWavelet(regime, metrics);
        
        // 4. Apply real-time adjustments
        double[] adjustedCoeffs = applyRealTimeAdjustments(
            baseWavelet.getCoefficients(), 
            currentData
        );
        
        // 5. Cache for performance
        AdaptedWavelet adapted = new AdaptedWavelet(adjustedCoeffs);
        cache.store(regime, adapted);
        
        return adapted;
    }
    
    private double[] applyRealTimeAdjustments(double[] base, MarketData data) {
        double[] adjusted = Arrays.copyOf(base, base.length);
        
        // Volatility adjustment
        double volMultiplier = calculateVolatilityMultiplier(data);
        for (int i = 0; i < adjusted.length; i++) {
            adjusted[i] *= volMultiplier;
        }
        
        // Correlation adjustment (for ES vs NQ)
        double correlation = data.getESNQCorrelation();
        if (correlation < 0.7) { // Correlation breakdown
            adjusted = enhanceForDivergence(adjusted);
        }
        
        // Time-of-day adjustment
        LocalTime now = LocalTime.now(ZoneId.of("America/New_York"));
        adjusted = adjustForTimeOfDay(adjusted, now);
        
        return normalize(adjusted);
    }
}
```

## Validation and Testing Framework

### 1. **Comprehensive Backtesting**

```java
public class ESNQWaveletBacktester {
    
    public BacktestResults validateWavelet(Wavelet wavelet, HistoricalData data) {
        
        BacktestResults results = new BacktestResults();
        
        // 1. Pattern Detection Accuracy
        PatternDetectionResults patterns = testPatternDetection(wavelet, data);
        results.addSection("Pattern Detection", patterns);
        
        // 2. Signal Quality Analysis
        SignalQualityResults signals = analyzeSignalQuality(wavelet, data);
        results.addSection("Signal Quality", signals);
        
        // 3. Risk-Adjusted Returns
        RiskReturnResults returns = calculateRiskAdjustedReturns(wavelet, data);
        results.addSection("Risk-Return", returns);
        
        // 4. Market Condition Performance
        MarketConditionResults conditions = testAcrossMarketConditions(wavelet, data);
        results.addSection("Market Conditions", conditions);
        
        return results;
    }
    
    private PatternDetectionResults testPatternDetection(Wavelet wavelet, HistoricalData data) {
        PatternDetectionResults results = new PatternDetectionResults();
        
        // Test opening range breakouts
        List<OpeningRangeBreakout> knownBreakouts = data.getOpeningRangeBreakouts();
        double breakoutAccuracy = 0;
        
        for (OpeningRangeBreakout breakout : knownBreakouts) {
            double[][] coeffs = wavelet.transform(breakout.getPriceData());
            boolean detected = detectBreakoutInCoefficients(coeffs, breakout.getBreakoutTime());
            if (detected) breakoutAccuracy++;
        }
        
        results.setOpeningRangeAccuracy(breakoutAccuracy / knownBreakouts.size());
        
        // Test other patterns...
        return results;
    }
}
```

### 2. **Performance Metrics**

```java
public class WaveletPerformanceMetrics {
    
    public static class MetricSuite {
        // Detection metrics
        double patternDetectionRate;
        double falsePositiveRate;
        double signalToNoiseRatio;
        
        // Trading metrics
        double sharpeRatio;
        double maxDrawdown;
        double winRate;
        double profitFactor;
        
        // Technical metrics
        double computationalEfficiency; // ms per transform
        double memoryUsage;            // bytes per operation
        double reconstructionError;    // for orthogonal wavelets
        
        // Market-specific metrics
        double openingRangePerformance;
        double lunchChopHandling;
        double afternoonTrendCapture;
        double newsEventDetection;
    }
    
    public MetricSuite calculateComprehensiveMetrics(
            Wavelet wavelet, 
            HistoricalData data,
            TradingStrategy strategy) {
        
        MetricSuite metrics = new MetricSuite();
        
        // Run comprehensive analysis
        WaveletAnalysisResult analysis = runAnalysis(wavelet, data, strategy);
        
        // Calculate all metrics
        metrics.patternDetectionRate = calculatePatternDetection(analysis);
        metrics.falsePositiveRate = calculateFalsePositives(analysis);
        metrics.sharpeRatio = calculateSharpe(analysis);
        // ... etc
        
        return metrics;
    }
}
```

## Integration with MotiveWave Package

### 1. **Study Implementation**

```java
@StudyHeader(
    namespace = "com.prophetizo.motivewave.studies",
    id = "CUSTOM_ESNQ_WAVELET",
    name = "Custom ES/NQ Wavelet",
    desc = "AI-Optimized Wavelet for ES/NQ Microstructure",
    menu = "Prophetizo, LLC",
    overlay = false
)
public class CustomESNQWaveletStudy extends Study {
    
    private AdaptiveESNQWavelet customWavelet;
    private RealTimeWaveletAdapter adapter;
    private PerformanceTracker tracker;
    
    @Override
    public void initialize(Defaults defaults) {
        SettingsDescriptor settings = new SettingsDescriptor();
        
        // Wavelet Configuration Tab
        SettingTab waveletTab = settings.addTab("Wavelet Config");
        
        // Model selection
        EnumDescriptor<ESNQModel> model = new EnumDescriptor<>(
            "MODEL", "Wavelet Model", ESNQModel.ADAPTIVE);
        waveletTab.addRow(model);
        
        // Time session adaptation
        BooleanDescriptor timeAdapt = new BooleanDescriptor(
            "TIME_ADAPT", "Time-of-Day Adaptation", true);
        waveletTab.addRow(timeAdapt);
        
        // Volatility regime adaptation
        BooleanDescriptor volAdapt = new BooleanDescriptor(
            "VOL_ADAPT", "Volatility Adaptation", true);
        waveletTab.addRow(volAdapt);
        
        // ML continuous learning
        BooleanDescriptor mlLearn = new BooleanDescriptor(
            "ML_LEARN", "Continuous Learning", false);
        waveletTab.addRow(mlLearn);
        
        // Performance tracking
        SettingTab performanceTab = settings.addTab("Performance");
        
        BooleanDescriptor showMetrics = new BooleanDescriptor(
            "SHOW_METRICS", "Show Performance Metrics", true);
        performanceTab.addRow(showMetrics);
        
        setSettingsDescriptor(settings);
        
        // Initialize custom components
        initializeCustomWavelet();
    }
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        try {
            // Get current market data
            MarketData currentData = getCurrentMarketData(ctx, index);
            
            // Adapt wavelet for current conditions
            AdaptedWavelet adapted = adapter.adaptForCurrentConditions(currentData);
            
            // Perform transform
            double[][] coefficients = adapted.transform(
                getPriceData(ctx, index), 
                getDecompositionLevels()
            );
            
            // Generate signals
            generateCustomSignals(coefficients, ctx, index);
            
            // Track performance
            tracker.recordPerformance(adapted, coefficients, currentData);
            
            // Update display
            updateDisplay(coefficients, ctx, index);
            
        } catch (Exception e) {
            logger.error("Error in custom ES/NQ wavelet calculation", e);
        }
    }
}
```

### 2. **Roadmap Integration**

Update CLAUDE.md and STRATEGIES.md to reflect this as the flagship feature:

```markdown
## Phase 4 - Flagship Feature (The Penultimate Achievement)

- [ ] **Custom ES/NQ Microstructure-Optimized Wavelet** 🏆 Flagship
  - AI-designed wavelet specifically for ES/NQ patterns
  - Real-time adaptation based on market conditions
  - Machine learning continuous improvement
  - Multi-scale family (scalping, intraday, swing)
  - Session-specific coefficient optimization
  - Backtested performance validation
  - Integration with all other strategies
```

## Success Criteria

1. **Performance**: >15% improvement over Daubechies4 in ES/NQ pattern detection
2. **Adaptability**: Automatically adjusts to market regime changes within 5 bars
3. **Accuracy**: >80% accuracy in opening range breakout detection
4. **Efficiency**: Computation time <15ms for 512-point transform
5. **Robustness**: Maintains performance across different market conditions
6. **Learning**: Continuous improvement demonstrated over time

This custom wavelet represents the culmination of combining deep market microstructure knowledge with advanced signal processing, creating a truly market-specific analytical tool that goes beyond generic mathematical transforms.