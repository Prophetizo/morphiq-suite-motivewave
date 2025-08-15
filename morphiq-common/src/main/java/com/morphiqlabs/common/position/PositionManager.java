package com.morphiqlabs.common.position;

import com.motivewave.platform.sdk.common.Enums;
import com.motivewave.platform.sdk.common.Instrument;
import com.motivewave.platform.sdk.order_mgmt.Order;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;
import com.morphiqlabs.common.LoggerConfig;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

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
 */
public class PositionManager {
    private static final Logger logger = LoggerConfig.getLogger(PositionManager.class);
    
    private final OrderContext orderContext;
    private final PositionSizer positionSizer;
    private final PositionTracker positionTracker;
    
    // Order bundle for managing multiple orders
    private final OrderBundle orderBundle = new OrderBundle();
    
    // Legacy order tracking for backward compatibility
    private final Map<String, Order> activeOrders = new ConcurrentHashMap<>();
    private final Map<OrderType, String> orderIdsByType = new ConcurrentHashMap<>(); // Type -> OrderId mapping
    
    /**
     * Enum representing the different types of orders managed by the position manager.
     */
    public enum OrderType {
        MARKET("market"),
        STOP("stop"),
        TARGET("target");
        
        private final String label;
        
        OrderType(String label) {
            this.label = label;
        }
        
        public String getLabel() {
            return label;
        }
    }
    
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
            
            // Clear tracked orders
            clearOrders();
            
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
        
        // Try to find and remove the filled order from tracking
        String filledOrderId = findOrderId(order);
        if (filledOrderId != null) {
            activeOrders.remove(filledOrderId);
            orderIdsByType.entrySet().removeIf(entry -> filledOrderId.equals(entry.getValue()));
            logger.debug("Removed filled order from tracking: {}", filledOrderId);
        }
        
        // Also remove from OrderBundle
        orderBundle.removeOrder(order);
        
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
            clearOrders(); // Clear all orders when position is closed
        }
    }
    
    /**
     * Finds the order ID for a given order object.
     * 
     * @param order the order to find
     * @return the order ID if found, null otherwise
     */
    private String findOrderId(Order order) {
        for (Map.Entry<String, Order> entry : activeOrders.entrySet()) {
            if (entry.getValue().equals(order)) {
                return entry.getKey();
            }
        }
        return null;
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
            
            // Store orders and their IDs before submission
            storeOrder(OrderType.MARKET, marketOrderId, marketOrder);
            storeOrder(OrderType.STOP, stopOrderId, stopLoss);
            storeOrder(OrderType.TARGET, targetOrderId, takeProfit);
            
            // Submit all orders as a bracket
            orderContext.submitOrders(marketOrder, stopLoss, takeProfit);
            
            logger.info("✅ {} MARKET ORDER PLACED:", isLong ? "LONG" : "SHORT");
            logger.info("  - Quantity: {} contracts at market price", quantity);
            logger.info("  - Expected Entry: {}", String.format("%.2f", entryPrice));
            logger.info("  - Planned Stop: {}", String.format("%.2f", stopPrice));
            logger.info("  - Planned Target: {}", String.format("%.2f", targetPrice));
            logger.debug("  - Order IDs: Market={}, Stop={}, Target={}", 
                        marketOrderId, stopOrderId, targetOrderId);
            
            // Update position tracking
            positionTracker.updatePosition(entryPrice, stopPrice, targetPrice, isLong);
            
            return new PositionInfo(entryPrice, stopPrice, targetPrice, quantity, isLong,
                                  marketOrderId, stopOrderId, targetOrderId);
            
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
    
    // Order management methods
    
    /**
     * Stores an order with its ID and type for tracking.
     */
    private void storeOrder(OrderType orderType, String orderId, Order order) {
        // Store in legacy maps for backward compatibility
        activeOrders.put(orderId, order);
        orderIdsByType.put(orderType, orderId);
        
        // Also store in OrderBundle
        switch (orderType) {
            case MARKET:
                orderBundle.addMarketOrder(orderId, order, orderType.getLabel());
                break;
            case STOP:
                orderBundle.addStopOrder(orderId, order, orderType.getLabel());
                break;
            case TARGET:
                orderBundle.addTargetOrder(orderId, order, orderType.getLabel());
                break;
        }
        
        logger.debug("Stored {} order with ID: {}", orderType, orderId);
    }
    
    /**
     * Clears all tracked orders (typically called after position exit).
     * Note: MotiveWave automatically cancels related orders when positions are closed.
     */
    private void clearOrders() {
        clearOrders(false);
    }
    
    /**
     * Clears all tracked orders with optional cancellation.
     * 
     * @param cancelFirst if true, cancels orders before clearing; if false, just clears tracking
     */
    private void clearOrders(boolean cancelFirst) {
        if (cancelFirst) {
            cancelAllOrders();
        } else {
            activeOrders.clear();
            orderIdsByType.clear();
            orderBundle.clear();
            logger.debug("Cleared all tracked orders");
        }
    }
    
    /**
     * Cancels all active orders being tracked.
     * Uses OrderContext.cancelOrders() to cancel orders through the platform.
     * 
     * @return the number of orders cancelled
     */
    public int cancelAllOrders() {
        int cancelledCount = 0;
        List<Order> ordersToCancel = new ArrayList<>();
        
        // Collect all active orders
        for (Map.Entry<String, Order> entry : activeOrders.entrySet()) {
            Order order = entry.getValue();
            
            // Check if order is still active
            if (order != null && order.isActive() && !order.isCancelled() && !order.isFilled()) {
                ordersToCancel.add(order);
                cancelledCount++;
            }
        }
        
        // Cancel all orders at once if there are any to cancel
        if (!ordersToCancel.isEmpty()) {
            try {
                // Use OrderContext to cancel all orders (synchronous call)
                orderContext.cancelOrders(ordersToCancel);
                logger.info("Cancelled {} active orders", cancelledCount);
                
                // Clear tracking after successful cancellation
                activeOrders.clear();
                orderIdsByType.clear();
                orderBundle.clear();
            } catch (Exception e) {
                logger.error("Failed to cancel orders: {}", e.getMessage(), e);
            }
        }
        
        return cancelledCount;
    }
    
    /**
     * Gets an order by its ID.
     * 
     * @param orderId the order ID
     * @return the order if found, null otherwise
     */
    public Order getOrderById(String orderId) {
        return activeOrders.get(orderId);
    }
    
    /**
     * Gets an order ID by its type.
     * 
     * @param orderType the order type
     * @return the order ID if found, null otherwise
     */
    public String getOrderIdByType(OrderType orderType) {
        return orderIdsByType.get(orderType);
    }
    
    /**
     * Gets the current market order ID.
     * 
     * @return the market order ID if found, null otherwise
     */
    public String getMarketOrderId() {
        return orderIdsByType.get(OrderType.MARKET);
    }
    
    /**
     * Gets the current stop order ID.
     * 
     * @return the stop order ID if found, null otherwise
     */
    public String getStopOrderId() {
        return orderIdsByType.get(OrderType.STOP);
    }
    
    /**
     * Gets the current target order ID.
     * 
     * @return the target order ID if found, null otherwise
     */
    public String getTargetOrderId() {
        return orderIdsByType.get(OrderType.TARGET);
    }
    
    /**
     * Gets all active order IDs.
     * 
     * @return unmodifiable map of order IDs to orders
     */
    public Map<String, Order> getActiveOrders() {
        return Collections.unmodifiableMap(activeOrders);
    }
    
    /**
     * Cancels an order by its ID.
     * 
     * @param orderId the order ID to cancel
     * @return true if cancel was requested, false if order not found
     */
    public boolean cancelOrderById(String orderId) {
        Order order = activeOrders.get(orderId);
        if (order != null) {
            try {
                // Check if order is still active before attempting to cancel
                if (order.isActive() && !order.isCancelled() && !order.isFilled()) {
                    // Use OrderContext to cancel the order (pass as varargs)
                    orderContext.cancelOrders(order);
                    logger.info("Cancelled order: {}", orderId);
                }
                
                // Remove from tracking regardless
                activeOrders.remove(orderId);
                orderIdsByType.entrySet().removeIf(entry -> orderId.equals(entry.getValue()));
                orderBundle.removeOrder(order);
                
                logger.debug("Removed order from tracking: {}", orderId);
                return true;
            } catch (Exception e) {
                logger.error("Failed to cancel order {}: {}", orderId, e.getMessage(), e);
            }
        }
        return false;
    }
    
    /**
     * Modifies a stop order's price.
     * 
     * @param newStopPrice the new stop price
     * @return true if modification was successful, false otherwise
     */
    public boolean modifyStopPrice(double newStopPrice) {
        String stopOrderId = orderIdsByType.get(OrderType.STOP);
        if (stopOrderId == null) {
            logger.warn("No stop order found to modify");
            return false;
        }
        
        Order stopOrder = activeOrders.get(stopOrderId);
        if (stopOrder != null) {
            try {
                // Check if order is still active and can be modified
                if (!stopOrder.isActive() || stopOrder.isCancelled() || stopOrder.isFilled()) {
                    logger.warn("Stop order {} is not active (cancelled={}, filled={})", 
                               stopOrderId, stopOrder.isCancelled(), stopOrder.isFilled());
                    return false;
                }
                
                // Use the Order's setAdjStopPrice method to modify the stop price
                stopOrder.setAdjStopPrice((float) newStopPrice);
                
                logger.info("Modified stop order {} to new price: {}", stopOrderId, newStopPrice);
                
                // Update position tracker
                positionTracker.updateStopPrice(newStopPrice);
                return true;
            } catch (Exception e) {
                logger.error("Failed to modify stop order: {}", e.getMessage(), e);
            }
        }
        return false;
    }
    
    /**
     * Modifies a target order's price.
     * 
     * @param newTargetPrice the new target price
     * @return true if modification was successful, false otherwise
     */
    public boolean modifyTargetPrice(double newTargetPrice) {
        String targetOrderId = orderIdsByType.get(OrderType.TARGET);
        if (targetOrderId == null) {
            logger.warn("No target order found to modify");
            return false;
        }
        
        Order targetOrder = activeOrders.get(targetOrderId);
        if (targetOrder != null) {
            try {
                // Check if order is still active and can be modified
                if (!targetOrder.isActive() || targetOrder.isCancelled() || targetOrder.isFilled()) {
                    logger.warn("Target order {} is not active (cancelled={}, filled={})", 
                               targetOrderId, targetOrder.isCancelled(), targetOrder.isFilled());
                    return false;
                }
                
                // Use the Order's setAdjLimitPrice method to modify the limit price
                targetOrder.setAdjLimitPrice((float) newTargetPrice);
                
                logger.info("Modified target order {} to new price: {}", targetOrderId, newTargetPrice);
                
                // Update position tracker
                positionTracker.updateTargetPrice(newTargetPrice);
                return true;
            } catch (Exception e) {
                logger.error("Failed to modify target order: {}", e.getMessage(), e);
            }
        }
        return false;
    }
    
    /**
     * Trails the stop order to a new price (only if it improves the position).
     * For long positions, only allows raising the stop.
     * For short positions, only allows lowering the stop.
     * 
     * @param newStopPrice the proposed new stop price
     * @return true if stop was modified, false otherwise
     */
    public boolean trailStop(double newStopPrice) {
        if (!hasPosition()) {
            return false;
        }
        
        double currentStop = positionTracker.getStopPrice();
        boolean shouldTrail = false;
        
        if (isLong()) {
            // For long position, only trail up (raise stop)
            shouldTrail = newStopPrice > currentStop;
        } else {
            // For short position, only trail down (lower stop)
            shouldTrail = newStopPrice < currentStop;
        }
        
        if (shouldTrail) {
            boolean modified = modifyStopPrice(newStopPrice);
            if (modified) {
                logger.info("Trailed stop from {} to {}", 
                           String.format("%.2f", currentStop), 
                           String.format("%.2f", newStopPrice));
            }
            return modified;
        }
        
        return false;
    }
    
    /**
     * Modifies both stop and target prices for a position.
     * 
     * @param newStopPrice the new stop price
     * @param newTargetPrice the new target price
     * @return true if both modifications were successful
     */
    public boolean modifyBracket(double newStopPrice, double newTargetPrice) {
        boolean stopModified = modifyStopPrice(newStopPrice);
        boolean targetModified = modifyTargetPrice(newTargetPrice);
        
        if (stopModified && targetModified) {
            logger.info("Modified bracket orders - Stop: {}, Target: {}", 
                       String.format("%.2f", newStopPrice),
                       String.format("%.2f", newTargetPrice));
            return true;
        } else if (stopModified || targetModified) {
            logger.warn("Partial bracket modification - Stop modified: {}, Target modified: {}", 
                       stopModified, targetModified);
        }
        
        return stopModified && targetModified;
    }
    
    /**
     * Gets the status of all tracked orders.
     * 
     * @return formatted string with order statuses
     */
    public String getOrderStatus() {
        return orderBundle.getStatus();
    }
    
    /**
     * Gets the order bundle for advanced order management.
     * 
     * @return the order bundle
     */
    public OrderBundle getOrderBundle() {
        return orderBundle;
    }
    
    /**
     * Enters a position with multiple stops and targets.
     * 
     * @param isLong true for long, false for short
     * @param entryPrice the expected entry price
     * @param stopPrices array of stop prices (for different risk levels)
     * @param stopQuantities array of quantities for each stop
     * @param targetPrices array of target prices (for scaling out)
     * @param targetQuantities array of quantities for each target
     * @param totalQuantity total position quantity
     * @return position information if successful, null if failed
     */
    public PositionInfo enterWithMultipleOrders(boolean isLong, double entryPrice,
                                                double[] stopPrices, int[] stopQuantities,
                                                double[] targetPrices, int[] targetQuantities,
                                                int totalQuantity) {
        if (hasPosition()) {
            logger.warn("Cannot enter position - already have position: {}", getCurrentPositionSide());
            return null;
        }
        
        try {
            Instrument instrument = orderContext.getInstrument();
            List<Order> allOrders = new ArrayList<>();
            
            // Create market order
            String marketOrderId = UUID.randomUUID().toString();
            Order marketOrder = orderContext.createMarketOrder(
                instrument,
                marketOrderId,
                isLong ? Enums.OrderAction.BUY : Enums.OrderAction.SELL,
                totalQuantity
            );
            orderBundle.addMarketOrder(marketOrderId, marketOrder, "market");
            allOrders.add(marketOrder);
            
            // Create multiple stop orders
            for (int i = 0; i < stopPrices.length && i < stopQuantities.length; i++) {
                String stopOrderId = UUID.randomUUID().toString();
                Order stopOrder = orderContext.createStopOrder(
                    instrument,
                    stopOrderId,
                    isLong ? Enums.OrderAction.SELL : Enums.OrderAction.BUY,
                    Enums.TIF.DAY,
                    stopQuantities[i],
                    (float) stopPrices[i]
                );
                orderBundle.addStopOrder(stopOrderId, stopOrder, "stop" + (i + 1));
                allOrders.add(stopOrder);
            }
            
            // Create multiple target orders
            for (int i = 0; i < targetPrices.length && i < targetQuantities.length; i++) {
                String targetOrderId = UUID.randomUUID().toString();
                Order targetOrder = orderContext.createLimitOrder(
                    instrument,
                    targetOrderId,
                    isLong ? Enums.OrderAction.SELL : Enums.OrderAction.BUY,
                    Enums.TIF.DAY,
                    targetQuantities[i],
                    (float) targetPrices[i]
                );
                orderBundle.addTargetOrder(targetOrderId, targetOrder, "target" + (i + 1));
                allOrders.add(targetOrder);
            }
            
            // Submit all orders
            orderContext.submitOrders(allOrders.toArray(new Order[0]));
            
            logger.info("✅ {} POSITION ENTERED with {} stops and {} targets", 
                       isLong ? "LONG" : "SHORT", stopPrices.length, targetPrices.length);
            logger.info("  - Total Quantity: {}", totalQuantity);
            logger.info("  - Stop Levels: {}", Arrays.toString(stopPrices));
            logger.info("  - Target Levels: {}", Arrays.toString(targetPrices));
            
            // Update position tracking (use first stop and target for primary tracking)
            double primaryStop = stopPrices.length > 0 ? stopPrices[0] : 0;
            double primaryTarget = targetPrices.length > 0 ? targetPrices[0] : 0;
            positionTracker.updatePosition(entryPrice, primaryStop, primaryTarget, isLong);
            
            // Return position info with order bundle reference
            return new PositionInfo(entryPrice, primaryStop, primaryTarget, totalQuantity, isLong,
                                  marketOrderId, null, null); // Can extend to include bundle reference
            
        } catch (Exception e) {
            logger.error("Failed to enter position with multiple orders: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Modifies all active stop orders to new prices.
     * 
     * @param newStopPrices array of new stop prices
     * @return number of stops modified
     */
    public int modifyAllStops(double[] newStopPrices) {
        List<Order> activeStops = orderBundle.getActiveStopOrders();
        int modified = 0;
        
        for (int i = 0; i < activeStops.size() && i < newStopPrices.length; i++) {
            Order stop = activeStops.get(i);
            try {
                stop.setAdjStopPrice((float) newStopPrices[i]);
                modified++;
                logger.info("Modified stop order {} to price: {}", 
                           orderBundle.getOrderId(stop), newStopPrices[i]);
            } catch (Exception e) {
                logger.error("Failed to modify stop: {}", e.getMessage());
            }
        }
        
        return modified;
    }
    
    /**
     * Trails all stop orders by a fixed amount.
     * 
     * @param trailAmount the amount to trail by (positive for long, negative for short)
     * @return number of stops trailed
     */
    public int trailAllStops(double trailAmount) {
        List<Order> activeStops = orderBundle.getActiveStopOrders();
        int trailed = 0;
        
        for (Order stop : activeStops) {
            try {
                float currentStop = stop.getStopPrice();
                float newStop = currentStop + (float) trailAmount;
                
                // Validate trail direction
                if ((isLong() && trailAmount > 0) || (!isLong() && trailAmount < 0)) {
                    stop.setAdjStopPrice(newStop);
                    trailed++;
                    logger.info("Trailed stop from {} to {}", currentStop, newStop);
                }
            } catch (Exception e) {
                logger.error("Failed to trail stop: {}", e.getMessage());
            }
        }
        
        return trailed;
    }
    
    /**
     * Container for position information including order IDs.
     */
    public static class PositionInfo {
        private final double entryPrice;
        private final double stopPrice;
        private final double targetPrice;
        private final int quantity;
        private final boolean isLong;
        
        // Order IDs for tracking
        private final String marketOrderId;
        private final String stopOrderId;
        private final String targetOrderId;
        
        // Legacy constructor for backward compatibility
        public PositionInfo(double entryPrice, double stopPrice, double targetPrice, 
                           int quantity, boolean isLong) {
            this(entryPrice, stopPrice, targetPrice, quantity, isLong, null, null, null);
        }
        
        // Full constructor with order IDs
        public PositionInfo(double entryPrice, double stopPrice, double targetPrice, 
                           int quantity, boolean isLong, String marketOrderId, 
                           String stopOrderId, String targetOrderId) {
            this.entryPrice = entryPrice;
            this.stopPrice = stopPrice;
            this.targetPrice = targetPrice;
            this.quantity = quantity;
            this.isLong = isLong;
            this.marketOrderId = marketOrderId;
            this.stopOrderId = stopOrderId;
            this.targetOrderId = targetOrderId;
        }
        
        public double getEntryPrice() { return entryPrice; }
        public double getStopPrice() { return stopPrice; }
        public double getTargetPrice() { return targetPrice; }
        public int getQuantity() { return quantity; }
        public boolean isLong() { return isLong; }
        
        // New getter methods for order IDs
        public String getMarketOrderId() { return marketOrderId; }
        public String getStopOrderId() { return stopOrderId; }
        public String getTargetOrderId() { return targetOrderId; }
        
        @Override
        public String toString() {
            return String.format("PositionInfo{%s, entry=%.2f, stop=%.2f, target=%.2f, qty=%d, orders=[market=%s, stop=%s, target=%s]}",
                               isLong ? "LONG" : "SHORT", entryPrice, stopPrice, targetPrice, quantity,
                               marketOrderId, stopOrderId, targetOrderId);
        }
    }
}