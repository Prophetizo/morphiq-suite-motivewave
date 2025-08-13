# P&L Tracking Reference Code

This document preserves the P&L tracking logic that was removed from `SwtTrendMomentumStrategy.java` for future reference.

## Context
These methods don't match any MotiveWave SDK interface and were never called. The SDK's `onPositionClosed(OrderContext)` doesn't provide trade details. All position tracking is handled in `onOrderFilled()` which has access to the Order object.

## Reference Implementation

```java
/**
 * Track position opening (not part of MotiveWave SDK)
 */
public void onPositionOpened(OrderContext ctx, Instrument instrument, boolean isLong, float quantity, double avgPrice) {
    logger.info("Position opened: {} {} shares/contracts at {:.2f}", 
               isLong ? "Long" : "Short", quantity, avgPrice);
    
    // Update tracking
    hasPosition = true;
    this.isLong = isLong;
    entryPrice = avgPrice;
}

/**
 * Track position closing with P&L calculation (not part of MotiveWave SDK)
 */
public void onPositionClosed(OrderContext ctx, Instrument instrument, boolean isLong, float quantity, double avgPrice) {
    // Determine exit type
    String exitType = "MANUAL";
    if (stopPrice > 0) {
        double stopDistance = Math.abs(avgPrice - stopPrice);
        double targetDistance = Math.abs(avgPrice - targetPrice);
        
        if (stopDistance < targetDistance * 0.2) {
            exitType = "STOP_LOSS";
        } else if (targetPrice > 0 && targetDistance < Math.abs(targetPrice - entryPrice) * 0.1) {
            exitType = "TARGET";
        }
    }
    
    logger.info("=== POSITION CLOSED [{}] ===", exitType);
    logger.info("  Direction: {} {} shares/contracts", 
               isLong ? "LONG" : "SHORT", quantity);
    logger.info("  Entry: {}, Exit: {}", 
               String.format("%.2f", entryPrice), 
               String.format("%.2f", avgPrice));
    
    // Calculate P&L
    if (hasPosition && entryPrice > 0) {
        double pnl = isLong ? (avgPrice - entryPrice) * quantity : (entryPrice - avgPrice) * quantity;
        double pnlPercent = ((avgPrice - entryPrice) / entryPrice) * 100 * (isLong ? 1 : -1);
        
        if (pnl >= 0) {
            logger.info("  ✅ PROFIT: ${} ({}%)", 
                       String.format("%.2f", pnl), 
                       String.format("%.2f", pnlPercent));
        } else {
            logger.warn("  ❌ LOSS: ${} ({}%)", 
                       String.format("%.2f", pnl), 
                       String.format("%.2f", pnlPercent));
        }
        
        // Log stop/target info if available
        if (stopPrice > 0) {
            logger.info("  Stop was at: {}, Target was at: {}", 
                       String.format("%.2f", stopPrice), 
                       targetPrice > 0 ? String.format("%.2f", targetPrice) : "N/A");
        }
    }
    logger.info("==============================");
    
    // Reset tracking
    resetPositionTracking();
}
```

## Integration Notes

If P&L tracking is needed in the future, this logic should be integrated into the existing `onOrderFilled()` method which has access to the Order object with actual fill prices and quantities.

### Key Calculations:
- **P&L for Long**: `(exitPrice - entryPrice) * quantity`
- **P&L for Short**: `(entryPrice - exitPrice) * quantity`
- **P&L Percentage**: `((exitPrice - entryPrice) / entryPrice) * 100`

### Exit Type Detection:
- **STOP_LOSS**: When exit price is within 20% of stop price distance
- **TARGET**: When exit price is within 10% of target price
- **MANUAL**: All other cases