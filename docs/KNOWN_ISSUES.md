# Known Issues

## MODWT Energy Distribution Inconsistency for Non-Power-of-2 Lengths (Mostly Resolved)

### Issue Description
When using JWave 1.0.7-SNAPSHOT's MODWT implementation, the energy distribution of wavelet coefficients varies between power-of-2 and non-power-of-2 signal lengths, though the latest version has significantly improved.

### Current Status (JWave 1.0.7-SNAPSHOT Latest)
✅ **Energy Preservation**: Fixed - Total energy is now preserved for all signal lengths
⚠️  **Energy Distribution**: Mostly fixed - Energy distribution across levels shows minor variations (up to 4:1 ratio for small energy levels < 2%)

### Historical Symptoms (Now Resolved)
- Energy ratios between signals of length 288 vs 256 were as high as 37:1
- This was originally reported as "lookbacks of 256, and 512 seem similar, whereas a lookback of 288 seems to result in values that are off"

### Current Test Results
- Perfect reconstruction works correctly (MSE < 1e-24) ✅
- Energy preservation works perfectly (ratio = 1.000000) ✅
- Denoising functionality works well ✅
- Energy distribution shows minor variations at high-frequency levels (< 2% of total energy) ⚠️

### Recommendation
The current implementation is suitable for production use. The minor variations in energy distribution at high-frequency levels (which contain < 2% of total energy) are unlikely to affect practical applications.

### Update History
- 2025-07-02 (Initial): Documented severe 37:1 energy ratio issue
- 2025-07-02 (Update): JWave 1.0.7-SNAPSHOT latest fixes energy preservation
- 2025-07-02 (Current): Minor energy distribution variations remain but are acceptable