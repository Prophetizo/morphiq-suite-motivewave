package com.morphiqlabs.wavelets.swt;

import com.motivewave.platform.sdk.common.*;
import com.morphiqlabs.common.position.PositionSizer;
import com.morphiqlabs.common.position.PositionManager;
import com.morphiqlabs.common.position.PositionTracker;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.morphiqlabs.common.LoggerConfig;
import org.slf4j.Logger;

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
    
    // Position management components
    private PositionManager positionManager;
    
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
        
        // Initialize position manager
        com.motivewave.platform.sdk.common.Instrument instrument = ctx.getInstrument();
        PositionSizer positionSizer = PositionSizer.createValidated(instrument);
        if (positionSizer == null) {
            logger.error("Failed to create position sizer for instrument: {}", instrument.getSymbol());
            return;
        }
        
        positionManager = new PositionManager(ctx, positionSizer);
        
        // Check if we already have a position
        if (positionManager.hasPosition()) {
            logger.info("Existing position detected: {}", positionManager.getCurrentPositionSide());
            // Get entry price from average entry if available
            double avgEntry = ctx.getAvgEntryPrice();
            if (avgEntry > 0) {
                logger.info("Average entry price: {}", String.format("%.2f", avgEntry));
                positionManager.getPositionTracker().updateEntryPrice(avgEntry);
            }
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
        if (positionManager != null && positionManager.hasPosition()) {
            logger.info("Closing position on deactivation: {}", positionManager.getCurrentPositionSide());
            positionManager.exitPosition();
        }
    }
    
    /**
     * Called when an order is filled.
     * Update position tracking state and detect stop hits.
     */
    @Override
    public void onOrderFilled(OrderContext ctx, com.motivewave.platform.sdk.order_mgmt.Order order) {
        if (positionManager != null) {
            positionManager.onOrderFilled(order);
        } else {
            logger.warn("Position manager not initialized, cannot handle order fill");
        }
    }
    
    /**
     * Called when a position is closed.
     * Reset position tracking state.
     */
    @Override
    public void onPositionClosed(OrderContext ctx) {
        logger.info("Position closed");
        if (positionManager != null) {
            positionManager.getPositionTracker().reset();
        }
    }
    
    // Test helper methods - package private for testing
    double getEntryPrice() { 
        return positionManager != null ? positionManager.getPositionTracker().getEntryPrice() : 0.0; 
    }
    void setEntryPrice(double entryPrice) { 
        if (positionManager != null) {
            positionManager.getPositionTracker().updateEntryPrice(entryPrice);
        }
    }
    double getStopPriceValue() { 
        return positionManager != null ? positionManager.getPositionTracker().getStopPrice() : 0.0; 
    }
    void setStopPrice(double stopPrice) { 
        if (positionManager != null && positionManager.hasPosition()) {
            // Use PositionManager to modify the stop price
            boolean modified = positionManager.modifyStopPrice(stopPrice);
            if (modified) {
                logger.debug("Stop price modified to: {}", String.format("%.2f", stopPrice));
            } else {
                logger.warn("Failed to modify stop price to: {}", String.format("%.2f", stopPrice));
            }
        } else {
            // Update position tracker directly if no active position
            if (positionManager != null) {
                positionManager.getPositionTracker().updateStopPrice(stopPrice);
                logger.debug("Stop price updated in tracker to: {}", String.format("%.2f", stopPrice));
            } else {
                logger.warn("Cannot set stop price - position manager not initialized");
            }
        }
    }
    double getTargetPriceValue() {
        return positionManager != null ? positionManager.getPositionTracker().getTargetPrice() : 0.0;
    }
    void setTargetPriceValue(double targetPrice) {
        if (positionManager != null && positionManager.hasPosition()) {
            // Use PositionManager to modify the target price
            boolean modified = positionManager.modifyTargetPrice(targetPrice);
            if (modified) {
                logger.debug("Target price modified to: {}", String.format("%.2f", targetPrice));
            } else {
                logger.warn("Failed to modify target price to: {}", String.format("%.2f", targetPrice));
            }
        } else {
            // Update position tracker directly if no active position
            if (positionManager != null) {
                positionManager.getPositionTracker().updateTargetPrice(targetPrice);
                logger.debug("Target price updated in tracker to: {}", String.format("%.2f", targetPrice));
            } else {
                logger.warn("Cannot set target price - position manager not initialized");
            }
        }
    }
    public Object getBufferLock() { 
        // Return the actual bufferLock from parent class for thread-safe test access
        return bufferLock;
    }
    int calculateFinalQuantity(OrderContext ctx) { 
        int positionSizeFactor = getSettings().getInteger(POSITION_SIZE, 1);
        // Get trade lots from settings instead of OrderContext
        int tradeLots = getSettings().getInteger("TRADE_LOTS", 1);
        return calculatePositionSize(positionSizeFactor, tradeLots);
    }
    
    /**
     * Calculate the final position size by multiplying position size factor by trade lots.
     * Extracted as a static method for easy unit testing without SDK dependencies.
     * 
     * @param positionSizeFactor base position size from settings
     * @param tradeLots multiplier for position size
     * @return final position size
     */
    static int calculatePositionSize(int positionSizeFactor, int tradeLots) {
        return positionSizeFactor * tradeLots;
    }
    
    /**
     * Handle trading signals generated by the study.
     * This is called when the study generates a signal via ctx.signal().
     */
    @Override
    public void onSignal(OrderContext ctx, Object signal) {
        // Check if signals are enabled
        boolean signalsEnabled = getSettings().getBoolean(ENABLE_SIGNALS, true);
        if (!signalsEnabled) {
            if (logger.isTraceEnabled()) {
                logger.trace("Signal received but signals disabled: {}", signal);
            }
            return;
        }
        
        // Log signal reception
        if (logger.isDebugEnabled()) {
            logger.debug("=== SIGNAL RECEIVED ===");
            logger.debug("Signal type: {}", signal != null ? signal.getClass().getSimpleName() : "null");
            logger.debug("Signal value: {}", signal);
            logger.debug("Current position: {}", hasPosition(ctx) ? (isLong(ctx) ? "LONG" : "SHORT") : "FLAT");
        }
        
        try {
            DataSeries series = ctx.getDataContext().getDataSeries();
            int index = series.size() - 1;
            double currentPrice = series.getClose(index);
            
            // Log market context
            if (logger.isDebugEnabled()) {
                logger.debug("Market context - Index: {}, Price: {}, Time: {}", 
                    index, 
                    String.format("%.2f", currentPrice),
                    series.getStartTime(index));
            }
            
            // Log position status periodically
            logPositionStatus(ctx, series, index);
            
            if (signal instanceof Signals) {
                Signals swtSignal = (Signals) signal;
                
                // Log signal processing
                logger.info("Processing {} signal at price {}", 
                    swtSignal, 
                    String.format("%.2f", currentPrice));
                
                switch (swtSignal) {
                    case LONG:
                        if (logger.isInfoEnabled()) {
                            logger.info("LONG state signal - Current position: {}", 
                                hasPosition(ctx) ? (isLong(ctx) ? "Already LONG" : "SHORT") : "FLAT");
                        }
                        // Strategy decides whether to enter based on state
                        if (!hasPosition(ctx) || isShort(ctx)) {
                            handleLongEntry(ctx, index);
                        }
                        break;
                    case SHORT:
                        if (logger.isInfoEnabled()) {
                            logger.info("SHORT state signal - Current position: {}", 
                                hasPosition(ctx) ? (isLong(ctx) ? "LONG" : "Already SHORT") : "FLAT");
                        }
                        // Strategy decides whether to enter based on state
                        if (!hasPosition(ctx) || isLong(ctx)) {
                            handleShortEntry(ctx, index);
                        }
                        break;
                    default:
                        logger.warn("Unknown signal type: {}", swtSignal);
                }
                
                // Log post-signal state
                if (logger.isDebugEnabled()) {
                    logger.debug("Post-signal position: {}", 
                        hasPosition(ctx) ? (isLong(ctx) ? "LONG" : "SHORT") : "FLAT");
                }
            } else {
                logger.warn("Received non-Signals type: {} ({})", 
                    signal, 
                    signal != null ? signal.getClass().getName() : "null");
            }
        } catch (Exception e) {
            logger.error("Error handling signal: {} - Exception: {}", signal, e.getMessage(), e);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("=== SIGNAL PROCESSING COMPLETE ===");
            }
        }
    }
    
    private void logPositionStatus(OrderContext ctx, DataSeries series, int index) {
        if (!hasPosition(ctx) || positionManager == null) {
            return;
        }
        
        // Only log every 100 bars to avoid spam
        if (index % 100 != 0) {
            return;
        }
        
        double currentPrice = series.getClose(index);
        if (currentPrice <= 0) {
            return;
        }
        
        PositionTracker tracker = positionManager.getPositionTracker();
        double entryPrice = tracker.getEntryPrice();
        if (entryPrice <= 0) {
            return;
        }
        
        int positionSize = positionManager.getCurrentPosition();
        double unrealizedPnL = tracker.calculateUnrealizedPnL(currentPrice, positionSize);
        double stopDistance = tracker.getStopDistance(currentPrice);
        double targetDistance = tracker.getTargetDistance(currentPrice);
        
        String status = unrealizedPnL >= 0 ? "PROFIT" : "LOSS";
        
        if (logger.isDebugEnabled()) {
            logger.debug("Position Status [{}]: Price={}, P&L=${}, Stop Distance={}, Target Distance={}",
                        status, 
                        String.format("%.2f", currentPrice), 
                        String.format("%.2f", unrealizedPnL), 
                        String.format("%.2f", stopDistance), 
                        String.format("%.2f", targetDistance));
        }
        
        // Warn if getting close to stop (within 20% of total risk)
        if (tracker.isNearStop(currentPrice, 0.2)) {
            logger.warn("⚠️ WARNING: Position near stop! Current: {}, Stop: {}, Distance: {}",
                       String.format("%.2f", currentPrice), 
                       String.format("%.2f", tracker.getStopPrice()), 
                       String.format("%.2f", stopDistance));
        }
    }
    
    private void handleLongEntry(OrderContext ctx, int index) {
        if (logger.isDebugEnabled()) {
            logger.debug(">>> Entering handleLongEntry - Index: {}, HasPosition: {}", index, hasPosition(ctx));
        }

        if (positionManager == null) {
            logger.error("Position manager not initialized");
            return;
        }

        // If we have a short position, reverse to long
        if (hasPosition(ctx) && isShort(ctx)) {
            logger.info("Long signal received while SHORT - reversing to long position");
            DataSeries series = ctx.getDataContext().getDataSeries();
            double currentPrice = series.getClose(index);
            
            PositionSize positionInfo = calculatePositionSize(ctx, index, true, currentPrice);
            if (positionInfo != null && positionInfo.quantity > 0) {
                int tradeLots = getSettings().getTradeLots();
                if (tradeLots <= 0) tradeLots = 1;
                int finalQuantity = positionInfo.quantity * tradeLots;
                
                positionManager.reversePosition(currentPrice, positionInfo.stopPrice, positionInfo.targetPrice, finalQuantity);
            }
            return;
        }

        // If we already have a long position, return
        if (hasPosition(ctx) && isLong(ctx)) {
            logger.info("Already have LONG position, ignoring long entry signal");
            return;
        }

        DataSeries series = ctx.getDataContext().getDataSeries();
        double currentPrice = series.getClose(index);

        if (logger.isDebugEnabled()) {
            logger.debug("Long entry context - Price: {}, Bar: {}/{}",
                    String.format("%.2f", currentPrice), index, series.size());
        }

        if (currentPrice <= 0) {
            logger.error("Invalid price for long entry: {} at index {}", currentPrice, index);
            return;
        }

        // Calculate position size and risk using PositionSizer
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

            // Use PositionManager to enter long position
            PositionManager.PositionInfo result = positionManager.enterLong(
                currentPrice, positionInfo.stopPrice, positionInfo.targetPrice, finalQuantity);
            
            if (result != null) {
                logger.info("✅ LONG POSITION ENTERED via PositionManager");
            } else {
                logger.error("Failed to enter long position via PositionManager");
            }

        } catch (Exception e) {
            logger.error("Failed to place long order", e);
        }
    }
    
    private void handleShortEntry(OrderContext ctx, int index) {
        if (logger.isDebugEnabled()) {
            logger.debug(">>> Entering handleShortEntry - Index: {}, HasPosition: {}", index, hasPosition(ctx));
        }

        if (positionManager == null) {
            logger.error("Position manager not initialized");
            return;
        }
        
        // If we have a long position, reverse to short
        if (hasPosition(ctx) && isLong(ctx)) {
            logger.info("Short signal received while LONG - reversing to short position");
            DataSeries series = ctx.getDataContext().getDataSeries();
            double currentPrice = series.getClose(index);
            
            PositionSize positionInfo = calculatePositionSize(ctx, index, false, currentPrice);
            if (positionInfo != null && positionInfo.quantity > 0) {
                int tradeLots = getSettings().getTradeLots();
                if (tradeLots <= 0) tradeLots = 1;
                int finalQuantity = positionInfo.quantity * tradeLots;
                
                positionManager.reversePosition(currentPrice, positionInfo.stopPrice, positionInfo.targetPrice, finalQuantity);
            }
            return;
        }
        
        // If we already have a short position, return
        if (hasPosition(ctx) && isShort(ctx)) {
            logger.info("Already have SHORT position, ignoring short entry signal");
            return;
        }
        
        DataSeries series = ctx.getDataContext().getDataSeries();
        double currentPrice = series.getClose(index);
        
        if (logger.isDebugEnabled()) {
            logger.debug("Short entry context - Price: {}, Bar: {}/{}", 
                String.format("%.2f", currentPrice), index, series.size());
        }
        
        if (currentPrice <= 0) {
            logger.error("Invalid price for short entry: {} at index {}", currentPrice, index);
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
            
            // Use PositionManager to enter short position
            PositionManager.PositionInfo result = positionManager.enterShort(
                currentPrice, positionInfo.stopPrice, positionInfo.targetPrice, finalQuantity);
            
            if (result != null) {
                logger.info("✅ SHORT POSITION ENTERED via PositionManager");
            } else {
                logger.error("Failed to enter short position via PositionManager");
            }
            
        } catch (Exception e) {
            logger.error("Failed to place short order", e);
        }
    }
    
    private void handleFlatExit(OrderContext ctx, int index) {
        if (logger.isDebugEnabled()) {
            logger.debug(">>> Entering handleFlatExit - Index: {}, HasPosition: {}", index, hasPosition(ctx));
        }
        
        if (positionManager == null) {
            logger.error("Position manager not initialized");
            return;
        }
        
        if (!hasPosition(ctx)) {
            logger.info("No position to exit - already flat");
            return;
        }
        
        // Use PositionManager to exit position
        boolean success = positionManager.exitPosition();
        
        if (success) {
            logger.info("Position successfully exited via PositionManager");
        } else {
            logger.error("Failed to exit position via PositionManager");
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
    
    /**
     * Get current position from OrderContext
     * @return positive for long, negative for short, 0 for flat
     */
    int getPosition(OrderContext ctx) {
        return positionManager != null ? positionManager.getCurrentPosition() : ctx.getPosition();
    }
    
    /**
     * Check if we have any position
     */
    boolean hasPosition(OrderContext ctx) {
        return positionManager != null ? positionManager.hasPosition() : ctx.getPosition() != 0;
    }
    
    /**
     * Check if current position is long
     */
    boolean isLong(OrderContext ctx) {
        return positionManager != null ? positionManager.isLong() : ctx.getPosition() > 0;
    }
    
    /**
     * Check if current position is short
     */
    boolean isShort(OrderContext ctx) {
        return positionManager != null ? positionManager.isShort() : ctx.getPosition() < 0;
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