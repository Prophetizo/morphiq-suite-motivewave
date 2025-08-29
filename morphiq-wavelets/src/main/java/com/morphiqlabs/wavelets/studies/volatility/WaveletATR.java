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
import java.util.List;

/**
 * Wavelet ATR Study - Multi-scale volatility measurement indicator
 * 
 * This study implements a wavelet-based approach to measuring market volatility
 * by analyzing the energy content in different frequency bands of the price signal.
 * Unlike traditional ATR which uses a simple moving average of true ranges,
 * Wavelet ATR decomposes price into multiple scales and measures volatility
 * at each scale separately, then combines them for a more nuanced view.
 * 
 * Key Features:
 * - Multi-scale volatility decomposition using wavelets
 * - Optional denoising for cleaner volatility measurements
 * - Configurable detail levels for different timeframe analysis
 * - RMS energy-based volatility calculation
 * - Percentage display option for relative volatility
 * 
 * @author Morphiq Labs
 */
@StudyHeader(
    namespace = "com.morphiqlabs",
    id = "WAVELET_ATR",
    name = "Wavelet ATR",
    desc = "Multi-scale volatility measurement using wavelet decomposition",
    menu = "MorphIQ Labs",
    overlay = false,
    requiresBarUpdates = true,
    helpLink = "https://docs.morphiqlabs.com/wavelet-atr"
)
public class WaveletATR extends Study {
    private static final Logger logger = LoggerFactory.getLogger(WaveletATR.class);
    
    public enum Values {
        WATR
    }

    private static final String WAVELET_TYPE = "waveletType";
    private static final String DECOMPOSITION_LEVELS = "decompositionLevels";
    private static final String WINDOW_LENGTH = "windowLength";
    private static final String USE_DENOISED = "useDenoised";
    private static final String THRESH_LOOKBACK = "threshLookback";
    
    private static final String WATR_K = "watrK";
    private static final String WATR_LEVEL_DECAY = "watrLevelDecay";
    private static final String SMOOTHING_PERIOD = "smoothingPeriod";
    
    private static final String WATR_PATH = "watrPath";
    
    private static final int DEFAULT_LEVELS = 4;
    private static final int DEFAULT_WINDOW = 256;
    private static final int MIN_WINDOW = 64;
    private static final int MAX_WINDOW = 2048;
    private static final boolean DEFAULT_USE_DENOISED = false;
    private static final int DEFAULT_THRESH_LOOKBACK = 128;
    private static final int MIN_THRESH_LOOKBACK = 32;
    private static final int MAX_THRESH_LOOKBACK = 512;
    
    private static final int DEFAULT_WATR_K = 2;
    private static final double DEFAULT_WATR_LEVEL_DECAY = 0.5;
    private static final int DEFAULT_SMOOTHING = 14;
    
    private VectorWaveSwtAdapter swtAdapter;
    private WaveletAtr waveletAtr;
    private String lastWaveletType = null;
    
    @Override
    public void initialize(Defaults defaults) {
        logger.debug("Initializing Wavelet ATR Study");
        
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
                .setDescription("Number of detail levels to include in WATR calculation")
        );
        watrGroup.addRow(
            new DoubleDescriptor(WATR_LEVEL_DECAY, "Level Weight Decay",
                DEFAULT_WATR_LEVEL_DECAY, 0.1, 1.0, 0.05)
                .setDescription("Decay factor for weighting higher frequency levels")
        );
        watrGroup.addRow(
            new IntegerDescriptor(SMOOTHING_PERIOD, "Smoothing Period",
                DEFAULT_SMOOTHING, 1, 50, 1)
                .setDescription("EMA smoothing period for WATR output")
        );
        
        var displayTab = sd.addTab("Display");
        
        var pathsGroup = displayTab.addGroup("Lines");
        pathsGroup.addRow(
            new PathDescriptor(WATR_PATH, "WATR Line",
                defaults.getOrange(), 1.5f, null, true, false, true)
        );
        
        var indicatorsGroup = displayTab.addGroup("Indicators");
        indicatorsGroup.addRow(
            new IndicatorDescriptor(Inputs.IND, "WATR",
                defaults.getOrange(), Color.WHITE, false, true, true)
        );
        
        var desc = createRD();
        
        desc.setLabelSettings(WAVELET_TYPE, WATR_K);
        
        desc.exportValue(new ValueDescriptor(Values.WATR, "Wavelet ATR",
            new String[]{WAVELET_TYPE, WATR_K}));
        
        desc.declarePath(Values.WATR, WATR_PATH);
        desc.declareIndicator(Values.WATR, Inputs.IND);
        desc.setRangeKeys(Values.WATR);
        
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
        logger.info("=== onSettingsUpdated Called ===");
        
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
                series.setDouble(i, Values.WATR, null);
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
            
            calculateWatr(series, index, swtResult);
            
            series.setComplete(index);
            
        } catch (Exception e) {
            logger.error("calculate: Error at index {}", index, e);
            clearValues(series, index);
        }
    }
    
    private void calculateWatr(DataSeries series, int index, VectorWaveSwtAdapter.SwtResult swtResult) {
        int watrK = getSettings().getInteger(WATR_K, DEFAULT_WATR_K);
        
        double centerPrice = series.getClose(index);
        
        double rawWatr = waveletAtr.calculate(swtResult, watrK);
        
        series.setDouble(index, Values.WATR, rawWatr);
        
        if (index == series.size() - 1 || (logger.isDebugEnabled() && index % 50 == 0)) {
            logger.info("WATR[{}]: value={}, price={}",
                        index,
                        String.format("%.4f", rawWatr),
                        String.format("%.2f", centerPrice));
        }
    }
    
    private void clearValues(DataSeries series, int index) {
        series.setDouble(index, Values.WATR, null);
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