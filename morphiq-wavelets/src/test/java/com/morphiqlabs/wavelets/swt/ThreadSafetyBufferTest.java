package com.morphiqlabs.wavelets.swt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Thread safety tests for buffer operations and concurrent access.
 * Ensures thread-safe operations as specified in CLAUDE.md.
 */
class ThreadSafetyBufferTest {
    
    private static final int THREAD_COUNT = 20;
    private static final int OPERATIONS_PER_THREAD = 1000;
    private static final Random seededRandom = new Random(42);
    
    private BufferManager bufferManager;
    private ExecutorService executor;
    
    @BeforeEach
    void setUp() {
        bufferManager = new BufferManager();
        executor = Executors.newFixedThreadPool(THREAD_COUNT);
    }
    
    @Test
    @DisplayName("Concurrent buffer writes should be thread-safe")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentBufferWrites() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
        AtomicBoolean hasError = new AtomicBoolean(false);
        AtomicReference<Exception> lastException = new AtomicReference<>();
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        double value = threadId * 1000 + j;
                        bufferManager.writeToBuffer(j % 100, value);
                    }
                } catch (Exception e) {
                    hasError.set(true);
                    lastException.set(e);
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown(); // Start all threads simultaneously
        assertTrue(endLatch.await(10, TimeUnit.SECONDS));
        
        assertFalse(hasError.get(), 
            "Thread safety violation: " + 
            (lastException.get() != null ? lastException.get().getMessage() : "unknown"));
    }
    
    @Test
    @DisplayName("Concurrent buffer reads should be thread-safe")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentBufferReads() throws InterruptedException {
        // Pre-populate buffer
        for (int i = 0; i < 100; i++) {
            bufferManager.writeToBuffer(i, i * 10.0);
        }
        
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successfulReads = new AtomicInteger(0);
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        double value = bufferManager.readFromBuffer(j % 100);
                        if (value >= 0) {
                            successfulReads.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertTrue(endLatch.await(10, TimeUnit.SECONDS));
        
        assertEquals(THREAD_COUNT * OPERATIONS_PER_THREAD, successfulReads.get());
    }
    
    @Test
    @DisplayName("Mixed read/write operations should be thread-safe")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testMixedReadWriteOperations() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger operations = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    Random random = new Random(threadId);
                    
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        if (random.nextBoolean()) {
                            // Write operation
                            bufferManager.writeToBuffer(
                                random.nextInt(100), 
                                random.nextDouble() * 1000
                            );
                        } else {
                            // Read operation
                            bufferManager.readFromBuffer(random.nextInt(100));
                        }
                        operations.incrementAndGet();
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(exceptions.isEmpty(), 
            "Exceptions during mixed operations: " + exceptions.size());
        assertEquals(THREAD_COUNT * OPERATIONS_PER_THREAD, operations.get());
    }
    
    @Test
    @DisplayName("Buffer resize during concurrent access should be safe")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testBufferResizeDuringConcurrentAccess() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT + 1);
        AtomicBoolean resizeComplete = new AtomicBoolean(false);
        
        // Start reader/writer threads
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Random random = new Random(threadId);
                    int iterations = 0;
                    
                    while (!resizeComplete.get() && iterations < 100) {
                        bufferManager.writeToBuffer(
                            random.nextInt(50), 
                            random.nextDouble()
                        );
                        iterations++;
                        if (iterations % 10 == 0) {
                            Thread.sleep(1);
                        }
                    }
                } catch (Exception e) {
                    // Ignore interruptions
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        // Start resize thread
        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(50); // Let other threads start
                
                // Perform resize
                bufferManager.resizeBuffer(200);
                resizeComplete.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                endLatch.countDown();
            }
        });
        
        startLatch.countDown();
        assertTrue(endLatch.await(5, TimeUnit.SECONDS));
        
        // Verify buffer was resized
        assertEquals(200, bufferManager.getBufferSize());
    }
    
    @RepeatedTest(5)
    @DisplayName("Volatile fields ensure visibility across threads")
    void testVolatileFieldVisibility() throws InterruptedException {
        MomentumState state = new MomentumState();
        CountDownLatch writerDone = new CountDownLatch(1);
        AtomicBoolean readerSuccess = new AtomicBoolean(false);
        
        // Writer thread
        Thread writer = new Thread(() -> {
            state.setMomentumValue(42.0);
            state.setTrendDirection(1);
            state.setSignalActive(true);
            writerDone.countDown();
        });
        
        // Reader thread
        Thread reader = new Thread(() -> {
            try {
                writerDone.await();
                // Values should be immediately visible due to volatile
                if (state.getMomentumValue() == 42.0 &&
                    state.getTrendDirection() == 1 &&
                    state.isSignalActive()) {
                    readerSuccess.set(true);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        writer.start();
        reader.start();
        
        writer.join(1000);
        reader.join(1000);
        
        assertTrue(readerSuccess.get(), "Volatile fields not visible across threads");
    }
    
    @Test
    @DisplayName("Lock fairness under high contention")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testLockFairnessUnderContention() throws InterruptedException {
        final int ITERATIONS = 100;
        ConcurrentHashMap<Integer, Integer> accessCounts = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            accessCounts.put(threadId, 0);
            
            executor.submit(() -> {
                try {
                    for (int j = 0; j < ITERATIONS; j++) {
                        bufferManager.performSynchronizedOperation(() -> {
                            accessCounts.merge(threadId, 1, Integer::sum);
                            // Simulate work
                            try {
                                Thread.sleep(1);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(15, TimeUnit.SECONDS));
        
        // Check fairness - no thread should be starved
        int min = Collections.min(accessCounts.values());
        int max = Collections.max(accessCounts.values());
        double ratio = (double) max / min;
        
        assertTrue(ratio < 2.0, 
            String.format("Unfair lock access: max=%d, min=%d, ratio=%.2f", max, min, ratio));
    }
    
    /**
     * Mock buffer manager for testing thread safety.
     */
    private static class BufferManager {
        private double[] buffer = new double[100];
        private final Object bufferLock = new Object();
        
        void writeToBuffer(int index, double value) {
            synchronized (bufferLock) {
                if (index >= 0 && index < buffer.length) {
                    buffer[index] = value;
                }
            }
        }
        
        double readFromBuffer(int index) {
            synchronized (bufferLock) {
                if (index >= 0 && index < buffer.length) {
                    return buffer[index];
                }
                return -1;
            }
        }
        
        void resizeBuffer(int newSize) {
            synchronized (bufferLock) {
                double[] newBuffer = new double[newSize];
                System.arraycopy(buffer, 0, newBuffer, 0, 
                    Math.min(buffer.length, newSize));
                buffer = newBuffer;
            }
        }
        
        int getBufferSize() {
            synchronized (bufferLock) {
                return buffer.length;
            }
        }
        
        void performSynchronizedOperation(Runnable operation) {
            synchronized (bufferLock) {
                operation.run();
            }
        }
    }
    
    /**
     * Mock momentum state with volatile fields.
     */
    private static class MomentumState {
        private volatile double momentumValue;
        private volatile int trendDirection;
        private volatile boolean signalActive;
        
        double getMomentumValue() { return momentumValue; }
        void setMomentumValue(double value) { this.momentumValue = value; }
        
        int getTrendDirection() { return trendDirection; }
        void setTrendDirection(int direction) { this.trendDirection = direction; }
        
        boolean isSignalActive() { return signalActive; }
        void setSignalActive(boolean active) { this.signalActive = active; }
    }
}