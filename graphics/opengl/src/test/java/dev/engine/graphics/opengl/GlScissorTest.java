package dev.engine.graphics.opengl;

import dev.engine.core.property.PropertyMap;
import dev.engine.graphics.renderstate.RenderState;
import org.junit.jupiter.api.Test;

class GlScissorTest {

    @Test void scissorClipsToQuadrant() {
        try (var harness = new GpuTestHarness(64, 64)) {
            var vertShader = """
                #version 450
                layout(location = 0) in vec3 pos;
                layout(binding = 0) uniform MVP { mat4 mvp; };
                void main() { gl_Position = mvp * vec4(pos, 1.0); }
                """;
            var fragShader = """
                #version 450
                out vec4 fragColor;
                void main() { fragColor = vec4(1.0, 0.0, 0.0, 1.0); }
                """;

            // Clear to black
            harness.clear(0, 0, 0, 1);

            // Draw red fullscreen with scissor enabled, clipped to right half
            harness.drawFullscreen(vertShader, fragShader, rec -> {
                rec.setRenderState(PropertyMap.<RenderState>builder()
                    .set(RenderState.SCISSOR_TEST, true)
                    .build());
                rec.scissor(32, 0, 32, 64); // right half only
            });

            // Left half should be black (clipped)
            var left = harness.readPixel(16, 32);
            GpuTestHarness.assertChannelLow(left, 0, "Red (left, should be clipped)");

            // Right half should be red
            var right = harness.readPixel(48, 32);
            GpuTestHarness.assertChannelHigh(right, 0, "Red (right, should be drawn)");
        }
    }
}
