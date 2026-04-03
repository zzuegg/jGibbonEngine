package dev.engine.graphics.opengl;

import dev.engine.graphics.command.CommandRecorder;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.graphics.target.RenderTargetDescriptor;
import dev.engine.graphics.texture.TextureFormat;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL45;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlMultiRenderTargetTest {

    @Test void multipleColorAttachments() {
        try (var harness = new GpuTestHarness(64, 64)) {
            var device = harness.device();

            // Fragment shader writes different colors to two attachments
            var vertShader = """
                #version 450
                layout(location = 0) in vec3 pos;
                void main() { gl_Position = vec4(pos, 1.0); }
                """;
            var fragShader = """
                #version 450
                layout(location = 0) out vec4 color0;
                layout(location = 1) out vec4 color1;
                void main() {
                    color0 = vec4(1.0, 0.0, 0.0, 1.0);  // red
                    color1 = vec4(0.0, 0.0, 1.0, 1.0);   // blue
                }
                """;

            // Create MRT with 2 color attachments
            var rtDesc = new RenderTargetDescriptor(64, 64,
                List.of(TextureFormat.RGBA8, TextureFormat.RGBA8), null);
            var rt = device.createRenderTarget(rtDesc);
            var colorTex0 = device.getRenderTargetColorTexture(rt, 0);
            var colorTex1 = device.getRenderTargetColorTexture(rt, 1);

            assertTrue(device.isValidTexture(colorTex0), "Color attachment 0 should be valid");
            assertTrue(device.isValidTexture(colorTex1), "Color attachment 1 should be valid");

            // Build fullscreen triangle VBO + pipeline
            var pipeline = device.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, vertShader),
                new ShaderSource(ShaderStage.FRAGMENT, fragShader)));

            float[] verts = { -1,-1,0,  3,-1,0,  -1,3,0 };
            long vbSize = (long) verts.length * Float.BYTES;
            var vbo = device.createBuffer(new dev.engine.graphics.buffer.BufferDescriptor(
                vbSize, dev.engine.graphics.buffer.BufferUsage.VERTEX,
                dev.engine.graphics.buffer.AccessPattern.STATIC));
            try (var w = device.writeBuffer(vbo)) {
                for (int i = 0; i < verts.length; i++)
                    w.memory().putFloat((long) i * Float.BYTES, verts[i]);
            }
            var vi = device.createVertexInput(dev.engine.core.mesh.VertexFormat.of(
                new dev.engine.core.mesh.VertexAttribute(0, 3,
                    dev.engine.core.mesh.ComponentType.FLOAT, false, 0)));

            // Render to MRT
            device.beginFrame();
            var rec = new CommandRecorder();
            rec.bindRenderTarget(rt);
            rec.viewport(0, 0, 64, 64);
            rec.clear(0, 0, 0, 1);
            rec.bindPipeline(pipeline);
            rec.bindVertexBuffer(vbo, vi);
            rec.draw(3, 0);
            rec.bindDefaultRenderTarget();
            device.submit(rec.finish());
            device.endFrame();

            // Read back attachment 0 — should be red
            int glTex0 = device.getGlTextureName(colorTex0);
            ByteBuffer readback0 = ByteBuffer.allocateDirect(64 * 64 * 4);
            GL45.glGetTextureImage(glTex0, 0, GL45.GL_RGBA, GL45.GL_UNSIGNED_BYTE, readback0);
            int center = (32 * 64 + 32) * 4;
            int r0 = readback0.get(center) & 0xFF;
            int b0 = readback0.get(center + 2) & 0xFF;
            assertTrue(r0 > 200, "Attachment 0 red channel should be high, got " + r0);
            assertTrue(b0 < 50, "Attachment 0 blue channel should be low, got " + b0);

            // Read back attachment 1 — should be blue
            int glTex1 = device.getGlTextureName(colorTex1);
            ByteBuffer readback1 = ByteBuffer.allocateDirect(64 * 64 * 4);
            GL45.glGetTextureImage(glTex1, 0, GL45.GL_RGBA, GL45.GL_UNSIGNED_BYTE, readback1);
            int r1 = readback1.get(center) & 0xFF;
            int b1 = readback1.get(center + 2) & 0xFF;
            assertTrue(r1 < 50, "Attachment 1 red channel should be low, got " + r1);
            assertTrue(b1 > 200, "Attachment 1 blue channel should be high, got " + b1);

            device.destroyRenderTarget(rt);
            device.destroyBuffer(vbo);
            device.destroyVertexInput(vi);
            device.destroyPipeline(pipeline);
        }
    }
}
