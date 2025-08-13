package com.prophetizo.wavelets.swt;

import com.motivewave.platform.sdk.common.*;
import com.prophetizo.wavelets.swt.core.PositionSizer;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.prophetizo.LoggerConfig;
import org.slf4j.Logger;

import java.util.Arrays;

@StudyHeader(
        namespace = "com.prophetizo.wavelets.swt",
        id = "SWT_TREND_MOMENTUM_STRATEGY",
        name = "SWT Trend + Momentum Strategy",
        desc = "Trading strategy based on undecimated SWT trend and cross-scale momentum",
        menu = "MorphIQ | Wavelet Strategies",
        overlay = true,
        strategy = true,
        requiresBarUpdates = false,
        autoEntry = true,
        supportsEntryPrice = true,
        supportsPosition = true,
        supportsUnrealizedPL = true,
        supportsCurrentPL = true,
        supportsRealizedPL = true,
        supportsTotalPL = true
)
public class SwtTrendMomentumStrategy extends SwtTrendMomentumStudy {
    private static final Logger logger = LoggerConfig.getLogger(SwtTrendMomentumStrategy.class);
    
    // Additional strategy settings
    public static final String POSITION_SIZE = "POSITION_SIZE";
    public static final String USE_WATR_STOPS = "USE_WATR_STOPS";
    public static final String STOP_MULTIPLIER = "STOP_MULTIPLIER";
    public static final String TARGET_MULTIPLIER = "TARGET_MULTIPLIER";
    public static final String MIN_STOP_POINTS = "MIN_STOP_POINTS";
    public static final String MAX_STOP_POINTS = "MAX_STOP_POINTS";
    public static final String MAX_RISK_PER_TRADE = "MAX_RISK_PER_TRADE";
    public static final String ENABLE_BRACKET_ORDERS = "ENABLE_BRACKET_ORDERS";
    
    // Position tracking
    private boolean hasPosition = false;
    private boolean isLong = false;
    private double entryPrice = 0.0;
    private double stopPrice = 0.0;
    private double targetPrice = 0.0;
    
    @Override
    public void initialize(Defaults defaults) {
        // Call parent initialization first
        super.initialize(defaults);
        
        logger.debug("Initializing SWT Trend + Momentum Strategy");
        
        // Add strategy-specific settings
        SettingsDescriptor settings = getSettingsDescriptor();
        SettingTab strategyTab = settings.addTab("Strategy");
        
        SettingGroup positionGroup = strategyTab.addGroup("Position Management");
        positionGroup.addRow(new IntegerDescriptor(POSITION_SIZE, "Position Size Factor", 1, 1, 100, 1));
        // Note: This is multiplied by Trade Lots from Trading Options panel.
        // Example: If Trade Lots = 2 and Position Size Factor = 1, total quantity = 2
        // Set to 1 to use only Trade Lots value.
        positionGroup.addRow(new DoubleDescriptor(MAX_RISK_PER_TRADE, "Max Risk Per Trade ($)", 500.0, 50.0, 5000.0, 50.0));
        // Note: Point Value is automatically detected from the instrument.
        // Common values: ES=$50, NQ=$20, CL=$1000, GC=$100, 6E=$125000, Stock=$1
        
        SettingGroup riskGroup = strategyTab.addGroup("Risk Management");
        riskGroup.addRow(new BooleanDescriptor(USE_WATR_STOPS, "Use WATR-based Stops", true));
        riskGroup.addRow(new DoubleDescriptor(STOP_MULTIPLIER, "Stop Loss Multiplier", 2.0, 1.0, 5.0, 0.1));
        riskGroup.addRow(new DoubleDescriptor(TARGET_MULTIPLIER, "Target Multiplier", 3.0, 1.5, 10.0, 0.5));
        riskGroup.addRow(new DoubleDescriptor(MIN_STOP_POINTS, "Min Stop Distance (points)", 5.0, 2.0, 20.0, 1.0));
        riskGroup.addRow(new DoubleDescriptor(MAX_STOP_POINTS, "Max Stop Distance (points)", 25.0, 10.0, 100.0, 5.0));
        riskGroup.addRow(new BooleanDescriptor(ENABLE_BRACKET_ORDERS, "Enable Bracket Orders", true));
        
        // Strategy configuration is handled by @StudyHeader(strategy=true)
        // The runtime descriptor inherits all signal declarations from parent class
        
        logger.info("SWT Strategy initialized with risk management settings");
    }
    
    /**
     * Called when the strategy is activated for live trading or backtesting.
     * Initialize strategy state and check current positions.
     */
    @Override
    public void onActivate(OrderContext ctx) {
        logger.info("SWT Strategy activated");
        
        // Reset state
        hasPosition = false;
        isLong = false;
        entryPrice = 0.0;
        stopPrice = 0.0;
        
        // Check if we already have a position
        int posSize = ctx.getPosition();
        if (posSize != 0) {
            hasPosition = true;
            isLong = posSize > 0;
            logger.info("Existing position detected: size={}", posSize);
        }
    }
    
    /**
     * Called when the strategy is deactivated.
     * Clean up resources and optionally close positions.
     */
    @Override
    public void onDeactivate(OrderContext ctx) {
        logger.info("SWT Strategy deactivated");
        
        // Optionally close any open positions
        int posSize = ctx.getPosition();
        if (hasPosition && posSize != 0) {
            logger.info("Closing position on deactivation: size={}", posSize);
            ctx.closeAtMarket();
        }
    }
    
    /**
     * Called when an order is filled.
     * Update position tracking state and detect stop hits.
     */
    @Override
    public void onOrderFilled(OrderContext ctx, com.motivewave.platform.sdk.order_mgmt.Order order) {
        // Validate order object
        if (order == null) {
            logger.error("onOrderFilled called with null order");
            return;
        }
        
        double fillPrice = order.getAvgFillPrice();
        
        // Validate fill price
        if (fillPrice <= 0 || Double.isNaN(fillPrice) || Double.isInfinite(fillPrice)) {
            logger.error("Invalid fill price received: {}", fillPrice);
            return;
        }
        
        boolean isBuy = order.isBuy();
        int quantity = order.getQuantity();
        
        // Validate quantity
        if (quantity <= 0) {
            logger.error("Invalid order quantity: {}", quantity);
            return;
        }
        
        if (logger.isInfoEnabled()) {
            logger.info("📊 ORDER FILL RECEIVED:");
            logger.info("  ├─ Actual Filled Quantity: {} (includes Trade Lots multiplier)", quantity);
            logger.info("  ├─ Direction: {}", isBuy ? "BUY" : "SELL");
            logger.info("  └─ Fill Price: ${}", String.format("%.2f", fillPrice));
        }
        
        // Check if this is a stop order being filled
        boolean isStopHit = false;
        String orderType = "MARKET";
        
        // Detect stop hit: opposite direction order when we have a position
        if (hasPosition) {
            if ((isLong && !isBuy) || (!isLong && isBuy)) {
                // Check if fill price is near our stop price
                if (stopPrice > 0) {
                    double stopDistance = Math.abs(fillPrice - stopPrice);
                    double priceRange = Math.abs(entryPrice - stopPrice);
                    if (stopDistance < priceRange * 0.1) { // Within 10% of stop
                        isStopHit = true;
                        orderType = "STOP";
                    }
                }
            }
        }
        
        if (isStopHit) {
            logger.warn("⛔ STOP HIT: {} {} @ {} (stop was at {})", 
                       quantity, isBuy ? "BUY" : "SELL", 
                       String.format("%.2f", fillPrice), 
                       String.format("%.2f", stopPrice));
            
            // Calculate loss
            if (entryPrice > 0) {
                double loss = isLong ? 
                    (fillPrice - entryPrice) * quantity : 
                    (entryPrice - fillPrice) * quantity;
                double lossPerUnit = isLong ? fillPrice - entryPrice : entryPrice - fillPrice;
                logger.warn("Stop loss realized: ${} per unit, Total: ${}", 
                           String.format("%.2f", lossPerUnit), 
                           String.format("%.2f", loss));
            }
        } else {
            logger.info("Order filled: {} {} @ {} [{}]", 
                       quantity, isBuy ? "BUY" : "SELL", 
                       String.format("%.2f", fillPrice), orderType);
        }
        
        // Update position state
        int posSize = ctx.getPosition();
        if (posSize != 0) {
            hasPosition = true;
            isLong = posSize > 0;
            if (!isStopHit) { // Only update entry if not a stop
                entryPrice = fillPrice;
            }
        } else {
            hasPosition = false;
            entryPrice = 0.0;
            stopPrice = 0.0;
            targetPrice = 0.0;
        }
    }
    
    /**
     * Called when a position is closed.
     * Reset position tracking state.
     */
    @Override
    public void onPositionClosed(OrderContext ctx) {
        logger.info("Position closed");
        hasPosition = false;
        isLong = false;
        entryPrice = 0.0;
        stopPrice = 0.0;
    }
    
    // Test helper methods - package private for testing
    boolean hasPosition() { return hasPosition; }
    void setHasPosition(boolean hasPosition) { this.hasPosition = hasPosition; }
    boolean isPositionLong() { return isLong; }  // Renamed to avoid conflict
    void setIsLong(boolean isLong) { this.isLong = isLong; }
    double getEntryPrice() { return entryPrice; }
    void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }
    double getStopPriceValue() { return stopPrice; } // Renamed to avoid conflict
    void setStopPrice(double stopPrice) { this.stopPrice = stopPrice; }
    Object getBufferLock() { 
        // Return the actual bufferLock from parent class (now protected)
        return bufferLock;
    }
    int calculateFinalQuantity(OrderContext ctx) { 
        int positionSizeFactor = getSettings().getInteger(POSITION_SIZE, 1);
        // Get trade lots from settings instead of OrderContext
        int tradeLots = getSettings().getInteger("TRADE_LOTS", 1);
        return positionSizeFactor * tradeLots;
    }
    
    // Test helper method that doesn't require full initialization
    int calculateFinalQuantityForTest(int positionSizeFactor, int tradeLots) {
        return positionSizeFactor * tradeLots;
    }
    
    /**
     * Handle trading signals generated by the study.
     * This is called when the study generates a signal via ctx.signal().
     */
    @Override
    public void onSignal(OrderContext ctx, Object signal) {
        if (!getSettings().getBoolean(ENABLE_SIGNALS, true)) {
            return;
        }
        
        try {
            DataSeries series = ctx.getDataContext().getDataSeries();
            int index = series.size() - 1;
            
            // Log position status periodically
            logPositionStatus(ctx, series, index);
            
            if (signal instanceof Signals) {
                Signals swtSignal = (Signals) signal;
                
                switch (swtSignal) {
                    case LONG_ENTER:
                        handleLongEntry(ctx, index);
                        break;
                    case SHORT_ENTER:
                        handleShortEntry(ctx, index);
                        break;
                    case FLAT_EXIT:
                        handleFlatExit(ctx, index);
                        break;
                }
            }
        } catch (Exception e) {
            logger.error("Error handling signal: {}", signal, e);
        }
    }
    
    private void logPositionStatus(OrderContext ctx, DataSeries series, int index) {
        if (!hasPosition) {
            return;
        }
        
        // Only log every 100 bars to avoid spam
        if (index % 100 != 0) {
            return;
        }
        
        double currentPrice = series.getClose(index);
        if (currentPrice <= 0 || entryPrice <= 0) {
            return;
        }
        
        double unrealizedPnL = isLong ? 
            (currentPrice - entryPrice) * ctx.getPosition() :
            (entryPrice - currentPrice) * Math.abs(ctx.getPosition());
        
        double stopDistance = isLong ? 
            currentPrice - stopPrice : stopPrice - currentPrice;
        
        double targetDistance = isLong ? 
            targetPrice - currentPrice : currentPrice - targetPrice;
        
        String status = unrealizedPnL >= 0 ? "PROFIT" : "LOSS";
        
        if (logger.isDebugEnabled()) {
            logger.debug("Position Status [{}]: Price={}, P&L=${}, Stop Distance={}, Target Distance={}",
                        status, 
                        String.format("%.2f", currentPrice), 
                        String.format("%.2f", unrealizedPnL), 
                        String.format("%.2f", stopDistance), 
                        String.format("%.2f", targetDistance));
        }
        
        // Warn if getting close to stop
        if (stopDistance < Math.abs(entryPrice - stopPrice) * 0.2) {
            logger.warn("⚠️ WARNING: Position near stop! Current: {}, Stop: {}, Distance: {}",
                       String.format("%.2f", currentPrice), 
                       String.format("%.2f", stopPrice), 
                       String.format("%.2f", stopDistance));
        }
    }
    
    private void handleLongEntry(OrderContext ctx, int index) {
        if (hasPosition) {
            logger.debug("Already have position, ignoring long entry signal");
            return;
        }
        
        DataSeries series = ctx.getDataContext().getDataSeries();
        double currentPrice = series.getClose(index);
        
        if (currentPrice <= 0) {
            logger.warn("Invalid price for long entry: {}", currentPrice);
            return;
        }
        
        // Calculate position size and risk using new PositionSizer
        PositionSize positionInfo = calculatePositionSize(ctx, index, true, currentPrice);
        if (positionInfo == null || positionInfo.quantity <= 0) {
            logger.warn("Cannot calculate valid position size for long entry");
            return;
        }
        
        try {
            // Get Trade Lots from strategy settings (Trading Options panel)
            int tradeLots = getSettings().getTradeLots();
            if (tradeLots <= 0) tradeLots = 1; // Default to 1 if not set
            
            // Calculate final quantity including Trade Lots
            int finalQuantity = positionInfo.quantity * tradeLots;
            
            if (logger.isInfoEnabled()) {
                logger.info("📊 Trade Lots Calculation:");
                logger.info("  ├─ Position Size Factor: {}", positionInfo.quantity);
                logger.info("  ├─ Trade Lots Setting: {}", tradeLots);
                logger.info("  └─ Final Order Quantity: {} ({}×{})", finalQuantity, positionInfo.quantity, tradeLots);
            }
            
            if (getSettings().getBoolean(ENABLE_BRACKET_ORDERS, true)) {
                // Place bracket order (entry + stop + target) with Trade Lots multiplied
                ctx.buy(finalQuantity);
                
                if (logger.isInfoEnabled()) {
                    logger.info("✅ LONG BRACKET ORDER PLACED:");
                    logger.info("  ├─ Order Quantity: {} ({} × {} Trade Lots)", finalQuantity, positionInfo.quantity, tradeLots);
                    logger.info("  ├─ Entry Price: ${}", String.format("%.2f", currentPrice));
                    logger.info("  ├─ Stop Loss: ${} ({} points risk)", 
                               String.format("%.2f", positionInfo.stopPrice), 
                               String.format("%.2f", currentPrice - positionInfo.stopPrice));
                    logger.info("  └─ Target: ${} ({} points profit)", 
                               String.format("%.2f", positionInfo.targetPrice), 
                               String.format("%.2f", positionInfo.targetPrice - currentPrice));
                    
                    // Get actual point value from instrument
                    com.motivewave.platform.sdk.common.Instrument instrument = ctx.getInstrument();
                    double pointValue = 1.0;
                    if (instrument != null) {
                        pointValue = instrument.getPointValue();
                        if (pointValue <= 0) pointValue = 1.0;
                    }
                    
                    // Use finalQuantity (includes Trade Lots) for dollar calculations
                    double dollarRisk = finalQuantity * Math.abs(currentPrice - positionInfo.stopPrice) * pointValue;
                    double dollarReward = finalQuantity * (positionInfo.targetPrice - currentPrice) * pointValue;
                    double riskReward = (positionInfo.targetPrice - currentPrice) / (currentPrice - positionInfo.stopPrice);
                    
                    logger.info("  - Dollar Risk: ${}", String.format("%.2f", Math.abs(dollarRisk)));
                    logger.info("  - Dollar Reward: ${}", String.format("%.2f", dollarReward));
                    logger.info("  - Risk/Reward Ratio: 1:{}", String.format("%.2f", riskReward));
                    logger.info("🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢\n");
                }
            } else {
                // Simple market order with Trade Lots multiplied
                ctx.buy(finalQuantity);
                if (logger.isInfoEnabled()) {
                    logger.info("✅ LONG MARKET ORDER PLACED:");
                    logger.info("  - Quantity: {} contracts at market price ({}×{} Trade Lots)", 
                               finalQuantity, positionInfo.quantity, tradeLots);
                    logger.info("  - Expected Entry: {}", String.format("%.2f", currentPrice));
                    logger.info("  - Planned Stop: {}", String.format("%.2f", positionInfo.stopPrice));
                    logger.info("  - Planned Target: {}", String.format("%.2f", positionInfo.targetPrice));
                }
            }
            
            // Update position tracking
            hasPosition = true;
            isLong = true;
            entryPrice = currentPrice;
            stopPrice = positionInfo.stopPrice;
            targetPrice = positionInfo.targetPrice;
            
        } catch (Exception e) {
            logger.error("Failed to place long order", e);
        }
    }
    
    private void handleShortEntry(OrderContext ctx, int index) {
        if (hasPosition) {
            logger.debug("Already have position, ignoring short entry signal");
            return;
        }
        
        DataSeries series = ctx.getDataContext().getDataSeries();
        double currentPrice = series.getClose(index);
        
        if (currentPrice <= 0) {
            logger.warn("Invalid price for short entry: {}", currentPrice);
            return;
        }
        
        // Calculate position size and risk
        PositionSize positionInfo = calculatePositionSize(ctx, index, false, currentPrice);
        if (positionInfo == null || positionInfo.quantity <= 0) {
            logger.warn("Cannot calculate valid position size for short entry");
            return;
        }
        
        try {
            // Get Trade Lots from strategy settings (Trading Options panel)
            int tradeLots = getSettings().getTradeLots();
            if (tradeLots <= 0) tradeLots = 1; // Default to 1 if not set
            
            // Calculate final quantity including Trade Lots
            int finalQuantity = positionInfo.quantity * tradeLots;
            
            if (logger.isInfoEnabled()) {
                logger.info("📊 Trade Lots Calculation:");
                logger.info("  ├─ Position Size Factor: {}", positionInfo.quantity);
                logger.info("  ├─ Trade Lots Setting: {}", tradeLots);
                logger.info("  └─ Final Order Quantity: {} ({}×{})", finalQuantity, positionInfo.quantity, tradeLots);
            }
            
            if (getSettings().getBoolean(ENABLE_BRACKET_ORDERS, true)) {
                // Place bracket order (entry + stop + target) with Trade Lots multiplied
                ctx.sell(finalQuantity);
                
                if (logger.isInfoEnabled()) {
                    logger.info("✅ SHORT BRACKET ORDER PLACED:");
                    logger.info("  ├─ Order Quantity: {} ({} × {} Trade Lots)", finalQuantity, positionInfo.quantity, tradeLots);
                    logger.info("  ├─ Entry Price: ${}", String.format("%.2f", currentPrice));
                    logger.info("  ├─ Stop Loss: ${} ({} points risk)", 
                               String.format("%.2f", positionInfo.stopPrice), 
                               String.format("%.2f", positionInfo.stopPrice - currentPrice));
                    logger.info("  └─ Target: ${} ({} points profit)", 
                               String.format("%.2f", positionInfo.targetPrice), 
                               String.format("%.2f", currentPrice - positionInfo.targetPrice));
                    
                    // Get actual point value from instrument
                    com.motivewave.platform.sdk.common.Instrument instrument = ctx.getInstrument();
                    double pointValue = 1.0;
                    if (instrument != null) {
                        pointValue = instrument.getPointValue();
                        if (pointValue <= 0) pointValue = 1.0;
                    }
                    
                    double dollarRisk = finalQuantity * Math.abs(positionInfo.stopPrice - currentPrice) * pointValue;
                    double dollarReward = finalQuantity * (currentPrice - positionInfo.targetPrice) * pointValue;
                    double riskReward = (currentPrice - positionInfo.targetPrice) / (positionInfo.stopPrice - currentPrice);
                    
                    logger.info("  - Dollar Risk: ${}", String.format("%.2f", Math.abs(dollarRisk)));
                    logger.info("  - Dollar Reward: ${}", String.format("%.2f", dollarReward));
                    logger.info("  - Risk/Reward Ratio: 1:{}", String.format("%.2f", riskReward));
                    logger.info("🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴\n");
                }
            } else {
                // Simple market order with Trade Lots multiplied
                ctx.sell(finalQuantity);
                if (logger.isInfoEnabled()) {
                    logger.info("Short market order: qty={} ({}×{} Trade Lots), price={}", 
                               finalQuantity, positionInfo.quantity, tradeLots, String.format("%.2f", currentPrice));
                }
            }
            
            // Update position tracking
            hasPosition = true;
            isLong = false;
            entryPrice = currentPrice;
            stopPrice = positionInfo.stopPrice;
            targetPrice = positionInfo.targetPrice;
            
        } catch (Exception e) {
            logger.error("Failed to place short order", e);
        }
    }
    
    private void handleFlatExit(OrderContext ctx, int index) {
        if (!hasPosition) {
            logger.debug("No position to exit");
            return;
        }
        
        try {
            // Close all positions
            ctx.cancelOrders();
            logger.info("Closed all positions on flat exit signal");
            
            // Reset position tracking
            resetPositionTracking();
            
        } catch (Exception e) {
            logger.error("Failed to close positions on flat exit", e);
        }
    }
    
    private PositionSize calculatePositionSize(OrderContext ctx, int index, boolean isLongTrade, double entryPrice) {
        if (logger.isInfoEnabled()) {
            logger.info("╔══════════════════════════════════════════════════════════╗");
            logger.info("║           POSITION SIZING CALCULATION                      ║");
            logger.info("╠══════════════════════════════════════════════════════════╣");
            logger.info("║ Direction: {:8} | Entry Price: ${:10}         ║", 
                       isLongTrade ? "LONG" : "SHORT", 
                       String.format("%.2f", entryPrice));
            logger.info("╚══════════════════════════════════════════════════════════╝");
        }
        
        DataSeries series = ctx.getDataContext().getDataSeries();
        
        // Get instrument point value from SDK
        com.motivewave.platform.sdk.common.Instrument instrument = ctx.getInstrument();
        double instrumentPointValue = 1.0; // Default fallback
        
        if (instrument != null) {
            instrumentPointValue = instrument.getPointValue();
            if (instrumentPointValue <= 0) {
                // Fallback to 1.0 if invalid
                instrumentPointValue = 1.0;
                logger.warn("Invalid point value from instrument: {}, using default 1.0", instrument.getSymbol());
            }
        }
        
        // Get base quantity from strategy settings
        // Note: MotiveWave multiplies this by Trade Lots from Trading Options panel
        // Default to 1 to respect only the Trade Lots setting
        int baseQuantity = getSettings().getInteger(POSITION_SIZE, 1);
        
        if (logger.isInfoEnabled()) {
            logger.info("📊 Position Size Factor: {} (from strategy settings)", baseQuantity);
            logger.info("   ⚠️  NOTE: This will be multiplied by Trade Lots in Trading Options panel");
            logger.info("   📌 If Trade Lots = 2 and Factor = {}, Final Quantity = {}", 
                       baseQuantity, baseQuantity * 2);
        }
        
        // Get risk parameters
        double maxRisk = getSettings().getDouble(MAX_RISK_PER_TRADE, 500.0);
        
        // Always use instrument point value from SDK
        double pointValue = instrumentPointValue;
        
        if (logger.isInfoEnabled()) {
            logger.info("Using instrument point value: ${}/point for {}", 
                       pointValue, 
                       instrument != null ? instrument.getSymbol() : "unknown");
        }
        
        boolean useWatrStops = getSettings().getBoolean(USE_WATR_STOPS, true);
        double stopMultiplier = getSettings().getDouble(STOP_MULTIPLIER, 2.0);
        double targetMultiplier = getSettings().getDouble(TARGET_MULTIPLIER, 3.0);
        
        if (logger.isInfoEnabled()) {
            logger.info("💰 Risk Management Parameters:");
            logger.info("  ├─ Position Size Factor: {} (× Trade Lots)", baseQuantity);
            logger.info("  ├─ Max Risk Per Trade: ${}", String.format("%.2f", maxRisk));
            logger.info("  ├─ Point Value: ${}/point (from SDK)", String.format("%.2f", pointValue));
            logger.info("  ├─ Use WATR Stops: {}", useWatrStops);
            logger.info("  ├─ Stop Multiplier: {}x", stopMultiplier);
            logger.info("  └─ Target Multiplier: {}x", targetMultiplier);
        }
        
        double stopPrice, targetPrice;
        
        if (useWatrStops) {
            // Use WATR-based stops
            Double watr = series.getDouble(index, Values.WATR);
            
            if (logger.isDebugEnabled()) {
                logger.debug("WATR Stop Calculation: raw WATR={}, stopMultiplier={}, targetMultiplier={}", 
                            watr != null ? String.format("%.4f", watr) : "null", 
                            stopMultiplier, targetMultiplier);
            }
            
            if (watr == null || watr <= 0) {
                // Fallback to simple percentage
                double stopDistance = entryPrice * 0.01; // 1% fallback
                stopPrice = isLongTrade ? entryPrice - stopDistance : entryPrice + stopDistance;
                targetPrice = isLongTrade ? entryPrice + stopDistance * targetMultiplier : 
                                          entryPrice - stopDistance * targetMultiplier;
                logger.warn("WATR not available, using 1% fallback");
                if (logger.isInfoEnabled()) {
                    logger.info("  - Fallback Stop Distance: {} points", String.format("%.2f", stopDistance));
                    logger.info("  - Stop Price: {}", String.format("%.2f", stopPrice));
                    logger.info("  - Target Price: {}", String.format("%.2f", targetPrice));
                }
            } else {
                // WATR needs reasonable bounds for ES
                // Get configured bounds
                double minStopPoints = getSettings().getDouble(MIN_STOP_POINTS, 5.0);
                double maxStopPoints = getSettings().getDouble(MAX_STOP_POINTS, 25.0);
                double watrDistance = watr * stopMultiplier;
                
                if (logger.isInfoEnabled()) {
                    logger.info("WATR Stop Calculation:");
                    logger.info("  - Raw WATR: {} points", String.format("%.4f", watr));
                    logger.info("  - Stop Multiplier: {}x", stopMultiplier);
                    logger.info("  - Initial WATR Distance: {} points", String.format("%.2f", watrDistance));
                }
                
                // Apply min/max bounds
                if (watrDistance < minStopPoints) {
                    logger.warn("  ⚠️  WATR distance {} too small, using minimum {} points", 
                               String.format("%.2f", watrDistance), minStopPoints);
                    watrDistance = minStopPoints;
                } else if (watrDistance > maxStopPoints) {
                    logger.warn("  ⚠️  WATR distance {} too large, capping at {} points", 
                               String.format("%.2f", watrDistance), maxStopPoints);
                    watrDistance = maxStopPoints;
                } else {
                    if (logger.isInfoEnabled()) {
                        logger.info("  - WATR distance within bounds: {} points", String.format("%.2f", watrDistance));
                    }
                }
                
                stopPrice = isLongTrade ? entryPrice - watrDistance : entryPrice + watrDistance;
                targetPrice = isLongTrade ? entryPrice + watrDistance * targetMultiplier : 
                                          entryPrice - watrDistance * targetMultiplier;
                
                if (logger.isInfoEnabled()) {
                    logger.info("  - Final WATR Distance: {} points", String.format("%.2f", watrDistance));
                    logger.info("  - Stop Price: {}", String.format("%.2f", stopPrice));
                    logger.info("  - Target Price: {}", String.format("%.2f", targetPrice));
                }
            }
        } else {
            // Use fixed percentage stops
            double stopDistance = entryPrice * 0.015; // 1.5% default
            stopPrice = isLongTrade ? entryPrice - stopDistance : entryPrice + stopDistance;
            targetPrice = isLongTrade ? entryPrice + stopDistance * targetMultiplier : 
                                      entryPrice - stopDistance * targetMultiplier;
            
            if (logger.isInfoEnabled()) {
                logger.info("Fixed Percentage Stop Calculation:");
                logger.info("  - Stop Distance: {} points (1.5% of entry)", String.format("%.2f", stopDistance));
                logger.info("  - Stop Price: {}", String.format("%.2f", stopPrice));
                logger.info("  - Target Price: {}", String.format("%.2f", targetPrice));
            }
        }
        
        // Calculate risk per share/contract
        double riskInPoints = Math.abs(entryPrice - stopPrice);
        double riskPerUnit = riskInPoints * pointValue; // Apply point value multiplier
        
        if (logger.isInfoEnabled()) {
            logger.info("📐 Risk Calculation Details:");
            logger.info("  ├─ Entry Price: ${}", String.format("%.2f", entryPrice));
            logger.info("  ├─ Stop Price: ${}", String.format("%.2f", stopPrice));
            logger.info("  ├─ Risk in Points: {} points", String.format("%.2f", riskInPoints));
            logger.info("  ├─ Point Value: ${}/point", String.format("%.2f", pointValue));
            logger.info("  └─ Risk Per Unit: ${}/contract", String.format("%.2f", riskPerUnit));
        }
        
        if (riskPerUnit <= 0) {
            logger.error("❌ Invalid risk calculation: riskInPoints={}, pointValue={}, riskPerUnit={}", 
                       riskInPoints, pointValue, riskPerUnit);
            return null;
        }
        
        // Use PositionSizer for risk-based position sizing
        PositionSizer sizer = PositionSizer.createValidated(ctx.getInstrument());
        if (sizer == null) {
            logger.error("Failed to create position sizer for instrument {}", ctx.getInstrument().getSymbol());
            // Fallback to simple calculation
            int maxQuantityByRisk = (int) (maxRisk / riskPerUnit);
            int finalQuantity = Math.min(baseQuantity, maxQuantityByRisk);
            return new PositionSize(finalQuantity, stopPrice, targetPrice);
        }
        
        // Use WATR-based or direct position calculation
        PositionSizer.PositionInfo sizerInfo;
        if (useWatrStops && series.getDouble(index, Values.WATR) != null) {
            double watr = series.getDouble(index, Values.WATR);
            double minStopPoints = getSettings().getDouble(MIN_STOP_POINTS, 5.0);
            double maxStopPoints = getSettings().getDouble(MAX_STOP_POINTS, 25.0);
            
            sizerInfo = sizer.calculatePositionWithWatr(
                baseQuantity,    // positionSizeFactor
                1,               // tradeLots (will be applied later)
                maxRisk,         // maxRiskDollars
                watr,           // watr value
                stopMultiplier,  // stopMultiplier
                minStopPoints,   // minStopPoints
                maxStopPoints    // maxStopPoints
            );
        } else {
            sizerInfo = sizer.calculatePosition(
                baseQuantity,  // positionSizeFactor
                1,             // tradeLots (will be applied later)
                maxRisk,       // maxRiskDollars
                riskInPoints   // stopDistancePoints
            );
        }
        
        int finalQuantity = sizerInfo.getFinalQuantity();
        finalQuantity = Math.max(1, finalQuantity); // Minimum 1 unit
        
        if (logger.isInfoEnabled()) {
            logger.info("🧮 Quantity Calculation:");
            logger.info("  ├─ Max Risk Allowed: ${}", String.format("%.2f", maxRisk));
            logger.info("  ├─ Risk Per Unit: ${}", String.format("%.2f", sizerInfo.getRiskPerUnit()));
            logger.info("  ├─ Max Qty by Risk: {} (${} ÷ ${})", 
                       sizerInfo.getBaseQuantity(), 
                       String.format("%.2f", maxRisk), 
                       String.format("%.2f", sizerInfo.getRiskPerUnit()));
            logger.info("  ├─ Position Size Factor: {}", baseQuantity);
            logger.info("  └─ Final Quantity: {} {}", 
                       finalQuantity,
                       sizerInfo.wasRiskAdjusted() ? "(LIMITED BY RISK)" : "(USING FACTOR)");
        }
        
        if (sizerInfo.wasRiskAdjusted()) {
            logger.warn("⚠️  POSITION SIZE REDUCED: {} → {} (risk limit exceeded)", 
                       sizerInfo.getBaseQuantity(), finalQuantity);
            logger.warn("    Original risk would be: ${}", 
                       String.format("%.2f", baseQuantity * riskPerUnit));
        }
        
        // Calculate total position metrics
        double totalRisk = finalQuantity * riskPerUnit;
        double totalReward = finalQuantity * Math.abs(targetPrice - entryPrice) * pointValue;
        double riskRewardRatio = totalReward / totalRisk;
        
        if (logger.isInfoEnabled()) {
            logger.info("📈 Final Position Metrics:");
            logger.info("  ├─ Final Quantity: {} contracts", finalQuantity);
            logger.info("  ├─ Total Dollar Risk: ${}", String.format("%.2f", totalRisk));
            logger.info("  ├─ Total Dollar Reward: ${}", String.format("%.2f", totalReward));
            logger.info("  ├─ Risk/Reward Ratio: 1:{}", String.format("%.2f", riskRewardRatio));
            logger.info("  └─ Target Price: ${}", String.format("%.2f", targetPrice));
            logger.info("╔══════════════════════════════════════════════════════════╗");
            logger.info("║ ⚠️  IMPORTANT: Final quantity will be multiplied by       ║");
            logger.info("║     Trade Lots setting from Trading Options panel        ║");
            logger.info("╚══════════════════════════════════════════════════════════╝");
        }
        
        return new PositionSize(finalQuantity, stopPrice, targetPrice);
    }
    
    private void resetPositionTracking() {
        hasPosition = false;
        isLong = false;
        entryPrice = 0.0;
        stopPrice = 0.0;
        targetPrice = 0.0;
    }
    
    // P&L tracking logic preserved in docs/REFERENCE_PNL_TRACKING.md
    // These methods don't match MotiveWave SDK interfaces and were removed
    
    /**
     * Helper class for position sizing calculations
     */
    private static class PositionSize {
        final int quantity;
        final double stopPrice;
        final double targetPrice;
        
        PositionSize(int quantity, double stopPrice, double targetPrice) {
            this.quantity = quantity;
            this.stopPrice = stopPrice;
            this.targetPrice = targetPrice;
        }
    }
}