package dev.engine.graphics.opengl;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.RenderContext;
import org.lwjgl.opengl.GL45;

class GlRenderContext implements RenderContext {

    private final long frameNumber;
    private final GlRenderDevice device;

    GlRenderContext(long frameNumber, GlRenderDevice device) {
        this.frameNumber = frameNumber;
        this.device = device;
    }

    @Override
    public long frameNumber() { return frameNumber; }

    @Override
    public void bindPipeline(Handle pipeline) {
        int program = device.getGlProgramName(pipeline);
        GL45.glUseProgram(program);
    }

    @Override
    public void bindVertexBuffer(Handle buffer, Handle vertexInput) {
        int vao = device.getGlVaoName(vertexInput);
        int vbo = device.getGlBufferName(buffer);
        GL45.glBindVertexArray(vao);
        // Bind the VBO to binding point 0 with stride from the VAO format
        int stride = device.getVertexInputStride(vertexInput);
        GL45.glVertexArrayVertexBuffer(vao, 0, vbo, 0, stride);
    }

    @Override
    public void draw(int vertexCount, int firstVertex) {
        GL45.glDrawArrays(GL45.GL_TRIANGLES, firstVertex, vertexCount);
    }

    @Override
    public void clear(float r, float g, float b, float a) {
        GL45.glClearColor(r, g, b, a);
        GL45.glClear(GL45.GL_COLOR_BUFFER_BIT | GL45.GL_DEPTH_BUFFER_BIT);
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        GL45.glViewport(x, y, width, height);
    }
}
