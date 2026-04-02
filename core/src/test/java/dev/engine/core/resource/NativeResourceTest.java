package dev.engine.core.resource;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class NativeResourceTest {

    static class TestResource extends NativeResource {
        TestResource(AtomicBoolean freed) {
            super(() -> freed.set(true));
        }
    }

    @Nested
    class DeterministicCleanup {
        @Test void closeFreesResource() {
            var freed = new AtomicBoolean(false);
            var resource = new TestResource(freed);
            resource.close();
            assertTrue(freed.get());
        }

        @Test void doubleCloseIsHarmless() {
            var freed = new AtomicBoolean(false);
            var resource = new TestResource(freed);
            resource.close();
            assertDoesNotThrow(resource::close);
        }

        @Test void isClosedReportsCorrectly() {
            var resource = new TestResource(new AtomicBoolean());
            assertFalse(resource.isClosed());
            resource.close();
            assertTrue(resource.isClosed());
        }
    }

    @Nested
    class CleanerSafetyNet {
        @Test void cleanerFreesWhenNotExplicitlyClosed() throws InterruptedException {
            var freed = new AtomicBoolean(false);
            allocateAndAbandon(freed);
            for (int i = 0; i < 10 && !freed.get(); i++) {
                System.gc();
                Thread.sleep(50);
            }
            assertTrue(freed.get(), "Cleaner should have freed the resource");
        }

        private void allocateAndAbandon(AtomicBoolean freed) {
            new TestResource(freed);
        }
    }
}
