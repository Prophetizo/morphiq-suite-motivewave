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
            Wavelet wavelet = WaveletRegistry.getWavelet(waveletType);
            if (wavelet == null) {
                logger.warn("Wavelet type '{}' not found, falling back to db4", waveletType);
                wavelet = WaveletRegistry.getWavelet("db4");
            }
            
            logger.debug("Creating TradingWaveletAnalyzer with wavelet: {}", waveletType);
            return new TradingWaveletAnalyzer(wavelet, DEFAULT_BOUNDARY_MODE);
            
        } catch (Exception e) {
            logger.error("Failed to create analyzer for wavelet '{}', using db4 fallback", waveletType, e);
            Wavelet fallback = WaveletRegistry.getWavelet("db4");
            return new TradingWaveletAnalyzer(fallback, DEFAULT_BOUNDARY_MODE);
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
            Wavelet wavelet = WaveletRegistry.getWavelet(waveletType);
            if (wavelet == null) {
                wavelet = WaveletRegistry.getWavelet("db4");
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
            // Test basic wavelet creation
            Wavelet testWavelet = WaveletRegistry.getWavelet("db4");
            if (testWavelet == null) {
                logger.error("VectorWave validation failed: Cannot create db4 wavelet");
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