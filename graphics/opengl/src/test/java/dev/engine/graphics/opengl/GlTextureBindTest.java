package dev.engine.graphics.opengl;

import dev.engine.core.layout.StructLayout;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.command.CommandRecorder;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.graphics.texture.TextureDescriptor;
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

class GlTextureBindTest {

    // Shader that samples a texture at UV (0.5, 0.5)
    static final String VS = """
            #version 450 core
            layout(location = 0) in vec3 position;
            out vec2 uv;
            void main() {
                gl_Position = vec4(position, 1.0);
                uv = position.xy * 0.5 + 0.5;
            }
            """;
    static final String FS = """
            #version 450 core
            in vec2 uv;
            layout(binding = 0) uniform sampler2D tex;
            out vec4 fragColor;
            void main() {
                fragColor = texture(tex, uv);
            }
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
    void texturedTriangleUsesTextureColor() {
        // Create a 1x1 magenta texture
        var tex = device.createTexture(new TextureDescriptor(1, 1, TextureFormat.RGBA8));
        ByteBuffer magenta = ByteBuffer.allocateDirect(4);
        magenta.put((byte) 255).put((byte) 0).put((byte) 255).put((byte) 255).flip();
        device.uploadTexture(tex, magenta);

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
        rec.bindTexture(0, tex);
        rec.bindVertexBuffer(vbo, vertexInput);
        rec.draw(3, 0);
        device.submit(rec.finish());
        device.endFrame();

        // Center pixel should be magenta
        ByteBuffer pixel = ByteBuffer.allocateDirect(4);
        GL45.glReadPixels(32, 32, 1, 1, GL45.GL_RGBA, GL45.GL_UNSIGNED_BYTE, pixel);
        assertTrue((pixel.get(0) & 0xFF) > 200, "Red should be high");
        assertTrue((pixel.get(1) & 0xFF) < 50, "Green should be low");
        assertTrue((pixel.get(2) & 0xFF) > 200, "Blue should be high");

        GL45.glDeleteFramebuffers(fbo);
        GL45.glDeleteTextures(colorTex);
        GL45.glBindFramebuffer(GL45.GL_FRAMEBUFFER, 0);
        device.destroyTexture(tex);
        device.destroyPipeline(pipeline);
        device.destroyVertexInput(vertexInput);
        device.destroyBuffer(vbo);
    }
}
