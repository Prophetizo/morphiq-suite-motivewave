package com.prophetizo.motivewave.studies;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;
import com.motivewave.platform.sdk.study.RuntimeDescriptor;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.motivewave.platform.sdk.study.Plot;
import com.prophetizo.LoggerConfig;
import com.prophetizo.motivewave.common.StudyUIHelper;
import com.prophetizo.wavelets.trading.*;
import org.slf4j.Logger;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

@StudyHeader(
        namespace = "com.prophetizo.motivewave.studies",
        id = "DENOISED_PRICE",
        name = "Denoised Price",
        desc = "Wavelet-based Denoising Study",
        menu = "MorphIQ | Wavelet Analysis",
        overlay = true,
        requiresBarUpdates = false
)
public class DenoisedTrendFollowing extends Study {

    // Settings keys
    final static String WAVELET_TYPE = "WAVELET_TYPE";
    final static String DECOMPOSITION_LEVELS = "DECOMPOSITION_LEVELS";
    final static String LOOKBACK_PERIOD = "LOOKBACK_PERIOD";
    final static String NOISE_LEVELS = "NOISE_LEVELS";
    final static String THRESHOLD_TYPE = "THRESHOLD_TYPE";
    final static String THRESHOLD_MULTIPLIER = "THRESHOLD_MULTIPLIER";

    // Path keys
    final static String DENOISED_PATH = "DENOISED_PATH";
    final static String ORIGINAL_PATH = "ORIGINAL_PATH";
    final static String NOISE_PANE_PATH = "NOISE_PANE_PATH";
    final static String APPROXIMATION_PATH = "APPROXIMATION_PATH";

    // Plot keys
    final static String NOISE_PLOT = "NOISE_PLOT";

    private static final Logger logger = LoggerConfig.getLogger(DenoisedTrendFollowing.class);

    // Modern VectorWave-based services
    private TradingDenoiser denoiser;
    private WaveletConfigHelper.TradingConfig config;

    // Track settings to detect changes
    private String lastWaveletType = null;
    private String lastDenoiseStrategy = null;

    // WaveletType enum has been moved to com.prophetizo.wavelets.WaveletType


    @Override
    public void initialize(Defaults defaults) {
        logger.debug("Initializing Denoised Trend Following Study");

        SettingsDescriptor settings = new SettingsDescriptor();
        setSettingsDescriptor(settings);

        // General settings tab
        SettingTab generalTab = settings.addTab("General");

        SettingGroup waveletGroup = generalTab.addGroup("Wavelet Configuration");
        
        // Use helper to create wavelet options - show all for denoising analysis
        List<NVP> waveletOptions = StudyUIHelper.createWaveletOptions();
        
        waveletGroup.addRow(StudyUIHelper.createWaveletTypeDescriptor(WAVELET_TYPE,
                com.prophetizo.wavelets.WaveletType.DAUBECHIES6.getDisplayName(), waveletOptions));
        waveletGroup.addRow(new IntegerDescriptor(DECOMPOSITION_LEVELS, "Decomposition Levels", 5, 1, 8, 1));
        waveletGroup.addRow(new IntegerDescriptor(LOOKBACK_PERIOD, "Lookback Period", 512, 64, 1024, 32));

        SettingGroup denoisingGroup = generalTab.addGroup("Denoising Parameters");
        denoisingGroup.addRow(new StringDescriptor(NOISE_LEVELS, "Noise Levels to Remove", "0,1"));
        denoisingGroup.addRow(new DiscreteDescriptor("DENOISE_STRATEGY", "Denoising Strategy", "BALANCED",
                Arrays.asList(
                        new NVP("CONSERVATIVE", "Conservative - Preserve most signal"),
                        new NVP("BALANCED", "Balanced - Good compromise"),
                        new NVP("AGGRESSIVE", "Aggressive - Maximum noise reduction"))));

        SettingTab displayTab = settings.addTab("Display");


        var paths = displayTab.addGroup("Price Paths");
        paths.addRow(new PathDescriptor(DENOISED_PATH, "Denoised Price",
                new Color(0, 150, 255), 2.0f, null, true, true, true));
        paths.addRow(new PathDescriptor(ORIGINAL_PATH, "Original Price",
                new Color(128, 128, 128), 1.0f, null, true, false, true));
        paths.addRow(new PathDescriptor(APPROXIMATION_PATH, "Approximation (Smooth Trend)",
                new Color(255, 215, 0), 2.0f, null, true, false, true));

        var noisePaths = displayTab.addGroup("Noise Pane Paths");
        noisePaths.addRow(new PathDescriptor(NOISE_PANE_PATH, "Removed Noise",
                new Color(255, 100, 100), 1.0f, null, true, true, true));

        RuntimeDescriptor desc = new RuntimeDescriptor();
        setRuntimeDescriptor(desc);

        // Price plot (default) - for denoised, original price, and approximation
        desc.declarePath(Values.DENOISED, DENOISED_PATH);
        desc.declarePath(Values.ORIGINAL, ORIGINAL_PATH);
        desc.declarePath(Values.APPROXIMATION, APPROXIMATION_PATH);
        desc.declareIndicator(Values.DENOISED, "Denoised");
        desc.declareIndicator(Values.ORIGINAL, "Original");
        desc.declareIndicator(Values.APPROXIMATION, "Approx");
        desc.setRangeKeys(Values.DENOISED, Values.ORIGINAL, Values.APPROXIMATION);

        // Always create noise plot - it's now a core feature
        Plot noisePlot = new Plot();
        desc.addPlot(NOISE_PLOT, noisePlot);

        noisePlot.setLabelSettings("Removed Noise");
        noisePlot.setLabelPrefix("Noise");
        noisePlot.setTabName("Noise");
        noisePlot.declarePath(Values.NOISE_PANE, NOISE_PANE_PATH);
        noisePlot.declareIndicator(Values.NOISE_PANE, "Noise");
        noisePlot.setRangeKeys(Values.NOISE_PANE);
        noisePlot.addHorizontalLine(new LineInfo(0.0, null, 1.0f, new float[]{3, 3}));

        // Initialize modern trading services
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
            
            // Create default config for denoising
            this.config = WaveletConfigHelper.forTradingStyle(
                TradingStyle.DAY_TRADING, null // Will be set properly in onLoad
            );
            
            // Create denoiser with default wavelet
            this.denoiser = factory.createDenoiser("db6");
            
            logger.info("Initialized modern trading services for denoising with VectorWave");
        } catch (Exception e) {
            logger.error("Failed to initialize trading services for denoising", e);
        }
    }

    @Override
    public void onActivate(OrderContext ctx) {
        logger.debug("Activating Denoised Trend Following");
        updateTradingServices();
    }

    @Override
    public void onLoad(Defaults defaults) {
        logger.debug("DenoisedTrendFollowing onLoad called - bar type or settings may have changed");
        
        // Ensure trading services are initialized when study loads
        if (denoiser == null) {
            initializeTradingServices();
        }
        
        // Update configuration for the current bar size
        if (defaults != null) {
            // Use reflection to avoid direct dependency on MotiveWave SDK in core
            this.config = WaveletConfigHelper.fromMotiveWaveSettings(getSettings(), defaults);
            logger.debug("Updated configuration for bar size context");
        }
        
        // Clear tracked settings to force update on next calculate
        lastWaveletType = null;
        lastDenoiseStrategy = null;
    }

    private void checkAndUpdateSettings() {
        // Get current settings
        String waveletTypeStr = getSettings().getString(WAVELET_TYPE, com.prophetizo.wavelets.WaveletType.DAUBECHIES6.getDisplayName());
        String denoiseStrategyStr = getSettings().getString("DENOISE_STRATEGY", "BALANCED");

        // Check if settings have changed
        boolean settingsChanged = !waveletTypeStr.equals(lastWaveletType) ||
                !denoiseStrategyStr.equals(lastDenoiseStrategy);

        if (settingsChanged || denoiser == null) {
            updateTradingServices();

            // Update tracked values
            lastWaveletType = waveletTypeStr;
            lastDenoiseStrategy = denoiseStrategyStr;

            logger.debug("Settings changed - updated trading services");
        }
    }

    private void updateTradingServices() {
        try {
            // Get wavelet type and strategy from settings
            String waveletTypeStr = getSettings().getString(WAVELET_TYPE, com.prophetizo.wavelets.WaveletType.DAUBECHIES6.getDisplayName());
            String strategyStr = getSettings().getString("DENOISE_STRATEGY", "BALANCED");
            
            // Map to VectorWave wavelet name
            String vectorWaveType = WaveletConfigHelper.mapWaveletType(waveletTypeStr);
            
            // Parse denoise strategy
            DenoiseStrategy strategy = WaveletConfigHelper.parseDenoiseStrategy(strategyStr);
            
            // Create new denoiser with updated configuration
            TradingWaveletFactory factory = TradingWaveletFactory.getInstance();
            this.denoiser = factory.createDenoiser(vectorWaveType);
            
            // Update configuration
            this.config = new WaveletConfigHelper.TradingConfig(
                vectorWaveType, 5, strategy, TradingStyle.DAY_TRADING
            );

            logger.debug("Updated trading services with wavelet: {} -> {}, strategy: {}", 
                        waveletTypeStr, vectorWaveType, strategy);
        } catch (Exception e) {
            logger.error("Failed to update trading services", e);
        }
    }

    private int[] parseNoiseLevels(String noiseLevelsStr) {
        try {
            String[] parts = noiseLevelsStr.split(",");
            int[] levels = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                levels[i] = Integer.parseInt(parts[i].trim());
            }
            return levels;
        } catch (Exception e) {
            logger.warn("Failed to parse noise levels '{}', using default [0,1]", noiseLevelsStr);
            return new int[]{0, 1}; // Default: remove D1 and D2
        }
    }


    @Override
    public int getMinBars(DataContext ctx, BarSize bs) {
        return getSettings().getInteger(LOOKBACK_PERIOD, 512);
    }

    @Override
    protected void calculate(int index, DataContext ctx) {
        try {
            DataSeries dataSeries = ctx.getDataSeries();

            // Check if settings have changed and update components if needed
            checkAndUpdateSettings();

            // Get configuration from settings
            int lookbackPeriod = getSettings().getInteger(LOOKBACK_PERIOD, 512);
            int decompositionLevels = getSettings().getInteger(DECOMPOSITION_LEVELS, 5);

            // Need enough data for analysis
            if (index < lookbackPeriod) {
                return;
            }

            // Collect price data
            double[] prices = new double[lookbackPeriod];
            double lastValidPrice = dataSeries.getClose(index - lookbackPeriod + 1);

            for (int i = 0; i < lookbackPeriod; i++) {
                int barIndex = index - (lookbackPeriod - 1) + i;
                Double close = dataSeries.getDouble(barIndex, Enums.BarInput.CLOSE, ctx.getInstrument());

                if (close != null) {
                    prices[i] = close;
                    lastValidPrice = close;
                } else {
                    prices[i] = lastValidPrice;
                    logger.warn("Null close price at index {}, using previous price of {}",
                            barIndex, lastValidPrice);
                }
            }

            // Modern VectorWave-based denoising
            if (denoiser == null) {
                logger.warn("Trading denoiser not initialized, initializing now");
                initializeTradingServices();
                if (denoiser == null) {
                    logger.error("Failed to initialize denoiser, skipping calculation");
                    return;
                }
            }
            
            // Get denoise strategy from configuration
            DenoiseStrategy strategy = config != null ? config.getStrategy() : DenoiseStrategy.BALANCED;
            
            // Perform comprehensive denoising with VectorWave
            DenoisedPriceData denoisedResult = denoiser.denoise(prices, strategy);
            
            // Extract trend (approximation) using VectorWave
            double[] approximationPrices = denoiser.extractTrend(prices, decompositionLevels);
            
            double[] denoisedPrices = denoisedResult.getCleanedPrices();
            double latestApproximation = approximationPrices[approximationPrices.length - 1];
            
            // Log quality information
            if (denoisedResult.isHighQuality()) {
                logger.debug("High-quality denoising achieved: {}", denoisedResult.getQualityAssessment());
            }

            // Get the latest denoised value
            double latestDenoised = denoisedPrices[denoisedPrices.length - 1];
            double originalPrice = prices[prices.length - 1];
            double noise = originalPrice - latestDenoised;

            // Plot the results - path visibility is controlled by path display checkboxes
            dataSeries.setDouble(index, Values.DENOISED, latestDenoised);
            dataSeries.setDouble(index, Values.ORIGINAL, originalPrice);

            // Always display noise in separate pane (raw value around zero)
            dataSeries.setDouble(index, Values.NOISE_PANE, noise);
            
            // Display approximation (smooth trend)
            dataSeries.setDouble(index, Values.APPROXIMATION, latestApproximation);

            String formattedOriginalPrice = String.format("%.2f", originalPrice);
            String formattedDenoised = String.format("%.2f", latestDenoised);
            String formattedNoise = String.format("%.4f", noise);
            String formattedApprox = String.format("%.2f", latestApproximation);
            
            // Log with more detail when noise is large
            if (Math.abs(noise) > 10) {
                logger.debug("Large noise detected at index {}: original={}, denoised={}, noise={}, approx={}",
                    index, formattedOriginalPrice, formattedDenoised, formattedNoise, formattedApprox);
            } else {
                logger.trace("Denoised trend at index {}: original={}, denoised={}, noise={}, approx={}",
                    index, formattedOriginalPrice, formattedDenoised, formattedNoise, formattedApprox);
            }

        } catch (Exception e) {
            logger.error("Error during denoised trend calculation at index " + index, e);
        }
    }

    // parseWaveletType method removed - now using WaveletType.parse() from shared enum

    // Enums for values and settings keys
    enum Values { DENOISED, ORIGINAL, NOISE_PANE, APPROXIMATION }
}