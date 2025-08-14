package com.prophetizo.wavelets.swt;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.draw.Marker;
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
    
    /**
     * Settings keys are organized into logical groups:
     * 1. Wavelet Transform - Core SWT configuration
     * 2. Thresholding - Denoising parameters
     * 3. Momentum & Signal Generation - Trading signal parameters
     * 4. WATR (Wavelet ATR) - Volatility measurement
     */
    
    // Settings keys - Wavelet Transform
    public static final String WAVELET_TYPE = "WAVELET_TYPE";
    public static final String LEVELS = "LEVELS";
    public static final String WINDOW_LENGTH = "WINDOW_LENGTH";
    
    // Settings keys - Thresholding
    public static final String THRESHOLD_METHOD = "THRESHOLD_METHOD";
    public static final String SHRINKAGE_TYPE = "SHRINKAGE_TYPE";
    public static final String USE_DENOISED = "USE_DENOISED";
    
    // Settings keys - Momentum & Signal Generation
    public static final String DETAIL_CONFIRM_K = "DETAIL_CONFIRM_K";
    public static final String MOMENTUM_TYPE = "MOMENTUM_TYPE";
    public static final String MOMENTUM_THRESHOLD = "MOMENTUM_THRESHOLD";
    public static final String MOMENTUM_WINDOW = "MOMENTUM_WINDOW";
    public static final String MOMENTUM_SMOOTHING = "MOMENTUM_SMOOTHING";
    public static final String MOMENTUM_SCALING_FACTOR = "MOMENTUM_SCALING_FACTOR";
    public static final String MIN_SLOPE_THRESHOLD = "MIN_SLOPE_THRESHOLD";
    public static final String ENABLE_SIGNALS = "ENABLE_SIGNALS";
    
    // Settings keys - WATR (Wavelet ATR)
    public static final String SHOW_WATR = "SHOW_WATR";
    public static final String WATR_K = "WATR_K";
    public static final String WATR_MULTIPLIER = "WATR_MULTIPLIER";
    public static final String WATR_SCALE_METHOD = "WATR_SCALE_METHOD";
    public static final String WATR_SCALE_FACTOR = "WATR_SCALE_FACTOR";
    public static final String WATR_LEVEL_DECAY = "WATR_LEVEL_DECAY";
    
    // Path keys
    public static final String AJ_PATH = "AJ_PATH";
    public static final String WATR_UPPER_PATH = "WATR_UPPER_PATH";
    public static final String WATR_LOWER_PATH = "WATR_LOWER_PATH";
    
    // Plot keys
    public static final String MOMENTUM_PLOT = "MOMENTUM_PLOT";
    
    // Path keys for momentum plot
    public static final String MOMENTUM_SUM_PATH = "MOMENTUM_SUM_PATH";
    public static final String SLOPE_PATH = "SLOPE_PATH";
    
    // Marker keys
    public static final String LONG_MARKER = "LONG_MARKER";
    public static final String SHORT_MARKER = "SHORT_MARKER";
    
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
    
    // Signals - state-based, not action-based
    public enum Signals {
        LONG, SHORT
    }
    
    // Core components - volatile for thread safety
    private volatile VectorWaveSwtAdapter swtAdapter;
    private volatile WaveletAtr waveletAtr;
    
    // State tracking - volatile for thread safety in multi-threaded calculation engine
    private volatile String currentWaveletType = null;
    private volatile Integer currentLevels = null;
    private volatile Integer currentWindowLength = null;
    private volatile boolean lastLongFilter = false;
    private volatile boolean lastShortFilter = false;
    
    // No need for marker tracking - SDK handles this when we follow the pattern:
    // Only add markers when series.isBarComplete(index) is true
    
    // Sliding window buffer for streaming updates - synchronized for thread safety
    // These fields must be accessed together atomically, so we use synchronization
    private double[] priceBuffer = null;
    private int bufferStartIndex = -1;
    private boolean bufferInitialized = false;
    protected final Object bufferLock = new Object(); // Lock for buffer operations (protected for test access)
    
    // Momentum smoothing - volatile for thread safety in MotiveWave's multi-threaded calculation engine
    private volatile double smoothedMomentum = 0.0;
    private static final double DEFAULT_MOMENTUM_SMOOTHING = 0.5; // Default EMA smoothing factor (0.1-0.9, higher = more responsive)
    private static final int DEFAULT_MOMENTUM_WINDOW = 10; // Default window for RMS calculation
    private static final double DEFAULT_MOMENTUM_SCALING_FACTOR = 100.0; // Default momentum scaling factor
    
    // Momentum type enum for efficient comparison
    private enum MomentumType {
        SUM,  // Sum of Details (D₁ + D₂ + ...)
        SIGN; // Sign Count (±1 per level)
        
        private static final Logger enumLogger = LoggerConfig.getLogger(MomentumType.class);
        
        /**
         * Converts a string value to a MomentumType enum.
         * 
         * @param value the string value to convert (case-insensitive)
         * @return the corresponding MomentumType, or SUM as default
         */
        static MomentumType fromString(String value) {
            if (value == null) {
                return SUM; // Default to SUM for null input
            }
            
            // Use switch for clarity and extensibility
            String upperValue = value.toUpperCase().trim();
            switch (upperValue) {
                case "SIGN":
                    return SIGN;
                case "SUM":
                    return SUM;
                default:
                    // Log warning for unexpected value and default to SUM
                    if (enumLogger.isWarnEnabled()) {
                        enumLogger.warn("Unknown momentum type '{}', defaulting to SUM", value);
                    }
                    return SUM;
            }
        }
    }
    
    // Thread-safe cached settings using immutable object pattern
    // All related settings are updated atomically to prevent inconsistent state
    private static class CachedSettings {
        final MomentumType momentumType;
        final int momentumWindow;
        final double levelWeightDecay;
        final double momentumScalingFactor;
        
        CachedSettings(MomentumType momentumType, int momentumWindow, 
                      double levelWeightDecay, double momentumScalingFactor) {
            this.momentumType = momentumType;
            this.momentumWindow = momentumWindow;
            this.levelWeightDecay = levelWeightDecay;
            this.momentumScalingFactor = momentumScalingFactor;
        }
    }
    
    // Single volatile reference ensures all settings are updated atomically
    // Uses a safe default to avoid NPE, will be replaced with actual settings in onLoad()
    private volatile CachedSettings cachedSettings = new CachedSettings(
        MomentumType.SUM, 
        DEFAULT_MOMENTUM_WINDOW, 
        0.5, 
        DEFAULT_MOMENTUM_SCALING_FACTOR
    );
    
    // Flag to track if settings have been initialized from framework
    private volatile boolean settingsInitialized = false;
    
    /**
     * WATR Scaling Methods for different market conditions and instruments.
     * 
     * Different markets and price ranges require different scaling approaches:
     * - LINEAR: Direct multiplication, good for stable markets
     * - SQRT: Square root scaling, dampens effect at higher prices
     * - LOG: Logarithmic scaling, for wide price ranges
     * - ADAPTIVE: Automatically adjusts based on recent volatility
     */
    
    /**
     * Default WATR scaling factors empirically determined for common instruments.
     * 
     * These values were derived through backtesting across multiple market conditions:
     * - ES/NQ (6000-20000 range): Factor 100 for LINEAR, 10 for SQRT
     * - Forex (0.5-2.0 range): Factor 0.01 for LINEAR, 0.1 for SQRT  
     * - Crypto (1000-100000 range): Factor 1000 for LINEAR, 30 for SQRT
     * - Stocks (10-1000 range): Factor 10 for LINEAR, 3 for SQRT
     * 
     * The factors aim to normalize WATR to approximately 0.1-1.0% of price
     * across different instruments, providing consistent risk metrics.
     */
    private static final double DEFAULT_LINEAR_FACTOR = 100.0;
    private static final double DEFAULT_SQRT_FACTOR = 10.0;
    private static final double DEFAULT_LOG_FACTOR = 5.0;
    
    /**
     * Default minimum slope threshold in absolute price points.
     * 
     * This threshold filters out signals in choppy/flat markets by requiring
     * a minimum absolute change in the trend line between bars.
     * The value 0.05 means the trend must change by at least 0.05 points
     * from one bar to the next to generate a signal.
     * 
     * This value was empirically determined to balance:
     * - Sensitivity: Low enough to capture legitimate trend changes
     * - Noise rejection: High enough to avoid false signals in ranging markets
     * 
     * For ES futures:
     * - Typical bar-to-bar trend changes: 0.25 - 2.0 points
     * - Threshold of 0.05 filters out very small noise
     * - Allows most legitimate moves through
     * 
     * Adjustment guidelines:
     * - 0.00: Disable slope filtering entirely
     * - 0.01-0.05: Very sensitive (catches small moves)
     * - 0.05-0.10: Balanced (default range)
     * - 0.10-0.50: Conservative (filters more aggressively)
     * - 0.50+: Very conservative (only large moves)
     * 
     * Note: This is an absolute threshold in price points, NOT a percentage.
     */
    private static final double DEFAULT_MIN_SLOPE_THRESHOLD = 0.05; // Absolute price points (e.g., 0.05 = 0.05 point minimum move)
    
    @Override
    public void onLoad(Defaults defaults) {
        logger.info("Study onLoad - initializing");
        
        // CRITICAL: Call super.onLoad first to let framework handle setup
        super.onLoad(defaults);
        
        // Update minimum bars requirement based on window length after framework initialization
        setMinBars(getSettings().getInteger(WINDOW_LENGTH, 4096));
        
        // Cache momentum settings on initialization and mark as initialized
        updateCachedSettings();
        settingsInitialized = true;
    }
    
    /**
     * Ensures settings have been initialized from the framework.
     * Thread-safe: Uses double-checked locking to avoid race conditions.
     */
    private void ensureSettingsInitialized() {
        if (!settingsInitialized) {
            synchronized (this) {
                if (!settingsInitialized) {
                    updateCachedSettings();
                    settingsInitialized = true;
                    logger.warn("Settings were not initialized, forcing initialization. This should not happen in normal operation.");
                }
            }
        }
    }
    
    /**
     * Updates cached settings to avoid repeated getSettings() calls during calculation.
     * This method should be called during initialization and when settings are updated.
     * 
     * Thread-safe: Creates a new immutable settings object and atomically swaps the reference.
     * This ensures all related settings are always consistent with each other.
     * Safe to call multiple times - will always create a fresh settings object from current values.
     * 
     * Called automatically by onLoad() and onSettingsUpdated().
     */
    private void updateCachedSettings() {
        // Read all settings into local variables first
        MomentumType momentumType = MomentumType.fromString(getSettings().getString(MOMENTUM_TYPE, "SUM"));
        int momentumWindow = getSettings().getInteger(MOMENTUM_WINDOW, DEFAULT_MOMENTUM_WINDOW);
        double levelWeightDecay = getSettings().getDouble(WATR_LEVEL_DECAY, 0.5);
        double momentumScalingFactor = getSettings().getDouble(MOMENTUM_SCALING_FACTOR, DEFAULT_MOMENTUM_SCALING_FACTOR);
        
        // Create new immutable settings object
        CachedSettings newSettings = new CachedSettings(
            momentumType, 
            momentumWindow, 
            levelWeightDecay, 
            momentumScalingFactor
        );
        
        // Atomically swap the reference - all settings updated together
        this.cachedSettings = newSettings;
        
        if (logger.isDebugEnabled()) {
            logger.debug("Cached settings updated atomically: type={}, window={}, decay={}, scaling={}", 
                        momentumType.name(), momentumWindow, 
                        levelWeightDecay, momentumScalingFactor);
        }
    }
    
    @Override
    public void onSettingsUpdated(DataContext ctx) {
        // CRITICAL: Update cached settings FIRST before any other operations
        // This ensures any calculations triggered during settings update use the new values
        updateCachedSettings();
        
        logger.info("Settings updated - triggering recalculation");
        
        // Log which settings might have changed (for debugging)
        if (logger.isDebugEnabled()) {
            logger.debug("Current settings: wavelet={}, levels={}, window={}, threshold={}, shrinkage={}, k={}, watr={}", 
                        getSettings().getString(WAVELET_TYPE, "db4"),
                        getSettings().getInteger(LEVELS, 5),
                        getSettings().getInteger(WINDOW_LENGTH, 4096),
                        getSettings().getString(THRESHOLD_METHOD, "Universal"),
                        getSettings().getString(SHRINKAGE_TYPE, "Soft"),
                        getSettings().getInteger(DETAIL_CONFIRM_K, 2),
                        getSettings().getBoolean(SHOW_WATR, false));
        }
        
        // Clear state to force re-initialization of all components
        clearState();
        
        // Note: We don't call clearFigures() here as it can cause JavaFX threading issues
        // The framework will handle figure updates when we recalculate
        
        // Update minimum bars requirement
        setMinBars(getSettings().getInteger(WINDOW_LENGTH, 4096));
        
        // Update momentum plot range for new k value or momentum type change
        updateMomentumPlotRange();
        
        // Call super to trigger recalculation
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
        
        // Clear buffer - synchronized to ensure atomic updates
        synchronized (bufferLock) {
            priceBuffer = null;
            bufferStartIndex = -1;
            bufferInitialized = false;
        }
        
        // Reset momentum smoothing
        smoothedMomentum = 0.0;
        
        // Note: Do NOT call clearFigures() in clearState() - only call it in onSettingsUpdated()
        // clearState() is for internal state reset during normal calculation cycles
        // clearFigures() should only be called when settings change to force redraw
    }
    
    // The SDK pattern is to NOT override calculateValues() when using
    // incremental markers. Only override calculate(int index, DataContext ctx)
    
    private void updateMomentumPlotRange() {
        // Get the runtime descriptor and momentum plot
        var desc = getRuntimeDescriptor();
        if (desc != null) {
            var momentumPlot = desc.getPlot(MOMENTUM_PLOT);
            if (momentumPlot != null) {
                MomentumType momentumType = MomentumType.fromString(getSettings().getString(MOMENTUM_TYPE, "SUM"));
                
                if (momentumType == MomentumType.SIGN) {
                    // For SIGN mode: range is -k to +k
                    int k = getSettings().getInteger(DETAIL_CONFIRM_K, 2);
                    int range = Math.max(k + 1, 3);
                    momentumPlot.setFixedTopValue(range);
                    momentumPlot.setFixedBottomValue(-range);
                    logger.debug("Updated momentum plot range (SIGN): {} to {}", -range, range);
                } else {
                    // For SUM mode: use auto-scaling since coefficient sums can vary widely
                    // Remove fixed scaling to let the plot auto-adjust
                    momentumPlot.setFixedTopValue(null);
                    momentumPlot.setFixedBottomValue(null);
                    logger.debug("Momentum plot using auto-scaling (SUM mode)");
                }
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
        signalGroup.addRow(new DiscreteDescriptor(MOMENTUM_TYPE, "Momentum Calculation", "SUM",
                Arrays.asList(
                        new NVP("SUM", "Sum of Details (D₁ + D₂ + ...)"),
                        new NVP("SIGN", "Sign Count (±1 per level)")
                )));
        signalGroup.addRow(new DoubleDescriptor(MOMENTUM_THRESHOLD, "Momentum Threshold", 1.0, 0.0, 100.0, 0.1));
        signalGroup.addRow(new DoubleDescriptor(MIN_SLOPE_THRESHOLD, "Min Slope Threshold (Points)", DEFAULT_MIN_SLOPE_THRESHOLD, 0.0, 5.0, 0.01));
        // Note: Min Slope Threshold is in absolute price points (0.05 = 0.05 point minimum move, NOT percentage)
        signalGroup.addRow(new DoubleDescriptor(MOMENTUM_SMOOTHING, "Momentum Smoothing (α)", DEFAULT_MOMENTUM_SMOOTHING, 0.1, 0.9, 0.05));
        // Note: Momentum smoothing alpha - 0.1 = heavy smoothing, 0.5 = balanced, 0.9 = minimal smoothing
        signalGroup.addRow(new IntegerDescriptor(MOMENTUM_WINDOW, "Momentum Window (bars)", DEFAULT_MOMENTUM_WINDOW, 5, 50, 1));
        // Note: Number of bars used for RMS energy calculation
        signalGroup.addRow(new DoubleDescriptor(MOMENTUM_SCALING_FACTOR, "Momentum Scaling Factor", DEFAULT_MOMENTUM_SCALING_FACTOR, 1.0, 1000.0, 1.0));
        // Note: Scaling factor to improve momentum visibility (100.0 = 100x amplification)
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
        
        // Add scaling configuration
        watrGroup.addRow(new DiscreteDescriptor(WATR_SCALE_METHOD, "WATR Scaling Method", "SQRT",
                Arrays.asList(
                        new NVP("LINEAR", "Linear (direct multiplication)"),
                        new NVP("SQRT", "Square Root (dampened scaling)"),
                        new NVP("LOG", "Logarithmic (compressed scaling)"),
                        new NVP("ADAPTIVE", "Adaptive (volatility-based)")
                )));
        watrGroup.addRow(new DoubleDescriptor(WATR_SCALE_FACTOR, "WATR Scale Factor", 
                DEFAULT_SQRT_FACTOR, 0.01, 1000.0, 0.1));
        watrGroup.addRow(new DoubleDescriptor(WATR_LEVEL_DECAY, "WATR Level Weight Decay", 
                0.5, 0.1, 1.0, 0.05));
        // Note: Level Weight Decay controls contribution of coarser scales
        // - 0.3-0.4: More weight on coarser scales (trending markets)
        // - 0.5-0.6: Balanced weighting (default)
        // - 0.7-0.8: More weight on finer scales (volatile/choppy markets)
        
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
        
        // Markers tab
        var markersTab = sd.addTab("Markers");
        var markersGroup = markersTab.addGroup("State Markers");
        markersGroup.addRow(new MarkerDescriptor(LONG_MARKER, "Long State", 
                Enums.MarkerType.TRIANGLE, Enums.Size.SMALL, 
                new Color(0, 200, 0), new Color(0, 150, 0), true, true));
        markersGroup.addRow(new MarkerDescriptor(SHORT_MARKER, "Short State",
                Enums.MarkerType.TRIANGLE, Enums.Size.SMALL,
                new Color(200, 0, 0), new Color(150, 0, 0), true, true));
        
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
        
        // Initial scale - will be updated based on momentum type
        // For SUM mode, we'll use auto-scaling
        // For SIGN mode, we'll use fixed scale based on k
        updateMomentumPlotRange();
        
        // Add zero line
        momentumPlot.addHorizontalLine(new LineInfo(0.0, null, 1.0f, new float[]{3, 3}));
        
        // Signals - state based, not action based
        desc.declareSignal(Signals.LONG, "Long State");
        desc.declareSignal(Signals.SHORT, "Short State");
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
                
                // Reset buffer when window size changes - synchronized for thread safety
                if (windowChanged || priceBuffer == null) {
                    synchronized (bufferLock) {
                        priceBuffer = new double[windowLength];
                        bufferStartIndex = -1;
                        bufferInitialized = false;
                    }
                }
                
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
            // Ensure settings are initialized from framework (defensive check)
            ensureSettingsInitialized();
            
            // Use cached settings for thread-safe access
            CachedSettings settings = this.cachedSettings;
            this.waveletAtr = new WaveletAtr(14, settings.levelWeightDecay); // 14-period smoothing with configurable decay
            logger.debug("Initialized WATR component with level decay: {}", settings.levelWeightDecay);
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
    
    // REMOVED onBarUpdate - not needed since requiresBarUpdates=false
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        // This is called for each bar by the framework
        // Following the SDK pattern for incremental marker addition
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
            
            // Update sliding window buffer efficiently
            double[] prices = updateSlidingWindow(series, index, windowLength, ctx);
            
            // Perform SWT
            VectorWaveSwtAdapter.SwtResult swtResult = swtAdapter.transform(prices, levels);
            
            // CRITICAL: Calculate momentum from RAW detail coefficients BEFORE thresholding
            // This preserves the actual market momentum information
            double momentumSum = calculateMomentumSum(swtResult, detailConfirmK);
            series.setDouble(index, Values.MOMENTUM_SUM, momentumSum);
            
            // NOW apply thresholding for denoising (only affects reconstruction, not momentum)
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
            
            // Store detail signs
            storeDetailSigns(series, index, swtResult, detailConfirmK);
            
            // Determine filter states based on slope and detail sum with threshold
            // Long: positive slope AND momentum above threshold
            // Short: negative slope AND momentum below negative threshold
            double momentumThreshold = getSettings().getDouble(MOMENTUM_THRESHOLD, 1.0);
            
            // Require minimum slope to avoid signals in choppy/flat markets
            // The slope threshold is meant to filter out noise, not strong moves
            // Setting it to 0 disables the filter, 0.05 means filter slopes < 0.05 points
            // This is an absolute threshold in price points, not a percentage
            double minSlope = getSettings().getDouble(MIN_SLOPE_THRESHOLD, DEFAULT_MIN_SLOPE_THRESHOLD);
            
            boolean longFilter = slope > minSlope && momentumSum > momentumThreshold;
            boolean shortFilter = slope < -minSlope && momentumSum < -momentumThreshold;
            
            // Debug logging to understand why signals aren't generating
            if (logger.isDebugEnabled()) {
                if (index % 50 == 0) { // Log every 50 bars to avoid spam
                    logger.debug("Signal check at {}: slope={}, minSlope={}, momentum={}, threshold={}, long={}, short={}", 
                                index, 
                                String.format("%.6f", slope), 
                                String.format("%.6f", minSlope), 
                                String.format("%.2f", momentumSum), 
                                String.format("%.2f", momentumThreshold), 
                                longFilter, shortFilter);
                    logger.debug("  Trend: current={}, previous={}, diff={}", 
                                String.format("%.2f", currentTrend), 
                                String.format("%.2f", previousTrend), 
                                String.format("%.4f", currentTrend - previousTrend));
                }
            }
            
            series.setDouble(index, Values.LONG_FILTER, longFilter ? 1.0 : 0.0);
            series.setDouble(index, Values.SHORT_FILTER, shortFilter ? 1.0 : 0.0);
            
            // Generate signals
            generateSignals(ctx, series, index, longFilter, shortFilter);
            
            // Calculate WATR (always calculate for strategy use, only display if enabled)
            // The Strategy subclass needs WATR for stop calculations even when not displayed
            calculateWatr(series, index, swtResult, currentTrend);
            
            // Mark this bar as complete (following MotiveWave best practice)
            series.setComplete(index);
            
            if (logger.isTraceEnabled()) {
                logger.trace("SWT calculation at {}: trend={:.2f}, slope={:.4f}, momentum={}", 
                            index, currentTrend, slope, momentumSum);
            }
                        
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
    
    /**
     * Updates the sliding window buffer efficiently.
     * Only fetches new data points instead of the entire window.
     * Thread-safe: All buffer operations are synchronized.
     */
    private double[] updateSlidingWindow(DataSeries series, int index, int windowLength, DataContext ctx) {
        int startIndex = index - windowLength + 1;
        
        // Synchronize all buffer operations for thread safety
        synchronized (bufferLock) {
            // Check if we need to initialize or completely refresh the buffer
            if (!bufferInitialized || priceBuffer == null || 
                bufferStartIndex < 0 || startIndex < bufferStartIndex ||
                startIndex > bufferStartIndex + windowLength) {
                
                // Full refresh needed
                if (logger.isTraceEnabled()) {
                    logger.trace("Full buffer refresh at index {}", index);
                }
                priceBuffer = extractPriceWindow(series, index, windowLength, ctx);
                bufferStartIndex = startIndex;
                bufferInitialized = true;
                return priceBuffer.clone(); // Return copy to prevent external modification
            }
            
            // Sliding window update - only fetch new bars
            int shift = startIndex - bufferStartIndex;
            
            if (shift > 0 && shift < windowLength) {
                // Slide the window forward
                if (logger.isTraceEnabled()) {
                    logger.trace("Sliding window by {} positions at index {}", shift, index);
                }
                
                // Shift existing data to the left
                System.arraycopy(priceBuffer, shift, priceBuffer, 0, windowLength - shift);
                
                // Fetch only the new data points
                double lastValid = priceBuffer[windowLength - shift - 1];
                for (int i = 0; i < shift; i++) {
                    int barIndex = index - shift + 1 + i;
                    Double close = series.getDouble(barIndex, Enums.BarInput.CLOSE, ctx.getInstrument());
                    
                    if (close != null) {
                        priceBuffer[windowLength - shift + i] = close;
                        lastValid = close;
                    } else {
                        priceBuffer[windowLength - shift + i] = lastValid;
                    }
                }
                
                bufferStartIndex = startIndex;
            } else if (shift == 0) {
                // Same window, just update the last bar (for tick updates)
                Double close = series.getDouble(index, Enums.BarInput.CLOSE, ctx.getInstrument());
                if (close != null) {
                    priceBuffer[windowLength - 1] = close;
                }
            }
            
            return priceBuffer.clone(); // Return copy to prevent external modification
        } // End of synchronized block
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
    
    private double calculateMomentumSum(VectorWaveSwtAdapter.SwtResult swtResult, int k) {
        int levelsToUse = Math.min(k, swtResult.getLevels());
        // Ensure settings are initialized from framework (defensive check)
        ensureSettingsInitialized();
        
        // Get cached settings atomically - all values are consistent with each other
        CachedSettings settings = this.cachedSettings;
        MomentumType momentumType = settings.momentumType;
        int momentumWindow = settings.momentumWindow;
        double levelWeightDecay = settings.levelWeightDecay;
        double momentumScalingFactor = settings.momentumScalingFactor;
        
        double rawMomentum = 0.0;
        
        // Calculate weighted RMS energy from detail coefficients
        for (int level = 1; level <= levelsToUse; level++) {
            double[] detail = swtResult.getDetail(level);
            if (detail.length > 0) {
                // Use a window of recent coefficients for RMS calculation
                int windowSize = Math.min(momentumWindow, detail.length);
                int startIdx = Math.max(0, detail.length - windowSize);
                
                // Calculate RMS energy for this level over the window
                double levelEnergy = 0.0;
                double levelSum = 0.0;
                for (int i = startIdx; i < detail.length; i++) {
                    levelEnergy += detail[i] * detail[i];
                    levelSum += detail[i];
                }
                
                // Weight factor: finer scales (lower levels) get more weight
                // Level 1 = 100%, Level 2 = 67%, Level 3 = 50%
                double weight = 1.0 / (1.0 + (level - 1) * levelWeightDecay);
                
                double contribution = 0.0;
                if (momentumType == MomentumType.SUM) {
                    // Use weighted average of coefficients (preserves sign)
                    double avgCoeff = levelSum / windowSize;
                    contribution = avgCoeff * weight;
                    rawMomentum += contribution;
                } else {
                    // Sign-based: use RMS with sign of average
                    double rms = Math.sqrt(levelEnergy / windowSize);
                    double sign = Math.signum(levelSum);
                    contribution = sign * rms * weight;
                    rawMomentum += contribution;
                }
                
                if (logger.isTraceEnabled()) {
                    logger.trace("Level {} momentum: window={}, weight={:.2f}, contribution={:.4f}", 
                                level, windowSize, weight, contribution);
                }
            }
        }
        
        // Scale the momentum to make it more visible (using pre-fetched scaling factor)
        rawMomentum *= momentumScalingFactor;
        
        // Apply exponential smoothing to filter noise
        if (smoothedMomentum == 0.0) {
            // Initialize with first value
            smoothedMomentum = rawMomentum;
        } else {
            // EMA update
            double alpha = getSettings().getDouble(MOMENTUM_SMOOTHING, DEFAULT_MOMENTUM_SMOOTHING);
            smoothedMomentum = alpha * rawMomentum + (1.0 - alpha) * smoothedMomentum;
        }
        
        return smoothedMomentum;
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
    
    private void generateSignals(DataContext ctx, DataSeries series, int index, boolean longFilter, boolean shortFilter) {
        if (!getSettings().getBoolean(ENABLE_SIGNALS, true)) {
            return;
        }
        
        // IMPORTANT: Only add markers when the bar is complete to prevent duplicates
        // This follows the MotiveWave SDK pattern and ensures markers persist
        boolean barComplete = series.isBarComplete(index);
        
        // Detect transitions
        boolean wasLongFilter = lastLongFilter;
        boolean wasShortFilter = lastShortFilter;
        
        // Get current price for signal value
        double price = series.getClose(index);
        double trendValue = series.getDouble(index, Values.AJ, price);
        
        // According to spec:
        // Entry: slope > 0 AND momentum > 0 (long), slope < 0 AND momentum < 0 (short)
        // Exit: slope loss OR momentum sign flip
        
        // Emit state signals - not entry decisions
        if (longFilter) {
            series.setBoolean(index, Signals.LONG, true);
            
            // Only add marker on state transition
            if (barComplete && !wasLongFilter) {
                try {
                    var marker = getSettings().getMarker(LONG_MARKER);
                    if (marker != null && marker.isEnabled()) {
                        var coord = new Coordinate(series.getStartTime(index), trendValue);
                        String msg = String.format("Long State @ %.2f", price);
                        addFigure(new Marker(coord, Enums.Position.BOTTOM, marker, msg));
                    }
                } catch (Exception e) {
                    logger.trace("Could not add long marker: {}", e.getMessage());
                }
                
                ctx.signal(index, Signals.LONG, "Long State", price);
                logger.debug("Long state at index {} price {}", index, price);
            }
        } else if (shortFilter) {
            series.setBoolean(index, Signals.SHORT, true);
            
            // Only add marker on state transition
            if (barComplete && !wasShortFilter) {
                try {
                    var marker = getSettings().getMarker(SHORT_MARKER);
                    if (marker != null && marker.isEnabled()) {
                        var coord = new Coordinate(series.getStartTime(index), trendValue);
                        String msg = String.format("Short State @ %.2f", price);
                        addFigure(new Marker(coord, Enums.Position.TOP, marker, msg));
                    }
                } catch (Exception e) {
                    logger.trace("Could not add short marker: {}", e.getMessage());
                }
                
                ctx.signal(index, Signals.SHORT, "Short State", price);
                logger.debug("Short state at index {} price {}", index, price);
            }
        }
        
        // Update state
        lastLongFilter = longFilter;
        lastShortFilter = shortFilter;
    }
    
    private void calculateWatr(DataSeries series, int index, VectorWaveSwtAdapter.SwtResult swtResult, double centerPrice) {
        int watrK = getSettings().getInteger(WATR_K, 2);
        double watrMultiplier = getSettings().getDouble(WATR_MULTIPLIER, 2.0);
        
        double rawWatr = waveletAtr.calculate(swtResult, watrK);
        
        // Scale WATR based on configured method and factor
        String scaleMethod = getSettings().getString(WATR_SCALE_METHOD, "SQRT"); // Default to Square Root
        double scaleFactor = getSettings().getDouble(WATR_SCALE_FACTOR, DEFAULT_SQRT_FACTOR);
        
        double priceScaleFactor;
        switch (scaleMethod) {
            case "LINEAR":
                // Direct linear scaling: good for stable price ranges
                priceScaleFactor = centerPrice / scaleFactor;
                break;
            case "SQRT":
                // Square root scaling: dampens effect at higher prices
                // Good for indices like ES/NQ that range from 1000s to 10000s
                priceScaleFactor = Math.sqrt(centerPrice) / scaleFactor;
                break;
            case "LOG":
                // Logarithmic scaling: for very wide price ranges
                // Good for crypto or penny stocks to mega-caps
                priceScaleFactor = Math.log(centerPrice) / scaleFactor;
                break;
            case "ADAPTIVE":
                // Adaptive scaling based on recent price volatility
                // Uses 20-period standard deviation as reference
                double recentVol = series.std(index, 20, Enums.BarInput.CLOSE);
                priceScaleFactor = recentVol > 0 ? centerPrice / (scaleFactor * recentVol) : 1.0;
                break;
            default:
                // Default to square root scaling
                priceScaleFactor = Math.sqrt(centerPrice) / scaleFactor;
        }
        
        double watr = rawWatr * priceScaleFactor;
        
        if (logger.isDebugEnabled()) {
            if (index % 50 == 0) {
                logger.debug("WATR Calculation: method={}, rawWatr={}, scaleFactor={}, priceScale={}, scaledWatr={}, centerPrice={}", 
                            scaleMethod,
                            String.format("%.6f", rawWatr),
                            String.format("%.2f", scaleFactor),
                            String.format("%.2f", priceScaleFactor),
                            String.format("%.2f", watr),
                            String.format("%.2f", centerPrice));
            }
        }
        
        // Always store WATR value (needed by Strategy for stops)
        series.setDouble(index, Values.WATR, watr);
        
        // Only calculate and store bands if display is enabled
        if (getSettings().getBoolean(SHOW_WATR, false)) {
            double upperBand = centerPrice + watr * watrMultiplier;
            double lowerBand = centerPrice - watr * watrMultiplier;
            
            series.setDouble(index, Values.WATR_UPPER, upperBand);
            series.setDouble(index, Values.WATR_LOWER, lowerBand);
        }
    }
}