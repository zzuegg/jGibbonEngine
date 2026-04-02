package dev.engine.graphics;

import dev.engine.core.handle.Handle;
import dev.engine.core.handle.HandlePool;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.buffer.BufferWriter;
import dev.engine.graphics.buffer.StreamingBuffer;
import dev.engine.graphics.sync.GpuFence;
import dev.engine.graphics.command.CommandList;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.core.mesh.VertexFormat;

import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

class StubRenderDevice implements RenderDevice {

    private final HandlePool<BufferResource> bufferPool = new HandlePool<>();
    private final HandlePool<TextureResource> texturePool = new HandlePool<>();
    private final HandlePool<RenderTargetResource> renderTargetPool = new HandlePool<>();
    private final HandlePool<VertexInputResource> vertexInputPool = new HandlePool<>();
    private final HandlePool<SamplerResource> samplerPool = new HandlePool<>();
    private final HandlePool<PipelineResource> pipelinePool = new HandlePool<>();
    private final AtomicLong frameCounter = new AtomicLong(0);
    private final Map<DeviceCapability<?>, Object> capabilities = new ConcurrentHashMap<>();

    StubRenderDevice() {
        capabilities.put(DeviceCapability.MAX_TEXTURE_SIZE, 4096);
        capabilities.put(DeviceCapability.MAX_FRAMEBUFFER_WIDTH, 8192);
        capabilities.put(DeviceCapability.MAX_FRAMEBUFFER_HEIGHT, 8192);
    }

    @Override
    public Handle<BufferResource> createBuffer(BufferDescriptor descriptor) {
        return bufferPool.allocate();
    }

    @Override
    public void destroyBuffer(Handle<BufferResource> buffer) {
        bufferPool.release(buffer);
    }

    @Override
    public boolean isValidBuffer(Handle<BufferResource> buffer) {
        return bufferPool.isValid(buffer);
    }

    @Override
    public BufferWriter writeBuffer(Handle<BufferResource> buffer) {
        var arena = Arena.ofConfined();
        var seg = arena.allocate(1024);
        return new BufferWriter() {
            @Override public java.lang.foreign.MemorySegment segment() { return seg; }
            @Override public void close() { arena.close(); }
        };
    }

    @Override
    public BufferWriter writeBuffer(Handle<BufferResource> buffer, long offset, long length) {
        return writeBuffer(buffer);
    }

    @Override
    public Handle<TextureResource> createTexture(TextureDescriptor descriptor) { return texturePool.allocate(); }

    @Override
    public void uploadTexture(Handle<TextureResource> texture, ByteBuffer pixels) {}

    @Override
    public void destroyTexture(Handle<TextureResource> texture) { texturePool.release(texture); }

    @Override
    public boolean isValidTexture(Handle<TextureResource> texture) { return texturePool.isValid(texture); }

    @Override
    public long getBindlessTextureHandle(Handle<TextureResource> texture) { return 0L; }

    @Override
    public Handle<RenderTargetResource> createRenderTarget(dev.engine.graphics.target.RenderTargetDescriptor descriptor) { return renderTargetPool.allocate(); }

    @Override
    public Handle<TextureResource> getRenderTargetColorTexture(Handle<RenderTargetResource> renderTarget, int index) { return Handle.invalid(); }

    @Override
    public void destroyRenderTarget(Handle<RenderTargetResource> renderTarget) { renderTargetPool.release(renderTarget); }

    @Override
    public Handle<VertexInputResource> createVertexInput(VertexFormat format) { return vertexInputPool.allocate(); }

    @Override
    public void destroyVertexInput(Handle<VertexInputResource> vertexInput) { vertexInputPool.release(vertexInput); }

    @Override
    public Handle<SamplerResource> createSampler(SamplerDescriptor descriptor) { return samplerPool.allocate(); }

    @Override
    public void destroySampler(Handle<SamplerResource> sampler) { samplerPool.release(sampler); }

    @Override
    public Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor) { return pipelinePool.allocate(); }

    @Override
    public void destroyPipeline(Handle<PipelineResource> pipeline) { pipelinePool.release(pipeline); }

    @Override
    public boolean isValidPipeline(Handle<PipelineResource> pipeline) { return pipelinePool.isValid(pipeline); }

    @Override
    public StreamingBuffer createStreamingBuffer(long frameSize, int frameCount, BufferUsage usage) {
        return null;
    }

    @Override
    public GpuFence createFence() {
        return new GpuFence() {
            @Override public boolean isSignaled() { return true; }
            @Override public void waitFor() {}
            @Override public boolean waitFor(long timeoutNanos) { return true; }
            @Override public void close() {}
        };
    }

    @Override
    public void beginFrame() {
        frameCounter.incrementAndGet();
    }

    @Override
    public void endFrame() {
        // no-op for stub
    }

    @Override
    public void submit(CommandList commands) {
        // no-op for stub
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T queryCapability(DeviceCapability<T> capability) {
        return (T) capabilities.get(capability);
    }

    @Override
    public void close() {
        // no-op for stub
    }
}
