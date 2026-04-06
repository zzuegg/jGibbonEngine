package dev.engine.core.handle;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HandleCleanerTest {

    @Test void markClosedPreventsCleanupAction() {
        var cleaned = new AtomicBoolean(false);
        var handle = new Handle<Void>(0, 0);
        handle.registerCleanup(() -> cleaned.set(true));

        assertTrue(handle.markClosed());
        assertFalse(cleaned.get(), "cleanup action should not run — closed flag was set before clean()");
    }

    @Test void doubleMarkClosedReturnsFalse() {
        var handle = new Handle<Void>(0, 0);
        handle.registerCleanup(() -> {});
        assertTrue(handle.markClosed());
        assertFalse(handle.markClosed());
    }

    @Test void isClosedReflectsState() {
        var handle = new Handle<Void>(0, 0);
        handle.registerCleanup(() -> {});
        assertFalse(handle.isClosed());
        handle.markClosed();
        assertTrue(handle.isClosed());
    }

    @Test void handleWithoutCleanupCanStillBeClosed() {
        var handle = new Handle<Void>(0, 0);
        assertTrue(handle.markClosed());
        assertTrue(handle.isClosed());
    }

    @Test void cleanerRunsActionWhenHandleBecomesUnreachable() throws InterruptedException {
        var cleanupCount = new AtomicInteger(0);
        createAndAbandonHandle(cleanupCount);

        for (int i = 0; i < 10; i++) {
            System.gc();
            Thread.sleep(50);
            if (cleanupCount.get() > 0) break;
        }
        assertEquals(1, cleanupCount.get(), "Cleaner should have run cleanup for abandoned handle");
    }

    @Test void cleanerDoesNotRunIfExplicitlyClosed() throws InterruptedException {
        var cleanupCount = new AtomicInteger(0);
        createAndCloseHandle(cleanupCount);

        System.gc();
        Thread.sleep(200);
        assertEquals(0, cleanupCount.get(), "Cleaner should not run — handle was explicitly closed");
    }

    @Test void equalityBasedOnIndexAndGeneration() {
        var a = new Handle<Void>(5, 3);
        var b = new Handle<Void>(5, 3);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        var c = new Handle<Void>(5, 4);
        assertNotEquals(a, c);
    }

    private void createAndAbandonHandle(AtomicInteger counter) {
        var handle = new Handle<Void>(99, 0);
        handle.registerCleanup(counter::incrementAndGet);
    }

    private void createAndCloseHandle(AtomicInteger counter) {
        var handle = new Handle<Void>(99, 0);
        handle.registerCleanup(counter::incrementAndGet);
        handle.markClosed();
    }
}
