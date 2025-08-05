package com.prophetizo.motivewave.studies;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;
import com.motivewave.platform.sdk.study.RuntimeDescriptor;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.prophetizo.LoggerConfig;
import com.prophetizo.motivewave.common.StudyUIHelper;
import com.prophetizo.wavelets.trading.*;
import org.slf4j.Logger;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

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

    // Modern VectorWave-based services
    private TradingWaveletAnalyzer analyzer;
    private WaveletConfigHelper.TradingConfig config;
    
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
        
        // Use helper to create wavelet options - show all for manual analysis
        List<NVP> waveletOptions = StudyUIHelper.createWaveletOptions();
        
        waveletGroup.addRow(StudyUIHelper.createWaveletTypeDescriptor(WAVELET_TYPE,
                com.prophetizo.wavelets.WaveletType.DAUBECHIES4.getDisplayName(), waveletOptions));
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

        // Initialize modern trading services with default configuration
        initializeTradingServices();
    }

    private void initializeTradingServices() {
        try {
            // Initialize with default configuration - will be updated when settings change
            TradingWaveletFactory factory = TradingWaveletFactory.getInstance();
            
            // Validate VectorWave integration
            if (!factory.validateVectorWaveIntegration()) {
                logger.error("VectorWave integration validation failed");
                return;
            }
            
            // Create default config
            this.config = WaveletConfigHelper.forTradingStyle(
                TradingStyle.DAY_TRADING, null // Will be set properly in onLoad
            );
            
            // Create analyzer with default wavelet
            this.analyzer = factory.createAnalyzer("db4");
            
            logger.info("Initialized modern trading services with VectorWave");
        } catch (Exception e) {
            logger.error("Failed to initialize trading services", e);
        }
    }

    private void checkAndUpdateSettings() {
        // Get current settings
        String waveletTypeStr = getSettings().getString(WAVELET_TYPE, com.prophetizo.wavelets.WaveletType.DAUBECHIES4.getDisplayName());

        // Check if settings have changed
        boolean settingsChanged = !waveletTypeStr.equals(lastWaveletType);

        if (settingsChanged || analyzer == null) {
            updateTradingServices();

            // Update tracked values
            lastWaveletType = waveletTypeStr;

            logger.debug("Settings changed - updated trading services");
        }
    }

    private void updateTradingServices() {
        try {
            // Get wavelet type from settings
            String waveletTypeStr = getSettings().getString(WAVELET_TYPE, com.prophetizo.wavelets.WaveletType.DAUBECHIES4.getDisplayName());
            
            // Map to VectorWave wavelet name
            String vectorWaveType = WaveletConfigHelper.mapWaveletType(waveletTypeStr);
            
            // Create new analyzer with updated configuration
            TradingWaveletFactory factory = TradingWaveletFactory.getInstance();
            this.analyzer = factory.createAnalyzer(vectorWaveType);
            
            // Update configuration
            this.config = new WaveletConfigHelper.TradingConfig(
                vectorWaveType, 5, DenoiseStrategy.BALANCED, TradingStyle.DAY_TRADING
            );

            logger.debug("Updated trading services with wavelet type: {} -> {}", waveletTypeStr, vectorWaveType);
        } catch (Exception e) {
            logger.error("Failed to update trading services", e);
        }
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
        logger.debug("Activating Wavelets");
        updateTradingServices();
    }

    @Override
    public void onLoad(Defaults defaults) {
        logger.debug("Wavelets onLoad called - bar type or settings may have changed");
        
        // Ensure trading services are initialized when study loads
        if (analyzer == null) {
            initializeTradingServices();
        }
        
        // Update configuration for the current bar size
        if (defaults != null) {
            // Use reflection to avoid direct dependency on MotiveWave SDK in core
            this.config = WaveletConfigHelper.fromMotiveWaveSettings(getSettings(), defaults);
            logger.debug("Updated configuration for bar size context");
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

            // Modern VectorWave-based analysis
            if (analyzer == null) {
                logger.warn("Trading analyzer not initialized, initializing now");
                initializeTradingServices();
                if (analyzer == null) {
                    logger.error("Failed to initialize analyzer, skipping calculation");
                    return;
                }
            }
            
            // Perform comprehensive trading analysis
            TradingAnalysisResult analysis = analyzer.analyzePriceAction(closingPrices, decompositionLevels);
            
            // Extract detail signals for each level (manual study shows raw coefficients)
            for (int level = 0; level < decompositionLevels && level < MAX_DECOMPOSITION_LEVELS; level++) {
                Paths valueKey = Paths.values()[level];
                double signalValue = analysis.getDetailSignalAtLevel(level);
                dataSeries.setDouble(index, valueKey, signalValue);
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

    // WaveletType enum has been moved to com.prophetizo.wavelets.WaveletType

    // Enums for calculated values and settings keys
    enum Paths {D1, D2, D3, D4, D5, D6, D7}
}