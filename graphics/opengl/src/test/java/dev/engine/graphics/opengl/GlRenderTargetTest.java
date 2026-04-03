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
import dev.engine.graphics.target.RenderTargetDescriptor;
import dev.engine.graphics.texture.TextureFormat;
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

class GlRenderTargetTest {

    static final String VS = """
            #version 450 core
            layout(location = 0) in vec3 position;
            void main() { gl_Position = vec4(position, 1.0); }
            """;
    static final String FS = """
            #version 450 core
            out vec4 fragColor;
            void main() { fragColor = vec4(0.0, 0.0, 1.0, 1.0); }
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
    void renderToTextureAndReadBack() {
        // Create render target
        var rt = device.createRenderTarget(RenderTargetDescriptor.color(128, 128, TextureFormat.RGBA8));
        var colorTex = device.getRenderTargetColorTexture(rt, 0);

        // Full-screen triangle
        var layout = StructLayout.of(Vertex.class);
        var verts = new Vertex[]{
                new Vertex(-1f, -1f, 0f), new Vertex(3f, -1f, 0f), new Vertex(-1f, 3f, 0f),
        };
        long vbSize = (long) layout.size() * verts.length;
        var vbo = device.createBuffer(new BufferDescriptor(vbSize, BufferUsage.VERTEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(vbo)) {
            for (int i = 0; i < verts.length; i++)
                layout.write(w.segment(), (long) layout.size() * i, verts[i]);
        }

        var format = VertexFormat.of(new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0));
        var vertexInput = device.createVertexInput(format);
        var pipeline = device.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, VS),
                new ShaderSource(ShaderStage.FRAGMENT, FS)));

        // Render to the render target
        device.beginFrame();
        var rec = new CommandRecorder();
        rec.bindRenderTarget(rt);
        rec.viewport(0, 0, 128, 128);
        rec.clear(0f, 0f, 0f, 1f);
        rec.bindPipeline(pipeline);
        rec.bindVertexBuffer(vbo, vertexInput);
        rec.draw(3, 0);
        rec.bindDefaultRenderTarget();
        device.submit(rec.finish());
        device.endFrame();

        // Read back from the color texture
        int glTex = device.getGlTextureName(colorTex);
        ByteBuffer readback = ByteBuffer.allocateDirect(128 * 128 * 4);
        GL45.glGetTextureImage(glTex, 0, GL45.GL_RGBA, GL45.GL_UNSIGNED_BYTE, readback);

        // Center pixel should be blue
        int centerOffset = (64 * 128 + 64) * 4;
        int b = readback.get(centerOffset + 2) & 0xFF;
        assertTrue(b > 200, "Blue channel should be high, got " + b);

        device.destroyRenderTarget(rt);
        device.destroyPipeline(pipeline);
        device.destroyVertexInput(vertexInput);
        device.destroyBuffer(vbo);
    }
}
