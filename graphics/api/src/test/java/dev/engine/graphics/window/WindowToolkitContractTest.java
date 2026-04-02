package dev.engine.graphics.window;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WindowToolkitContractTest {

    @Nested
    class WindowCreation {
        @Test void createWindowReturnsHandle() {
            var toolkit = new StubWindowToolkit();
            var desc = new WindowDescriptor("Test", 800, 600);
            var handle = toolkit.createWindow(desc);
            assertNotNull(handle);
            assertTrue(handle.isOpen());
        }

        @Test void windowHasRequestedDimensions() {
            var toolkit = new StubWindowToolkit();
            var desc = new WindowDescriptor("Test", 1024, 768);
            var handle = toolkit.createWindow(desc);
            assertEquals(1024, handle.width());
            assertEquals(768, handle.height());
        }
    }

    @Nested
    class WindowLifecycle {
        @Test void closeWindowMakesItNotOpen() {
            var toolkit = new StubWindowToolkit();
            var handle = toolkit.createWindow(new WindowDescriptor("Test", 800, 600));
            handle.close();
            assertFalse(handle.isOpen());
        }
    }

    @Nested
    class EventPolling {
        @Test void pollEventsDoesNotThrow() {
            var toolkit = new StubWindowToolkit();
            toolkit.createWindow(new WindowDescriptor("Test", 800, 600));
            assertDoesNotThrow(toolkit::pollEvents);
        }
    }
}
