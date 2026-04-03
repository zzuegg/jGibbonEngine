package dev.engine.graphics.webgpu;

import dev.engine.core.handle.Handle;
import dev.engine.core.mesh.ComponentType;
import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.DeviceCapability;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.command.CommandList;
import dev.engine.graphics.command.RenderCommand;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.target.RenderTargetDescriptor;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.graphics.texture.TextureFormat;
import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.windowing.glfw.GlfwWindowToolkit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WgpuRenderDeviceTest {

    private static GlfwWindowToolkit toolkit;
    private WgpuRenderDevice device;

    @BeforeAll
    static void initGlfw() {
        assumeTrue(WgpuRenderDevice.isAvailable(), "jWebGPU not available");
        toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.NO_API_HINTS);
    }

    @AfterAll
    static void destroyGlfw() {
        if (toolkit != null) {
            toolkit.close();
            toolkit = null;
        }
    }

    @BeforeEach
    void setUp() {
        assumeTrue(WgpuRenderDevice.isAvailable(), "jWebGPU not available");
        var window = toolkit.createWindow(new WindowDescriptor("WGPU Test", 256, 256));
        device = new WgpuRenderDevice(window);
    }

    @AfterEach
    void tearDown() {
        if (device != null) device.close();
    }

    @Test
    void deviceCreatesSuccessfully() {
        assertNotNull(device);
    }

    @Test
    void createAndDestroyBuffer() {
        var descriptor = new BufferDescriptor(1024, BufferUsage.VERTEX, AccessPattern.STATIC);
        Handle<BufferResource> buffer = device.createBuffer(descriptor);

        assertNotNull(buffer);
        assertTrue(device.isValidBuffer(buffer));

        device.destroyBuffer(buffer);
        assertFalse(device.isValidBuffer(buffer));
    }

    @Test
    void queryCapabilityReturnsValues() {
        Integer maxTexSize = device.queryCapability(DeviceCapability.MAX_TEXTURE_SIZE);
        assertNotNull(maxTexSize);
        assertTrue(maxTexSize > 0);

        Integer maxFbWidth = device.queryCapability(DeviceCapability.MAX_FRAMEBUFFER_WIDTH);
        assertNotNull(maxFbWidth);
        assertTrue(maxFbWidth > 0);

        Integer maxFbHeight = device.queryCapability(DeviceCapability.MAX_FRAMEBUFFER_HEIGHT);
        assertNotNull(maxFbHeight);
        assertTrue(maxFbHeight > 0);

        assertEquals("WebGPU", device.queryCapability(DeviceCapability.BACKEND_NAME));
    }

    @Test
    void createAndDestroyTexture() {
        var desc = TextureDescriptor.rgba(64, 64);
        var texture = device.createTexture(desc);

        assertNotNull(texture);
        assertTrue(device.isValidTexture(texture));

        device.destroyTexture(texture);
        assertFalse(device.isValidTexture(texture));
    }

    @Test
    void createAndDestroyRenderTarget() {
        var desc = RenderTargetDescriptor.colorDepth(256, 256, TextureFormat.RGBA8, TextureFormat.DEPTH24_STENCIL8);
        var rt = device.createRenderTarget(desc);

        assertNotNull(rt);
        var colorTex = device.getRenderTargetColorTexture(rt, 0);
        assertNotNull(colorTex);
        assertTrue(device.isValidTexture(colorTex));

        device.destroyRenderTarget(rt);
    }

    @Test
    void createAndDestroyVertexInput() {
        var format = VertexFormat.of(
                new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0),
                new VertexAttribute(1, 2, ComponentType.FLOAT, false, 12)
        );
        var vi = device.createVertexInput(format);
        assertNotNull(vi);
        device.destroyVertexInput(vi);
    }

    @Test
    void createAndDestroySampler() {
        var sampler = device.createSampler(SamplerDescriptor.linear());
        assertNotNull(sampler);
        device.destroySampler(sampler);
    }

    @Test
    void writeBufferDoesNotCrash() {
        var descriptor = new BufferDescriptor(256, BufferUsage.UNIFORM, AccessPattern.DYNAMIC);
        var buffer = device.createBuffer(descriptor);

        try (var writer = device.writeBuffer(buffer)) {
            var segment = writer.segment();
            segment.set(java.lang.foreign.ValueLayout.JAVA_FLOAT, 0, 1.0f);
            segment.set(java.lang.foreign.ValueLayout.JAVA_FLOAT, 4, 2.0f);
        }

        device.destroyBuffer(buffer);
    }

    @Test
    void clearRenderTargetDoesNotCrash() {
        int w = 64, h = 64;
        var rt = device.createRenderTarget(
                RenderTargetDescriptor.colorDepth(w, h, TextureFormat.RGBA8, TextureFormat.DEPTH24_STENCIL8));

        device.beginFrame();
        device.submit(new CommandList(List.of(
                new RenderCommand.BindRenderTarget(rt),
                new RenderCommand.Clear(1.0f, 0.0f, 0.0f, 1.0f)
        )));
        device.endFrame();

        device.destroyRenderTarget(rt);
    }

    @Test
    void clearAndReadbackProducesExpectedColor() {
        int w = 64, h = 64;
        var rt = device.createRenderTarget(
                RenderTargetDescriptor.colorDepth(w, h, TextureFormat.RGBA8, TextureFormat.DEPTH24_STENCIL8));

        device.beginFrame();
        device.submit(new CommandList(List.of(
                new RenderCommand.BindRenderTarget(rt),
                new RenderCommand.Clear(1.0f, 0.0f, 0.0f, 1.0f)
        )));
        device.endFrame();

        byte[] pixels = device.readFramebuffer(w, h);

        assertNotNull(pixels, "readFramebuffer should return data");
        assertEquals(w * h * 4, pixels.length);

        // Check center pixel is red (RGBA = 255, 0, 0, 255)
        int cx = w / 2, cy = h / 2;
        int idx = (cy * w + cx) * 4;
        assertEquals((byte) 255, pixels[idx],     "Red channel should be 255");
        assertEquals((byte) 0,   pixels[idx + 1], "Green channel should be 0");
        assertEquals((byte) 0,   pixels[idx + 2], "Blue channel should be 0");
        assertEquals((byte) 255, pixels[idx + 3], "Alpha channel should be 255");

        device.destroyRenderTarget(rt);
    }

    @Test
    void streamingBufferWorks() {
        var streaming = device.createStreamingBuffer(256, 3, BufferUsage.UNIFORM);
        assertNotNull(streaming);
        assertNotNull(streaming.handle());
        assertEquals(256, streaming.frameSize());
        assertEquals(768, streaming.size());

        var segment = streaming.beginWrite();
        segment.set(java.lang.foreign.ValueLayout.JAVA_FLOAT, 0, 42.0f);
        streaming.endWrite();

        streaming.advance();
        streaming.close();
    }

    @Test
    void createShaderModuleDoesNotCrash() {
        var wgslSource = """
            @vertex
            fn vertexMain(@builtin(vertex_index) i : u32) -> @builtin(position) vec4f {
                var pos = array<vec2f, 3>(vec2f(-0.5, -0.5), vec2f(0.5, -0.5), vec2f(0.0, 0.5));
                return vec4f(pos[i], 0.0, 1.0);
            }
            @fragment
            fn fragmentMain() -> @location(0) vec4f {
                return vec4f(1.0, 0.0, 0.0, 1.0);
            }
            """;
        // Create shader module via WgpuRenderDevice's pipeline creation
        var pipeline = device.createPipeline(
                dev.engine.graphics.pipeline.PipelineDescriptor.of(
                        new dev.engine.graphics.pipeline.ShaderSource(
                                dev.engine.graphics.pipeline.ShaderStage.VERTEX, wgslSource, "vertexMain"),
                        new dev.engine.graphics.pipeline.ShaderSource(
                                dev.engine.graphics.pipeline.ShaderStage.FRAGMENT, wgslSource, "fragmentMain")
                ));
        assertNotNull(pipeline);
        assertTrue(device.isValidPipeline(pipeline));
        device.destroyPipeline(pipeline);
    }

    @Test
    void uploadTextureDoesNotCrash() {
        var desc = TextureDescriptor.rgba(64, 64);
        var texture = device.createTexture(desc);

        ByteBuffer pixels = ByteBuffer.allocateDirect(64 * 64 * 4);
        for (int i = 0; i < 64 * 64 * 4; i++) {
            pixels.put((byte) (i % 256));
        }
        pixels.flip();

        device.uploadTexture(texture, pixels);
        device.destroyTexture(texture);
    }
}
