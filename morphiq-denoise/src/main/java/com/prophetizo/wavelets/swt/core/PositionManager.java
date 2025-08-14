package com.prophetizo.wavelets.swt.core;

import com.motivewave.platform.sdk.common.Enums;
import com.motivewave.platform.sdk.common.Instrument;
import com.motivewave.platform.sdk.order_mgmt.Order;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;
import com.prophetizo.LoggerConfig;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Centralized position management component that can be reused across multiple trading strategies.
 * 
 * <p>This class handles:
 * <ul>
 *   <li>Position state tracking</li>
 *   <li>Order creation and submission</li>
 *   <li>Position sizing via PositionSizer integration</li>
 *   <li>Entry and exit logic coordination</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>
 * PositionManager manager = new PositionManager(orderContext, positionSizer);
 * PositionInfo entry = manager.enterLong(currentPrice, stopPrice, targetPrice, quantity);
 * if (manager.hasPosition()) {
 *     manager.exitPosition();
 * }
 * </pre>
 * 
 * @author Prophetizo
 * @since 1.0.0
 */
public class PositionManager {
    private static final Logger logger = LoggerConfig.getLogger(PositionManager.class);
    
    private final OrderContext orderContext;
    private final PositionSizer positionSizer;
    private final PositionTracker positionTracker;
    
    /**
     * Creates a position manager with the specified order context and position sizer.
     * 
     * @param orderContext the MotiveWave order context for order operations
     * @param positionSizer the position sizer for risk calculations
     */
    public PositionManager(OrderContext orderContext, PositionSizer positionSizer) {
        if (orderContext == null) {
            throw new IllegalArgumentException("OrderContext cannot be null");
        }
        if (positionSizer == null) {
            throw new IllegalArgumentException("PositionSizer cannot be null");
        }
        
        this.orderContext = orderContext;
        this.positionSizer = positionSizer;
        this.positionTracker = new PositionTracker();
        
        if (logger.isDebugEnabled()) {
            logger.debug("PositionManager initialized for instrument: {}", 
                        orderContext.getInstrument().getSymbol());
        }
    }
    
    /**
     * Enters a long position with the specified parameters.
     * 
     * @param entryPrice the expected entry price
     * @param stopPrice the stop loss price
     * @param targetPrice the take profit price
     * @param quantity the position quantity
     * @return position information if successful, null if failed
     */
    public PositionInfo enterLong(double entryPrice, double stopPrice, double targetPrice, int quantity) {
        if (hasPosition()) {
            logger.warn("Cannot enter long position - already have position: {}", 
                       getCurrentPositionSide());
            return null;
        }
        
        return executeEntry(true, entryPrice, stopPrice, targetPrice, quantity);
    }
    
    /**
     * Enters a short position with the specified parameters.
     * 
     * @param entryPrice the expected entry price
     * @param stopPrice the stop loss price
     * @param targetPrice the take profit price
     * @param quantity the position quantity
     * @return position information if successful, null if failed
     */
    public PositionInfo enterShort(double entryPrice, double stopPrice, double targetPrice, int quantity) {
        if (hasPosition()) {
            logger.warn("Cannot enter short position - already have position: {}", 
                       getCurrentPositionSide());
            return null;
        }
        
        return executeEntry(false, entryPrice, stopPrice, targetPrice, quantity);
    }
    
    /**
     * Reverses the current position from long to short or vice versa.
     * 
     * @param entryPrice the expected entry price for new position
     * @param stopPrice the stop loss price for new position
     * @param targetPrice the take profit price for new position
     * @param quantity the new position quantity
     * @return position information if successful, null if failed
     */
    public PositionInfo reversePosition(double entryPrice, double stopPrice, double targetPrice, int quantity) {
        if (!hasPosition()) {
            logger.warn("Cannot reverse position - no current position");
            return null;
        }
        
        boolean wasLong = isLong();
        
        // Exit current position
        exitPosition();
        
        // Enter opposite position
        return executeEntry(!wasLong, entryPrice, stopPrice, targetPrice, quantity);
    }
    
    /**
     * Exits the current position at market price.
     * 
     * @return true if exit was successful, false otherwise
     */
    public boolean exitPosition() {
        if (!hasPosition()) {
            logger.info("No position to exit - already flat");
            return true;
        }
        
        try {
            String exitReason = "Manual Exit";
            double exitPrice = getCurrentPrice();
            
            // Calculate P&L if we have position tracking
            calculateAndLogPnL(exitPrice, exitReason);
            
            // Close all positions
            orderContext.closeAtMarket();
            
            logger.info("Exit order placed - Closing {} position at market", 
                       getCurrentPositionSide());
            
            // Reset position tracking
            positionTracker.reset();
            
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to exit position: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Checks if there is currently a position.
     * 
     * @return true if there is a position, false if flat
     */
    public boolean hasPosition() {
        return orderContext.getPosition() != 0;
    }
    
    /**
     * Checks if the current position is long.
     * 
     * @return true if long, false if short or flat
     */
    public boolean isLong() {
        return orderContext.getPosition() > 0;
    }
    
    /**
     * Checks if the current position is short.
     * 
     * @return true if short, false if long or flat
     */
    public boolean isShort() {
        return orderContext.getPosition() < 0;
    }
    
    /**
     * Gets the current position size.
     * 
     * @return positive for long, negative for short, 0 for flat
     */
    public int getCurrentPosition() {
        return orderContext.getPosition();
    }
    
    /**
     * Gets the current position side as a string.
     * 
     * @return "LONG", "SHORT", or "FLAT"
     */
    public String getCurrentPositionSide() {
        int position = getCurrentPosition();
        if (position > 0) return "LONG";
        if (position < 0) return "SHORT";
        return "FLAT";
    }
    
    /**
     * Gets the position tracker for access to position state.
     * 
     * @return the position tracker
     */
    public PositionTracker getPositionTracker() {
        return positionTracker;
    }
    
    /**
     * Gets the position sizer for risk calculations.
     * 
     * @return the position sizer
     */
    public PositionSizer getPositionSizer() {
        return positionSizer;
    }
    
    /**
     * Handles order fill notifications to update position tracking.
     * 
     * @param order the filled order
     */
    public void onOrderFilled(Order order) {
        if (order == null) {
            logger.error("onOrderFilled called with null order");
            return;
        }
        
        double fillPrice = order.getAvgFillPrice();
        boolean isBuy = order.isBuy();
        int quantity = order.getQuantity();
        
        // Validate fill data
        if (fillPrice <= 0 || Double.isNaN(fillPrice) || Double.isInfinite(fillPrice)) {
            logger.error("Invalid fill price received: {}", fillPrice);
            return;
        }
        
        if (quantity <= 0) {
            logger.error("Invalid order quantity: {}", quantity);
            return;
        }
        
        logger.info("Order filled: {} {} @ {}", 
                   quantity, isBuy ? "BUY" : "SELL", 
                   String.format("%.2f", fillPrice));
        
        // Update position tracking
        int currentPos = getCurrentPosition();
        if (currentPos != 0) {
            // Check if this is an exit or entry
            if (isStopHit(fillPrice)) {
                logger.warn("Stop hit detected at price: {}", String.format("%.2f", fillPrice));
            }
            
            positionTracker.updateEntryPrice(fillPrice);
        } else {
            // Position was closed
            positionTracker.reset();
        }
    }
    
    // Private helper methods
    
    private PositionInfo executeEntry(boolean isLong, double entryPrice, double stopPrice, 
                                    double targetPrice, int quantity) {
        try {
            String marketOrderId = UUID.randomUUID().toString();
            String stopOrderId = UUID.randomUUID().toString();
            String targetOrderId = UUID.randomUUID().toString();
            
            Instrument instrument = orderContext.getInstrument();
            
            // Create market order
            Order marketOrder = orderContext.createMarketOrder(
                instrument,
                marketOrderId,
                isLong ? Enums.OrderAction.BUY : Enums.OrderAction.SELL,
                quantity
            );
            
            // Create stop loss order (opposite direction)
            Order stopLoss = orderContext.createStopOrder(
                instrument,
                stopOrderId,
                isLong ? Enums.OrderAction.SELL : Enums.OrderAction.BUY,
                Enums.TIF.DAY,
                quantity,
                (float) stopPrice
            );
            
            // Create take profit order (opposite direction)
            Order takeProfit = orderContext.createLimitOrder(
                instrument,
                targetOrderId,
                isLong ? Enums.OrderAction.SELL : Enums.OrderAction.BUY,
                Enums.TIF.DAY,
                quantity,
                (float) targetPrice
            );
            
            // Submit all orders as a bracket
            orderContext.submitOrders(marketOrder, stopLoss, takeProfit);
            
            logger.info("✅ {} MARKET ORDER PLACED:", isLong ? "LONG" : "SHORT");
            logger.info("  - Quantity: {} contracts at market price", quantity);
            logger.info("  - Expected Entry: {}", String.format("%.2f", entryPrice));
            logger.info("  - Planned Stop: {}", String.format("%.2f", stopPrice));
            logger.info("  - Planned Target: {}", String.format("%.2f", targetPrice));
            
            // Update position tracking
            positionTracker.updatePosition(entryPrice, stopPrice, targetPrice, isLong);
            
            return new PositionInfo(entryPrice, stopPrice, targetPrice, quantity, isLong);
            
        } catch (Exception e) {
            logger.error("Failed to place {} order: {}", isLong ? "long" : "short", e.getMessage(), e);
            return null;
        }
    }
    
    private void calculateAndLogPnL(double exitPrice, String exitReason) {
        double entryPrice = positionTracker.getEntryPrice();
        if (entryPrice <= 0 || exitPrice <= 0) {
            return;
        }
        
        boolean wasLong = isLong();
        int quantity = Math.abs(getCurrentPosition());
        double pnl = wasLong ? (exitPrice - entryPrice) : (entryPrice - exitPrice);
        double totalPnl = pnl * quantity;
        double pnlPercent = (pnl / entryPrice) * 100;
        
        logger.info("🔴 POSITION EXIT - {}", exitReason);
        logger.info("  Position: {} from {} to {}", 
                   wasLong ? "LONG" : "SHORT",
                   String.format("%.2f", entryPrice),
                   String.format("%.2f", exitPrice));
        logger.info("  P&L: {} points per unit, Total: {} ({}%)", 
                   String.format("%.2f", pnl),
                   String.format("%.2f", totalPnl),
                   String.format("%.2f", pnlPercent));
        
        if (totalPnl >= 0) {
            logger.info("  Result: ✅ PROFIT");
        } else {
            logger.info("  Result: ❌ LOSS");
        }
    }
    
    private boolean isStopHit(double fillPrice) {
        double stopPrice = positionTracker.getStopPrice();
        if (stopPrice <= 0) return false;
        
        double entryPrice = positionTracker.getEntryPrice();
        if (entryPrice <= 0) return false;
        
        boolean wasLong = isLong();
        if (wasLong && fillPrice <= stopPrice) {
            return Math.abs(fillPrice - stopPrice) < Math.abs(entryPrice - stopPrice) * 0.1;
        } else if (!wasLong && fillPrice >= stopPrice) {
            return Math.abs(fillPrice - stopPrice) < Math.abs(entryPrice - stopPrice) * 0.1;
        }
        
        return false;
    }
    
    private double getCurrentPrice() {
        try {
            int index = orderContext.getDataContext().getDataSeries().size() - 1;
            return orderContext.getDataContext().getDataSeries().getClose(index);
        } catch (Exception e) {
            logger.warn("Could not get current price: {}", e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Container for position information.
     */
    public static class PositionInfo {
        private final double entryPrice;
        private final double stopPrice;
        private final double targetPrice;
        private final int quantity;
        private final boolean isLong;
        
        public PositionInfo(double entryPrice, double stopPrice, double targetPrice, 
                           int quantity, boolean isLong) {
            this.entryPrice = entryPrice;
            this.stopPrice = stopPrice;
            this.targetPrice = targetPrice;
            this.quantity = quantity;
            this.isLong = isLong;
        }
        
        public double getEntryPrice() { return entryPrice; }
        public double getStopPrice() { return stopPrice; }
        public double getTargetPrice() { return targetPrice; }
        public int getQuantity() { return quantity; }
        public boolean isLong() { return isLong; }
        
        @Override
        public String toString() {
            return String.format("PositionInfo{%s, entry=%.2f, stop=%.2f, target=%.2f, qty=%d}",
                               isLong ? "LONG" : "SHORT", entryPrice, stopPrice, targetPrice, quantity);
        }
    }
}