# VectorWave Issue: Duplicate Wavelets in Registry

## Issue Summary
The `WaveletRegistry.getOrthogonalWavelets()` method returns duplicate wavelets with different naming conventions, causing confusion and UI issues in consuming applications.

## Problem Description

### Current Behavior
When calling `WaveletRegistry.getOrthogonalWavelets()`, the method returns 27 wavelets, but many are duplicates with different naming schemes:

```java
List<String> wavelets = WaveletRegistry.getOrthogonalWavelets();
// Returns: [haar, db2, db4, db6, db8, db10, daubechies2, daubechies4, daubechies6, daubechies8, daubechies10, ...]
```

**Duplicates found:**
- `db2` and `daubechies2` (same wavelet, different names)
- `db4` and `daubechies4` 
- `db6` and `daubechies6`
- `db8` and `daubechies8`
- `db10` and `daubechies10`

### Expected Behavior
The registry should return only unique wavelets using canonical names, without duplicates.

## Impact

1. **UI Confusion**: Dropdown menus show duplicate options, confusing users
2. **Performance**: Applications process redundant wavelets
3. **Inconsistency**: Violates the single responsibility principle - one wavelet should have one canonical identifier
4. **Best Practices**: Contradicts the WaveletRegistry Best Practices Guide which emphasizes efficient type-specific queries

## Reproduction Steps

```java
import ai.prophetizo.wavelet.api.WaveletRegistry;
import java.util.*;

public class DuplicateWaveletDemo {
    public static void main(String[] args) {
        List<String> orthogonalWavelets = WaveletRegistry.getOrthogonalWavelets();
        
        // Group by family to show duplicates
        Map<String, List<String>> byFamily = new HashMap<>();
        for (String wavelet : orthogonalWavelets) {
            String family = wavelet.replaceAll("\\d+", "");
            byFamily.computeIfAbsent(family, k -> new ArrayList<>()).add(wavelet);
        }
        
        System.out.println("Total wavelets: " + orthogonalWavelets.size());
        System.out.println("\nWavelets by family:");
        byFamily.forEach((family, wavelets) -> {
            System.out.println("  " + family + ": " + wavelets);
        });
        
        // Detect duplicates
        System.out.println("\nDuplicates detected:");
        for (String wavelet : orthogonalWavelets) {
            if (wavelet.startsWith("daubechies")) {
                String number = wavelet.substring(10);
                String shortForm = "db" + number;
                if (orthogonalWavelets.contains(shortForm)) {
                    System.out.println("  " + wavelet + " duplicates " + shortForm);
                }
            }
        }
    }
}
```

### Output:
```
Total wavelets: 27

Wavelets by family:
  daubechies: [daubechies10, daubechies2, daubechies4, daubechies6, daubechies8]
  db: [db10, db2, db4, db6, db8]
  sym: [sym2, sym3, sym4, sym5, sym6, sym7, sym8, sym10, sym12, sym15, sym20]
  coif: [coif1, coif2, coif3, coif4, coif5]
  haar: [haar]

Duplicates detected:
  daubechies10 duplicates db10
  daubechies2 duplicates db2
  daubechies4 duplicates db4
  daubechies6 duplicates db6
  daubechies8 duplicates db8
```

## Proposed Solution

### Option 1: Return Only Canonical Names (Recommended)
Modify `WaveletRegistry` to return only canonical short-form names:
- Use `db4` instead of `daubechies4`
- Maintain internal alias mapping for backward compatibility
- `getWavelet("daubechies4")` should still work but map to `db4`

### Option 2: Add Deduplication Method
Add a new method that returns deduplicated wavelets:
```java
public static List<String> getOrthogonalWaveletsCanonical() {
    // Returns only canonical names without duplicates
}
```

### Option 3: Configuration Flag
Add a configuration option to control duplicate filtering:
```java
public static List<String> getOrthogonalWavelets(boolean includeAliases) {
    // If false, returns only canonical names
}
```

## Recommended Implementation

```java
// Internal alias mapping
private static final Map<String, String> WAVELET_ALIASES = Map.of(
    "daubechies2", "db2",
    "daubechies4", "db4",
    "daubechies6", "db6",
    "daubechies8", "db8",
    "daubechies10", "db10"
    // ... other aliases
);

public static List<String> getOrthogonalWavelets() {
    Set<String> canonical = new HashSet<>();
    
    for (String wavelet : getAllOrthogonalInternal()) {
        // Map to canonical name if alias exists
        String canonicalName = WAVELET_ALIASES.getOrDefault(wavelet.toLowerCase(), wavelet);
        canonical.add(canonicalName);
    }
    
    return new ArrayList<>(canonical);
}

public static Wavelet getWavelet(String name) {
    // Support both forms for backward compatibility
    String canonical = WAVELET_ALIASES.getOrDefault(name.toLowerCase(), name);
    return getWaveletInternal(canonical);
}
```

## Workaround

Until fixed in VectorWave, consuming applications can filter duplicates:

```java
private List<String> filterDuplicateWavelets(List<String> wavelets) {
    Set<String> filtered = new HashSet<>();
    Set<String> skipLongForm = new HashSet<>();
    
    // First pass: identify short forms
    for (String wavelet : wavelets) {
        if (wavelet.matches("db\\d+")) {
            skipLongForm.add("daubechies" + wavelet.substring(2));
        }
    }
    
    // Second pass: add non-duplicates
    for (String wavelet : wavelets) {
        if (!skipLongForm.contains(wavelet)) {
            filtered.add(wavelet);
        }
    }
    
    return new ArrayList<>(filtered);
}
```

## Environment
- VectorWave Version: 1.0-SNAPSHOT
- Java Version: 23
- Affected Methods: 
  - `WaveletRegistry.getOrthogonalWavelets()`
  - Potentially other type-specific query methods

## Priority
Medium - Causes UI issues and violates best practices, but has a workaround

## Related Documentation
- WaveletRegistry Best Practices Guide (Section: "Discovery Patterns")
- The guide emphasizes using type-specific queries for efficiency, but these queries return duplicates

## Additional Notes
This issue may also affect other wavelet families if they have similar naming conventions. A comprehensive audit of all registry methods would be beneficial.