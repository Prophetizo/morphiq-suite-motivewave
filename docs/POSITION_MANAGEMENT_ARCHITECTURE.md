# Position Management Architecture

## Overview

The position management components have been refactored to decouple them from the strategy for improved reusability and maintainability. This document describes the new architecture and how to use the components.

## Architecture Components

### PositionManager

**Location**: `com.morphiqlabs.common.position.PositionManager`

The central position management orchestrator that can be reused across multiple trading strategies.

**Key Features**:
- Position state tracking through integrated PositionTracker
- Order creation and submission via bracket orders
- Position sizing integration with existing PositionSizer
- Entry and exit logic coordination
- Position reversal support
- Comprehensive error handling and validation

**Usage Example**:
```java
// Initialize in strategy
PositionSizer positionSizer = PositionSizer.createValidated(ctx.getInstrument());
PositionManager positionManager = new PositionManager(ctx, positionSizer);

// Enter positions
PositionManager.PositionInfo longEntry = positionManager.enterLong(
    entryPrice, stopPrice, targetPrice, quantity);

PositionManager.PositionInfo shortEntry = positionManager.enterShort(
    entryPrice, stopPrice, targetPrice, quantity);

// Reverse positions
PositionManager.PositionInfo reversed = positionManager.reversePosition(
    newEntryPrice, newStopPrice, newTargetPrice, newQuantity);

// Exit positions
boolean exitSuccess = positionManager.exitPosition();

// Check position state
boolean hasPos = positionManager.hasPosition();
boolean isLong = positionManager.isLong();
String side = positionManager.getCurrentPositionSide(); // "LONG", "SHORT", "FLAT"
```

### PositionTracker

**Location**: `com.morphiqlabs.common.position.PositionTracker`

Thread-safe position state tracking and calculations.

**Key Features**:
- Entry, stop, and target price tracking
- Position direction tracking
- Unrealized P&L calculations
- Risk/reward ratio calculations
- Proximity detection for stops and targets
- Clean separation of state from order operations

**Usage Example**:
```java
PositionTracker tracker = positionManager.getPositionTracker();

// Check position state
boolean hasPosition = tracker.hasPosition();
boolean isLong = tracker.isLong();
double entryPrice = tracker.getEntryPrice();
double stopPrice = tracker.getStopPrice();
double targetPrice = tracker.getTargetPrice();

// Calculate metrics
double unrealizedPnL = tracker.calculateUnrealizedPnL(currentPrice, quantity);
double stopDistance = tracker.getStopDistance(currentPrice);
double targetDistance = tracker.getTargetDistance(currentPrice);
double riskReward = tracker.getRiskRewardRatio();

// Proximity detection
boolean nearStop = tracker.isNearStop(currentPrice, 0.2); // Within 20% of risk
boolean nearTarget = tracker.isNearTarget(currentPrice, 0.2); // Within 20% of reward
```

### PositionSizer (Existing)

**Location**: `com.morphiqlabs.common.position.PositionSizer`

The existing position sizing calculator remains unchanged and is integrated with the new components.

## Strategy Integration

### Before (Tightly Coupled)

```java
public class SwtTrendMomentumStrategy extends SwtTrendMomentumStudy {
    // Position tracking state embedded in strategy
    private double entryPrice = 0.0;
    private double stopPrice = 0.0;
    private double targetPrice = 0.0;
    
    // Complex order management logic in strategy
    private void handleLongEntry(OrderContext ctx, int index) {
        // ... 100+ lines of order creation and submission logic
        // ... Position size calculation mixed with strategy logic
        // ... Manual position tracking updates
    }
    
    // Duplicate position checking methods
    boolean hasPosition(OrderContext ctx) {
        return ctx.getPosition() != 0;
    }
}
```

### After (Decoupled)

```java
public class SwtTrendMomentumStrategy extends SwtTrendMomentumStudy {
    // Single position management component
    private PositionManager positionManager;
    
    @Override
    public void onActivate(OrderContext ctx) {
        // Initialize position manager
        PositionSizer positionSizer = PositionSizer.createValidated(ctx.getInstrument());
        positionManager = new PositionManager(ctx, positionSizer);
    }
    
    // Simplified entry logic
    private void handleLongEntry(OrderContext ctx, int index) {
        if (positionManager.hasPosition() && positionManager.isShort()) {
            // Reverse position
            positionManager.reversePosition(entryPrice, stopPrice, targetPrice, quantity);
        } else if (!positionManager.hasPosition()) {
            // Enter new position
            positionManager.enterLong(entryPrice, stopPrice, targetPrice, quantity);
        }
    }
    
    // Delegated position checking
    boolean hasPosition(OrderContext ctx) {
        return positionManager != null ? positionManager.hasPosition() : ctx.getPosition() != 0;
    }
}
```

## Benefits

### 1. Reusability
- PositionManager can be used by any trading strategy
- PositionTracker can be used independently for analysis
- Consistent position management behavior across strategies

### 2. Maintainability
- Clear separation of concerns
- Position management logic centralized in dedicated components
- Strategy focuses on signal processing, not order management

### 3. Testability
- Position management logic can be tested independently
- Mock-based testing without external dependencies
- Comprehensive test coverage for all components

### 4. Extensibility
- Easy to add new position management features
- Support for new order types or position strategies
- Clean interfaces for future enhancements

### 5. Consistency
- Standardized position tracking across strategies
- Uniform error handling and validation
- Consistent logging and reporting

## Migration Guide

### For Existing Strategies

1. **Replace position tracking fields** with PositionManager:
   ```java
   // Old
   private double entryPrice, stopPrice, targetPrice;
   
   // New
   private PositionManager positionManager;
   ```

2. **Initialize PositionManager** in `onActivate()`:
   ```java
   @Override
   public void onActivate(OrderContext ctx) {
       PositionSizer sizer = PositionSizer.createValidated(ctx.getInstrument());
       positionManager = new PositionManager(ctx, sizer);
   }
   ```

3. **Replace order management logic** with PositionManager calls:
   ```java
   // Old
   ctx.submitOrders(marketOrder, stopOrder, targetOrder);
   entryPrice = currentPrice;
   stopPrice = calculatedStop;
   
   // New
   positionManager.enterLong(currentPrice, calculatedStop, calculatedTarget, quantity);
   ```

4. **Update position checking methods** to delegate:
   ```java
   boolean hasPosition(OrderContext ctx) {
       return positionManager != null ? positionManager.hasPosition() : ctx.getPosition() != 0;
   }
   ```

### For New Strategies

1. **Extend from base strategy class** and add PositionManager field
2. **Initialize PositionManager** in activation
3. **Use PositionManager methods** for all position operations
4. **Access PositionTracker** for position state and calculations

## Testing

### Unit Testing

Each component has comprehensive unit tests:

- **PositionManagerTest**: 11 test methods covering all operations
- **PositionTrackerTest**: 15 test methods covering state and calculations  
- **PositionManagementIntegrationTest**: End-to-end workflow testing

### Test Coverage

- Position entry/exit operations
- Position reversal logic
- Order fill handling
- Error conditions and edge cases
- Invalid input validation
- Thread safety (where applicable)

### Running Tests

```bash
# Run all position management tests
mvn test -Dtest="*Position*Test"

# Run specific component tests
mvn test -Dtest=PositionManagerTest
mvn test -Dtest=PositionTrackerTest
mvn test -Dtest=PositionManagementIntegrationTest
```

## Future Enhancements

### Potential Additions

1. **Advanced Order Types**: Support for trailing stops, scaled entries/exits
2. **Risk Management**: Portfolio-level risk management across multiple strategies
3. **Position Analytics**: Advanced position performance metrics and reporting
4. **State Persistence**: Save/restore position state across strategy restarts
5. **Event System**: Position lifecycle events for external monitoring

### Extension Points

- **PositionManager**: Add new entry/exit methods
- **PositionTracker**: Add new calculation methods
- **Order Management**: Support for additional order types
- **Risk Management**: Pluggable risk calculation strategies

## Performance Considerations

- **Thread Safety**: PositionTracker uses synchronized methods for thread safety
- **Memory Efficiency**: Minimal object allocation during normal operations
- **Calculation Efficiency**: Lazy calculation of derived metrics
- **Logging**: Debug-level logging to avoid performance impact in production

## Compatibility

- **MotiveWave SDK**: Compatible with existing SDK interfaces
- **Existing Code**: Backward compatible through delegation methods
- **Java Version**: Requires Java 21+ (same as project requirement)
- **Dependencies**: No additional external dependencies introduced