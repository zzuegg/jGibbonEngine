package dev.engine.graphics;

import dev.engine.core.handle.Handle;
import dev.engine.core.handle.HandlePool;
import dev.engine.graphics.buffer.BufferDescriptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

class StubRenderDevice implements RenderDevice {

    private final HandlePool bufferPool = new HandlePool();
    private final AtomicLong frameCounter = new AtomicLong(0);
    private final Map<RenderCapability<?>, Object> capabilities = new ConcurrentHashMap<>();

    StubRenderDevice() {
        capabilities.put(RenderCapability.MAX_TEXTURE_SIZE, 4096);
        capabilities.put(RenderCapability.MAX_FRAMEBUFFER_WIDTH, 8192);
        capabilities.put(RenderCapability.MAX_FRAMEBUFFER_HEIGHT, 8192);
    }

    @Override
    public Handle createBuffer(BufferDescriptor descriptor) {
        return bufferPool.allocate();
    }

    @Override
    public void destroyBuffer(Handle buffer) {
        bufferPool.release(buffer);
    }

    @Override
    public boolean isValidBuffer(Handle buffer) {
        return bufferPool.isValid(buffer);
    }

    @Override
    public RenderContext beginFrame() {
        long frame = frameCounter.incrementAndGet();
        return () -> frame;
    }

    @Override
    public void endFrame(RenderContext context) {
        // no-op for stub
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T queryCapability(RenderCapability<T> capability) {
        return (T) capabilities.get(capability);
    }

    @Override
    public void close() {
        // no-op for stub
    }
}
