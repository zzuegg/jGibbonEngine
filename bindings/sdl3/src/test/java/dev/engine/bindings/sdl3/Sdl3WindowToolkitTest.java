package dev.engine.bindings.sdl3;

import dev.engine.graphics.window.WindowDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Sdl3WindowToolkitTest {

    private Sdl3WindowToolkit toolkit;

    @BeforeEach
    void setUp() { toolkit = new Sdl3WindowToolkit(); }

    @AfterEach
    void tearDown() { if (toolkit != null) toolkit.close(); }

    @Nested
    class WindowCreation {
        @Test void createWindowReturnsValidHandle() {
            var handle = toolkit.createWindow(new WindowDescriptor("SDL3 Test", 320, 240));
            assertNotNull(handle);
            assertTrue(handle.isOpen());
        }

        @Test void windowHasRequestedTitle() {
            var handle = toolkit.createWindow(new WindowDescriptor("My Title", 320, 240));
            assertEquals("My Title", handle.title());
        }

        @Test void windowHasPositiveDimensions() {
            var handle = toolkit.createWindow(new WindowDescriptor("Test", 640, 480));
            handle.show();
            // SDL3 may need a poll cycle to update dimensions after show
            toolkit.pollEvents();
            assertTrue(handle.width() > 0, "Width should be positive, got " + handle.width());
            assertTrue(handle.height() > 0, "Height should be positive, got " + handle.height());
        }
    }

    @Nested
    class WindowLifecycle {
        @Test void closeWindow() {
            var handle = toolkit.createWindow(new WindowDescriptor("Test", 320, 240));
            handle.close();
            assertFalse(handle.isOpen());
        }

        @Test void pollEventsDoesNotThrow() {
            toolkit.createWindow(new WindowDescriptor("Test", 320, 240));
            assertDoesNotThrow(() -> toolkit.pollEvents());
        }
    }
}
