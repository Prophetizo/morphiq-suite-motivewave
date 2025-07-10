# Changelog

All notable changes to the Morphiq Suite MotiveWave project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Comprehensive wavelet capabilities documentation (JWAVE_CAPABILITIES.md)
- Archive directory for historical documentation

### Changed
- Reorganized documentation structure
- Updated KNOWN_ISSUES.md to reflect current state
- Merged JWave analysis documents into unified capability guide

### Removed
- Historical Claude prompt files (moved to archive)
- Redundant JWave analysis documents

## [1.0.0] - 2025-07-02

### Added
- Initial release of Morphiq Suite MotiveWave
- Core wavelet processing library (morphiq-core)
- AutoWavelets indicator (morphiq-autowave) 
- DenoisedTrendFollowing indicator (morphiq-denoise)
- Premium bundle packaging (morphiq-bundle-premium)
- Parallel processing for datasets ≥ 512 points
- MODWT (Maximal Overlap Discrete Wavelet Transform) support
- Support for Daubechies4 and Daubechies6 wavelets

### Fixed
- MODWT energy distribution for non-power-of-2 lengths (via JWave 1.0.7-SNAPSHOT)
- Added inverse MODWT support (via JWave 1.0.7-SNAPSHOT)

### Technical Details
- Java 21 requirement
- Maven multi-module project structure
- MotiveWave SDK v20230627 integration
- JWave 1.2.0-SNAPSHOT for wavelet transforms
- GitHub Actions CI/CD pipeline

## Version History

### JWave Integration
- **1.0.7-SNAPSHOT**: Fixed MODWT energy preservation and added inverse transform
- **1.2.0-SNAPSHOT**: Current version with 68 discrete wavelets
- **develop (250105)**: Adds CWT and 6 continuous wavelets (not yet integrated)

### Known Limitations
- Currently using only 2 of 74+ available wavelets (2.7% utilization)
- No CWT (Continuous Wavelet Transform) support yet
- Single wavelet type per study instance
- No adaptive wavelet selection

## Future Roadmap

### Phase 1 (Immediate)
- Add Haar wavelet for breakout detection
- Implement Symlet family for balanced analysis
- Create wavelet selection framework

### Phase 2 (1-2 months)
- Integrate JWave develop branch for CWT support
- Add Morlet and Mexican Hat wavelets
- Implement cross-wavelet coherence

### Phase 3 (3-6 months)
- Custom ES/NQ microstructure wavelets
- Machine learning wavelet optimization
- Real-time adaptive wavelet selection