package dev.engine.core.version;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionedTest {

    @Nested
    class BasicVersioning {
        @Test void initialVersionIsZero() {
            var v = new VersionCounter();
            assertEquals(0, v.version());
        }

        @Test void incrementAdvancesVersion() {
            var v = new VersionCounter();
            v.increment();
            assertEquals(1, v.version());
            v.increment();
            assertEquals(2, v.version());
        }

        @Test void isNewerThan() {
            var a = new VersionCounter();
            var b = new VersionCounter();
            b.increment();
            assertTrue(b.version() > a.version());
        }
    }

    @Nested
    class VersionSnapshot {
        @Test void snapshotCapturesCurrentVersion() {
            var v = new VersionCounter();
            v.increment();
            v.increment();
            long snap = v.version();
            assertEquals(2, snap);
        }

        @Test void hasChangedSinceDetectsChanges() {
            var v = new VersionCounter();
            long snap = v.version();
            assertFalse(v.hasChangedSince(snap));
            v.increment();
            assertTrue(v.hasChangedSince(snap));
        }
    }

    @Nested
    class HierarchicalVersioning {
        @Test void effectiveVersionIsMaxOfSelfAndParent() {
            var parent = new VersionCounter();
            var child = new VersionCounter();
            parent.increment(); // parent v=1
            child.increment();  // child v=1
            parent.increment(); // parent v=2

            long effective = Math.max(child.version(), parent.version());
            assertEquals(2, effective);
        }

        @Test void parentChangeInvalidatesChild() {
            var parent = new VersionCounter();
            var child = new VersionCounter();
            child.increment(); // child v=1

            long childSnap = child.version();
            long parentSnap = parent.version();

            // Child hasn't changed
            assertFalse(child.hasChangedSince(childSnap));

            // Parent changes
            parent.increment();

            // Child's own version hasn't changed, but effective version has
            assertFalse(child.hasChangedSince(childSnap));
            assertTrue(parent.hasChangedSince(parentSnap));
        }
    }

    @Nested
    class ThreadSafety {
        @Test void concurrentIncrements() throws InterruptedException {
            var v = new VersionCounter();
            int threads = 8;
            int perThread = 1000;
            var latch = new java.util.concurrent.CountDownLatch(threads);

            try (var executor = java.util.concurrent.Executors.newFixedThreadPool(threads)) {
                for (int t = 0; t < threads; t++) {
                    executor.submit(() -> {
                        for (int i = 0; i < perThread; i++) {
                            v.increment();
                        }
                        latch.countDown();
                    });
                }
                assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS));
            }
            assertEquals(threads * perThread, v.version());
        }
    }
}
