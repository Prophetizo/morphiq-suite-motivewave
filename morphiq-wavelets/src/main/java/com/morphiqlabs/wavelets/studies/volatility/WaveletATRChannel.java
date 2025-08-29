package com.morphiqlabs.wavelets.studies.volatility;

import com.morphiqlabs.wavelet.api.TransformType;
import com.morphiqlabs.wavelet.api.WaveletName;
import com.morphiqlabs.wavelet.api.WaveletRegistry;
import com.morphiqlabs.wavelets.core.VectorWaveSwtAdapter;
import com.morphiqlabs.wavelets.core.WaveletAtr;
import com.morphiqlabs.wavelets.core.Thresholds;
import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Wavelet ATR Channel Study - Price overlay bands based on wavelet volatility
 * 
 * This study creates upper and lower bands around price based on the Wavelet ATR
 * volatility measurement. The bands expand and contract based on multi-scale
 * volatility analysis using wavelet decomposition.
 * 
 * Key Features:
 * - Dynamic bands that adapt to market volatility
 * - Multiple scaling methods (Linear, Square Root, Logarithmic, Adaptive)
 * - Optional denoising for cleaner bands
 * - Configurable band multiplier for risk management
 * 
 * @author Morphiq Labs
 */
@StudyHeader(
    namespace = "com.morphiqlabs",
    id = "WAVELET_ATR_CHANNEL",
    name = "Wavelet ATR Channel",
    desc = "Dynamic price bands based on wavelet volatility measurement",
    menu = "MorphIQ Labs",
    overlay = true,
    requiresBarUpdates = true,
    helpLink = "https://docs.morphiqlabs.com/wavelet-atr-channel"
)
public class WaveletATRChannel extends Study {
    private static final Logger logger = LoggerFactory.getLogger(WaveletATRChannel.class);
    
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
            if (value == null) return SQUARE_ROOT;
            
            try {
                return ScalingMethod.valueOf(value);
            } catch (IllegalArgumentException e) {
            }
            
            for (ScalingMethod method : values()) {
                if (method.displayName.equalsIgnoreCase(value)) {
                    return method;
                }
            }
            
            logger.warn("Unknown scaling method: {}, using SQUARE_ROOT", value);
            return SQUARE_ROOT;
        }
    }

    public enum Values {
        WATR_UPPER,
        WATR_LOWER,
        WATR_CENTER
    }

    private static final String WAVELET_TYPE = "waveletType";
    private static final String DECOMPOSITION_LEVELS = "decompositionLevels";
    private static final String WINDOW_LENGTH = "windowLength";
    private static final String USE_DENOISED = "useDenoised";
    private static final String THRESH_LOOKBACK = "threshLookback";
    
    private static final String WATR_K = "watrK";
    private static final String WATR_MULTIPLIER = "watrMultiplier";
    private static final String WATR_SCALE_METHOD = "watrScaleMethod";
    private static final String WATR_SCALE_FACTOR = "watrScaleFactor";
    private static final String WATR_LEVEL_DECAY = "watrLevelDecay";
    private static final String SMOOTHING_PERIOD = "smoothingPeriod";
    private static final String SHOW_CENTER = "showCenter";
    
    private static final String WATR_UPPER_PATH = "watrUpperPath";
    private static final String WATR_LOWER_PATH = "watrLowerPath";
    private static final String WATR_CENTER_PATH = "watrCenterPath";
    
    private static final int DEFAULT_LEVELS = 4;
    private static final int DEFAULT_WINDOW = 256;
    private static final int MIN_WINDOW = 64;
    private static final int MAX_WINDOW = 2048;
    private static final boolean DEFAULT_USE_DENOISED = false;
    private static final int DEFAULT_THRESH_LOOKBACK = 128;
    private static final int MIN_THRESH_LOOKBACK = 32;
    private static final int MAX_THRESH_LOOKBACK = 512;
    
    private static final int DEFAULT_WATR_K = 2;
    private static final double DEFAULT_WATR_MULTIPLIER = 1.0;
    private static final ScalingMethod DEFAULT_WATR_SCALE_METHOD = ScalingMethod.SQUARE_ROOT;
    private static final double DEFAULT_WATR_SCALE_FACTOR = 10.0;
    private static final double DEFAULT_LINEAR_FACTOR = 10.0;
    private static final double DEFAULT_SQRT_FACTOR = 10.0;
    private static final double DEFAULT_LOG_FACTOR = 10.0;
    private static final double DEFAULT_WATR_LEVEL_DECAY = 0.5;
    private static final int DEFAULT_SMOOTHING = 14;
    
    private VectorWaveSwtAdapter swtAdapter;
    private WaveletAtr waveletAtr;
    private String lastWaveletType = null;
    
    @Override
    public void initialize(Defaults defaults) {
        logger.debug("Initializing Wavelet ATR Channel Study");
        
        var sd = createSD();
        
        var generalTab = sd.addTab("General");
        
        var inputsGroup = generalTab.addGroup("Inputs");
        inputsGroup.addRow(new InputDescriptor(Inputs.INPUT, "Input", Enums.BarInput.CLOSE));
        
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
        
        sd.addDependency(new EnabledDependency(USE_DENOISED, THRESH_LOOKBACK));
        
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
        
        var scalingTab = sd.addTab("Scaling");
        
        var scaleGroup = scalingTab.addGroup("Scale Configuration");
        scaleGroup.addRow(
            new DiscreteDescriptor(WATR_SCALE_METHOD, "Scaling Method",
                DEFAULT_WATR_SCALE_METHOD.name(),
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
        
        var displayTab = sd.addTab("Display");
        
        var bandsGroup = displayTab.addGroup("Bands");
        bandsGroup.addRow(
            new DoubleDescriptor(WATR_MULTIPLIER, "Band Multiplier",
                DEFAULT_WATR_MULTIPLIER, 0.5, 3.0, 0.1)
        );
        bandsGroup.addRow(
            new BooleanDescriptor(SHOW_CENTER, "Show Center Line", false)
        );
        
        var pathsGroup = displayTab.addGroup("Lines");
        pathsGroup.addRow(
            new PathDescriptor(WATR_UPPER_PATH, "Upper Band",
                new Color(255, 100, 100, 128), 1.0f, null, true, false, true)
        );
        pathsGroup.addRow(
            new PathDescriptor(WATR_LOWER_PATH, "Lower Band",
                new Color(255, 100, 100, 128), 1.0f, null, true, false, true)
        );
        pathsGroup.addRow(
            new PathDescriptor(WATR_CENTER_PATH, "Center Line",
                new Color(200, 200, 200, 100), 1.0f, new float[]{3, 3}, false, false, true)
        );
        
        var indicatorsGroup = displayTab.addGroup("Indicators");
        indicatorsGroup.addRow(
            new IndicatorDescriptor(Inputs.IND, "Upper Band",
                new Color(255, 100, 100), Color.WHITE, false, true, true)
        );
        indicatorsGroup.addRow(
            new IndicatorDescriptor(Inputs.IND2, "Lower Band",
                new Color(255, 100, 100), Color.WHITE, false, true, true)
        );
        
        var desc = createRD();
        
        desc.setLabelSettings(WAVELET_TYPE, WATR_K);
        
        desc.exportValue(new ValueDescriptor(Values.WATR_UPPER, "Upper Band",
            new String[]{WAVELET_TYPE, WATR_K}));
        desc.exportValue(new ValueDescriptor(Values.WATR_LOWER, "Lower Band",
            new String[]{WAVELET_TYPE, WATR_K}));
        
        desc.declarePath(Values.WATR_UPPER, WATR_UPPER_PATH);
        desc.declarePath(Values.WATR_LOWER, WATR_LOWER_PATH);
        desc.declarePath(Values.WATR_CENTER, WATR_CENTER_PATH);
        
        desc.declareIndicator(Values.WATR_UPPER, Inputs.IND);
        desc.declareIndicator(Values.WATR_LOWER, Inputs.IND2);
        
        desc.setRangeKeys(Values.WATR_UPPER, Values.WATR_LOWER);
        
        setRuntimeDescriptor(desc);
    }
    
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
        
        clearState();
        
        lastWaveletType = null;
        swtAdapter = null;
        waveletAtr = null;
        
        setMinBars(getSettings().getInteger(WINDOW_LENGTH, DEFAULT_WINDOW));
        
        DataSeries series = ctx.getDataSeries();
        if (series != null) {
            logger.info("Marking {} bars for recalculation", series.size());
            
            for (int i = 0; i < series.size(); i++) {
                series.setComplete(i, false);
                series.setDouble(i, Values.WATR_UPPER, null);
                series.setDouble(i, Values.WATR_LOWER, null);
                series.setDouble(i, Values.WATR_CENTER, null);
            }
        }
        
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
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        if (series == null || series.size() == 0) {
            return;
        }
        
        if (series.isComplete(index)) {
            return;
        }
        
        String currentWaveletType = getSettings().getString(WAVELET_TYPE, "db4");
        if (!currentWaveletType.equals(lastWaveletType)) {
            logger.info("Wavelet type changed from {} to {}", lastWaveletType, currentWaveletType);
            swtAdapter = null;
            lastWaveletType = null;
            initializeAdapter();
        }
        
        if (swtAdapter == null) {
            initializeAdapter();
            if (swtAdapter == null) {
                swtAdapter = new VectorWaveSwtAdapter("db4");
                lastWaveletType = "db4";
            }
        }
        
        if (waveletAtr == null) {
            double levelDecay = getSettings().getDouble(WATR_LEVEL_DECAY, DEFAULT_WATR_LEVEL_DECAY);
            int smoothingPeriod = getSettings().getInteger(SMOOTHING_PERIOD, DEFAULT_SMOOTHING);
            this.waveletAtr = new WaveletAtr(smoothingPeriod, levelDecay);
        }
        
        int levels = getSettings().getInteger(DECOMPOSITION_LEVELS, DEFAULT_LEVELS);
        int windowLength = getSettings().getInteger(WINDOW_LENGTH, DEFAULT_WINDOW);
        boolean useDenoised = getSettings().getBoolean(USE_DENOISED, DEFAULT_USE_DENOISED);
        int threshLookback = getSettings().getInteger(THRESH_LOOKBACK, DEFAULT_THRESH_LOOKBACK);
        Object input = getSettings().getInput(Inputs.INPUT);
        
        if (index < windowLength - 1) {
            clearValues(series, index);
            return;
        }
        
        double[] data = getWindowData(series, index, windowLength, input);
        if (data == null || data.length < windowLength) {
            clearValues(series, index);
            return;
        }
        
        try {
            VectorWaveSwtAdapter.SwtResult swtResult = swtAdapter.transform(data, levels);
            if (swtResult == null) {
                clearValues(series, index);
                return;
            }
            
            if (useDenoised) {
                applyBayesShrinkDenoising(swtResult, threshLookback);
            }
            
            calculateBands(series, index, swtResult);
            
            series.setComplete(index);
            
        } catch (Exception e) {
            logger.error("calculate: Error at index {}", index, e);
            clearValues(series, index);
        }
    }
    
    private void calculateBands(DataSeries series, int index, VectorWaveSwtAdapter.SwtResult swtResult) {
        int watrK = getSettings().getInteger(WATR_K, DEFAULT_WATR_K);
        double watrMultiplier = getSettings().getDouble(WATR_MULTIPLIER, DEFAULT_WATR_MULTIPLIER);
        boolean showCenter = getSettings().getBoolean(SHOW_CENTER, false);
        
        double centerPrice = series.getClose(index);
        
        double rawWatr = waveletAtr.calculate(swtResult, watrK);
        
        String scaleMethodStr = getSettings().getString(WATR_SCALE_METHOD, DEFAULT_WATR_SCALE_METHOD.name());
        ScalingMethod scaleMethod = ScalingMethod.fromString(scaleMethodStr);
        double scaleFactor = getSettings().getDouble(WATR_SCALE_FACTOR, DEFAULT_WATR_SCALE_FACTOR);
        
        if (index == series.size() - 1) {
            logger.info("Settings retrieved - Method: {} ({}), Factor: {}", 
                scaleMethod.name(), scaleMethod.getDisplayName(), scaleFactor);
        }
        
        double methodDefaultFactor = getDefaultScaleFactorForMethod(scaleMethod);
        
        if (scaleFactor == DEFAULT_WATR_SCALE_FACTOR || 
            scaleFactor == DEFAULT_LINEAR_FACTOR || 
            scaleFactor == DEFAULT_SQRT_FACTOR || 
            scaleFactor == DEFAULT_LOG_FACTOR) {
            scaleFactor = methodDefaultFactor;
        }
        
        double bandScaleFactor;
        
        switch (scaleMethod) {
            case LINEAR:
                bandScaleFactor = 1.0;
                break;
            case SQUARE_ROOT:
                bandScaleFactor = Math.sqrt(centerPrice / 6450.0);
                break;
            case LOGARITHMIC:
                bandScaleFactor = Math.log(centerPrice / 1000.0) / Math.log(10.0);
                break;
            case ADAPTIVE:
                double recentVol = series.std(index, 20, Enums.BarInput.CLOSE);
                Double avgPriceObj = series.sma(index, 20, Enums.BarInput.CLOSE);
                if (avgPriceObj != null && avgPriceObj > 0 && recentVol > 0) {
                    double volPct = recentVol / avgPriceObj;
                    bandScaleFactor = 1.0 + (volPct * 10.0);
                } else {
                    bandScaleFactor = 1.0;
                }
                break;
            default:
                logger.error("Unexpected scale method enum: {}", scaleMethod);
                bandScaleFactor = 1.0;
        }
        
        bandScaleFactor *= (scaleFactor / 10.0);
        
        double scaledBandWidth = rawWatr * bandScaleFactor * watrMultiplier;
        double upperBand = centerPrice + scaledBandWidth;
        double lowerBand = centerPrice - scaledBandWidth;
        
        series.setDouble(index, Values.WATR_UPPER, upperBand);
        series.setDouble(index, Values.WATR_LOWER, lowerBand);
        
        if (showCenter) {
            series.setDouble(index, Values.WATR_CENTER, centerPrice);
        }
        
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
    
    private void clearValues(DataSeries series, int index) {
        series.setDouble(index, Values.WATR_UPPER, null);
        series.setDouble(index, Values.WATR_LOWER, null);
        series.setDouble(index, Values.WATR_CENTER, null);
    }
    
    private void applyBayesShrinkDenoising(VectorWaveSwtAdapter.SwtResult swtResult, int lookback) {
        for (int level = 1; level <= swtResult.getLevels(); level++) {
            double[] detail = swtResult.getDetail(level);
            if (detail == null || detail.length == 0) continue;
            
            int m = Math.min(lookback, detail.length);
            int start = detail.length - m;
            double[] tail = new double[m];
            System.arraycopy(detail, start, tail, 0, m);
            
            double threshold = Thresholds.calculateThreshold(
                tail, Thresholds.ThresholdMethod.BAYES, level);
            
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
    
    private double getDefaultScaleFactorForMethod(ScalingMethod scaleMethod) {
        switch (scaleMethod) {
            case LINEAR:
                return DEFAULT_LINEAR_FACTOR;
            case SQUARE_ROOT:
                return DEFAULT_SQRT_FACTOR;
            case LOGARITHMIC:
                return DEFAULT_LOG_FACTOR;
            case ADAPTIVE:
                return DEFAULT_SQRT_FACTOR;
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