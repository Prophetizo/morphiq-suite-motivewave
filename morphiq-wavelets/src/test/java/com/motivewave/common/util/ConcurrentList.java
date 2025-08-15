package com.motivewave.common.util;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mock implementation of ConcurrentList for testing purposes.
 * This class satisfies the MotiveWave SDK dependency during tests.
 */
public class ConcurrentList<E> extends CopyOnWriteArrayList<E> {
    
    public ConcurrentList() {
        super();
    }
    
    public ConcurrentList(int initialCapacity) {
        super();
    }
}