# Position Manager Framework Documentation

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Quick Start](#quick-start)
4. [Core Components](#core-components)
5. [Basic Usage](#basic-usage)
6. [Advanced Features](#advanced-features)
7. [Order Bundle Management](#order-bundle-management)
8. [Real-World Examples](#real-world-examples)
9. [API Reference](#api-reference)
10. [Best Practices](#best-practices)

## Overview

The Position Manager Framework is a comprehensive order and position management system designed for MotiveWave trading strategies. It provides robust support for simple single-order positions as well as complex multi-order strategies with multiple stops and targets.

### Key Features
- ✅ **Order Tracking**: Full lifecycle management with unique UUID identifiers
- ✅ **Multiple Orders**: Support for multiple stops and targets per position
- ✅ **Order Modification**: Dynamic price adjustments for stops and targets
- ✅ **Trailing Stops**: Smart trailing with directional validation
- ✅ **Order Tagging**: Named orders for easy identification and management
- ✅ **Position Tracking**: Real-time position state and P&L calculations
- ✅ **Thread Safety**: Concurrent operations support
- ✅ **Backward Compatible**: Existing single-order code continues to work

## Architecture

```
PositionManager
├── OrderBundle (manages multiple orders)
│   ├── Market Orders Map
│   ├── Stop Orders Map
│   └── Target Orders Map
├── PositionTracker (tracks position state)
│   ├── Entry/Stop/Target Prices
│   ├── Position Direction
│   └── P&L Calculations
└── PositionSizer (calculates position sizes)
    ├── Risk-based Sizing
    └── Instrument-specific Logic
```

## Quick Start

### Basic Setup

```java
import com.morphiqlabs.wavelets.swt.core.*;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;

// Initialize the framework
OrderContext ctx = ... // From MotiveWave
        PositionSizer sizer = new PositionSizer(ctx.getInstrument());
        PositionManager positionManager = new PositionManager(ctx, sizer);
```

### Simple Position Entry

```java
// Enter a long position with single stop and target
PositionInfo position = positionManager.enterLong(
    4500.0,  // entry price
    4490.0,  // stop loss
    4520.0,  // take profit
    2        // quantity
);

// Check if entry was successful
if (position != null) {
    logger.info("Position entered: {}", position);
    logger.info("Market Order ID: {}", position.getMarketOrderId());
    logger.info("Stop Order ID: {}", position.getStopOrderId());
    logger.info("Target Order ID: {}", position.getTargetOrderId());
}
```

## Core Components

### 1. PositionManager

The main orchestrator that coordinates all position-related operations.

```java
public class PositionManager {
    // Position entry methods
    public PositionInfo enterLong(double entry, double stop, double target, int qty);
    public PositionInfo enterShort(double entry, double stop, double target, int qty);
    public PositionInfo enterWithMultipleOrders(...);
    
    // Position management
    public boolean exitPosition();
    public PositionInfo reversePosition(...);
    
    // Order modifications
    public boolean modifyStopPrice(double newPrice);
    public boolean modifyTargetPrice(double newPrice);
    public boolean trailStop(double newPrice);
    
    // Position queries
    public boolean hasPosition();
    public boolean isLong();
    public boolean isShort();
    public int getCurrentPosition();
}
```

### 2. OrderBundle

Manages collections of related orders with support for tagging and bulk operations.

```java
public class OrderBundle {
    // Add orders with tags
    public void addStopOrder(String orderId, Order order, String tag);
    public void addTargetOrder(String orderId, Order order, String tag);
    
    // Query orders
    public Order getOrderByTag(String tag);
    public List<Order> getActiveStopOrders();
    public List<Order> getActiveTargetOrders();
    
    // Modify orders
    public boolean modifyStopByTag(String tag, double newPrice);
    public boolean modifyTargetByTag(String tag, double newPrice);
    public int modifyAllStops(double newPrice);
}
```

### 3. PositionTracker

Tracks position state and provides calculations.

```java
public class PositionTracker {
    // Position updates
    public void updatePosition(double entry, double stop, double target, boolean isLong);
    public void updateStopPrice(double newStop);
    public void updateTargetPrice(double newTarget);
    
    // Calculations
    public double calculateUnrealizedPnL(double currentPrice, int quantity);
    public boolean isNearStop(double currentPrice, double threshold);
    
    // State queries
    public boolean hasPosition();
    public double getEntryPrice();
    public double getStopPrice();
    public double getTargetPrice();
}
```

## Basic Usage

### Entering Positions

```java
// Long position
PositionInfo longPos = positionManager.enterLong(4500.0, 4490.0, 4520.0, 2);

// Short position
PositionInfo shortPos = positionManager.enterShort(4500.0, 4510.0, 4480.0, 2);

// Check position state
if (positionManager.hasPosition()) {
    String side = positionManager.isLong() ? "LONG" : "SHORT";
    int quantity = positionManager.getCurrentPosition();
    logger.info("Position: {} {}", quantity, side);
}
```

### Modifying Orders

```java
// Modify stop loss
boolean modified = positionManager.modifyStopPrice(4495.0);

// Modify take profit
modified = positionManager.modifyTargetPrice(4525.0);

// Trail stop (only improves position)
modified = positionManager.trailStop(4497.0);

// Modify both stop and target
modified = positionManager.modifyBracket(4495.0, 4525.0);
```

### Exiting Positions

```java
// Exit at market
boolean exited = positionManager.exitPosition();

// Reverse position (exit and enter opposite)
PositionInfo reversed = positionManager.reversePosition(
    4505.0,  // new entry
    4515.0,  // new stop
    4485.0,  // new target
    2        // quantity
);
```

### Order Status Monitoring

```java
// Get comprehensive order status
String status = positionManager.getOrderStatus();
logger.info(status);

// Check specific orders
String stopId = positionManager.getStopOrderId();
Order stopOrder = positionManager.getOrderById(stopId);
if (stopOrder != null && stopOrder.isActive()) {
    logger.info("Stop order is active at price: {}", stopOrder.getStopPrice());
}
```

## Advanced Features

### Multiple Stops and Targets

Perfect for scaling strategies and multiple risk levels.

```java
// Define multiple stops (different risk levels)
double[] stopPrices = {4495.0, 4490.0, 4485.0};  // Tight, medium, wide
int[] stopQuantities = {1, 1, 1};                 // 1 contract each

// Define multiple targets (scaling out)
double[] targetPrices = {4510.0, 4520.0, 4530.0}; // 3 profit levels
int[] targetQuantities = {1, 1, 1};               // Exit 1 at each level

// Enter position with multiple orders
PositionInfo position = positionManager.enterWithMultipleOrders(
    true,               // isLong
    4500.0,            // entry price
    stopPrices,        // array of stops
    stopQuantities,    // quantities for each stop
    targetPrices,      // array of targets
    targetQuantities,  // quantities for each target
    3                  // total quantity
);
```

### Working with Tagged Orders

```java
// Access OrderBundle for advanced management
OrderBundle bundle = positionManager.getOrderBundle();

// Modify specific orders by tag
bundle.modifyStopByTag("stop1", 4496.0);    // Tighten first stop
bundle.modifyStopByTag("stop2", 4492.0);    // Adjust second stop
bundle.modifyTargetByTag("target1", 4512.0); // Move first target

// Get specific orders by tag
Order firstStop = bundle.getOrderByTag("stop1");
Order secondTarget = bundle.getOrderByTag("target2");

// Check order status
if (firstStop != null && firstStop.isActive()) {
    logger.info("Stop1 active at: {}", firstStop.getStopPrice());
}
```

### Bulk Order Operations

```java
// Modify all stops to new prices
double[] newStopPrices = {4496.0, 4493.0, 4490.0};
int modified = positionManager.modifyAllStops(newStopPrices);
logger.info("Modified {} stop orders", modified);

// Trail all stops by fixed amount
int trailed = positionManager.trailAllStops(2.0); // Trail up by 2 points
logger.info("Trailed {} stop orders", trailed);

// Modify all stops to same price (disaster stop)
int updated = bundle.modifyAllStops(4480.0);
logger.info("Updated {} stops to disaster level", updated);
```

### Dynamic Position Management

```java
// Monitor and adjust position dynamically
public void onBarUpdate(double currentPrice) {
    if (!positionManager.hasPosition()) return;
    
    // Trail stops based on profit
    PositionTracker tracker = positionManager.getPositionTracker();
    double entry = tracker.getEntryPrice();
    double profit = positionManager.isLong() ? 
        currentPrice - entry : entry - currentPrice;
    
    if (profit > 10.0) {
        // Lock in profit with tight stop
        positionManager.trailStop(currentPrice - 5.0);
    } else if (profit > 5.0) {
        // Breakeven stop
        positionManager.trailStop(entry);
    }
    
    // Check if near stop
    if (tracker.isNearStop(currentPrice, 2.0)) {
        logger.warn("Price near stop level!");
    }
}
```

## Order Bundle Management

### Understanding Order Bundle

The OrderBundle class provides sophisticated multi-order management:

```java
OrderBundle bundle = positionManager.getOrderBundle();

// Get all orders by type
Map<String, Order> marketOrders = bundle.getMarketOrders();
Map<String, Order> stopOrders = bundle.getStopOrders();
Map<String, Order> targetOrders = bundle.getTargetOrders();

// Get active orders only
List<Order> activeStops = bundle.getActiveStopOrders();
List<Order> activeTargets = bundle.getActiveTargetOrders();

// Count orders
int totalOrders = bundle.size();
int activeCount = bundle.getActiveCount();

// Get detailed status
String status = bundle.getStatus();
```

### Order Lifecycle Management

```java
// Handle order fills
public void onOrderFilled(Order filledOrder) {
    // Position manager automatically handles this
    positionManager.onOrderFilled(filledOrder);
    
    // Check what type of order was filled
    OrderBundle bundle = positionManager.getOrderBundle();
    String orderId = bundle.getOrderId(filledOrder);
    String tag = bundle.getOrderTag(orderId);
    
    if (tag != null && tag.startsWith("target")) {
        logger.info("Target {} filled - partial profit taken", tag);
        
        // Optionally tighten remaining stops
        if (tag.equals("target1")) {
            bundle.modifyStopByTag("stop2", filledOrder.getAvgFillPrice() - 5);
            bundle.modifyStopByTag("stop3", filledOrder.getAvgFillPrice() - 8);
        }
    }
}
```

## Real-World Examples

### Example 1: Scaling Out Strategy

```java
public class ScalingOutStrategy {
    private PositionManager positionManager;
    
    public void enterScalingPosition(double entryPrice) {
        // Single stop for entire position
        double[] stops = {entryPrice - 10.0};
        int[] stopQty = {3};
        
        // Three targets for scaling out
        double[] targets = {
            entryPrice + 5.0,   // Quick profit
            entryPrice + 10.0,  // Medium target
            entryPrice + 20.0   // Runner
        };
        int[] targetQty = {1, 1, 1};
        
        PositionInfo pos = positionManager.enterWithMultipleOrders(
            true, entryPrice, stops, stopQty, targets, targetQty, 3
        );
        
        if (pos != null) {
            logger.info("Scaling position entered with 3 targets");
        }
    }
    
    public void onTargetFilled(String targetTag) {
        OrderBundle bundle = positionManager.getOrderBundle();
        
        switch(targetTag) {
            case "target1":
                // First target hit - move stop to breakeven
                bundle.modifyStopByTag("stop1", getEntryPrice());
                logger.info("Target 1 filled - stop moved to breakeven");
                break;
                
            case "target2":
                // Second target hit - trail stop tighter
                double currentPrice = getCurrentPrice();
                bundle.modifyStopByTag("stop1", currentPrice - 5.0);
                logger.info("Target 2 filled - trailing stop tightened");
                break;
                
            case "target3":
                // Final target hit - position closed
                logger.info("All targets filled - position closed");
                break;
        }
    }
}
```

### Example 2: Multiple Risk Level Strategy

```java
public class MultiRiskStrategy {
    private PositionManager positionManager;
    
    public void enterMultiRiskPosition(double entry, int aggressiveQty, 
                                      int moderateQty, int conservativeQty) {
        // Three different risk levels
        double[] stops = {
            entry - 5.0,   // Aggressive: tight stop
            entry - 10.0,  // Moderate: medium stop
            entry - 15.0   // Conservative: wide stop
        };
        int[] stopQty = {aggressiveQty, moderateQty, conservativeQty};
        
        // Single target for all
        double[] targets = {entry + 20.0};
        int[] targetQty = {aggressiveQty + moderateQty + conservativeQty};
        
        int totalQty = aggressiveQty + moderateQty + conservativeQty;
        
        PositionInfo pos = positionManager.enterWithMultipleOrders(
            true, entry, stops, stopQty, targets, targetQty, totalQty
        );
        
        if (pos != null) {
            logger.info("Multi-risk position entered with 3 stop levels");
        }
    }
    
    public void manageRiskLevels(double currentPrice) {
        OrderBundle bundle = positionManager.getOrderBundle();
        double entry = positionManager.getPositionTracker().getEntryPrice();
        double profit = currentPrice - entry;
        
        if (profit > 10.0) {
            // Significant profit - tighten all stops
            bundle.modifyStopByTag("stop1", entry + 5.0);  // Lock profit
            bundle.modifyStopByTag("stop2", entry + 2.0);  // Lock profit
            bundle.modifyStopByTag("stop3", entry);        // Breakeven
            logger.info("Profit > 10 - all stops tightened");
            
        } else if (profit > 5.0) {
            // Moderate profit - move aggressive to breakeven
            bundle.modifyStopByTag("stop1", entry);
            logger.info("Profit > 5 - aggressive stop at breakeven");
        }
    }
}
```

### Example 3: Bracket Order with Trailing

```java
public class TrailingBracketStrategy {
    private PositionManager positionManager;
    private double trailDistance = 5.0;
    private double highWaterMark = 0.0;
    
    public void enterBracketPosition(double entry) {
        // Standard bracket order
        PositionInfo pos = positionManager.enterLong(
            entry,
            entry - 10.0,  // Initial stop
            entry + 20.0,  // Target
            2
        );
        
        if (pos != null) {
            highWaterMark = entry;
            logger.info("Bracket position entered");
        }
    }
    
    public void updateTrailingStop(double currentPrice) {
        if (!positionManager.hasPosition()) return;
        
        if (positionManager.isLong() && currentPrice > highWaterMark) {
            highWaterMark = currentPrice;
            double newStop = highWaterMark - trailDistance;
            
            if (positionManager.trailStop(newStop)) {
                logger.info("Stop trailed to: {}", newStop);
            }
        }
    }
    
    public void onProfitTarget(double currentPrice) {
        PositionTracker tracker = positionManager.getPositionTracker();
        double entry = tracker.getEntryPrice();
        double profit = currentPrice - entry;
        
        // Adjust targets based on momentum
        if (profit > 15.0 && isStrongMomentum()) {
            // Extend target in strong trend
            positionManager.modifyTargetPrice(entry + 30.0);
            logger.info("Target extended due to strong momentum");
        }
    }
}
```

### Example 4: Pyramiding Strategy

```java
public class PyramidingStrategy {
    private PositionManager positionManager;
    private List<Double> pyramidLevels = Arrays.asList(4500.0, 4510.0, 4520.0);
    private int pyramidIndex = 0;
    
    public void enterInitialPosition() {
        // Enter first position with multiple stops for future pyramids
        double entry = pyramidLevels.get(0);
        
        // Stops for each pyramid level
        double[] stops = {entry - 10.0, entry - 5.0, entry + 5.0};
        int[] stopQty = {1, 1, 1};
        
        // Targets for scaling out
        double[] targets = {entry + 30.0, entry + 40.0, entry + 50.0};
        int[] targetQty = {1, 1, 1};
        
        positionManager.enterWithMultipleOrders(
            true, entry, stops, stopQty, targets, targetQty, 3
        );
        
        pyramidIndex = 1;
        logger.info("Initial pyramid position entered");
    }
    
    public void addPyramid(double currentPrice) {
        if (pyramidIndex >= pyramidLevels.size()) return;
        
        double pyramidLevel = pyramidLevels.get(pyramidIndex);
        if (currentPrice >= pyramidLevel) {
            // Move stops up for existing positions
            OrderBundle bundle = positionManager.getOrderBundle();
            
            // Tighten stop for first entry
            bundle.modifyStopByTag("stop1", pyramidLevels.get(0) + 5.0);
            
            // Set stop for new pyramid level
            bundle.modifyStopByTag("stop" + (pyramidIndex + 1), pyramidLevel - 5.0);
            
            pyramidIndex++;
            logger.info("Pyramid level {} activated", pyramidIndex);
        }
    }
}
```

## API Reference

### PositionManager Methods

| Method | Description | Returns |
|--------|-------------|---------|
| `enterLong(entry, stop, target, qty)` | Enter long position | PositionInfo |
| `enterShort(entry, stop, target, qty)` | Enter short position | PositionInfo |
| `enterWithMultipleOrders(...)` | Enter with multiple stops/targets | PositionInfo |
| `exitPosition()` | Exit current position at market | boolean |
| `reversePosition(...)` | Reverse current position | PositionInfo |
| `modifyStopPrice(price)` | Modify stop order price | boolean |
| `modifyTargetPrice(price)` | Modify target order price | boolean |
| `trailStop(price)` | Trail stop (only improves) | boolean |
| `modifyBracket(stop, target)` | Modify both stop and target | boolean |
| `hasPosition()` | Check if position exists | boolean |
| `isLong()` | Check if position is long | boolean |
| `isShort()` | Check if position is short | boolean |
| `getCurrentPosition()` | Get current position size | int |
| `getOrderBundle()` | Get OrderBundle for advanced ops | OrderBundle |
| `getPositionTracker()` | Get PositionTracker | PositionTracker |

### OrderBundle Methods

| Method | Description | Returns |
|--------|-------------|---------|
| `addStopOrder(id, order, tag)` | Add stop order with tag | void |
| `addTargetOrder(id, order, tag)` | Add target order with tag | void |
| `getOrderByTag(tag)` | Get order by its tag | Order |
| `getOrderById(id)` | Get order by ID | Order |
| `getActiveStopOrders()` | Get all active stops | List<Order> |
| `getActiveTargetOrders()` | Get all active targets | List<Order> |
| `modifyStopByTag(tag, price)` | Modify specific stop | boolean |
| `modifyTargetByTag(tag, price)` | Modify specific target | boolean |
| `modifyAllStops(price)` | Modify all stops to same price | int |
| `removeOrder(id)` | Remove order from tracking | boolean |
| `clear()` | Clear all orders | void |
| `getStatus()` | Get formatted status report | String |

### PositionTracker Methods

| Method | Description | Returns |
|--------|-------------|---------|
| `updatePosition(...)` | Update position info | void |
| `updateStopPrice(price)` | Update stop price | void |
| `updateTargetPrice(price)` | Update target price | void |
| `calculateUnrealizedPnL(...)` | Calculate current P&L | double |
| `isNearStop(price, threshold)` | Check if near stop | boolean |
| `hasPosition()` | Check if tracking position | boolean |
| `getEntryPrice()` | Get entry price | double |
| `getStopPrice()` | Get stop price | double |
| `getTargetPrice()` | Get target price | double |
| `reset()` | Reset all tracking | void |

## Best Practices

### 1. Always Check Return Values

```java
PositionInfo position = positionManager.enterLong(...);
if (position == null) {
    logger.error("Failed to enter position");
    return;
}
```

### 2. Use Try-Catch for Order Operations

```java
try {
    boolean modified = positionManager.modifyStopPrice(newPrice);
    if (!modified) {
        logger.warn("Stop modification failed");
    }
} catch (Exception e) {
    logger.error("Error modifying stop: {}", e.getMessage());
}
```

### 3. Clean Up on Strategy Shutdown

```java
@Override
public void onShutdown() {
    if (positionManager.hasPosition()) {
        positionManager.exitPosition();
    }
    positionManager.getOrderBundle().clear();
}
```

### 4. Log Order IDs for Debugging

```java
PositionInfo pos = positionManager.enterLong(...);
logger.info("Position entered - Market: {}, Stop: {}, Target: {}",
    pos.getMarketOrderId(),
    pos.getStopOrderId(),
    pos.getTargetOrderId());
```

### 5. Handle Order Fills Properly

```java
@Override
public void onOrderFilled(OrderContext ctx, Order order) {
    positionManager.onOrderFilled(order);
    
    // Additional logic based on order type
    String orderId = positionManager.getOrderBundle().getOrderId(order);
    String tag = positionManager.getOrderBundle().getOrderTag(orderId);
    
    if (tag != null) {
        handleOrderFillByTag(tag, order);
    }
}
```

### 6. Use Tags for Complex Strategies

```java
// Use meaningful tags
bundle.addStopOrder(id1, stop1, "initial_stop");
bundle.addStopOrder(id2, stop2, "breakeven_stop");
bundle.addStopOrder(id3, stop3, "trailing_stop");

// Easy to identify and modify later
bundle.modifyStopByTag("breakeven_stop", entryPrice);
```

### 7. Monitor Order Status

```java
// Periodic status check
if (positionManager.hasPosition()) {
    OrderBundle bundle = positionManager.getOrderBundle();
    int activeOrders = bundle.getActiveCount();
    
    if (activeOrders == 0) {
        logger.warn("No active orders protecting position!");
    }
    
    // Log detailed status periodically
    if (barCount % 100 == 0) {
        logger.info(bundle.getStatus());
    }
}
```

### 8. Validate Prices Before Modification

```java
public boolean safeModifyStop(double newStop) {
    if (positionManager.isLong() && newStop >= getCurrentPrice()) {
        logger.error("Invalid stop price for long position");
        return false;
    }
    if (positionManager.isShort() && newStop <= getCurrentPrice()) {
        logger.error("Invalid stop price for short position");
        return false;
    }
    return positionManager.modifyStopPrice(newStop);
}
```

## Troubleshooting

### Common Issues and Solutions

| Issue | Cause | Solution |
|-------|-------|----------|
| Orders not modifying | Order not active | Check `order.isActive()` before modifying |
| Position not entering | Already have position | Check `hasPosition()` first |
| Trail stop not working | Wrong direction | Ensure trail improves position |
| Orders not found | Order filled/cancelled | Check order status before operations |
| Null PositionInfo | Entry failed | Check logs for error details |

### Debug Helpers

```java
// Enable debug logging
logger.setLevel(Level.DEBUG);

// Log complete position state
public void debugPosition() {
    if (!positionManager.hasPosition()) {
        logger.info("No position");
        return;
    }
    
    PositionTracker tracker = positionManager.getPositionTracker();
    logger.info("Position Debug:");
    logger.info("  Direction: {}", positionManager.isLong() ? "LONG" : "SHORT");
    logger.info("  Quantity: {}", positionManager.getCurrentPosition());
    logger.info("  Entry: {}", tracker.getEntryPrice());
    logger.info("  Stop: {}", tracker.getStopPrice());
    logger.info("  Target: {}", tracker.getTargetPrice());
    logger.info("  Orders: {}", positionManager.getOrderBundle().getStatus());
}
```

## Migration Guide

### Upgrading from Simple Orders

If you have existing code using simple orders:

```java
// Old code
ctx.submitOrders(marketOrder, stopOrder, targetOrder);

// New code - automatic tracking
PositionInfo pos = positionManager.enterLong(entry, stop, target, qty);
// Orders are automatically tracked and managed
```

### Adding Multiple Orders to Existing Strategy

```java
// Enhance existing single-order strategy
public class EnhancedStrategy extends ExistingStrategy {
    
    @Override
    protected void enterPosition(double entry) {
        // Old: single stop/target
        // super.enterPosition(entry);
        
        // New: multiple stops/targets
        double[] stops = {entry - 5, entry - 10};
        int[] stopQty = {1, 1};
        double[] targets = {entry + 10, entry + 20};
        int[] targetQty = {1, 1};
        
        positionManager.enterWithMultipleOrders(
            true, entry, stops, stopQty, targets, targetQty, 2
        );
    }
}
```

## Performance Considerations

- **Thread Safety**: All operations are thread-safe using ConcurrentHashMap
- **Memory**: Each order stores ~100 bytes; 10 orders = ~1KB
- **CPU**: O(1) for most operations, O(n) for bulk modifications
- **Network**: Order modifications require broker communication

## Conclusion

The Position Manager Framework provides a robust, flexible, and production-ready solution for managing trading positions in MotiveWave. Whether you need simple bracket orders or complex multi-level strategies, the framework handles it elegantly while maintaining backward compatibility and thread safety.

For questions or support, refer to the main project documentation or file an issue on the project repository.

---

*Last Updated: November 2024*
*Version: 1.1.0*