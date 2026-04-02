package dev.engine.graphics;

import dev.engine.core.handle.Handle;

public interface RenderContext {

    long frameNumber();

    void bindPipeline(Handle pipeline);
    void bindVertexBuffer(Handle buffer, Handle vertexInput);
    void draw(int vertexCount, int firstVertex);
    void clear(float r, float g, float b, float a);
    void viewport(int x, int y, int width, int height);
}
