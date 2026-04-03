package dev.engine.graphics.opengl;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static dev.engine.graphics.opengl.GpuTestHarness.*;

class GlPushConstantsTest {

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

    @Test void pushConstantDrivesGreenColor() {
        // Fragment shader reads from push constant UBO at binding 15 (OpenGL emulation slot)
        var fragShader = """
                #version 450 core
                layout(std140, binding = 15) uniform PushConstants {
                    vec4 color;
                };
                out vec4 fragColor;
                void main() {
                    fragColor = color;
                }
                """;

        // Push green color via push constants
        var pushData = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        pushData.putFloat(0.0f).putFloat(1.0f).putFloat(0.0f).putFloat(1.0f);
        pushData.flip();

        gpu.drawFullscreen(PASSTHROUGH_VS, fragShader, rec -> {
            rec.pushConstants(pushData);
        });

        var pixel = gpu.readCenterPixel();
        assertChannelLow(pixel, 0, "Red");
        assertChannelHigh(pixel, 1, "Green"); // push constant = green
        assertChannelLow(pixel, 2, "Blue");
    }

    @Test void pushConstantDrivesRedColor() {
        var fragShader = """
                #version 450 core
                layout(std140, binding = 15) uniform PushConstants {
                    vec4 color;
                };
                out vec4 fragColor;
                void main() {
                    fragColor = color;
                }
                """;

        // Push red color
        var pushData = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        pushData.putFloat(1.0f).putFloat(0.0f).putFloat(0.0f).putFloat(1.0f);
        pushData.flip();

        gpu.drawFullscreen(PASSTHROUGH_VS, fragShader, rec -> {
            rec.pushConstants(pushData);
        });

        var pixel = gpu.readCenterPixel();
        assertChannelHigh(pixel, 0, "Red");   // push constant = red
        assertChannelLow(pixel, 1, "Green");
        assertChannelLow(pixel, 2, "Blue");
    }

    @Test void pushConstantChangeBetweenDraws() {
        var fragShader = """
                #version 450 core
                layout(std140, binding = 15) uniform PushConstants {
                    vec4 color;
                };
                out vec4 fragColor;
                void main() {
                    fragColor = color;
                }
                """;

        // First draw: blue
        var blueData = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        blueData.putFloat(0.0f).putFloat(0.0f).putFloat(1.0f).putFloat(1.0f);
        blueData.flip();

        gpu.drawFullscreen(PASSTHROUGH_VS, fragShader, rec -> {
            rec.pushConstants(blueData);
        });

        var pixel = gpu.readCenterPixel();
        assertChannelLow(pixel, 0, "Red");
        assertChannelLow(pixel, 1, "Green");
        assertChannelHigh(pixel, 2, "Blue");

        // Second draw: yellow
        var yellowData = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        yellowData.putFloat(1.0f).putFloat(1.0f).putFloat(0.0f).putFloat(1.0f);
        yellowData.flip();

        gpu.drawFullscreen(PASSTHROUGH_VS, fragShader, rec -> {
            rec.pushConstants(yellowData);
        });

        var pixel2 = gpu.readCenterPixel();
        assertChannelHigh(pixel2, 0, "Red");   // yellow
        assertChannelHigh(pixel2, 1, "Green");
        assertChannelLow(pixel2, 2, "Blue");
    }
}
