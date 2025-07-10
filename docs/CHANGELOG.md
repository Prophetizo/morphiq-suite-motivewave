# Changelog

All notable changes to the Morphiq Suite MotiveWave project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Comprehensive wavelet capabilities documentation (JWAVE_CAPABILITIES.md)
- Archive directory for historical documentation
- 5 new wavelets with JWave 2.0.0:
  - Haar - Fast breakout detection
  - Daubechies2 - High-frequency trading
  - Symlet4 - Day trading momentum detection
  - Symlet8 - Swing trading trend following
  - Coiflet3 - Position trading smooth trends
- WaveletAnalyzerFactory for centralized wavelet creation
- WaveletOptionsProvider for consistent wavelet selection logic
- StudyUIHelper to eliminate code duplication in UI components
- Comprehensive unit tests for all wavelets
- Named constants for all test values in NewWaveletsTest (addresses code review feedback)
- morphiq-common module for shared MotiveWave utilities (addresses code review feedback)
- createWaveletTypeDescriptor helper method in StudyUIHelper (addresses code review feedback)
- CWT (Continuous Wavelet Transform) support with JWave 2.0.0:
  - ContinuousWaveletType enum with 5 wavelets (Morlet, Mexican Hat, DOG, Meyer, Paul)
  - ContinuousWaveletAnalyzer for time-frequency analysis
  - Factory methods in WaveletAnalyzerFactory for CWT use cases
  - Comprehensive unit tests for CWT functionality

### Changed
- Upgraded JWave from 1.2.0-SNAPSHOT to 2.0.0
- Reorganized documentation structure
- Updated KNOWN_ISSUES.md to reflect current state
- Merged JWave analysis documents into unified capability guide
- Refactored to eliminate WaveletType enum duplication
- All studies now use shared WaveletType from morphiq-core
- Extracted common UI code to StudyUIHelper (addresses code review feedback)
- Replaced magic numbers with named constants in test code (addresses code review feedback)
- Fixed non-deterministic tests by using seeded Random (addresses code review feedback)
- Moved StudyUIHelper to morphiq-common module to eliminate duplication (addresses code review feedback)
- Refactored DiscreteDescriptor creation into helper method (addresses code review feedback)

### Removed
- Historical Claude prompt files (moved to archive)
- Redundant JWave analysis documents
- Duplicated WaveletType enums from individual studies

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
- Currently using only 7 of 74+ available wavelets (9.5% utilization)
- No CWT (Continuous Wavelet Transform) support yet
- Single wavelet type per study instance
- No adaptive wavelet selection

## Future Roadmap

### Phase 1 (Completed)
- ✅ Add Haar wavelet for breakout detection
- ✅ Implement Symlet family for balanced analysis (Symlet4, Symlet8)
- ✅ Create wavelet selection framework (WaveletAnalyzerFactory)
- ✅ Upgrade to JWave 2.0.0
- ✅ Add Coiflet3 for position trading

### Phase 2 (1-2 months)
- Integrate JWave develop branch for CWT support
- Add Morlet and Mexican Hat wavelets
- Implement cross-wavelet coherence

### Phase 3 (3-6 months)
- Custom ES/NQ microstructure wavelets
- Machine learning wavelet optimization
- Real-time adaptive wavelet selection