package dev.engine.graphics.common;

import dev.engine.core.handle.Handle;
import dev.engine.core.handle.HandlePool;
import dev.engine.graphics.*;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.buffer.BufferWriter;
import dev.engine.graphics.buffer.StreamingBuffer;
import dev.engine.graphics.sync.GpuFence;
import dev.engine.graphics.command.CommandList;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.target.RenderTargetDescriptor;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.core.mesh.VertexFormat;

import dev.engine.core.memory.NativeMemory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * A render device that does nothing — for testing without a GPU.
 * All resource operations return valid handles. Submit is a no-op.
 */
public class HeadlessRenderDevice implements RenderDevice {

    private final HandlePool<BufferResource> bufferPool = new HandlePool<>();
    private final HandlePool<TextureResource> texturePool = new HandlePool<>();
    private final HandlePool<RenderTargetResource> rtPool = new HandlePool<>();
    private final HandlePool<VertexInputResource> viPool = new HandlePool<>();
    private final HandlePool<SamplerResource> samplerPool = new HandlePool<>();
    private final HandlePool<PipelineResource> pipelinePool = new HandlePool<>();
    private final Map<Integer, Long> bufferSizes = new HashMap<>();

    @Override public Handle<BufferResource> createBuffer(BufferDescriptor d) {
        var h = bufferPool.allocate();
        bufferSizes.put(h.index(), d.size());
        return h;
    }
    @Override public void destroyBuffer(Handle<BufferResource> h) { bufferPool.release(h); }
    @Override public boolean isValidBuffer(Handle<BufferResource> h) { return bufferPool.isValid(h); }

    @Override public BufferWriter writeBuffer(Handle<BufferResource> h) {
        long size = bufferSizes.getOrDefault(h.index(), 1024L);
        return writeBuffer(h, 0, size);
    }
    @Override public BufferWriter writeBuffer(Handle<BufferResource> h, long offset, long length) {
        var bb = java.nio.ByteBuffer.allocate((int) length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        var memory = new dev.engine.core.memory.ByteBufferNativeMemory(bb);
        return new BufferWriter() {
            @Override public NativeMemory memory() { return memory; }
            @Override public void close() {}
        };
    }

    @Override public Handle<TextureResource> createTexture(TextureDescriptor d) { return texturePool.allocate(); }
    @Override public void uploadTexture(Handle<TextureResource> h, ByteBuffer p) {}
    @Override public void destroyTexture(Handle<TextureResource> h) { texturePool.release(h); }
    @Override public boolean isValidTexture(Handle<TextureResource> h) { return texturePool.isValid(h); }

    @Override public long getBindlessTextureHandle(Handle<TextureResource> texture) { return 0L; }

    @Override public Handle<RenderTargetResource> createRenderTarget(RenderTargetDescriptor d) { return rtPool.allocate(); }
    @Override public Handle<TextureResource> getRenderTargetColorTexture(Handle<RenderTargetResource> h, int i) { return Handle.invalid(); }
    @Override public void destroyRenderTarget(Handle<RenderTargetResource> h) { rtPool.release(h); }

    @Override public Handle<VertexInputResource> createVertexInput(VertexFormat f) { return viPool.allocate(); }
    @Override public void destroyVertexInput(Handle<VertexInputResource> h) { viPool.release(h); }

    @Override public Handle<SamplerResource> createSampler(SamplerDescriptor d) { return samplerPool.allocate(); }
    @Override public void destroySampler(Handle<SamplerResource> h) { samplerPool.release(h); }

    @Override public Handle<PipelineResource> createPipeline(PipelineDescriptor d) { return pipelinePool.allocate(); }
    @Override public void destroyPipeline(Handle<PipelineResource> h) { pipelinePool.release(h); }
    @Override public boolean isValidPipeline(Handle<PipelineResource> h) { return pipelinePool.isValid(h); }

    @Override public StreamingBuffer createStreamingBuffer(long frameSize, int frameCount, BufferUsage usage) { return null; }
    @Override public GpuFence createFence() {
        return new GpuFence() {
            @Override public boolean isSignaled() { return true; }
            @Override public void waitFor() {}
            @Override public boolean waitFor(long timeoutNanos) { return true; }
            @Override public void close() {}
        };
    }

    @Override public void beginFrame() {}
    @Override public void endFrame() {}
    @Override public void submit(CommandList commands) {}

    @Override
    @SuppressWarnings("unchecked")
    public <T> T queryCapability(DeviceCapability<T> cap) {
        if (cap == DeviceCapability.BACKEND_NAME) return (T) "Headless";
        if (cap == DeviceCapability.DEVICE_NAME) return (T) "Headless Test Device";
        if (cap == DeviceCapability.MAX_TEXTURE_SIZE) return (T) Integer.valueOf(4096);
        return null;
    }

    @Override public void close() {}
}
