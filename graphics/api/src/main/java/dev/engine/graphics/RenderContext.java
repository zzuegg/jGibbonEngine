package dev.engine.graphics;

import dev.engine.core.handle.Handle;

public interface RenderContext {

    long frameNumber();

    void bindPipeline(Handle pipeline);
    void bindVertexBuffer(Handle buffer, Handle vertexInput);
    void bindIndexBuffer(Handle buffer);
    void bindUniformBuffer(int binding, Handle buffer);
    void draw(int vertexCount, int firstVertex);
    void drawIndexed(int indexCount, int firstIndex);
    void bindRenderTarget(Handle renderTarget);
    void bindDefaultRenderTarget();
    void clear(float r, float g, float b, float a);
    void viewport(int x, int y, int width, int height);
}
