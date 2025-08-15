package com.motivewave.common.util;

import java.util.HashMap;

/**
 * Mock implementation of FastMap for testing purposes.
 * This class satisfies the MotiveWave SDK dependency during tests.
 */
public class FastMap<K, V> extends HashMap<K, V> {
    
    public FastMap() {
        super();
    }
    
    public FastMap(int initialCapacity) {
        super(initialCapacity);
    }
    
    public FastMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }
    
    // FastMap-specific methods can be added here if needed
    // The base HashMap already provides getOrDefault and putIfAbsent
}