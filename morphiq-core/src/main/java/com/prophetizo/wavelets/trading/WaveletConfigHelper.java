package com.prophetizo.wavelets.trading;

// Note: MotiveWave imports removed - these should only be used in study modules
// Using generic Object parameters with type checking at runtime
import com.prophetizo.LoggerConfig;
import org.slf4j.Logger;

/**
 * Helper class for converting MotiveWave settings to trading wavelet configuration.
 * Bridges the gap between MotiveWave's string-based settings and VectorWave's rich configuration objects.
 */
public class WaveletConfigHelper {
    private static final Logger logger = LoggerConfig.getLogger(WaveletConfigHelper.class);
    
    // Common setting keys used across studies
    public static final String WAVELET_TYPE_KEY = "WAVELET_TYPE";
    public static final String DECOMPOSITION_LEVELS_KEY = "LEVELS";
    public static final String DENOISE_STRATEGY_KEY = "DENOISE_STRATEGY";
    public static final String TRADING_STYLE_KEY = "TRADING_STYLE";
    public static final String AUTO_ADAPT_KEY = "AUTO_ADAPT";
    
    // Default values
    private static final String DEFAULT_WAVELET = "DAUBECHIES4";
    private static final int DEFAULT_LEVELS = 5;
    private static final String DEFAULT_STRATEGY = "BALANCED";
    private static final String DEFAULT_TRADING_STYLE = "DAY_TRADING";
    
    /**
     * Create TradingConfig from MotiveWave settings and bar size.
     * Using Object parameters to avoid MotiveWave dependencies in core module.
     */
    public static TradingConfig fromMotiveWaveSettings(Object settings, Object barSize) {
        if (settings == null) {
            logger.warn("Null settings provided, using defaults");
            return createDefaultConfig(barSize);
        }
        
        try {
            // Use reflection to access Settings methods without importing MotiveWave SDK
            Class<?> settingsClass = settings.getClass();
            
            // Extract wavelet type
            String waveletTypeStr = (String) settingsClass.getMethod("getString", String.class, String.class)
                .invoke(settings, WAVELET_TYPE_KEY, DEFAULT_WAVELET);
            String vectorWaveType = mapWaveletType(waveletTypeStr);
            
            // Extract decomposition levels
            int levels = (Integer) settingsClass.getMethod("getInteger", String.class, int.class)
                .invoke(settings, DECOMPOSITION_LEVELS_KEY, DEFAULT_LEVELS);
            levels = validateLevels(levels);
            
            // Extract denoise strategy
            String strategyStr = (String) settingsClass.getMethod("getString", String.class, String.class)
                .invoke(settings, DENOISE_STRATEGY_KEY, DEFAULT_STRATEGY);
            DenoiseStrategy strategy = parseDenoiseStrategy(strategyStr);
            
            // Extract or infer trading style
            String tradingStyleStr;
            try {
                tradingStyleStr = (String) settingsClass.getMethod("getString", String.class, String.class)
                    .invoke(settings, TRADING_STYLE_KEY, null);
            } catch (Exception e) {
                tradingStyleStr = null;
            }
            TradingStyle tradingStyle = parseTradingStyle(tradingStyleStr, barSize);
            
            // Check for auto-adaptation
            boolean autoAdapt;
            try {
                autoAdapt = (Boolean) settingsClass.getMethod("getBoolean", String.class, boolean.class)
                    .invoke(settings, AUTO_ADAPT_KEY, true);
            } catch (Exception e) {
                autoAdapt = true;
            }
            
            // Create and return config
            TradingConfig config = new TradingConfig(vectorWaveType, levels, strategy, tradingStyle);
            
            if (autoAdapt) {
                config = adaptConfigForBarSize(config, barSize);
            }
            
            logger.debug("Created TradingConfig: wavelet={}, levels={}, strategy={}, style={}", 
                        vectorWaveType, levels, strategy, tradingStyle);
            
            return config;
            
        } catch (Exception e) {
            logger.error("Error creating config from settings, using defaults", e);
            return createDefaultConfig(barSize);
        }
    }
    
    /**
     * Create optimal config for specific trading style.
     */
    public static TradingConfig forTradingStyle(TradingStyle style, Object barSize) {
        String wavelet = style.getRecommendedWavelet();
        int levels = style.getOptimalLevels(getBarSizeMinutes(barSize));
        DenoiseStrategy strategy = style.getDefaultDenoiseStrategy();
        
        TradingConfig config = new TradingConfig(wavelet, levels, strategy, style);
        
        logger.debug("Created style-optimized config for {}: {}", style, config);
        return config;
    }
    
    /**
     * Create config optimized for specific timeframe.
     */
    public static TradingConfig forTimeframe(Object barSize) {
        long minutes = getBarSizeMinutes(barSize);
        TradingStyle style = inferTradingStyle(minutes);
        return forTradingStyle(style, barSize);
    }
    
    /**
     * Map MotiveWave wavelet type strings to VectorWave wavelet names.
     */
    public static String mapWaveletType(String motiveWaveType) {
        if (motiveWaveType == null) {
            return "db4";
        }
        
        return switch (motiveWaveType.toUpperCase()) {
            case "HAAR" -> "haar";
            case "DAUBECHIES2", "DB2" -> "db2";
            case "DAUBECHIES4", "DB4" -> "db4";
            case "DAUBECHIES6", "DB6" -> "db6";
            case "SYMLET4", "SYM4" -> "sym4";
            case "SYMLET8", "SYM8" -> "sym8";
            case "COIFLET3", "COIF3" -> "coif3";
            default -> {
                logger.warn("Unknown wavelet type '{}', using db4", motiveWaveType);
                yield "db4";
            }
        };
    }
    
    /**
     * Parse denoise strategy from string.
     */
    public static DenoiseStrategy parseDenoiseStrategy(String strategyStr) {
        if (strategyStr == null) {
            return DenoiseStrategy.BALANCED;
        }
        
        try {
            return DenoiseStrategy.valueOf(strategyStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown denoise strategy '{}', using BALANCED", strategyStr);
            return DenoiseStrategy.BALANCED;
        }
    }
    
    /**
     * Parse trading style from string, with fallback to bar size inference.
     */
    public static TradingStyle parseTradingStyle(String styleStr, Object barSize) {
        if (styleStr != null) {
            try {
                return TradingStyle.valueOf(styleStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.debug("Unknown trading style '{}', inferring from bar size", styleStr);
            }
        }
        
        // Infer from bar size
        return inferTradingStyle(getBarSizeMinutes(barSize));
    }
    
    /**
     * Infer trading style from timeframe.
     */
    public static TradingStyle inferTradingStyle(long barSizeMinutes) {
        if (barSizeMinutes <= 5) {
            return TradingStyle.SCALPING;
        } else if (barSizeMinutes <= 240) { // Up to 4 hours
            return TradingStyle.DAY_TRADING;
        } else if (barSizeMinutes <= 1440) { // Up to daily
            return TradingStyle.SWING_TRADING;
        } else {
            return TradingStyle.POSITION_TRADING;
        }
    }
    
    /**
     * Get optimal decomposition levels for given data length and bar size.
     */
    public static int getOptimalLevels(int dataLength, Object barSize, TradingStyle style) {
        // Start with style recommendation
        int styleLevels = style.getOptimalLevels(getBarSizeMinutes(barSize));
        
        // Adjust based on data length (can't have more levels than log2(length))
        int maxPossible = (int) Math.floor(Math.log(dataLength) / Math.log(2));
        
        // Practical limits for trading (too many levels can introduce lag)
        int maxPractical = 8;
        
        int optimal = Math.min(styleLevels, Math.min(maxPossible, maxPractical));
        
        // Ensure minimum of 1
        return Math.max(1, optimal);
    }
    
    /**
     * Adapt configuration for specific bar size characteristics.
     */
    public static TradingConfig adaptConfigForBarSize(TradingConfig baseConfig, Object barSize) {
        long minutes = getBarSizeMinutes(barSize);
        
        // Adjust levels based on timeframe
        int adaptedLevels = baseConfig.getLevels();
        if (minutes <= 1) {
            adaptedLevels = Math.max(1, adaptedLevels - 1); // Fewer levels for very short timeframes
        } else if (minutes >= 1440) {
            adaptedLevels = Math.min(8, adaptedLevels + 1); // More levels for long timeframes
        }
        
        // Adjust strategy based on timeframe characteristics
        DenoiseStrategy adaptedStrategy = baseConfig.getStrategy();
        if (minutes <= 5) {
            // Very short timeframes need conservative denoising to preserve signals
            adaptedStrategy = DenoiseStrategy.CONSERVATIVE;
        } else if (minutes >= 1440) {
            // Long timeframes can benefit from aggressive denoising
            adaptedStrategy = DenoiseStrategy.AGGRESSIVE;
        }
        
        if (adaptedLevels != baseConfig.getLevels() || adaptedStrategy != baseConfig.getStrategy()) {
            logger.debug("Adapted config for {} minute bars: levels {} -> {}, strategy {} -> {}", 
                        minutes, baseConfig.getLevels(), adaptedLevels, baseConfig.getStrategy(), adaptedStrategy);
            
            return new TradingConfig(baseConfig.getWaveletType(), adaptedLevels, adaptedStrategy, baseConfig.getTradingStyle());
        }
        
        return baseConfig;
    }
    
    /**
     * Convert bar size to minutes for easier processing.
     */
    public static long getBarSizeMinutes(Object barSize) {
        if (barSize == null) {
            return 60; // Default to 1 hour
        }
        
        try {
            // Use reflection to call MotiveWave BarSize methods without importing them
            // This allows the core module to remain independent of MotiveWave SDK
            java.lang.reflect.Method getSizeMillisMethod = barSize.getClass().getMethod("getSizeMillis");
            long sizeMillis = (Long) getSizeMillisMethod.invoke(barSize);
            return sizeMillis / (1000 * 60); // Convert milliseconds to minutes
        } catch (Exception e) {
            logger.warn("Error converting bar size to minutes using reflection, using default", e);
            return 60;
        }
    }
    
    /**
     * Validate decomposition levels.
     */
    private static int validateLevels(int levels) {
        if (levels < 1) {
            logger.warn("Invalid levels {}, using minimum 1", levels);
            return 1;
        } else if (levels > 10) {
            logger.warn("Excessive levels {}, capping at 10", levels);
            return 10;
        }
        return levels;
    }
    
    /**
     * Create default configuration.
     */
    private static TradingConfig createDefaultConfig(Object barSize) {
        TradingStyle style = inferTradingStyle(getBarSizeMinutes(barSize));
        return forTradingStyle(style, barSize);
    }
    
    /**
     * Configuration object for trading wavelet analysis.
     */
    public static class TradingConfig {
        private final String waveletType;
        private final int levels;
        private final DenoiseStrategy strategy;
        private final TradingStyle tradingStyle;
        
        public TradingConfig(String waveletType, int levels, DenoiseStrategy strategy, TradingStyle tradingStyle) {
            this.waveletType = waveletType;
            this.levels = levels;
            this.strategy = strategy;
            this.tradingStyle = tradingStyle;
        }
        
        // Getters
        public String getWaveletType() { return waveletType; }
        public int getLevels() { return levels; }
        public DenoiseStrategy getStrategy() { return strategy; }
        public TradingStyle getTradingStyle() { return tradingStyle; }
        
        @Override
        public String toString() {
            return String.format("TradingConfig{wavelet='%s', levels=%d, strategy=%s, style=%s}", 
                               waveletType, levels, strategy, tradingStyle);
        }
    }
}