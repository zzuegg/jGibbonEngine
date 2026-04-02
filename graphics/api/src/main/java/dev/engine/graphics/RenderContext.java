package dev.engine.graphics;

import dev.engine.core.handle.Handle;

public interface RenderContext {

    long frameNumber();

    // Resource binding
    void bindPipeline(Handle<PipelineResource> pipeline);
    void bindVertexBuffer(Handle<BufferResource> buffer, Handle<VertexInputResource> vertexInput);
    void bindIndexBuffer(Handle<BufferResource> buffer);
    void bindUniformBuffer(int binding, Handle<BufferResource> buffer);
    void bindTexture(int unit, Handle<TextureResource> texture);
    void bindSampler(int unit, Handle<SamplerResource> sampler);

    // Draw commands
    void draw(int vertexCount, int firstVertex);
    void drawIndexed(int indexCount, int firstIndex);

    // Render targets
    void bindRenderTarget(Handle<RenderTargetResource> renderTarget);
    void bindDefaultRenderTarget();

    // Render state
    void setDepthTest(boolean enabled);
    void setBlending(boolean enabled);
    void setCullFace(boolean enabled);
    void setWireframe(boolean enabled);
    void clear(float r, float g, float b, float a);
    void viewport(int x, int y, int width, int height);
    void scissor(int x, int y, int width, int height);
}
