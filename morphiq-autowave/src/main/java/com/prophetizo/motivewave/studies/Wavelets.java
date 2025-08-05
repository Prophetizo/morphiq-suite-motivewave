package com.prophetizo.motivewave.studies;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;
import com.motivewave.platform.sdk.study.RuntimeDescriptor;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;

import ai.prophetizo.wavelet.api.BoundaryMode;
import ai.prophetizo.wavelet.api.DiscreteWavelet;
import ai.prophetizo.wavelet.api.Wavelet;
import ai.prophetizo.wavelet.api.WaveletRegistry;
import ai.prophetizo.wavelet.modwt.MODWTTransformFactory;
import ai.prophetizo.wavelet.modwt.MultiLevelMODWTResult;
import ai.prophetizo.wavelet.modwt.MultiLevelMODWTTransform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final Logger logger = LoggerFactory.getLogger(Wavelets.class);
    
    // Settings keys
    final static String WAVELET_TYPE = "WAVELET_TYPE";
    final static String DECOMPOSITION_LEVELS = "DECOMPOSITION_LEVELS";
    final static String LOOKBACK_PERIOD = "LOOKBACK_PERIOD";
    private static final int MAX_DECOMPOSITION_LEVELS = 7;
    
    // Path keys array for dynamic access
    private static final String[] PATH_KEYS = new String[MAX_DECOMPOSITION_LEVELS];
    
    // Color scheme for decomposition levels
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
        for (int i = 0; i < MAX_DECOMPOSITION_LEVELS; i++) {
            PATH_KEYS[i] = "D" + (i + 1) + "_PATH";
        }
    }

    // VectorWave components
    private MultiLevelMODWTTransform modwtTransform;
    private DiscreteWavelet currentWavelet;
    private String currentWaveletName;
    
    // Track settings to detect changes
    private String lastWaveletType = null;
    
    // Performance-optimized wavelet cache
    private final Map<String, DiscreteWavelet> waveletCache = new ConcurrentHashMap<>();

    @Override
    public void initialize(Defaults defaults) {
        logger.debug("Initializing Wavelets Study with VectorWave");

        SettingsDescriptor settings = new SettingsDescriptor();
        setSettingsDescriptor(settings);

        // General settings tab
        SettingTab generalTab = settings.addTab("General");
        SettingGroup waveletGroup = generalTab.addGroup("Wavelet Configuration");
        
        // Create wavelet options from registry
        List<NVP> waveletOptions = createWaveletOptions();
        
        // Create discrete descriptor with wavelet options
        DiscreteDescriptor waveletDescriptor = new DiscreteDescriptor(
            WAVELET_TYPE, "Wavelet Type", "db4", waveletOptions);
        
        waveletGroup.addRow(waveletDescriptor);
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

        // Initialize with default wavelet
        initializeDefaultWavelet();
    }

    private List<NVP> createWaveletOptions() {
        List<NVP> options = new ArrayList<>();
        
        try {
            // Get orthogonal wavelets suitable for MODWT
            List<String> orthogonalWavelets = WaveletRegistry.getOrthogonalWavelets();
            
            logger.info("Found {} orthogonal wavelets in registry", orthogonalWavelets.size());
            
            if (orthogonalWavelets.isEmpty()) {
                logger.warn("No orthogonal wavelets found in registry, using defaults");
                // Provide minimal defaults
                options.add(new NVP("Daubechies 4", "db4"));
                options.add(new NVP("Haar", "haar"));
                return options;
            }
            
            // Preferred order for financial analysis
            String[] preferredOrder = {"haar", "db2", "db4", "db6", "sym4", "sym8", "coif2", "coif3"};
            
            // Add preferred wavelets first
            for (String waveletName : preferredOrder) {
                if (orthogonalWavelets.contains(waveletName)) {
                    String displayName = getWaveletDisplayName(waveletName);
                    options.add(new NVP(displayName, waveletName));
                }
            }
            
            // Add remaining orthogonal wavelets
            for (String waveletName : orthogonalWavelets) {
                boolean alreadyAdded = false;
                for (String preferred : preferredOrder) {
                    if (waveletName.equals(preferred)) {
                        alreadyAdded = true;
                        break;
                    }
                }
                if (!alreadyAdded) {
                    String displayName = getWaveletDisplayName(waveletName);
                    options.add(new NVP(displayName, waveletName));
                }
            }
            
            logger.info("Created {} wavelet options from registry", options.size());
            
        } catch (Exception e) {
            logger.error("Error creating wavelet options", e);
            // Provide minimal defaults on error
            options.add(new NVP("Daubechies 4", "db4"));
            options.add(new NVP("Haar", "haar"));
        }
        
        return options;
    }

    private String getWaveletDisplayName(String waveletName) {
        // Use simple formatting for display names
        return switch (waveletName.toLowerCase()) {
            case "haar" -> "Haar";
            case "db2" -> "Daubechies 2";
            case "db4" -> "Daubechies 4";
            case "db6" -> "Daubechies 6";
            case "db8" -> "Daubechies 8";
            case "sym4" -> "Symlet 4";
            case "sym6" -> "Symlet 6";
            case "sym8" -> "Symlet 8";
            case "coif2" -> "Coiflet 2";
            case "coif3" -> "Coiflet 3";
            case "coif4" -> "Coiflet 4";
            default -> waveletName.toUpperCase();
        };
    }

    private void initializeDefaultWavelet() {
        // Use fallback strategy for initialization
        String[] candidates = {"db4", "haar", "db2", "sym4"};
        
        for (String candidate : candidates) {
            DiscreteWavelet wavelet = getOrCreateDiscreteWavelet(candidate);
            if (wavelet != null) {
                currentWavelet = wavelet;
                currentWaveletName = candidate;
                modwtTransform = MODWTTransformFactory.createMultiLevel(currentWavelet, BoundaryMode.PERIODIC);
                logger.info("Initialized with wavelet: {}", candidate);
                return;
            }
        }
        
        logger.error("Failed to initialize any wavelet - check VectorWave installation");
    }
    
    private DiscreteWavelet getOrCreateDiscreteWavelet(String waveletName) {
        if (waveletName == null || waveletName.trim().isEmpty()) {
            return null;
        }
        
        // Check cache first for performance
        DiscreteWavelet cached = waveletCache.get(waveletName);
        if (cached != null) {
            logger.debug("Using cached wavelet: {}", waveletName);
            return cached;
        }
        
        // Check registry availability
        if (!WaveletRegistry.isWaveletAvailable(waveletName)) {
            logger.warn("Wavelet {} not available in registry", waveletName);
            return null;
        }
        
        try {
            Wavelet wavelet = WaveletRegistry.getWavelet(waveletName);
            
            if (!(wavelet instanceof DiscreteWavelet)) {
                logger.warn("Wavelet {} is not a discrete wavelet", waveletName);
                return null;
            }
            
            DiscreteWavelet discrete = (DiscreteWavelet) wavelet;
            
            // Validate the wavelet has proper coefficients
            if (discrete.lowPassDecomposition() == null || 
                discrete.lowPassDecomposition().length == 0) {
                logger.warn("Wavelet {} has invalid coefficients", waveletName);
                return null;
            }
            
            // Cache for future use
            waveletCache.put(waveletName, discrete);
            
            // Log wavelet info
            logger.info("Loaded wavelet: {} (filter length: {})", 
                waveletName, discrete.lowPassDecomposition().length);
            
            return discrete;
            
        } catch (Exception e) {
            logger.error("Failed to create wavelet {}: {}", waveletName, e.getMessage());
            return null;
        }
    }

    private void checkAndUpdateSettings() {
        String waveletTypeStr = getSettings().getString(WAVELET_TYPE, "db4");
        
        // Check if settings have changed
        boolean settingsChanged = !waveletTypeStr.equals(lastWaveletType);

        if (settingsChanged || currentWavelet == null) {
            updateWaveletComponents(waveletTypeStr);
            lastWaveletType = waveletTypeStr;
        }
    }

    private void updateWaveletComponents(String waveletName) {
        // Validate wavelet is available
        if (!WaveletRegistry.isWaveletAvailable(waveletName)) {
            logger.warn("Requested wavelet {} not available, keeping current", waveletName);
            return;
        }
        
        DiscreteWavelet newWavelet = getOrCreateDiscreteWavelet(waveletName);
        
        if (newWavelet != null) {
            currentWavelet = newWavelet;
            currentWaveletName = waveletName;
            
            // Recreate transform with new wavelet
            modwtTransform = MODWTTransformFactory.createMultiLevel(currentWavelet, BoundaryMode.PERIODIC);
            logger.info("Updated to wavelet: {}", waveletName);
        } else {
            logger.error("Failed to update to wavelet: {}", waveletName);
        }
    }

    @Override
    public void onActivate(OrderContext ctx) {
        logger.debug("Activating Wavelets study");
        checkAndUpdateSettings();
    }

    @Override
    public void onLoad(Defaults defaults) {
        logger.debug("Wavelets onLoad called");
        
        // Log registry status
        int totalWavelets = WaveletRegistry.getAvailableWavelets().size();
        logger.info("WaveletRegistry has {} wavelets available", totalWavelets);
        
        // Ensure components are initialized
        if (modwtTransform == null) {
            initializeDefaultWavelet();
        }
        
        // Force settings check on next calculate
        lastWaveletType = null;
    }

    @Override
    public int getMinBars(DataContext ctx, BarSize bs) {
        return getSettings().getInteger(LOOKBACK_PERIOD, 512);
    }

    @Override
    protected void calculate(int index, DataContext ctx) {
        // Skip if not ready
        if (modwtTransform == null || currentWavelet == null) {
            logger.trace("Not ready, skipping calculation at index {}", index);
            return;
        }
        
        try {
            // Check if settings have changed
            checkAndUpdateSettings();

            // Get parameters
            int decompositionLevels = getSettings().getInteger(DECOMPOSITION_LEVELS, 5);
            int lookbackPeriod = getSettings().getInteger(LOOKBACK_PERIOD, 512);

            DataSeries dataSeries = ctx.getDataSeries();

            // Need enough data for analysis
            if (index < lookbackPeriod) {
                return;
            }

            // Collect price data
            double[] closingPrices = new double[lookbackPeriod];
            double lastValidPrice = dataSeries.getClose(index - lookbackPeriod + 1);

            for (int i = 0; i < lookbackPeriod; i++) {
                int barIndex = index - (lookbackPeriod - 1) + i;
                Double close = dataSeries.getDouble(barIndex, Enums.BarInput.CLOSE, ctx.getInstrument());

                if (close != null) {
                    closingPrices[i] = close;
                    lastValidPrice = close;
                } else {
                    closingPrices[i] = lastValidPrice;
                }
            }

            // Perform MODWT decomposition
            MultiLevelMODWTResult result = modwtTransform.decompose(closingPrices, decompositionLevels);
            
            // Check if result is valid
            if (result == null || !result.isValid()) {
                logger.error("MODWT decomposition failed or returned invalid result");
                return;
            }
            
            // Extract and plot detail coefficients for each level
            // NOTE: VectorWave uses 1-based indexing for levels
            for (int level = 0; level < decompositionLevels && level < MAX_DECOMPOSITION_LEVELS; level++) {
                double[] coeffs = result.getDetailCoeffsAtLevel(level + 1); // VectorWave uses 1-based indexing
                
                if (coeffs != null && coeffs.length > 0) {
                    // Get the latest coefficient
                    double signalValue = coeffs[coeffs.length - 1];
                    
                    Paths valueKey = Paths.values()[level];
                    dataSeries.setDouble(index, valueKey, signalValue);
                } else {
                    Paths valueKey = Paths.values()[level];
                    dataSeries.setDouble(index, valueKey, Double.NaN);
                }
            }

            // Clear any unused higher levels
            for (int level = decompositionLevels; level < MAX_DECOMPOSITION_LEVELS; level++) {
                Paths valueKey = Paths.values()[level];
                dataSeries.setDouble(index, valueKey, Double.NaN);
            }

            logger.trace("Calculated wavelets at index {} with {} levels using {}", 
                        index, decompositionLevels, currentWaveletName);

        } catch (Exception e) {
            logger.error("Error during wavelet calculation at index {}", index, e);
            
            // Clear all levels on error
            for (int level = 0; level < MAX_DECOMPOSITION_LEVELS; level++) {
                Paths valueKey = Paths.values()[level];
                ctx.getDataSeries().setDouble(index, valueKey, Double.NaN);
            }
        }
    }

    enum Paths {D1, D2, D3, D4, D5, D6, D7}
}