package dev.engine.graphics;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.buffer.BufferWriter;
import dev.engine.graphics.buffer.StreamingBuffer;
import dev.engine.graphics.sync.GpuFence;
import dev.engine.graphics.command.CommandExecutor;
import dev.engine.graphics.command.CommandList;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.target.RenderTargetDescriptor;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.core.mesh.VertexFormat;

import java.nio.ByteBuffer;

/**
 * Low-level backend interface for GPU resource management and command execution.
 * Users should not use this directly — use {@code Renderer} from graphics:common instead.
 */
public interface RenderDevice extends AutoCloseable {

    // --- Buffers ---
    Handle<BufferResource> createBuffer(BufferDescriptor descriptor);
    void destroyBuffer(Handle<BufferResource> buffer);
    boolean isValidBuffer(Handle<BufferResource> buffer);
    BufferWriter writeBuffer(Handle<BufferResource> buffer);
    BufferWriter writeBuffer(Handle<BufferResource> buffer, long offset, long length);

    // --- Textures ---
    Handle<TextureResource> createTexture(TextureDescriptor descriptor);
    void uploadTexture(Handle<TextureResource> texture, ByteBuffer pixels);
    void destroyTexture(Handle<TextureResource> texture);
    boolean isValidTexture(Handle<TextureResource> texture);

    // --- Render targets ---
    Handle<RenderTargetResource> createRenderTarget(RenderTargetDescriptor descriptor);
    Handle<TextureResource> getRenderTargetColorTexture(Handle<RenderTargetResource> renderTarget, int index);
    void destroyRenderTarget(Handle<RenderTargetResource> renderTarget);

    // --- Vertex input ---
    Handle<VertexInputResource> createVertexInput(VertexFormat format);
    void destroyVertexInput(Handle<VertexInputResource> vertexInput);

    // --- Samplers ---
    Handle<SamplerResource> createSampler(SamplerDescriptor descriptor);
    void destroySampler(Handle<SamplerResource> sampler);

    // --- Pipelines ---
    Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor);
    void destroyPipeline(Handle<PipelineResource> pipeline);
    boolean isValidPipeline(Handle<PipelineResource> pipeline);

    // --- Frame lifecycle ---
    void beginFrame();
    void endFrame();

    // --- Command execution ---
    void submit(CommandList commands);

    // --- Streaming buffers ---
    StreamingBuffer createStreamingBuffer(long frameSize, int frameCount, BufferUsage usage);

    // --- Synchronization ---
    GpuFence createFence();

    // --- Bindless textures ---
    long getBindlessTextureHandle(Handle<TextureResource> texture);

    // --- Capabilities ---
    <T> T queryCapability(DeviceCapability<T> capability);

    default boolean supports(DeviceCapability<Boolean> feature) {
        Boolean result = queryCapability(feature);
        return result != null && result;
    }

    @Override
    void close();
}
