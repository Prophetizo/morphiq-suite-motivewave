package com.prophetizo.motivewave.studies;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;
import com.motivewave.platform.sdk.study.RuntimeDescriptor;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.motivewave.platform.sdk.study.Plot;
import com.prophetizo.LoggerConfig;
import com.prophetizo.wavelets.WaveletAnalyzer;
import com.prophetizo.wavelets.WaveletDenoiser;
import jwave.transforms.wavelets.daubechies.Daubechies4;
import jwave.transforms.wavelets.daubechies.Daubechies6;
import jwave.transforms.wavelets.Wavelet;
import org.slf4j.Logger;

import java.awt.*;
import java.util.Arrays;

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

    private WaveletAnalyzer waveletAnalyzer;
    private WaveletDenoiser denoiser;

    // Track settings to detect changes
    private String lastNoiseLevels = null;
    private String lastThresholdType = null;
    private double lastThresholdMultiplier = -1;
    private String lastWaveletType = null;

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
                case DAUBECHIES4: return new Daubechies4();
                case DAUBECHIES6: return new Daubechies6();
                default: return new Daubechies4();
            }
        }
    }


    @Override
    public void initialize(Defaults defaults) {
        logger.debug("Initializing Denoised Trend Following Study");

        SettingsDescriptor settings = new SettingsDescriptor();
        setSettingsDescriptor(settings);

        // General settings tab
        SettingTab generalTab = settings.addTab("General");

        SettingGroup waveletGroup = generalTab.addGroup("Wavelet Configuration");
        waveletGroup.addRow(new DiscreteDescriptor(WAVELET_TYPE, "Wavelet Type",
                WaveletType.DAUBECHIES6.toString(),
                Arrays.asList(
                        new NVP("Daubechies4", "Daubechies4"),
                        new NVP("Daubechies6", "Daubechies6"))));
        waveletGroup.addRow(new IntegerDescriptor(DECOMPOSITION_LEVELS, "Decomposition Levels", 5, 1, 8, 1));
        waveletGroup.addRow(new IntegerDescriptor(LOOKBACK_PERIOD, "Lookback Period", 512, 64, 1024, 32));

        SettingGroup denoisingGroup = generalTab.addGroup("Denoising Parameters");
        denoisingGroup.addRow(new StringDescriptor(NOISE_LEVELS, "Noise Levels to Remove", "0,1"));
        denoisingGroup.addRow(new DiscreteDescriptor(THRESHOLD_TYPE, "Denoising Method", "HARD",
                Arrays.asList(
                        new NVP("ZERO", "Zero Out Coefficients"),
                        new NVP("HARD", "Hard Threshold"),
                        new NVP("SOFT", "Soft Threshold"),
                        new NVP("ADAPTIVE_HARD", "Adaptive Hard Threshold"),
                        new NVP("ADAPTIVE_SOFT", "Adaptive Soft Threshold"))));
        denoisingGroup.addRow(new DoubleDescriptor(THRESHOLD_MULTIPLIER, "Threshold Multiplier", 0.1, 0.01, 2.0, 0.01));

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

        // Initialize components
        initializeWaveletComponents();
    }

    private void initializeWaveletComponents() {
        // Initialize with default settings - will be updated in onSettingsUpdated
        WaveletType defaultType = WaveletType.DAUBECHIES6;
        this.waveletAnalyzer = new WaveletAnalyzer(defaultType.createWavelet());
        this.denoiser = new WaveletDenoiser(waveletAnalyzer);
    }

    @Override
    public void onActivate(OrderContext ctx) {
        logger.debug("Activating Denoised Trend Following");
        updateWaveletComponents();
    }

    @Override
    public void onLoad(Defaults defaults) {
        logger.debug("DenoisedTrendFollowing onLoad called - bar type or settings may have changed");
        // Ensure components are initialized when study loads
        if (waveletAnalyzer == null || denoiser == null) {
            initializeWaveletComponents();
        }
        // Clear tracked settings to force update on next calculate
        lastNoiseLevels = null;
        lastThresholdType = null;
        lastThresholdMultiplier = -1;
        lastWaveletType = null;
    }

    private void checkAndUpdateSettings() {
        // Get current settings
        String noiseLevelsStr = getSettings().getString(NOISE_LEVELS, "0,1");
        String thresholdTypeStr = getSettings().getString(THRESHOLD_TYPE, "HARD");
        double thresholdMultiplier = getSettings().getDouble(THRESHOLD_MULTIPLIER, 0.1);
        String waveletTypeStr = getSettings().getString(WAVELET_TYPE, WaveletType.DAUBECHIES6.toString());

        // Check if any settings have changed
        boolean settingsChanged = !noiseLevelsStr.equals(lastNoiseLevels) ||
                !thresholdTypeStr.equals(lastThresholdType) ||
                thresholdMultiplier != lastThresholdMultiplier ||
                !waveletTypeStr.equals(lastWaveletType);

        if (settingsChanged || denoiser == null) {
            updateWaveletComponents();

            // Update tracked values
            lastNoiseLevels = noiseLevelsStr;
            lastThresholdType = thresholdTypeStr;
            lastThresholdMultiplier = thresholdMultiplier;
            lastWaveletType = waveletTypeStr;

            logger.debug("Settings changed - updated wavelet components");
        }
    }

    private void updateWaveletComponents() {
        // Get wavelet type from settings
        String waveletTypeStr = getSettings().getString(WAVELET_TYPE, WaveletType.DAUBECHIES6.toString());
        WaveletType waveletType = parseWaveletType(waveletTypeStr);
        this.waveletAnalyzer = new WaveletAnalyzer(waveletType.createWavelet());

        // Update denoiser configuration from settings
        this.denoiser = new WaveletDenoiser(waveletAnalyzer);

        // Get settings values
        String noiseLevelsStr = getSettings().getString(NOISE_LEVELS, "0,1");
        String thresholdTypeStr = getSettings().getString(THRESHOLD_TYPE, "HARD");
        double thresholdMultiplier = getSettings().getDouble(THRESHOLD_MULTIPLIER, 0.1);

        // Parse noise levels first as they're always needed
        int[] noiseLevels = parseNoiseLevels(noiseLevelsStr);
        denoiser.setNoiseLevels(noiseLevels);
        
        // Only configure thresholding parameters if not using ZERO method
        if (!"ZERO".equals(thresholdTypeStr)) {
            // Parse threshold type and determine if adaptive
            boolean useAdaptiveCalculation = thresholdTypeStr.startsWith("ADAPTIVE_");
            WaveletDenoiser.ThresholdType thresholdType;

            if (thresholdTypeStr.equals("HARD") || thresholdTypeStr.equals("ADAPTIVE_HARD")) {
                thresholdType = WaveletDenoiser.ThresholdType.HARD;
            } else if (thresholdTypeStr.equals("SOFT") || thresholdTypeStr.equals("ADAPTIVE_SOFT")) {
                thresholdType = WaveletDenoiser.ThresholdType.SOFT;
            } else {
                // Default to soft thresholding
                thresholdType = WaveletDenoiser.ThresholdType.SOFT;
                logger.warn("Unknown threshold type '{}', defaulting to SOFT", thresholdTypeStr);
            }
            
            denoiser.setThresholdType(thresholdType);
            denoiser.setThresholdMultiplier(thresholdMultiplier);
            denoiser.setAdaptiveThresholding(useAdaptiveCalculation);
        }

        logger.debug("Updated wavelet components: {} with {} noise levels, method: {}",
                waveletType, noiseLevels.length, thresholdTypeStr);
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

            // Get approximation (smooth trend)
            double[] approximationPrices = denoiser.getApproximation(prices, decompositionLevels);
            double latestApproximation = approximationPrices[approximationPrices.length - 1];
            
            // Perform denoising
            String thresholdTypeStr = getSettings().getString(THRESHOLD_TYPE, "HARD");
            double[] denoisedPrices;
            
            if ("ZERO".equals(thresholdTypeStr)) {
                // Use the zero-out method for complete removal of specified levels
                denoisedPrices = denoiser.denoiseByZeroing(prices, decompositionLevels);
            } else {
                // Use traditional thresholding methods
                denoisedPrices = denoiser.denoise(prices, decompositionLevels);
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

    private WaveletType parseWaveletType(String waveletTypeStr) {
        try {
            for (WaveletType type : WaveletType.values()) {
                if (type.toString().equals(waveletTypeStr)) {
                    return type;
                }
            }
            logger.warn("Unknown wavelet type '{}'. Valid options: Daubechies4, Daubechies6. Using Daubechies6 as default.", waveletTypeStr);
            return WaveletType.DAUBECHIES6;
        } catch (Exception e) {
            logger.warn("Failed to parse wavelet type '{}'. Valid options: Daubechies4, Daubechies6. Using Daubechies6 as default.", waveletTypeStr);
            return WaveletType.DAUBECHIES6;
        }
    }

    // Enums for values and settings keys
    enum Values { DENOISED, ORIGINAL, NOISE_PANE, APPROXIMATION }
}