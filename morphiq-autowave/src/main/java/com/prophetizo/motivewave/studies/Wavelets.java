package com.prophetizo.motivewave.studies;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;
import com.motivewave.platform.sdk.study.RuntimeDescriptor;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.prophetizo.LoggerConfig;
import com.prophetizo.wavelets.WaveletAnalyzer;
import jwave.transforms.wavelets.Wavelet;
import jwave.transforms.wavelets.daubechies.Daubechies4;
import jwave.transforms.wavelets.daubechies.Daubechies6;
import org.slf4j.Logger;

import java.awt.*;
import java.util.Arrays;

@StudyHeader(
        namespace = "com.prophetizo.motivewave.studies",
        id = "WAVELETS",
        name = "Wavelets",
        desc = "Manual Wavelets Study with configurable parameters",
        menu = "MorphIQ | Wavelet Analysis",
        overlay = false,
        requiresBarUpdates = false
)
public class Wavelets extends Study {
    // Settings keys
    final static String WAVELET_TYPE = "WAVELET_TYPE";
    final static String DECOMPOSITION_LEVELS = "DECOMPOSITION_LEVELS";
    final static String LOOKBACK_PERIOD = "LOOKBACK_PERIOD";
    private static final int MAX_DECOMPOSITION_LEVELS = 7;
    private static final Logger logger = LoggerConfig.getLogger(Wavelets.class);
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
        logger.debug("Initializing Wavelets Study");

        SettingsDescriptor settings = new SettingsDescriptor();
        setSettingsDescriptor(settings);

        // General settings tab
        SettingTab generalTab = settings.addTab("General");

        SettingGroup waveletGroup = generalTab.addGroup("Wavelet Configuration");
        waveletGroup.addRow(new DiscreteDescriptor(WAVELET_TYPE, "Wavelet Type",
                WaveletType.DAUBECHIES4.toString(),
                Arrays.asList(
                        new NVP("Daubechies4", "Daubechies4"),
                        new NVP("Daubechies6", "Daubechies6"))));
        waveletGroup.addRow(new IntegerDescriptor(DECOMPOSITION_LEVELS, "Decomposition Levels", 5, 1, MAX_DECOMPOSITION_LEVELS, 1));
        waveletGroup.addRow(new IntegerDescriptor(LOOKBACK_PERIOD, "Lookback Period", 512, 64, 2048, 32));

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
        WaveletType defaultType = WaveletType.DAUBECHIES4;
        this.waveletAnalyzer = new WaveletAnalyzer(defaultType.createWavelet());
    }

    private void checkAndUpdateSettings() {
        // Get current settings
        String waveletTypeStr = getSettings().getString(WAVELET_TYPE, WaveletType.DAUBECHIES4.toString());

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
        String waveletTypeStr = getSettings().getString(WAVELET_TYPE, WaveletType.DAUBECHIES4.toString());
        WaveletType waveletType = parseWaveletType(waveletTypeStr);

        // Update analyzer if needed
        this.waveletAnalyzer = new WaveletAnalyzer(waveletType.createWavelet());

        logger.debug("Updated wavelet analyzer with wavelet type: {}", waveletType);
    }

    private WaveletType parseWaveletType(String waveletTypeStr) {
        try {
            for (WaveletType type : WaveletType.values()) {
                if (type.toString().equals(waveletTypeStr)) {
                    return type;
                }
            }
            logger.warn("Unknown wavelet type '{}'. Valid options: Daubechies4, Daubechies6. Using Daubechies4 as default.", waveletTypeStr);
            return WaveletType.DAUBECHIES4;
        } catch (Exception e) {
            logger.warn("Failed to parse wavelet type '{}'. Valid options: Daubechies4, Daubechies6. Using Daubechies4 as default.", waveletTypeStr);
            return WaveletType.DAUBECHIES4;
        }
    }

    /**
     * Called when the study is activated on a chart or instrument.
     * This is a good place to perform any setup or logging needed when the study starts.
     *
     * @param ctx The order context associated with the activation event.
     */
    @Override
    public void onActivate(OrderContext ctx) {
        logger.debug("Activating Wavelets");
        updateWaveletAnalyzer();
    }

    @Override
    public void onLoad(Defaults defaults) {
        logger.debug("Wavelets onLoad called - bar type or settings may have changed");
        // Ensure wavelet analyzer is initialized when study loads
        if (waveletAnalyzer == null) {
            initializeWaveletAnalyzer();
        }
        // Update tracked settings to ensure proper recalculation
        lastWaveletType = null; // Force settings check on next calculate
    }

    /**
     * This method is called when the study is first loaded or when its settings change.
     * Returns the minimum number of bars required for calculation.
     */
    @Override
    public int getMinBars(DataContext ctx, BarSize bs) {
        return getSettings().getInteger(LOOKBACK_PERIOD, 512);
    }

    /**
     * Performs the main calculation for the Wavelets study.
     * <p>
     * For each bar (at the given index), this method:
     * 1. Gets the manually configured decomposition level and lookback period from settings.
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

            // Get manually configured parameters
            int decompositionLevels = getSettings().getInteger(DECOMPOSITION_LEVELS, 5);
            int lookbackPeriod = getSettings().getInteger(LOOKBACK_PERIOD, 512);

            DataSeries dataSeries = ctx.getDataSeries();

            // Need enough data for analysis
            if (index < lookbackPeriod) {
                return;
            }

            double[] closingPrices = new double[lookbackPeriod];
            double lastValidPrice = dataSeries.getClose(index - lookbackPeriod + 1);
            int nullCloseCount = 0;

            for (int i = 0; i < lookbackPeriod; i++) {
                int barIndex = index - (lookbackPeriod - 1) + i;
                Double close = dataSeries.getDouble(barIndex, Enums.BarInput.CLOSE, ctx.getInstrument());

                if (close != null) {
                    closingPrices[i] = close;
                    lastValidPrice = close;
                } else {
                    closingPrices[i] = lastValidPrice;
                    nullCloseCount++; // Increment counter for null values
                }
            }

            if (nullCloseCount > 0) {
                logger.warn("Encountered {} null close prices during lookback period, using previous price of {}",
                        nullCloseCount, lastValidPrice);
            }
            double[][] modwtCoefficients = waveletAnalyzer.performForwardMODWT(closingPrices, decompositionLevels);
            int lastCoeffIndex = closingPrices.length - 1;

            // Plot only the levels that are active based on the configured decomposition level
            for (int level = 0; level < decompositionLevels && level < MAX_DECOMPOSITION_LEVELS; level++) {
                Paths valueKey = Paths.values()[level];
                dataSeries.setDouble(index, valueKey, modwtCoefficients[level][lastCoeffIndex]);
            }

            // Clear any unused higher levels to avoid stale data
            for (int level = decompositionLevels; level < MAX_DECOMPOSITION_LEVELS; level++) {
                Paths valueKey = Paths.values()[level];
                dataSeries.setDouble(index, valueKey, Double.NaN);
            }

            logger.trace("Calculated wavelets at index {} with {} levels and {} lookback",
                    index, decompositionLevels, lookbackPeriod);

        } catch (Exception e) {
            logger.error("Error during wavelet calculation at index {}", index, e);
        }
    }

    public enum WaveletType {
        DAUBECHIES4("Daubechies4"),
        DAUBECHIES6("Daubechies6");

        private final String displayName;

        WaveletType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        public Wavelet createWavelet() {
            switch (this) {
                case DAUBECHIES4:
                    return new Daubechies4();
                case DAUBECHIES6:
                    return new Daubechies6();
                default:
                    return new Daubechies4();
            }
        }
    }

    // Enums for calculated values and settings keys
    enum Paths {D1, D2, D3, D4, D5, D6, D7}
}