package dev.engine.graphics.common;

import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.graphics.texture.TextureFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GpuResourceManagerCleanerTest {

    private HeadlessRenderDevice device;
    private GpuResourceManager gpu;

    @BeforeEach
    void setUp() {
        device = new HeadlessRenderDevice();
        gpu = new GpuResourceManager(device);
    }

    @Test void explicitDestroyMarksClosed() {
        var buffer = gpu.createBuffer(64, BufferUsage.VERTEX, AccessPattern.STATIC);
        assertFalse(buffer.isClosed());
        gpu.destroyBuffer(buffer);
        assertTrue(buffer.isClosed());
    }

    @Test void doubleDestroyIsSafe() {
        var buffer = gpu.createBuffer(64, BufferUsage.VERTEX, AccessPattern.STATIC);
        gpu.destroyBuffer(buffer);
        assertDoesNotThrow(() -> gpu.destroyBuffer(buffer));
    }

    @Test void explicitDestroyUpdatesResourceStats() {
        var buffer = gpu.createBuffer(64, BufferUsage.VERTEX, AccessPattern.STATIC);
        assertEquals(1, gpu.liveBufferCount());
        gpu.destroyBuffer(buffer);
        flushDeferred();
        assertEquals(0, gpu.liveBufferCount());
    }

    @Test void textureExplicitDestroyMarksClosed() {
        var tex = gpu.createTexture(new TextureDescriptor(16, 16, TextureFormat.RGBA8));
        assertFalse(tex.isClosed());
        gpu.destroyTexture(tex);
        assertTrue(tex.isClosed());
    }

    @Test void abandonedBufferCleanedByGC() throws InterruptedException {
        createAndAbandonBuffer();

        for (int i = 0; i < 10; i++) {
            System.gc();
            Thread.sleep(50);
        }
        flushDeferred();

        assertEquals(0, gpu.liveBufferCount(),
                "Abandoned buffer should have been cleaned by GC + processDeferred");
    }

    @Test void abandonedTextureCleanedByGC() throws InterruptedException {
        createAndAbandonTexture();

        for (int i = 0; i < 10; i++) {
            System.gc();
            Thread.sleep(50);
        }
        flushDeferred();

        assertEquals(0, gpu.liveTextureCount(),
                "Abandoned texture should have been cleaned by GC + processDeferred");
    }

    @Test void explicitlyClosedBufferNotDoubleFreed() throws InterruptedException {
        var buffer = gpu.createBuffer(64, BufferUsage.UNIFORM, AccessPattern.DYNAMIC);
        gpu.destroyBuffer(buffer);
        flushDeferred();

        assertEquals(0, gpu.liveBufferCount());

        // Let GC run — Cleaner should NOT decrement again
        System.gc();
        Thread.sleep(200);
        flushDeferred();

        assertEquals(0, gpu.liveBufferCount(), "Should still be 0, not negative");
    }

    @Test void pipelineExplicitDestroyMarksClosed() {
        var pipeline = gpu.createPipeline(null);
        assertFalse(pipeline.isClosed());
        gpu.destroyPipeline(pipeline);
        assertTrue(pipeline.isClosed());
    }

    @Test void samplerExplicitDestroyMarksClosed() {
        var sampler = gpu.createSampler(null);
        assertFalse(sampler.isClosed());
        gpu.destroySampler(sampler);
        assertTrue(sampler.isClosed());
    }

    @Test void vertexInputExplicitDestroyMarksClosed() {
        var vi = gpu.createVertexInput(null);
        assertFalse(vi.isClosed());
        gpu.destroyVertexInput(vi);
        assertTrue(vi.isClosed());
    }

    /** Flushes the entire deferral ring — processes all pending deletions regardless of depth. */
    private void flushDeferred() {
        // Process enough times to rotate through the entire ring + 1
        for (int i = 0; i < 4; i++) {
            gpu.processDeferred();
        }
    }

    private void createAndAbandonBuffer() {
        gpu.createBuffer(64, BufferUsage.VERTEX, AccessPattern.STATIC);
    }

    private void createAndAbandonTexture() {
        gpu.createTexture(new TextureDescriptor(16, 16, TextureFormat.RGBA8));
    }
}
