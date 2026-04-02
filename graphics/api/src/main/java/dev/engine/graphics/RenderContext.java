package dev.engine.graphics;

import dev.engine.core.handle.Handle;

public interface RenderContext {

    long frameNumber();

    void bindPipeline(Handle<PipelineResource> pipeline);
    void bindVertexBuffer(Handle<BufferResource> buffer, Handle<VertexInputResource> vertexInput);
    void bindIndexBuffer(Handle<BufferResource> buffer);
    void bindUniformBuffer(int binding, Handle<BufferResource> buffer);
    void draw(int vertexCount, int firstVertex);
    void drawIndexed(int indexCount, int firstIndex);
    void bindRenderTarget(Handle<RenderTargetResource> renderTarget);
    void bindDefaultRenderTarget();
    void setDepthTest(boolean enabled);
    void clear(float r, float g, float b, float a);
    void viewport(int x, int y, int width, int height);
}
