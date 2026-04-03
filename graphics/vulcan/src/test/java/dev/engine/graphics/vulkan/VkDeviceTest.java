package dev.engine.graphics.vulkan;

import dev.engine.bindings.slang.SlangCompilerNative;
import dev.engine.bindings.slang.SlangNative;
import dev.engine.core.handle.Handle;
import dev.engine.graphics.DeviceCapability;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.command.CommandRecorder;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderBinary;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.windowing.glfw.GlfwWindowToolkit;
import dev.engine.graphics.window.WindowDescriptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class VkDeviceTest {

    static GlfwWindowToolkit toolkit;
    static VkRenderDevice device;

    @BeforeAll
    static void setUp() {
        toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.NO_API_HINTS);
        var window = toolkit.createWindow(new WindowDescriptor("Vulkan Test", 1, 1));
        device = new VkRenderDevice(
                org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions(),
                instance -> GlfwWindowToolkit.createVulkanSurface(instance, window.nativeHandle()),
                window.width(), window.height());
    }

    @AfterAll
    static void tearDown() {
        if (device != null) device.close();
        if (toolkit != null) toolkit.close();
    }

    @Nested
    class DeviceCreation {
        @Test void deviceCreatesSuccessfully() {
            assertNotNull(device);
        }

        @Test void maxTextureSizeIsPositive() {
            int maxSize = device.queryCapability(DeviceCapability.MAX_TEXTURE_SIZE);
            assertTrue(maxSize >= 1024, "Got " + maxSize);
        }
    }

    @Nested
    class FrameLifecycle {
        @Test void beginEndFrameDoesNotThrow() {
            assertDoesNotThrow(() -> {
                device.beginFrame();
                device.endFrame();
            });
        }

        @Test void multipleFramesDoNotThrow() {
            assertDoesNotThrow(() -> {
                for (int i = 0; i < 5; i++) {
                    device.beginFrame();
                    device.endFrame();
                }
            });
        }
    }

    @Nested
    class PipelineCreation {
        private static final String SIMPLE_SHADER = """
                struct VertexOutput { float4 position : SV_Position; };
                [shader("vertex")]
                VertexOutput vertexMain(float3 pos : POSITION) {
                    VertexOutput output;
                    output.position = float4(pos, 1.0);
                    return output;
                }
                [shader("fragment")]
                float4 fragmentMain(VertexOutput input) : SV_Target {
                    return float4(1.0, 0.0, 0.0, 1.0);
                }
                """;

        @Test void createPipelineFromSpirv() {
            assumeTrue(SlangCompilerNative.isAvailable(), "Slang native not available");
            var compiler = SlangCompilerNative.create();
            try (var result = compiler.compile(SIMPLE_SHADER,
                    List.of(
                            new SlangCompilerNative.EntryPointDesc("vertexMain", SlangNative.SLANG_STAGE_VERTEX),
                            new SlangCompilerNative.EntryPointDesc("fragmentMain", SlangNative.SLANG_STAGE_FRAGMENT)),
                    SlangNative.SLANG_SPIRV)) {

                var pipeline = device.createPipeline(PipelineDescriptor.ofSpirv(
                        new ShaderBinary(ShaderStage.VERTEX, result.codeBytes(0)),
                        new ShaderBinary(ShaderStage.FRAGMENT, result.codeBytes(1))));

                assertNotEquals(Handle.invalid(), pipeline);
                assertTrue(device.isValidPipeline(pipeline));
                device.destroyPipeline(pipeline);
            }
            compiler.close();
        }
    }

    @Nested
    class CommandSubmission {
        @Test void submitEmptyCommandListDoesNotThrow() {
            device.beginFrame();
            var rec = new CommandRecorder();
            rec.viewport(0, 0, 800, 600);
            device.submit(rec.finish());
            device.endFrame();
        }

        @Test void submitDrawCommandDoesNotThrow() {
            assumeTrue(SlangCompilerNative.isAvailable(), "Slang native not available");
            var compiler = SlangCompilerNative.create();

            var shader = """
                struct VertexOutput { float4 position : SV_Position; };
                [shader("vertex")]
                VertexOutput vertexMain(float3 pos : POSITION) {
                    VertexOutput output;
                    output.position = float4(pos, 1.0);
                    return output;
                }
                [shader("fragment")]
                float4 fragmentMain(VertexOutput input) : SV_Target {
                    return float4(1.0, 0.0, 0.0, 1.0);
                }
                """;

            try (var result = compiler.compile(shader,
                    List.of(
                            new SlangCompilerNative.EntryPointDesc("vertexMain", SlangNative.SLANG_STAGE_VERTEX),
                            new SlangCompilerNative.EntryPointDesc("fragmentMain", SlangNative.SLANG_STAGE_FRAGMENT)),
                    SlangNative.SLANG_SPIRV)) {

                var pipeline = device.createPipeline(PipelineDescriptor.ofSpirv(
                        new ShaderBinary(ShaderStage.VERTEX, result.codeBytes(0)),
                        new ShaderBinary(ShaderStage.FRAGMENT, result.codeBytes(1))));

                // Create a vertex buffer with a triangle
                var vbDesc = new BufferDescriptor(3 * 3 * 4, BufferUsage.VERTEX, AccessPattern.STATIC);
                var vb = device.createBuffer(vbDesc);
                try (var w = device.writeBuffer(vb)) {
                    var seg = w.segment();
                    float[] verts = {0f, 0.5f, 0f, -0.5f, -0.5f, 0f, 0.5f, -0.5f, 0f};
                    for (int i = 0; i < verts.length; i++) {
                        seg.set(java.lang.foreign.ValueLayout.JAVA_FLOAT, i * 4L, verts[i]);
                    }
                }

                device.beginFrame();

                var rec = new CommandRecorder();
                rec.viewport(0, 0, 800, 600);
                rec.bindPipeline(pipeline);
                rec.bindVertexBuffer(vb, null);
                rec.draw(3, 0);
                device.submit(rec.finish());

                device.endFrame();

                device.destroyBuffer(vb);
                device.destroyPipeline(pipeline);
            }
            compiler.close();
        }
    }

    @Nested
    class BufferOperations {
        @Test void createBufferReturnsValidHandle() {
            var desc = new BufferDescriptor(1024, BufferUsage.VERTEX, AccessPattern.STATIC);
            var handle = device.createBuffer(desc);
            assertNotEquals(Handle.invalid(), handle);
            assertTrue(device.isValidBuffer(handle));
            device.destroyBuffer(handle);
        }

        @Test void destroyBufferInvalidatesHandle() {
            var desc = new BufferDescriptor(512, BufferUsage.INDEX, AccessPattern.DYNAMIC);
            var handle = device.createBuffer(desc);
            device.destroyBuffer(handle);
            assertFalse(device.isValidBuffer(handle));
        }
    }
}
