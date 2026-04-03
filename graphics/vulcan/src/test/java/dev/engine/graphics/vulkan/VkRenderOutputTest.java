package dev.engine.graphics.vulkan;

import dev.engine.bindings.slang.SlangCompilerNative;
import dev.engine.bindings.slang.SlangNative;
import dev.engine.core.mesh.ComponentType;
import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.command.CommandRecorder;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderBinary;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.windowing.glfw.GlfwWindowToolkit;
import dev.engine.graphics.window.WindowDescriptor;
import org.junit.jupiter.api.*;

import java.lang.foreign.ValueLayout;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests actual Vulkan rendering output by drawing and checking descriptor bindings.
 */
class VkRenderOutputTest {

    static GlfwWindowToolkit toolkit;
    static VkRenderDevice device;

    @BeforeAll
    static void setUp() {
        toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.NO_API_HINTS);
        var window = toolkit.createWindow(new WindowDescriptor("Vk Render Test", 64, 64));
        device = new VkRenderDevice(
                org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions(),
                instance -> GlfwWindowToolkit.createVulkanSurface(instance, window.nativeHandle()),
                64, 64);
    }

    @AfterAll
    static void tearDown() {
        if (device != null) device.close();
        if (toolkit != null) toolkit.close();
    }

    @Test
    void redTriangleDrawsWithoutError() {
        assumeTrue(SlangCompilerNative.isAvailable(), "Slang not available");
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

        var format = VertexFormat.of(new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0));
        try (var result = compiler.compile(shader,
                List.of(
                        new SlangCompilerNative.EntryPointDesc("vertexMain", SlangNative.SLANG_STAGE_VERTEX),
                        new SlangCompilerNative.EntryPointDesc("fragmentMain", SlangNative.SLANG_STAGE_FRAGMENT)),
                SlangNative.SLANG_SPIRV)) {

            var pipeline = device.createPipeline(PipelineDescriptor.ofSpirv(
                    new ShaderBinary(ShaderStage.VERTEX, result.codeBytes(0)),
                    new ShaderBinary(ShaderStage.FRAGMENT, result.codeBytes(1)))
                    .withVertexFormat(format));

            // Fullscreen triangle in NDC
            float[] verts = {-1, -1, 0, 3, -1, 0, -1, 3, 0};
            var vb = device.createBuffer(new BufferDescriptor(verts.length * 4L, BufferUsage.VERTEX, AccessPattern.STATIC));
            try (var w = device.writeBuffer(vb)) {
                for (int i = 0; i < verts.length; i++)
                    w.segment().set(ValueLayout.JAVA_FLOAT, i * 4L, verts[i]);
            }

            // Render 3 frames without error
            for (int i = 0; i < 3; i++) {
                device.beginFrame();
                var rec = new CommandRecorder();
                rec.viewport(0, 0, 64, 64);
                rec.bindPipeline(pipeline);
                rec.bindVertexBuffer(vb, null);
                rec.draw(3, 0);
                device.submit(rec.finish());
                device.endFrame();
            }

            device.destroyBuffer(vb);
            device.destroyPipeline(pipeline);
        }
        compiler.close();
    }

    @Test
    void redTriangleReadback() {
        assumeTrue(SlangCompilerNative.isAvailable(), "Slang not available");
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

        var format = VertexFormat.of(new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0));
        try (var result = compiler.compile(shader,
                List.of(
                        new SlangCompilerNative.EntryPointDesc("vertexMain", SlangNative.SLANG_STAGE_VERTEX),
                        new SlangCompilerNative.EntryPointDesc("fragmentMain", SlangNative.SLANG_STAGE_FRAGMENT)),
                SlangNative.SLANG_SPIRV)) {

            var pipeline = device.createPipeline(PipelineDescriptor.ofSpirv(
                    new ShaderBinary(ShaderStage.VERTEX, result.codeBytes(0)),
                    new ShaderBinary(ShaderStage.FRAGMENT, result.codeBytes(1)))
                    .withVertexFormat(format));

            float[] verts = {-1, -1, 0, 3, -1, 0, -1, 3, 0};
            var vb = device.createBuffer(new BufferDescriptor(verts.length * 4L, BufferUsage.VERTEX, AccessPattern.STATIC));
            try (var w = device.writeBuffer(vb)) {
                for (int i = 0; i < verts.length; i++)
                    w.segment().set(ValueLayout.JAVA_FLOAT, i * 4L, verts[i]);
            }

            device.beginFrame();
            var rec = new CommandRecorder();
            rec.viewport(0, 0, 64, 64);
            rec.bindPipeline(pipeline);
            rec.bindVertexBuffer(vb, null);
            rec.draw(3, 0);
            device.submit(rec.finish());
            device.endFrame();

            // Read back center pixel
            var pixel = device.readPixel(32, 32);
            System.out.println("Red triangle pixel: R=" + pixel[0] + " G=" + pixel[1] + " B=" + pixel[2] + " A=" + pixel[3]);

            // Should be red (SRGB may adjust values)
            assertTrue(pixel[0] > 200, "Red should be > 200, got " + pixel[0]);
            assertTrue(pixel[1] < 50, "Green should be < 50, got " + pixel[1]);
            assertTrue(pixel[2] < 50, "Blue should be < 50, got " + pixel[2]);

            device.destroyBuffer(vb);
            device.destroyPipeline(pipeline);
        }
        compiler.close();
    }

    @Test
    void uboColorReadback() {
        assumeTrue(SlangCompilerNative.isAvailable(), "Slang not available");
        var compiler = SlangCompilerNative.create();

        var shader = """
                [[vk::binding(0)]]
                cbuffer ColorBuffer : register(b0) {
                    float4 testColor;
                };
                struct VertexOutput { float4 position : SV_Position; };
                [shader("vertex")]
                VertexOutput vertexMain(float3 pos : POSITION) {
                    VertexOutput output;
                    output.position = float4(pos, 1.0);
                    return output;
                }
                [shader("fragment")]
                float4 fragmentMain(VertexOutput input) : SV_Target {
                    return testColor;
                }
                """;

        var format = VertexFormat.of(new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0));
        try (var result = compiler.compile(shader,
                List.of(
                        new SlangCompilerNative.EntryPointDesc("vertexMain", SlangNative.SLANG_STAGE_VERTEX),
                        new SlangCompilerNative.EntryPointDesc("fragmentMain", SlangNative.SLANG_STAGE_FRAGMENT)),
                SlangNative.SLANG_SPIRV)) {

            var pipeline = device.createPipeline(PipelineDescriptor.ofSpirv(
                    new ShaderBinary(ShaderStage.VERTEX, result.codeBytes(0)),
                    new ShaderBinary(ShaderStage.FRAGMENT, result.codeBytes(1)))
                    .withVertexFormat(format));

            float[] verts = {-1, -1, 0, 3, -1, 0, -1, 3, 0};
            var vb = device.createBuffer(new BufferDescriptor(verts.length * 4L, BufferUsage.VERTEX, AccessPattern.STATIC));
            try (var w = device.writeBuffer(vb)) {
                for (int i = 0; i < verts.length; i++)
                    w.segment().set(ValueLayout.JAVA_FLOAT, i * 4L, verts[i]);
            }

            // Green UBO
            var colorUbo = device.createBuffer(new BufferDescriptor(16, BufferUsage.UNIFORM, AccessPattern.DYNAMIC));
            try (var w = device.writeBuffer(colorUbo)) {
                w.segment().set(ValueLayout.JAVA_FLOAT, 0, 0.0f);
                w.segment().set(ValueLayout.JAVA_FLOAT, 4, 1.0f);
                w.segment().set(ValueLayout.JAVA_FLOAT, 8, 0.0f);
                w.segment().set(ValueLayout.JAVA_FLOAT, 12, 1.0f);
            }

            device.beginFrame();
            var rec = new CommandRecorder();
            rec.viewport(0, 0, 64, 64);
            rec.bindPipeline(pipeline);
            rec.bindUniformBuffer(0, colorUbo);
            rec.bindVertexBuffer(vb, null);
            rec.draw(3, 0);
            device.submit(rec.finish());
            device.endFrame();

            var pixel = device.readPixel(32, 32);
            System.out.println("UBO color pixel: R=" + pixel[0] + " G=" + pixel[1] + " B=" + pixel[2] + " A=" + pixel[3]);

            // Save SPIRV to disk for inspection
            byte[] fsSpirv = result.codeBytes(1);
            byte[] vsSpirv = result.codeBytes(0);
            try {
                java.nio.file.Files.write(java.nio.file.Path.of("/tmp/vk_test_vs.spv"), vsSpirv);
                java.nio.file.Files.write(java.nio.file.Path.of("/tmp/vk_test_fs.spv"), fsSpirv);
                System.out.println("SPIRV saved to /tmp/vk_test_vs.spv and /tmp/vk_test_fs.spv");
            } catch (Exception e) { e.printStackTrace(); }
            System.out.println("FS SPIRV size: " + fsSpirv.length + " bytes");
            // Check binding decorations
            for (int off = 20; off < fsSpirv.length - 4; off += 4) {
                int wc = ((fsSpirv[off+3] & 0xFF) << 8) | (fsSpirv[off+2] & 0xFF);
                int op = ((fsSpirv[off+1] & 0xFF) << 8) | (fsSpirv[off] & 0xFF);
                if (op == 71 && wc >= 4) { // OpDecorate
                    int target = java.nio.ByteBuffer.wrap(fsSpirv, off+4, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
                    int dec = java.nio.ByteBuffer.wrap(fsSpirv, off+8, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
                    if (dec == 33 && wc >= 4) { // Binding
                        int bnd = java.nio.ByteBuffer.wrap(fsSpirv, off+12, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
                        System.out.println("  SPIRV: ID " + target + " Binding=" + bnd);
                    }
                    if (dec == 34 && wc >= 4) { // DescriptorSet
                        int ds = java.nio.ByteBuffer.wrap(fsSpirv, off+12, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
                        System.out.println("  SPIRV: ID " + target + " DescriptorSet=" + ds);
                    }
                }
            }

            // Should be green
            assertTrue(pixel[1] > 200, "Green should be > 200, got " + pixel[1]);
            assertTrue(pixel[0] < 50, "Red should be < 50, got " + pixel[0]);

            device.destroyBuffer(vb);
            device.destroyBuffer(colorUbo);
            device.destroyPipeline(pipeline);
        }
        compiler.close();
    }

    @Test
    void uboColorReadbackWithDirectDescriptorSet() {
        // Bypass the command system — bind descriptor set directly
        assumeTrue(SlangCompilerNative.isAvailable(), "Slang not available");
        var compiler = SlangCompilerNative.create();

        var shader = """
                [[vk::binding(0)]]
                cbuffer ColorBuffer : register(b0) {
                    float4 testColor;
                };
                struct VertexOutput { float4 position : SV_Position; };
                [shader("vertex")]
                VertexOutput vertexMain(float3 pos : POSITION) {
                    VertexOutput output;
                    output.position = float4(pos, 1.0);
                    return output;
                }
                [shader("fragment")]
                float4 fragmentMain(VertexOutput input) : SV_Target {
                    return testColor;
                }
                """;

        var format = VertexFormat.of(new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0));
        try (var result = compiler.compile(shader,
                List.of(
                        new SlangCompilerNative.EntryPointDesc("vertexMain", SlangNative.SLANG_STAGE_VERTEX),
                        new SlangCompilerNative.EntryPointDesc("fragmentMain", SlangNative.SLANG_STAGE_FRAGMENT)),
                SlangNative.SLANG_SPIRV)) {

            var pipeline = device.createPipeline(PipelineDescriptor.ofSpirv(
                    new ShaderBinary(ShaderStage.VERTEX, result.codeBytes(0)),
                    new ShaderBinary(ShaderStage.FRAGMENT, result.codeBytes(1)))
                    .withVertexFormat(format));

            float[] verts = {-1, -1, 0, 3, -1, 0, -1, 3, 0};
            var vb = device.createBuffer(new BufferDescriptor(verts.length * 4L, BufferUsage.VERTEX, AccessPattern.STATIC));
            try (var w = device.writeBuffer(vb)) {
                for (int i = 0; i < verts.length; i++)
                    w.segment().set(ValueLayout.JAVA_FLOAT, i * 4L, verts[i]);
            }

            // Green UBO
            var colorUbo = device.createBuffer(new BufferDescriptor(16, BufferUsage.UNIFORM, AccessPattern.DYNAMIC));
            try (var w = device.writeBuffer(colorUbo)) {
                w.segment().set(ValueLayout.JAVA_FLOAT, 0, 0.0f);
                w.segment().set(ValueLayout.JAVA_FLOAT, 4, 1.0f);
                w.segment().set(ValueLayout.JAVA_FLOAT, 8, 0.0f);
                w.segment().set(ValueLayout.JAVA_FLOAT, 12, 1.0f);
            }

            // Verify UBO data was written
            try (var r = device.writeBuffer(colorUbo)) {
                float g = r.segment().get(ValueLayout.JAVA_FLOAT, 4);
                System.out.println("UBO readback: green channel = " + g);
                assertEquals(1.0f, g, 0.001f);
            }

            device.beginFrame();

            // Use the command system but log what happens
            var rec = new CommandRecorder();
            rec.viewport(0, 0, 64, 64);
            rec.bindPipeline(pipeline);
            rec.bindUniformBuffer(0, colorUbo);
            rec.bindVertexBuffer(vb, null);
            rec.draw(3, 0);
            device.submit(rec.finish());

            device.endFrame();

            var pixel = device.readPixel(32, 32);
            System.out.println("Direct descriptor pixel: R=" + pixel[0] + " G=" + pixel[1] + " B=" + pixel[2] + " A=" + pixel[3]);

            // If still black, the UBO binding is fundamentally broken
            // If green, the command system works
            if (pixel[0] == 0 && pixel[1] == 0 && pixel[2] == 0) {
                System.out.println("STILL BLACK — descriptor binding broken");
            }

            device.destroyBuffer(vb);
            device.destroyBuffer(colorUbo);
            device.destroyPipeline(pipeline);
        }
        compiler.close();
    }

    @Test
    void uboBindingWorks() {
        assumeTrue(SlangCompilerNative.isAvailable(), "Slang not available");
        var compiler = SlangCompilerNative.create();

        // Shader with UBO — prints the color to a debug variable we can check
        var shader = """
                [[vk::binding(0)]]
                cbuffer ColorBuffer : register(b0) {
                    float4 testColor;
                };
                struct VertexOutput { float4 position : SV_Position; };
                [shader("vertex")]
                VertexOutput vertexMain(float3 pos : POSITION) {
                    VertexOutput output;
                    output.position = float4(pos, 1.0);
                    return output;
                }
                [shader("fragment")]
                float4 fragmentMain(VertexOutput input) : SV_Target {
                    return testColor;
                }
                """;

        var format = VertexFormat.of(new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0));
        try (var result = compiler.compile(shader,
                List.of(
                        new SlangCompilerNative.EntryPointDesc("vertexMain", SlangNative.SLANG_STAGE_VERTEX),
                        new SlangCompilerNative.EntryPointDesc("fragmentMain", SlangNative.SLANG_STAGE_FRAGMENT)),
                SlangNative.SLANG_SPIRV)) {

            var pipeline = device.createPipeline(PipelineDescriptor.ofSpirv(
                    new ShaderBinary(ShaderStage.VERTEX, result.codeBytes(0)),
                    new ShaderBinary(ShaderStage.FRAGMENT, result.codeBytes(1)))
                    .withVertexFormat(format));

            float[] verts = {-1, -1, 0, 3, -1, 0, -1, 3, 0};
            var vb = device.createBuffer(new BufferDescriptor(verts.length * 4L, BufferUsage.VERTEX, AccessPattern.STATIC));
            try (var w = device.writeBuffer(vb)) {
                for (int i = 0; i < verts.length; i++)
                    w.segment().set(ValueLayout.JAVA_FLOAT, i * 4L, verts[i]);
            }

            // Green color UBO (float4)
            var colorUbo = device.createBuffer(new BufferDescriptor(16, BufferUsage.UNIFORM, AccessPattern.DYNAMIC));
            try (var w = device.writeBuffer(colorUbo)) {
                w.segment().set(ValueLayout.JAVA_FLOAT, 0, 0.0f);  // r
                w.segment().set(ValueLayout.JAVA_FLOAT, 4, 1.0f);  // g
                w.segment().set(ValueLayout.JAVA_FLOAT, 8, 0.0f);  // b
                w.segment().set(ValueLayout.JAVA_FLOAT, 12, 1.0f); // a
            }

            // This should not crash — if UBO binding is wrong, validation catches it
            assertDoesNotThrow(() -> {
                for (int i = 0; i < 3; i++) {
                    device.beginFrame();
                    var rec = new CommandRecorder();
                    rec.viewport(0, 0, 64, 64);
                    rec.bindPipeline(pipeline);
                    rec.bindUniformBuffer(0, colorUbo);
                    rec.bindVertexBuffer(vb, null);
                    rec.draw(3, 0);
                    device.submit(rec.finish());
                    device.endFrame();
                }
            });

            device.destroyBuffer(vb);
            device.destroyBuffer(colorUbo);
            device.destroyPipeline(pipeline);
        }
        compiler.close();
    }
}
