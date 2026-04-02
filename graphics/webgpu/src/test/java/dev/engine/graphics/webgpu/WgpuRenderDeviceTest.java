package dev.engine.graphics.webgpu;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.RenderCapability;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WgpuRenderDeviceTest {

    private WgpuRenderDevice device;

    @BeforeEach
    void setUp() {
        device = new WgpuRenderDevice();
    }

    @AfterEach
    void tearDown() {
        device.close();
    }

    @Test
    void deviceCreatesSuccessfully() {
        assertNotNull(device);
    }

    @Test
    void createAndDestroyBuffer() {
        var descriptor = new BufferDescriptor(1024, BufferUsage.VERTEX, AccessPattern.STATIC);
        Handle<BufferResource> buffer = device.createBuffer(descriptor);

        assertNotNull(buffer);
        assertTrue(device.isValidBuffer(buffer));

        device.destroyBuffer(buffer);
        assertFalse(device.isValidBuffer(buffer));
    }

    @Test
    void queryCapabilityReturnsValues() {
        Integer maxTexSize = device.queryCapability(RenderCapability.MAX_TEXTURE_SIZE);
        assertNotNull(maxTexSize);
        assertTrue(maxTexSize > 0);

        Integer maxFbWidth = device.queryCapability(RenderCapability.MAX_FRAMEBUFFER_WIDTH);
        assertNotNull(maxFbWidth);
        assertTrue(maxFbWidth > 0);

        Integer maxFbHeight = device.queryCapability(RenderCapability.MAX_FRAMEBUFFER_HEIGHT);
        assertNotNull(maxFbHeight);
        assertTrue(maxFbHeight > 0);
    }
}
