package com.morphiqlabs.wavelets.studies.analysis;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.study.RuntimeDescriptor;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;

import com.morphiqlabs.wavelet.api.BoundaryMode;
import com.morphiqlabs.wavelet.api.DiscreteWavelet;
import com.morphiqlabs.wavelet.api.TransformType;
import com.morphiqlabs.wavelet.api.Wavelet;
import com.morphiqlabs.wavelet.api.WaveletName;
import com.morphiqlabs.wavelet.api.WaveletRegistry;
import com.morphiqlabs.wavelet.modwt.MODWTTransformFactory;
import com.morphiqlabs.wavelet.modwt.MultiLevelMODWTResult;
import com.morphiqlabs.wavelet.modwt.MultiLevelMODWTTransform;
import com.morphiqlabs.wavelets.core.TransformWaveletPair;
import com.morphiqlabs.wavelets.core.VectorWaveCwtAdapter;
import com.morphiqlabs.wavelet.cwt.CWTResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Wavelets Study - Multi-resolution analysis using configurable wavelet transforms
 * 
 * This study performs wavelet decomposition on price data to extract multi-scale features:
 * - Transform selection (MODWT, SWT, CWT) with transform-specific wavelet families
 * - Detail coefficients (D1-D7) representing different frequency components
 * - Optional denoising using BayesShrink or Universal thresholding
 * - Auto-windowing based on wavelet effective support
 * - Decoupled threshold lookback for adaptive denoising
 * 
 * Key Features:
 * - Configurable transform types with appropriate wavelet family filtering
 * - Supports multiple wavelet families (Daubechies, Symlets, Coiflets, Haar)
 * - Automatic window size calculation based on wavelet properties
 * - Performance-optimized with wavelet caching
 * - Real-time coefficient visualization across multiple scales
 * 
 * @author Morphiq Labs
 */
@StudyHeader(
    namespace = "com.morphiqlabs",
    id = "WAVELETS_ANALYSIS",
    name = "Wavelet Analysis",
    desc = "Multi-resolution wavelet decomposition with configurable transforms and adaptive windowing",
    menu = "MorphIQ Labs",
    overlay = false,
    requiresBarUpdates = false,
    signals = false
)
public class Wavelets extends Study {
    private static final Logger logger = LoggerFactory.getLogger(Wavelets.class);

    // =============================================================================================
    // ENUMS - Type-safe keys following best practices
    // =============================================================================================
    
    /**
     * Value keys for storing wavelet coefficients
     * Using enums provides compile-time type safety
     */
    public enum Values {
        D1, D2, D3, D4, D5, D6, D7  // Detail coefficients at each level
    }
    
    // =============================================================================================
    // CONSTANTS - Settings keys and configuration
    // =============================================================================================
    
    // Core wavelet settings
    private static final String WAVELET_PAIR = "waveletPair";
    private static final String WAVELET_TYPE = "waveletType"; // Legacy, for backwards compatibility
    private static final String DECOMPOSITION_LEVELS = "decompositionLevels";
    
    // Window configuration
    private static final String AUTO_WINDOW = "autoWindow";
    private static final String WINDOW_LENGTH = "windowLength";
    private static final String GAMMA_MARGIN = "gammaMargin";
    
    // Denoising configuration
    private static final String USE_DENOISED = "useDenoised";
    private static final String AUTO_THRESH_LOOKBACK = "autoThreshLookback";
    private static final String THRESH_LOOKBACK = "threshLookback";
    private static final String BETA_MARGIN = "betaMargin";
    private static final String THRESH_METHOD = "threshMethod";
    private static final String SOFT_THRESHOLD = "softThreshold";
    
    // Display keys
    private static final String[] PATH_KEYS = new String[7];
    static {
        for (int i = 0; i < 7; i++) {
            PATH_KEYS[i] = "D" + (i + 1) + "_PATH";
        }
    }
    
    // =============================================================================================
    // DEFAULTS AND LIMITS
    // =============================================================================================
    
    // Decomposition levels
    private static final int MAX_DECOMPOSITION_LEVELS = 7;
    private static final int DEFAULT_LEVELS = 5;
    
    // Window parameters
    private static final boolean DEFAULT_AUTO_WINDOW = true;
    private static final int DEFAULT_WINDOW = 1024;
    private static final int MIN_WINDOW = 256;
    private static final int MAX_WINDOW = 8192;
    private static final double DEFAULT_GAMMA = 6.0;  // Window = gamma * effective_support
    
    // Threshold parameters
    private static final boolean DEFAULT_USE_DENOISED = false;
    private static final boolean DEFAULT_AUTO_THRESH_LOOKBACK = true;
    private static final int DEFAULT_THRESH_LOOKBACK = 1024;
    private static final int MIN_THRESH_LOOKBACK = 256;
    private static final int MAX_THRESH_LOOKBACK = 4096;
    private static final double DEFAULT_BETA = 4.0;  // Lookback = beta * effective_support
    private static final String DEFAULT_THRESH_METHOD = "BAYES";
    private static final boolean DEFAULT_SOFT = true;
    
    // Visual configuration - professional color scheme
    private static final Color[] LEVEL_COLORS = new Color[]{
        new Color(135, 206, 250), // Light Sky Blue - High frequency
        new Color(70, 130, 180),  // Steel Blue
        new Color(123, 104, 238), // Medium Slate Blue
        new Color(186, 85, 211),  // Medium Orchid
        new Color(255, 20, 147),  // Deep Pink
        new Color(220, 20, 60),   // Crimson
        new Color(255, 99, 71)    // Tomato - Low frequency
    };
    
    // =============================================================================================
    // STATE MANAGEMENT
    // =============================================================================================
    
    // Wavelet transform components
    private MultiLevelMODWTTransform modwtTransform;
    private VectorWaveCwtAdapter cwtAdapter;
    private DiscreteWavelet currentWavelet;
    private WaveletName currentWaveletName;
    private TransformType currentTransformType = TransformType.MODWT;
    private TransformWaveletPair lastWaveletPair = null;
    
    // Performance optimization: cache wavelets to avoid recreation
    private final Map<WaveletName, DiscreteWavelet> waveletCache = new ConcurrentHashMap<>();
    
    // =============================================================================================
    // INITIALIZATION - Following Tab → Group → Row pattern
    // =============================================================================================
    
    @Override
    public void initialize(Defaults defaults) {
        logger.debug("Initializing Wavelets Analysis Study");
        
        // Create Settings Descriptor
        var sd = createSD();
        
        // ---- General Tab ----
        var generalTab = sd.addTab("General");
        
        // Input configuration
        var inputGroup = generalTab.addGroup("Input");
        inputGroup.addRow(new InputDescriptor(Inputs.INPUT, "Input", Enums.BarInput.CLOSE));
        
        // Wavelet configuration
        var waveletGroup = generalTab.addGroup("Wavelet Configuration");
        waveletGroup.addRow(
            new DiscreteDescriptor(WAVELET_PAIR, "Transform & Wavelet", "MODWT:DB4", 
                createWaveletPairOptions())
        );
        waveletGroup.addRow(
            new IntegerDescriptor(DECOMPOSITION_LEVELS, "Decomposition Levels", 
                DEFAULT_LEVELS, 1, MAX_DECOMPOSITION_LEVELS, 1)
        );
        
        // Window configuration
        var windowGroup = generalTab.addGroup("Window Configuration");
        windowGroup.addRow(
            new BooleanDescriptor(AUTO_WINDOW, "Auto Window Size", DEFAULT_AUTO_WINDOW)
        );
        windowGroup.addRow(
            new DoubleDescriptor(GAMMA_MARGIN, "Auto Window Margin (γ)", 
                DEFAULT_GAMMA, 2.0, 12.0, 0.5)
        );
        windowGroup.addRow(
            new IntegerDescriptor(WINDOW_LENGTH, "Manual Window (bars)", 
                DEFAULT_WINDOW, MIN_WINDOW, MAX_WINDOW, 64)
        );
        
        // Enable manual window only when auto is disabled
        sd.addDependency(new EnabledDependency(false, AUTO_WINDOW, WINDOW_LENGTH));
        sd.addDependency(new EnabledDependency(true, AUTO_WINDOW, GAMMA_MARGIN));
        
        // ---- Denoising Tab ----
        var denoisingTab = sd.addTab("Denoising");
        
        var thresholdGroup = denoisingTab.addGroup("Threshold Configuration");
        thresholdGroup.addRow(
            new BooleanDescriptor(USE_DENOISED, "Enable Denoising", DEFAULT_USE_DENOISED)
        );
        thresholdGroup.addRow(
            new DiscreteDescriptor(THRESH_METHOD, "Threshold Method", DEFAULT_THRESH_METHOD,
                Arrays.asList(
                    new NVP("BayesShrink", "BAYES"),
                    new NVP("Universal", "UNIVERSAL")
                ))
        );
        thresholdGroup.addRow(
            new BooleanDescriptor(SOFT_THRESHOLD, "Soft Thresholding", DEFAULT_SOFT)
        );
        
        var lookbackGroup = denoisingTab.addGroup("Threshold Lookback");
        lookbackGroup.addRow(
            new BooleanDescriptor(AUTO_THRESH_LOOKBACK, "Auto Lookback", DEFAULT_AUTO_THRESH_LOOKBACK)
        );
        lookbackGroup.addRow(
            new DoubleDescriptor(BETA_MARGIN, "Auto Lookback Margin (β)", 
                DEFAULT_BETA, 2.0, 12.0, 0.5)
        );
        lookbackGroup.addRow(
            new IntegerDescriptor(THRESH_LOOKBACK, "Manual Lookback (bars)", 
                DEFAULT_THRESH_LOOKBACK, MIN_THRESH_LOOKBACK, MAX_THRESH_LOOKBACK, 64)
        );
        
        // Enable threshold settings only when denoising is enabled
        sd.addDependency(new EnabledDependency(USE_DENOISED, 
            THRESH_METHOD, SOFT_THRESHOLD, AUTO_THRESH_LOOKBACK, BETA_MARGIN, THRESH_LOOKBACK));
        sd.addDependency(new EnabledDependency(false, AUTO_THRESH_LOOKBACK, THRESH_LOOKBACK));
        sd.addDependency(new EnabledDependency(true, AUTO_THRESH_LOOKBACK, BETA_MARGIN));
        
        // ---- Display Tab ----
        var displayTab = sd.addTab("Display");
        var pathsGroup = displayTab.addGroup("Detail Paths");
        
        for (int i = 0; i < MAX_DECOMPOSITION_LEVELS; i++) {
            String pathName = "D" + (i + 1) + " (Level " + (i + 1) + ")";
            float lineWidth = (i == 0) ? 2.0f : 1.5f;  // Emphasize high-frequency
            pathsGroup.addRow(
                new PathDescriptor(PATH_KEYS[i], pathName, LEVEL_COLORS[i], 
                    lineWidth, null, true, true, true)
            );
        }
        
        // Create Runtime Descriptor
        var desc = createRD();
        
        // Configure label generation
        desc.setLabelSettings(WAVELET_PAIR, DECOMPOSITION_LEVELS);
        
        // Export values for external access
        for (Values v : Values.values()) {
            desc.exportValue(new ValueDescriptor(v, v.name() + " Coefficient", 
                new String[]{WAVELET_TYPE, DECOMPOSITION_LEVELS}));
        }
        
        // Declare paths and indicators
        for (int i = 0; i < MAX_DECOMPOSITION_LEVELS; i++) {
            Values value = Values.values()[i];
            desc.declarePath(value, PATH_KEYS[i]);
            desc.declareIndicator(value, "D" + (i + 1));
        }

        // Set range keys for auto-scaling
        desc.setRangeKeys(Values.D1, Values.D2, Values.D3, Values.D4, Values.D5, Values.D6, Values.D7);
        
        
        
        // Add zero reference line
        desc.addHorizontalLine(new LineInfo(0.0, null, 1.0f, new float[]{3, 3}));
        
        setRuntimeDescriptor(desc);
        
        // Initialize default wavelet
        initializeDefaultWavelet();
    }
    
    // =============================================================================================
    // LIFECYCLE METHODS
    // =============================================================================================
    
    @Override
    public void onLoad(Defaults defaults) {
        // Get the wavelet type from settings and initialize
        String waveletType = getSettings().getString(WAVELET_TYPE, "db4");
        
        // Force initialization
        lastWaveletPair = null;
        checkAndUpdateSettings();
        
        // If still not initialized, try default
        if (modwtTransform == null) {
            initializeDefaultWavelet();
        }
        
        // Set minimum bars based on current settings
        int minBars = currentWindowLength(getSettings());
        setMinBars(minBars);
        
        if (logger.isDebugEnabled()) {
            logger.debug("onLoad: Initialized with wavelet={}, minBars={}", waveletType, minBars);
        }
    }
    
    @Override
    public void onSettingsUpdated(DataContext ctx) {
        logger.debug("onSettingsUpdated: Clearing state for recalculation");
        
        // Clear cached state
        clearState();
        
        // Update minimum bars
        setMinBars(currentWindowLength(getSettings()));
        
        // Mark all bars for recalculation and clear values to force range recalculation
        DataSeries series = ctx.getDataSeries();
        if (series != null) {
            for (int i = 0; i < series.size(); i++) {
                series.setComplete(i, false);
                // Clear all coefficient values to force range recalculation
                for (Values v : Values.values()) {
                    series.setDouble(i, v, null);
                }
            }
        }
        
        // Call parent implementation
        super.onSettingsUpdated(ctx);
    }
    
    @Override
    public void clearState() {
        logger.debug("clearState: Resetting wavelet transform state");
        super.clearState();
        
        // Reset wavelet state (but keep cache for performance)
        modwtTransform = null;
        cwtAdapter = null;
        currentWavelet = null;
        currentWaveletName = null;
        lastWaveletPair = null;
    }
    
    @Override
    public int getMinBars() {
        // Return minimum bars needed based on window configuration
        return currentWindowLength(getSettings()) + 10;  // Add buffer for safety
    }
    
    @Override
    public String getLabel() {
        // Custom label to properly display the TransformWaveletPair
        Settings settings = getSettings();
        if (settings == null) {
            return "Wavelet Analysis";
        }
        
        String pairStr = settings.getString(WAVELET_PAIR, "MODWT:DB4");
        TransformWaveletPair pair = TransformWaveletPair.fromString(pairStr);
        int levels = settings.getInteger(DECOMPOSITION_LEVELS, DEFAULT_LEVELS);
        
        // Format: "Wavelet Analysis(MODWT:Daubechies 4, 4)"
        return String.format("Wavelet Analysis(%s, %d)", pair.toString(), levels);
    }
    
    // =============================================================================================
    // CALCULATION - Core wavelet processing
    // =============================================================================================
    
    @Override
    protected void calculateValues(DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        if (series == null) return;
        
        int size = series.size();
        if (size == 0) return;
        
        // Find the first incomplete bar to start processing
        int startIndex = size - 1;
        for (int i = size - 1; i >= 0; i--) {
            if (!series.isComplete(i)) {
                startIndex = i;
            } else {
                // Found a complete bar, can stop looking backwards
                break;
            }
        }
        
        // Process only incomplete bars starting from the first one found
        for (int i = startIndex; i < size; i++) {
            if (!series.isComplete(i)) {
                calculate(i, ctx);
            }
        }
    }
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        if (series == null) {
            return;
        }
        
        // Skip if already complete (avoid recalculation)
        if (series.isComplete(index)) {
            return;
        }
        
        // Ensure wavelet components are initialized based on transform type
        if (currentTransformType == TransformType.CWT) {
            if (cwtAdapter == null) {
                checkAndUpdateSettings();
                if (cwtAdapter == null) {
                    logger.error("Failed to initialize CWT adapter at index {}", index);
                    clearLevels(series, index);
                    return;
                }
            }
        } else {
            if (modwtTransform == null || currentWavelet == null) {
                // Try to initialize
                checkAndUpdateSettings();
                if (modwtTransform == null || currentWavelet == null) {
                    clearLevels(series, index);
                    return;
                }
            }
        }
        
        try {
            // Only check settings on first bar or settings update
            if (index == 0 || lastWaveletPair == null) {
                checkAndUpdateSettings();
            }
            
            // Get current settings
            int levels = getSettings().getInteger(DECOMPOSITION_LEVELS, DEFAULT_LEVELS);
            int windowLength = currentWindowLength(getSettings());
            boolean useDenoised = getSettings().getBoolean(USE_DENOISED, DEFAULT_USE_DENOISED);
            Object input = getSettings().getInput(Inputs.INPUT);
            
            // Check if we have enough data
            if (index < windowLength - 1) {
                clearLevels(series, index);
                return;
            }
            
            // Get sliding window of data
            double[] data = getWindowData(series, index, windowLength, input);
            if (data == null || data.length < windowLength) {
                clearLevels(series, index);
                return;
            }
            
            // Log parameters only once
            if (index == windowLength - 1 && logger.isDebugEnabled()) {
                logCalculationParameters(levels, windowLength);
            }
            
            // Perform wavelet decomposition based on transform type
            if (currentTransformType == TransformType.CWT) {
                // Ensure CWT adapter is initialized
                if (cwtAdapter == null) {
                    cwtAdapter = new VectorWaveCwtAdapter();
                    logger.info("Created CWT adapter on-demand for wavelet: {}", currentWaveletName);
                }
                
                // Perform CWT analysis
                CWTResult cwtResult = cwtAdapter.analyzeWithAutoScales(data, currentWaveletName, levels);
                if (cwtResult == null) {
                    logger.error("CWT analysis failed for wavelet: {} at index {}", currentWaveletName, index);
                    clearLevels(series, index);
                    return;
                }
                
                // Extract levels from CWT result for compatibility with existing display
                // The extractLevels method now normalizes the coefficients
                double[][] cwtLevels = cwtAdapter.extractLevels(cwtResult, levels);
                if (cwtLevels == null || cwtLevels.length == 0) {
                    logger.error("Failed to extract CWT levels at index {}", index);
                    clearLevels(series, index);
                    return;
                }
                
                // Store CWT coefficients in data series
                storeCWTCoefficients(series, index, cwtLevels, levels);
            } else {
                // MODWT decomposition (default)
                MultiLevelMODWTResult result = modwtTransform.decompose(data, levels);
                if (result == null || !result.isValid()) {
                    clearLevels(series, index);
                    return;
                }
                
                // Apply denoising if enabled
                if (useDenoised) {
                    applyDenoising(result, levels);
                }
                
                // Store coefficients in data series
                storeCoefficients(series, index, result, levels);
            }
            
            // Mark as complete
            series.setComplete(index);
            
        } catch (Exception e) {
            logger.error("calculate: Error at index {}", index, e);
            clearLevels(series, index);
        }
    }
    
    /**
     * Collect sliding window of input data
     */
    private double[] getWindowData(DataSeries series, int index, int windowLength, Object input) {
        double[] window = new double[windowLength];
        int start = index - windowLength + 1;
        
        // Use forward-fill for missing data
        double lastValid = series.getClose(Math.max(0, start));
        
        for (int i = 0; i < windowLength; i++) {
            int barIndex = start + i;
            if (barIndex < 0 || barIndex >= series.size()) {
                window[i] = lastValid;
                continue;
            }
            
            Double value = series.getDouble(barIndex, input);
            if (value != null) {
                window[i] = value;
                lastValid = value;
            } else {
                window[i] = lastValid;
            }
        }
        
        // Only log in trace mode
        if (logger.isTraceEnabled() && index == windowLength - 1) {
            logger.trace("Window at index {}: {} bars", index, windowLength);
        }
        
        return window;
    }
    
    /**
     * Apply thresholding to denoise detail coefficients
     */
    private void applyDenoising(MultiLevelMODWTResult result, int levels) {
        String method = getSettings().getString(THRESH_METHOD, DEFAULT_THRESH_METHOD);
        boolean soft = getSettings().getBoolean(SOFT_THRESHOLD, DEFAULT_SOFT);
        int lookback = currentThresholdLookback(getSettings(), levels);
        
        for (int level = 1; level <= levels && level <= MAX_DECOMPOSITION_LEVELS; level++) {
            double[] detail = result.getDetailCoeffsAtLevel(level);
            if (detail == null || detail.length == 0) continue;
            
            // Use last M samples to estimate threshold
            int m = Math.min(lookback, detail.length);
            double[] tail = Arrays.copyOfRange(detail, detail.length - m, detail.length);
            
            // Calculate threshold
            double threshold = "UNIVERSAL".equalsIgnoreCase(method) 
                ? calculateUniversalThreshold(tail) 
                : calculateBayesThreshold(tail);
            
            // Apply thresholding in-place
            applyThreshold(detail, threshold, soft);
        }
    }
    
    /**
     * Store wavelet coefficients in data series
     */
    private void storeCoefficients(DataSeries series, int index, 
                                   MultiLevelMODWTResult result, int levels) {
        // Store detail coefficients for active levels
        for (int level = 0; level < levels && level < MAX_DECOMPOSITION_LEVELS; level++) {
            double[] coeffs = result.getDetailCoeffsAtLevel(level + 1);  // 1-based
            Values key = Values.values()[level];
            
            if (coeffs != null && coeffs.length > 0) {
                // Store last coefficient (most recent)
                double value = coeffs[coeffs.length - 1];
                series.setDouble(index, key, value);
                
                // Debug logging for first few bars
                if (logger.isTraceEnabled() && index < 5) {
                    logger.trace("Stored D{} at index {}: {}", level + 1, index, value);
                }
            } else {
                series.setDouble(index, key, null);
            }
        }
        
        // Clear unused levels
        for (int level = levels; level < MAX_DECOMPOSITION_LEVELS; level++) {
            series.setDouble(index, Values.values()[level], null);
        }
    }
    
    /**
     * Store CWT coefficients in data series
     * Maps CWT scales to decomposition levels for visualization
     */
    private void storeCWTCoefficients(DataSeries series, int index, double[][] cwtLevels, int numLevels) {
        // Store CWT coefficients for all active levels
        // CWT returns [numLevels][windowLength] where each level contains coefficients for all time points
        // We need to store the last coefficient (most recent) for the current bar
        for (int level = 0; level < numLevels && level < MAX_DECOMPOSITION_LEVELS; level++) {
            Values key = Values.values()[level];
            
            if (level < cwtLevels.length && cwtLevels[level] != null && cwtLevels[level].length > 0) {
                // Store the last coefficient (most recent) from this scale
                double value = cwtLevels[level][cwtLevels[level].length - 1];
                series.setDouble(index, key, value);
                
                // Debug logging for first few values
                if (logger.isDebugEnabled() && index < 5) {
                    logger.debug("Stored CWT level {} at index {}: value={}", 
                                level + 1, index, value);
                }
            } else {
                series.setDouble(index, key, null);
            }
        }
        
        // Clear unused levels
        for (int level = numLevels; level < MAX_DECOMPOSITION_LEVELS; level++) {
            series.setDouble(index, Values.values()[level], null);
        }
    }
    
    /**
     * Clear all coefficient levels
     */
    private void clearLevels(DataSeries series, int index) {
        for (Values v : Values.values()) {
            series.setDouble(index, v, null);
        }
    }
    
    /**
     * Log calculation parameters for debugging
     */
    private void logCalculationParameters(int levels, int windowLength) {
        if (!logger.isInfoEnabled()) return;
        
        int filterLength = getFilterLength(currentWavelet);
        int support = calculateEffectiveSupport(filterLength, levels);
        int threshLookback = currentThresholdLookback(getSettings(), levels);
        
        logger.info("MODWT Parameters: wavelet={}, filterLength={}, levels={}, " +
                   "effectiveSupport={}, window={}, threshLookback={}",
                   currentWaveletName, filterLength, levels, support, 
                   windowLength, threshLookback);
    }
    
    // =============================================================================================
    // WAVELET MANAGEMENT
    // =============================================================================================
    
    /**
     * Check and update wavelet settings if changed
     */
    private void checkAndUpdateSettings() {
        // Get the selected transform-wavelet pair
        String pairStr = getSettings().getString(WAVELET_PAIR, "MODWT:DB4");
        TransformWaveletPair pair = TransformWaveletPair.fromString(pairStr);
        
        boolean pairChanged = (lastWaveletPair == null) || !pair.equals(lastWaveletPair);
        
        if (pairChanged || currentWavelet == null) {
            currentTransformType = pair.getTransform();
            updateWaveletComponents(pair.getWavelet());
            lastWaveletPair = pair;
        }
    }
    
    /**
     * Parse wavelet name string to WaveletName enum
     */
    private WaveletName parseWaveletName(String name) {
        if (name == null) return WaveletName.DB4;
        
        // Convert common formats to enum names
        String normalized = name.toUpperCase()
            .replace("DAUBECHIES", "DB")
            .replace("SYMLET", "SYM")
            .replace("COIFLET", "COIF");
        
        try {
            return WaveletName.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown wavelet name: {}, using DB4", name);
            return WaveletName.DB4;
        }
    }
    
    /**
     * Update wavelet transform components
     */
    private void updateWaveletComponents(WaveletName waveletName) {
        try {
            // Check if this is a CWT wavelet (continuous wavelets)
            if (currentTransformType == TransformType.CWT) {
                // Initialize CWT adapter if needed
                if (cwtAdapter == null) {
                    cwtAdapter = new VectorWaveCwtAdapter();
                    logger.info("Initialized CWT adapter for wavelet: {}", waveletName);
                }
                currentWaveletName = waveletName;
                // For CWT, we don't need discrete wavelets or MODWT transform
                currentWavelet = null;
                modwtTransform = null;
                return;
            }
            
            // For MODWT, continue with discrete wavelet setup
            // Validate wavelet availability
            if (!WaveletRegistry.isWaveletAvailable(waveletName)) {
                logger.warn("updateWaveletComponents: Wavelet {} not available, trying fallback", waveletName);
                // Try fallback wavelets
                WaveletName[] fallbacks = {WaveletName.DB4, WaveletName.HAAR, WaveletName.DB2};
                for (WaveletName fallback : fallbacks) {
                    if (WaveletRegistry.isWaveletAvailable(fallback)) {
                        logger.info("Using fallback wavelet: {}", fallback);
                        waveletName = fallback;
                        break;
                    }
                }
            }
            
            // Get or create wavelet
            DiscreteWavelet wavelet = getOrCreateWavelet(waveletName);
            if (wavelet != null) {
                currentWavelet = wavelet;
                currentWaveletName = waveletName;
                modwtTransform = MODWTTransformFactory.createMultiLevel(
                    currentWavelet, BoundaryMode.PERIODIC);
                if (logger.isDebugEnabled()) {
                    logger.debug("Successfully updated to wavelet: {}", waveletName);
                }
            } else {
                logger.error("Failed to create wavelet: {}", waveletName);
                // Try one more time with DB4
                if (waveletName != WaveletName.DB4) {
                    logger.info("Attempting final fallback to DB4");
                    updateWaveletComponents(WaveletName.DB4);
                }
            }
        } catch (Exception e) {
            logger.error("Error updating wavelet components for: {}", waveletName, e);
        }
    }
    
    /**
     * Get wavelet from cache or create new instance
     */
    private DiscreteWavelet getOrCreateWavelet(WaveletName waveletName) {
        if (waveletName == null) {
            return null;
        }
        
        // Check cache first
        DiscreteWavelet cached = waveletCache.get(waveletName);
        if (cached != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Using cached wavelet: {}", waveletName);
            }
            return cached;
        }
        
        // Create new wavelet
        try {
            Wavelet wavelet = WaveletRegistry.getWavelet(waveletName);
            if (!(wavelet instanceof DiscreteWavelet)) {
                logger.warn("Wavelet {} is not discrete", waveletName);
                return null;
            }
            
            DiscreteWavelet discrete = (DiscreteWavelet) wavelet;
            
            // Validate wavelet has valid coefficients
            if (discrete.lowPassDecomposition() == null || 
                discrete.lowPassDecomposition().length == 0) {
                logger.warn("Wavelet {} has invalid coefficients", waveletName);
                return null;
            }
            
            // Cache and return
            waveletCache.put(waveletName, discrete);
            if (logger.isDebugEnabled()) {
                logger.debug("Created wavelet: {} (filter length: {})", 
                           waveletName, discrete.lowPassDecomposition().length);
            }
            return discrete;
            
        } catch (Exception e) {
            logger.error("Failed to create wavelet {}", waveletName, e);
            return null;
        }
    }
    
    /**
     * Initialize with default wavelet
     */
    private void initializeDefaultWavelet() {
        WaveletName[] candidates = {WaveletName.DB4, WaveletName.HAAR, WaveletName.DB2, WaveletName.SYM4};
        
        for (WaveletName candidate : candidates) {
            DiscreteWavelet wavelet = getOrCreateWavelet(candidate);
            if (wavelet != null) {
                currentWavelet = wavelet;
                currentWaveletName = candidate;
                modwtTransform = MODWTTransformFactory.createMultiLevel(
                    currentWavelet, BoundaryMode.PERIODIC);
                if (logger.isDebugEnabled()) {
                    logger.debug("Initialized with default wavelet: {}", candidate);
                }
                return;
            }
        }
        
        logger.error("Failed to initialize any wavelet - check VectorWave installation");
    }
    
    // =============================================================================================
    // AUTO-SIZING CALCULATIONS
    // =============================================================================================
    
    /**
     * Calculate current window length based on settings
     */
    private int currentWindowLength(Settings settings) {
        boolean autoWindow = settings.getBoolean(AUTO_WINDOW, DEFAULT_AUTO_WINDOW);
        
        if (autoWindow) {
            int levels = settings.getInteger(DECOMPOSITION_LEVELS, DEFAULT_LEVELS);
            double gamma = settings.getDouble(GAMMA_MARGIN, DEFAULT_GAMMA);
            return calculateAutoWindow(levels, gamma);
        }
        
        return settings.getInteger(WINDOW_LENGTH, DEFAULT_WINDOW);
    }
    
    /**
     * Calculate current threshold lookback based on settings
     */
    private int currentThresholdLookback(Settings settings, int levels) {
        boolean autoLookback = settings.getBoolean(AUTO_THRESH_LOOKBACK, DEFAULT_AUTO_THRESH_LOOKBACK);
        
        if (autoLookback) {
            double beta = settings.getDouble(BETA_MARGIN, DEFAULT_BETA);
            return calculateAutoThresholdLookback(levels, beta);
        }
        
        return settings.getInteger(THRESH_LOOKBACK, DEFAULT_THRESH_LOOKBACK);
    }
    
    /**
     * Calculate automatic window size based on wavelet properties
     * Window = gamma * effective_support
     */
    private int calculateAutoWindow(int levels, double gamma) {
        int filterLength = getFilterLength(currentWavelet);
        int support = calculateEffectiveSupport(filterLength, levels);
        int raw = (int) Math.ceil(gamma * support);
        return clamp(roundUp64(raw), MIN_WINDOW, MAX_WINDOW);
    }
    
    /**
     * Calculate automatic threshold lookback
     * Lookback = beta * effective_support
     */
    private int calculateAutoThresholdLookback(int levels, double beta) {
        int filterLength = getFilterLength(currentWavelet);
        int support = calculateEffectiveSupport(filterLength, levels);
        int raw = (int) Math.ceil(beta * support);
        return clamp(roundUp64(raw), MIN_THRESH_LOOKBACK, MAX_THRESH_LOOKBACK);
    }
    
    /**
     * Get filter length from wavelet
     */
    private int getFilterLength(DiscreteWavelet wavelet) {
        if (wavelet != null && wavelet.lowPassDecomposition() != null) {
            return wavelet.lowPassDecomposition().length;
        }
        
        // Fallback based on known wavelets
        return switch (currentWaveletName != null ? currentWaveletName : WaveletName.DB4) {
            case HAAR -> 2;
            case DB6 -> 12;
            case DB8 -> 16;
            case SYM8 -> 16;
            default -> 8;  // db4 default
        };
    }
    
    /**
     * Calculate effective support for undecimated wavelet transform
     * Support ≈ 1 + (L - 1) * (2^J - 1)
     */
    private int calculateEffectiveSupport(int filterLength, int levels) {
        return 1 + (filterLength - 1) * ((1 << levels) - 1);
    }
    
    /**
     * Round up to nearest multiple of 64 for cache efficiency
     */
    private int roundUp64(int n) {
        return ((n + 63) / 64) * 64;
    }
    
    /**
     * Clamp value to range [min, max]
     */
    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    // =============================================================================================
    // THRESHOLD CALCULATIONS
    // =============================================================================================
    
    /**
     * Calculate median of array (for robust statistics)
     */
    private static double median(double[] data) {
        double[] sorted = Arrays.copyOf(data, data.length);
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        
        if (sorted.length % 2 == 0) {
            return (sorted[mid - 1] + sorted[mid]) / 2.0;
        }
        return sorted[mid];
    }
    
    /**
     * Calculate Median Absolute Deviation (robust noise estimator)
     * MAD = median(|x_i - median(x)|) / 0.6745
     */
    private static double mad(double[] data) {
        double med = median(data);
        double[] deviations = new double[data.length];
        
        for (int i = 0; i < data.length; i++) {
            deviations[i] = Math.abs(data[i] - med);
        }
        
        return median(deviations) / 0.6745;  // Scale to match normal distribution
    }
    
    /**
     * Calculate standard deviation
     */
    private static double standardDeviation(double[] data) {
        if (data.length == 0) return 0.0;
        
        double mean = 0.0;
        for (double v : data) mean += v;
        mean /= data.length;
        
        double sumSquares = 0.0;
        for (double v : data) {
            double diff = v - mean;
            sumSquares += diff * diff;
        }
        
        return Math.sqrt(sumSquares / Math.max(1, data.length - 1));
    }
    
    /**
     * Calculate Universal threshold
     * T = sigma_noise * sqrt(2 * log(N))
     */
    private static double calculateUniversalThreshold(double[] data) {
        double sigmaNoise = mad(data);
        double n = Math.max(2, data.length);
        return sigmaNoise * Math.sqrt(2.0 * Math.log(n));
    }
    
    /**
     * Calculate BayesShrink threshold
     * Minimizes Bayesian risk assuming generalized Gaussian prior
     */
    private static double calculateBayesThreshold(double[] data) {
        double sigmaNoise = mad(data);
        double sigmaY = standardDeviation(data);
        
        // Estimate signal variance
        double varNoise = sigmaNoise * sigmaNoise;
        double varY = sigmaY * sigmaY;
        double varSignal = Math.max(varY - varNoise, 0.0);
        
        // If pure noise, use strong shrinkage
        if (varSignal <= 1e-12) {
            double maxAbs = Arrays.stream(data).map(Math::abs).max().orElse(0.0);
            return Math.max(maxAbs, sigmaNoise);
        }
        
        // BayesShrink threshold
        double sigmaSignal = Math.sqrt(varSignal);
        return varNoise / (sigmaSignal + 1e-12);
    }
    
    /**
     * Apply thresholding to coefficients
     */
    private static void applyThreshold(double[] coeffs, double threshold, boolean soft) {
        for (int i = 0; i < coeffs.length; i++) {
            double value = coeffs[i];
            double absValue = Math.abs(value);
            
            if (soft) {
                // Soft thresholding: shrink by threshold amount
                if (absValue <= threshold) {
                    coeffs[i] = 0.0;
                } else {
                    coeffs[i] = Math.signum(value) * (absValue - threshold);
                }
            } else {
                // Hard thresholding: zero if below threshold
                if (absValue < threshold) {
                    coeffs[i] = 0.0;
                }
            }
        }
    }
    
    // =============================================================================================
    // UI HELPERS
    // =============================================================================================
    
    /**
     * Create transform-wavelet pair options for the dropdown
     * Shows only the curated subset of wavelets for each transform
     */
    private List<NVP> createWaveletPairOptions() {
        List<NVP> options = new ArrayList<>();
        
        // MODWT wavelets
        options.add(createPairOption(TransformType.MODWT, WaveletName.HAAR));
        options.add(createPairOption(TransformType.MODWT, WaveletName.DB2));
        options.add(createPairOption(TransformType.MODWT, WaveletName.DB4));
        options.add(createPairOption(TransformType.MODWT, WaveletName.DB8));
        options.add(createPairOption(TransformType.MODWT, WaveletName.SYM4));
        options.add(createPairOption(TransformType.MODWT, WaveletName.SYM8));
        options.add(createPairOption(TransformType.MODWT, WaveletName.COIF1));
        options.add(createPairOption(TransformType.MODWT, WaveletName.COIF2));
        options.add(createPairOption(TransformType.MODWT, WaveletName.COIF3));
        
        // CWT wavelets
        options.add(createPairOption(TransformType.CWT, WaveletName.MORLET));
        options.add(createPairOption(TransformType.CWT, WaveletName.MEXICAN_HAT));
        options.add(createPairOption(TransformType.CWT, WaveletName.PAUL));
        
        return options;
    }
    
    /**
     * Create a single pair option for the dropdown
     */
    private NVP createPairOption(TransformType transform, WaveletName wavelet) {
        TransformWaveletPair pair = new TransformWaveletPair(transform, wavelet);
        return new NVP(pair.toString(), pair.toStorageString());
    }
    
    /**
     * Get default wavelets for SWT transform (fallback for development)
     */
    private List<WaveletName> getDefaultSWTWavelets() {
        return Arrays.asList(
            WaveletName.HAAR, WaveletName.DB2, WaveletName.DB4, WaveletName.DB6, WaveletName.DB8,
            WaveletName.SYM4, WaveletName.SYM8, WaveletName.COIF2, WaveletName.COIF4
        );
    }
    
    /**
     * Get default wavelets for MODWT transform (fallback for development)
     */
    private List<WaveletName> getDefaultMODWTWavelets() {
        return Arrays.asList(
            WaveletName.HAAR, WaveletName.DB2, WaveletName.DB4, WaveletName.DB6, WaveletName.DB8, WaveletName.DB10,
            WaveletName.SYM2, WaveletName.SYM3, WaveletName.SYM4, WaveletName.SYM5, WaveletName.SYM6, WaveletName.SYM7, WaveletName.SYM8,
            WaveletName.COIF1, WaveletName.COIF2, WaveletName.COIF3, WaveletName.COIF4, WaveletName.COIF5
        );
    }
    
    /**
     * Get default wavelets for CWT transform (fallback for development)
     * CWT supports continuous wavelets for advanced financial analysis
     */
    private List<WaveletName> getDefaultCWTWavelets() {
        List<WaveletName> wavelets = new ArrayList<>();
        
        // Define known CWT wavelets using actual enum values where available
        // This approach is more robust than using string literals
        
        // Primary CWT wavelets - these are known to exist in the current VectorWave version
        WaveletName[] primaryCwtWavelets = {
            WaveletName.MORLET,
            WaveletName.MEXICAN_HAT,
            WaveletName.PAUL
        };
        
        for (WaveletName waveletName : primaryCwtWavelets) {
            if (WaveletRegistry.isWaveletAvailable(waveletName)) {
                wavelets.add(waveletName);
            } else {
                logger.warn("Expected CWT wavelet {} not available in registry", waveletName);
            }
        }
        
        // Check for additional continuous wavelets that might be available in future versions
        // Using reflection to discover all enum values dynamically
        for (WaveletName waveletName : WaveletName.values()) {
            // Skip if already added
            if (wavelets.contains(waveletName)) {
                continue;
            }
            
            // Check if this is a continuous wavelet based on naming convention
            String name = waveletName.name();
            if (isContinuousWaveletName(name) && WaveletRegistry.isWaveletAvailable(waveletName)) {
                wavelets.add(waveletName);
                logger.debug("Found additional CWT wavelet: {}", waveletName);
            }
        }
        
        // Add discrete wavelets that work well with CWT as fallback
        List<WaveletName> discreteFallback = Arrays.asList(
            WaveletName.HAAR, WaveletName.DB4, WaveletName.DB8,
            WaveletName.SYM4, WaveletName.SYM8, WaveletName.COIF2
        );
        
        for (WaveletName wavelet : discreteFallback) {
            if (!wavelets.contains(wavelet)) {
                wavelets.add(wavelet);
            }
        }
        
        // Log what wavelets are available for CWT
        if (logger.isInfoEnabled()) {
            logger.info("CWT wavelets available: {} (continuous: {}, discrete fallback: {})", 
                wavelets.size(), 
                Math.max(0, wavelets.size() - discreteFallback.size()),
                Math.min(wavelets.size(), discreteFallback.size()));
        }
        
        return wavelets;
    }
    
    /**
     * Check if a wavelet name corresponds to a continuous wavelet
     * based on known naming conventions
     */
    private boolean isContinuousWaveletName(String name) {
        // Known continuous wavelet name patterns
        return name.equals("MORLET") ||
               name.equals("MEXICAN_HAT") ||
               name.equals("PAUL") ||
               name.equals("GAUSSIAN") ||
               name.equals("DOG") ||  // Derivative of Gaussian
               name.equals("SHANNON") ||
               name.equals("MEYER") ||
               name.equals("RICKER") ||
               name.startsWith("FBSP") ||  // Frequency B-Spline
               name.startsWith("CMOR") ||  // Complex Morlet
               name.startsWith("SHAN");    // Shannon
    }
    
    /**
     * Get display name for wavelet with financial use case descriptions
     */
    private String getWaveletDisplayName(WaveletName waveletName) {
        if (waveletName == null) return "Unknown";
        
        String name = waveletName.name();
        
        // Handle continuous wavelets for CWT transform with additional descriptions
        if (name.equals("MORLET")) {
            return "Morlet - Market cycles & dominant frequency analysis";
        }
        if (name.equals("MEXICAN_HAT")) {
            return "Mexican Hat - Flash crashes & liquidity events";
        }
        if (name.equals("PAUL")) {
            return "Paul - Asymmetric events & regime detection";
        }
        if (name.equals("GAUSSIAN")) {
            return "Gaussian - Edge detection & volatility analysis";
        }
        if (name.equals("DOG")) {
            return "DOG - Advanced volatility metrics";
        }
        if (name.equals("SHANNON")) {
            return "Shannon - Spectral analysis with reduced artifacts";
        }
        if (name.equals("MEYER")) {
            return "Meyer - Continuous wavelet version";
        }
        if (name.equals("RICKER")) {
            return "Ricker - Seismic-style analysis";
        }
        
        // Use VectorWave's built-in display name functionality
        // This handles all standard wavelets (DB, SYM, COIF, etc.) consistently
        return waveletName.getDisplayName();
    }
}