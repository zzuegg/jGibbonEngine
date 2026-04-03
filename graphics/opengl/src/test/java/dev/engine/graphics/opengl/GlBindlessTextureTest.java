package dev.engine.graphics.opengl;

import dev.engine.windowing.glfw.GlfwWindowToolkit;

import dev.engine.core.layout.StructLayout;
import dev.engine.graphics.DeviceCapability;
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
import static org.junit.jupiter.api.Assumptions.*;

class GlBindlessTextureTest {

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
    void getBindlessHandle() {
        assumeTrue(device.supports(DeviceCapability.BINDLESS_TEXTURES),
                "Bindless textures not supported");

        var tex = device.createTexture(new TextureDescriptor(2, 2, TextureFormat.RGBA8));
        ByteBuffer red = ByteBuffer.allocateDirect(16);
        for (int i = 0; i < 4; i++) red.put((byte)255).put((byte)0).put((byte)0).put((byte)255);
        red.flip();
        device.uploadTexture(tex, red);

        long bindlessHandle = device.getBindlessTextureHandle(tex);
        assertTrue(bindlessHandle != 0, "Bindless handle should be non-zero");

        device.destroyTexture(tex);
    }

    @Test
    void bindlessTextureInShader() {
        assumeTrue(device.supports(DeviceCapability.BINDLESS_TEXTURES),
                "Bindless textures not supported");

        // Create a 1x1 cyan texture
        var tex = device.createTexture(new TextureDescriptor(1, 1, TextureFormat.RGBA8));
        ByteBuffer cyan = ByteBuffer.allocateDirect(4);
        cyan.put((byte)0).put((byte)255).put((byte)255).put((byte)255).flip();
        device.uploadTexture(tex, cyan);

        long bindlessHandle = device.getBindlessTextureHandle(tex);

        // SSBO holding the texture handle
        var handleBuf = device.createBuffer(new BufferDescriptor(8, BufferUsage.STORAGE, AccessPattern.DYNAMIC));
        try (var w = device.writeBuffer(handleBuf)) {
            w.memory().putLong(0, bindlessHandle);
        }

        // Shader that reads bindless texture from SSBO
        String vs = """
                #version 450 core
                #extension GL_ARB_bindless_texture : require
                layout(location = 0) in vec3 position;
                void main() { gl_Position = vec4(position, 1.0); }
                """;
        String fs = """
                #version 450 core
                #extension GL_ARB_bindless_texture : require
                layout(std430, binding = 0) buffer TextureHandles {
                    sampler2D texHandle;
                };
                out vec4 fragColor;
                void main() {
                    fragColor = texture(texHandle, vec2(0.5, 0.5));
                }
                """;

        var layout = StructLayout.of(Vertex.class);
        var verts = new Vertex[]{
                new Vertex(-1,-1,0), new Vertex(3,-1,0), new Vertex(-1,3,0),
        };
        long vbSize = (long) layout.size() * verts.length;
        var vbo = device.createBuffer(new BufferDescriptor(vbSize, BufferUsage.VERTEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(vbo)) {
            for (int i = 0; i < verts.length; i++)
                layout.write(w.memory(), (long) layout.size() * i, verts[i]);
        }

        var format = VertexFormat.of(new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0));
        var vertexInput = device.createVertexInput(format);
        var pipeline = device.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, vs),
                new ShaderSource(ShaderStage.FRAGMENT, fs)));

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
        rec.bindStorageBuffer(0, handleBuf);
        rec.bindVertexBuffer(vbo, vertexInput);
        rec.draw(3, 0);
        device.submit(rec.finish());
        device.endFrame();

        ByteBuffer pixel = ByteBuffer.allocateDirect(4);
        GL45.glReadPixels(32, 32, 1, 1, GL45.GL_RGBA, GL45.GL_UNSIGNED_BYTE, pixel);
        int g = pixel.get(1) & 0xFF;
        int b = pixel.get(2) & 0xFF;
        assertTrue(g > 200, "Green should be high (cyan texture), got " + g);
        assertTrue(b > 200, "Blue should be high (cyan texture), got " + b);

        GL45.glDeleteFramebuffers(fbo);
        GL45.glDeleteTextures(colorTex);
        GL45.glBindFramebuffer(GL45.GL_FRAMEBUFFER, 0);
        device.destroyBuffer(handleBuf);
        device.destroyTexture(tex);
        device.destroyBuffer(vbo);
        device.destroyVertexInput(vertexInput);
        device.destroyPipeline(pipeline);
    }
}
