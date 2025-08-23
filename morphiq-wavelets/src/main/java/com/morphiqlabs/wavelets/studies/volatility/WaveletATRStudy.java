package com.morphiqlabs.wavelets.studies.volatility;

import ai.prophetizo.wavelet.api.TransformType;
import ai.prophetizo.wavelet.api.WaveletName;
import ai.prophetizo.wavelet.api.WaveletRegistry;
import com.morphiqlabs.wavelets.core.VectorWaveSwtAdapter;
import com.morphiqlabs.wavelets.core.WaveletAtr;
import com.morphiqlabs.wavelets.core.Thresholds;
import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.study.Plot;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Wavelet ATR (Average True Range) Study - Multi-scale volatility measurement
 * 
 * This study implements a wavelet-based approach to measuring market volatility
 * by analyzing the energy content in different frequency bands of the price signal.
 * Unlike traditional ATR which uses a simple moving average of true ranges,
 * Wavelet ATR decomposes price into multiple scales and measures volatility
 * at each scale separately, then combines them for a more nuanced view.
 * 
 * Key Features:
 * - Multi-scale volatility decomposition using wavelets
 * - Optional denoising for cleaner volatility measurements
 * - Adaptive scaling based on market price levels
 * - Configurable detail levels for different timeframe analysis
 * - Multiple scaling methods (Linear, Square Root, Logarithmic, Adaptive)
 * - BayesShrink thresholding to remove noise while preserving signal features
 * 
 * @author Morphiq Labs
 */
@StudyHeader(
    namespace = "com.morphiqlabs",
    id = "WAVELET_ATR",
    name = "Wavelet ATR",
    desc = "Multi-scale volatility measurement using wavelet decomposition",
    menu = "MorphIQ Labs",
    overlay = true,  // Bands need to be on price chart
    requiresBarUpdates = true,
    helpLink = "https://docs.morphiqlabs.com/wavelet-atr"
)
public class WaveletATRStudy extends Study {
    private static final Logger logger = LoggerFactory.getLogger(WaveletATRStudy.class);
    
    /**
     * Enum for WATR scaling methods
     */
    public enum ScalingMethod {
        LINEAR("Linear", "Fixed band width regardless of price level"),
        SQUARE_ROOT("Square Root", "Band width scales with square root of price"),
        LOGARITHMIC("Logarithmic", "Band width scales logarithmically with price"),
        ADAPTIVE("Adaptive", "Band width adapts to recent market volatility");
        
        private final String displayName;
        private final String description;
        
        ScalingMethod(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
        
        public static ScalingMethod fromString(String value) {
            if (value == null) return SQUARE_ROOT; // default
            
            // Try exact enum name match first
            try {
                return ScalingMethod.valueOf(value);
            } catch (IllegalArgumentException e) {
                // Fall through to display name match
            }
            
            // Try display name match
            for (ScalingMethod method : values()) {
                if (method.displayName.equalsIgnoreCase(value)) {
                    return method;
                }
            }
            
            logger.warn("Unknown scaling method: {}, using SQUARE_ROOT", value);
            return SQUARE_ROOT; // default
        }
    }

    // =============================================================================================
    // ENUMS - Type-safe keys
    // =============================================================================================
    
    public enum Values {
        WATR,         // Wavelet ATR value
        WATR_UPPER,   // Upper band
        WATR_LOWER,   // Lower band
        WATR_PCT      // WATR as percentage of price
    }

    // =============================================================================================
    // CONSTANTS - Settings keys
    // =============================================================================================
    
    // Core settings
    private static final String WAVELET_TYPE = "waveletType";
    private static final String DECOMPOSITION_LEVELS = "decompositionLevels";
    private static final String WINDOW_LENGTH = "windowLength";
    private static final String USE_DENOISED = "useDenoised";
    private static final String THRESH_LOOKBACK = "threshLookback";
    
    // WATR specific settings
    private static final String WATR_K = "watrK";
    private static final String WATR_MULTIPLIER = "watrMultiplier";
    private static final String WATR_SCALE_METHOD = "watrScaleMethod";
    private static final String WATR_SCALE_FACTOR = "watrScaleFactor";
    private static final String WATR_LEVEL_DECAY = "watrLevelDecay";
    private static final String SMOOTHING_PERIOD = "smoothingPeriod";
    private static final String SHOW_BANDS = "showBands";
    private static final String SHOW_PERCENTAGE = "showPercentage";
    
    // Display settings
    private static final String WATR_PATH = "watrPath";
    private static final String WATR_UPPER_PATH = "watrUpperPath";
    private static final String WATR_LOWER_PATH = "watrLowerPath";
    private static final String WATR_PCT_PATH = "watrPctPath";
    
    // =============================================================================================
    // DEFAULTS
    // =============================================================================================
    
    private static final int DEFAULT_LEVELS = 4;
    private static final int DEFAULT_WINDOW = 256;
    private static final int MIN_WINDOW = 64;
    private static final int MAX_WINDOW = 2048;
    private static final boolean DEFAULT_USE_DENOISED = false;
    private static final int DEFAULT_THRESH_LOOKBACK = 128;
    private static final int MIN_THRESH_LOOKBACK = 32;
    private static final int MAX_THRESH_LOOKBACK = 512;
    
    private static final int DEFAULT_WATR_K = 2;
    private static final double DEFAULT_WATR_MULTIPLIER = 1.0;  // Reduced since bands are now properly scaled
    private static final ScalingMethod DEFAULT_WATR_SCALE_METHOD = ScalingMethod.SQUARE_ROOT;
    private static final double DEFAULT_WATR_SCALE_FACTOR = 10.0;
    private static final double DEFAULT_LINEAR_FACTOR = 10.0;     // Standard band width
    private static final double DEFAULT_SQRT_FACTOR = 10.0;       // Standard band width  
    private static final double DEFAULT_LOG_FACTOR = 10.0;        // Standard band width
    private static final double DEFAULT_WATR_LEVEL_DECAY = 0.5;
    private static final int DEFAULT_SMOOTHING = 14;
    
    // =============================================================================================
    // STATE
    // =============================================================================================
    
    private VectorWaveSwtAdapter swtAdapter;
    private WaveletAtr waveletAtr;
    private String lastWaveletType = null;
    
    // =============================================================================================
    // INITIALIZATION
    // =============================================================================================
    
    @Override
    public void initialize(Defaults defaults) {
        logger.debug("Initializing Wavelet ATR Study");
        
        var sd = createSD();
        
        // ---- General Tab ----
        var generalTab = sd.addTab("General");
        
        // Input configuration
        var inputsGroup = generalTab.addGroup("Inputs");
        inputsGroup.addRow(new InputDescriptor(Inputs.INPUT, "Input", Enums.BarInput.CLOSE));
        
        // Wavelet configuration
        var waveletGroup = generalTab.addGroup("Wavelet Settings");
        waveletGroup.addRow(
            new DiscreteDescriptor(WAVELET_TYPE, "Wavelet Type", "DB4",
                createSWTWaveletOptions())
        );
        waveletGroup.addRow(
            new IntegerDescriptor(DECOMPOSITION_LEVELS, "Decomposition Levels",
                DEFAULT_LEVELS, 2, 8, 1)
        );
        waveletGroup.addRow(
            new IntegerDescriptor(WINDOW_LENGTH, "Window Length",
                DEFAULT_WINDOW, MIN_WINDOW, MAX_WINDOW, 32)
        );
        waveletGroup.addRow(
            new BooleanDescriptor(USE_DENOISED, "Use Denoised Signal", DEFAULT_USE_DENOISED)
                .setDescription("Apply BayesShrink denoising to remove noise while preserving volatility signals")
        );
        waveletGroup.addRow(
            new IntegerDescriptor(THRESH_LOOKBACK, "Denoising Lookback",
                DEFAULT_THRESH_LOOKBACK, MIN_THRESH_LOOKBACK, MAX_THRESH_LOOKBACK, 16)
                .setDescription("Number of bars used to estimate noise threshold for denoising")
        );
        
        // Enable threshold lookback only when denoising is enabled
        sd.addDependency(new EnabledDependency(USE_DENOISED, THRESH_LOOKBACK));
        
        // WATR configuration
        var watrGroup = generalTab.addGroup("WATR Parameters");
        watrGroup.addRow(
            new IntegerDescriptor(WATR_K, "Detail Levels (K)",
                DEFAULT_WATR_K, 1, 3, 1)
        );
        watrGroup.addRow(
            new DoubleDescriptor(WATR_LEVEL_DECAY, "Level Weight Decay",
                DEFAULT_WATR_LEVEL_DECAY, 0.1, 1.0, 0.05)
        );
        watrGroup.addRow(
            new IntegerDescriptor(SMOOTHING_PERIOD, "Smoothing Period",
                DEFAULT_SMOOTHING, 1, 50, 1)
        );
        
        // ---- Scaling Tab ----
        var scalingTab = sd.addTab("Scaling");
        
        var scaleGroup = scalingTab.addGroup("Scale Configuration");
        scaleGroup.addRow(
            new DiscreteDescriptor(WATR_SCALE_METHOD, "Scaling Method",
                DEFAULT_WATR_SCALE_METHOD.name(),  // Store enum name
                Arrays.asList(
                    new NVP(ScalingMethod.LINEAR.name(), ScalingMethod.LINEAR.getDisplayName()),
                    new NVP(ScalingMethod.SQUARE_ROOT.name(), ScalingMethod.SQUARE_ROOT.getDisplayName()),
                    new NVP(ScalingMethod.LOGARITHMIC.name(), ScalingMethod.LOGARITHMIC.getDisplayName()),
                    new NVP(ScalingMethod.ADAPTIVE.name(), ScalingMethod.ADAPTIVE.getDisplayName())
                ))
        );
        scaleGroup.addRow(
            new DoubleDescriptor(WATR_SCALE_FACTOR, "Scale Factor",
                DEFAULT_WATR_SCALE_FACTOR, 0.01, 1000.0, 0.1)
        );
        
        // ---- Display Tab ----
        var displayTab = sd.addTab("Display");
        
        // Band configuration
        var bandsGroup = displayTab.addGroup("Bands");
        bandsGroup.addRow(
            new BooleanDescriptor(SHOW_BANDS, "Show Bands", true)
        );
        bandsGroup.addRow(
            new DoubleDescriptor(WATR_MULTIPLIER, "Band Multiplier",
                DEFAULT_WATR_MULTIPLIER, 0.5, 3.0, 0.1)
        );
        bandsGroup.addRow(
            new BooleanDescriptor(SHOW_PERCENTAGE, "Show Percentage", false)
        );
        
        // Path configuration
        var pathsGroup = displayTab.addGroup("Lines");
        pathsGroup.addRow(
            new PathDescriptor(WATR_PATH, "WATR Line",
                defaults.getOrange(), 1.5f, null, false, false, true)  // Raw WATR value (small, shows actual volatility)
        );
        pathsGroup.addRow(
            new PathDescriptor(WATR_UPPER_PATH, "Upper Band",
                new Color(255, 100, 100, 128), 1.0f, null, true, false, true)
        );
        pathsGroup.addRow(
            new PathDescriptor(WATR_LOWER_PATH, "Lower Band",
                new Color(255, 100, 100, 128), 1.0f, null, true, false, true)
        );
        pathsGroup.addRow(
            new PathDescriptor(WATR_PCT_PATH, "WATR %",
                defaults.getPurple(), 1.0f, null, false, false, true)
        );
        
        // Indicator configuration
        var indicatorsGroup = displayTab.addGroup("Indicators");
        indicatorsGroup.addRow(
            new IndicatorDescriptor(Inputs.IND, "WATR",
                defaults.getOrange(), Color.WHITE, false, true, true)
        );
        indicatorsGroup.addRow(
            new IndicatorDescriptor(Inputs.IND2, "WATR %",
                defaults.getPurple(), Color.WHITE, false, false, true)
        );
        
        // Create Runtime Descriptor
        var desc = createRD();
        
        desc.setLabelSettings(WAVELET_TYPE, WATR_K);
        
        // Export values
        desc.exportValue(new ValueDescriptor(Values.WATR, "Wavelet ATR",
            new String[]{WAVELET_TYPE, WATR_K}));
        desc.exportValue(new ValueDescriptor(Values.WATR_PCT, "WATR %",
            new String[]{WAVELET_TYPE, WATR_K}));
        
        // Declare paths for bands on price chart
        desc.declarePath(Values.WATR_UPPER, WATR_UPPER_PATH);
        desc.declarePath(Values.WATR_LOWER, WATR_LOWER_PATH);
        
        // Set range keys for price overlay (bands)
        desc.setRangeKeys(Values.WATR_UPPER, Values.WATR_LOWER);
        
        // Create separate plot for WATR indicator
        Plot watrPlot = new Plot();
        desc.addPlot("WATR", watrPlot);
        watrPlot.setLabelSettings("WATR");
        watrPlot.setTabName("WATR");
        
        // Declare WATR path in the separate plot
        watrPlot.declarePath(Values.WATR, WATR_PATH);
        watrPlot.declarePath(Values.WATR_PCT, WATR_PCT_PATH);
        
        // Declare indicators in the WATR plot
        watrPlot.declareIndicator(Values.WATR, Inputs.IND);
        watrPlot.declareIndicator(Values.WATR_PCT, Inputs.IND2);
        
        // Set range keys for the WATR plot
        watrPlot.setRangeKeys(Values.WATR);
        
        setRuntimeDescriptor(desc);
    }
    
    // =============================================================================================
    // LIFECYCLE
    // =============================================================================================
    
    @Override
    public void onLoad(Defaults defaults) {
        try {
            initializeAdapter();
            
            if (swtAdapter == null) {
                swtAdapter = new VectorWaveSwtAdapter("db4");
                lastWaveletType = "db4";
            }
            
            double levelDecay = getSettings().getDouble(WATR_LEVEL_DECAY, DEFAULT_WATR_LEVEL_DECAY);
            int smoothingPeriod = getSettings().getInteger(SMOOTHING_PERIOD, DEFAULT_SMOOTHING);
            this.waveletAtr = new WaveletAtr(smoothingPeriod, levelDecay);
            
            setMinBars(getSettings().getInteger(WINDOW_LENGTH, DEFAULT_WINDOW));
            
            logger.debug("onLoad: Initialized with wavelet={}, decay={}, smoothing={}",
                        lastWaveletType, levelDecay, smoothingPeriod);
                        
        } catch (Exception e) {
            if (swtAdapter == null) {
                swtAdapter = new VectorWaveSwtAdapter("db4");
                lastWaveletType = "db4";
            }
            setMinBars(DEFAULT_WINDOW);
            logger.debug("onLoad: Error during initialization, using defaults", e);
        }
    }
    
    @Override
    public void onSettingsUpdated(DataContext ctx) {
        String scaleMethodStr = getSettings().getString(WATR_SCALE_METHOD, DEFAULT_WATR_SCALE_METHOD.name());
        ScalingMethod newScaleMethod = ScalingMethod.fromString(scaleMethodStr);
        double newScaleFactor = getSettings().getDouble(WATR_SCALE_FACTOR, DEFAULT_WATR_SCALE_FACTOR);
        
        logger.info("=== onSettingsUpdated Called ===");
        logger.info("New Scale Method: {} ({})", newScaleMethod.name(), newScaleMethod.getDisplayName());
        logger.info("New Scale Factor: {}", newScaleFactor);
        logger.info("================================");
        
        // Clear cached state
        clearState();
        
        // Force reinitialization
        lastWaveletType = null;
        swtAdapter = null;
        waveletAtr = null;
        
        // Update minimum bars
        setMinBars(getSettings().getInteger(WINDOW_LENGTH, DEFAULT_WINDOW));
        
        // Mark all bars for recalculation
        DataSeries series = ctx.getDataSeries();
        if (series != null) {
            logger.info("Marking {} bars for recalculation", series.size());
            
            for (int i = 0; i < series.size(); i++) {
                series.setComplete(i, false);
                // Clear all values to force range recalculation
                series.setDouble(i, Values.WATR, null);
                series.setDouble(i, Values.WATR_UPPER, null);
                series.setDouble(i, Values.WATR_LOWER, null);
                series.setDouble(i, Values.WATR_PCT, null);
            }
        }
        
        // Call parent implementation
        super.onSettingsUpdated(ctx);
    }
    
    @Override
    public void clearState() {
        logger.debug("clearState: Resetting internal state");
        super.clearState();
        swtAdapter = null;
        waveletAtr = null;
        lastWaveletType = null;
    }
    
    // =============================================================================================
    // CALCULATION
    // =============================================================================================
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        if (series == null || series.size() == 0) {
            return;
        }
        
        if (series.isComplete(index)) {
            return;
        }
        
        // Check if wavelet type changed
        String currentWaveletType = getSettings().getString(WAVELET_TYPE, "db4");
        if (!currentWaveletType.equals(lastWaveletType)) {
            logger.info("Wavelet type changed from {} to {}", lastWaveletType, currentWaveletType);
            swtAdapter = null;
            lastWaveletType = null;
            initializeAdapter();
        }
        
        // Ensure adapter is initialized
        if (swtAdapter == null) {
            initializeAdapter();
            if (swtAdapter == null) {
                swtAdapter = new VectorWaveSwtAdapter("db4");
                lastWaveletType = "db4";
            }
        }
        
        // Ensure WATR is initialized
        if (waveletAtr == null) {
            double levelDecay = getSettings().getDouble(WATR_LEVEL_DECAY, DEFAULT_WATR_LEVEL_DECAY);
            int smoothingPeriod = getSettings().getInteger(SMOOTHING_PERIOD, DEFAULT_SMOOTHING);
            this.waveletAtr = new WaveletAtr(smoothingPeriod, levelDecay);
        }
        
        // Get settings
        int levels = getSettings().getInteger(DECOMPOSITION_LEVELS, DEFAULT_LEVELS);
        int windowLength = getSettings().getInteger(WINDOW_LENGTH, DEFAULT_WINDOW);
        boolean useDenoised = getSettings().getBoolean(USE_DENOISED, DEFAULT_USE_DENOISED);
        int threshLookback = getSettings().getInteger(THRESH_LOOKBACK, DEFAULT_THRESH_LOOKBACK);
        Object input = getSettings().getInput(Inputs.INPUT);
        
        // Check if we have enough data
        if (index < windowLength - 1) {
            clearValues(series, index);
            return;
        }
        
        // Get window of data
        double[] data = getWindowData(series, index, windowLength, input);
        if (data == null || data.length < windowLength) {
            clearValues(series, index);
            return;
        }
        
        try {
            // Perform SWT transform
            VectorWaveSwtAdapter.SwtResult swtResult = swtAdapter.transform(data, levels);
            if (swtResult == null) {
                clearValues(series, index);
                return;
            }
            
            // Apply denoising if enabled
            if (useDenoised) {
                applyBayesShrinkDenoising(swtResult, threshLookback);
            }
            
            // Calculate WATR
            calculateWatr(series, index, swtResult);
            
            // Mark as complete
            series.setComplete(index);
            
        } catch (Exception e) {
            logger.error("calculate: Error at index {}", index, e);
            clearValues(series, index);
        }
    }
    
    private void calculateWatr(DataSeries series, int index, VectorWaveSwtAdapter.SwtResult swtResult) {
        int watrK = getSettings().getInteger(WATR_K, DEFAULT_WATR_K);
        double watrMultiplier = getSettings().getDouble(WATR_MULTIPLIER, DEFAULT_WATR_MULTIPLIER);
        boolean showBands = getSettings().getBoolean(SHOW_BANDS, true);
        boolean showPercentage = getSettings().getBoolean(SHOW_PERCENTAGE, false);
        
        // Get center price
        double centerPrice = series.getClose(index);
        
        // Calculate raw WATR (this is the actual volatility in price points)
        double rawWatr = waveletAtr.calculate(swtResult, watrK);
        
        // Scale WATR for display purposes only
        String scaleMethodStr = getSettings().getString(WATR_SCALE_METHOD, DEFAULT_WATR_SCALE_METHOD.name());
        ScalingMethod scaleMethod = ScalingMethod.fromString(scaleMethodStr);
        double scaleFactor = getSettings().getDouble(WATR_SCALE_FACTOR, DEFAULT_WATR_SCALE_FACTOR);
        
        // Log settings retrieval for debugging
        if (index == series.size() - 1) {
            logger.info("Settings retrieved - Method: {} ({}), Factor: {}", 
                scaleMethod.name(), scaleMethod.getDisplayName(), scaleFactor);
        }
        
        // Get method-specific default scale factor if not manually overridden
        double methodDefaultFactor = getDefaultScaleFactorForMethod(scaleMethod);
        
        // If scale factor is at any default value, use the method-specific default
        if (scaleFactor == DEFAULT_WATR_SCALE_FACTOR || 
            scaleFactor == DEFAULT_LINEAR_FACTOR || 
            scaleFactor == DEFAULT_SQRT_FACTOR || 
            scaleFactor == DEFAULT_LOG_FACTOR) {
            scaleFactor = methodDefaultFactor;
        }
        
        // Calculate band width scaling factor based on method
        // This scaling affects how wide the bands are from the center price
        double bandScaleFactor;
        
        switch (scaleMethod) {  // Now using enum directly
            case LINEAR:
                // Linear: no additional scaling, use raw WATR as-is
                bandScaleFactor = 1.0;
                break;
            case SQUARE_ROOT:
                // Square root: mild scaling based on price level
                // ES at 6450 -> sqrt(6450/6450) = 1.0
                // ES at 3225 -> sqrt(3225/6450) = 0.707
                bandScaleFactor = Math.sqrt(centerPrice / 6450.0);  // Normalized to ES typical price
                break;
            case LOGARITHMIC:
                // Logarithmic: very gradual scaling with price
                // ES at 6450 -> log(6450/1000) / log(10) = 0.81
                bandScaleFactor = Math.log(centerPrice / 1000.0) / Math.log(10.0);
                break;
            case ADAPTIVE:
                // Adaptive: scale bands based on recent volatility
                // Wider bands in volatile markets, tighter in calm markets
                double recentVol = series.std(index, 20, Enums.BarInput.CLOSE);
                Double avgPriceObj = series.sma(index, 20, Enums.BarInput.CLOSE);  // Returns Double
                if (avgPriceObj != null && avgPriceObj > 0 && recentVol > 0) {
                    double volPct = recentVol / avgPriceObj;  // Volatility as percentage
                    bandScaleFactor = 1.0 + (volPct * 10.0);  // Scale up based on volatility
                } else {
                    bandScaleFactor = 1.0;
                }
                break;
            default:
                // This should never happen with enums
                logger.error("Unexpected scale method enum: {}", scaleMethod);
                bandScaleFactor = 1.0;
        }
        
        // Apply user's scale factor adjustment
        bandScaleFactor *= (scaleFactor / 10.0);  // Normalize around 10.0 as the default
        
        // Store WATR for display
        series.setDouble(index, Values.WATR, rawWatr);
        
        // Calculate and store bands using band-specific scaling
        if (showBands) {
            // Scale the band width based on the scaling method
            double scaledBandWidth = rawWatr * bandScaleFactor * watrMultiplier;
            double upperBand = centerPrice + scaledBandWidth;
            double lowerBand = centerPrice - scaledBandWidth;
            series.setDouble(index, Values.WATR_UPPER, upperBand);
            series.setDouble(index, Values.WATR_LOWER, lowerBand);
            
            // Log band values for the last bar
            if (index == series.size() - 1) {
                logger.info("Bands: Upper={}, Lower={}, Width={}, Method={}, BandScale={}, Center={}",
                    String.format("%.2f", upperBand),
                    String.format("%.2f", lowerBand),
                    String.format("%.4f", scaledBandWidth),
                    scaleMethod.name(),
                    String.format("%.4f", bandScaleFactor),
                    String.format("%.2f", centerPrice));
            }
        }
        
        // Calculate and store percentage using display WATR
        if (showPercentage) {
            double watrPct = (rawWatr / centerPrice) * 100.0;
            series.setDouble(index, Values.WATR_PCT, watrPct);
        }
        
        // Always log for the last bar to see current values
        if (index == series.size() - 1 || (logger.isDebugEnabled() && index % 50 == 0)) {
            logger.info("WATR[{}]: method={}, display={}, bandBase={}, bandScale={}, price={}",
                        index,
                        scaleMethod.name(), 
                        String.format("%.2f", rawWatr),
                        String.format("%.2f", rawWatr),
                        String.format("%.4f", bandScaleFactor),
                        String.format("%.2f", centerPrice));
        }
    }
    
    private void clearValues(DataSeries series, int index) {
        series.setDouble(index, Values.WATR, null);
        series.setDouble(index, Values.WATR_UPPER, null);
        series.setDouble(index, Values.WATR_LOWER, null);
        series.setDouble(index, Values.WATR_PCT, null);
    }
    
    /**
     * Applies BayesShrink denoising to the detail coefficients of the SWT result.
     * This adaptive thresholding method removes noise while preserving important signal features,
     * which results in more stable and reliable volatility measurements.
     *
     * @param swtResult The SWT result containing the coefficients to be denoised.
     * @param lookback  The number of recent samples to use for estimating the noise level.
     */
    private void applyBayesShrinkDenoising(VectorWaveSwtAdapter.SwtResult swtResult, int lookback) {
        // Apply threshold to each detail level
        for (int level = 1; level <= swtResult.getLevels(); level++) {
            double[] detail = swtResult.getDetail(level);
            if (detail == null || detail.length == 0) continue;
            
            // Use last M samples to estimate threshold
            int m = Math.min(lookback, detail.length);
            int start = detail.length - m;
            double[] tail = new double[m];
            System.arraycopy(detail, start, tail, 0, m);
            
            // Calculate BayesShrink threshold
            double threshold = Thresholds.calculateThreshold(
                tail, Thresholds.ThresholdMethod.BAYES, level);
            
            // Apply soft thresholding
            swtResult.applyShrinkage(level, threshold, true);
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("Applied BayesShrink denoising with lookback={}", lookback);
        }
    }
    
    private double[] getWindowData(DataSeries series, int index, int windowLength, Object input) {
        if (series == null || windowLength <= 0) {
            return null;
        }
        
        double[] window = new double[windowLength];
        int start = index - windowLength + 1;
        boolean hasValidData = false;
        
        for (int i = 0; i < windowLength; i++) {
            int dataIndex = start + i;
            if (dataIndex < 0 || dataIndex >= series.size()) {
                window[i] = (dataIndex < 0) ? series.getClose(0) : series.getClose(series.size() - 1);
                continue;
            }
            
            Double value = series.getDouble(dataIndex, input);
            if (value != null) {
                window[i] = value;
                hasValidData = true;
            } else {
                window[i] = series.getClose(dataIndex);
                if (window[i] != 0.0) {
                    hasValidData = true;
                }
            }
        }
        
        return hasValidData ? window : null;
    }
    
    // =============================================================================================
    // HELPERS
    // =============================================================================================
    
    private double getDefaultScaleFactorForMethod(ScalingMethod scaleMethod) {
        switch (scaleMethod) {
            case LINEAR:
                return DEFAULT_LINEAR_FACTOR;
            case SQUARE_ROOT:
                return DEFAULT_SQRT_FACTOR;
            case LOGARITHMIC:
                return DEFAULT_LOG_FACTOR;
            case ADAPTIVE:
                return DEFAULT_SQRT_FACTOR; // Use SQRT default for adaptive
            default:
                return DEFAULT_SQRT_FACTOR;
        }
    }
    
    private void initializeAdapter() {
        String waveletType = getSettings().getString(WAVELET_TYPE, "DB4");
        logger.info("Creating new SWT adapter with wavelet: {}", waveletType);
        swtAdapter = new VectorWaveSwtAdapter(waveletType);
        lastWaveletType = waveletType;
    }
    
    private List<NVP> createSWTWaveletOptions() {
        List<NVP> options = new ArrayList<>();
        
        try {
            List<WaveletName> swtWavelets = WaveletRegistry.getWaveletsForTransform(TransformType.SWT);
            
            List<WaveletName> daubechiesFamily = WaveletRegistry.getDaubechiesWavelets();
            List<WaveletName> symletFamily = WaveletRegistry.getSymletWavelets();
            List<WaveletName> coifletFamily = WaveletRegistry.getCoifletWavelets();
            
            if (swtWavelets.contains(WaveletName.HAAR)) {
                options.add(new NVP("Haar", WaveletName.HAAR.name()));
            }
            
            for (WaveletName wavelet : daubechiesFamily) {
                if (swtWavelets.contains(wavelet)) {
                    options.add(new NVP(wavelet.getDescription(), wavelet.name()));
                }
            }
            
            for (WaveletName wavelet : symletFamily) {
                if (swtWavelets.contains(wavelet)) {
                    options.add(new NVP(wavelet.getDescription(), wavelet.name()));
                }
            }
            
            for (WaveletName wavelet : coifletFamily) {
                if (swtWavelets.contains(wavelet)) {
                    options.add(new NVP(wavelet.getDescription(), wavelet.name()));
                }
            }
            
            logger.info("Created {} SWT-compatible wavelet options", options.size());
            
        } catch (Exception e) {
            logger.error("Error creating SWT wavelet options", e);
        }
        
        return options;
    }
}