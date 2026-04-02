package dev.engine.graphics.opengl;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static dev.engine.graphics.opengl.GpuTestHarness.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstrates the GpuTestHarness — clean, minimal GPU rendering tests.
 */
class GpuTestHarnessTest {

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

    @Test void solidRedShader() {
        gpu.drawFullscreen(PASSTHROUGH_VS, """
                #version 450 core
                out vec4 fragColor;
                void main() { fragColor = vec4(1, 0, 0, 1); }
                """);
        var pixel = gpu.readCenterPixel();
        assertChannelHigh(pixel, 0, "Red");
        assertChannelLow(pixel, 1, "Green");
        assertChannelLow(pixel, 2, "Blue");
    }

    @Test void colorFromUbo() {
        var colorUbo = gpu.createUbo(0f, 1f, 0f, 0f); // green, padded

        gpu.drawFullscreen(PASSTHROUGH_VS, """
                #version 450 core
                layout(std140, binding = 1) uniform Color { vec3 color; };
                out vec4 fragColor;
                void main() { fragColor = vec4(color, 1); }
                """, rec -> rec.bindUniformBuffer(1, colorUbo));

        var pixel = gpu.readCenterPixel();
        assertChannelLow(pixel, 0, "Red");
        assertChannelHigh(pixel, 1, "Green");
        assertChannelLow(pixel, 2, "Blue");

        gpu.device().destroyBuffer(colorUbo);
    }

    @Test void colorFromSsbo() {
        var ssbo = gpu.createSsbo(0f, 0f, 1f); // blue

        gpu.drawFullscreen(PASSTHROUGH_VS, """
                #version 450 core
                layout(std430, binding = 0) buffer Data { vec3 color; };
                out vec4 fragColor;
                void main() { fragColor = vec4(color, 1); }
                """, rec -> rec.bindStorageBuffer(0, ssbo));

        var pixel = gpu.readCenterPixel();
        assertChannelLow(pixel, 0, "Red");
        assertChannelLow(pixel, 1, "Green");
        assertChannelHigh(pixel, 2, "Blue");

        gpu.device().destroyBuffer(ssbo);
    }

    @Test void changingUboChangesOutput() {
        var ubo = gpu.createUbo(1f, 0f, 0f, 0f); // red

        String fs = """
                #version 450 core
                layout(std140, binding = 1) uniform Mat { vec3 color; };
                out vec4 fragColor;
                void main() { fragColor = vec4(color, 1); }
                """;

        // Render red
        gpu.drawFullscreen(PASSTHROUGH_VS, fs, rec -> rec.bindUniformBuffer(1, ubo));
        assertChannelHigh(gpu.readCenterPixel(), 0, "Red");

        // Update to cyan
        try (var w = gpu.device().writeBuffer(ubo)) {
            w.segment().set(java.lang.foreign.ValueLayout.JAVA_FLOAT, 0, 0f);
            w.segment().set(java.lang.foreign.ValueLayout.JAVA_FLOAT, 4, 1f);
            w.segment().set(java.lang.foreign.ValueLayout.JAVA_FLOAT, 8, 1f);
        }

        gpu.drawFullscreen(PASSTHROUGH_VS, fs, rec -> rec.bindUniformBuffer(1, ubo));
        var pixel = gpu.readCenterPixel();
        assertChannelLow(pixel, 0, "Red");
        assertChannelHigh(pixel, 1, "Green");
        assertChannelHigh(pixel, 2, "Blue");

        gpu.device().destroyBuffer(ubo);
    }
}
