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
    private String lastWaveletType = null;
    private Integer lastLevels = null;
    private boolean lastLongFilter = false;
    private boolean lastShortFilter = false;
    
    @Override
    public void initialize(Defaults defaults) {
        logger.debug("Initializing SWT Trend + Momentum Study");
        
        SettingsDescriptor settings = new SettingsDescriptor();
        setSettingsDescriptor(settings);
        
        // General settings
        SettingTab generalTab = settings.addTab("General");
        SettingGroup waveletGroup = generalTab.addGroup("Wavelet Configuration");
        
        List<NVP> waveletOptions = StudyUIHelper.createWaveletOptions();
        waveletGroup.addRow(StudyUIHelper.createWaveletTypeDescriptor(WAVELET_TYPE, "db4", waveletOptions));
        waveletGroup.addRow(new IntegerDescriptor(LEVELS, "Decomposition Levels", 5, 2, 8, 1));
        waveletGroup.addRow(new IntegerDescriptor(WINDOW_LENGTH, "Window Length (bars)", 4096, 512, 8192, 256));
        
        SettingGroup thresholdGroup = generalTab.addGroup("Thresholding");
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
        
        SettingGroup signalGroup = generalTab.addGroup("Signal Configuration");
        signalGroup.addRow(new IntegerDescriptor(DETAIL_CONFIRM_K, "Detail Confirmation (k)", 2, 1, 3, 1));
        signalGroup.addRow(new BooleanDescriptor(ENABLE_SIGNALS, "Enable Trading Signals", true));
        
        // Display settings
        SettingTab displayTab = settings.addTab("Display");
        
        SettingGroup trendGroup = displayTab.addGroup("Trend Display");
        trendGroup.addRow(new PathDescriptor(AJ_PATH, "Approximation (A_J)",
                new Color(0, 150, 255), 2.0f, null, true, true, true));
        
        SettingGroup watrGroup = displayTab.addGroup("Wavelet ATR");
        watrGroup.addRow(new BooleanDescriptor(SHOW_WATR, "Show WATR Bands", false));
        watrGroup.addRow(new IntegerDescriptor(WATR_K, "WATR Detail Levels", 2, 1, 3, 1));
        watrGroup.addRow(new DoubleDescriptor(WATR_MULTIPLIER, "WATR Multiplier", 2.0, 1.0, 5.0, 0.1));
        watrGroup.addRow(new PathDescriptor(WATR_UPPER_PATH, "WATR Upper Band",
                new Color(255, 100, 100, 128), 1.0f, null, true, false, true));
        watrGroup.addRow(new PathDescriptor(WATR_LOWER_PATH, "WATR Lower Band",
                new Color(255, 100, 100, 128), 1.0f, null, true, false, true));
        
        // Runtime descriptor
        RuntimeDescriptor desc = new RuntimeDescriptor();
        setRuntimeDescriptor(desc);
        
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
        momentumPlot.declareIndicator(Values.MOMENTUM_SUM, "Momentum");
        momentumPlot.declareIndicator(Values.SLOPE, "Slope");
        momentumPlot.setRangeKeys(Values.MOMENTUM_SUM, Values.SLOPE);
        momentumPlot.addHorizontalLine(new LineInfo(0.0, null, 1.0f, new float[]{3, 3}));
        
        // Signals
        desc.declareSignal(Signals.LONG_ENTER, "Long Entry");
        desc.declareSignal(Signals.SHORT_ENTER, "Short Entry");
        desc.declareSignal(Signals.FLAT_EXIT, "Flat Exit");
        
        initializeComponents();
    }
    
    private void initializeComponents() {
        try {
            // Initialize with default wavelet
            this.swtAdapter = new VectorWaveSwtAdapter("db4");
            this.waveletAtr = new WaveletAtr(14); // 14-period smoothing
            
            logger.info("Initialized SWT components successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize SWT components", e);
        }
    }
    
    @Override
    public void onLoad(Defaults defaults) {
        logger.debug("SWT study loaded");
        checkAndUpdateSettings();
    }
    
    @Override
    public int getMinBars(DataContext ctx, BarSize bs) {
        return getSettings().getInteger(WINDOW_LENGTH, 4096);
    }
    
    private void checkAndUpdateSettings() {
        String waveletType = getSettings().getString(WAVELET_TYPE, "db4");
        Integer levels = getSettings().getInteger(LEVELS, 5);
        
        boolean settingsChanged = !waveletType.equals(lastWaveletType) || !levels.equals(lastLevels);
        
        if (settingsChanged || swtAdapter == null) {
            updateComponents(waveletType, levels);
            lastWaveletType = waveletType;
            lastLevels = levels;
            
            logger.debug("Settings updated: wavelet={}, levels={}", waveletType, levels);
        }
    }
    
    private void updateComponents(String waveletType, int levels) {
        try {
            // Map to VectorWave naming convention
            String vectorWaveType = mapWaveletName(waveletType);
            
            this.swtAdapter = new VectorWaveSwtAdapter(vectorWaveType);
            
            // Reset state
            if (waveletAtr != null) {
                waveletAtr.reset();
            }
            
            logger.info("Updated SWT components: wavelet={} -> {}, levels={}", 
                       waveletType, vectorWaveType, levels);
        } catch (Exception e) {
            logger.error("Failed to update SWT components", e);
        }
    }
    
    private String mapWaveletName(String displayName) {
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
        
        // Default fallback
        return "db4";
    }
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        try {
            DataSeries series = ctx.getDataSeries();
            
            // Check settings
            checkAndUpdateSettings();
            
            if (swtAdapter == null) {
                logger.warn("SWT adapter not initialized");
                return;
            }
            
            // Get configuration
            int windowLength = getSettings().getInteger(WINDOW_LENGTH, 4096);
            int levels = getSettings().getInteger(LEVELS, 5);
            int detailConfirmK = getSettings().getInteger(DETAIL_CONFIRM_K, 2);
            
            // Need enough data
            if (index < windowLength) {
                return;
            }
            
            // Extract price window
            double[] prices = extractPriceWindow(series, index, windowLength, ctx);
            if (prices == null) {
                return;
            }
            
            // Perform SWT
            VectorWaveSwtAdapter.SwtResult swtResult = swtAdapter.transform(prices, levels);
            
            // Apply thresholding
            applyThresholding(swtResult);
            
            // Get trend (approximation)
            double[] approximation = swtResult.reconstructApproximation();
            double currentTrend = approximation[approximation.length - 1];
            series.setDouble(index, Values.AJ, currentTrend);
            
            // Calculate trend slope
            double previousTrend = approximation.length > 1 ? 
                    approximation[approximation.length - 2] : currentTrend;
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