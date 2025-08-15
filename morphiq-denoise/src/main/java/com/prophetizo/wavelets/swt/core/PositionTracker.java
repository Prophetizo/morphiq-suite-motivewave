package com.prophetizo.wavelets.swt.core;

import com.prophetizo.LoggerConfig;
import org.slf4j.Logger;

/**
 * Tracks position state and provides position-related calculations.
 * 
 * <p>This class maintains:
 * <ul>
 *   <li>Entry price tracking</li>
 *   <li>Stop and target price tracking</li>
 *   <li>Position direction tracking</li>
 *   <li>Unrealized P&L calculations</li>
 * </ul>
 * 
 * <p>Thread-safe for use across multiple strategies.
 *
 */
public class PositionTracker {
    private static final Logger logger = LoggerConfig.getLogger(PositionTracker.class);
    
    private volatile double entryPrice = 0.0;
    private volatile double stopPrice = 0.0;
    private volatile double targetPrice = 0.0;
    private volatile boolean isLong = false;
    private volatile boolean hasPosition = false;
    
    /**
     * Updates the position tracking with new position information.
     * 
     * @param entryPrice the entry price
     * @param stopPrice the stop loss price
     * @param targetPrice the take profit price
     * @param isLong true if long position, false if short
     */
    public synchronized void updatePosition(double entryPrice, double stopPrice, 
                                          double targetPrice, boolean isLong) {
        this.entryPrice = entryPrice;
        this.stopPrice = stopPrice;
        this.targetPrice = targetPrice;
        this.isLong = isLong;
        this.hasPosition = true;
        
        if (logger.isDebugEnabled()) {
            logger.debug("Position updated: {} entry={}, stop={}, target={}", 
                        isLong ? "LONG" : "SHORT", 
                        String.format("%.2f", entryPrice),
                        String.format("%.2f", stopPrice),
                        String.format("%.2f", targetPrice));
        }
    }
    
    /**
     * Updates only the entry price (useful for average entry price updates).
     * 
     * @param entryPrice the new entry price
     */
    public synchronized void updateEntryPrice(double entryPrice) {
        if (hasPosition) {
            this.entryPrice = entryPrice;
            
            if (logger.isDebugEnabled()) {
                logger.debug("Entry price updated to: {}", String.format("%.2f", entryPrice));
            }
        }
    }
    
    /**
     * Updates only the stop price (useful for trailing stops).
     * 
     * @param stopPrice the new stop price
     */
    public synchronized void updateStopPrice(double stopPrice) {
        if (hasPosition) {
            this.stopPrice = stopPrice;
            
            if (logger.isDebugEnabled()) {
                logger.debug("Stop price updated to: {}", String.format("%.2f", stopPrice));
            }
        }
    }
    
    /**
     * Updates only the target price.
     * 
     * @param targetPrice the new target price
     */
    public synchronized void updateTargetPrice(double targetPrice) {
        if (hasPosition) {
            this.targetPrice = targetPrice;
            
            if (logger.isDebugEnabled()) {
                logger.debug("Target price updated to: {}", String.format("%.2f", targetPrice));
            }
        }
    }
    
    /**
     * Resets all position tracking data.
     */
    public synchronized void reset() {
        this.entryPrice = 0.0;
        this.stopPrice = 0.0;
        this.targetPrice = 0.0;
        this.isLong = false;
        this.hasPosition = false;
        
        if (logger.isDebugEnabled()) {
            logger.debug("Position tracking reset - now FLAT");
        }
    }
    
    /**
     * Gets the current entry price.
     * 
     * @return the entry price, or 0.0 if no position
     */
    public double getEntryPrice() {
        return entryPrice;
    }
    
    /**
     * Gets the current stop price.
     * 
     * @return the stop price, or 0.0 if no position
     */
    public double getStopPrice() {
        return stopPrice;
    }
    
    /**
     * Gets the current target price.
     * 
     * @return the target price, or 0.0 if no position
     */
    public double getTargetPrice() {
        return targetPrice;
    }
    
    /**
     * Checks if the tracked position is long.
     * 
     * @return true if long, false if short or no position
     */
    public boolean isLong() {
        return hasPosition && isLong;
    }
    
    /**
     * Checks if the tracked position is short.
     * 
     * @return true if short, false if long or no position
     */
    public boolean isShort() {
        return hasPosition && !isLong;
    }
    
    /**
     * Checks if there is a tracked position.
     * 
     * @return true if tracking a position, false if flat
     */
    public boolean hasPosition() {
        return hasPosition;
    }
    
    /**
     * Calculates unrealized P&L per unit based on current price.
     * 
     * @param currentPrice the current market price
     * @return unrealized P&L per unit, or 0.0 if no position
     */
    public double calculateUnrealizedPnLPerUnit(double currentPrice) {
        if (!hasPosition || entryPrice <= 0 || currentPrice <= 0) {
            return 0.0;
        }
        
        // Calculate P&L per unit based on position direction
        return isLong ? (currentPrice - entryPrice) : (entryPrice - currentPrice);
    }
    
    /**
     * Calculates total unrealized P&L based on current price and quantity.
     * 
     * <p>The quantity parameter should be:
     * <ul>
     *   <li>Positive for long positions</li>
     *   <li>Negative for short positions</li>
     *   <li>Or always positive (absolute value will be used)</li>
     * </ul>
     * 
     * @param currentPrice the current market price
     * @param quantity the position quantity (must not be zero)
     * @return total unrealized P&L (P&L per unit × |quantity|), or 0.0 if no position
     * @throws IllegalArgumentException if quantity is zero
     */
    public double calculateUnrealizedPnL(double currentPrice, int quantity) {
        if (quantity == 0) {
            throw new IllegalArgumentException("Quantity cannot be zero");
        }
        
        // Get P&L per unit
        double pnlPerUnit = calculateUnrealizedPnLPerUnit(currentPrice);
        
        // Always use absolute value of quantity for calculation
        // The P&L direction is determined by the position type (long/short)
        int absQuantity = Math.abs(quantity);
        
        // Return total P&L
        return pnlPerUnit * absQuantity;
    }
    
    /**
     * Calculates the distance to stop loss in points.
     * 
     * @param currentPrice the current market price
     * @return distance to stop in points, negative if stop already hit
     */
    public double getStopDistance(double currentPrice) {
        if (!hasPosition || stopPrice <= 0 || currentPrice <= 0) {
            return 0.0;
        }
        
        return isLong ? (currentPrice - stopPrice) : (stopPrice - currentPrice);
    }
    
    /**
     * Calculates the distance to target in points.
     * 
     * @param currentPrice the current market price
     * @return distance to target in points, negative if target already hit
     */
    public double getTargetDistance(double currentPrice) {
        if (!hasPosition || targetPrice <= 0 || currentPrice <= 0) {
            return 0.0;
        }
        
        return isLong ? (targetPrice - currentPrice) : (currentPrice - targetPrice);
    }
    
    /**
     * Gets the risk per unit (entry to stop distance).
     * 
     * @return risk per unit in points, or 0.0 if no position
     */
    public double getRiskPerUnit() {
        if (!hasPosition || entryPrice <= 0 || stopPrice <= 0) {
            return 0.0;
        }
        
        return Math.abs(entryPrice - stopPrice);
    }
    
    /**
     * Gets the reward per unit (entry to target distance).
     * 
     * @return reward per unit in points, or 0.0 if no position
     */
    public double getRewardPerUnit() {
        if (!hasPosition || entryPrice <= 0 || targetPrice <= 0) {
            return 0.0;
        }
        
        return Math.abs(targetPrice - entryPrice);
    }
    
    /**
     * Calculates the risk/reward ratio.
     * 
     * @return risk/reward ratio, or 0.0 if no position or no risk
     */
    public double getRiskRewardRatio() {
        double risk = getRiskPerUnit();
        double reward = getRewardPerUnit();
        
        if (risk <= 0) return 0.0;
        return reward / risk;
    }
    
    /**
     * Checks if the position is near the stop loss.
     * 
     * @param currentPrice the current market price
     * @param threshold the threshold as a percentage of total risk (0.0 to 1.0)
     * @return true if near stop, false otherwise
     */
    public boolean isNearStop(double currentPrice, double threshold) {
        if (!hasPosition || threshold <= 0) return false;
        
        double stopDistance = getStopDistance(currentPrice);
        double totalRisk = getRiskPerUnit();
        
        if (totalRisk <= 0) return false;
        
        return stopDistance <= (totalRisk * threshold);
    }
    
    /**
     * Checks if the position is near the target.
     * 
     * @param currentPrice the current market price
     * @param threshold the threshold as a percentage of total reward (0.0 to 1.0)
     * @return true if near target, false otherwise
     */
    public boolean isNearTarget(double currentPrice, double threshold) {
        if (!hasPosition || threshold <= 0) return false;
        
        double targetDistance = getTargetDistance(currentPrice);
        double totalReward = getRewardPerUnit();
        
        if (totalReward <= 0) return false;
        
        return targetDistance <= (totalReward * threshold);
    }
    
    @Override
    public String toString() {
        if (!hasPosition) {
            return "PositionTracker{FLAT}";
        }
        
        return String.format("PositionTracker{%s, entry=%.2f, stop=%.2f, target=%.2f, RR=%.2f}",
                           isLong ? "LONG" : "SHORT", entryPrice, stopPrice, targetPrice, getRiskRewardRatio());
    }
}