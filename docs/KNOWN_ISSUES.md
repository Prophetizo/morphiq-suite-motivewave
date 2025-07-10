# Known Issues

## Current Issues

### 1. Limited Wavelet Utilization
- **Status**: Partially Resolved
- **Description**: Morphiq Suite now uses 7 discrete + 5 continuous wavelets (16.2% total utilization)
- **Progress**: 
  - Added discrete: Haar, Daubechies2, Symlet4, Symlet8, and Coiflet3
  - Added continuous: Morlet, Mexican Hat, DOG, Meyer, and Paul wavelets
- **Remaining**: Still 62+ wavelets unused, including specialized families like Biorthogonal and Legendre
- **Resolution**: Continue adding wavelets based on user needs and trading scenarios

### 2. CWT Implementation
- **Status**: Partially Resolved
- **Description**: CWT support added using JWave 2.0.0
- **Progress**: 
  - ContinuousWaveletAnalyzer implemented
  - 5 continuous wavelets available
  - Time-frequency analysis now possible
- **Remaining**: Need to create CWT-based trading studies
- **Resolution**: Implement scalogram visualization and cycle detection studies

### 3. Single Wavelet per Study
- **Status**: Active  
- **Description**: Current architecture supports only one wavelet type per study instance
- **Impact**: Cannot dynamically switch wavelets based on market conditions
- **Resolution**: Implement adaptive wavelet selection framework

## Resolved Issues

### MODWT Energy Distribution (Resolved in JWave 1.0.7-SNAPSHOT)
- **Previous Issue**: Energy distribution varied between power-of-2 and non-power-of-2 lengths
- **Resolution**: JWave 1.0.7-SNAPSHOT fixes energy preservation completely
- **Current State**: 
  - ✅ Perfect reconstruction (MSE < 1e-24)
  - ✅ Energy preservation (ratio = 1.000000)
  - ✅ Minor variations at high frequencies acceptable (< 2% total energy)
- **Date Resolved**: 2025-07-02

### Inverse MODWT Missing (Resolved in JWave 1.0.7-SNAPSHOT)
- **Previous Issue**: No native inverse MODWT implementation
- **Resolution**: JWave 1.0.7-SNAPSHOT includes native inverse MODWT
- **Date Resolved**: 2025-07-02

## Performance Considerations

### 1. Parallel Processing Threshold
- **Current**: Automatically enabled for data ≥ 512 points
- **Consideration**: May need tuning for different hardware configurations
- **Recommendation**: Make threshold configurable via study parameters

### 2. Memory Usage with CWT
- **Issue**: Full CWT scalogram requires O(n²) memory
- **Mitigation**: Implement sliding window or scale-limited computation
- **Status**: Planning required before CWT implementation