package dev.engine.graphics;

import dev.engine.core.handle.Handle;
import dev.engine.core.handle.HandlePool;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferWriter;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.graphics.vertex.VertexFormat;

import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
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
    public BufferWriter writeBuffer(Handle buffer) {
        var arena = Arena.ofConfined();
        var seg = arena.allocate(1024);
        return new BufferWriter() {
            @Override public java.lang.foreign.MemorySegment segment() { return seg; }
            @Override public void close() { arena.close(); }
        };
    }

    @Override
    public BufferWriter writeBuffer(Handle buffer, long offset, long length) {
        return writeBuffer(buffer);
    }

    @Override
    public Handle createTexture(TextureDescriptor descriptor) { return bufferPool.allocate(); }

    @Override
    public void uploadTexture(Handle texture, ByteBuffer pixels) {}

    @Override
    public void destroyTexture(Handle texture) { bufferPool.release(texture); }

    @Override
    public boolean isValidTexture(Handle texture) { return bufferPool.isValid(texture); }

    @Override
    public Handle createVertexInput(VertexFormat format) { return bufferPool.allocate(); }

    @Override
    public void destroyVertexInput(Handle vertexInput) { bufferPool.release(vertexInput); }

    @Override
    public Handle createPipeline(PipelineDescriptor descriptor) { return bufferPool.allocate(); }

    @Override
    public void destroyPipeline(Handle pipeline) { bufferPool.release(pipeline); }

    @Override
    public boolean isValidPipeline(Handle pipeline) { return bufferPool.isValid(pipeline); }

    @Override
    public RenderContext beginFrame() {
        long frame = frameCounter.incrementAndGet();
        return new RenderContext() {
            @Override public long frameNumber() { return frame; }
            @Override public void bindPipeline(Handle pipeline) {}
            @Override public void bindVertexBuffer(Handle buffer, Handle vertexInput) {}
            @Override public void draw(int vertexCount, int firstVertex) {}
            @Override public void clear(float r, float g, float b, float a) {}
            @Override public void viewport(int x, int y, int width, int height) {}
        };
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
