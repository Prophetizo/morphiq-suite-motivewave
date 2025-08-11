# Morphiq Suite for MotiveWave

Advanced wavelet-based trading indicators powered by VectorWave's high-performance wavelet transforms.

## Overview

Morphiq Suite provides professional-grade wavelet analysis tools for the MotiveWave trading platform, delivering:
- **Real-time wavelet decomposition** for trend/noise separation
- **Market-aware denoising** optimized for different asset classes
- **Multi-timeframe analysis** in a single indicator
- **Sub-microsecond performance** with SIMD optimizations

## Features

### 🚀 Performance
- **4x faster** than traditional wavelet libraries
- **Zero-copy operations** with MotiveWave DataSeries
- **Incremental updates** for real-time tick processing
- **Parallel processing** for historical data

### 📊 Trading Indicators
- **AutoWavelets**: Multi-level wavelet decomposition display
- **DenoisedTrendFollowing**: Adaptive noise reduction for cleaner signals
- **WaveletVolatility**: High-frequency component analysis
- **MultiTimeframeWavelets**: Simultaneous multi-TF decomposition

### 🔧 Technical Features
- Multiple wavelet families (Haar, Daubechies, Symlets, Coiflets)
- Automatic wavelet selection based on market conditions
- Volume-weighted transforms
- Market microstructure preservation

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

See the [docs/](docs/) directory for comprehensive documentation:
- [Integration Guide](docs/guides/INTEGRATION_GUIDE.md) - Complete development guide
- [Migration Guide](docs/guides/MIGRATION_GUIDE.md) - Migrating from JWave to VectorWave
- [Architecture](docs/architecture/) - System design and specifications
- [Trading Strategies](docs/guides/STRATEGIES.md) - Using wavelets in trading

## Project Structure

```
morphiq-suite-motivewave/
├── morphiq-core/          # Core wavelet processing library
├── morphiq-autowave/      # AutoWavelets indicator
├── morphiq-denoise/       # Denoised trend following indicator
├── morphiq-bundle-premium/ # Bundle of all indicators
├── vectorwave-review/     # VectorWave library documentation
└── docs/                  # Project documentation
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

```java
import com.prophetizo.vectorwave.motivewave.studies.VectorWaveStudy;

@StudyHeader(
    namespace = "com.mycompany",
    id = "MY_WAVELET",
    name = "My Wavelet Indicator"
)
public class MyWaveletIndicator extends VectorWaveStudy {
    @Override
    protected void storeResults(DataSeries series, int index, 
                              TransformResult result) {
        // Your custom logic here
    }
}
```

## Performance Benchmarks

| Operation | JWave | VectorWave | Improvement |
|-----------|-------|------------|-------------|
| Haar (64 samples) | ~500 ns | ~107 ns | 4.7x |
| DB4 (64 samples) | ~1200 ns | ~294 ns | 4.1x |
| Denoising (1K samples) | ~50 µs | ~12 µs | 4.2x |
| Real-time update | ~5 µs | ~100 ns | 50x |

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

Proprietary - See [LICENSE](LICENSE) file.

## Support

- Documentation: [docs/](docs/)
- Issues: [GitHub Issues](https://github.com/Prophetizo/morphiq-suite-motivewave/issues)
- VectorWave: [vectorwave-review/](vectorwave-review/)

---

*Morphiq Suite - Professional Wavelet Analysis for MotiveWave*