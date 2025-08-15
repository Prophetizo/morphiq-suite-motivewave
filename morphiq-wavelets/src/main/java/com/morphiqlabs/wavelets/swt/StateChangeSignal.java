package com.morphiqlabs.wavelets.swt;

/**
 * Represents a market state change event with detailed information about what changed.
 * Used by the SWT Study to report state changes to strategies, allowing strategies
 * to make their own trading decisions based on the market state information.
 * 
 * This design follows separation of concerns - the study reports what changed,
 * the strategy decides what to do about it.
 */
public class StateChangeSignal {
    
    /**
     * The type of state change that occurred.
     */
    public enum SignalType {
        // Slope state changes
        SLOPE_TURNED_POSITIVE,
        SLOPE_TURNED_NEGATIVE,
        
        // Momentum state changes  
        MOMENTUM_CROSSED_POSITIVE,
        MOMENTUM_CROSSED_NEGATIVE,
        MOMENTUM_THRESHOLD_EXCEEDED,
        MOMENTUM_THRESHOLD_LOST
    }
    
    private final SignalType type;
    private final double oldValue;
    private final double newValue;
    private final double magnitude;
    private final long timestamp;
    
    /**
     * Creates a new state change signal.
     * 
     * @param type The type of state change
     * @param oldValue The previous value before the change
     * @param newValue The current value after the change
     * @param magnitude The magnitude/strength of the change (positive value)
     * @param timestamp The timestamp when the change occurred
     */
    public StateChangeSignal(SignalType type, double oldValue, double newValue, double magnitude, long timestamp) {
        this.type = type;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.magnitude = Math.abs(magnitude);
        this.timestamp = timestamp;
    }
    
    /**
     * Convenience constructor for threshold-based signals.
     * 
     * @param type The type of state change
     * @param oldValue The previous value before the change
     * @param newValue The current value after the change
     * @param timestamp The timestamp when the change occurred
     */
    public StateChangeSignal(SignalType type, double oldValue, double newValue, long timestamp) {
        this(type, oldValue, newValue, Math.abs(newValue - oldValue), timestamp);
    }
    
    // Getters
    public SignalType getType() { return type; }
    public double getOldValue() { return oldValue; }
    public double getNewValue() { return newValue; }
    public double getMagnitude() { return magnitude; }
    public long getTimestamp() { return timestamp; }
    
    /**
     * Returns true if this is a slope-related signal.
     */
    public boolean isSlopeSignal() {
        return type == SignalType.SLOPE_TURNED_POSITIVE || type == SignalType.SLOPE_TURNED_NEGATIVE;
    }
    
    /**
     * Returns true if this is a momentum-related signal.
     */
    public boolean isMomentumSignal() {
        return type == SignalType.MOMENTUM_CROSSED_POSITIVE || 
               type == SignalType.MOMENTUM_CROSSED_NEGATIVE ||
               type == SignalType.MOMENTUM_THRESHOLD_EXCEEDED ||
               type == SignalType.MOMENTUM_THRESHOLD_LOST;
    }
    
    /**
     * Returns true if this signal indicates a positive direction change.
     */
    public boolean isPositiveChange() {
        return type == SignalType.SLOPE_TURNED_POSITIVE || 
               type == SignalType.MOMENTUM_CROSSED_POSITIVE ||
               type == SignalType.MOMENTUM_THRESHOLD_EXCEEDED;
    }
    
    /**
     * Returns true if this signal indicates a negative direction change.
     */
    public boolean isNegativeChange() {
        return type == SignalType.SLOPE_TURNED_NEGATIVE || 
               type == SignalType.MOMENTUM_CROSSED_NEGATIVE ||
               type == SignalType.MOMENTUM_THRESHOLD_LOST;
    }
    
    @Override
    public String toString() {
        return String.format("%s[%s -> %s, magnitude=%.4f]", 
            type.name(), 
            String.format("%.4f", oldValue),
            String.format("%.4f", newValue),
            magnitude);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        StateChangeSignal that = (StateChangeSignal) obj;
        return Double.compare(that.oldValue, oldValue) == 0 &&
               Double.compare(that.newValue, newValue) == 0 &&
               Double.compare(that.magnitude, magnitude) == 0 &&
               timestamp == that.timestamp &&
               type == that.type;
    }
    
    @Override
    public int hashCode() {
        int result = type.hashCode();
        long temp = Double.doubleToLongBits(oldValue);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(newValue);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(magnitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
        return result;
    }
}