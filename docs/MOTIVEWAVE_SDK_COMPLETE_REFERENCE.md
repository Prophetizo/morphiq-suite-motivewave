# MotiveWave SDK Complete Reference Guide

## Table of Contents
1. [SDK Package Structure](#sdk-package-structure)
2. [Core Classes and Interfaces](#core-classes-and-interfaces)
3. [DataContext API Reference](#datacontext-api-reference)
4. [DataSeries API Reference](#dataseries-api-reference)
5. [OrderContext API Reference](#ordercontext-api-reference)
6. [Common Enumerations](#common-enumerations)
7. [Advanced Calculation Methods](#advanced-calculation-methods)
8. [Complete Settings Descriptors](#complete-settings-descriptors)
9. [Signal and Marker Management](#signal-and-marker-management)
10. [Performance Optimization Techniques](#performance-optimization-techniques)

---

## SDK Package Structure

### Core Packages
```
com.motivewave.platform.sdk
├── common/              # Core utilities, enums, and interfaces
│   ├── desc/           # Setting descriptors for UI
│   └── menu/           # Menu customization
├── draw/               # Drawing and visualization
├── order_mgmt/         # Order and position management
└── study/              # Study and strategy base classes
```

### Package Responsibilities
- **common**: DataContext, DataSeries, Instrument, Enums, Utilities
- **common.desc**: All UI descriptors (IntegerDescriptor, PathDescriptor, etc.)
- **draw**: Marker, Figure, DrawingTool interfaces
- **order_mgmt**: OrderContext, Order, OrderAction
- **study**: Study base class, StudyHeader, RuntimeDescriptor, Plot

---

## Core Classes and Interfaces

### Study Class Hierarchy
```java
public abstract class Study {
    // Lifecycle methods
    public void initialize(Defaults defaults) {}
    public void onLoad(Defaults defaults) {}
    public void onSettingsUpdated(DataContext ctx) {}
    public void clearState() {}
    public void destroy() {}
    
    // Calculation
    protected abstract void calculate(int index, DataContext ctx);
    protected void calculateValues(DataContext ctx) {}
    
    // Configuration
    public int getMinBars() { return 1; }
    protected void setMinBars(int minBars) {}
    
    // Utilities
    protected Settings getSettings() {}
    protected RuntimeDescriptor getRuntimeDescriptor() {}
    protected SettingsDescriptor createSD() {}
    protected RuntimeDescriptor createRD() {}
}
```

### StudyHeader Annotation
```java
@StudyHeader(
    namespace = "com.company",          // Required: Unique namespace
    id = "STUDY_ID",                   // Required: Unique identifier
    name = "Display Name",              // Required: UI display name
    
    // Optional attributes
    desc = "Description",               // HTML supported
    menu = "Menu/Submenu",             // Menu location
    rb = "resource.bundle",            // Localization
    label = "Label",                   // Short label
    helpLink = "http://...",           // Documentation URL
    
    // Behavior flags
    overlay = true,                    // Draw on price chart
    requiresBarUpdates = false,        // Needs bar-by-bar updates
    studyOverlay = true,               // Can overlay on other studies
    signals = false,                   // Generates signals
    
    // Strategy-specific
    strategy = false,                  // Is a trading strategy
    autoEntry = false,                 // Supports auto-entry
    manualEntry = true,                // Supports manual entry
    supportsUnrealizedPL = false,      // Track unrealized P&L
    supportsRealizedPL = false,        // Track realized P&L
    supportsTotalPL = false            // Track total P&L
)
```

---

## DataContext API Reference

### Core Methods
```java
public interface DataContext {
    // Data access
    DataSeries getDataSeries();
    DataSeries getDataSeries(BarSize size);
    Instrument getInstrument();
    List<Instrument> getInstruments();
    
    // Time and environment
    long getCurrentTime();
    TimeZone getTimeZone();
    BarSize getChartBarSize();
    
    // State queries
    boolean isRTH();                   // Regular trading hours
    boolean isLoadingData();           // Loading historical data
    boolean isReplayMode();            // In replay/backtest mode
    boolean isCalculateAll();          // Full recalculation needed
    
    // Signal generation
    void signal(int index, Object key, String msg, double value);
    
    // Figure management
    void clearFigures();               // AVOID - causes threading issues
    void repaint();                    // Request chart repaint
}
```

---

## DataSeries API Reference

### Price Data Access
```java
// Basic OHLCV
Double getOpen(int index);
Double getHigh(int index);
Double getLow(int index);
Double getClose(int index);
Long getVolume(int index);

// Bid/Ask
Double getBid(int index);
Double getAsk(int index);
Double getBidVol(int index);
Double getAskVol(int index);

// Time
long getStartTime(int index);
long getEndTime(int index);

// Bar information
int size();                           // Number of bars
boolean isLastBarComplete();          // Check if last bar is complete
Float getBarSize();                    // Get bar size in minutes
```

### Value Storage and Retrieval
```java
// Store values (key can be enum or string)
void setDouble(int index, Object key, Double value);
void setFloat(int index, Object key, Float value);
void setInt(int index, Object key, Integer value);
void setLong(int index, Object key, Long value);
void setBoolean(int index, Object key, Boolean value);
void setObject(int index, Object key, Object value);

// Retrieve values
Double getDouble(int index, Object key);
Float getFloat(int index, Object key);
Integer getInt(int index, Object key);
Long getLong(int index, Object key);
Boolean getBoolean(int index, Object key);
Object getObject(int index, Object key);

// Latest values
Double getLatestDouble(Object key);
Float getLatestFloat(Object key);
```

### Moving Averages
```java
// Simple Moving Average
Double sma(int index, int period, Object input);

// Exponential Moving Average
Double ema(int index, int period, Object input);

// Weighted Moving Average  
Double wma(int index, int period, Object input);

// Hull Moving Average
Double hma(int index, int period, Object input);

// Double Exponential Moving Average
Double dema(int index, int period, Object input);

// Triple Exponential Moving Average
Double tema(int index, int period, Object input);

// Triangular Moving Average
Double tma(int index, int period, Object input);

// Modified Exponential Moving Average
Double mema(int index, int period, Object input);

// Kaufmann Adaptive Moving Average
Double kama(int index, int period, Object input);

// Volume Weighted Moving Average
Double vwma(int index, int period, Object input);

// Moving Average with custom method
Double ma(MAMethod method, int index, int period, Object input);
```

### Technical Indicators
```java
// Average True Range
Double atr(int index, int period);

// Standard Deviation
Double std(int index, int period, Object input);

// Rate of Change
Double roc(int index, int period, Object input);

// Stochastic %K
Double stochasticK(int index, int kPeriod, int smooth);

// Money Flow Multiplier
Double mfm(int index);

// Accumulation/Distribution
Double accDist(int index);

// Highest/Lowest
Double highest(int index, int period, Object input);
Double lowest(int index, int period, Object input);

// Sum
Double sum(int index, int period, Object input);

// Linear Regression
Double linReg(int index, int period, Object input);
Double linRegSlope(int index, int period, Object input);
```

---

## OrderContext API Reference

### Position Management
```java
// Get current position
int getPosition();                    // Default instrument
int getPosition(Instrument instr);    // Specific instrument

// Close positions
void closeAtMarket();                  // Close strategy position
void closeAccountAtMarket();           // Close account position
```

### Order Creation
```java
// Market orders
void buy(int quantity);
void sell(int quantity);

// Create orders (not submitted)
Order createMarketOrder(OrderAction action, int qty);
Order createLimitOrder(OrderAction action, TIF tif, int qty, double price);
Order createStopOrder(OrderAction action, TIF tif, int qty, double stopPrice);
Order createStopLimitOrder(OrderAction action, TIF tif, int qty, 
                          double stopPrice, double limitPrice);
```

### Order Submission
```java
// Submit orders
void submitOrders(Order... orders);

// Cancel orders
void cancelOrders();                  // Cancel all
void cancelOrders(Order... orders);   // Cancel specific

// Modify orders
void modifyOrder(Order order, Integer qty, Double price, Double stopPrice);
```

### Bracket Orders
```java
// Create OCO (One-Cancels-Other) bracket
Order createOCOBracket(Order parent, Order stopLoss, Order takeProfit);

// Submit with brackets
void submitWithBracket(Order entry, Order stopLoss, Order takeProfit);
```

---

## Common Enumerations

### Enums.BarInput
```java
public enum BarInput {
    OPEN, HIGH, LOW, CLOSE,           // Basic OHLC
    HL2,                              // (High + Low) / 2
    HLC3,                             // (High + Low + Close) / 3
    OHLC4,                            // (Open + High + Low + Close) / 4
    VOLUME,                           // Volume
    OPEN_INTEREST                     // Open Interest
}
```

### Enums.MAMethod
```java
public enum MAMethod {
    SMA,      // Simple Moving Average
    EMA,      // Exponential Moving Average
    WMA,      // Weighted Moving Average
    DEMA,     // Double Exponential MA
    TEMA,     // Triple Exponential MA
    TMA,      // Triangular MA
    HMA,      // Hull MA
    KAMA,     // Kaufmann Adaptive MA
    MEMA,     // Modified Exponential MA
    VWMA      // Volume Weighted MA
}
```

### Enums.MarkerType
```java
public enum MarkerType {
    TRIANGLE,
    TRIANGLE_DOWN,
    SQUARE,
    DIAMOND,
    CIRCLE,
    ARROW_UP,
    ARROW_DOWN,
    DOT,
    LINE
}
```

### Enums.OrderAction
```java
public enum OrderAction {
    BUY,
    SELL,
    SELL_SHORT,
    BUY_TO_COVER
}
```

### Enums.Size
```java
public enum Size {
    VERY_SMALL,
    SMALL,
    MEDIUM,
    LARGE,
    VERY_LARGE
}
```

---

## Advanced Calculation Methods

### Efficient Sliding Window Pattern
```java
public class OptimizedStudy extends Study {
    private RingBuffer buffer;
    private int lastCalculatedIndex = -1;
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        // Skip already calculated bars
        if (index <= lastCalculatedIndex) return;
        
        var series = ctx.getDataSeries();
        int period = getSettings().getInteger(Inputs.PERIOD);
        
        // Initialize buffer on first call
        if (buffer == null) {
            buffer = new RingBuffer(period);
            // Backfill buffer
            for (int i = Math.max(0, index - period + 1); i <= index; i++) {
                buffer.add(series.getClose(i));
            }
        } else {
            // Update buffer with new value
            buffer.add(series.getClose(index));
        }
        
        // Calculate using buffer
        double result = buffer.average();
        series.setDouble(index, Values.MA, result);
        
        lastCalculatedIndex = index;
    }
}
```

### Parallel Calculation Pattern
```java
@Override
protected void calculateValues(DataContext ctx) {
    var series = ctx.getDataSeries();
    int size = series.size();
    int cores = Runtime.getRuntime().availableProcessors();
    
    // Use parallel processing for large datasets
    if (size > 1000) {
        IntStream.range(0, size)
            .parallel()
            .forEach(i -> calculateSingle(i, ctx));
    } else {
        // Sequential for small datasets
        for (int i = 0; i < size; i++) {
            calculate(i, ctx);
        }
    }
}
```

---

## Complete Settings Descriptors

### All Available Descriptors
```java
// Numeric inputs
new IntegerDescriptor(key, label, defaultVal, min, max, step)
new DoubleDescriptor(key, label, defaultVal, min, max, step)
new FloatDescriptor(key, label, defaultVal, min, max, step)
new LongDescriptor(key, label, defaultVal, min, max, step)

// Sliders
new SliderDescriptor(key, label, defaultVal, min, max, step)

// Choice inputs
new InputDescriptor(key, label, Enums.BarInput.CLOSE)
new MAMethodDescriptor(key, label, Enums.MAMethod.SMA)
new DiscreteDescriptor(key, label, defaultChoice, List<NVP>)

// Boolean
new BooleanDescriptor(key, label, defaultValue)

// Visual elements
new PathDescriptor(key, label, color, width, style, visible, 
                  supportsColor, supportsStyle)
new MarkerDescriptor(key, label, type, size, color, outline, 
                    enabled, supportsDisable)
new ShadeDescriptor(key, label, topKey, bottomKey, color, 
                   primaryEnabled, secondaryEnabled)
new BarDescriptor(key, label, color, enabled, supportsDisable)
new GuideDescriptor(key, label, color, lineStyle, width, enabled)

// Indicators
new IndicatorDescriptor(key, label, color, displayType, 
                       showByDefault, supportsDisable, 
                       supportsColor)

// Special
new BarSizeDescriptor(key, label, defaultBarSize)
new ColorDescriptor(key, label, defaultColor)
new FontDescriptor(key, label, defaultFont)
new InstrumentDescriptor(key, label, defaultInstrument)
new TimeDescriptor(key, label, defaultTime)
```

### Dependencies
```java
// Enable/disable based on checkbox
sd.addDependency(new EnabledDependency(controlKey, 
    dependentKey1, dependentKey2, ...));

// Show/hide based on selection
sd.addDependency(new VisibilityDependency(controlKey, 
    value, targetKey1, targetKey2, ...));
```

---

## Signal and Marker Management

### Signal Generation Best Practices
```java
@Override
protected void calculate(int index, DataContext ctx) {
    var series = ctx.getDataSeries();
    
    // Always check previous values exist
    if (index < 1) return;
    
    Double current = series.getDouble(index, Values.INDICATOR);
    Double previous = series.getDouble(index - 1, Values.INDICATOR);
    
    if (current == null || previous == null) return;
    
    // Detect crossovers
    double threshold = getSettings().getDouble(THRESHOLD);
    
    // Rising cross
    if (previous <= threshold && current > threshold) {
        // Store signal state
        series.setBoolean(index, Signals.CROSS_UP, true);
        
        // Fire signal if enabled
        if (getSettings().isSignalsEnabled()) {
            ctx.signal(index, Signals.CROSS_UP, 
                      "Crossed Above " + threshold, 
                      series.getClose(index));
        }
        
        // Add marker if configured
        MarkerInfo marker = getSettings().getMarker(Signals.CROSS_UP);
        if (marker != null && marker.isEnabled()) {
            addFigure(new Marker(
                new Coordinate(series.getStartTime(index), current),
                Enums.Position.BOTTOM,
                marker
            ));
        }
    }
}
```

### Strategy Signal Handling
```java
@Override
public void onSignal(OrderContext ctx, Object signal) {
    // Get current position
    int position = ctx.getPosition();
    Instrument instr = ctx.getInstrument();
    
    // Calculate order size
    int baseLots = getSettings().getTradeLots();
    int qty = baseLots * instr.getDefaultQuantity();
    
    // Position management logic
    if (signal == Signals.LONG_ENTRY) {
        if (position < 0) {
            // Close short and go long
            qty += Math.abs(position);
        }
        if (position <= 0) {
            ctx.buy(qty);
        }
    } else if (signal == Signals.SHORT_ENTRY) {
        if (position > 0) {
            // Close long and go short
            qty += position;
        }
        if (position >= 0) {
            ctx.sell(qty);
        }
    } else if (signal == Signals.EXIT) {
        // Flat position
        if (position != 0) {
            ctx.closeAtMarket();
        }
    }
}
```

---

## Performance Optimization Techniques

### 1. Lazy Initialization
```java
private Double[] cachedEMA = null;

@Override
protected void calculate(int index, DataContext ctx) {
    // Initialize cache only when needed
    if (cachedEMA == null) {
        cachedEMA = new Double[ctx.getDataSeries().size()];
    }
    
    // Check cache first
    if (cachedEMA[index] != null) {
        return; // Already calculated
    }
    
    // Calculate and cache
    cachedEMA[index] = calculateEMA(index, ctx);
}
```

### 2. Incremental Calculation
```java
@Override
protected void calculate(int index, DataContext ctx) {
    var series = ctx.getDataSeries();
    int period = getSettings().getInteger(Inputs.PERIOD);
    
    if (index < period) {
        // Not enough data
        return;
    } else if (index == period) {
        // First calculation - full computation
        double sum = 0;
        for (int i = index - period + 1; i <= index; i++) {
            sum += series.getClose(i);
        }
        series.setDouble(index, Values.SMA, sum / period);
    } else {
        // Incremental update
        Double prevSMA = series.getDouble(index - 1, Values.SMA);
        if (prevSMA == null) return;
        
        double oldValue = series.getClose(index - period);
        double newValue = series.getClose(index);
        double newSMA = prevSMA + (newValue - oldValue) / period;
        
        series.setDouble(index, Values.SMA, newSMA);
    }
}
```

### 3. Batch Processing
```java
@Override
protected void calculateValues(DataContext ctx) {
    var series = ctx.getDataSeries();
    int size = series.size();
    int period = getSettings().getInteger(Inputs.PERIOD);
    
    // Skip individual calculate() calls
    if (size > 10000) {
        // Batch process in chunks
        double[] prices = new double[size];
        double[] results = new double[size];
        
        // Bulk load data
        for (int i = 0; i < size; i++) {
            prices[i] = series.getClose(i);
        }
        
        // Process in optimized batch
        calculateBatch(prices, results, period);
        
        // Bulk store results
        for (int i = period; i < size; i++) {
            series.setDouble(i, Values.MA, results[i]);
        }
        
        return; // Skip individual calculate() calls
    }
    
    // Fall back to normal processing
    super.calculateValues(ctx);
}
```

### 4. Memory-Efficient Patterns
```java
public class MemoryEfficientStudy extends Study {
    // Use primitive arrays instead of ArrayList<Double>
    private double[] buffer;
    
    // Reuse objects
    private final Statistics stats = new Statistics();
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        // Reuse statistics object
        stats.reset();
        
        // Use primitive arrays
        if (buffer == null || buffer.length != period) {
            buffer = new double[period];
        }
        
        // Fill buffer
        var series = ctx.getDataSeries();
        for (int i = 0; i < period; i++) {
            buffer[i] = series.getClose(index - period + 1 + i);
        }
        
        // Calculate using reusable object
        stats.calculate(buffer);
        series.setDouble(index, Values.MEAN, stats.getMean());
        series.setDouble(index, Values.STDEV, stats.getStdDev());
    }
}
```

---

## Complete Study Template

```java
package com.company.studies;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;
import com.motivewave.platform.sdk.study.*;

@StudyHeader(
    namespace = "com.company",
    id = "ADVANCED_STUDY",
    name = "Advanced Study Template",
    desc = "Complete study with all features",
    menu = "Company/Studies",
    overlay = false,
    signals = true,
    strategy = true,
    autoEntry = true,
    supportsUnrealizedPL = true,
    supportsRealizedPL = true,
    supportsTotalPL = true
)
public class AdvancedStudy extends Study {
    
    // Value keys
    enum Values { 
        FAST_MA, SLOW_MA, SIGNAL, HISTOGRAM,
        UPPER_BAND, LOWER_BAND, MOMENTUM 
    }
    
    // Signals
    enum Signals { 
        BUY, SELL, EXIT,
        OVERBOUGHT, OVERSOLD 
    }
    
    // Custom settings keys
    static final String FAST_PERIOD = "fastPeriod";
    static final String SLOW_PERIOD = "slowPeriod";
    static final String USE_ALERTS = "useAlerts";
    static final String MOMENTUM_THRESHOLD = "momentumThreshold";
    
    // State management
    private transient double[] fastBuffer;
    private transient double[] slowBuffer;
    private transient int lastCalculated = -1;
    
    @Override
    public void initialize(Defaults defaults) {
        // Settings descriptor
        var sd = createSD();
        
        // General tab
        var generalTab = sd.addTab("General");
        var inputGroup = generalTab.addGroup("Inputs");
        
        inputGroup.addRow(
            new InputDescriptor(Inputs.INPUT, "Input", Enums.BarInput.CLOSE)
        );
        inputGroup.addRow(
            new IntegerDescriptor(FAST_PERIOD, "Fast Period", 12, 1, 200, 1),
            new IntegerDescriptor(SLOW_PERIOD, "Slow Period", 26, 1, 200, 1)
        );
        inputGroup.addRow(
            new MAMethodDescriptor(Inputs.METHOD, "MA Method", Enums.MAMethod.EMA)
        );
        
        // Signals tab
        var signalTab = sd.addTab("Signals");
        var signalGroup = signalTab.addGroup("Signal Configuration");
        
        signalGroup.addRow(
            new BooleanDescriptor(USE_ALERTS, "Enable Alerts", true)
        );
        signalGroup.addRow(
            new DoubleDescriptor(MOMENTUM_THRESHOLD, "Momentum Threshold", 
                                0.5, 0.0, 10.0, 0.1)
        );
        
        // Display tab
        var displayTab = sd.addTab("Display");
        var pathGroup = displayTab.addGroup("Paths");
        
        pathGroup.addRow(
            new PathDescriptor(Inputs.PATH, "Fast MA", 
                             defaults.getGreen(), 1.5f, null, true, true, true)
        );
        pathGroup.addRow(
            new PathDescriptor(Inputs.PATH2, "Slow MA", 
                             defaults.getRed(), 1.5f, null, true, true, true)
        );
        
        // Markers
        var markerGroup = displayTab.addGroup("Markers");
        markerGroup.addRow(
            new MarkerDescriptor(Signals.BUY.name(), "Buy Signal",
                               Enums.MarkerType.TRIANGLE, Enums.Size.MEDIUM,
                               defaults.getGreen(), defaults.getLineColor(),
                               true, true)
        );
        markerGroup.addRow(
            new MarkerDescriptor(Signals.SELL.name(), "Sell Signal",
                               Enums.MarkerType.TRIANGLE_DOWN, Enums.Size.MEDIUM,
                               defaults.getRed(), defaults.getLineColor(),
                               true, true)
        );
        
        // Runtime descriptor
        var desc = createRD();
        desc.setLabelSettings(Inputs.INPUT, FAST_PERIOD, SLOW_PERIOD);
        
        // Export values
        desc.exportValue(new ValueDescriptor(Values.FAST_MA, "Fast MA", 
                                           new String[]{Inputs.INPUT, FAST_PERIOD}));
        desc.exportValue(new ValueDescriptor(Values.SLOW_MA, "Slow MA",
                                           new String[]{Inputs.INPUT, SLOW_PERIOD}));
        
        // Declare paths
        desc.declarePath(Values.FAST_MA, Inputs.PATH);
        desc.declarePath(Values.SLOW_MA, Inputs.PATH2);
        
        // Declare signals
        desc.declareSignal(Signals.BUY, "Buy Signal");
        desc.declareSignal(Signals.SELL, "Sell Signal");
        
        // Create secondary plot
        Plot momentumPlot = new Plot();
        desc.addPlot("Momentum", momentumPlot);
        momentumPlot.declarePath(Values.MOMENTUM, "momentumPath");
        momentumPlot.addHorizontalLine(new LineInfo(0, null, 1.0f, new float[]{3,3}));
        
        // Set range keys
        desc.setRangeKeys(Values.FAST_MA, Values.SLOW_MA);
        momentumPlot.setRangeKeys(Values.MOMENTUM);
    }
    
    @Override
    public void onLoad(Defaults defaults) {
        // Initialize on load
        int fastPeriod = getSettings().getInteger(FAST_PERIOD);
        int slowPeriod = getSettings().getInteger(SLOW_PERIOD);
        
        fastBuffer = new double[fastPeriod];
        slowBuffer = new double[slowPeriod];
        
        setMinBars(Math.max(fastPeriod, slowPeriod) * 2);
    }
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        // Skip if already calculated
        if (index <= lastCalculated) return;
        
        var series = ctx.getDataSeries();
        if (series == null || series.size() == 0) return;
        
        // Get settings
        int fastPeriod = getSettings().getInteger(FAST_PERIOD);
        int slowPeriod = getSettings().getInteger(SLOW_PERIOD);
        Object input = getSettings().getInput(Inputs.INPUT);
        MAMethod method = getSettings().getMAMethod(Inputs.METHOD);
        
        // Check minimum bars
        if (index < Math.max(fastPeriod, slowPeriod)) return;
        
        // Calculate moving averages
        Double fastMA = series.ma(method, index, fastPeriod, input);
        Double slowMA = series.ma(method, index, slowPeriod, input);
        
        if (fastMA == null || slowMA == null) return;
        
        // Store values
        series.setDouble(index, Values.FAST_MA, fastMA);
        series.setDouble(index, Values.SLOW_MA, slowMA);
        
        // Calculate momentum
        double momentum = fastMA - slowMA;
        series.setDouble(index, Values.MOMENTUM, momentum);
        
        // Generate signals
        if (index > 0) {
            generateSignals(ctx, series, index, fastMA, slowMA, momentum);
        }
        
        lastCalculated = index;
    }
    
    private void generateSignals(DataContext ctx, DataSeries series, 
                                int index, double fastMA, double slowMA, 
                                double momentum) {
        
        // Get previous values
        Double prevFast = series.getDouble(index - 1, Values.FAST_MA);
        Double prevSlow = series.getDouble(index - 1, Values.SLOW_MA);
        
        if (prevFast == null || prevSlow == null) return;
        
        double threshold = getSettings().getDouble(MOMENTUM_THRESHOLD);
        boolean useAlerts = getSettings().getBoolean(USE_ALERTS);
        
        // Detect crossovers
        boolean wasBelow = prevFast <= prevSlow;
        boolean isAbove = fastMA > slowMA;
        
        if (wasBelow && isAbove && momentum > threshold) {
            // Bullish crossover
            series.setBoolean(index, Signals.BUY, true);
            
            if (useAlerts) {
                ctx.signal(index, Signals.BUY, "Bullish Crossover", 
                          series.getClose(index));
            }
        } else if (!wasBelow && !isAbove && momentum < -threshold) {
            // Bearish crossover
            series.setBoolean(index, Signals.SELL, true);
            
            if (useAlerts) {
                ctx.signal(index, Signals.SELL, "Bearish Crossover",
                          series.getClose(index));
            }
        }
    }
    
    @Override
    public void onActivate(OrderContext ctx) {
        // Strategy activation
        if (getSettings().isEnterOnActivate()) {
            var series = ctx.getDataContext().getDataSeries();
            int lastIndex = series.size() - 1;
            
            Double fastMA = series.getDouble(lastIndex, Values.FAST_MA);
            Double slowMA = series.getDouble(lastIndex, Values.SLOW_MA);
            
            if (fastMA != null && slowMA != null) {
                int qty = getSettings().getTradeLots() * 
                         ctx.getInstrument().getDefaultQuantity();
                
                if (fastMA > slowMA) {
                    ctx.buy(qty);
                } else {
                    ctx.sell(qty);
                }
            }
        }
    }
    
    @Override
    public void onSignal(OrderContext ctx, Object signal) {
        // Handle trading signals
        int position = ctx.getPosition();
        int qty = getSettings().getTradeLots() * 
                 ctx.getInstrument().getDefaultQuantity();
        
        if (signal == Signals.BUY) {
            if (position < 0) {
                // Close short and go long
                qty += Math.abs(position);
            }
            if (position <= 0) {
                ctx.buy(qty);
            }
        } else if (signal == Signals.SELL) {
            if (position > 0) {
                // Close long and go short  
                qty += position;
            }
            if (position >= 0) {
                ctx.sell(qty);
            }
        } else if (signal == Signals.EXIT) {
            ctx.closeAtMarket();
        }
    }
    
    @Override
    public void onSettingsUpdated(DataContext ctx) {
        // Clear state on settings change
        clearState();
        
        // Reinitialize
        onLoad(null);
        
        // Trigger recalculation
        super.onSettingsUpdated(ctx);
    }
    
    @Override
    public void clearState() {
        super.clearState();
        lastCalculated = -1;
        fastBuffer = null;
        slowBuffer = null;
    }
}
```

---

## Summary of Key SDK Insights

1. **DataSeries provides extensive built-in calculations** - Always check for existing methods before implementing your own
2. **OrderContext handles all trading operations** - From simple market orders to complex bracket orders
3. **Enums provide standardization** - Use SDK enums for consistency across studies
4. **Multiple calculation patterns available** - Choose between calculate(), calculateValues(), or batch processing based on needs
5. **Signal generation is separate from trading** - Studies generate signals, strategies act on them
6. **Settings descriptors are highly flexible** - Support dependencies, validation, and complex UI layouts
7. **Performance optimization is critical** - Use caching, incremental updates, and batch processing for large datasets
8. **State management must be careful** - Always call super methods, avoid clearFigures(), handle null values

This complete reference combines the official SDK documentation with practical patterns and best practices for developing robust MotiveWave studies and strategies.