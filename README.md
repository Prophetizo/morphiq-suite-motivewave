# Morphiq Suite for MotiveWave

Professional wavelet-based trading indicators and strategies for the MotiveWave platform, powered by VectorWave's high-performance wavelet transforms.

## Overview

Morphiq Suite provides advanced wavelet analysis tools for systematic trading:
- **SWT/MODWT Implementation** - Undecimated transforms for shift-invariant analysis
- **Cross-Scale Momentum** - Multi-resolution signal confirmation
- **Adaptive Denoising** - Three thresholding methods (Universal, BayesShrink, SURE)
- **Wavelet-ATR** - Volatility estimation from detail coefficients
- **Real-time Performance** - Optimized sliding window buffers and parallel processing

## Indicators & Strategies

### 📈 SWT Trend + Momentum (NEW)
Complete trading system using Stationary Wavelet Transform:
- **Study**: Overlay indicator with trend line and momentum plot
- **Strategy**: Automated trading with risk management
- **Features**: RMS energy-based momentum, balanced signal generation, WATR stops
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
- **Daubechies** (db2-db20): Smooth, good for trends
- **Symlets** (sym2-sym20): Near-symmetric, balanced
- **Coiflets** (coif1-coif5): Compact support
- **Haar**: Simple, good for breakouts

### Advanced Algorithms
- **Thresholding Methods**: Universal, BayesShrink, SURE
- **Shrinkage Types**: Soft and hard thresholding
- **Sliding Window**: Efficient streaming updates
- **Cross-Scale Analysis**: Multi-resolution momentum confirmation

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.6+
- MotiveWave Platform (latest version)

### Installation

```bash
# Clone the repository
git clone https://github.com/Prophetizo/morphiq-suite-motivewave.git
cd morphiq-suite-motivewave

# Build the project
mvn clean package

# Install to MotiveWave
cp morphiq-bundle-premium/target/morphiq-premium-*.jar ~/Documents/MotiveWave/studies/
```

### Usage in MotiveWave

1. Restart MotiveWave after copying the JAR
2. Right-click on a chart → "Add Study"
3. Navigate to "Wavelets" menu
4. Select desired wavelet indicator

## Documentation

- **[SWT Strategy Documentation](docs/SWT_TREND_MOMENTUM_DOCUMENTATION.md)** - Complete guide to the SWT/MODWT trading system
- **[API Reference](API_REFERENCE.md)** - Class and method reference
- **[CLAUDE.md](CLAUDE.md)** - Development guide for AI assistants
- **[Architecture Docs](docs/architecture/)** - System design and specifications

## Project Structure

```
morphiq-suite-motivewave/
├── morphiq-core/           # Core wavelet processing library
├── morphiq-common/         # Shared MotiveWave utilities
├── morphiq-autowave/       # AutoWavelets indicator
├── morphiq-denoise/        # SWT/MODWT trend following system
├── morphiq-bundle-premium/ # Bundle of all indicators
└── docs/                   # All documentation
    ├── SWT_TREND_MOMENTUM_DOCUMENTATION.md
    ├── architecture/       # System design docs
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
cd morphiq-core
mvn clean package
```

### Creating Custom Indicators

See the [API Reference](API_REFERENCE.md) for detailed class documentation and the [SWT implementation](morphiq-denoise/src/main/java/com/prophetizo/wavelets/swt/) for complete examples.

## Performance Features

- **Sliding Window Buffers** - O(1) updates for new bars
- **Parallel Processing** - Automatic for data ≥ 512 points  
- **RMS Energy Calculation** - Efficient multi-scale momentum
- **Logging Guards** - Zero overhead when debug disabled
- **Native SIMD** - VectorWave uses CPU vector instructions

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

Proprietary - See [LICENSE](LICENSE) file.

## Support

- Issues: [GitHub Issues](https://github.com/Prophetizo/morphiq-suite-motivewave/issues)
- Documentation: See links above

---

*Morphiq Suite - Professional Wavelet Analysis for MotiveWave*