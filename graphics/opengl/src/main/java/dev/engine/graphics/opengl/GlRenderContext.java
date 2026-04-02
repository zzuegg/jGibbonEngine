package dev.engine.graphics.opengl;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.PipelineResource;
import dev.engine.graphics.RenderContext;
import dev.engine.graphics.RenderTargetResource;
import dev.engine.graphics.SamplerResource;
import dev.engine.graphics.TextureResource;
import dev.engine.graphics.VertexInputResource;
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
    public void bindPipeline(Handle<PipelineResource> pipeline) {
        int program = device.getGlProgramName(pipeline);
        GL45.glUseProgram(program);
    }

    @Override
    public void bindVertexBuffer(Handle<BufferResource> buffer, Handle<VertexInputResource> vertexInput) {
        int vao = device.getGlVaoName(vertexInput);
        int vbo = device.getGlBufferName(buffer);
        GL45.glBindVertexArray(vao);
        // Bind the VBO to binding point 0 with stride from the VAO format
        int stride = device.getVertexInputStride(vertexInput);
        GL45.glVertexArrayVertexBuffer(vao, 0, vbo, 0, stride);
    }

    @Override
    public void bindIndexBuffer(Handle<BufferResource> buffer) {
        int ibo = device.getGlBufferName(buffer);
        GL45.glBindBuffer(GL45.GL_ELEMENT_ARRAY_BUFFER, ibo);
    }

    @Override
    public void bindUniformBuffer(int binding, Handle<BufferResource> buffer) {
        int ubo = device.getGlBufferName(buffer);
        GL45.glBindBufferBase(GL45.GL_UNIFORM_BUFFER, binding, ubo);
    }

    @Override
    public void bindTexture(int unit, Handle<TextureResource> texture) {
        int glTex = device.getGlTextureName(texture);
        GL45.glBindTextureUnit(unit, glTex);
    }

    @Override
    public void bindSampler(int unit, Handle<SamplerResource> sampler) {
        GL45.glBindSampler(unit, device.getGlSamplerName(sampler));
    }

    @Override
    public void draw(int vertexCount, int firstVertex) {
        GL45.glDrawArrays(GL45.GL_TRIANGLES, firstVertex, vertexCount);
    }

    @Override
    public void drawIndexed(int indexCount, int firstIndex) {
        GL45.glDrawElements(GL45.GL_TRIANGLES, indexCount, GL45.GL_UNSIGNED_INT,
                (long) firstIndex * Integer.BYTES);
    }

    @Override
    public void bindRenderTarget(Handle<RenderTargetResource> renderTarget) {
        int fbo = device.getGlFboName(renderTarget);
        GL45.glBindFramebuffer(GL45.GL_FRAMEBUFFER, fbo);
    }

    @Override
    public void bindDefaultRenderTarget() {
        GL45.glBindFramebuffer(GL45.GL_FRAMEBUFFER, 0);
    }

    @Override
    public void setDepthTest(boolean enabled) {
        if (enabled) GL45.glEnable(GL45.GL_DEPTH_TEST);
        else GL45.glDisable(GL45.GL_DEPTH_TEST);
    }

    @Override
    public void setBlending(boolean enabled) {
        if (enabled) {
            GL45.glEnable(GL45.GL_BLEND);
            GL45.glBlendFunc(GL45.GL_SRC_ALPHA, GL45.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            GL45.glDisable(GL45.GL_BLEND);
        }
    }

    @Override
    public void setCullFace(boolean enabled) {
        if (enabled) GL45.glEnable(GL45.GL_CULL_FACE);
        else GL45.glDisable(GL45.GL_CULL_FACE);
    }

    @Override
    public void setWireframe(boolean enabled) {
        GL45.glPolygonMode(GL45.GL_FRONT_AND_BACK, enabled ? GL45.GL_LINE : GL45.GL_FILL);
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

    @Override
    public void scissor(int x, int y, int width, int height) {
        GL45.glScissor(x, y, width, height);
    }
}
