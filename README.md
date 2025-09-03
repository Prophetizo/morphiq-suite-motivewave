# Morphiq Suite for MotiveWave

Professional wavelet-based trading indicators and strategies for the MotiveWave platform, powered by VectorWave's high-performance wavelet transforms.

## Overview

Morphiq Suite provides advanced wavelet analysis tools for systematic trading:
- **SWT/MODWT Implementation** - Undecimated transforms for shift-invariant analysis
- **Cross-Scale Momentum** - Multi-resolution signal confirmation with 100x scaled oscillator
- **Adaptive Denoising** - Three thresholding methods (Universal, BayesShrink, SURE)
- **Wavelet-ATR (WATR)** - RMS energy-based volatility estimation from detail coefficients
- **Real-time Performance** - Optimized sliding window buffers and parallel processing
- **Position Management** - Advanced order tracking with multi-order support and bracket orders

## Indicators & Strategies

### 📈 SWT Trend + Momentum
Complete trading system using Stationary Wavelet Transform:
- **Study**: Overlay indicator with trend line and momentum oscillator
- **Strategy**: Automated trading with bracket orders and position sizing
- **Features**: State-based signals (LONG/SHORT), RMS energy momentum, WATR-based stops
- **Enhancements**: 100x momentum scaling, Trade Lots integration, thread-safe buffers
- [Full Documentation](docs/SWT_TREND_MOMENTUM_DOCUMENTATION.md)

### 📊 AutoWavelets
Multi-level wavelet decomposition visualization:
- Real-time decomposition across multiple scales
- Dynamic level adjustment based on timeframe
- Visual representation of market structure

### 🔇 Denoised Trend Following
Classic wavelet denoising for cleaner price action:
- Adaptive noise reduction
- Preserves important market moves
- Reduced false signals in choppy markets

## Technical Features

### Wavelet Families
- **Daubechies** (db4, db6): Smooth, good for trends
- **Symlets** (sym4, sym6): Near-symmetric, balanced
- **Coiflets** (coif2, coif4): Compact support
- **Haar**: Simple, good for breakouts

### Advanced Algorithms
- **Thresholding Methods**: Universal, BayesShrink, SURE
- **Shrinkage Types**: Soft and hard thresholding
- **Sliding Window**: Efficient streaming updates with 512-bar buffer
- **Cross-Scale Analysis**: Multi-resolution momentum confirmation
- **Position Sizing**: Factor-based with Trade Lots multiplication
- **Risk Management**: Automated bracket orders with stop loss and take profit
- **Order Management**: UUID-based tracking with OrderBundle for multi-order strategies
- **Position Tracking**: Real-time P&L, risk/reward ratios, and trailing stops

## Quick Start

### Prerequisites
- Java 23+
- Maven 3.6+ (3.9+ recommended)
- MotiveWave Platform (latest version)

### Installation

```bash
# Clone the repository
git clone https://github.com/Prophetizo/morphiq-suite-motivewave.git
cd morphiq-suite-motivewave

# Build the project
mvn clean package

# Install to MotiveWave
cp morphiq-wavelets/target/morphiq-wavelets-*-motivewave.jar ~/Documents/MotiveWave/studies/
```

### Usage in MotiveWave

1. Restart MotiveWave after copying the JAR
2. Right-click on a chart → "Add Study"
3. Navigate to "Wavelets" menu
4. Select desired wavelet indicator

## Documentation

- **[SWT Strategy Documentation](docs/SWT_TREND_MOMENTUM_DOCUMENTATION.md)** - Complete guide to the SWT/MODWT trading system
- **[Position Manager Guide](docs/POSITION_MANAGER_GUIDE.md)** - Comprehensive guide to the position management framework
- **[SWT White Paper](docs/white_papers/SWT_TREND_MOMENTUM_WHITEPAPER.md)** - Technical white paper on the trading methodology
- **[API Reference](API_REFERENCE.md)** - Class and method reference
- **[AGENTS.md](AGENTS.md)** - Development guide for AI assistants
- **[Architecture Docs](docs/architecture/)** - System design and specifications
- **[Blog Posts](docs/blog/)** - Educational articles on wavelet trading

## Project Structure

```
morphiq-suite-motivewave/
├── morphiq-common/         # Shared utilities & position management
│   ├── LoggerConfig       # Centralized logging
│   ├── position/          # Position management framework
│   └── wavelets/          # Wavelet types & options
├── morphiq-wavelets/       # All wavelet indicators & strategies
│   ├── AutoWavelets       # Multi-level decomposition
│   └── swt/               # SWT/MODWT trend following system
└── docs/                   # All documentation
    ├── SWT_TREND_MOMENTUM_DOCUMENTATION.md
    ├── POSITION_MANAGER_GUIDE.md  # Position management framework
    ├── architecture/       # System design docs
    ├── blog/              # Educational articles
    ├── guides/            # Integration and migration guides
    ├── reference/         # Technical specifications
    ├── white_papers/      # Research papers
    └── archive/           # Historical migration docs
```

## Development

### Building from Source

```bash
# Full build with tests
mvn clean install

# Quick build (skip tests)
mvn clean package -DskipTests

# Build specific module
cd morphiq-wavelets
mvn clean package
```

### Creating Custom Indicators

See the [API Reference](API_REFERENCE.md) for detailed class documentation and the [SWT implementation](morphiq-wavelets/src/main/java/com/morphiqlabs/wavelets/swt/) for complete examples.

## Performance Features

- **Sliding Window Buffers** - O(1) updates for new bars
- **Parallel Processing** - Automatic for data ≥ 512 points  
- **RMS Energy Calculation** - Efficient multi-scale momentum
- **Logging Guards** - Zero overhead when debug disabled
- **Native SIMD** - VectorWave uses CPU vector instructions
- **Optimized Reconstruction** - Direct coefficient manipulation without deep copying
- **Thread-Safe Operations** - Defensive copying in WATR calculations

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## Key Settings & Configuration

### Important Parameters
- **Min Slope Threshold**: Absolute price points (e.g., 0.05 = 0.05 point minimum move)
- **Momentum Threshold**: Now scaled by 100x (use 1.0 instead of old 0.01)
- **Trade Lots**: Multiplies the final position size
- **Point Value**: Auto-detected from instrument (ES=$50, NQ=$20, etc.)

## License

Proprietary - See [LICENSE](LICENSE) file.

## Support

- Issues: [GitHub Issues](https://github.com/Prophetizo/morphiq-suite-motivewave/issues)
- Documentation: See links above

---

*Morphiq Suite - Professional Wavelet Analysis for MotiveWave*