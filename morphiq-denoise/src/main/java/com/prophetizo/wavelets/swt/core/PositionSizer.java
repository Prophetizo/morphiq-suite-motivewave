package com.prophetizo.wavelets.swt.core;

import com.motivewave.platform.sdk.common.Instrument;
import com.prophetizo.LoggerConfig;
import org.slf4j.Logger;

/**
 * Position sizing calculator for trading strategies.
 * 
 * <p>This class encapsulates position sizing logic including:
 * <ul>
 *   <li>Risk-based position sizing</li>
 *   <li>Maximum risk per trade constraints</li>
 *   <li>Trade lots multiplier support</li>
 *   <li>Point value calculations</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>
 * PositionSizer sizer = new PositionSizer(instrument);
 * PositionInfo info = sizer.calculatePosition(
 *     positionSizeFactor: 1,
 *     tradeLots: 2,
 *     maxRiskDollars: 500,
 *     stopDistancePoints: 10
 * );
 * ctx.buy(info.getFinalQuantity());
 * </pre>
 *
 */
public class PositionSizer {
    private static final Logger logger = LoggerConfig.getLogger(PositionSizer.class);
    
    private final Instrument instrument;
    private final double pointValue;
    
    /**
     * Creates a position sizer for the given instrument.
     * 
     * @param instrument the trading instrument
     */
    public PositionSizer(Instrument instrument) {
        if (instrument == null) {
            throw new IllegalArgumentException("Instrument cannot be null");
        }
        this.instrument = instrument;
        this.pointValue = instrument.getPointValue();
        
        if (logger.isDebugEnabled()) {
            logger.debug("PositionSizer initialized for {} with point value: {}", 
                        instrument.getSymbol(), pointValue);
        }
    }
    
    /**
     * Calculates position size based on risk parameters.
     * 
     * @param positionSizeFactor base position size multiplier
     * @param tradeLots trade lots from platform settings
     * @param maxRiskDollars maximum dollar risk per trade
     * @param stopDistancePoints stop loss distance in points
     * @return position information including final quantity and risk metrics
     */
    public PositionInfo calculatePosition(int positionSizeFactor, int tradeLots, 
                                         double maxRiskDollars, double stopDistancePoints) {
        // Validate inputs
        positionSizeFactor = Math.max(1, positionSizeFactor);
        tradeLots = Math.max(1, tradeLots);
        maxRiskDollars = Math.max(0, maxRiskDollars);
        stopDistancePoints = Math.max(0, stopDistancePoints);
        
        // Calculate base quantity
        int baseQuantity = positionSizeFactor * tradeLots;
        
        // Calculate risk-adjusted quantity if risk management is enabled
        int finalQuantity = baseQuantity;
        double riskPerUnit = 0;
        double totalRisk = 0;
        
        if (maxRiskDollars > 0 && stopDistancePoints > 0) {
            // Calculate risk per unit
            riskPerUnit = stopDistancePoints * pointValue;
            
            // Calculate maximum quantity based on risk
            int maxQuantityByRisk = (int) (maxRiskDollars / riskPerUnit);
            
            // Apply risk constraint
            finalQuantity = Math.min(baseQuantity, maxQuantityByRisk);
            
            // Calculate actual total risk
            totalRisk = finalQuantity * riskPerUnit;
            
            if (logger.isDebugEnabled()) {
                logger.debug("Position sizing: base={}, maxByRisk={}, final={}, risk=${}", 
                            baseQuantity, maxQuantityByRisk, finalQuantity, 
                            String.format("%.2f", totalRisk));
            }
        }
        
        return new PositionInfo(
            finalQuantity,
            baseQuantity,
            riskPerUnit,
            totalRisk,
            stopDistancePoints,
            pointValue
        );
    }
    
    /**
     * Calculates position size with WATR-based stops.
     * 
     * @param positionSizeFactor base position size multiplier
     * @param tradeLots trade lots from platform settings
     * @param maxRiskDollars maximum dollar risk per trade
     * @param watr wavelet ATR value
     * @param stopMultiplier WATR multiplier for stop distance
     * @param minStopPoints minimum stop distance in points
     * @param maxStopPoints maximum stop distance in points
     * @return position information including final quantity and risk metrics
     */
    public PositionInfo calculatePositionWithWatr(int positionSizeFactor, int tradeLots,
                                                  double maxRiskDollars, double watr,
                                                  double stopMultiplier, double minStopPoints,
                                                  double maxStopPoints) {
        // Calculate stop distance from WATR
        double rawStopDistance = watr * stopMultiplier;
        double stopDistancePoints = Math.max(minStopPoints, Math.min(maxStopPoints, rawStopDistance));
        
        if (logger.isDebugEnabled()) {
            logger.debug("WATR stop calculation: watr={}, mult={}, raw={}, final={}", 
                        String.format("%.4f", watr),
                        stopMultiplier,
                        String.format("%.2f", rawStopDistance),
                        String.format("%.2f", stopDistancePoints));
        }
        
        return calculatePosition(positionSizeFactor, tradeLots, maxRiskDollars, stopDistancePoints);
    }
    
    /**
     * Container for position sizing results.
     */
    public static class PositionInfo {
        private final int finalQuantity;
        private final int baseQuantity;
        private final double riskPerUnit;
        private final double totalRisk;
        private final double stopDistancePoints;
        private final double pointValue;
        
        public PositionInfo(int finalQuantity, int baseQuantity, double riskPerUnit,
                           double totalRisk, double stopDistancePoints, double pointValue) {
            this.finalQuantity = finalQuantity;
            this.baseQuantity = baseQuantity;
            this.riskPerUnit = riskPerUnit;
            this.totalRisk = totalRisk;
            this.stopDistancePoints = stopDistancePoints;
            this.pointValue = pointValue;
        }
        
        public int getFinalQuantity() { return finalQuantity; }
        public int getBaseQuantity() { return baseQuantity; }
        public double getRiskPerUnit() { return riskPerUnit; }
        public double getTotalRisk() { return totalRisk; }
        public double getStopDistancePoints() { return stopDistancePoints; }
        public double getPointValue() { return pointValue; }
        
        public boolean wasRiskAdjusted() {
            return finalQuantity < baseQuantity;
        }
        
        public double getTargetDistance(double targetMultiplier) {
            return stopDistancePoints * targetMultiplier;
        }
        
        public double getPotentialReward(double targetMultiplier) {
            return getTargetDistance(targetMultiplier) * pointValue * finalQuantity;
        }
        
        public double getRiskRewardRatio(double targetMultiplier) {
            if (totalRisk == 0) return 0;
            return getPotentialReward(targetMultiplier) / totalRisk;
        }
        
        @Override
        public String toString() {
            return String.format("PositionInfo{qty=%d, risk=$%.2f, stop=%.2f pts, RR=%.2f}",
                    finalQuantity, totalRisk, stopDistancePoints, getRiskRewardRatio(3.0));
        }
    }
    
    /**
     * Get the instrument for this sizer.
     */
    public Instrument getInstrument() {
        return instrument;
    }
    
    /**
     * Get the point value for this instrument.
     */
    public double getPointValue() {
        return pointValue;
    }
    
    /**
     * Validate that the instrument supports the required features.
     */
    public boolean validateInstrument() {
        if (pointValue <= 0) {
            logger.warn("Invalid point value {} for instrument {}", 
                       pointValue, instrument.getSymbol());
            return false;
        }
        
        // Could add more validations here (e.g., check if futures, forex, etc.)
        return true;
    }
    
    /**
     * Create a position sizer with validation.
     * 
     * @param instrument the trading instrument
     * @return position sizer if valid, null otherwise
     */
    public static PositionSizer createValidated(Instrument instrument) {
        if (instrument == null) {
            logger.error("Cannot create PositionSizer with null instrument");
            return null;
        }
        
        PositionSizer sizer = new PositionSizer(instrument);
        if (!sizer.validateInstrument()) {
            logger.error("Instrument {} failed validation", instrument.getSymbol());
            return null;
        }
        
        return sizer;
    }
}