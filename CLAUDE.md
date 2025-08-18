# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Morphiq Suite MotiveWave is a multi-module Maven project that provides advanced wavelet-based trading indicators for the MotiveWave platform. The project uses Java 23 and leverages parallel processing for high-performance signal analysis.

## Recent Updates (August 2024)

### Module Consolidation (August 15, 2024)
- **Project Structure Simplified**:
  - Consolidated from 5 modules to 2 modules
  - Removed `morphiq-core`, `morphiq-denoise`, and `morphiq-bundle-premium`
  - All wavelet functionality now in `morphiq-wavelets`
  - Shared utilities and position management in `morphiq-common`
  
- **Position Management Improvements**:
  - Converted Order Type from string constants to OrderType enum
  - Moved all position management code to `morphiq-common`
  - Better type safety and maintainability

## Previous Updates (August 2024)

### Strategy Signal System Refactoring (August 14, 2024)
- **Position Tracking Improvements**:
  - Replaced manual position tracking (hasPosition/isLong) with OrderContext-based tracking
  - Added helper methods: `hasPosition(ctx)`, `isLong(ctx)`, `isShort(ctx)`, `getPosition(ctx)`
  - Strategy now uses OrderContext as single source of truth for position state
  - Improved thread safety by relying on MotiveWave's position management

- **Signal System Changes**:
  - Changed from action-based signals (LONG_ENTER, SHORT_ENTER) to state-based signals (LONG, SHORT)
  - Removed FLAT_EXIT signal entirely - study only reports market state
  - Strategy decides entry/exit actions based on state signals
  - Proper separation of concerns: study reports state, strategy makes trading decisions

- **Bracket Order Implementation**:
  - Standardized bracket orders for both long and short entries
  - Removed ENABLE_BRACKET_ORDERS setting - bracket orders are now mandatory
  - Each entry creates three orders with unique UUIDs:
    - Market order for entry
    - Stop loss order at calculated stop price
    - Take profit order at calculated target price
  - Consistent order submission using `ctx.submitOrders()`

- **Entry Logic Updates**:
  - Long state signal: enters long if flat, exits short then enters long if short
  - Short state signal: enters short if flat, exits long then enters short if long
  - Prevents duplicate entries in same direction
  - Automatic position reversal on opposite signals

### Documentation Corrections & Performance Optimization (August 13, 2024)
- Fixed slope threshold documentation across all files
- Clarified that Min Slope Threshold is in absolute price points, NOT percentage
- Updated market-specific configurations with correct units
- Major optimization of VectorWaveSwtAdapter.reconstruct() method:
  - **Eliminated deep copy**: Now works directly with original MutableMultiLevelMODWTResult
  - **Removed unnecessary conversion**: No more toImmutable() -> new MutableMultiLevelMODWTResultImpl()
  - **Temporary modification strategy**: Zeros coefficients, reconstructs, then restores
  - **Significant performance gain**: Avoids expensive object allocation on every reconstruction
  - **Memory efficient**: Only allocates small arrays for coefficient backup
- Fixed non-deterministic test behavior in VectorWaveSwtAdapterTest
  - All test methods now use the seeded Random instance
  - Ensures consistent, reproducible test results
- Clarified testing approach for calculateFinalQuantity method
  - Method exists and is package-private (accessible to tests in same package)
  - Core logic tested via static calculatePositionSize method
  - Integration tests handle full SDK initialization separately
  - Removed duplicate test file (CalculateFinalQuantityTest.java)
- Enhanced thread safety in WaveletAtr.calculate() method:
  - Now makes defensive copies of input arrays to prevent concurrent modification issues
  - Protects against ArrayIndexOutOfBoundsException from external array modifications
  - Verified with comprehensive concurrent access tests (5000+ operations)
  - Maintains performance while ensuring correctness

### Trade Lots Integration
- Fixed Trade Lots multiplication in `SwtTrendMomentumStrategy`
- Final position = Position Size Factor × Trade Lots
- Comprehensive logging of position calculations

### Momentum Oscillator Enhancement
- Added 100x scaling factor for better visibility
- Updated default threshold from 0.01 to 1.0
- Increased EMA smoothing alpha to 0.5
- Expanded momentum window to 10 bars

### Thread Safety Improvements
- All buffer operations synchronized with `bufferLock`
- Momentum state declared `volatile`
- WATR calculations use `stateLock` synchronization

### Bug Fixes
- Resolved JavaFX NullPointerExceptions
- Fixed Maven Shade overlapping class warnings
- Removed Point Value Override (now auto-detected)
- Fixed order fill validation
- Fixed slope threshold from 0.001 to 0.05 (50x increase for proper signal generation)

### Code Quality Improvements
- Removed ~65 lines of dead code from SwtTrendMomentumStrategy
- Preserved P&L tracking logic in docs/REFERENCE_PNL_TRACKING.md for future reference
- Optimized VectorWaveSwtAdapter.reconstruct() to cache adapter and reduce object creation
  - Caches VectorWaveSwtAdapter instance to avoid recreation
  - Tracks last reconstruction level to avoid unnecessary MutableMultiLevelMODWTResult recreations
  - Reduces GC pressure in high-frequency scenarios (called on every bar update)

## Build Commands

```bash
# Build entire project
mvn clean package

# Build with dependencies update
mvn clean package -U

# Run tests
mvn test

# Run specific test
mvn test -Dtest=WaveletDenoiserTest

# Build without tests (faster)
mvn clean package -DskipTests

# Install to local repository
mvn clean install
```

## Architecture

### Module Structure
- **morphiq-common**: Shared utilities and core components
  - `LoggerConfig`: Centralized logging configuration
  - `position/`: Complete position management framework
    - `PositionManager`: Position and order management with OrderType enum
    - `PositionSizer`: Risk-based position sizing calculations
    - `PositionTracker`: Thread-safe position state tracking
    - `OrderBundle`: Multi-order management for bracket orders
  - `wavelets/`: Wavelet configuration
    - `WaveletType`: Available wavelet types
    - `WaveletOptionsProvider`: Wavelet options interface
  - `StudyUIHelper`: UI component builders for consistent settings
  
- **morphiq-wavelets**: All wavelet indicators and strategies
  - `Wavelets`: Automatic wavelet decomposition indicator (AutoWavelets)
  - `swt/`: SWT/MODWT trend following system
    - `SwtTrendMomentumStudy`: Complete indicator with trend and momentum
    - `SwtTrendMomentumStrategy`: Automated trading strategy
    - `core/`: Core wavelet processing
      - `VectorWaveSwtAdapter`: Bridge to VectorWave SWT implementation
      - `Thresholds`: Universal, BayesShrink, SURE thresholding
      - `WaveletAtr`: RMS energy-based volatility estimation

### Key Design Patterns
1. **Parallel Processing**: Automatic parallelization for datasets ≥ 512 points
2. **MotiveWave Integration**: All indicators extend `com.motivewave.platform.sdk.study.Study`
3. **Maven Shade Plugin**: Creates fat JARs with all dependencies included
4. **Modular Architecture**: Each indicator is independently deployable
5. **Sliding Window Buffers**: Efficient streaming updates for real-time processing
6. **Logging Guards**: SLF4J `isDebugEnabled()`/`isTraceEnabled()` checks to avoid computation

## MotiveWave Development Reference Documentation

When developing MotiveWave studies and strategies, **ALWAYS consult these comprehensive reference documents**:

### 📚 Required Reading for MotiveWave Development

1. **`docs/MOTIVEWAVE_PATTERNS_AND_PRACTICES.md`**
   - Study structure patterns and best practices
   - Initialization, calculation, and settings patterns
   - Strategy implementation patterns
   - Common pitfalls and how to avoid them
   - Signal generation and marker management
   - State management best practices

2. **`docs/MOTIVEWAVE_SDK_COMPLETE_REFERENCE.md`**
   - Complete SDK API reference
   - DataContext and DataSeries method signatures
   - OrderContext for trading strategies
   - All SDK enumerations (BarInput, MAMethod, etc.)
   - Advanced calculation methods and optimizations
   - Complete settings descriptors reference
   - Performance optimization techniques
   - Full study template with all features

### ⚠️ Critical MotiveWave Rules (MUST FOLLOW)

1. **NEVER call `clearFigures()`** - Causes JavaFX threading issues
2. **NEVER override `clearState()` without calling `super.clearState()` first**
3. **ALWAYS check for null** when using DataSeries calculation methods
4. **ALWAYS use SDK enums** (Enums.BarInput, Enums.MAMethod, etc.) for type safety
5. **ALWAYS use built-in calculations** from DataSeries when available (30+ methods)
6. **NEVER use string literals for keys** - Use enums for Values and Signals

### 🎯 Development Checklist for New Studies

- [ ] Review both reference documents before starting
- [ ] Use the complete study template from SDK reference as starting point
- [ ] Follow the Tab → Group → Row pattern for settings organization
- [ ] Implement proper null checking for all calculations
- [ ] Use incremental calculation patterns for performance
- [ ] Test with different bar sizes and data ranges
- [ ] Verify signals fire correctly
- [ ] Check memory usage with large datasets

### Dependencies
- Java 23 - target platform
- MotiveWave SDK (v20230627) - provided scope
- VectorWave (v1.0-SNAPSHOT) - high-performance wavelet transforms with financial analysis
- SLF4J Simple (v2.0.17) - logging
- JUnit 5 (v5.13.2) & Mockito (v5.14.2) - testing

## Testing Strategy

Tests are located in module-specific test directories:
- Unit tests for mathematical functions
- Integration tests for wavelet transforms
- Parallel processing verification
- Non-power-of-two data handling
- Performance benchmarking tests
- Thread safety verification tests

### Test System Properties
The following system properties can be used to configure test behavior:

- **`momentum.perf.iterations`**: Controls iterations for momentum type performance tests
  - Default: 100,000 (balances CI speed vs accuracy)
  - Usage: `mvn test -Dmomentum.perf.iterations=1000000`
  - Values:
    - 10,000: Quick smoke test (may have JIT warmup noise)
    - 100,000: Default for CI, runs in ~10ms
    - 1,000,000+: Detailed local testing with stable results

## CI/CD Pipeline

GitHub Actions workflow (`.github/workflows/maven.yml`):
- Two-stage pipeline: build and test
- Requires GITLAB_TOKEN and GITHUB_TOKEN secrets
- Uploads artifacts for distribution
- Maven settings configured for GitHub Packages and GitLab Maven repositories

## Development Notes

### MotiveWave Study Development
- Studies must implement `com.motivewave.platform.sdk.study.Study`
- Use `StudyDescriptor` for configuration
- Override `calculate()` method for main logic
- Access price data via `DataContext`

### Wavelet Processing
- Core processing in `morphiq-wavelets` module
- Supports multiple wavelet types (Daubechies4, Daubechies6)
- Automatic parallel processing for performance
- Handles non-power-of-two data lengths

### Future Features
The `CUSTOM_WAVELET_DESIGN.md` outlines advanced features including:
- Custom ES/NQ microstructure-optimized wavelets
- Machine learning integration for coefficient optimization
- Real-time market regime adaptation
- Multi-scale wavelet families

### Important Files
- Parent POM: `/pom.xml` - manages all dependencies and plugin versions
- Common utilities: `/morphiq-common/src/main/java/com/morphiqlabs/common/`
- SWT Strategy: `/morphiq-wavelets/src/main/java/com/morphiqlabs/wavelets/swt/`
- Documentation: `/docs/SWT_TREND_MOMENTUM_DOCUMENTATION.md`

## Important Considerations

### When Working with Settings
- **Momentum Threshold**: Now scaled by 100x (use 1.0 instead of 0.01)
- **Min Slope Threshold**: Absolute price points (0.05 = 0.05 point minimum move, NOT percentage)
  - FIXED: Changed from percentage-based to absolute points
  - 0.00 disables filtering, 0.05-0.10 typical for ES futures
- **Trade Lots**: Properly multiplies position size
- **Point Value**: Auto-detected from instrument (ES=$50, NQ=$20)

### Thread Safety Requirements
- Always synchronize buffer operations with `bufferLock`
- Mark momentum-related fields as `volatile`
- Use separate lock objects for different state groups
- Never call `clearFigures()` outside `onSettingsUpdated()`

### Maven Build Configuration
- Use `shadedArtifactAttached=true` to avoid JAR conflicts and overlapping class warnings
- Classifier `motivewave` creates separate shaded JAR for MotiveWave deployment
- Dependencies included: morphiq-common, vector-wave, slf4j
- Shaded JAR includes all necessary dependencies for MotiveWave

### Testing Best Practices
- Use instance-specific Random with fixed seeds
- Test thread safety with parallel execution
- Verify Trade Lots multiplication
- Check momentum scaling (100x factor)