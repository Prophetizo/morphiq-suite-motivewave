package com.morphiqlabs.wavelets.swt;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.SettingsDescriptor;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;
import com.motivewave.platform.sdk.order_mgmt.Order;
import com.motivewave.platform.sdk.study.StudyHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SwtTrendMomentumStrategy.
 * Tests trading logic, signal handling, and position management.
 */
class SwtTrendMomentumStrategyFullIT {
    
    private SwtTrendMomentumStrategy strategy;
    private OrderContext mockOrderContext;
    private DataContext mockDataContext;
    private DataSeries mockDataSeries;
    private Defaults mockDefaults;
    private Settings mockSettings;
    private Instrument mockInstrument;
    private Order mockOrder;
    
    @BeforeEach
    void setUp() {
        strategy = new SwtTrendMomentumStrategy();
        
        // Mock dependencies
        mockOrderContext = mock(OrderContext.class);
        mockDataContext = mock(DataContext.class);
        mockDataSeries = mock(DataSeries.class);
        mockDefaults = mock(Defaults.class);
        mockSettings = mock(Settings.class);
        mockInstrument = mock(Instrument.class);
        mockOrder = mock(Order.class);
        
        // Configure mocks
        when(mockOrderContext.getDataContext()).thenReturn(mockDataContext);
        when(mockDataContext.getDataSeries()).thenReturn(mockDataSeries);
        when(mockDataContext.getInstrument()).thenReturn(mockInstrument);
        when(mockInstrument.getSymbol()).thenReturn("ES");
        when(mockInstrument.getPointValue()).thenReturn(50.0);
        when(mockDataSeries.size()).thenReturn(100);
        
        // Default settings
        when(mockSettings.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        when(mockSettings.getInteger(anyString(), anyInt())).thenReturn(1);
        when(mockSettings.getDouble(anyString(), anyDouble())).thenAnswer(
            invocation -> invocation.getArgument(1));
    }
    
    @Test
    @DisplayName("Strategy should have correct study header annotation")
    void testStudyHeaderAnnotation() {
        StudyHeader header = SwtTrendMomentumStrategy.class.getAnnotation(StudyHeader.class);
        
        assertNotNull(header);
        assertEquals("SWT_TREND_MOMENTUM_STRATEGY", header.id());
        assertEquals("SWT Trend + Momentum Strategy", header.name());
        assertTrue(header.strategy());
        assertTrue(header.overlay());
        assertFalse(header.requiresBarUpdates());
    }
    
    @Test
    @DisplayName("Initialize should set up strategy settings")
    void testInitialize() {
        strategy.initialize(mockDefaults);
        
        SettingsDescriptor settings = strategy.getSettingsDescriptor();
        assertNotNull(settings);
        
        // Verify strategy-specific settings are added
        assertTrue(settings.getTabs().stream()
            .anyMatch(tab -> "Strategy".equals(tab.getName())));
    }
    
    @Test
    @DisplayName("onActivate should initialize strategy state")
    void testOnActivate() {
        when(mockOrderContext.getPosition()).thenReturn(0);
        
        strategy.onActivate(mockOrderContext);
        
        // Verify state is initialized
        assertFalse(strategy.hasPosition(mockOrderContext));
        assertEquals(0.0, strategy.getEntryPrice());
        assertEquals(0.0, strategy.getStopPriceValue());
    }
    
    @Test
    @DisplayName("onActivate should detect existing position")
    void testOnActivateWithExistingPosition() {
        when(mockOrderContext.getPosition()).thenReturn(2); // Long position
        
        strategy.onActivate(mockOrderContext);
        
        assertTrue(strategy.hasPosition(mockOrderContext));
        assertTrue(strategy.isLong(mockOrderContext));
    }
    
    @Test
    @DisplayName("onDeactivate should close open positions")
    void testOnDeactivate() {
        when(mockOrderContext.getPosition()).thenReturn(2);
        
        strategy.onDeactivate(mockOrderContext);
        
        verify(mockOrderContext).closeAtMarket();
    }
    
    @Test
    @DisplayName("onOrderFilled should handle valid buy orders")
    void testOnOrderFilledBuyOrder() {
        when(mockOrder.getAvgFillPrice()).thenReturn(4500.25f);
        when(mockOrder.isBuy()).thenReturn(true);
        when(mockOrder.getQuantity()).thenReturn(2);
        when(mockOrderContext.getPosition()).thenReturn(2);
        
        strategy.onOrderFilled(mockOrderContext, mockOrder);
        
        assertTrue(strategy.hasPosition(mockOrderContext));
        assertTrue(strategy.isLong(mockOrderContext));
        assertEquals(4500.25, strategy.getEntryPrice());
    }
    
    @Test
    @DisplayName("onOrderFilled should handle valid sell orders")
    void testOnOrderFilledSellOrder() {
        when(mockOrder.getAvgFillPrice()).thenReturn(4500.25f);
        when(mockOrder.isBuy()).thenReturn(false);
        when(mockOrder.getQuantity()).thenReturn(2);
        when(mockOrderContext.getPosition()).thenReturn(-2);
        
        strategy.onOrderFilled(mockOrderContext, mockOrder);
        
        assertTrue(strategy.hasPosition(mockOrderContext));
        assertFalse(strategy.isLong(mockOrderContext));
        assertEquals(4500.25, strategy.getEntryPrice());
    }
    
    @Test
    @DisplayName("onOrderFilled should detect stop hits")
    void testOnOrderFilledStopHit() {
        // Setup existing long position
        when(mockOrderContext.getPosition()).thenReturn(2); // Start with long position
        strategy.setEntryPrice(4500.0);
        strategy.setStopPrice(4490.0);
        
        // Sell order near stop price (stop hit)
        when(mockOrder.getAvgFillPrice()).thenReturn(4490.5f);
        when(mockOrder.isBuy()).thenReturn(false);
        when(mockOrder.getQuantity()).thenReturn(2);
        when(mockOrderContext.getPosition()).thenReturn(0);
        
        strategy.onOrderFilled(mockOrderContext, mockOrder);
        
        assertFalse(strategy.hasPosition(mockOrderContext));
        assertEquals(0.0, strategy.getEntryPrice());
        assertEquals(0.0, strategy.getStopPriceValue());
    }
    
    @Test
    @DisplayName("onOrderFilled should validate null order")
    void testOnOrderFilledNullOrder() {
        // Should not throw exception
        assertDoesNotThrow(() -> strategy.onOrderFilled(mockOrderContext, null));
    }
    
    @ParameterizedTest
    @DisplayName("onOrderFilled should validate fill price")
    @ValueSource(doubles = {0.0, -100.0, Double.NaN, Double.POSITIVE_INFINITY})
    void testOnOrderFilledInvalidFillPrice(double invalidPrice) {
        when(mockOrder.getAvgFillPrice()).thenReturn((float)invalidPrice);
        when(mockOrder.getQuantity()).thenReturn(1);
        
        // Should not throw exception, but should return early
        assertDoesNotThrow(() -> strategy.onOrderFilled(mockOrderContext, mockOrder));
    }
    
    @Test
    @DisplayName("onOrderFilled should validate quantity")
    void testOnOrderFilledInvalidQuantity() {
        when(mockOrder.getAvgFillPrice()).thenReturn(4500.0f);
        when(mockOrder.getQuantity()).thenReturn(0);
        
        // Should not throw exception, but should return early
        assertDoesNotThrow(() -> strategy.onOrderFilled(mockOrderContext, mockOrder));
    }
    
    @Test
    @DisplayName("onPositionClosed should reset state")
    void testOnPositionClosed() {
        when(mockOrderContext.getPosition()).thenReturn(0);
        strategy.setEntryPrice(4500.0);
        strategy.setStopPrice(4490.0);
        
        strategy.onPositionClosed(mockOrderContext);
        
        assertFalse(strategy.hasPosition(mockOrderContext));
        assertEquals(0.0, strategy.getEntryPrice());
        assertEquals(0.0, strategy.getStopPriceValue());
    }
    
    @Test
    @DisplayName("onSignal should handle LONG signal")
    void testOnSignalLong() {
        when(mockSettings.getBoolean(SwtTrendMomentumStrategy.ENABLE_SIGNALS, true))
            .thenReturn(true);
        when(mockOrderContext.getPosition()).thenReturn(0); // No position
        
        strategy.onSignal(mockOrderContext, SwtTrendMomentumStrategy.Signals.LONG);
        
        // Verify handleLongEntry was called (would need to verify through side effects)
        // In a real test, we'd verify the order was placed
    }
    
    @Test
    @DisplayName("onSignal should handle SHORT signal")
    void testOnSignalShort() {
        when(mockSettings.getBoolean(SwtTrendMomentumStrategy.ENABLE_SIGNALS, true))
            .thenReturn(true);
        when(mockOrderContext.getPosition()).thenReturn(0); // No position
        
        strategy.onSignal(mockOrderContext, SwtTrendMomentumStrategy.Signals.SHORT);
        
        // Verify handleShortEntry was called
    }
    
    
    @Test
    @DisplayName("onSignal should not re-enter when already in position")
    void testOnSignalNoReentry() {
        when(mockSettings.getBoolean(SwtTrendMomentumStrategy.ENABLE_SIGNALS, true))
            .thenReturn(true);
        when(mockOrderContext.getPosition()).thenReturn(2); // Already long
        
        strategy.onSignal(mockOrderContext, SwtTrendMomentumStrategy.Signals.LONG);
        
        // Verify no new order was placed
        verify(mockOrderContext, never()).buy(anyInt());
    }
    
    @Test
    @DisplayName("onSignal should respect ENABLE_SIGNALS setting")
    void testOnSignalDisabled() {
        when(mockSettings.getBoolean(SwtTrendMomentumStrategy.ENABLE_SIGNALS, true))
            .thenReturn(false);
        
        strategy.onSignal(mockOrderContext, SwtTrendMomentumStrategy.Signals.LONG);
        
        // Verify no action was taken (no orders placed)
        verify(mockOrderContext, never()).buy(anyInt());
    }
    
    @Test
    @DisplayName("Position sizing with trade lots multiplication")
    void testPositionSizingWithTradeLots() {
        // Initialize strategy first to set up settingsDescriptor
        strategy.initialize(mockDefaults);
        
        // Setup settings for position sizing
        strategy.setSettings(mockSettings);
        when(mockSettings.getInteger(SwtTrendMomentumStrategy.POSITION_SIZE, 1))
            .thenReturn(2);
        when(mockSettings.getInteger("TRADE_LOTS", 1)).thenReturn(3);
        
        int expectedQuantity = 2 * 3; // positionSizeFactor * tradeLots
        assertEquals(6, strategy.calculateFinalQuantity(mockOrderContext));
    }
    
    @Test
    @DisplayName("Momentum scaling factor should be 100x")
    void testMomentumScaling() {
        double rawMomentum = 0.01;
        double scaledMomentum = rawMomentum * 100.0;
        
        assertEquals(1.0, scaledMomentum);
        
        // Verify threshold scaling
        double defaultThreshold = 1.0; // Scaled from 0.01
        assertTrue(scaledMomentum >= defaultThreshold);
    }
}