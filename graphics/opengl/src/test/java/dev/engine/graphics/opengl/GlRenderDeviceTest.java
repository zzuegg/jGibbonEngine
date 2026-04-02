package dev.engine.graphics.opengl;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.DeviceCapability;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.window.WindowDescriptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GPU test harness: creates a real OpenGL context via hidden GLFW window
 * and tests actual GPU operations.
 */
class GlRenderDeviceTest {

    static GlfwWindowToolkit toolkit;
    static GlRenderDevice device;

    @BeforeAll
    static void setUp() {
        toolkit = new GlfwWindowToolkit();
        var window = toolkit.createWindow(new WindowDescriptor("GPU Test", 1, 1));
        device = new GlRenderDevice((GlfwWindowToolkit.GlfwWindowHandle) window);
    }

    @AfterAll
    static void tearDown() {
        if (device != null) device.close();
        if (toolkit != null) toolkit.close();
    }

    @Nested
    class BufferOperations {
        @Test
        void createBufferReturnsValidHandle() {
            var desc = new BufferDescriptor(1024, BufferUsage.VERTEX, AccessPattern.STATIC);
            var handle = device.createBuffer(desc);
            assertNotEquals(Handle.invalid(), handle);
            assertTrue(device.isValidBuffer(handle));
        }

        @Test
        void destroyBufferInvalidatesHandle() {
            var desc = new BufferDescriptor(512, BufferUsage.INDEX, AccessPattern.DYNAMIC);
            var handle = device.createBuffer(desc);
            device.destroyBuffer(handle);
            assertFalse(device.isValidBuffer(handle));
        }

        @Test
        void createMultipleBuffers() {
            var v = device.createBuffer(new BufferDescriptor(256, BufferUsage.VERTEX, AccessPattern.STATIC));
            var i = device.createBuffer(new BufferDescriptor(128, BufferUsage.INDEX, AccessPattern.STATIC));
            var u = device.createBuffer(new BufferDescriptor(64, BufferUsage.UNIFORM, AccessPattern.DYNAMIC));
            assertNotEquals(v, i);
            assertNotEquals(i, u);
            assertTrue(device.isValidBuffer(v));
            assertTrue(device.isValidBuffer(i));
            assertTrue(device.isValidBuffer(u));
        }
    }

    @Nested
    class FrameLifecycle {
        @Test
        void beginAndEndFrame() {
            assertDoesNotThrow(() -> {
                device.beginFrame();
                device.endFrame();
            });
        }
    }

    @Nested
    class Capabilities {
        @Test
        void maxTextureSizeIsPositive() {
            int maxSize = device.queryCapability(DeviceCapability.MAX_TEXTURE_SIZE);
            assertTrue(maxSize >= 1024, "GPU should support at least 1024 texture size, got " + maxSize);
        }

        @Test
        void backendNameIsOpenGL() {
            String name = device.queryCapability(DeviceCapability.BACKEND_NAME);
            assertEquals("OpenGL", name);
        }

        @Test
        void deviceNameIsNonNull() {
            String name = device.queryCapability(DeviceCapability.DEVICE_NAME);
            assertNotNull(name);
            assertFalse(name.isEmpty());
        }
    }
}
