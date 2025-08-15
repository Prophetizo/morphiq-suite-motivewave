package com.motivewave.common.util;

import java.beans.PropertyChangeSupport;

/**
 * Mock implementation of WeakPropertyChangeSupport for testing purposes.
 */
public class WeakPropertyChangeSupport extends PropertyChangeSupport {
    
    public WeakPropertyChangeSupport(Object sourceBean) {
        super(sourceBean);
    }
}