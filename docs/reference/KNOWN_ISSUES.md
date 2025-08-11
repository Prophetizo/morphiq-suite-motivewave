# Known Issues

## Migration to VectorWave

This project is migrating from JWave to VectorWave. Most issues below are resolved or irrelevant with VectorWave.

## Current Issues

### 1. Migration in Progress
- **Status**: Active
- **Description**: Migrating from JWave-Pro to VectorWave
- **Impact**: Some studies may need updates during transition
- **Resolution**: Follow [Migration Guide](guides/MIGRATION_GUIDE.md)

### 2. Custom Wavelets Implementation
- **Status**: Pending
- **Description**: Need to add DB6 and SYM8 to VectorWave for full compatibility
- **Impact**: These wavelets currently fall back to DB4 and SYM4
- **Resolution**: Implement in VectorWave core

## Resolved with VectorWave

### ✅ Wavelet Availability
- VectorWave provides all commonly needed wavelets
- Additional wavelets can be easily added as needed

### ✅ Performance Issues
- VectorWave is 4x faster than JWave
- SIMD optimizations for modern CPUs
- Zero-copy operations with MotiveWave

### ✅ Real-time Updates
- VectorWave supports incremental transforms
- O(1) updates for streaming tick data

### ✅ Memory Management
- Built-in buffer pooling
- Reduced GC pressure

## Performance Considerations

### 1. Parallel Processing Threshold
- **Current**: Automatically enabled for data ≥ 512 points
- **Consideration**: May need tuning for different hardware configurations
- **Recommendation**: Make threshold configurable via study parameters

### 2. Memory Usage with CWT
- **Issue**: Full CWT scalogram requires O(n²) memory
- **Mitigation**: Implement sliding window or scale-limited computation
- **Status**: Planning required before CWT implementation