# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Morphiq Suite MotiveWave is a multi-module Maven project that provides advanced wavelet-based trading indicators for the MotiveWave platform. The project uses Java 21 and leverages parallel processing for high-performance signal analysis.

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
- **morphiq-core**: Core wavelet processing library with utilities
  - `WaveletAnalyzer`: Parallel wavelet transform implementation
  - `WaveletDenoiser`: Signal denoising using wavelet thresholding
  - Mathematical utilities: `MovingAverage`, `Statistics`
  
- **morphiq-common**: Shared MotiveWave utilities
  - `StudyUIHelper`: UI component builders for consistent settings
  
- **morphiq-autowave**: Automatic wavelet decomposition indicator
  - `AutoWavelets`: Multi-level decomposition visualization
  
- **morphiq-denoise**: SWT/MODWT trend following system
  - `SwtTrendMomentumStudy`: Complete indicator with trend and momentum
  - `SwtTrendMomentumStrategy`: Automated trading strategy
  - `VectorWaveSwtAdapter`: Bridge to VectorWave SWT implementation
  - `Thresholds`: Universal, BayesShrink, SURE thresholding
  - `WaveletAtr`: RMS energy-based volatility estimation
  
- **morphiq-bundle-premium**: Bundle packaging all indicators into single JAR

### Key Design Patterns
1. **Parallel Processing**: Automatic parallelization for datasets ≥ 512 points
2. **MotiveWave Integration**: All indicators extend `com.motivewave.platform.sdk.study.Study`
3. **Maven Shade Plugin**: Creates fat JARs with all dependencies included
4. **Modular Architecture**: Each indicator is independently deployable
5. **Sliding Window Buffers**: Efficient streaming updates for real-time processing
6. **Logging Guards**: SLF4J `isDebugEnabled()`/`isTraceEnabled()` checks to avoid computation

### Dependencies
- MotiveWave SDK (v20230627) - provided scope
- VectorWave (v1.0.0) - high-performance wavelet transforms with financial analysis
- SLF4J Simple (v2.0.17) - logging
- JUnit 5 & Mockito - testing

## Testing Strategy

Tests are located in `morphiq-core/src/test/java/`:
- Unit tests for mathematical functions
- Integration tests for wavelet transforms
- Parallel processing verification
- Non-power-of-two data handling

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
- Core processing in `morphiq-core` module
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
- Core utilities: `/morphiq-core/src/main/java/com/prophetizo/wavelets/`
- Study implementations: `/morphiq-*/src/main/java/com/prophetizo/studies/`