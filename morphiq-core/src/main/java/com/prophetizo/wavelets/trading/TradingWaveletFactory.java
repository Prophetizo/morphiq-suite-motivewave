package com.prophetizo.wavelets.trading;

import ai.prophetizo.wavelet.api.BoundaryMode;
import ai.prophetizo.wavelet.api.Wavelet;
import ai.prophetizo.wavelet.api.WaveletRegistry;
import ai.prophetizo.wavelet.denoising.WaveletDenoiser;
import com.prophetizo.LoggerConfig;
import org.slf4j.Logger;

/**
 * Factory for creating trading-specific wavelet analysis services.
 * Uses singleton pattern to ensure consistent configuration across studies.
 */
public class TradingWaveletFactory {
    private static final Logger logger = LoggerConfig.getLogger(TradingWaveletFactory.class);
    
    private static volatile TradingWaveletFactory instance;
    
    // Default configuration
    private static final BoundaryMode DEFAULT_BOUNDARY_MODE = BoundaryMode.SYMMETRIC;
    
    private TradingWaveletFactory() {
        logger.info("Initializing TradingWaveletFactory with VectorWave integration");
    }
    
    /**
     * Get singleton instance of the factory.
     * Thread-safe lazy initialization.
     */
    public static TradingWaveletFactory getInstance() {
        if (instance == null) {
            synchronized (TradingWaveletFactory.class) {
                if (instance == null) {
                    instance = new TradingWaveletFactory();
                }
            }
        }
        return instance;
    }
    
    /**
     * Create a trading wavelet analyzer for the specified wavelet type.
     * 
     * @param waveletType The wavelet type (e.g., "db4", "haar", "sym4")
     * @return TradingWaveletAnalyzer configured for trading analysis
     */
    public TradingWaveletAnalyzer createAnalyzer(String waveletType) {
        try {
            // Use the new VectorWave API to check availability first
            Wavelet wavelet = null;
            String actualWaveletName = null;
            
            try {
                java.util.Set<String> availableWavelets = WaveletRegistry.getAvailableWavelets();
                
                // Check if the requested wavelet is available directly
                if (WaveletRegistry.isWaveletAvailable(waveletType)) {
                    wavelet = WaveletRegistry.getWavelet(waveletType);
                    actualWaveletName = waveletType;
                    logger.debug("Found exact match for wavelet: {}", waveletType);
                } else {
                    // Try mapped name
                    String mappedType = WaveletConfigHelper.mapWaveletType(waveletType);
                    if (WaveletRegistry.isWaveletAvailable(mappedType)) {
                        wavelet = WaveletRegistry.getWavelet(mappedType);
                        actualWaveletName = mappedType;
                        logger.debug("Found mapped wavelet '{}' for requested '{}'", mappedType, waveletType);
                    } else {
                        // Find the best available fallback
                        String[] fallbackOrder = {"db4", "haar", "db2", "db6", "sym4"};
                        for (String fallback : fallbackOrder) {
                            if (availableWavelets.contains(fallback)) {
                                wavelet = WaveletRegistry.getWavelet(fallback);
                                actualWaveletName = fallback;
                                logger.warn("Wavelet '{}' not available, using fallback: {}", waveletType, fallback);
                                break;
                            }
                        }
                    }
                }
                
                if (wavelet == null) {
                    // Use the first available wavelet as last resort
                    if (!availableWavelets.isEmpty()) {
                        actualWaveletName = availableWavelets.iterator().next();
                        wavelet = WaveletRegistry.getWavelet(actualWaveletName);
                        logger.warn("Using first available wavelet '{}' as last resort for '{}'", 
                                  actualWaveletName, waveletType);
                    }
                }
                
            } catch (Exception e) {
                logger.warn("Could not use VectorWave discovery API, falling back to old method", e);
                // Fallback to old trial-and-error method
                wavelet = tryCreateWavelet(waveletType);
                if (wavelet == null) {
                    String mappedType = WaveletConfigHelper.mapWaveletType(waveletType);
                    wavelet = tryCreateWavelet(mappedType);
                    actualWaveletName = mappedType;
                }
                if (wavelet == null) {
                    wavelet = tryCreateWavelet("db4");
                    actualWaveletName = "db4";
                }
            }
            
            if (wavelet == null) {
                throw new IllegalStateException("Cannot create any wavelet, VectorWave may not be properly initialized");
            }
            
            logger.debug("Creating TradingWaveletAnalyzer with wavelet: {}", actualWaveletName);
            return new TradingWaveletAnalyzer(wavelet, DEFAULT_BOUNDARY_MODE);
            
        } catch (Exception e) {
            logger.error("Failed to create analyzer for wavelet '{}'", waveletType, e);
            throw new IllegalStateException("Cannot create wavelet analyzer for: " + waveletType, e);
        }
    }
    
    /**
     * Try to create a wavelet with the given name, returning null on failure.
     */
    private Wavelet tryCreateWavelet(String name) {
        if (name == null) return null;
        try {
            return WaveletRegistry.getWavelet(name);
        } catch (Exception e) {
            logger.debug("Failed to create wavelet with name '{}': {}", name, e.getMessage());
            return null;
        }
    }
    
    /**
     * Create a trading denoiser optimized for financial data.
     * 
     * @param waveletType The wavelet type for denoising operations
     * @return TradingDenoiser configured for financial data
     */
    public TradingDenoiser createDenoiser(String waveletType) {
        try {
            // Use VectorWave's financial-optimized denoiser
            WaveletDenoiser vectorDenoiser = WaveletDenoiser.forFinancialData();
            
            logger.debug("Creating TradingDenoiser with financial optimization");
            return new TradingDenoiser(vectorDenoiser, waveletType);
            
        } catch (Exception e) {
            logger.error("Failed to create financial denoiser, creating standard denoiser", e);
            
            // Fallback to standard denoiser with specified wavelet
            // Use the same logic as createAnalyzer to find a working wavelet
            Wavelet wavelet = tryCreateWavelet(waveletType);
            if (wavelet == null) {
                String mappedType = WaveletConfigHelper.mapWaveletType(waveletType);
                wavelet = tryCreateWavelet(mappedType);
            }
            if (wavelet == null) {
                wavelet = tryCreateWavelet(waveletType.toUpperCase());
            }
            if (wavelet == null) {
                wavelet = tryCreateWavelet("db4");
            }
            if (wavelet == null) {
                wavelet = tryCreateWavelet("DB4");
            }
            if (wavelet == null) {
                wavelet = tryCreateWavelet("DAUBECHIES4");
            }
            if (wavelet == null) {
                throw new IllegalStateException("Cannot create any wavelet for denoiser");
            }
            WaveletDenoiser standardDenoiser = new WaveletDenoiser(wavelet, DEFAULT_BOUNDARY_MODE);
            return new TradingDenoiser(standardDenoiser, waveletType);
        }
    }
    
    /**
     * Create analyzer and denoiser pair for integrated analysis.
     * 
     * @param waveletType The wavelet type to use
     * @return TradingServicePair containing both analyzer and denoiser
     */
    public TradingServicePair createServicePair(String waveletType) {
        TradingWaveletAnalyzer analyzer = createAnalyzer(waveletType);
        TradingDenoiser denoiser = createDenoiser(waveletType);
        
        return new TradingServicePair(analyzer, denoiser);
    }
    
    /**
     * Container for analyzer and denoiser services.
     */
    public static class TradingServicePair {
        private final TradingWaveletAnalyzer analyzer;
        private final TradingDenoiser denoiser;
        
        public TradingServicePair(TradingWaveletAnalyzer analyzer, TradingDenoiser denoiser) {
            this.analyzer = analyzer;
            this.denoiser = denoiser;
        }
        
        public TradingWaveletAnalyzer getAnalyzer() { return analyzer; }
        public TradingDenoiser getDenoiser() { return denoiser; }
    }
    
    /**
     * Validate that VectorWave is properly configured.
     * 
     * @return true if VectorWave is ready for use
     */
    public boolean validateVectorWaveIntegration() {
        try {
            // First log available wavelets
            try {
                java.util.Set<String> available = WaveletRegistry.getAvailableWavelets();
                logger.info("Available wavelets from VectorWave: {}", available);
                
                // Log some wavelet info for common types
                for (String waveletName : java.util.Arrays.asList("haar", "db4", "sym4")) {
                    if (available.contains(waveletName)) {
                        try {
                            var info = WaveletRegistry.getWaveletInfo(waveletName);
                            logger.info("Wavelet '{}' - Family: {}, Order: {}", 
                                waveletName, info.getFamily(), info.getOrder());
                        } catch (Exception e) {
                            logger.debug("Could not get info for wavelet '{}'", waveletName);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Could not retrieve available wavelets list", e);
            }
            
            // Test basic wavelet creation - try different names
            String[] waveletNamesToTry = {"db4", "DB4", "daubechies4", "DAUBECHIES4", "Daubechies4", "daubechies-4"};
            Wavelet testWavelet = null;
            String workingName = null;
            
            for (String name : waveletNamesToTry) {
                try {
                    testWavelet = WaveletRegistry.getWavelet(name);
                    if (testWavelet != null) {
                        workingName = name;
                        logger.info("Successfully created wavelet with name: {}", name);
                        break;
                    }
                } catch (Exception e) {
                    logger.debug("Failed to create wavelet with name '{}': {}", name, e.getMessage());
                }
            }
            
            if (testWavelet == null) {
                logger.error("VectorWave validation failed: Cannot create any wavelet. Tried: {}", 
                    java.util.Arrays.toString(waveletNamesToTry));
                return false;
            }
            
            // Test financial denoiser creation
            WaveletDenoiser.forFinancialData();
            
            logger.info("VectorWave integration validated successfully");
            return true;
            
        } catch (Exception e) {
            logger.error("VectorWave validation failed", e);
            return false;
        }
    }
    
    /**
     * Get available wavelet types from VectorWave registry.
     * 
     * @return Set of available wavelet type names
     */
    public java.util.Set<String> getAvailableWavelets() {
        try {
            return WaveletRegistry.getAvailableWavelets();
        } catch (Exception e) {
            logger.error("Failed to get available wavelets", e);
            return java.util.Set.of("db4", "haar", "sym4"); // Fallback set
        }
    }
}