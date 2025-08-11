package com.prophetizo.wavelets.swt;

import com.motivewave.platform.sdk.common.*;
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
        requiresBarUpdates = false
)
public class SwtTrendMomentumStrategy extends SwtTrendMomentumStudy {
    private static final Logger logger = LoggerConfig.getLogger(SwtTrendMomentumStrategy.class);
    
    // Additional strategy settings
    public static final String POSITION_SIZE = "POSITION_SIZE";
    public static final String USE_WATR_STOPS = "USE_WATR_STOPS";
    public static final String STOP_MULTIPLIER = "STOP_MULTIPLIER";
    public static final String TARGET_MULTIPLIER = "TARGET_MULTIPLIER";
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
        positionGroup.addRow(new IntegerDescriptor(POSITION_SIZE, "Position Size (shares/contracts)", 100, 1, 10000, 1));
        positionGroup.addRow(new DoubleDescriptor(MAX_RISK_PER_TRADE, "Max Risk Per Trade ($)", 500.0, 50.0, 5000.0, 50.0));
        
        SettingGroup riskGroup = strategyTab.addGroup("Risk Management");
        riskGroup.addRow(new BooleanDescriptor(USE_WATR_STOPS, "Use WATR-based Stops", true));
        riskGroup.addRow(new DoubleDescriptor(STOP_MULTIPLIER, "Stop Loss Multiplier", 2.0, 1.0, 5.0, 0.1));
        riskGroup.addRow(new DoubleDescriptor(TARGET_MULTIPLIER, "Target Multiplier", 3.0, 1.5, 10.0, 0.5));
        riskGroup.addRow(new BooleanDescriptor(ENABLE_BRACKET_ORDERS, "Enable Bracket Orders", true));
        
        // Mark as strategy
        setStrategy(true);
        
        logger.info("SWT Strategy initialized with risk management settings");
    }
    
    @Override
    public void onSignal(OrderContext ctx, Object signal) {
        if (!getSettings().getBoolean(ENABLE_SIGNALS, true)) {
            return;
        }
        
        try {
            DataSeries series = ctx.getDataSeries();
            int index = series.size() - 1;
            
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
    
    private void handleLongEntry(OrderContext ctx, int index) {
        if (hasPosition) {
            logger.debug("Already have position, ignoring long entry signal");
            return;
        }
        
        DataSeries series = ctx.getDataSeries();
        double currentPrice = series.getClose(index);
        
        if (currentPrice <= 0) {
            logger.warn("Invalid price for long entry: {}", currentPrice);
            return;
        }
        
        // Calculate position size and risk
        PositionSize positionInfo = calculatePositionSize(ctx, index, true, currentPrice);
        if (positionInfo == null || positionInfo.quantity <= 0) {
            logger.warn("Cannot calculate valid position size for long entry");
            return;
        }
        
        try {
            if (getSettings().getBoolean(ENABLE_BRACKET_ORDERS, true)) {
                // Place bracket order (entry + stop + target)
                ctx.buy(positionInfo.quantity, positionInfo.stopPrice, positionInfo.targetPrice);
                logger.info("Long bracket order: qty={}, entry={:.2f}, stop={:.2f}, target={:.2f}",
                           positionInfo.quantity, currentPrice, positionInfo.stopPrice, positionInfo.targetPrice);
            } else {
                // Simple market order
                ctx.buy(positionInfo.quantity);
                logger.info("Long market order: qty={}, price={:.2f}", positionInfo.quantity, currentPrice);
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
        
        DataSeries series = ctx.getDataSeries();
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
            if (getSettings().getBoolean(ENABLE_BRACKET_ORDERS, true)) {
                // Place bracket order (entry + stop + target)
                ctx.sell(positionInfo.quantity, positionInfo.stopPrice, positionInfo.targetPrice);
                logger.info("Short bracket order: qty={}, entry={:.2f}, stop={:.2f}, target={:.2f}",
                           positionInfo.quantity, currentPrice, positionInfo.stopPrice, positionInfo.targetPrice);
            } else {
                // Simple market order
                ctx.sell(positionInfo.quantity);
                logger.info("Short market order: qty={}, price={:.2f}", positionInfo.quantity, currentPrice);
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
            ctx.closeAllPositions();
            logger.info("Closed all positions on flat exit signal");
            
            // Reset position tracking
            resetPositionTracking();
            
        } catch (Exception e) {
            logger.error("Failed to close positions on flat exit", e);
        }
    }
    
    private PositionSize calculatePositionSize(OrderContext ctx, int index, boolean isLongTrade, double entryPrice) {
        DataSeries series = ctx.getDataSeries();
        
        // Get risk parameters
        int baseQuantity = getSettings().getInteger(POSITION_SIZE, 100);
        double maxRisk = getSettings().getDouble(MAX_RISK_PER_TRADE, 500.0);
        boolean useWatrStops = getSettings().getBoolean(USE_WATR_STOPS, true);
        double stopMultiplier = getSettings().getDouble(STOP_MULTIPLIER, 2.0);
        double targetMultiplier = getSettings().getDouble(TARGET_MULTIPLIER, 3.0);
        
        double stopPrice, targetPrice;
        
        if (useWatrStops) {
            // Use WATR-based stops
            Double watr = series.getDouble(index, Values.WATR);
            if (watr == null || watr <= 0) {
                // Fallback to simple percentage
                double stopDistance = entryPrice * 0.01; // 1% fallback
                stopPrice = isLongTrade ? entryPrice - stopDistance : entryPrice + stopDistance;
                targetPrice = isLongTrade ? entryPrice + stopDistance * targetMultiplier : 
                                          entryPrice - stopDistance * targetMultiplier;
            } else {
                double watrDistance = watr * stopMultiplier;
                stopPrice = isLongTrade ? entryPrice - watrDistance : entryPrice + watrDistance;
                targetPrice = isLongTrade ? entryPrice + watrDistance * targetMultiplier : 
                                          entryPrice - watrDistance * targetMultiplier;
            }
        } else {
            // Use fixed percentage stops
            double stopDistance = entryPrice * 0.015; // 1.5% default
            stopPrice = isLongTrade ? entryPrice - stopDistance : entryPrice + stopDistance;
            targetPrice = isLongTrade ? entryPrice + stopDistance * targetMultiplier : 
                                      entryPrice - stopDistance * targetMultiplier;
        }
        
        // Calculate risk per share/contract
        double riskPerUnit = Math.abs(entryPrice - stopPrice);
        
        if (riskPerUnit <= 0) {
            logger.warn("Invalid risk calculation: riskPerUnit={}", riskPerUnit);
            return null;
        }
        
        // Adjust quantity based on risk
        int maxQuantityByRisk = (int) (maxRisk / riskPerUnit);
        int finalQuantity = Math.min(baseQuantity, maxQuantityByRisk);
        finalQuantity = Math.max(1, finalQuantity); // Minimum 1 unit
        
        logger.debug("Position sizing: entry={:.2f}, stop={:.2f}, target={:.2f}, riskPerUnit={:.2f}, qty={}",
                    entryPrice, stopPrice, targetPrice, riskPerUnit, finalQuantity);
        
        return new PositionSize(finalQuantity, stopPrice, targetPrice);
    }
    
    private void resetPositionTracking() {
        hasPosition = false;
        isLong = false;
        entryPrice = 0.0;
        stopPrice = 0.0;
        targetPrice = 0.0;
    }
    
    @Override
    public void onActivate(OrderContext ctx) {
        super.onActivate(ctx);
        logger.debug("SWT Strategy activated");
    }
    
    @Override
    public void onDeactivate(OrderContext ctx) {
        logger.debug("SWT Strategy deactivated");
        
        try {
            // Close all positions when strategy is deactivated
            if (hasPosition) {
                ctx.closeAllPositions();
                logger.info("Closed all positions on strategy deactivation");
            }
        } catch (Exception e) {
            logger.error("Error closing positions on deactivation", e);
        }
        
        resetPositionTracking();
    }
    
    @Override
    public void onPositionOpened(OrderContext ctx, Instrument instrument, boolean isLong, float quantity, double avgPrice) {
        logger.info("Position opened: {} {} shares/contracts at {:.2f}", 
                   isLong ? "Long" : "Short", quantity, avgPrice);
        
        // Update tracking
        hasPosition = true;
        this.isLong = isLong;
        entryPrice = avgPrice;
    }
    
    @Override
    public void onPositionClosed(OrderContext ctx, Instrument instrument, boolean isLong, float quantity, double avgPrice) {
        logger.info("Position closed: {} {} shares/contracts at {:.2f}", 
                   isLong ? "Long" : "Short", quantity, avgPrice);
        
        // Calculate P&L
        if (hasPosition && entryPrice > 0) {
            double pnl = isLong ? (avgPrice - entryPrice) * quantity : (entryPrice - avgPrice) * quantity;
            logger.info("Trade P&L: ${:.2f}", pnl);
        }
        
        // Reset tracking
        resetPositionTracking();
    }
    
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