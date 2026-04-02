package dev.engine.graphics;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferWriter;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.target.RenderTargetDescriptor;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.graphics.vertex.VertexFormat;

import java.nio.ByteBuffer;

public interface RenderDevice extends AutoCloseable {

    Handle<BufferResource> createBuffer(BufferDescriptor descriptor);
    void destroyBuffer(Handle<BufferResource> buffer);
    boolean isValidBuffer(Handle<BufferResource> buffer);
    BufferWriter writeBuffer(Handle<BufferResource> buffer);
    BufferWriter writeBuffer(Handle<BufferResource> buffer, long offset, long length);

    Handle<TextureResource> createTexture(TextureDescriptor descriptor);
    void uploadTexture(Handle<TextureResource> texture, ByteBuffer pixels);
    void destroyTexture(Handle<TextureResource> texture);
    boolean isValidTexture(Handle<TextureResource> texture);

    Handle<RenderTargetResource> createRenderTarget(RenderTargetDescriptor descriptor);
    Handle<TextureResource> getRenderTargetColorTexture(Handle<RenderTargetResource> renderTarget, int index);
    void destroyRenderTarget(Handle<RenderTargetResource> renderTarget);

    Handle<VertexInputResource> createVertexInput(VertexFormat format);
    void destroyVertexInput(Handle<VertexInputResource> vertexInput);

    Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor);
    void destroyPipeline(Handle<PipelineResource> pipeline);
    boolean isValidPipeline(Handle<PipelineResource> pipeline);

    RenderContext beginFrame();
    void endFrame(RenderContext context);

    <T> T queryCapability(RenderCapability<T> capability);

    @Override
    void close();
}
