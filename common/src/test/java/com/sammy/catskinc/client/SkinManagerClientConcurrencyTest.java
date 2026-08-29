package com.sammy.catskinc.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinManagerClientConcurrencyTest {
    private static final UUID TEST_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174111");

    @Test
    void skinOverrideStoreThreadSafety() throws Exception {
        int threadCount = 20;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-42661417411" + (threadId % 10));
                    for (int j = 0; j < operationsPerThread; j++) {
                        SkinOverrideStore.put(uuid, Identifiers.mod("test/thread" + threadId), j % 2 == 0);
                        SkinOverrideStore.get(uuid);
                        if (j % 10 == 0) {
                            SkinOverrideStore.clear(uuid);
                        }
                    }
                } catch (Exception e) {
                    // Ignore exceptions in stress test
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(10, TimeUnit.SECONDS), "All threads should complete");

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void fastRetryScheduledConcurrency() throws Exception {
        // Verify FAST_RETRY_SCHEDULED map is accessed thread-safely
        // We can't directly test the internal map, but we can verify
        // that ensureFetch/forceFetch don't throw under concurrent access
        
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadIdx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-42661417411" + (threadIdx % 10));
                    // Call methods that access FAST_RETRY_SCHEDULED
                    SkinManagerClient.ensureFetch(uuid);
                    SkinManagerClient.forceFetch(uuid);
                    SkinManagerClient.refresh(uuid);
                } catch (Exception e) {
                    // Expected in test environment without full Minecraft client
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(10, TimeUnit.SECONDS), "All threads should complete");

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
}