package dev.engine.graphics.opengl;

import dev.engine.graphics.command.CommandRecorder;
import dev.engine.graphics.pipeline.ComputePipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.graphics.renderstate.BarrierScope;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static dev.engine.graphics.opengl.GpuTestHarness.*;

class GlComputeDispatchTest {

    static final String PASSTHROUGH_VS = """
            #version 450 core
            layout(location = 0) in vec3 position;
            layout(row_major, std140, binding = 0) uniform Matrices { mat4 mvp; };
            void main() { gl_Position = mvp * vec4(position, 1.0); }
            """;

    static GpuTestHarness gpu;

    @BeforeAll
    static void setUp() { gpu = new GpuTestHarness(64, 64); }

    @AfterAll
    static void tearDown() { gpu.close(); }

    @Test void computeWritesSsboThenFragmentReads() {
        var device = gpu.device();

        // Create SSBO with zeros
        var ssbo = gpu.createSsbo(0f, 0f, 0f, 0f);

        // Compute shader writes (1.0, 0.0, 0.0, 1.0) into the SSBO
        var computeSource = """
                #version 450 core
                layout(std430, binding = 0) buffer ColorBuf {
                    vec4 color;
                };
                layout(local_size_x = 1) in;
                void main() {
                    color = vec4(1.0, 0.0, 0.0, 1.0);
                }
                """;

        var computePipeline = device.createComputePipeline(
                ComputePipelineDescriptor.of(new ShaderSource(ShaderStage.COMPUTE, computeSource)));

        // Dispatch compute
        device.beginFrame();
        var rec = new CommandRecorder();
        rec.bindComputePipeline(computePipeline);
        rec.bindStorageBuffer(0, ssbo);
        rec.dispatch(1, 1, 1);
        rec.memoryBarrier(BarrierScope.STORAGE_BUFFER);
        device.submit(rec.finish());
        device.endFrame();

        // Fragment shader reads SSBO and outputs as color
        var fragShader = """
                #version 450 core
                layout(std430, binding = 0) buffer ColorBuf {
                    vec4 color;
                };
                out vec4 fragColor;
                void main() {
                    fragColor = color;
                }
                """;

        gpu.drawFullscreen(PASSTHROUGH_VS, fragShader, rec2 -> {
            rec2.bindStorageBuffer(0, ssbo);
        });

        var pixel = gpu.readCenterPixel();
        assertChannelHigh(pixel, 0, "Red");   // compute wrote red
        assertChannelLow(pixel, 1, "Green");
        assertChannelLow(pixel, 2, "Blue");

        device.destroyPipeline(computePipeline);
        device.destroyBuffer(ssbo);
    }

    @Test void computeWritesMultipleValues() {
        var device = gpu.device();

        // SSBO with 4 floats (will become an RGBA color)
        var ssbo = gpu.createSsbo(0f, 0f, 0f, 0f);

        // Compute shader writes cyan (0, 1, 1, 1)
        var computeSource = """
                #version 450 core
                layout(std430, binding = 0) buffer ColorBuf {
                    float r, g, b, a;
                };
                layout(local_size_x = 1) in;
                void main() {
                    r = 0.0;
                    g = 1.0;
                    b = 1.0;
                    a = 1.0;
                }
                """;

        var computePipeline = device.createComputePipeline(
                ComputePipelineDescriptor.of(new ShaderSource(ShaderStage.COMPUTE, computeSource)));

        device.beginFrame();
        var rec = new CommandRecorder();
        rec.bindComputePipeline(computePipeline);
        rec.bindStorageBuffer(0, ssbo);
        rec.dispatch(1, 1, 1);
        rec.memoryBarrier(BarrierScope.STORAGE_BUFFER);
        device.submit(rec.finish());
        device.endFrame();

        // Fragment reads the 4 floats as color
        var fragShader = """
                #version 450 core
                layout(std430, binding = 0) buffer ColorBuf {
                    float r, g, b, a;
                };
                out vec4 fragColor;
                void main() {
                    fragColor = vec4(r, g, b, a);
                }
                """;

        gpu.drawFullscreen(PASSTHROUGH_VS, fragShader, rec2 -> {
            rec2.bindStorageBuffer(0, ssbo);
        });

        var pixel = gpu.readCenterPixel();
        assertChannelLow(pixel, 0, "Red");
        assertChannelHigh(pixel, 1, "Green");  // cyan
        assertChannelHigh(pixel, 2, "Blue");

        device.destroyPipeline(computePipeline);
        device.destroyBuffer(ssbo);
    }
}
