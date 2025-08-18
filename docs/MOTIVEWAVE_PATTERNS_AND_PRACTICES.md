# MotiveWave Study Development Patterns and Best Practices

## Table of Contents
1. [Study Structure Patterns](#study-structure-patterns)
2. [Initialization Patterns](#initialization-patterns)
3. [Calculation Patterns](#calculation-patterns)
4. [Settings and UI Patterns](#settings-and-ui-patterns)
5. [Strategy Patterns](#strategy-patterns)
6. [Multi-Plot Studies](#multi-plot-studies)
7. [State Management](#state-management)
8. [Performance Considerations](#performance-considerations)
9. [Common Pitfalls to Avoid](#common-pitfalls-to-avoid)

---

## Study Structure Patterns

### Basic Study Anatomy
```java
@StudyHeader(
    namespace="com.company",     // Unique namespace
    id="STUDY_ID",               // Unique identifier
    rb="resource.bundle",        // Optional: Localization
    name="Study Name",           // Display name
    desc="Description",          // Study description
    menu="Menu Location",        // Menu placement
    overlay=true/false,          // Chart overlay or separate pane
    signals=true/false,          // Generates signals
    strategy=true/false          // Trading strategy
)
public class MyStudy extends Study {
    // Value storage keys
    enum Values { MA, SIGNAL, HISTOGRAM }
    
    // Signal types
    enum Signals { CROSS_ABOVE, CROSS_BELOW }
    
    // Settings keys (use constants)
    static final String CUSTOM_PERIOD = "customPeriod";
}
```

### Key Observations:
- **Always use enums** for Values and Signals - provides type safety
- **Use constants** for setting keys - prevents typos
- **Namespace + ID** must be globally unique
- **Resource bundles** for internationalization (optional)

---

## Initialization Patterns

### Standard Initialize Method Structure
```java
@Override
public void initialize(Defaults defaults) {
    // 1. Create Settings Descriptor (Design-time)
    var sd = createSD();
    
    // 2. Add tabs for organization
    var generalTab = sd.addTab("General");
    
    // 3. Add groups within tabs
    var inputGroup = generalTab.addGroup("Inputs");
    
    // 4. Add settings rows
    inputGroup.addRow(new IntegerDescriptor(Inputs.PERIOD, "Period", 20, 1, 9999, 1));
    
    // 5. Create Runtime Descriptor
    var desc = createRD();
    
    // 6. Configure runtime behavior
    desc.setLabelSettings(Inputs.INPUT, Inputs.PERIOD);
    desc.exportValue(new ValueDescriptor(Values.MA, "Moving Average"));
    desc.declarePath(Values.MA, Inputs.PATH);
    desc.declareIndicator(Values.MA, Inputs.IND);
    
    // 7. Set range keys for auto-scaling
    desc.setRangeKeys(Values.MA);
}
```

### Settings Organization Pattern
```java
// Tab -> Group -> Row structure
var tab = sd.addTab("General");
var group = tab.addGroup("Configuration");
group.addRow(descriptor1, descriptor2); // Multiple descriptors per row
```

### Common Descriptors
```java
// Input selection
new InputDescriptor(Inputs.INPUT, "Input", Enums.BarInput.CLOSE)

// Integer with range
new IntegerDescriptor(key, label, default, min, max, step)

// Moving average method
new MAMethodDescriptor(Inputs.METHOD, "Method", Enums.MAMethod.SMA)

// Path configuration
new PathDescriptor(Inputs.PATH, "Line", color, width, style, visible, supportsColor, supportsStyle)

// Boolean toggle
new BooleanDescriptor(key, "Enable Feature", false)

// Bar coloring
new BarDescriptor(Inputs.BAR, "Bar Color", color, enabled, true)

// Markers
new MarkerDescriptor(signal, "Signal", MarkerType.TRIANGLE, Size.MEDIUM, color, outline, enabled, true)
```

---

## Calculation Patterns

### Basic Calculation Structure
```java
@Override
protected void calculate(int index, DataContext ctx) {
    // 1. Get settings
    int period = getSettings().getInteger(Inputs.PERIOD);
    Object input = getSettings().getInput(Inputs.INPUT);
    
    // 2. Check minimum data requirement
    if (index < period) return;
    
    // 3. Get data series
    var series = ctx.getDataSeries();
    
    // 4. Perform calculations
    Double ma = series.ema(index, period, input);
    if (ma == null) return;
    
    // 5. Store results
    series.setDouble(index, Values.MA, ma);
    
    // 6. Mark as complete (optional)
    series.setComplete(index);
}
```

### Using Built-in Calculations
```java
// Moving averages
series.sma(index, period, input)   // Simple MA
series.ema(index, period, input)   // Exponential MA
series.wma(index, period, input)   // Weighted MA

// Price data
series.getClose(index)
series.getHigh(index)
series.getLow(index)
series.getOpen(index)
series.getVolume(index)

// Retrieve stored values
series.getDouble(index, Values.KEY)
series.getBoolean(index, Signals.KEY)
```

### Minimum Bars Pattern
```java
@Override
public int getMinBars() {
    // Return minimum bars needed for calculation
    return getSettings().getInteger(Inputs.PERIOD) * 2;
}
```

---

## Settings and UI Patterns

### Dependency Management
```java
// Enable/disable fields based on checkbox
sd.addDependency(new EnabledDependency(
    ENABLE_FEATURE,           // Control field
    DEPENDENT_FIELD1,         // Fields to enable/disable
    DEPENDENT_FIELD2
));
```

### Path and Indicator Declaration
```java
// Declare what to draw
desc.declarePath(Values.MA, Inputs.PATH);        // Line
desc.declareBars(Values.HIST, Inputs.BAR);       // Histogram bars
desc.declareIndicator(Values.MA, Inputs.IND);    // Y-axis indicator

// Add reference lines
desc.addHorizontalLine(new LineInfo(0, null, 1.0f, new float[]{3,3})); // Dashed zero line
```

### Label Generation Pattern
```java
// Auto-generate label from settings
desc.setLabelSettings(Inputs.INPUT, Inputs.PERIOD);
// Results in: "StudyName(CLOSE, 20)"
```

---

## Strategy Patterns

### Strategy Study Header
```java
@StudyHeader(
    // ... standard fields ...
    strategy = true,           // Enable strategy features
    autoEntry = true,          // Support auto-entry
    manualEntry = false,       // Disable manual entry
    supportsUnrealizedPL = true,
    supportsRealizedPL = true,
    supportsTotalPL = true
)
```

### Strategy Implementation
```java
public class MyStrategy extends MyStudy { // Extend from indicator
    
    @Override
    public void onActivate(OrderContext ctx) {
        // Called when strategy is activated
        if (getSettings().isEnterOnActivate()) {
            // Enter position based on current state
            int qty = getSettings().getTradeLots() * ctx.getInstrument().getDefaultQuantity();
            ctx.buy(qty);  // or ctx.sell(qty)
        }
    }
    
    @Override
    public void onSignal(OrderContext ctx, Object signal) {
        // Handle signals
        int position = ctx.getPosition();
        int qty = getSettings().getTradeLots() * ctx.getInstrument().getDefaultQuantity();
        
        // Stop and reverse pattern
        qty += Math.abs(position);
        
        if (signal == Signals.BUY && position <= 0) {
            ctx.buy(qty);
        }
        if (signal == Signals.SELL && position >= 0) {
            ctx.sell(qty);
        }
    }
}
```

---

## Multi-Plot Studies

### Creating Multiple Plots
```java
@Override
public void initialize(Defaults defaults) {
    var sd = createSD();
    // ... settings ...
    
    var desc = createRD();
    
    // Main plot (price overlay)
    desc.declarePath(Values.MA, Inputs.PATH);
    
    // Create secondary plot
    Plot rsiPlot = new Plot();
    desc.addPlot("RSI", rsiPlot);
    rsiPlot.declarePath(Values.RSI, RSI_PATH);
    rsiPlot.addHorizontalLine(new LineInfo(70, null, 1.0f, null));
    rsiPlot.addHorizontalLine(new LineInfo(30, null, 1.0f, null));
    
    // Create tertiary plot
    Plot macdPlot = new Plot();
    desc.addPlot("MACD", macdPlot);
    macdPlot.declarePath(Values.MACD, MACD_PATH);
    macdPlot.declareBars(Values.HISTOGRAM, Inputs.BAR);
}
```

---

## State Management

### Best Practices for State

1. **DO NOT override clearState()** unless absolutely necessary
   - The framework handles this appropriately
   - If you must override, always call `super.clearState()` first

2. **DO NOT call clearFigures()** 
   - Can cause JavaFX threading issues
   - The framework handles figure updates

3. **Settings Update Pattern**
```java
@Override
public void onSettingsUpdated(DataContext ctx) {
    // Update any cached settings
    updateCachedSettings();
    
    // Clear internal state if needed
    clearInternalState();
    
    // Update minimum bars
    setMinBars(getSettings().getInteger(PERIOD));
    
    // Call super to trigger recalculation
    super.onSettingsUpdated(ctx);
}
```

4. **Buffer Management Pattern**
```java
private double[] buffer;
private int bufferStart = -1;

private void updateBuffer(DataSeries series, int index, int windowSize) {
    // Check if buffer needs reinitialization
    if (buffer == null || buffer.length != windowSize || 
        index < bufferStart || index >= bufferStart + windowSize) {
        
        buffer = new double[windowSize];
        bufferStart = Math.max(0, index - windowSize + 1);
        
        // Fill buffer
        for (int i = 0; i < windowSize; i++) {
            int idx = bufferStart + i;
            if (idx <= index) {
                buffer[i] = series.getClose(idx);
            }
        }
    }
}
```

---

## Performance Considerations

### Optimization Patterns

1. **Cache Settings in calculate()**
```java
private Integer cachedPeriod = null;

@Override
protected void calculate(int index, DataContext ctx) {
    // Cache settings to avoid repeated lookups
    if (cachedPeriod == null) {
        cachedPeriod = getSettings().getInteger(Inputs.PERIOD);
    }
    // Use cachedPeriod...
}
```

2. **Early Returns**
```java
// Return early if insufficient data
if (index < minRequired) return;

// Return early on null values
Double value = series.ema(index, period, input);
if (value == null) return;
```

3. **Sliding Window for Large Calculations**
```java
// Don't recalculate entire history, use sliding window
if (index > 0) {
    // Incremental update using previous value
    Double prev = series.getDouble(index - 1, Values.MA);
    if (prev != null) {
        // Update calculation based on previous
    }
}
```

---

## Common Pitfalls to Avoid

### ❌ DON'T Do This:

1. **Don't use string literals for keys**
```java
// BAD
series.setDouble(index, "myValue", value);

// GOOD
series.setDouble(index, Values.MY_VALUE, value);
```

2. **Don't override clearState() without calling super**
```java
// BAD
@Override
public void clearState() {
    myBuffer = null; // Missing super call!
}

// GOOD
@Override
public void clearState() {
    super.clearState();
    myBuffer = null;
}
```

3. **Don't call clearFigures()**
```java
// BAD - Can cause threading issues
ctx.clearFigures();
```

4. **Don't forget null checks**
```java
// BAD
double ma = series.ema(index, period, input);
double diff = ma - baseline; // NPE if ma is null!

// GOOD
Double ma = series.ema(index, period, input);
if (ma == null) return;
double diff = ma - baseline;
```

### ✅ DO This:

1. **Use enums for type safety**
2. **Check minimum data requirements**
3. **Handle null values gracefully**
4. **Use built-in calculations when available**
5. **Organize settings with tabs and groups**
6. **Export values for other studies to use**
7. **Set appropriate range keys for auto-scaling**
8. **Use resource bundles for internationalization**
9. **Cache frequently accessed settings**
10. **Call super methods when overriding lifecycle methods**

---

## Signal Generation Pattern

### Proper Signal Generation
```java
@Override
protected void calculate(int index, DataContext ctx) {
    // ... calculations ...
    
    // Check for signal conditions
    if (index > 0) {
        Double current = series.getDouble(index, Values.MA);
        Double previous = series.getDouble(index - 1, Values.MA);
        Double price = series.getClose(index);
        
        if (current != null && previous != null && price != null) {
            // Cross above
            if (previous <= price && current > price) {
                series.setBoolean(index, Signals.CROSS_ABOVE, true);
                ctx.signal(index, Signals.CROSS_ABOVE, "MA Cross Above", price);
            }
            // Cross below
            if (previous >= price && current < price) {
                series.setBoolean(index, Signals.CROSS_BELOW, true);
                ctx.signal(index, Signals.CROSS_BELOW, "MA Cross Below", price);
            }
        }
    }
}
```

---

## Summary

The MotiveWave SDK follows clear patterns:
1. **Settings** are defined in `initialize()` using descriptors
2. **Calculations** happen in `calculate()` for each bar
3. **State management** should be minimal and careful
4. **Strategies** extend indicators and add trading logic
5. **Multiple plots** can be added for complex indicators
6. **Performance** is achieved through caching and early returns
7. **Signals** are generated during calculation and consumed by strategies

Following these patterns ensures robust, maintainable, and performant MotiveWave studies.