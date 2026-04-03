package dev.engine.graphics.opengl;

import dev.engine.windowing.glfw.GlfwWindowToolkit;

import dev.engine.core.handle.Handle;
import dev.engine.core.layout.StructLayout;
import dev.engine.core.math.Mat4;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.PipelineResource;
import dev.engine.graphics.VertexInputResource;
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

import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;

/**
 * Reusable test harness for GPU rendering tests.
 *
 * <p>Eliminates boilerplate: manages the GLFW window, GL device, offscreen FBO,
 * and provides helpers for vertex upload, shader compile, draw, and pixel readback.
 *
 * <p>Usage:
 * <pre>
 *   var harness = new GpuTestHarness(64, 64);
 *   harness.clear(0, 0, 0, 1);
 *   harness.drawFullscreen(vertexShader, fragmentShader, rec -> {
 *       rec.bindUniformBuffer(1, myUbo);
 *   });
 *   int[] pixel = harness.readPixel(32, 32);
 *   assertTrue(pixel[0] > 200); // red
 *   harness.close();
 * </pre>
 */
public class GpuTestHarness implements AutoCloseable {

    private final GlfwWindowToolkit toolkit;
    private final GlRenderDevice device;
    private final GlBindings gl;
    private final int width, height;
    private final int fbo, colorTex, depthTex;

    // Shared resources
    private final Handle<BufferResource> fullscreenVbo;
    private final Handle<VertexInputResource> posOnlyInput;
    private final Handle<BufferResource> identityMvpUbo;

    public GpuTestHarness(int width, int height) {
        this(width, height, createDefaultBindings());
    }

    public GpuTestHarness(int width, int height, GlBindings gl) {
        this.width = width;
        this.height = height;
        this.gl = gl;
        this.toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.OPENGL_HINTS);
        var window = toolkit.createWindow(new WindowDescriptor("GPU Test", 1, 1));
        this.device = new GlRenderDevice(window, gl);

        // Offscreen FBO
        fbo = gl.glCreateFramebuffers();
        colorTex = gl.glCreateTextures(GlBindings.GL_TEXTURE_2D);
        gl.glTextureStorage2D(colorTex, 1, GlBindings.GL_RGBA8, width, height);
        gl.glNamedFramebufferTexture(fbo, GlBindings.GL_COLOR_ATTACHMENT0, colorTex, 0);
        depthTex = gl.glCreateTextures(GlBindings.GL_TEXTURE_2D);
        gl.glTextureStorage2D(depthTex, 1, GlBindings.GL_DEPTH_COMPONENT24, width, height);
        gl.glNamedFramebufferTexture(fbo, GlBindings.GL_DEPTH_ATTACHMENT, depthTex, 0);
        gl.glBindFramebuffer(GlBindings.GL_FRAMEBUFFER, fbo);

        // Fullscreen triangle
        var layout = StructLayout.of(Pos.class);
        var verts = new Pos[]{new Pos(-1, -1, 0), new Pos(3, -1, 0), new Pos(-1, 3, 0)};
        long vbSize = (long) layout.size() * verts.length;
        fullscreenVbo = device.createBuffer(new BufferDescriptor(vbSize, BufferUsage.VERTEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(fullscreenVbo)) {
            for (int i = 0; i < verts.length; i++) layout.write(w.segment(), (long) layout.size() * i, verts[i]);
        }
        posOnlyInput = device.createVertexInput(VertexFormat.of(
                new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0)));

        // Identity MVP UBO at binding 0
        var matLayout = StructLayout.of(Mat4.class);
        identityMvpUbo = device.createBuffer(new BufferDescriptor(matLayout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC));
        try (var w = device.writeBuffer(identityMvpUbo)) { matLayout.write(w.segment(), 0, Mat4.IDENTITY); }
    }

    /**
     * Creates a default {@link GlBindings} using service-loader discovery.
     * Falls back to reflective instantiation of the LWJGL provider.
     */
    private static GlBindings createDefaultBindings() {
        try {
            var clazz = Class.forName("dev.engine.providers.lwjgl.graphics.opengl.LwjglGlBindings");
            return (GlBindings) clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No GlBindings provider found on classpath. "
                    + "Add the providers:lwjgl-gl module as a dependency.", e);
        }
    }

    record Pos(float x, float y, float z) {}

    public GlRenderDevice device() { return device; }
    public int width() { return width; }
    public int height() { return height; }

    /** Clears the framebuffer. */
    public void clear(float r, float g, float b, float a) {
        device.beginFrame();
        var rec = new CommandRecorder();
        rec.viewport(0, 0, width, height);
        rec.clear(r, g, b, a);
        device.submit(rec.finish());
        device.endFrame();
    }

    /**
     * Draws a fullscreen triangle with the given shaders.
     * Automatically binds identity MVP at binding 0.
     * The extraBindings callback can bind additional UBOs/SSBOs.
     */
    public void drawFullscreen(String vertexShader, String fragmentShader, java.util.function.Consumer<CommandRecorder> extraBindings) {
        var pipeline = device.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, vertexShader),
                new ShaderSource(ShaderStage.FRAGMENT, fragmentShader)));

        device.beginFrame();
        var rec = new CommandRecorder();
        rec.viewport(0, 0, width, height);
        rec.clear(0, 0, 0, 1);
        rec.bindPipeline(pipeline);
        rec.bindUniformBuffer(0, identityMvpUbo);
        if (extraBindings != null) extraBindings.accept(rec);
        rec.bindVertexBuffer(fullscreenVbo, posOnlyInput);
        rec.draw(3, 0);
        device.submit(rec.finish());
        device.endFrame();

        device.destroyPipeline(pipeline);
    }

    /** Draws a fullscreen triangle with no extra bindings. */
    public void drawFullscreen(String vertexShader, String fragmentShader) {
        drawFullscreen(vertexShader, fragmentShader, null);
    }

    /** Reads a single pixel at (x, y). Returns [R, G, B, A] as 0-255 values. */
    public int[] readPixel(int x, int y) {
        ByteBuffer pixel = ByteBuffer.allocateDirect(4);
        gl.glReadPixels(x, y, 1, 1, GlBindings.GL_RGBA, GlBindings.GL_UNSIGNED_BYTE, pixel);
        return new int[]{pixel.get(0) & 0xFF, pixel.get(1) & 0xFF, pixel.get(2) & 0xFF, pixel.get(3) & 0xFF};
    }

    /** Reads the center pixel. */
    public int[] readCenterPixel() {
        return readPixel(width / 2, height / 2);
    }

    /** Creates a UBO from raw float data. */
    public Handle<BufferResource> createUbo(float... data) {
        long size = (long) data.length * Float.BYTES;
        var ubo = device.createBuffer(new BufferDescriptor(Math.max(size, 16), BufferUsage.UNIFORM, AccessPattern.DYNAMIC));
        try (var w = device.writeBuffer(ubo)) {
            for (int i = 0; i < data.length; i++) {
                w.segment().set(ValueLayout.JAVA_FLOAT, (long) i * Float.BYTES, data[i]);
            }
        }
        return ubo;
    }

    /** Creates an SSBO from raw float data. */
    public Handle<BufferResource> createSsbo(float... data) {
        long size = (long) data.length * Float.BYTES;
        var ssbo = device.createBuffer(new BufferDescriptor(Math.max(size, 16), BufferUsage.STORAGE, AccessPattern.DYNAMIC));
        try (var w = device.writeBuffer(ssbo)) {
            for (int i = 0; i < data.length; i++) {
                w.segment().set(ValueLayout.JAVA_FLOAT, (long) i * Float.BYTES, data[i]);
            }
        }
        return ssbo;
    }

    /** Asserts a pixel channel is above a threshold. */
    public static void assertChannelHigh(int[] pixel, int channel, String name) {
        if (pixel[channel] <= 200) {
            throw new AssertionError(name + " channel should be > 200, got " + pixel[channel]
                    + " (RGBA: " + pixel[0] + "," + pixel[1] + "," + pixel[2] + "," + pixel[3] + ")");
        }
    }

    /** Asserts a pixel channel is below a threshold. */
    public static void assertChannelLow(int[] pixel, int channel, String name) {
        if (pixel[channel] >= 50) {
            throw new AssertionError(name + " channel should be < 50, got " + pixel[channel]
                    + " (RGBA: " + pixel[0] + "," + pixel[1] + "," + pixel[2] + "," + pixel[3] + ")");
        }
    }

    @Override
    public void close() {
        gl.glBindFramebuffer(GlBindings.GL_FRAMEBUFFER, 0);
        gl.glDeleteFramebuffers(fbo);
        gl.glDeleteTextures(colorTex);
        gl.glDeleteTextures(depthTex);
        device.destroyBuffer(fullscreenVbo);
        device.destroyVertexInput(posOnlyInput);
        device.destroyBuffer(identityMvpUbo);
        device.close();
        toolkit.close();
    }
}
