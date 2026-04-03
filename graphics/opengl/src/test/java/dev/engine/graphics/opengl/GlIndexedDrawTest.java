package dev.engine.graphics.opengl;

import dev.engine.windowing.glfw.GlfwWindowToolkit;

import dev.engine.core.layout.StructLayout;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.command.CommandRecorder;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.core.mesh.ComponentType;
import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.graphics.window.WindowDescriptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL45;

import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class GlIndexedDrawTest {

    static final String VS = """
            #version 450 core
            layout(location = 0) in vec3 position;
            void main() { gl_Position = vec4(position, 1.0); }
            """;
    static final String FS = """
            #version 450 core
            out vec4 fragColor;
            void main() { fragColor = vec4(0.0, 1.0, 0.0, 1.0); }
            """;

    record Vertex(float x, float y, float z) {}

    static GlfwWindowToolkit toolkit;
    static GlRenderDevice device;

    @BeforeAll
    static void setUp() {
        toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.OPENGL_HINTS);
        var window = toolkit.createWindow(new WindowDescriptor("GPU Test", 1, 1));
        device = new GlRenderDevice(window, new dev.engine.providers.lwjgl.graphics.opengl.LwjglGlBindings());
    }

    @AfterAll
    static void tearDown() {
        if (device != null) device.close();
        if (toolkit != null) toolkit.close();
    }

    @Test
    void indexedDrawRendersQuad() {
        // 4 vertices for a quad, 6 indices (two triangles)
        var layout = StructLayout.of(Vertex.class);
        var verts = new Vertex[]{
                new Vertex(-1f, -1f, 0f),
                new Vertex(1f, -1f, 0f),
                new Vertex(1f, 1f, 0f),
                new Vertex(-1f, 1f, 0f),
        };
        long vbSize = (long) layout.size() * verts.length;
        var vbo = device.createBuffer(new BufferDescriptor(vbSize, BufferUsage.VERTEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(vbo)) {
            for (int i = 0; i < verts.length; i++)
                layout.write(w.segment(), (long) layout.size() * i, verts[i]);
        }

        // Index buffer: two triangles forming a quad
        int[] indices = {0, 1, 2, 0, 2, 3};
        long ibSize = (long) indices.length * Integer.BYTES;
        var ibo = device.createBuffer(new BufferDescriptor(ibSize, BufferUsage.INDEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(ibo)) {
            for (int i = 0; i < indices.length; i++)
                w.segment().setAtIndex(ValueLayout.JAVA_INT, i, indices[i]);
        }

        var format = VertexFormat.of(new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0));
        var vertexInput = device.createVertexInput(format);
        var pipeline = device.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, VS),
                new ShaderSource(ShaderStage.FRAGMENT, FS)));

        // Offscreen FBO
        int fbo = GL45.glCreateFramebuffers();
        int colorTex = GL45.glCreateTextures(GL45.GL_TEXTURE_2D);
        GL45.glTextureStorage2D(colorTex, 1, GL45.GL_RGBA8, 64, 64);
        GL45.glNamedFramebufferTexture(fbo, GL45.GL_COLOR_ATTACHMENT0, colorTex, 0);

        GL45.glBindFramebuffer(GL45.GL_FRAMEBUFFER, fbo);
        device.beginFrame();
        var rec = new CommandRecorder();
        rec.viewport(0, 0, 64, 64);
        rec.clear(0f, 0f, 0f, 1f);
        rec.bindPipeline(pipeline);
        rec.bindVertexBuffer(vbo, vertexInput);
        rec.bindIndexBuffer(ibo);
        rec.drawIndexed(6, 0);
        device.submit(rec.finish());
        device.endFrame();

        // Center pixel should be green
        ByteBuffer pixel = ByteBuffer.allocateDirect(4);
        GL45.glReadPixels(32, 32, 1, 1, GL45.GL_RGBA, GL45.GL_UNSIGNED_BYTE, pixel);
        assertTrue((pixel.get(1) & 0xFF) > 200, "Green channel should be high");

        GL45.glDeleteFramebuffers(fbo);
        GL45.glDeleteTextures(colorTex);
        GL45.glBindFramebuffer(GL45.GL_FRAMEBUFFER, 0);
        device.destroyPipeline(pipeline);
        device.destroyVertexInput(vertexInput);
        device.destroyBuffer(vbo);
        device.destroyBuffer(ibo);
    }
}
