# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Vision

This project aims to create a **commercial wavelet-based trading analysis package** for MotiveWave, distributed through online stores (Shopify). The goal is to leverage wavelet transforms' unique ability to analyze financial data across multiple time scales simultaneously, providing traders with sophisticated tools for identifying trends, cycles, and trading opportunities.

**Commercial Objective**: Build a premium trading software package with multiple licensing tiers, professional support, and comprehensive customer success infrastructure.

## Trading Strategies Roadmap

### Phase 1 - Foundation (COMPLETED ✅)
- [x] **AutoWavelets** - MODWT decomposition with dynamic levels based on timeframe
  - Automatically scales decomposition levels (1-7) to match trading session duration (~390 minutes)
  - Dynamically enables/disables paths based on calculated levels
  - **NEW**: Configurable wavelet selection (Daubechies4, Daubechies6) via dropdown
  - **NEW**: DiscreteDescriptor-based UI controls for better user experience

- [x] **Denoised Trend Following** ✅ COMPLETED
  - Zero out D1/D2 coefficients to remove market microstructure noise
  - Reconstruct cleaner price series for traditional indicators
  - Reduce false signals and whipsaws in trend following systems
  - **NEW**: Separate noise visualization pane with zero reference line
  - **NEW**: Configurable threshold types (HARD, SOFT, ADAPTIVE) via dropdown
  - **NEW**: Advanced thresholding with adaptive noise level detection

- [ ] **Multi-Timeframe Energy Burst Detection**
  - Calculate energy (Σcoefficient²) for each decomposition level
  - Identify momentum injections at specific time scales
  - Align energy spikes with broader trend direction

### Phase 2 - Enhanced Analysis
- [ ] **Wavelet Support/Resistance Detection**
  - Monitor zero-crossings in detail coefficients as reversal zones
  - Track coefficient magnitude peaks for key price levels
  - Multi-scale confluence for stronger S/R identification

- [ ] **Volatility Regime Monitor**
  - Track wavelet packet energy distribution across scales
  - Detect regime shifts through energy redistribution
  - Adaptive strategy selection based on market state

- [ ] **Cycle Extraction and Prediction**
  - Hilbert transform on coefficients for phase analysis
  - Calculate instantaneous period per decomposition level
  - Project future cycle turns based on phase progression

### Phase 3 - Advanced Strategies
- [ ] **Wavelet Coherence Pairs Trading (ES vs NQ)**
  - Analyze time-frequency correlation between instruments
  - Trade divergences when coherence breaks down
  - Lead-lag analysis via phase relationships

- [ ] **Wavelet-Based Momentum Oscillators**
  - Rate of change calculations on wavelet coefficients
  - Scale-specific divergence detection
  - Adaptive overbought/oversold zones

### Phase 4 - Flagship Feature (The Penultimate Achievement)
- [x] **Custom ES/NQ Microstructure-Optimized Wavelet** 🏆 Flagship ✅ COMPLETED
  - AI-designed wavelet specifically for ES/NQ microstructure patterns
  - Real-time adaptation based on trading session and market conditions
  - Machine learning continuous improvement and performance tracking
  - Multi-scale family (scalping, intraday, swing, news events)
  - Session-specific coefficient optimization (opening, lunch, afternoon, close)
  - Time-of-day and volatility regime adaptive responses
  - Comprehensive backtesting and validation framework
  - Integration with all other wavelet strategies as the ultimate analytical engine

## Recent Progress & GitHub Issues

### Completed Recent Work ✅
- **Dropdown Controls Implementation**: Both AutoWavelets and DenoisedTrendFollowing now use DiscreteDescriptor for proper dropdown UIs
- **Maven Build Cleanup**: Eliminated all Maven warnings, optimized compiler configuration
- **GitHub Issue Tracking**: All remaining enhancements converted to tracked GitHub issues

### Active GitHub Issues 🚀

#### Core Technical Features
- **Issue #4**: [Implement A/B testing capability for db4 vs db6 comparison](https://github.com/Prophetizo/motivewave-wavelets/issues/4)
- **Issue #5**: [Document wavelet selection tradeoffs in STRATEGIES.md](https://github.com/Prophetizo/motivewave-wavelets/issues/5)  
- **Issue #6**: [Design custom wavelets for JWave fork (Morlet, Mexican Hat)](https://github.com/Prophetizo/motivewave-wavelets/issues/6)
- **Issues #7-12**: Phase 2-3 Advanced Trading Strategies
- **Issues #14-18**: Flagship Custom ES/NQ Wavelet Implementation

#### Commercial Infrastructure 💼
- **Issue #19**: [Commercial Licensing and Distribution Strategy](https://github.com/Prophetizo/motivewave-wavelets/issues/19)
- **Issue #20**: [Professional Documentation and User Guide Creation](https://github.com/Prophetizo/motivewave-wavelets/issues/20)
- **Issue #21**: [Performance Validation and Marketing Case Studies](https://github.com/Prophetizo/motivewave-wavelets/issues/21)
- **Issue #22**: [Professional Software Packaging and Distribution System](https://github.com/Prophetizo/motivewave-wavelets/issues/22)
- **Issue #23**: [Customer Support and Success Infrastructure](https://github.com/Prophetizo/motivewave-wavelets/issues/23)
- **Issue #24**: [Marketing Website and Sales Funnel Development](https://github.com/Prophetizo/motivewave-wavelets/issues/24)

## Commercial Product Tiers

### Wavelet Essentials ($199)
- AutoWavelets with dynamic scaling
- Denoised Trend Following
- Basic documentation and support
- 30-day trial, annual license

### Wavelet Professional ($499)  
- All Essentials features
- Multi-Timeframe Energy Burst Detection
- Wavelet Support/Resistance Detection
- Volatility Regime Monitor
- Priority support and training

### Wavelet Enterprise ($999)
- All Professional features
- Wavelet Coherence Pairs Trading
- Custom ES/NQ Optimized Wavelet
- Advanced momentum oscillators
- White-glove support and custom training

### Institutional License ($2499)
- Complete package with all features
- Multi-user licensing
- API access for algorithmic trading
- Custom development and consultation
- Dedicated account management

## Build Commands

```bash
# Clean and build
mvn clean compile

# Package (creates JAR)
mvn clean package

# Run all tests
mvn test

# Run specific test
mvn test -Dtest=MovingAverageTest

# Install to local repository
mvn clean install
```

## Architecture Overview

This is a MotiveWave trading platform extension that performs wavelet analysis on financial price data. Key architectural components:

### MotiveWave Integration
- **AutoWavelets** (`src/main/java/com/prophetizo/motivewave/studies/AutoWavelets.java`): Main Study implementation that integrates with MotiveWave
- **DenoisedTrendFollowing** (`src/main/java/com/prophetizo/motivewave/studies/DenoisedTrendFollowing.java`): Wavelet-based denoising for cleaner trend analysis
- Uses Study pattern (not Strategy) for visualization and analysis
- Registered under "Prophetizo, LLC" menu in MotiveWave
- Both studies feature modern dropdown controls via DiscreteDescriptor

### Data Flow Pipelines
```
AutoWavelets:
Price Data → AutoWavelets.calculate() → WaveletAnalyzer → MODWT Transform → Plot Coefficients

DenoisedTrendFollowing:
Price Data → DenoisedTrendFollowing.calculate() → WaveletDenoiser → Threshold/Reconstruct → Denoised Signal
```

### Core Components
1. **WaveletAnalyzer** (`src/main/java/com/prophetizo/wavelets/WaveletAnalyzer.java`): Wraps JWave's MODWT implementation with configurable wavelet selection
2. **WaveletDenoiser** (`src/main/java/com/prophetizo/wavelets/WaveletDenoiser.java`): Advanced coefficient manipulation with multiple thresholding algorithms
3. **Auto-scaling Logic**: Automatically determines decomposition levels based on bar size (targets ~390 minute trading sessions)
4. **Visualization**: Up to 7 decomposition levels (D1-D7) displayed as colored paths
5. **UI Controls**: DiscreteDescriptor-based dropdown controls for all configuration options

### Key Design Patterns
- **Adapter Pattern**: WaveletAnalyzer simplifies JWave interface
- **Configuration**: Settings managed through MotiveWave's SettingsDescriptor
- **Logging**: Custom async logging with Logstash JSON encoding

### Dependencies
- **MotiveWave SDK** (20230627): Private GitLab repository - requires authentication
- **JWave** (1.0.6): Wavelet transforms - GitHub packages
- **Java 21+** required

### Maven Configuration
- Shade plugin creates uber-JAR excluding MotiveWave SDK
- Output: `target/motivewave-wavelets-1.0.0-SNAPSHOT-shaded.jar`

## Development Notes

### Adding New Wavelets
✅ **COMPLETED**: Both AutoWavelets and DenoisedTrendFollowing now support configurable wavelet selection via dropdown controls. Current options: Daubechies4, Daubechies6. Additional wavelets can be added by extending the WaveletType enum and updating the dropdown options.

### Testing MotiveWave Integration
The AutoWavelets study cannot be unit tested directly due to MotiveWave SDK dependencies. Test the underlying components (WaveletAnalyzer, Statistics, MovingAverage) instead.

### Logging
Logs written to `/tmp/prophetizo_motivewave.log` with async queue size of 100,000 messages. Configuration in `src/main/resources/logger.properties`.