# Known Issues and Status

## Current Implementation Status

The Morphiq Suite has successfully integrated VectorWave for high-performance wavelet analysis. This document tracks current issues and their resolutions.

## ✅ Resolved Issues

### Marker Persistence
- **Resolution**: Fixed by following MotiveWave SDK pattern
- **Solution**: Only add markers when `series.isBarComplete(index)` is true
- **Implementation**: Removed `calculateValues()` override, use only `calculate(int index, DataContext ctx)`

### Momentum Calculation
- **Resolution**: Implemented RMS energy-based momentum
- **Solution**: Multi-scale weighted RMS with exponential smoothing
- **Implementation**: See `calculateMomentumSum()` in SwtTrendMomentumStudy

### Signal Balance
- **Resolution**: Fixed signal generation logic
- **Solution**: Proper state transitions with exit-before-reversal pattern
- **Implementation**: Updated `generateSignals()` method

### Performance Optimization
- **Resolution**: Implemented sliding window buffers
- **Solution**: O(1) updates for streaming data
- **Implementation**: `updateSlidingWindow()` method with incremental updates

### Logging Performance
- **Resolution**: Added proper guards
- **Solution**: Use `logger.isDebugEnabled()` and `logger.isTraceEnabled()`
- **Implementation**: All expensive logging now guarded

## ⚠️ Current Considerations

### 1. Momentum Threshold Sensitivity
- **Status**: Working but needs tuning
- **Description**: Default thresholds may need adjustment per market
- **Recommendation**: Use settings provided in documentation for specific timeframes
- **Workaround**: Start with 0.000 threshold and gradually increase

### 2. Window Size for Short Timeframes
- **Status**: Functional with caveats
- **Description**: 1-minute charts with 512+ window may be slow to initialize
- **Recommendation**: Use 256 window for 1-minute, 512 for 5-minute+
- **Impact**: Initial calculation delay on first load

### 3. Plot Auto-Scaling
- **Status**: Working with mode dependency
- **Description**: Momentum plot scaling differs between SUM and SIGN modes
- **Current Behavior**: 
  - SUM mode: Auto-scaling (dynamic range)
  - SIGN mode: Fixed range based on k parameter

## 🚧 Future Enhancements

### Custom Wavelets for ES/NQ
- **Status**: Planned
- **Description**: Market microstructure-optimized wavelets
- **Impact**: Better signal quality for specific instruments
- **Timeline**: Future release

### Machine Learning Integration
- **Status**: Research phase
- **Description**: Adaptive coefficient optimization
- **Components**: Market regime detection, dynamic thresholds

### Multi-Timeframe Synchronization
- **Status**: Design phase
- **Description**: Aligned signals across timeframes
- **Benefit**: Reduced false signals, better trend confirmation

## 📋 Configuration Notes

### Memory Usage
| Window Size | Memory | Suitable For |
|------------|---------|--------------|
| 256 | ~5MB | Scalping (1-min) |
| 512 | ~10MB | Day trading (5-min) |
| 1024 | ~15MB | Swing (15-min+) |
| 2048 | ~20MB | Position (1H+) |
| 4096 | ~30MB | Long-term (Daily) |

### CPU Performance
- Parallel processing activates at ≥512 data points
- SIMD optimizations via VectorWave
- Typical calculation: <10ms per bar

## 🐛 Bug Reporting

To report issues:
1. Check this document first
2. Include MotiveWave version
3. Specify indicator settings
4. Attach screenshots if visual issue
5. Report at: [GitHub Issues](https://github.com/Prophetizo/morphiq-motivewave/issues)

## 📝 Version Compatibility

| Component | Version | Status |
|-----------|---------|--------|
| MotiveWave SDK | 20230627+ | ✅ Supported |
| VectorWave | 1.0.0+ | ✅ Integrated |
| Java | 21+ | ✅ Required |
| Maven | 3.6+ | ✅ Build tool |

## 🔧 Troubleshooting Guide

### Markers Not Appearing
1. Check "Enable Trading Signals" is checked
2. Verify Momentum Threshold (try 0.000)
3. Ensure markers are enabled in Display settings
4. Restart MotiveWave after JAR update

### Plots Not Updating
1. Click "Update" in settings dialog
2. Check data connection is active
3. Verify sufficient historical data
4. Try different timeframe

### Performance Issues
1. Reduce window size
2. Disable WATR bands if not needed
3. Close other indicators
4. Increase MotiveWave heap size

### Signal Quality Issues
1. Adjust momentum threshold for market
2. Try different wavelet type
3. Check timeframe-specific settings
4. Review market conditions (trending vs ranging)

---

Last Updated: 2024
Version: 1.0.0
