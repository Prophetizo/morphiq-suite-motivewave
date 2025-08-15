package com.morphiqlabs.common.position;

import com.motivewave.platform.sdk.common.DataContext;
import com.motivewave.platform.sdk.common.DataSeries;
import com.motivewave.platform.sdk.common.Enums;
import com.motivewave.platform.sdk.common.Instrument;
import com.motivewave.platform.sdk.order_mgmt.Order;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.stubbing.OngoingStubbing;

/**
 * Unit tests for PositionManager class.
 * Tests position management operations without external dependencies.
 */
class PositionManagerTest {
    
    private OrderContext mockOrderContext;
    private PositionSizer mockPositionSizer;
    private Instrument mockInstrument;
    private DataContext mockDataContext;
    private DataSeries mockDataSeries;
    private PositionManager positionManager;
    
    @BeforeEach
    void setUp() {
        mockOrderContext = mock(OrderContext.class);
        mockPositionSizer = mock(PositionSizer.class);
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
        
        positionManager = new PositionManager(mockOrderContext, mockPositionSizer);
    }
    
    /**
     * Helper method to setup position state for cleaner test code.
     * @param position the position value (positive for long, negative for short, 0 for flat)
     */
    private void setupPosition(int position) {
        reset(mockOrderContext);
        when(mockOrderContext.getPosition()).thenReturn(position);
        // Re-setup other required mocks after reset
        when(mockOrderContext.getInstrument()).thenReturn(mockInstrument);
        when(mockOrderContext.getDataContext()).thenReturn(mockDataContext);
    }
    
    /**
     * Helper method to setup sequential position states for operations that check position multiple times.
     * @param positions array of position values to return in sequence
     */
    private void setupPositionSequence(int... positions) {
        if (positions.length == 0) return;
        
        // Setup to return first value, then subsequent values
        OngoingStubbing<Integer> stubbing = when(mockOrderContext.getPosition()).thenReturn(positions[0]);
        for (int i = 1; i < positions.length; i++) {
            stubbing = stubbing.thenReturn(positions[i]);
        }
    }
    
    @Test
    @DisplayName("Constructor should validate null inputs")
    void testConstructorValidation() {
        assertThrows(IllegalArgumentException.class, 
                    () -> new PositionManager(null, mockPositionSizer));
        assertThrows(IllegalArgumentException.class, 
                    () -> new PositionManager(mockOrderContext, null));
    }
    
    @Test
    @DisplayName("Constructor should initialize properly")
    void testConstructorInitialization() {
        assertNotNull(positionManager);
        assertEquals(mockPositionSizer, positionManager.getPositionSizer());
        assertNotNull(positionManager.getPositionTracker());
        assertFalse(positionManager.hasPosition());
    }
    
    @Test
    @DisplayName("Position state checks should work correctly")
    void testPositionStateChecks() {
        // Test flat position
        when(mockOrderContext.getPosition()).thenReturn(0);
        assertFalse(positionManager.hasPosition());
        assertFalse(positionManager.isLong());
        assertFalse(positionManager.isShort());
        assertEquals("FLAT", positionManager.getCurrentPositionSide());
        assertEquals(0, positionManager.getCurrentPosition());
        
        // Test long position
        when(mockOrderContext.getPosition()).thenReturn(2);
        assertTrue(positionManager.hasPosition());
        assertTrue(positionManager.isLong());
        assertFalse(positionManager.isShort());
        assertEquals("LONG", positionManager.getCurrentPositionSide());
        assertEquals(2, positionManager.getCurrentPosition());
        
        // Test short position
        when(mockOrderContext.getPosition()).thenReturn(-2);
        assertTrue(positionManager.hasPosition());
        assertFalse(positionManager.isLong());
        assertTrue(positionManager.isShort());
        assertEquals("SHORT", positionManager.getCurrentPositionSide());
        assertEquals(-2, positionManager.getCurrentPosition());
    }
    
    @Test
    @DisplayName("Enter long position should create proper orders")
    void testEnterLongPosition() {
        Order mockMarketOrder = mock(Order.class);
        Order mockStopOrder = mock(Order.class);
        Order mockTargetOrder = mock(Order.class);
        
        when(mockOrderContext.createMarketOrder(any(), any(), eq(Enums.OrderAction.BUY), eq(2)))
            .thenReturn(mockMarketOrder);
        when(mockOrderContext.createStopOrder(any(), any(), eq(Enums.OrderAction.SELL), 
                                             eq(Enums.TIF.DAY), eq(2), eq(4490.0f)))
            .thenReturn(mockStopOrder);
        when(mockOrderContext.createLimitOrder(any(), any(), eq(Enums.OrderAction.SELL), 
                                              eq(Enums.TIF.DAY), eq(2), eq(4530.0f)))
            .thenReturn(mockTargetOrder);
        
        PositionManager.PositionInfo result = positionManager.enterLong(4500.0, 4490.0, 4530.0, 2);
        
        assertNotNull(result);
        assertEquals(4500.0, result.getEntryPrice());
        assertEquals(4490.0, result.getStopPrice());
        assertEquals(4530.0, result.getTargetPrice());
        assertEquals(2, result.getQuantity());
        assertTrue(result.isLong());
        
        verify(mockOrderContext).submitOrders(mockMarketOrder, mockStopOrder, mockTargetOrder);
    }
    
    @Test
    @DisplayName("Enter short position should create proper orders")
    void testEnterShortPosition() {
        Order mockMarketOrder = mock(Order.class);
        Order mockStopOrder = mock(Order.class);
        Order mockTargetOrder = mock(Order.class);
        
        when(mockOrderContext.createMarketOrder(any(), any(), eq(Enums.OrderAction.SELL), eq(2)))
            .thenReturn(mockMarketOrder);
        when(mockOrderContext.createStopOrder(any(), any(), eq(Enums.OrderAction.BUY), 
                                             eq(Enums.TIF.DAY), eq(2), eq(4510.0f)))
            .thenReturn(mockStopOrder);
        when(mockOrderContext.createLimitOrder(any(), any(), eq(Enums.OrderAction.BUY), 
                                              eq(Enums.TIF.DAY), eq(2), eq(4470.0f)))
            .thenReturn(mockTargetOrder);
        
        PositionManager.PositionInfo result = positionManager.enterShort(4500.0, 4510.0, 4470.0, 2);
        
        assertNotNull(result);
        assertEquals(4500.0, result.getEntryPrice());
        assertEquals(4510.0, result.getStopPrice());
        assertEquals(4470.0, result.getTargetPrice());
        assertEquals(2, result.getQuantity());
        assertFalse(result.isLong());
        
        verify(mockOrderContext).submitOrders(mockMarketOrder, mockStopOrder, mockTargetOrder);
    }
    
    @Test
    @DisplayName("Cannot enter position when already positioned")
    void testCannotEnterWhenPositioned() {
        // Setup existing long position
        when(mockOrderContext.getPosition()).thenReturn(2);
        
        PositionManager.PositionInfo longResult = positionManager.enterLong(4500.0, 4490.0, 4530.0, 2);
        PositionManager.PositionInfo shortResult = positionManager.enterShort(4500.0, 4510.0, 4470.0, 2);
        
        assertNull(longResult);
        assertNull(shortResult);
        
        verify(mockOrderContext, never()).submitOrders(any(), any(), any());
    }
    
    @Test
    @DisplayName("Exit position should close at market")
    void testExitPosition() {
        // Setup existing position
        when(mockOrderContext.getPosition()).thenReturn(2);
        
        boolean result = positionManager.exitPosition();
        
        assertTrue(result);
        verify(mockOrderContext).closeAtMarket();
    }
    
    @Test
    @DisplayName("Exit position when flat should succeed")
    void testExitPositionWhenFlat() {
        // Already flat
        when(mockOrderContext.getPosition()).thenReturn(0);
        
        boolean result = positionManager.exitPosition();
        
        assertTrue(result);
        verify(mockOrderContext, never()).closeAtMarket();
    }
    
    @Test
    @DisplayName("Reverse position should exit and enter opposite")
    void testReversePosition() {
        // Setup mock behavior for position checks during reversal:
        // 1st call: hasPosition() check before reversal - returns 2 (long position)
        // 2nd call: isLong() check to determine direction - returns 2 (long position)
        // 3rd call: hasPosition() check during exit - returns 2 (still has position)
        // 4th call: hasPosition() check after exit for new entry - returns 0 (flat after exit)
        when(mockOrderContext.getPosition())
            .thenReturn(2)  // hasPosition() - initial check
            .thenReturn(2)  // isLong() - determine direction
            .thenReturn(2)  // hasPosition() - during exit
            .thenReturn(0); // hasPosition() - after exit, before new entry
        
        Order mockMarketOrder = mock(Order.class);
        Order mockStopOrder = mock(Order.class);
        Order mockTargetOrder = mock(Order.class);
        
        when(mockOrderContext.createMarketOrder(any(), any(), eq(Enums.OrderAction.SELL), eq(1)))
            .thenReturn(mockMarketOrder);
        when(mockOrderContext.createStopOrder(any(), any(), eq(Enums.OrderAction.BUY), 
                                             eq(Enums.TIF.DAY), eq(1), eq(4510.0f)))
            .thenReturn(mockStopOrder);
        when(mockOrderContext.createLimitOrder(any(), any(), eq(Enums.OrderAction.BUY), 
                                              eq(Enums.TIF.DAY), eq(1), eq(4470.0f)))
            .thenReturn(mockTargetOrder);
        
        PositionManager.PositionInfo result = positionManager.reversePosition(4500.0, 4510.0, 4470.0, 1);
        
        assertNotNull(result);
        assertFalse(result.isLong()); // Should be short now
        
        verify(mockOrderContext).closeAtMarket(); // Exit existing
        verify(mockOrderContext).submitOrders(mockMarketOrder, mockStopOrder, mockTargetOrder); // Enter new
    }
    
    @Test
    @DisplayName("Cannot reverse when flat")
    void testCannotReverseWhenFlat() {
        when(mockOrderContext.getPosition()).thenReturn(0);
        
        PositionManager.PositionInfo result = positionManager.reversePosition(4500.0, 4510.0, 4470.0, 1);
        
        assertNull(result);
        verify(mockOrderContext, never()).closeAtMarket();
        verify(mockOrderContext, never()).submitOrders(any(), any(), any());
    }
    
    @Test
    @DisplayName("Order fill handling should update tracking")
    void testOrderFillHandling() {
        Order mockOrder = mock(Order.class);
        when(mockOrder.getAvgFillPrice()).thenReturn(4500.25f);
        when(mockOrder.getQuantity()).thenReturn(2);
        when(mockOrder.isBuy()).thenReturn(true);
        
        // Setup position
        when(mockOrderContext.getPosition()).thenReturn(2);
        
        // First initialize position tracking
        positionManager.getPositionTracker().updatePosition(4500.0, 4490.0, 4530.0, true);
        
        positionManager.onOrderFilled(mockOrder);
        
        // Verify tracking is updated
        assertEquals(4500.25, positionManager.getPositionTracker().getEntryPrice());
    }
    
    @Test
    @DisplayName("Invalid order fill should be handled gracefully")
    void testInvalidOrderFill() {
        // Test null order
        positionManager.onOrderFilled(null);
        
        // Test invalid price
        Order invalidOrder = mock(Order.class);
        when(invalidOrder.getAvgFillPrice()).thenReturn(0.0f);
        when(invalidOrder.getQuantity()).thenReturn(2);
        when(invalidOrder.isBuy()).thenReturn(true);
        
        positionManager.onOrderFilled(invalidOrder);
        
        // Should not crash and should not update tracking
        assertEquals(0.0, positionManager.getPositionTracker().getEntryPrice());
    }
    
    @Test
    @DisplayName("PositionInfo toString should be readable")
    void testPositionInfoToString() {
        PositionManager.PositionInfo longInfo = 
            new PositionManager.PositionInfo(4500.0, 4490.0, 4530.0, 2, true);
        PositionManager.PositionInfo shortInfo = 
            new PositionManager.PositionInfo(4500.0, 4510.0, 4470.0, 1, false);
        
        String longStr = longInfo.toString();
        String shortStr = shortInfo.toString();
        
        assertTrue(longStr.contains("LONG"));
        assertTrue(longStr.contains("4500.00"));
        assertTrue(longStr.contains("qty=2"));
        
        assertTrue(shortStr.contains("SHORT"));
        assertTrue(shortStr.contains("4500.00"));
        assertTrue(shortStr.contains("qty=1"));
    }
}