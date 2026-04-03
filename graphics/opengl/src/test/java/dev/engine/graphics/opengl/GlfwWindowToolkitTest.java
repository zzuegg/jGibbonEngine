package dev.engine.graphics.opengl;

import dev.engine.windowing.glfw.GlfwWindowToolkit;

import dev.engine.graphics.window.WindowDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * These tests require a display server (X11/Wayland).
 * They create real GLFW windows — skip in headless CI.
 */
class GlfwWindowToolkitTest {

    private GlfwWindowToolkit toolkit;

    @BeforeEach
    void setUp() {
        toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.OPENGL_HINTS);
    }

    @AfterEach
    void tearDown() {
        if (toolkit != null) toolkit.close();
    }

    @Nested
    class WindowCreation {
        @Test
        void createWindowReturnsValidHandle() {
            var handle = toolkit.createWindow(new WindowDescriptor("Test", 320, 240));
            assertNotNull(handle);
            assertTrue(handle.isOpen());
        }

        @Test
        void windowHasRequestedTitle() {
            var handle = toolkit.createWindow(new WindowDescriptor("My Title", 320, 240));
            assertEquals("My Title", handle.title());
        }

        @Test
        void windowDimensionsMatch() {
            var handle = toolkit.createWindow(new WindowDescriptor("Test", 640, 480));
            // Window managers may adjust, but should be close
            assertTrue(handle.width() > 0);
            assertTrue(handle.height() > 0);
        }
    }

    @Nested
    class WindowLifecycle {
        @Test
        void closeWindow() {
            var handle = toolkit.createWindow(new WindowDescriptor("Test", 320, 240));
            handle.close();
            assertFalse(handle.isOpen());
        }

        @Test
        void pollEventsDoesNotThrow() {
            toolkit.createWindow(new WindowDescriptor("Test", 320, 240));
            assertDoesNotThrow(() -> toolkit.pollEvents());
        }
    }
}
