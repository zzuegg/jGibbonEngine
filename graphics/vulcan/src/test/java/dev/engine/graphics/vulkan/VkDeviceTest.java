package dev.engine.graphics.vulkan;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.RenderCapability;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.opengl.GlfwWindowToolkit;
import dev.engine.graphics.window.WindowDescriptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VkDeviceTest {

    static GlfwWindowToolkit toolkit;
    static VkRenderDevice device;

    @BeforeAll
    static void setUp() {
        toolkit = new GlfwWindowToolkit();
        var window = toolkit.createWindow(new WindowDescriptor("Vulkan Test", 1, 1));
        device = new VkRenderDevice((GlfwWindowToolkit.GlfwWindowHandle) window);
    }

    @AfterAll
    static void tearDown() {
        if (device != null) device.close();
        if (toolkit != null) toolkit.close();
    }

    @Nested
    class DeviceCreation {
        @Test void deviceCreatesSuccessfully() {
            assertNotNull(device);
        }

        @Test void maxTextureSizeIsPositive() {
            int maxSize = device.queryCapability(RenderCapability.MAX_TEXTURE_SIZE);
            assertTrue(maxSize >= 1024, "Got " + maxSize);
        }
    }

    @Nested
    class BufferOperations {
        @Test void createBufferReturnsValidHandle() {
            var desc = new BufferDescriptor(1024, BufferUsage.VERTEX, AccessPattern.STATIC);
            var handle = device.createBuffer(desc);
            assertNotEquals(Handle.invalid(), handle);
            assertTrue(device.isValidBuffer(handle));
            device.destroyBuffer(handle);
        }

        @Test void destroyBufferInvalidatesHandle() {
            var desc = new BufferDescriptor(512, BufferUsage.INDEX, AccessPattern.DYNAMIC);
            var handle = device.createBuffer(desc);
            device.destroyBuffer(handle);
            assertFalse(device.isValidBuffer(handle));
        }
    }
}
