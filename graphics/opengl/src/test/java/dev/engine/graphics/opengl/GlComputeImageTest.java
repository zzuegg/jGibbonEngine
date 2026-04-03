package dev.engine.graphics.opengl;

import dev.engine.graphics.command.CommandRecorder;
import dev.engine.graphics.pipeline.ComputePipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.graphics.renderstate.BarrierScope;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.texture.MipMode;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.graphics.texture.TextureFormat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static dev.engine.graphics.opengl.GpuTestHarness.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the compute imageStore -> texture sample -> render pipeline.
 * A compute shader writes a procedural pattern to a texture via imageStore,
 * then a fragment shader samples it and renders it to the framebuffer.
 */
class GlComputeImageTest {

    static final String PASSTHROUGH_VS = """
            #version 450 core
            layout(location = 0) in vec3 position;
            layout(row_major, std140, binding = 0) uniform Matrices { mat4 mvp; };
            out vec2 vUv;
            void main() {
                gl_Position = mvp * vec4(position, 1.0);
                vUv = position.xy * 0.5 + 0.5;
            }
            """;

    static final String TEXTURE_FS = """
            #version 450 core
            layout(binding = 1) uniform sampler2D tex;
            in vec2 vUv;
            out vec4 fragColor;
            void main() {
                fragColor = texture(tex, vUv);
            }
            """;

    static GpuTestHarness gpu;

    @BeforeAll
    static void setUp() { gpu = new GpuTestHarness(64, 64); }

    @AfterAll
    static void tearDown() { gpu.close(); }

    /**
     * Compute shader writes a diagonal gradient pattern via imageStore:
     *   color = (u, v, 1-u, 1) where u=x/width, v=y/height
     * Then a fragment shader samples it and renders to the framebuffer.
     * Verified by reading pixels at known locations.
     */
    @Test void computeWritesGradientThenFragmentSamples() {
        var device = gpu.device();

        var texture = device.createTexture(
                new TextureDescriptor(64, 64, TextureFormat.RGBA8, MipMode.NONE));

        var computeSource = """
                #version 450 core
                layout(rgba8, binding = 0) uniform image2D outputImage;
                layout(local_size_x = 8, local_size_y = 8) in;
                void main() {
                    ivec2 pos = ivec2(gl_GlobalInvocationID.xy);
                    ivec2 size = imageSize(outputImage);
                    float u = float(pos.x) / float(size.x);
                    float v = float(pos.y) / float(size.y);
                    imageStore(outputImage, pos, vec4(u, v, 1.0 - u, 1.0));
                }
                """;

        var computePipeline = device.createComputePipeline(
                ComputePipelineDescriptor.of(new ShaderSource(ShaderStage.COMPUTE, computeSource)));

        // Dispatch compute to fill the texture
        device.beginFrame();
        var rec = new CommandRecorder();
        rec.bindComputePipeline(computePipeline);
        rec.bindImage(0, texture);
        rec.dispatch(64 / 8, 64 / 8, 1);
        rec.memoryBarrier(BarrierScope.TEXTURE);
        device.submit(rec.finish());
        device.endFrame();

        // Render: sample the compute-generated texture
        var sampler = device.createSampler(SamplerDescriptor.nearest());

        gpu.drawFullscreen(PASSTHROUGH_VS, TEXTURE_FS, drawRec -> {
            drawRec.bindTexture(1, texture);
            drawRec.bindSampler(1, sampler);
        });

        // Center (32,32): u=0.5, v=0.5 -> color=(0.5, 0.5, 0.5, 1.0) = gray
        var center = gpu.readCenterPixel();
        assertTrue(center[0] > 100 && center[0] < 160,
                "Red ~128 at center, got " + center[0]);
        assertTrue(center[1] > 100 && center[1] < 160,
                "Green ~128 at center, got " + center[1]);
        assertTrue(center[2] > 100 && center[2] < 160,
                "Blue ~128 at center, got " + center[2]);

        // Near top-left (2,2): u~0, v~0 -> color=(~0, ~0, ~1, 1) = blue
        var topLeft = gpu.readPixel(2, 2);
        assertTrue(topLeft[2] > 200,
                "Blue at top-left, got B=" + topLeft[2]);
        assertTrue(topLeft[0] < 50,
                "Low red at top-left, got R=" + topLeft[0]);

        // Near bottom-right (61,61): u~1, v~1 -> color=(~1, ~1, ~0, 1) = yellow
        var bottomRight = gpu.readPixel(61, 61);
        assertTrue(bottomRight[0] > 200,
                "Red at bottom-right, got R=" + bottomRight[0]);
        assertTrue(bottomRight[1] > 200,
                "Green at bottom-right, got G=" + bottomRight[1]);
        assertTrue(bottomRight[2] < 50,
                "Low blue at bottom-right, got B=" + bottomRight[2]);

        device.destroyTexture(texture);
        device.destroySampler(sampler);
        device.destroyPipeline(computePipeline);
    }

    /**
     * Compute shader writes a checkerboard pattern: 8x8 pixel blocks
     * alternating between gold (1.0, 0.8, 0.2, 1.0) and dark blue (0.1, 0.1, 0.4, 1.0).
     * Fragment shader samples it and renders to the framebuffer.
     */
    @Test void computeCheckerboardPattern() {
        var device = gpu.device();

        var texture = device.createTexture(
                new TextureDescriptor(64, 64, TextureFormat.RGBA8, MipMode.NONE));

        var computeSource = """
                #version 450 core
                layout(rgba8, binding = 0) uniform image2D outputImage;
                layout(local_size_x = 8, local_size_y = 8) in;
                void main() {
                    ivec2 pos = ivec2(gl_GlobalInvocationID.xy);
                    bool white = ((pos.x / 8) + (pos.y / 8)) % 2 == 0;
                    vec4 color = white ? vec4(1.0, 0.8, 0.2, 1.0) : vec4(0.1, 0.1, 0.4, 1.0);
                    imageStore(outputImage, pos, color);
                }
                """;

        var computePipeline = device.createComputePipeline(
                ComputePipelineDescriptor.of(new ShaderSource(ShaderStage.COMPUTE, computeSource)));

        device.beginFrame();
        var rec = new CommandRecorder();
        rec.bindComputePipeline(computePipeline);
        rec.bindImage(0, texture);
        rec.dispatch(64 / 8, 64 / 8, 1);
        rec.memoryBarrier(BarrierScope.TEXTURE);
        device.submit(rec.finish());
        device.endFrame();

        var sampler = device.createSampler(SamplerDescriptor.nearest());

        gpu.drawFullscreen(PASSTHROUGH_VS, TEXTURE_FS, drawRec -> {
            drawRec.bindTexture(1, texture);
            drawRec.bindSampler(1, sampler);
        });

        // Block (0,0) is "white" (gold): pixel at (4,4) should be gold
        var goldSquare = gpu.readPixel(4, 4);
        assertTrue(goldSquare[0] > 200,
                "Gold R at white square, got " + goldSquare[0]);
        assertTrue(goldSquare[1] > 150,
                "Gold G at white square, got " + goldSquare[1]);

        // Block (1,0) is "dark" (dark blue): pixel at (12,4) should be dark blue
        var darkSquare = gpu.readPixel(12, 4);
        assertTrue(darkSquare[2] > 80,
                "Blue at dark square, got B=" + darkSquare[2]);
        assertTrue(darkSquare[0] < 50,
                "Low red at dark square, got R=" + darkSquare[0]);

        device.destroyTexture(texture);
        device.destroySampler(sampler);
        device.destroyPipeline(computePipeline);
    }
}
