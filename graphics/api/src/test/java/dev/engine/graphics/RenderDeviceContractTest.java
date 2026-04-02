package dev.engine.graphics;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RenderDeviceContractTest {

    @Nested
    class BufferCreation {
        @Test void createBufferReturnsValidHandle() {
            var device = new StubRenderDevice();
            var desc = new BufferDescriptor(1024, BufferUsage.VERTEX, AccessPattern.STATIC);
            var handle = device.createBuffer(desc);
            assertNotNull(handle);
            assertNotEquals(Handle.INVALID, handle);
        }

        @Test void destroyBufferInvalidatesHandle() {
            var device = new StubRenderDevice();
            var desc = new BufferDescriptor(1024, BufferUsage.VERTEX, AccessPattern.STATIC);
            var handle = device.createBuffer(desc);
            device.destroyBuffer(handle);
            assertFalse(device.isValidBuffer(handle));
        }
    }

    @Nested
    class FrameLifecycle {
        @Test void beginFrameReturnsContext() {
            var device = new StubRenderDevice();
            var ctx = device.beginFrame();
            assertNotNull(ctx);
        }

        @Test void endFrameCompletesWithoutError() {
            var device = new StubRenderDevice();
            var ctx = device.beginFrame();
            assertDoesNotThrow(() -> device.endFrame(ctx));
        }
    }

    @Nested
    class Capabilities {
        @Test void queryMaxTextureSizeReturnsPositive() {
            var device = new StubRenderDevice();
            int size = device.queryCapability(RenderCapability.MAX_TEXTURE_SIZE);
            assertTrue(size > 0);
        }
    }
}
