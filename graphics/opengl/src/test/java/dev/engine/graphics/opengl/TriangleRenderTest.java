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

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end GPU rendering test:
 * Creates a window, uploads a triangle, compiles shaders,
 * renders to a framebuffer, reads back pixels to verify the triangle was drawn.
 */
class TriangleRenderTest {

    static final String VERTEX_SHADER = """
            #version 450 core
            layout(location = 0) in vec3 position;
            void main() {
                gl_Position = vec4(position, 1.0);
            }
            """;

    static final String FRAGMENT_SHADER = """
            #version 450 core
            out vec4 fragColor;
            void main() {
                fragColor = vec4(1.0, 0.0, 0.0, 1.0);
            }
            """;

    record Vertex(float x, float y, float z) {}

    static GlfwWindowToolkit toolkit;
    static GlRenderDevice device;

    @BeforeAll
    static void setUp() {
        toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.OPENGL_HINTS);
        var window = toolkit.createWindow(new WindowDescriptor("Render Test", 64, 64));
        device = new GlRenderDevice(window, new dev.engine.providers.lwjgl.graphics.opengl.LwjglGlBindings());
    }

    @AfterAll
    static void tearDown() {
        if (device != null) device.close();
        if (toolkit != null) toolkit.close();
    }

    @Test
    void renderRedTriangleAndVerifyPixels() {
        // 1. Create vertex buffer with a full-screen triangle
        var layout = StructLayout.of(Vertex.class);
        var vertices = new Vertex[]{
                new Vertex(-1f, -1f, 0f),
                new Vertex(3f, -1f, 0f),   // oversized to cover full screen
                new Vertex(-1f, 3f, 0f),
        };
        long bufSize = (long) layout.size() * vertices.length;
        var vbo = device.createBuffer(new BufferDescriptor(bufSize, BufferUsage.VERTEX, AccessPattern.STATIC));
        try (var writer = device.writeBuffer(vbo)) {
            for (int i = 0; i < vertices.length; i++) {
                layout.write(writer.segment(), (long) layout.size() * i, vertices[i]);
            }
        }

        // 2. Create vertex format (position = location 0, 3 floats)
        var format = VertexFormat.of(
                new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0)
        );
        var vertexInput = device.createVertexInput(format);

        // 3. Compile shader pipeline
        var pipeline = device.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, VERTEX_SHADER),
                new ShaderSource(ShaderStage.FRAGMENT, FRAGMENT_SHADER)
        ));

        // 4. Create an offscreen framebuffer for pixel readback
        int fbo = GL45.glCreateFramebuffers();
        int colorTex = GL45.glCreateTextures(GL45.GL_TEXTURE_2D);
        GL45.glTextureStorage2D(colorTex, 1, GL45.GL_RGBA8, 64, 64);
        GL45.glNamedFramebufferTexture(fbo, GL45.GL_COLOR_ATTACHMENT0, colorTex, 0);
        assertEquals(GL45.GL_FRAMEBUFFER_COMPLETE,
                GL45.glCheckNamedFramebufferStatus(fbo, GL45.GL_FRAMEBUFFER));

        // 5. Render
        GL45.glBindFramebuffer(GL45.GL_FRAMEBUFFER, fbo);
        device.beginFrame();
        var rec = new CommandRecorder();
        rec.viewport(0, 0, 64, 64);
        rec.clear(0f, 0f, 0f, 1f);
        rec.bindPipeline(pipeline);
        rec.bindVertexBuffer(vbo, vertexInput);
        rec.draw(3, 0);
        device.submit(rec.finish());
        device.endFrame();

        // 6. Read back center pixel — should be red
        ByteBuffer pixel = ByteBuffer.allocateDirect(4);
        GL45.glReadPixels(32, 32, 1, 1, GL45.GL_RGBA, GL45.GL_UNSIGNED_BYTE, pixel);

        int r = pixel.get(0) & 0xFF;
        int g = pixel.get(1) & 0xFF;
        int b = pixel.get(2) & 0xFF;
        int a = pixel.get(3) & 0xFF;

        assertTrue(r > 200, "Red channel should be high, got " + r);
        assertTrue(g < 50, "Green channel should be low, got " + g);
        assertTrue(b < 50, "Blue channel should be low, got " + b);
        assertTrue(a > 200, "Alpha channel should be high, got " + a);

        // 7. Cleanup
        GL45.glDeleteFramebuffers(fbo);
        GL45.glDeleteTextures(colorTex);
        GL45.glBindFramebuffer(GL45.GL_FRAMEBUFFER, 0);
        device.destroyPipeline(pipeline);
        device.destroyVertexInput(vertexInput);
        device.destroyBuffer(vbo);
    }
}
