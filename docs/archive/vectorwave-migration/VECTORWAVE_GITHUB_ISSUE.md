# GitHub Issue: Add Morphiq Suite Integration Support

## Title
**Add API methods for seamless Morphiq Suite MotiveWave integration**

## Labels
- `enhancement`
- `api`
- `integration` 
- `trading`
- `modwt`

## Priority
**High** - Required for Morphiq Suite integration

---

## Summary

The Morphiq Suite MotiveWave trading indicators require specific MODWT operations that are not directly available in the current VectorWave API. Adding these methods will enable seamless integration and eliminate the need for wrapper classes.

## Background

Morphiq Suite is migrating from JWave-Pro to VectorWave for wavelet-based trading indicators. The suite includes:
- **AutoWavelets**: Multi-level wavelet decomposition for trend analysis
- **DenoisedTrendFollowing**: Wavelet denoising for smooth trend extraction

Current VectorWave provides excellent MODWT and denoising capabilities, but three specific API additions would make integration optimal.

## Required Enhancements

### 1. Approximation-Only Signal Reconstruction 🎯 **HIGH PRIORITY**

**API Addition:**
```java
// In MultiLevelMODWTTransform class
public double[] extractApproximation(double[] signal, int levels)
```

**Purpose:** Extract only the smooth trend by reconstructing from approximation coefficients while zeroing all detail levels.

**Current Workaround:** Manual coefficient manipulation and reconstruction
**Use Case:** `DenoisedTrendFollowing.getApproximation()` method

**Implementation Notes:**
- Perform MODWT decomposition to specified levels
- Zero out all detail coefficient arrays (D1, D2, ..., Dn)
- Reconstruct signal from approximation coefficients only
- Return the smoothed trend signal

---

### 2. Selective Detail Level Zeroing 🎯 **HIGH PRIORITY**

**API Addition:**
```java
// In WaveletDenoiser class
public double[] denoiseByZeroing(double[] signal, int levels, int[] levelsToZero)
```

**Purpose:** Complete removal of specified detail levels (not thresholding) for aggressive noise filtering.

**Current Limitation:** Only thresholding-based denoising available
**Use Case:** `DenoisedTrendFollowing.denoiseByZeroing()` method

**Parameters:**
- `signal`: Input price/signal data
- `levels`: Total decomposition levels
- `levelsToZero`: Array of detail levels to completely zero (e.g., [0, 1] for D1, D2)

**Implementation Notes:**
- Perform MODWT decomposition
- Set specified detail coefficient arrays to zero
- Reconstruct signal with zeroed levels
- More aggressive than thresholding for high-frequency noise removal

---

### 3. Coefficient Matrix Format Support 🎯 **MEDIUM PRIORITY**

**API Additions:**
```java
// In MultiLevelMODWTResult interface
public double[][] getDetailCoefficientsMatrix()

// In MultiLevelMODWTTransform class  
public double[] reconstructFromMatrix(double[][] detailCoeffs, double[] approxCoeffs)
```

**Purpose:** Compatibility with existing Morphiq code that expects `double[level][time]` coefficient format.

**Current Format:** `MultiLevelMODWTResult` object-oriented approach
**Legacy Format:** `double[][]` where `[level][time]` represents coefficients

**Use Case:** `WaveletAnalyzer.performForwardMODWT()` return format

**Implementation Notes:**
- `getDetailCoefficientsMatrix()`: Convert result to matrix format
- `reconstructFromMatrix()`: Accept legacy format for reconstruction
- Maintain backward compatibility with JWave-based code

---

## Optional Enhancements (Nice to Have)

### 4. MotiveWave-Specific Factory Methods
```java
public static WaveletDenoiser forMotiveWave(String waveletName)
public static MultiLevelMODWTTransform forMotiveWave(String waveletName)
```

### 5. Configurable Parallel Processing Threshold
```java
public void setParallelThreshold(int threshold) // Currently hardcoded at 512
```

---

## Integration Impact

### With These Enhancements ✅
- **Integration time**: 2-3 days
- **Code complexity**: Low
- **Performance**: Optimal (no wrapper overhead)
- **Maintainability**: High
- **API consistency**: Native VectorWave methods

### Without Enhancements (Current) ⚠️
- **Integration time**: 4-5 days
- **Code complexity**: Medium (wrapper classes needed)
- **Performance**: Good (slight wrapper overhead)
- **Maintainability**: Medium (wrapper maintenance required)

---

## Test Cases

### Test 1: Approximation Extraction
```java
@Test
public void testApproximationExtraction() {
    double[] signal = generateTestPriceData(1000);
    MultiLevelMODWTTransform transform = MODWTTransformFactory.createMultiLevel("db4");
    
    double[] approximation = transform.extractApproximation(signal, 5);
    
    // Should be smoother than original
    assertTrue(calculateVariance(approximation) < calculateVariance(signal));
    assertEquals(signal.length, approximation.length);
}
```

### Test 2: Selective Level Zeroing
```java
@Test
public void testSelectiveLevelZeroing() {
    double[] noisySignal = addHighFrequencyNoise(generateCleanSignal(1000));
    WaveletDenoiser denoiser = new WaveletDenoiser(WaveletRegistry.getWavelet("db4"), BoundaryMode.REFLECT);
    
    double[] denoised = denoiser.denoiseByZeroing(noisySignal, 5, new int[]{0, 1}); // Zero D1, D2
    
    // Should have removed high-frequency components
    assertTrue(calculateHighFrequencyEnergy(denoised) < calculateHighFrequencyEnergy(noisySignal));
}
```

### Test 3: Matrix Format Compatibility
```java
@Test
public void testMatrixFormatCompatibility() {
    double[] signal = generateTestData(1000);
    MultiLevelMODWTTransform transform = MODWTTransformFactory.createMultiLevel("db4");
    
    MultiLevelMODWTResult result = transform.decompose(signal, 5);
    double[][] matrix = result.getDetailCoefficientsMatrix();
    double[] reconstructed = transform.reconstructFromMatrix(matrix, result.getApproximationCoeffs());
    
    assertArrayEquals(signal, reconstructed, 1e-10); // Perfect reconstruction
}
```

---

## Files to Modify

1. `ai/prophetizo/wavelet/modwt/MultiLevelMODWTTransform.java`
2. `ai/prophetizo/wavelet/modwt/MultiLevelMODWTResult.java` 
3. `ai/prophetizo/wavelet/denoising/WaveletDenoiser.java`

## Estimated Effort

- **Development**: 4-6 hours
- **Testing**: 2-3 hours  
- **Documentation**: 1-2 hours
- **Total**: 1-2 days

---

## Success Criteria

- [ ] All three API methods implemented and tested
- [ ] Morphiq Suite integration completed successfully  
- [ ] Performance benchmarks meet or exceed JWave equivalents
- [ ] No breaking changes to existing VectorWave API
- [ ] Documentation updated with new methods

---

## Related Issues

- Integration with Morphiq Suite MotiveWave indicators
- JWave-Pro migration and deprecation
- Trading-specific wavelet analysis improvements

---

**Reporter:** Morphiq Suite Integration Team  
**Assignee:** VectorWave Maintainer  
**Milestone:** v1.1 - Trading Integrations