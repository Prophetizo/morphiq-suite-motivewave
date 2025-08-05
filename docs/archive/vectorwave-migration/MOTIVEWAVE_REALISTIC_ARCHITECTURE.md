# MotiveWave-Compatible Architecture for VectorWave Integration

## MotiveWave Runtime Reality Check

### What MotiveWave DOES NOT Have:
- ❌ Spring Framework / Dependency Injection
- ❌ `@Service`, `@Component`, `@Autowired` annotations
- ❌ Application Context or IoC Container
- ❌ Enterprise Java patterns

### What MotiveWave DOES Have:
- ✅ Plain Java classes and objects
- ✅ Static methods and factory patterns
- ✅ Instance variables in Study classes
- ✅ Standard Java dependency management (constructor/setter injection)
- ✅ Access to external JARs in classpath

## Revised Architecture - MotiveWave Compatible

### 1. Factory Pattern Instead of Dependency Injection

```java
// Factory for creating trading analysis services
public class TradingWaveletFactory {
    private static TradingWaveletFactory instance;
    
    public static TradingWaveletFactory getInstance() {
        if (instance == null) {
            instance = new TradingWaveletFactory();
        }
        return instance;
    }
    
    public TradingWaveletAnalyzer createAnalyzer(String waveletType) {
        return new TradingWaveletAnalyzer(
            WaveletRegistry.getWavelet(waveletType),
            BoundaryMode.REFLECT
        );
    }
    
    public TradingDenoiser createDenoiser(String waveletType) {
        return new TradingDenoiser(
            WaveletDenoiser.forFinancialData()
        );
    }
}
```

### 2. Plain Java Service Classes

```java
// No @Service annotation - just a regular class
public class TradingWaveletAnalyzer {
    private final MultiLevelMODWTTransform modwtTransform;
    private final FinancialAnalyzer financialAnalyzer;
    
    // Constructor dependency injection (manual)
    public TradingWaveletAnalyzer(Wavelet wavelet, BoundaryMode boundaryMode) {
        this.modwtTransform = MODWTTransformFactory.createMultiLevel(wavelet, boundaryMode);
        this.financialAnalyzer = new FinancialAnalyzer(createFinancialConfig(wavelet));
    }
    
    public TradingAnalysisResult analyzePriceAction(double[] prices, int levels) {
        // Use VectorWave's financial analysis
        double trend = financialAnalyzer.analyzeRegimeTrend(prices);
        double volatility = financialAnalyzer.analyzeVolatility(prices);
        boolean anomaly = financialAnalyzer.detectAnomalies(prices);
        
        // MODWT decomposition
        MultiLevelMODWTResult modwtResult = modwtTransform.decompose(prices, levels);
        
        return new TradingAnalysisResult(trend, volatility, anomaly, modwtResult);
    }
}

// Plain Java denoising service
public class TradingDenoiser {
    private final WaveletDenoiser vectorDenoiser;
    
    public TradingDenoiser(WaveletDenoiser denoiser) {
        this.vectorDenoiser = denoiser;
    }
    
    public DenoisedPriceData denoise(double[] prices, DenoiseStrategy strategy) {
        ThresholdMethod method = mapStrategy(strategy);
        double[] cleanedPrices = vectorDenoiser.denoiseMultiLevel(
            prices, 5, method, ThresholdType.ADAPTIVE
        );
        
        return new DenoisedPriceData(cleanedPrices, prices, calculateQuality(prices, cleanedPrices));
    }
    
    public double[] extractTrend(double[] prices, int levels) {
        // Use VectorWave's approximation extraction
        MultiLevelMODWTResult result = modwtTransform.decompose(prices, levels);
        return modwtTransform.reconstructFromLevel(result, levels); // Approximation only
    }
}
```

### 3. Data Objects (Plain Java)

```java
// Simple data class - no fancy annotations
public class TradingAnalysisResult {
    private final double trendSignal;
    private final double volatilityLevel;
    private final boolean anomalyDetected;
    private final MultiLevelMODWTResult modwtResult;
    private final MarketRegime regime;
    
    public TradingAnalysisResult(double trend, double volatility, boolean anomaly, 
                                MultiLevelMODWTResult result) {
        this.trendSignal = trend;
        this.volatilityLevel = volatility;
        this.anomalyDetected = anomaly;
        this.modwtResult = result;
        this.regime = determineRegime(trend, volatility);
    }
    
    // Getter methods
    public double getDetailSignalAtLevel(int level) {
        double[] coeffs = modwtResult.getDetailCoeffsAtLevel(level);
        return coeffs[coeffs.length - 1]; // Latest value
    }
    
    public double getTrendSignal() { return trendSignal; }
    public double getVolatilityLevel() { return volatilityLevel; }
    public boolean isAnomalyDetected() { return anomalyDetected; }
    public MarketRegime getMarketRegime() { return regime; }
}

// Simple record-like class for denoised data
public class DenoisedPriceData {
    private final double[] cleanedPrices;
    private final double[] originalPrices;
    private final double qualityScore;
    
    public DenoisedPriceData(double[] cleaned, double[] original, double quality) {
        this.cleanedPrices = cleaned.clone();
        this.originalPrices = original.clone();
        this.qualityScore = quality;
    }
    
    public double getLatestCleanPrice() {
        return cleanedPrices[cleanedPrices.length - 1];
    }
    
    public double getQualityScore() { return qualityScore; }
    public boolean isHighQuality() { return qualityScore > 0.8; }
}
```

### 4. Configuration Management

```java
// Configuration helper for MotiveWave settings
public class WaveletConfigHelper {
    
    public static TradingConfig fromMotiveWaveSettings(Settings settings, BarSize barSize) {
        String waveletType = settings.getString("WAVELET_TYPE", "DAUBECHIES4");
        int levels = settings.getInteger("LEVELS", 5);
        String strategy = settings.getString("STRATEGY", "BALANCED");
        
        return new TradingConfig(
            mapWaveletType(waveletType),
            levels,
            DenoiseStrategy.valueOf(strategy),
            inferTradingStyle(barSize)
        );
    }
    
    private static String mapWaveletType(String motiveWaveType) {
        return switch (motiveWaveType) {
            case "HAAR" -> "haar";
            case "DAUBECHIES2" -> "db2";
            case "DAUBECHIES4" -> "db4";
            case "DAUBECHIES6" -> "db6";
            case "SYMLET4" -> "sym4";
            case "SYMLET8" -> "sym8";
            case "COIFLET3" -> "coif3";
            default -> "db4";
        };
    }
}

// Simple configuration object
public class TradingConfig {
    private final String waveletType;
    private final int levels;
    private final DenoiseStrategy strategy;
    private final TradingStyle tradingStyle;
    
    public TradingConfig(String waveletType, int levels, DenoiseStrategy strategy, TradingStyle style) {
        this.waveletType = waveletType;
        this.levels = levels;
        this.strategy = strategy;
        this.tradingStyle = style;
    }
    
    // Getters
    public String getWaveletType() { return waveletType; }
    public int getLevels() { return levels; }
    public DenoiseStrategy getStrategy() { return strategy; }
    public TradingStyle getTradingStyle() { return tradingStyle; }
}
```

## Revised Study Implementations

### AutoWavelets - MotiveWave Compatible

```java
public class AutoWavelets extends Study {
    // Instance variables instead of @Autowired
    private TradingWaveletAnalyzer analyzer;
    private TradingConfig config;
    
    @Override
    public void initialize(Defaults defaults) {
        super.initialize(defaults);
        
        // Manual "dependency injection" via factory
        TradingWaveletFactory factory = TradingWaveletFactory.getInstance();
        
        // Initialize configuration from settings
        this.config = WaveletConfigHelper.fromMotiveWaveSettings(getSettings(), defaults.getBarSize());
        
        // Create analyzer
        this.analyzer = factory.createAnalyzer(config.getWaveletType());
    }
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        if (analyzer == null) {
            // Lazy initialization if needed
            TradingWaveletFactory factory = TradingWaveletFactory.getInstance();
            this.analyzer = factory.createAnalyzer(config.getWaveletType());
        }
        
        // Collect price data (existing logic)
        double[] prices = collectPriceData(index, ctx);
        
        // Use our plain Java service
        TradingAnalysisResult analysis = analyzer.analyzePriceAction(prices, config.getLevels());
        
        // Set traditional outputs
        for (int level = 0; level < config.getLevels() && level < MAX_DECOMPOSITION_LEVELS; level++) {
            double signalValue = analysis.getDetailSignalAtLevel(level);
            dataSeries.setDouble(index, Paths.values()[level], signalValue);
        }
        
        // Enhanced outputs using VectorWave's financial analysis
        dataSeries.setDouble(index, TREND, analysis.getTrendSignal());
        dataSeries.setDouble(index, VOLATILITY, analysis.getVolatilityLevel());
        dataSeries.setBoolean(index, ANOMALY, analysis.isAnomalyDetected());
        dataSeries.setString(index, REGIME, analysis.getMarketRegime().toString());
    }
}
```

### DenoisedTrendFollowing - MotiveWave Compatible

```java
public class DenoisedTrendFollowing extends Study {
    // Instance variables
    private TradingDenoiser denoiser;
    private TradingConfig config;
    
    @Override
    public void initialize(Defaults defaults) {
        super.initialize(defaults);
        
        // Manual setup
        this.config = WaveletConfigHelper.fromMotiveWaveSettings(getSettings(), defaults.getBarSize());
        
        TradingWaveletFactory factory = TradingWaveletFactory.getInstance();
        this.denoiser = factory.createDenoiser(config.getWaveletType());
    }
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        double[] prices = collectPriceData(index, ctx);
        
        // Use plain Java service for denoising
        DenoisedPriceData denoised = denoiser.denoise(prices, config.getStrategy());
        
        // Extract trend using VectorWave's capabilities
        double[] trendData = denoiser.extractTrend(prices, config.getLevels());
        double trendValue = trendData[trendData.length - 1];
        
        // Set outputs
        dataSeries.setDouble(index, DENOISED_PRICE, denoised.getLatestCleanPrice());
        dataSeries.setDouble(index, TREND, trendValue);
        dataSeries.setDouble(index, QUALITY, denoised.getQualityScore());
        dataSeries.setBoolean(index, HIGH_QUALITY, denoised.isHighQuality());
    }
}
```

## Benefits of This Approach

### ✅ MotiveWave Compatible
- **No Spring dependencies** - works in MotiveWave's runtime
- **Standard Java patterns** - factory, plain objects, manual wiring
- **Familiar architecture** - similar to existing MotiveWave studies

### ✅ Still Leverages VectorWave
- **FinancialAnalyzer** for market analysis
- **WaveletDenoiser.forFinancialData()** for optimal denoising
- **MultiLevelMODWTTransform** for MODWT operations
- **Rich financial analysis** capabilities

### ✅ Clean Architecture
- **Separation of concerns** - services, data objects, configuration
- **Testable** - plain Java objects are easy to unit test
- **Maintainable** - clear responsibilities and dependencies
- **Extensible** - easy to add new analysis services

## Implementation Timeline

- **Day 1-2**: Create factory classes and service implementations
- **Day 3**: Implement data objects and configuration helpers
- **Day 4-5**: Refactor studies to use new architecture
- **Day 6**: Testing and optimization

**Total: 6 days** - Same timeline, MotiveWave-compatible architecture!