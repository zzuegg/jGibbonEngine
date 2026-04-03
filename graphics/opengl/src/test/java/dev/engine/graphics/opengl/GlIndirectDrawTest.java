package dev.engine.graphics.opengl;

import dev.engine.core.mesh.ComponentType;
import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.command.CommandRecorder;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import org.junit.jupiter.api.Test;


class GlIndirectDrawTest {

    @Test void indirectDrawFromBuffer() {
        try (var harness = new GpuTestHarness(64, 64)) {
            var device = harness.device();

            var vertShader = """
                #version 450
                layout(location = 0) in vec3 pos;
                void main() { gl_Position = vec4(pos, 1.0); }
                """;
            var fragShader = """
                #version 450
                out vec4 fragColor;
                void main() { fragColor = vec4(0.0, 1.0, 0.0, 1.0); }
                """;

            var pipeline = device.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, vertShader),
                new ShaderSource(ShaderStage.FRAGMENT, fragShader)));

            // Create fullscreen triangle vertices
            float[] verts = { -1,-1,0,  3,-1,0,  -1,3,0 };
            long vbSize = (long) verts.length * Float.BYTES;
            var vbo = device.createBuffer(new BufferDescriptor(vbSize, BufferUsage.VERTEX, AccessPattern.STATIC));
            try (var w = device.writeBuffer(vbo)) {
                for (int i = 0; i < verts.length; i++)
                    w.memory().putFloat((long) i * Float.BYTES, verts[i]);
            }
            var vi = device.createVertexInput(VertexFormat.of(
                new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0)));

            // Create indirect draw buffer: { vertexCount=3, instanceCount=1, firstVertex=0, firstInstance=0 }
            var indirectBuf = device.createBuffer(new BufferDescriptor(16, BufferUsage.STORAGE, AccessPattern.STATIC));
            try (var w = device.writeBuffer(indirectBuf)) {
                w.memory().putInt(0, 3);   // vertexCount
                w.memory().putInt(4, 1);   // instanceCount
                w.memory().putInt(8, 0);   // firstVertex
                w.memory().putInt(12, 0);  // firstInstance
            }

            // Clear and draw via indirect
            device.beginFrame();
            var rec = new CommandRecorder();
            rec.viewport(0, 0, 64, 64);
            rec.clear(0, 0, 0, 1);
            rec.bindPipeline(pipeline);
            rec.bindVertexBuffer(vbo, vi);
            rec.drawIndirect(indirectBuf, 0, 1, 0);
            device.submit(rec.finish());
            device.endFrame();

            // Should be green (fullscreen)
            var pixel = harness.readCenterPixel();
            GpuTestHarness.assertChannelHigh(pixel, 1, "Green");
            GpuTestHarness.assertChannelLow(pixel, 0, "Red");

            device.destroyBuffer(vbo);
            device.destroyBuffer(indirectBuf);
            device.destroyVertexInput(vi);
            device.destroyPipeline(pipeline);
        }
    }
}
