package dev.engine.core.handle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HandlePoolTest {

    private HandlePool<Void> pool;

    @BeforeEach
    void setUp() { pool = new HandlePool<>(); }

    @Nested
    class Allocation {
        @Test void allocateReturnsValidHandle() {
            var h = pool.allocate();
            assertTrue(pool.isValid(h));
        }
        @Test void eachAllocationIsUnique() {
            var a = pool.allocate();
            var b = pool.allocate();
            assertNotEquals(a, b);
        }
    }

    @Nested
    class Release {
        @Test void releasedHandleIsInvalid() {
            var h = pool.allocate();
            pool.release(h);
            assertFalse(pool.isValid(h));
        }
        @Test void doubleReleaseIsHarmless() {
            var h = pool.allocate();
            pool.release(h);
            assertDoesNotThrow(() -> pool.release(h));
        }
        @Test void reusedSlotHasDifferentGeneration() {
            var h1 = pool.allocate();
            int index = h1.index();
            pool.release(h1);
            var h2 = pool.allocate();
            assertEquals(index, h2.index());
            assertNotEquals(h1.generation(), h2.generation());
            assertFalse(pool.isValid(h1));
            assertTrue(pool.isValid(h2));
        }
    }

    @Nested
    class ThreadSafety {
        @Test void concurrentAllocateAndRelease() throws InterruptedException {
            int threads = 8;
            int opsPerThread = 1000;
            var allHandles = new ConcurrentLinkedQueue<Handle<Void>>();
            var latch = new CountDownLatch(threads);

            try (var executor = Executors.newFixedThreadPool(threads)) {
                for (int t = 0; t < threads; t++) {
                    executor.submit(() -> {
                        for (int i = 0; i < opsPerThread; i++) {
                            var h = pool.allocate();
                            allHandles.add(h);
                            if (i % 3 == 0) pool.release(h);
                        }
                        latch.countDown();
                    });
                }
                assertTrue(latch.await(5, TimeUnit.SECONDS));
            }
            var h = pool.allocate();
            assertTrue(pool.isValid(h));
        }
    }
}
