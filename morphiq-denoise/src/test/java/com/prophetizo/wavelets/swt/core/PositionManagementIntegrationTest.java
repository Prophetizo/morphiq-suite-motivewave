package com.prophetizo.wavelets.swt.core;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.order_mgmt.Order;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for position management components working together.
 * Tests the interaction between PositionManager, PositionTracker, and PositionSizer.
 */
class PositionManagementIntegrationTest {
    
    private OrderContext mockOrderContext;
    private Instrument mockInstrument;
    private DataContext mockDataContext;
    private DataSeries mockDataSeries;
    private PositionSizer positionSizer;
    private PositionManager positionManager;
    
    @BeforeEach
    void setUp() {
        mockOrderContext = mock(OrderContext.class);
        mockInstrument = mock(Instrument.class);
        mockDataContext = mock(DataContext.class);
        mockDataSeries = mock(DataSeries.class);
        
        // Setup basic mocks
        when(mockOrderContext.getInstrument()).thenReturn(mockInstrument);
        when(mockOrderContext.getDataContext()).thenReturn(mockDataContext);
        when(mockDataContext.getDataSeries()).thenReturn(mockDataSeries);
        when(mockInstrument.getSymbol()).thenReturn("ES");
        when(mockInstrument.getPointValue()).thenReturn(50.0);
        when(mockDataSeries.size()).thenReturn(100);
        when(mockDataSeries.getClose(99)).thenReturn(4500.0f);
        
        // Initially flat position
        when(mockOrderContext.getPosition()).thenReturn(0);
        
        positionSizer = new PositionSizer(mockInstrument);
        positionManager = new PositionManager(mockOrderContext, positionSizer);
    }
    
    @Test
    @DisplayName("Complete long position workflow should work correctly")
    void testCompleteLongPositionWorkflow() {
        // Verify initial state
        assertFalse(positionManager.hasPosition());
        assertEquals("FLAT", positionManager.getCurrentPositionSide());
        assertFalse(positionManager.getPositionTracker().hasPosition());
        
        // Setup order mocks for long entry
        Order mockMarketOrder = mock(Order.class);
        Order mockStopOrder = mock(Order.class);
        Order mockTargetOrder = mock(Order.class);
        
        when(mockOrderContext.createMarketOrder(any(), any(), eq(Enums.OrderAction.BUY), eq(2)))
            .thenReturn(mockMarketOrder);
        when(mockOrderContext.createStopOrder(any(), any(), eq(Enums.OrderAction.SELL), 
                                             any(), eq(2), anyFloat()))
            .thenReturn(mockStopOrder);
        when(mockOrderContext.createLimitOrder(any(), any(), eq(Enums.OrderAction.SELL), 
                                              any(), eq(2), anyFloat()))
            .thenReturn(mockTargetOrder);
        
        // Enter long position
        PositionManager.PositionInfo entryResult = positionManager.enterLong(4500.0, 4490.0, 4530.0, 2);
        
        assertNotNull(entryResult);
        assertEquals(4500.0, entryResult.getEntryPrice());
        assertEquals(4490.0, entryResult.getStopPrice());
        assertEquals(4530.0, entryResult.getTargetPrice());
        assertEquals(2, entryResult.getQuantity());
        assertTrue(entryResult.isLong());
        
        // Verify orders were submitted
        verify(mockOrderContext).submitOrders(mockMarketOrder, mockStopOrder, mockTargetOrder);
        
        // Verify position tracker state
        PositionTracker tracker = positionManager.getPositionTracker();
        assertTrue(tracker.hasPosition());
        assertTrue(tracker.isLong());
        assertEquals(4500.0, tracker.getEntryPrice());
        assertEquals(4490.0, tracker.getStopPrice());
        assertEquals(4530.0, tracker.getTargetPrice());
        
        // Simulate order fill
        when(mockOrderContext.getPosition()).thenReturn(2); // Now have long position
        Order fillOrder = mock(Order.class);
        when(fillOrder.getAvgFillPrice()).thenReturn(4500.25f);
        when(fillOrder.getQuantity()).thenReturn(2);
        when(fillOrder.isBuy()).thenReturn(true);
        
        positionManager.onOrderFilled(fillOrder);
        
        // Verify position manager reflects the new state
        assertTrue(positionManager.hasPosition());
        assertTrue(positionManager.isLong());
        assertEquals(2, positionManager.getCurrentPosition());
        assertEquals("LONG", positionManager.getCurrentPositionSide());
        
        // Verify entry price was updated from fill
        assertEquals(4500.25, tracker.getEntryPrice());
        
        // Test P&L calculations
        double unrealizedPnL = tracker.calculateUnrealizedPnL(4520.0, 2);
        assertEquals(39.5, unrealizedPnL, 0.01); // (4520 - 4500.25) * 2
        
        // Test position near stop detection
        assertFalse(tracker.isNearStop(4520.0, 0.2)); // Far from stop
        assertTrue(tracker.isNearStop(4492.0, 0.2)); // Near stop
        
        // Exit position
        boolean exitResult = positionManager.exitPosition();
        assertTrue(exitResult);
        verify(mockOrderContext).closeAtMarket();
        
        // Verify position tracker is reset
        assertFalse(tracker.hasPosition());
        assertEquals(0.0, tracker.getEntryPrice());
        assertEquals(0.0, tracker.getStopPrice());
        assertEquals(0.0, tracker.getTargetPrice());
    }
    
    @Test
    @DisplayName("Position reversal workflow should work correctly")
    void testPositionReversalWorkflow() {
        // Setup existing short position
        when(mockOrderContext.getPosition()).thenReturn(-1);
        
        // Setup order mocks for reversal to long
        Order mockMarketOrder = mock(Order.class);
        Order mockStopOrder = mock(Order.class);
        Order mockTargetOrder = mock(Order.class);
        
        when(mockOrderContext.createMarketOrder(any(), any(), eq(Enums.OrderAction.BUY), eq(2)))
            .thenReturn(mockMarketOrder);
        when(mockOrderContext.createStopOrder(any(), any(), eq(Enums.OrderAction.SELL), 
                                             any(), eq(2), anyFloat()))
            .thenReturn(mockStopOrder);
        when(mockOrderContext.createLimitOrder(any(), any(), eq(Enums.OrderAction.SELL), 
                                              any(), eq(2), anyFloat()))
            .thenReturn(mockTargetOrder);
        
        // Mock position change to flat after exit, then to long
        when(mockOrderContext.getPosition()).thenReturn(-1).thenReturn(0).thenReturn(2);
        
        // Reverse position from short to long
        PositionManager.PositionInfo reverseResult = 
            positionManager.reversePosition(4500.0, 4490.0, 4530.0, 2);
        
        assertNotNull(reverseResult);
        assertTrue(reverseResult.isLong()); // Should be long (reversed from short)
        
        // Should exit existing position and enter new one
        verify(mockOrderContext).closeAtMarket(); // Exit existing
        verify(mockOrderContext).submitOrders(mockMarketOrder, mockStopOrder, mockTargetOrder); // Enter new
    }
    
    @Test
    @DisplayName("Position sizing integration should work correctly")
    void testPositionSizingIntegration() {
        // Test position sizing through PositionSizer integration
        PositionSizer.PositionInfo sizingInfo = positionSizer.calculatePosition(
            2,      // positionSizeFactor
            3,      // tradeLots
            1000.0, // maxRiskDollars
            10.0    // stopDistancePoints
        );
        
        // Verify risk-based sizing
        assertEquals(6, sizingInfo.getBaseQuantity()); // 2 * 3
        assertEquals(2, sizingInfo.getFinalQuantity()); // Limited by $1000 risk / $500 per unit
        assertEquals(500.0, sizingInfo.getRiskPerUnit()); // 10 points * $50/point
        assertEquals(1000.0, sizingInfo.getTotalRisk()); // 2 units * $500
        assertTrue(sizingInfo.wasRiskAdjusted());
        
        // Test WATR-based calculation
        PositionSizer.PositionInfo watrInfo = positionSizer.calculatePositionWithWatr(
            1, 2, 1000.0, 3.5, 2.0, 5.0, 20.0);
        
        assertEquals(7.0, watrInfo.getStopDistancePoints()); // 3.5 * 2.0 = 7.0 (within bounds)
        assertEquals(350.0, watrInfo.getRiskPerUnit()); // 7 points * $50/point
        assertEquals(2, watrInfo.getFinalQuantity()); // 1 * 2 = 2 (not risk limited)
    }
    
    @Test
    @DisplayName("Error handling should be robust")
    void testErrorHandling() {
        // Test with null order fill
        positionManager.onOrderFilled(null); // Should not crash
        
        // Test with invalid order fill
        Order invalidOrder = mock(Order.class);
        when(invalidOrder.getAvgFillPrice()).thenReturn(0.0f);
        when(invalidOrder.getQuantity()).thenReturn(-1);
        
        positionManager.onOrderFilled(invalidOrder); // Should not crash
        
        // Test entering position when already positioned
        when(mockOrderContext.getPosition()).thenReturn(2); // Existing long position
        
        PositionManager.PositionInfo result = positionManager.enterLong(4500.0, 4490.0, 4530.0, 1);
        assertNull(result); // Should fail gracefully
        
        // Test reversing when flat
        when(mockOrderContext.getPosition()).thenReturn(0); // Flat
        
        PositionManager.PositionInfo reverseResult = 
            positionManager.reversePosition(4500.0, 4490.0, 4530.0, 1);
        assertNull(reverseResult); // Should fail gracefully
        
        // Test exiting when flat
        boolean exitResult = positionManager.exitPosition();
        assertTrue(exitResult); // Should succeed (no-op)
    }
}