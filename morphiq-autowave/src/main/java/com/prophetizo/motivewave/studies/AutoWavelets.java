package com.prophetizo.motivewave.studies;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;
import com.motivewave.platform.sdk.study.RuntimeDescriptor;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.prophetizo.LoggerConfig;
import com.prophetizo.wavelets.WaveletAnalyzer;
import com.prophetizo.wavelets.WaveletAnalyzerFactory;
import com.prophetizo.wavelets.WaveletType;
import org.slf4j.Logger;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

@StudyHeader(
        namespace = "com.prophetizo.motivewave.studies",
        id = "AUTO_WAVELETS",
        name = "Auto Wavelets",
        desc = "AutoWavelets Study",
        menu = "MorphIQ | Wavelet Analysis",
        overlay = false,
        requiresBarUpdates = true
)
public class AutoWavelets extends Study {
    // Settings keys
    final static String WAVELET_TYPE = "WAVELET_TYPE";
    private static final int MAX_DECOMPOSITION_LEVELS = 7;
    private static final Logger logger = LoggerConfig.getLogger(AutoWavelets.class);
    // Path keys array for dynamic access
    private static final String[] PATH_KEYS = new String[MAX_DECOMPOSITION_LEVELS];
    // Color scheme for decomposition levels - from light blue to red
    private static final Color[] LEVEL_COLORS = new Color[]{
            new Color(135, 206, 250), // Light Sky Blue
            new Color(70, 130, 180),  // Steel Blue
            new Color(123, 104, 238), // Medium Slate Blue
            new Color(186, 85, 211),  // Medium Orchid
            new Color(255, 20, 147),  // Deep Pink
            new Color(220, 20, 60),   // Crimson
            new Color(255, 99, 71)    // Tomato
    };

    static {
        // Initialize path keys
        for (int i = 0; i < MAX_DECOMPOSITION_LEVELS; i++) {
            PATH_KEYS[i] = "D" + (i + 1) + "_PATH";
        }
    }

    private WaveletAnalyzer waveletAnalyzer;
    // Track settings to detect changes
    private String lastWaveletType = null;

    @Override
    public void initialize(Defaults defaults) {
        logger.debug("Initializing AutoWavelets Study");

        SettingsDescriptor settings = new SettingsDescriptor();
        setSettingsDescriptor(settings);

        // General settings tab
        SettingTab generalTab = settings.addTab("General");

        SettingGroup waveletGroup = generalTab.addGroup("Wavelet Configuration");
        
        // Create NVP list for all available wavelets
        List<NVP> waveletOptions = new ArrayList<>();
        for (WaveletType type : WaveletType.values()) {
            waveletOptions.add(new NVP(type.getDisplayName(), type.getDisplayName()));
        }
        
        waveletGroup.addRow(new DiscreteDescriptor(WAVELET_TYPE, "Wavelet Type",
                WaveletType.DAUBECHIES4.getDisplayName(), waveletOptions));

        SettingTab displayTab = settings.addTab("Display");
        var paths = displayTab.addGroup("Paths");

        // Create path descriptors for all possible decomposition levels
        for (int i = 0; i < MAX_DECOMPOSITION_LEVELS; i++) {
            String pathName = "D" + (i + 1);
            float lineWidth = (i == MAX_DECOMPOSITION_LEVELS - 1) ? 1.5f : 1.0f;

            PathDescriptor pathDescriptor = new PathDescriptor(PATH_KEYS[i], pathName, LEVEL_COLORS[i], lineWidth, null, true, true, true);
            paths.addRow(pathDescriptor);
        }

        RuntimeDescriptor runtimeDescriptor = new RuntimeDescriptor();
        setRuntimeDescriptor(runtimeDescriptor);

        // Declare all possible paths and indicators
        for (int i = 0; i < MAX_DECOMPOSITION_LEVELS; i++) {
            Paths value = Paths.values()[i];
            runtimeDescriptor.declarePath(value, PATH_KEYS[i]);
            runtimeDescriptor.declareIndicator(value, "D" + (i + 1));
        }

        // Set range keys for all levels
        runtimeDescriptor.setRangeKeys(Paths.D1, Paths.D2, Paths.D3, Paths.D4, Paths.D5, Paths.D6, Paths.D7);

        // Add white dashed line at zero
        runtimeDescriptor.addHorizontalLine(new LineInfo(0.0, null, 1.0f, new float[]{3, 3}));

        // Initialize wavelet analyzer with default settings
        initializeWaveletAnalyzer();
    }

    private void initializeWaveletAnalyzer() {
        // Initialize with default settings - will be updated when settings change
        this.waveletAnalyzer = WaveletAnalyzerFactory.create(WaveletType.DAUBECHIES4);
    }

    private void checkAndUpdateSettings() {
        // Get current settings
        String waveletTypeStr = getSettings().getString(WAVELET_TYPE, WaveletType.DAUBECHIES4.getDisplayName());

        // Check if settings have changed
        boolean settingsChanged = !waveletTypeStr.equals(lastWaveletType);

        if (settingsChanged || waveletAnalyzer == null) {
            updateWaveletAnalyzer();

            // Update tracked values
            lastWaveletType = waveletTypeStr;

            logger.debug("Settings changed - updated wavelet analyzer");
        }
    }

    private void updateWaveletAnalyzer() {
        // Get wavelet type from settings
        String waveletTypeStr = getSettings().getString(WAVELET_TYPE, WaveletType.DAUBECHIES4.getDisplayName());
        
        // Update analyzer using factory
        this.waveletAnalyzer = WaveletAnalyzerFactory.create(waveletTypeStr);

        logger.debug("Updated wavelet analyzer with wavelet type: {}", waveletTypeStr);
    }

    // parseWaveletType method removed - now using WaveletType.parse() from shared enum

    /**
     * Called when the study is activated on a chart or instrument.
     * This is a good place to perform any setup or logging needed when the study starts.
     *
     * @param ctx The order context associated with the activation event.
     */
    @Override
    public void onActivate(OrderContext ctx) {
        logger.debug("Activating AutoWavelets");
        updateWaveletAnalyzer();
    }

    @Override
    public void onLoad(Defaults defaults) {
        logger.debug("AutoWavelets onLoad called - bar type or settings may have changed");
        // Ensure wavelet analyzer is initialized when study loads
        if (waveletAnalyzer == null) {
            initializeWaveletAnalyzer();
        }
        // Update tracked settings to ensure proper recalculation
        lastWaveletType = null; // Force settings check on next calculate
    }

    /**
     * This method is called when the study is first loaded or when its settings change.
     * It's the ideal place to initialize our queue based on the user's settings.
     */
    @Override
    public int getMinBars(DataContext ctx, BarSize bs) {
        int autoDecompositionLevel = getAutoDecompositionLevel(bs);
        return getAutoLookbackLength(autoDecompositionLevel);
    }

    /**
     * Auto-selects a meaningful decomposition level based on the chart's bar size.
     * The goal is to find the level that corresponds to a full trading session (~390 minutes).
     *
     * @param bs The BarSize object for the current chart.
     * @return An automatically calculated decomposition level.
     */
    private int getAutoDecompositionLevel(BarSize bs) {
        // Step 1: Convert the chart's bar size into a consistent unit (minutes).
        long barSizeInMinutes;
        if (bs.getIntervalType() == Enums.IntervalType.SECOND) {
            barSizeInMinutes = bs.getSizeMillis() / 1000L;
        } else if (bs.getIntervalType() == Enums.IntervalType.MINUTE) {
            barSizeInMinutes = bs.getSizeMillis() / 1000L / 60L;
        } else if (bs.getIntervalType() == Enums.IntervalType.HOUR) {
            barSizeInMinutes = (bs.getSizeMillis() / 1000L / 60L) * 60L;
        } else if (bs.getIntervalType() == Enums.IntervalType.DAY) {
            // Assuming a standard ~6.5 hour RTH session (like US Equities)
            barSizeInMinutes = (bs.getSizeMillis() / 1000L / 60L) * 390L;
        } else {
            // For Tick, Volume, Range, etc., we can't derive a time-based level.
            // Fall back to a sensible default.
            return 7;
        }

        if (barSizeInMinutes <= 0) return 7; // Safeguard

        // Step 2: Define our target cycle in minutes (e.g., a full trading day).
        final double TARGET_CYCLE_MINUTES = 390; // Approx. minutes in a standard session

        // Step 3: Calculate how many bars fit into our target cycle.
        double barsPerTargetCycle = TARGET_CYCLE_MINUTES / barSizeInMinutes;
        if (barsPerTargetCycle < 2) return 1; // Need at least 2 bars to decompose

        // Step 4: Calculate the decomposition level using a base-2 logarithm.
        // This finds 'j' in the equation: 2^j ≈ barsPerTargetCycle
        int calculatedLevel = (int) Math.floor(Math.log(barsPerTargetCycle) / Math.log(2.0));

        logger.debug("Auto-selecting decomposition level: {} for BarSize: {}", calculatedLevel, bs);

        // Return the calculated level, but ensure it's within a reasonable range.
        return Math.max(1, Math.min(7, calculatedLevel));
    }

    /**
     * Auto-selects a robust lookback period based on the decomposition level.
     * The goal is to ensure the lookback window is long enough to capture
     * several cycles of the slowest wavelet component being analyzed.
     *
     * @param decompositionLevel The number of wavelet decomposition levels.
     * @return An automatically calculated lookback period.
     */
    private int getAutoLookbackLength(int decompositionLevel) {
        // The period of the slowest detail wavelet (Dj) is roughly 2^j bars.
        // To be safe, let's use the next level up to define the cycle length.
        double slowestCyclePeriod = Math.pow(2, decompositionLevel + 1);

        // A good lookback should contain at least 2-3 full cycles. Let's use 3.
        int calculatedLookback = (int) Math.ceil(slowestCyclePeriod * 3);

        // Cap the value to a reasonable range to prevent excessive memory usage.
        int lookback = Math.max(256, Math.min(1000, calculatedLookback));

        logger.debug("Auto-selecting lookback length: {} for decomposition level: {}", lookback, decompositionLevel);
        return lookback;
    }

    /**
     * Performs the main calculation for the AutoWavelets study.
     * <p>
     * For each bar (at the given index), this method:
     * 1. Determines the appropriate wavelet decomposition level and lookback period based on the chart's bar size.
     * 2. Collects the required number of closing prices, handling missing values by carrying forward the last valid price.
     * 3. Runs the MODWT (Maximal Overlap Discrete Wavelet Transform) using the configured wavelet.
     * 4. Extracts and plots the latest coefficients for each decomposition level that is active.
     * <p>
     * Any exceptions during calculation are logged for debugging.
     *
     * @param index The current bar index for which to perform calculations.
     * @param ctx   The data context providing access to chart data and settings.
     */
    @Override
    protected void calculate(int index, DataContext ctx) {
        try {
            // Check if settings have changed and update components if needed
            checkAndUpdateSettings();

            BarSize barSize = ctx.getChartBarSize();
            int autoDecompositionLevel = getAutoDecompositionLevel(barSize);
            int autoLookbackPeriod = getAutoLookbackLength(autoDecompositionLevel);

            DataSeries dataSeries = ctx.getDataSeries();

            double[] closingPrices = new double[autoLookbackPeriod];
            double lastValidPrice = dataSeries.getClose(index - autoLookbackPeriod + 1);

            for (int i = 0; i < autoLookbackPeriod; i++) {
                int barIndex = index - (autoLookbackPeriod - 1) + i;
                Double close = dataSeries.getDouble(barIndex, Enums.BarInput.CLOSE, ctx.getInstrument());

                if (close != null) {
                    closingPrices[i] = close;
                    lastValidPrice = close;
                } else {
                    closingPrices[i] = lastValidPrice;
                    logger.warn("Null close price at index {}, using previous price of {}", barIndex, lastValidPrice);
                }
            }

            double[][] modwtCoefficients = waveletAnalyzer.performForwardMODWT(closingPrices, autoDecompositionLevel);
            int lastCoeffIndex = closingPrices.length - 1;

            // Dynamically plot only the levels that are active based on the calculated decomposition level
            for (int level = 0; level < autoDecompositionLevel && level < MAX_DECOMPOSITION_LEVELS; level++) {
                Paths valueKey = Paths.values()[level];
                dataSeries.setDouble(index, valueKey, modwtCoefficients[level][lastCoeffIndex]);
            }

            // Clear any unused higher levels to avoid stale data
            for (int level = autoDecompositionLevel; level < MAX_DECOMPOSITION_LEVELS; level++) {
                Paths valueKey = Paths.values()[level];
                dataSeries.setDouble(index, valueKey, Double.NaN);
            }

        } catch (Exception e) {
            logger.error("Error during wavelet calculation at index {}", index, e);
        }
    }

    // WaveletType enum has been moved to com.prophetizo.wavelets.WaveletType

    // Enums for calculated values and settings keys
    enum Paths {D1, D2, D3, D4, D5, D6, D7}
}
