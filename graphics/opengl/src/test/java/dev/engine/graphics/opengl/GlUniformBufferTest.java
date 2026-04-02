package dev.engine.graphics.opengl;

import dev.engine.core.layout.StructLayout;
import dev.engine.core.math.Mat4;
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

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class GlUniformBufferTest {

    // Shader that reads MVP from UBO at binding 0 and scales position
    static final String VS = """
            #version 450 core
            layout(location = 0) in vec3 position;
            layout(row_major, std140, binding = 0) uniform Matrices {
                mat4 mvp;
            };
            void main() {
                gl_Position = mvp * vec4(position, 1.0);
            }
            """;
    static final String FS = """
            #version 450 core
            out vec4 fragColor;
            void main() { fragColor = vec4(1.0, 1.0, 0.0, 1.0); }
            """;

    record Vertex(float x, float y, float z) {}

    static GlfwWindowToolkit toolkit;
    static GlRenderDevice device;

    @BeforeAll
    static void setUp() {
        toolkit = new GlfwWindowToolkit();
        var window = toolkit.createWindow(new WindowDescriptor("GPU Test", 1, 1));
        device = new GlRenderDevice((GlfwWindowToolkit.GlfwWindowHandle) window);
    }

    @AfterAll
    static void tearDown() {
        if (device != null) device.close();
        if (toolkit != null) toolkit.close();
    }

    @Test
    void uniformBufferAffectsRendering() {
        // Full-screen triangle
        var layout = StructLayout.of(Vertex.class);
        var verts = new Vertex[]{
                new Vertex(-1f, -1f, 0f),
                new Vertex(3f, -1f, 0f),
                new Vertex(-1f, 3f, 0f),
        };
        long vbSize = (long) layout.size() * verts.length;
        var vbo = device.createBuffer(new BufferDescriptor(vbSize, BufferUsage.VERTEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(vbo)) {
            for (int i = 0; i < verts.length; i++)
                layout.write(w.segment(), (long) layout.size() * i, verts[i]);
        }

        // UBO with identity matrix — should render normally
        var matLayout = StructLayout.of(Mat4.class);
        var ubo = device.createBuffer(new BufferDescriptor(matLayout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC));
        try (var w = device.writeBuffer(ubo)) {
            matLayout.write(w.segment(), 0, Mat4.IDENTITY);
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
        rec.bindUniformBuffer(0, ubo);
        rec.bindVertexBuffer(vbo, vertexInput);
        rec.draw(3, 0);
        device.submit(rec.finish());
        device.endFrame();

        // Center pixel should be yellow (from fragment shader)
        ByteBuffer pixel = ByteBuffer.allocateDirect(4);
        GL45.glReadPixels(32, 32, 1, 1, GL45.GL_RGBA, GL45.GL_UNSIGNED_BYTE, pixel);
        assertTrue((pixel.get(0) & 0xFF) > 200, "Red channel should be high, got " + (pixel.get(0) & 0xFF));
        assertTrue((pixel.get(1) & 0xFF) > 200, "Green channel should be high, got " + (pixel.get(1) & 0xFF));

        GL45.glDeleteFramebuffers(fbo);
        GL45.glDeleteTextures(colorTex);
        GL45.glBindFramebuffer(GL45.GL_FRAMEBUFFER, 0);
        device.destroyPipeline(pipeline);
        device.destroyVertexInput(vertexInput);
        device.destroyBuffer(vbo);
        device.destroyBuffer(ubo);
    }
}
