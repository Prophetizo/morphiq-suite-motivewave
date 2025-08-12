package com.prophetizo.wavelets.swt;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.study.Plot;
import com.motivewave.platform.sdk.study.RuntimeDescriptor;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.prophetizo.LoggerConfig;
import com.prophetizo.motivewave.common.StudyUIHelper;
import com.prophetizo.wavelets.swt.core.*;
import org.slf4j.Logger;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

@StudyHeader(
        namespace = "com.prophetizo.wavelets.swt",
        id = "SWT_TREND_MOMENTUM",
        name = "SWT Trend + Momentum",
        desc = "Undecimated SWT/MODWT trend following with cross-scale momentum confirmation",
        menu = "MorphIQ | Wavelet Analysis",
        overlay = true,
        requiresBarUpdates = false
)
public class SwtTrendMomentumStudy extends Study {
    private static final Logger logger = LoggerConfig.getLogger(SwtTrendMomentumStudy.class);
    
    // Settings keys
    public static final String WAVELET_TYPE = "WAVELET_TYPE";
    public static final String LEVELS = "LEVELS";
    public static final String WINDOW_LENGTH = "WINDOW_LENGTH";
    public static final String THRESHOLD_METHOD = "THRESHOLD_METHOD";
    public static final String SHRINKAGE_TYPE = "SHRINKAGE_TYPE";
    public static final String USE_DENOISED = "USE_DENOISED";
    public static final String DETAIL_CONFIRM_K = "DETAIL_CONFIRM_K";
    public static final String SHOW_WATR = "SHOW_WATR";
    public static final String WATR_K = "WATR_K";
    public static final String WATR_MULTIPLIER = "WATR_MULTIPLIER";
    public static final String ENABLE_SIGNALS = "ENABLE_SIGNALS";
    
    // Path keys
    public static final String AJ_PATH = "AJ_PATH";
    public static final String WATR_UPPER_PATH = "WATR_UPPER_PATH";
    public static final String WATR_LOWER_PATH = "WATR_LOWER_PATH";
    
    // Plot keys
    public static final String MOMENTUM_PLOT = "MOMENTUM_PLOT";
    
    // Path keys for momentum plot
    public static final String MOMENTUM_SUM_PATH = "MOMENTUM_SUM_PATH";
    public static final String SLOPE_PATH = "SLOPE_PATH";
    
    // Values
    public enum Values { 
        AJ,                    // Approximation (trend)
        D1_SIGN, D2_SIGN, D3_SIGN,  // Detail signs for momentum
        LONG_FILTER, SHORT_FILTER,   // Filter states
        WATR,                  // Wavelet ATR
        WATR_UPPER, WATR_LOWER,      // WATR bands
        MOMENTUM_SUM,          // Sum of detail signs
        SLOPE                  // Trend slope
    }
    
    // Signals
    public enum Signals {
        LONG_ENTER, SHORT_ENTER, FLAT_EXIT
    }
    
    // Core components
    private VectorWaveSwtAdapter swtAdapter;
    private WaveletAtr waveletAtr;
    
    // State tracking
    private String currentWaveletType = null;
    private Integer currentLevels = null;
    private Integer currentWindowLength = null;
    private boolean lastLongFilter = false;
    private boolean lastShortFilter = false;
    
    @Override
    public void onLoad(Defaults defaults) {
        logger.info("Study onLoad - initializing");
        
        // CRITICAL: Call super.onLoad first to let framework handle setup
        super.onLoad(defaults);
        
        // Update minimum bars requirement based on window length after framework initialization
        setMinBars(getSettings().getInteger(WINDOW_LENGTH, 4096));
    }
    
    @Override
    public void onSettingsUpdated(DataContext ctx) {
        logger.info("Settings updated - triggering full recalculation for all plots");
        
        // Log which settings might have changed (for debugging)
        logger.debug("Current settings: wavelet={}, levels={}, window={}, threshold={}, shrinkage={}, k={}, watr={}", 
                    getSettings().getString(WAVELET_TYPE, "db4"),
                    getSettings().getInteger(LEVELS, 5),
                    getSettings().getInteger(WINDOW_LENGTH, 4096),
                    getSettings().getString(THRESHOLD_METHOD, "Universal"),
                    getSettings().getString(SHRINKAGE_TYPE, "Soft"),
                    getSettings().getInteger(DETAIL_CONFIRM_K, 2),
                    getSettings().getBoolean(SHOW_WATR, false));
        
        // Clear state to force re-initialization of all components
        // This ensures ALL plots (overlay and panes) are updated
        clearState();
        
        // Update minimum bars requirement
        setMinBars(getSettings().getInteger(WINDOW_LENGTH, 4096));
        
        // Update momentum plot range for new k value
        updateMomentumPlotRange();
        
        // CRITICAL: Force complete recalculation of all bars
        DataSeries series = ctx.getDataSeries();
        int startIdx = Math.max(0, series.size() - 5000); // Limit to last 5000 bars for performance
        for (int i = startIdx; i < series.size(); i++) {
            calculate(i, ctx);
        }
        
        // Call super to trigger any additional framework updates
        super.onSettingsUpdated(ctx);
    }
    
    @Override
    public void clearState() {
        logger.debug("Clearing state - resetting all cached values");
        
        // CRITICAL: Call super first
        super.clearState();
        
        // Reset all state tracking
        currentWaveletType = null;
        currentLevels = null;
        currentWindowLength = null;
        lastLongFilter = false;
        lastShortFilter = false;
        
        // Clear components to force re-initialization
        swtAdapter = null;
        waveletAtr = null;
        
        // Clear any figures (markers, etc.)
        clearFigures();
    }
    
    @Override
    protected void calculateValues(DataContext ctx) {
        logger.debug("Full recalculation triggered");
        
        // Clear all existing figures
        clearFigures();
        
        // Reset signal tracking state
        lastLongFilter = false;
        lastShortFilter = false;
        
        // Update momentum plot range
        updateMomentumPlotRange();
        
        // Now recalculate all bars
        super.calculateValues(ctx);
    }
    
    private void updateMomentumPlotRange() {
        // Get the runtime descriptor and momentum plot
        var desc = getRuntimeDescriptor();
        if (desc != null) {
            var momentumPlot = desc.getPlot(MOMENTUM_PLOT);
            if (momentumPlot != null) {
                // Get the k value to determine momentum range
                int k = getSettings().getInteger(DETAIL_CONFIRM_K, 2);
                
                // Momentum sum ranges from -k to +k
                // Add some padding for the slope values
                int range = Math.max(k + 1, 3);
                
                // Set fixed top and bottom to ensure consistent scale
                momentumPlot.setFixedTopValue(range);
                momentumPlot.setFixedBottomValue(-range);
                
                logger.debug("Updated momentum plot range: {} to {}", -range, range);
            }
        }
    }
    
    @Override
    public void initialize(Defaults defaults) {
        logger.debug("Initializing SWT Trend + Momentum Study");
        
        // Create Settings Descriptor using the standard pattern
        var sd = createSD();
        
        // General settings
        var generalTab = sd.addTab("General");
        var waveletGroup = generalTab.addGroup("Wavelet Configuration");
        
        List<NVP> waveletOptions = StudyUIHelper.createWaveletOptions();
        waveletGroup.addRow(StudyUIHelper.createWaveletTypeDescriptor(WAVELET_TYPE, "db4", waveletOptions));
        waveletGroup.addRow(new IntegerDescriptor(LEVELS, "Decomposition Levels", 5, 2, 8, 1));
        waveletGroup.addRow(new IntegerDescriptor(WINDOW_LENGTH, "Window Length (bars)", 512, 256, 4096, 256));
        
        var thresholdGroup = generalTab.addGroup("Thresholding");
        thresholdGroup.addRow(new DiscreteDescriptor(THRESHOLD_METHOD, "Threshold Method", "Universal",
                Arrays.asList(
                        new NVP("Universal", "Universal (robust)"),
                        new NVP("Bayes", "BayesShrink (adaptive)"),
                        new NVP("SURE", "SURE (optimal)")
                )));
        thresholdGroup.addRow(new DiscreteDescriptor(SHRINKAGE_TYPE, "Shrinkage Type", "Soft",
                Arrays.asList(
                        new NVP("Soft", "Soft thresholding"),
                        new NVP("Hard", "Hard thresholding")
                )));
        thresholdGroup.addRow(new BooleanDescriptor(USE_DENOISED, "Use Denoised Signal", false));
        
        var signalGroup = generalTab.addGroup("Signal Configuration");
        signalGroup.addRow(new IntegerDescriptor(DETAIL_CONFIRM_K, "Detail Confirmation (k)", 2, 1, 3, 1));
        signalGroup.addRow(new BooleanDescriptor(ENABLE_SIGNALS, "Enable Trading Signals", true));
        
        // Display settings
        var displayTab = sd.addTab("Display");
        
        var trendGroup = displayTab.addGroup("Trend Display");
        trendGroup.addRow(new PathDescriptor(AJ_PATH, "Approximation (A_J)",
                new Color(0, 150, 255), 2.0f, null, true, true, true));
        
        var watrGroup = displayTab.addGroup("Wavelet ATR");
        watrGroup.addRow(new BooleanDescriptor(SHOW_WATR, "Show WATR Bands", false));
        watrGroup.addRow(new IntegerDescriptor(WATR_K, "WATR Detail Levels", 2, 1, 3, 1));
        watrGroup.addRow(new DoubleDescriptor(WATR_MULTIPLIER, "WATR Multiplier", 2.0, 1.0, 5.0, 0.1));
        watrGroup.addRow(new PathDescriptor(WATR_UPPER_PATH, "WATR Upper Band",
                new Color(255, 100, 100, 128), 1.0f, null, true, false, true));
        watrGroup.addRow(new PathDescriptor(WATR_LOWER_PATH, "WATR Lower Band",
                new Color(255, 100, 100, 128), 1.0f, null, true, false, true));
        
        // Momentum plot paths
        var momentumGroup = displayTab.addGroup("Momentum Display");
        momentumGroup.addRow(new PathDescriptor(MOMENTUM_SUM_PATH, "Cross-Scale Momentum",
                new Color(0, 200, 150), 2.0f, null, true, true, true));
        momentumGroup.addRow(new PathDescriptor(SLOPE_PATH, "Trend Slope",
                new Color(200, 150, 0), 1.5f, null, true, false, true));
        
        // Create Runtime Descriptor using the standard pattern
        var desc = createRD();
        
        // Main price plot
        desc.declarePath(Values.AJ, AJ_PATH);
        desc.declarePath(Values.WATR_UPPER, WATR_UPPER_PATH);
        desc.declarePath(Values.WATR_LOWER, WATR_LOWER_PATH);
        desc.declareIndicator(Values.AJ, "A_J");
        desc.setRangeKeys(Values.AJ);
        
        // Momentum plot
        Plot momentumPlot = new Plot();
        desc.addPlot(MOMENTUM_PLOT, momentumPlot);
        momentumPlot.setLabelSettings("Cross-Scale Momentum");
        momentumPlot.setLabelPrefix("Momentum");
        momentumPlot.setTabName("Momentum");
        momentumPlot.declarePath(Values.MOMENTUM_SUM, MOMENTUM_SUM_PATH);
        momentumPlot.declarePath(Values.SLOPE, SLOPE_PATH);
        momentumPlot.declareIndicator(Values.MOMENTUM_SUM, "Momentum");
        momentumPlot.declareIndicator(Values.SLOPE, "Slope");
        momentumPlot.setRangeKeys(Values.MOMENTUM_SUM, Values.SLOPE);
        
        // Set initial scale based on default k value
        int defaultK = 2; // Default k value
        int range = Math.max(defaultK + 1, 3);
        momentumPlot.setFixedTopValue(range);
        momentumPlot.setFixedBottomValue(-range);
        
        // Add zero line
        momentumPlot.addHorizontalLine(new LineInfo(0.0, null, 1.0f, new float[]{3, 3}));
        
        // Signals
        desc.declareSignal(Signals.LONG_ENTER, "Long Entry");
        desc.declareSignal(Signals.SHORT_ENTER, "Short Entry");
        desc.declareSignal(Signals.FLAT_EXIT, "Flat Exit");
    }
    
    
    @Override
    public int getMinBars() {
        // Return the minimum number of bars needed for calculation
        return getSettings().getInteger(WINDOW_LENGTH, 4096);
    }
    
    @Override
    public int getMinBars(DataContext ctx, BarSize bs) {
        // Override with context-aware version
        return getSettings().getInteger(WINDOW_LENGTH, 4096);
    }
    
    private void ensureInitialized() {
        String waveletType = getSettings().getString(WAVELET_TYPE, "db4");
        Integer levels = getSettings().getInteger(LEVELS, 5);
        Integer windowLength = getSettings().getInteger(WINDOW_LENGTH, 4096);
        
        // Check if settings have changed
        boolean waveletChanged = !waveletType.equals(currentWaveletType);
        boolean levelsChanged = !levels.equals(currentLevels);
        boolean windowChanged = !windowLength.equals(currentWindowLength);
        
        // Initialize or reinitialize if needed
        if (swtAdapter == null || waveletChanged || levelsChanged || windowChanged) {
            try {
                String vectorWaveType = mapWaveletName(waveletType);
                this.swtAdapter = new VectorWaveSwtAdapter(vectorWaveType);
                currentWaveletType = waveletType;
                currentLevels = levels;
                currentWindowLength = windowLength;
                
                // Reset signal tracking on change
                lastLongFilter = false;
                lastShortFilter = false;
                
                logger.info("SWT adapter initialized/updated - wavelet: {} -> {}, levels: {}, window: {}", 
                           waveletType, vectorWaveType, levels, windowLength);
            } catch (Exception e) {
                logger.error("Failed to initialize SWT adapter", e);
            }
        }
        
        if (waveletAtr == null) {
            this.waveletAtr = new WaveletAtr(14); // 14-period smoothing
            logger.debug("Initialized WATR component");
        } else if (waveletChanged || levelsChanged || windowChanged) {
            // Reset WATR on settings change
            waveletAtr.reset();
            logger.debug("Reset WATR due to settings change");
        }
    }
    
    
    private String mapWaveletName(String displayName) {
        // Check if already in short form
        if (displayName.matches("^(db|sym|coif)\\d+$") || displayName.equalsIgnoreCase("haar")) {
            logger.debug("Wavelet already in short form: {}", displayName);
            return displayName.toLowerCase();
        }
        
        // Map UI display names to VectorWave names
        if (displayName.startsWith("Daubechies")) {
            String number = displayName.replaceAll("[^0-9]", "");
            return "db" + number;
        } else if (displayName.startsWith("Symlet")) {
            String number = displayName.replaceAll("[^0-9]", "");
            return "sym" + number;
        } else if (displayName.startsWith("Coiflet")) {
            String number = displayName.replaceAll("[^0-9]", "");
            return "coif" + number;
        } else if (displayName.equalsIgnoreCase("Haar")) {
            return "haar";
        }
        
        logger.warn("Unknown wavelet type: {}, defaulting to db4", displayName);
        // Default fallback
        return "db4";
    }
    
    @Override
    public void onBarUpdate(DataContext ctx) {
        // Handle tick-by-tick updates for the current bar
        DataSeries series = ctx.getDataSeries();
        int index = series.size() - 1;
        
        if (index >= 0) {
            // Recalculate only the current bar
            calculateBarSWT(index, ctx);
        }
    }
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        // This is called for historical bars and new complete bars
        calculateBarSWT(index, ctx);
    }
    
    private void calculateBarSWT(int index, DataContext ctx) {
        try {
            // Ensure components are initialized
            ensureInitialized();
            
            if (swtAdapter == null) {
                // Clear any stale values when adapter is not ready
                DataSeries series = ctx.getDataSeries();
                series.setDouble(index, Values.MOMENTUM_SUM, null);
                series.setDouble(index, Values.SLOPE, null);
                return; // Cannot proceed without SWT adapter
            }
            
            DataSeries series = ctx.getDataSeries();
            
            // Get configuration
            int windowLength = getSettings().getInteger(WINDOW_LENGTH, 4096);
            int levels = getSettings().getInteger(LEVELS, 5);
            int detailConfirmK = getSettings().getInteger(DETAIL_CONFIRM_K, 2);
            
            // Need enough data
            if (index < windowLength) {
                // Clear momentum values when we don't have enough data
                series.setDouble(index, Values.MOMENTUM_SUM, null);
                series.setDouble(index, Values.SLOPE, null);
                return;
            }
            
            // Extract price window
            double[] prices = extractPriceWindow(series, index, windowLength, ctx);
            
            // Perform SWT
            VectorWaveSwtAdapter.SwtResult swtResult = swtAdapter.transform(prices, levels);
            
            // Apply thresholding to remove noise from detail coefficients
            applyThresholding(swtResult);
            
            // Choose between denoised signal or just approximation
            boolean useDenoised = getSettings().getBoolean(USE_DENOISED, false);
            double currentTrend;
            double previousTrend;
            
            if (useDenoised) {
                // Use partial reconstruction with thresholded details for denoising
                // Reconstruct with fewer levels for smoother result
                int reconstructLevels = Math.max(2, levels - 1);
                double[] denoisedSignal = swtResult.reconstruct(reconstructLevels);
                currentTrend = denoisedSignal[denoisedSignal.length - 1];
                previousTrend = denoisedSignal.length > 1 ? 
                        denoisedSignal[denoisedSignal.length - 2] : currentTrend;
            } else {
                // Use approximation only (smooth trend without detail coefficients)
                double[] approximation = swtResult.getApproximation();
                currentTrend = approximation[approximation.length - 1];
                previousTrend = approximation.length > 1 ? 
                        approximation[approximation.length - 2] : currentTrend;
            }
            
            series.setDouble(index, Values.AJ, currentTrend);
            
            // Calculate trend slope
            double slope = currentTrend - previousTrend;
            series.setDouble(index, Values.SLOPE, slope);
            
            // Calculate cross-scale momentum
            int momentumSum = calculateMomentumSum(swtResult, detailConfirmK);
            series.setDouble(index, Values.MOMENTUM_SUM, (double) momentumSum);
            
            // Store detail signs
            storeDetailSigns(series, index, swtResult, detailConfirmK);
            
            // Determine filter states
            boolean longFilter = slope > 0 && momentumSum > 0;
            boolean shortFilter = slope < 0 && momentumSum < 0;
            
            series.setDouble(index, Values.LONG_FILTER, longFilter ? 1.0 : 0.0);
            series.setDouble(index, Values.SHORT_FILTER, shortFilter ? 1.0 : 0.0);
            
            // Generate signals
            generateSignals(series, index, longFilter, shortFilter);
            
            // Calculate and display WATR if enabled
            if (getSettings().getBoolean(SHOW_WATR, false)) {
                calculateWatr(series, index, swtResult, currentTrend);
            }
            
            // Mark this bar as complete (following MotiveWave best practice)
            series.setComplete(index);
            
            logger.trace("SWT calculation at {}: trend={:.2f}, slope={:.4f}, momentum={}", 
                        index, currentTrend, slope, momentumSum);
                        
        } catch (Exception e) {
            logger.error("Error in SWT calculation at index {}", index, e);
        }
    }
    
    private double[] extractPriceWindow(DataSeries series, int index, int windowLength, DataContext ctx) {
        double[] prices = new double[windowLength];
        double lastValid = 0.0;
        
        for (int i = 0; i < windowLength; i++) {
            int barIndex = index - windowLength + 1 + i;
            Double close = series.getDouble(barIndex, Enums.BarInput.CLOSE, ctx.getInstrument());
            
            if (close != null) {
                prices[i] = close;
                lastValid = close;
            } else {
                prices[i] = lastValid;
            }
        }
        
        return prices;
    }
    
    private void applyThresholding(VectorWaveSwtAdapter.SwtResult swtResult) {
        String thresholdMethodStr = getSettings().getString(THRESHOLD_METHOD, "Universal");
        String shrinkageTypeStr = getSettings().getString(SHRINKAGE_TYPE, "Soft");
        
        Thresholds.ThresholdMethod method = Thresholds.ThresholdMethod.fromString(thresholdMethodStr);
        Thresholds.ShrinkageType shrinkage = Thresholds.ShrinkageType.fromString(shrinkageTypeStr);
        
        boolean softThresholding = shrinkage == Thresholds.ShrinkageType.SOFT;
        
        // Apply per-level thresholding
        for (int level = 1; level <= swtResult.getLevels(); level++) {
            double[] detailCoeffs = swtResult.getDetail(level);
            double threshold = Thresholds.calculateThreshold(detailCoeffs, method, level);
            
            swtResult.applyShrinkage(level, threshold, softThresholding);
        }
    }
    
    private int calculateMomentumSum(VectorWaveSwtAdapter.SwtResult swtResult, int k) {
        int sum = 0;
        int levelsToUse = Math.min(k, swtResult.getLevels());
        
        for (int level = 1; level <= levelsToUse; level++) {
            double[] detail = swtResult.getDetail(level);
            if (detail.length > 0) {
                double lastCoeff = detail[detail.length - 1];
                sum += (lastCoeff > 0) ? 1 : (lastCoeff < 0) ? -1 : 0;
            }
        }
        
        return sum;
    }
    
    private void storeDetailSigns(DataSeries series, int index, VectorWaveSwtAdapter.SwtResult swtResult, int k) {
        Values[] signValues = {Values.D1_SIGN, Values.D2_SIGN, Values.D3_SIGN};
        
        for (int level = 1; level <= Math.min(k, Math.min(3, swtResult.getLevels())); level++) {
            double[] detail = swtResult.getDetail(level);
            if (detail.length > 0) {
                double lastCoeff = detail[detail.length - 1];
                double sign = (lastCoeff > 0) ? 1.0 : (lastCoeff < 0) ? -1.0 : 0.0;
                series.setDouble(index, signValues[level - 1], sign);
            }
        }
    }
    
    private void generateSignals(DataSeries series, int index, boolean longFilter, boolean shortFilter) {
        if (!getSettings().getBoolean(ENABLE_SIGNALS, true)) {
            return;
        }
        
        // Detect transitions
        boolean wasLongFilter = lastLongFilter;
        boolean wasShortFilter = lastShortFilter;
        boolean wasFlat = !wasLongFilter && !wasShortFilter;
        
        if (wasFlat && longFilter) {
            // Note: Marker API not available in current SDK version
            // series.setMarker(index, new Marker(Color.GREEN, MarkerType.CIRCLE));
            // series.setSignal(index, Signals.LONG_ENTER);
            logger.debug("Long entry signal at index {}", index);
        } else if (wasFlat && shortFilter) {
            // Note: Marker API not available in current SDK version
            // series.setMarker(index, new Marker(Color.RED, MarkerType.CIRCLE));
            // series.setSignal(index, Signals.SHORT_ENTER);
            logger.debug("Short entry signal at index {}", index);
        } else if ((wasLongFilter || wasShortFilter) && !longFilter && !shortFilter) {
            // Note: Marker API not available in current SDK version
            // series.setMarker(index, new Marker(Color.GRAY, MarkerType.SQUARE));
            // series.setSignal(index, Signals.FLAT_EXIT);
            logger.debug("Flat exit signal at index {}", index);
        }
        
        // Update state
        lastLongFilter = longFilter;
        lastShortFilter = shortFilter;
    }
    
    private void calculateWatr(DataSeries series, int index, VectorWaveSwtAdapter.SwtResult swtResult, double centerPrice) {
        int watrK = getSettings().getInteger(WATR_K, 2);
        double watrMultiplier = getSettings().getDouble(WATR_MULTIPLIER, 2.0);
        
        double watr = waveletAtr.calculate(swtResult, watrK);
        series.setDouble(index, Values.WATR, watr);
        
        // Calculate bands
        double upperBand = centerPrice + watr * watrMultiplier;
        double lowerBand = centerPrice - watr * watrMultiplier;
        
        series.setDouble(index, Values.WATR_UPPER, upperBand);
        series.setDouble(index, Values.WATR_LOWER, lowerBand);
    }
}