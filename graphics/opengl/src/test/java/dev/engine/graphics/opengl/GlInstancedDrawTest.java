package dev.engine.graphics.opengl;

import dev.engine.graphics.command.CommandRecorder;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.core.mesh.ComponentType;
import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import org.junit.jupiter.api.Test;

import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.*;

class GlInstancedDrawTest {

    @Test void instanceIdDrivesPosition() {
        try (var harness = new GpuTestHarness(64, 64)) {
            var device = harness.device();

            // Vertex shader: gl_InstanceID shifts x position
            // Instance 0 draws in the left half, instance 1 in the right half
            var vertShader = """
                #version 450 core
                layout(location = 0) in vec3 pos;
                void main() {
                    float xOffset = gl_InstanceID == 0 ? -0.5 : 0.5;
                    gl_Position = vec4(pos.x * 0.5 + xOffset, pos.y, pos.z, 1.0);
                }
                """;

            var fragShader = """
                #version 450 core
                out vec4 fragColor;
                void main() {
                    fragColor = vec4(0.0, 1.0, 0.0, 1.0);
                }
                """;

            var pipeline = device.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, vertShader),
                new ShaderSource(ShaderStage.FRAGMENT, fragShader)));

            // Full-screen quad (2 triangles, 6 vertices)
            float[] verts = {
                -1, -1, 0,   1, -1, 0,   1, 1, 0,
                -1, -1, 0,   1, 1, 0,  -1, 1, 0
            };
            long vbSize = (long) verts.length * Float.BYTES;
            var vbo = device.createBuffer(new BufferDescriptor(vbSize, BufferUsage.VERTEX, AccessPattern.STATIC));
            try (var w = device.writeBuffer(vbo)) {
                for (int i = 0; i < verts.length; i++)
                    w.segment().set(ValueLayout.JAVA_FLOAT, (long) i * Float.BYTES, verts[i]);
            }
            var vi = device.createVertexInput(VertexFormat.of(
                new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0)));

            // Clear to black, then draw 2 instances
            device.beginFrame();
            var rec = new CommandRecorder();
            rec.viewport(0, 0, 64, 64);
            rec.clear(0, 0, 0, 1);
            rec.bindPipeline(pipeline);
            rec.bindVertexBuffer(vbo, vi);
            rec.drawInstanced(6, 0, 2, 0);
            device.submit(rec.finish());
            device.endFrame();

            // Left quarter (instance 0 centered at x=-0.5) should be green
            var leftPixel = harness.readPixel(16, 32);
            GpuTestHarness.assertChannelHigh(leftPixel, 1, "Green (left)");

            // Right quarter (instance 1 centered at x=+0.5) should be green
            var rightPixel = harness.readPixel(48, 32);
            GpuTestHarness.assertChannelHigh(rightPixel, 1, "Green (right)");

            device.destroyBuffer(vbo);
            device.destroyVertexInput(vi);
            device.destroyPipeline(pipeline);
        }
    }
}
