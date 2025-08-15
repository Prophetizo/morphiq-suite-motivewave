package com.prophetizo.wavelets.swt.core;

import com.motivewave.platform.sdk.order_mgmt.Order;
import com.prophetizo.LoggerConfig;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages a bundle of related orders for a position.
 * Supports multiple market, stop, and target orders for scaling strategies.
 * 
 * <p>This class provides:
 * <ul>
 *   <li>Support for multiple stops (different risk levels)</li>
 *   <li>Support for multiple targets (scaling out)</li>
 *   <li>Tagged orders for easy identification</li>
 *   <li>Order grouping and management</li>
 * </ul>
 *
 */
public class OrderBundle {
    private static final Logger logger = LoggerConfig.getLogger(OrderBundle.class);
    
    // Order storage by type
    private final Map<String, Order> marketOrders = new ConcurrentHashMap<>();
    private final Map<String, Order> stopOrders = new ConcurrentHashMap<>();
    private final Map<String, Order> targetOrders = new ConcurrentHashMap<>();
    
    // Reverse mapping: Order -> ID
    private final Map<Order, String> orderToId = new ConcurrentHashMap<>();
    
    // Order tags for identification (e.g., "stop1", "stop2", "target1", "target2")
    private final Map<String, String> orderTags = new ConcurrentHashMap<>();
    
    /**
     * Adds a market order to the bundle.
     * 
     * @param orderId the unique order ID
     * @param order the order object
     * @param tag optional tag for identification (can be null)
     */
    public void addMarketOrder(String orderId, Order order, String tag) {
        marketOrders.put(orderId, order);
        orderToId.put(order, orderId);
        if (tag != null) {
            orderTags.put(orderId, tag);
        }
        logger.debug("Added market order {} with tag: {}", orderId, tag);
    }
    
    /**
     * Adds a stop order to the bundle.
     * 
     * @param orderId the unique order ID
     * @param order the order object
     * @param tag optional tag for identification (e.g., "stop1", "stop2")
     */
    public void addStopOrder(String orderId, Order order, String tag) {
        stopOrders.put(orderId, order);
        orderToId.put(order, orderId);
        if (tag != null) {
            orderTags.put(orderId, tag);
        }
        logger.debug("Added stop order {} with tag: {}", orderId, tag);
    }
    
    /**
     * Adds a target order to the bundle.
     * 
     * @param orderId the unique order ID
     * @param order the order object
     * @param tag optional tag for identification (e.g., "target1", "target2")
     */
    public void addTargetOrder(String orderId, Order order, String tag) {
        targetOrders.put(orderId, order);
        orderToId.put(order, orderId);
        if (tag != null) {
            orderTags.put(orderId, tag);
        }
        logger.debug("Added target order {} with tag: {}", orderId, tag);
    }
    
    /**
     * Gets all market orders.
     * 
     * @return unmodifiable map of market orders
     */
    public Map<String, Order> getMarketOrders() {
        return Collections.unmodifiableMap(marketOrders);
    }
    
    /**
     * Gets all stop orders.
     * 
     * @return unmodifiable map of stop orders
     */
    public Map<String, Order> getStopOrders() {
        return Collections.unmodifiableMap(stopOrders);
    }
    
    /**
     * Gets all target orders.
     * 
     * @return unmodifiable map of target orders
     */
    public Map<String, Order> getTargetOrders() {
        return Collections.unmodifiableMap(targetOrders);
    }
    
    /**
     * Gets an order by its ID.
     * 
     * @param orderId the order ID
     * @return the order if found, null otherwise
     */
    public Order getOrderById(String orderId) {
        Order order = marketOrders.get(orderId);
        if (order != null) return order;
        
        order = stopOrders.get(orderId);
        if (order != null) return order;
        
        return targetOrders.get(orderId);
    }
    
    /**
     * Gets an order by its tag.
     * 
     * @param tag the order tag (e.g., "stop1", "target2")
     * @return the order if found, null otherwise
     */
    public Order getOrderByTag(String tag) {
        for (Map.Entry<String, String> entry : orderTags.entrySet()) {
            if (tag.equals(entry.getValue())) {
                return getOrderById(entry.getKey());
            }
        }
        return null;
    }
    
    /**
     * Gets the order ID for a given order object.
     * 
     * @param order the order object
     * @return the order ID if found, null otherwise
     */
    public String getOrderId(Order order) {
        return orderToId.get(order);
    }
    
    /**
     * Gets the tag for a given order ID.
     * 
     * @param orderId the order ID
     * @return the tag if found, null otherwise
     */
    public String getOrderTag(String orderId) {
        return orderTags.get(orderId);
    }
    
    /**
     * Removes an order from the bundle (e.g., when filled or cancelled).
     * 
     * @param orderId the order ID to remove
     * @return true if order was removed, false if not found
     */
    public boolean removeOrder(String orderId) {
        Order order = null;
        
        // Remove from appropriate map
        order = marketOrders.remove(orderId);
        if (order == null) {
            order = stopOrders.remove(orderId);
        }
        if (order == null) {
            order = targetOrders.remove(orderId);
        }
        
        if (order != null) {
            orderToId.remove(order);
            orderTags.remove(orderId);
            logger.debug("Removed order {} from bundle", orderId);
            return true;
        }
        
        return false;
    }
    
    /**
     * Removes an order by its object reference.
     * 
     * @param order the order to remove
     * @return true if order was removed, false if not found
     */
    public boolean removeOrder(Order order) {
        String orderId = orderToId.get(order);
        if (orderId != null) {
            return removeOrder(orderId);
        }
        return false;
    }
    
    /**
     * Gets all active stop orders (not filled or cancelled).
     * 
     * @return list of active stop orders
     */
    public List<Order> getActiveStopOrders() {
        return stopOrders.values().stream()
            .filter(order -> order.isActive() && !order.isCancelled() && !order.isFilled())
            .collect(Collectors.toList());
    }
    
    /**
     * Gets all active target orders (not filled or cancelled).
     * 
     * @return list of active target orders
     */
    public List<Order> getActiveTargetOrders() {
        return targetOrders.values().stream()
            .filter(order -> order.isActive() && !order.isCancelled() && !order.isFilled())
            .collect(Collectors.toList());
    }
    
    /**
     * Modifies all active stop orders to a new price.
     * 
     * @param newStopPrice the new stop price
     * @return number of orders modified
     */
    public int modifyAllStops(double newStopPrice) {
        int modified = 0;
        for (Order stop : getActiveStopOrders()) {
            try {
                stop.setAdjStopPrice((float) newStopPrice);
                modified++;
                String orderId = orderToId.get(stop);
                logger.info("Modified stop order {} to price: {}", orderId, newStopPrice);
            } catch (Exception e) {
                logger.error("Failed to modify stop order: {}", e.getMessage());
            }
        }
        return modified;
    }
    
    /**
     * Modifies a specific stop order by tag.
     * 
     * @param tag the stop order tag
     * @param newStopPrice the new stop price
     * @return true if modified, false otherwise
     */
    public boolean modifyStopByTag(String tag, double newStopPrice) {
        Order stop = getOrderByTag(tag);
        if (stop != null && stop.isActive() && !stop.isCancelled() && !stop.isFilled()) {
            try {
                stop.setAdjStopPrice((float) newStopPrice);
                logger.info("Modified stop order with tag {} to price: {}", tag, newStopPrice);
                return true;
            } catch (Exception e) {
                logger.error("Failed to modify stop order: {}", e.getMessage());
            }
        }
        return false;
    }
    
    /**
     * Modifies a specific target order by tag.
     * 
     * @param tag the target order tag
     * @param newTargetPrice the new target price
     * @return true if modified, false otherwise
     */
    public boolean modifyTargetByTag(String tag, double newTargetPrice) {
        Order target = getOrderByTag(tag);
        if (target != null && target.isActive() && !target.isCancelled() && !target.isFilled()) {
            try {
                target.setAdjLimitPrice((float) newTargetPrice);
                logger.info("Modified target order with tag {} to price: {}", tag, newTargetPrice);
                return true;
            } catch (Exception e) {
                logger.error("Failed to modify target order: {}", e.getMessage());
            }
        }
        return false;
    }
    
    /**
     * Clears all orders from the bundle.
     */
    public void clear() {
        marketOrders.clear();
        stopOrders.clear();
        targetOrders.clear();
        orderToId.clear();
        orderTags.clear();
        logger.debug("Cleared all orders from bundle");
    }
    
    /**
     * Gets the total number of orders in the bundle.
     * 
     * @return total order count
     */
    public int size() {
        return marketOrders.size() + stopOrders.size() + targetOrders.size();
    }
    
    /**
     * Gets the number of active orders in the bundle.
     * 
     * @return active order count
     */
    public int getActiveCount() {
        int count = 0;
        for (Order order : marketOrders.values()) {
            if (order.isActive() && !order.isCancelled() && !order.isFilled()) count++;
        }
        for (Order order : stopOrders.values()) {
            if (order.isActive() && !order.isCancelled() && !order.isFilled()) count++;
        }
        for (Order order : targetOrders.values()) {
            if (order.isActive() && !order.isCancelled() && !order.isFilled()) count++;
        }
        return count;
    }
    
    /**
     * Gets a formatted status report of all orders.
     * 
     * @return formatted status string
     */
    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("Order Bundle Status:\n");
        
        sb.append("  Market Orders (").append(marketOrders.size()).append("):\n");
        for (Map.Entry<String, Order> entry : marketOrders.entrySet()) {
            appendOrderStatus(sb, entry.getKey(), entry.getValue());
        }
        
        sb.append("  Stop Orders (").append(stopOrders.size()).append("):\n");
        for (Map.Entry<String, Order> entry : stopOrders.entrySet()) {
            appendOrderStatus(sb, entry.getKey(), entry.getValue());
        }
        
        sb.append("  Target Orders (").append(targetOrders.size()).append("):\n");
        for (Map.Entry<String, Order> entry : targetOrders.entrySet()) {
            appendOrderStatus(sb, entry.getKey(), entry.getValue());
        }
        
        return sb.toString();
    }
    
    private void appendOrderStatus(StringBuilder sb, String orderId, Order order) {
        String tag = orderTags.get(orderId);
        sb.append("    [").append(orderId.substring(0, 8)).append("]");
        if (tag != null) {
            sb.append(" (").append(tag).append(")");
        }
        sb.append(": Active=").append(order.isActive());
        sb.append(", Filled=").append(order.isFilled());
        sb.append(", Cancelled=").append(order.isCancelled());
        sb.append("\n");
    }
}