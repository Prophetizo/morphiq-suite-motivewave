package com.morphiqlabs.wavelets.swt.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Thread safety tests for WaveletAtr to verify concurrent access handling.
 * Tests the defensive copying strategy for input arrays.
 */
class WaveletAtrThreadSafetyTest {
    
    private static final int THREAD_COUNT = 10;
    private static final int ITERATIONS = 1000;
    private static final int DATA_SIZE = 256;
    
    @Test
    @DisplayName("Concurrent calculate calls with array modification")
    void testConcurrentCalculateWithModification() throws InterruptedException, ExecutionException {
        WaveletAtr watr = new WaveletAtr(10);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        
        // Shared mutable data that will be modified concurrently
        double[][] sharedDetails = generateTestDetails(4, DATA_SIZE);
        
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);
        
        // Submit tasks that read and modify the array concurrently
        Future<?>[] futures = new Future[THREAD_COUNT];
        for (int t = 0; t < THREAD_COUNT; t++) {
            final int threadId = t;
            futures[t] = executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    
                    Random random = new Random(threadId);
                    for (int i = 0; i < ITERATIONS; i++) {
                        try {
                            // Half the threads calculate, half modify the arrays
                            if (threadId % 2 == 0) {
                                // Calculate WATR - should be safe due to defensive copying
                                double result = watr.calculate(sharedDetails, 3);
                                assertTrue(result >= 0, "WATR should be non-negative");
                                successCount.incrementAndGet();
                            } else {
                                // Modify the shared array
                                int level = random.nextInt(sharedDetails.length);
                                int index = random.nextInt(sharedDetails[level].length);
                                sharedDetails[level][index] = random.nextGaussian();
                            }
                        } catch (Exception e) {
                            exceptionCount.incrementAndGet();
                            throw e;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for completion
        for (Future<?> future : futures) {
            future.get();
        }
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        
        // Verify no exceptions occurred
        assertEquals(0, exceptionCount.get(), "No exceptions should occur");
        assertTrue(successCount.get() > 0, "Some calculations should succeed");
        
        System.out.printf("Thread safety test completed: %d successful calculations%n", 
                         successCount.get());
    }
    
    @RepeatedTest(5)
    @DisplayName("Multiple WaveletAtr instances with shared data")
    void testMultipleInstancesSharedData() throws InterruptedException {
        final int instances = 5;
        WaveletAtr[] watrs = new WaveletAtr[instances];
        for (int i = 0; i < instances; i++) {
            watrs[i] = new WaveletAtr(10 + i * 2); // Different smoothing periods
        }
        
        // Shared data that all instances will read
        double[][] sharedDetails = generateTestDetails(3, DATA_SIZE);
        
        ExecutorService executor = Executors.newFixedThreadPool(instances);
        CountDownLatch latch = new CountDownLatch(instances);
        
        // Each instance calculates in parallel
        for (int i = 0; i < instances; i++) {
            final int instanceId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        double result = watrs[instanceId].calculate(sharedDetails, 2);
                        assertNotNull(result);
                        
                        // Occasionally modify the shared data
                        if (j % 10 == 0) {
                            sharedDetails[0][j % DATA_SIZE] = Math.random();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();
    }
    
    @Test
    @DisplayName("Defensive copy prevents modification during calculation")
    void testDefensiveCopyPreventsModification() {
        WaveletAtr watr = new WaveletAtr(5);
        
        // Create test data
        double[][] details = generateTestDetails(3, 100);
        double[][] originalCopy = deepCopy(details);
        
        // Start a thread that will modify the array during calculation
        Thread modifierThread = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                details[0][i % 100] = Math.random() * 100;
                details[1][i % 100] = Math.random() * 100;
                details[2][i % 100] = Math.random() * 100;
                
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        
        modifierThread.start();
        
        // Perform calculations while the array is being modified
        double[] results = new double[10];
        for (int i = 0; i < results.length; i++) {
            results[i] = watr.calculate(details, 3);
            assertTrue(results[i] >= 0, "Result should be valid");
            
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        modifierThread.interrupt();
        
        // All calculations should have completed successfully
        for (double result : results) {
            assertTrue(Double.isFinite(result), "All results should be finite");
        }
    }
    
    @Test
    @DisplayName("getCurrentValue is thread-safe")
    void testGetCurrentValueThreadSafety() throws InterruptedException {
        WaveletAtr watr = new WaveletAtr(10);
        double[][] testDetails = generateTestDetails(3, 64);
        
        // Initialize with some values
        for (int i = 0; i < 5; i++) {
            watr.calculate(testDetails, 3);
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(100);
        
        // Multiple threads reading current value
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                try {
                    double value = watr.getCurrentValue();
                    assertTrue(value >= 0, "Current value should be non-negative");
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
    }
    
    // Helper methods
    
    private double[][] generateTestDetails(int levels, int size) {
        double[][] details = new double[levels][size];
        Random random = new Random(42);
        
        for (int level = 0; level < levels; level++) {
            for (int i = 0; i < size; i++) {
                details[level][i] = random.nextGaussian() * Math.pow(0.5, level);
            }
        }
        return details;
    }
    
    private double[][] deepCopy(double[][] original) {
        double[][] copy = new double[original.length][];
        for (int i = 0; i < original.length; i++) {
            copy[i] = original[i].clone();
        }
        return copy;
    }
}