package dev.engine.examples;

import dev.engine.core.layout.StructLayout;
import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.command.CommandRecorder;
import dev.engine.graphics.opengl.GlRenderDevice;
import dev.engine.graphics.opengl.GlfwWindowToolkit;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.graphics.vertex.ComponentType;
import dev.engine.graphics.vertex.VertexAttribute;
import dev.engine.graphics.vertex.VertexFormat;
import dev.engine.graphics.window.WindowDescriptor;

import java.lang.foreign.ValueLayout;

public class SpinningCubeExample {

    static final String VS = """
            #version 450 core
            layout(location = 0) in vec3 position;
            layout(location = 1) in vec3 color;
            layout(std140, binding = 0) uniform Matrices { mat4 mvp; };
            out vec3 vColor;
            void main() {
                gl_Position = mvp * vec4(position, 1.0);
                vColor = color;
            }
            """;

    static final String FS = """
            #version 450 core
            in vec3 vColor;
            out vec4 fragColor;
            void main() {
                fragColor = vec4(vColor, 1.0);
            }
            """;

    record Vertex(float x, float y, float z, float r, float g, float b) {}

    public static void main(String[] args) {
        var toolkit = new GlfwWindowToolkit();
        var window = toolkit.createWindow(new WindowDescriptor("Engine - Spinning Cube", 800, 600));
        var device = new GlRenderDevice((GlfwWindowToolkit.GlfwWindowHandle) window);
        window.show();

        // Cube: 24 vertices (4 per face), each face has a solid color
        var layout = StructLayout.of(Vertex.class);
        float s = 0.5f;
        var verts = new Vertex[]{
                // Front face (red)
                new Vertex(-s, -s,  s, 0.9f, 0.2f, 0.2f),
                new Vertex( s, -s,  s, 0.9f, 0.2f, 0.2f),
                new Vertex( s,  s,  s, 0.9f, 0.2f, 0.2f),
                new Vertex(-s,  s,  s, 0.9f, 0.2f, 0.2f),
                // Back face (green)
                new Vertex( s, -s, -s, 0.2f, 0.9f, 0.2f),
                new Vertex(-s, -s, -s, 0.2f, 0.9f, 0.2f),
                new Vertex(-s,  s, -s, 0.2f, 0.9f, 0.2f),
                new Vertex( s,  s, -s, 0.2f, 0.9f, 0.2f),
                // Top face (blue)
                new Vertex(-s,  s,  s, 0.2f, 0.2f, 0.9f),
                new Vertex( s,  s,  s, 0.2f, 0.2f, 0.9f),
                new Vertex( s,  s, -s, 0.2f, 0.2f, 0.9f),
                new Vertex(-s,  s, -s, 0.2f, 0.2f, 0.9f),
                // Bottom face (yellow)
                new Vertex(-s, -s, -s, 0.9f, 0.9f, 0.2f),
                new Vertex( s, -s, -s, 0.9f, 0.9f, 0.2f),
                new Vertex( s, -s,  s, 0.9f, 0.9f, 0.2f),
                new Vertex(-s, -s,  s, 0.9f, 0.9f, 0.2f),
                // Right face (cyan)
                new Vertex( s, -s,  s, 0.2f, 0.9f, 0.9f),
                new Vertex( s, -s, -s, 0.2f, 0.9f, 0.9f),
                new Vertex( s,  s, -s, 0.2f, 0.9f, 0.9f),
                new Vertex( s,  s,  s, 0.2f, 0.9f, 0.9f),
                // Left face (magenta)
                new Vertex(-s, -s, -s, 0.9f, 0.2f, 0.9f),
                new Vertex(-s, -s,  s, 0.9f, 0.2f, 0.9f),
                new Vertex(-s,  s,  s, 0.9f, 0.2f, 0.9f),
                new Vertex(-s,  s, -s, 0.9f, 0.2f, 0.9f),
        };
        long vbSize = (long) layout.size() * verts.length;
        var vbo = device.createBuffer(new BufferDescriptor(vbSize, BufferUsage.VERTEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(vbo)) {
            for (int i = 0; i < verts.length; i++)
                layout.write(w.segment(), (long) layout.size() * i, verts[i]);
        }

        // Index buffer: 6 faces * 2 triangles * 3 indices = 36
        int[] indices = new int[36];
        for (int face = 0; face < 6; face++) {
            int base = face * 4;
            indices[face * 6    ] = base;
            indices[face * 6 + 1] = base + 1;
            indices[face * 6 + 2] = base + 2;
            indices[face * 6 + 3] = base;
            indices[face * 6 + 4] = base + 2;
            indices[face * 6 + 5] = base + 3;
        }
        long ibSize = (long) indices.length * Integer.BYTES;
        var ibo = device.createBuffer(new BufferDescriptor(ibSize, BufferUsage.INDEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(ibo)) {
            for (int i = 0; i < indices.length; i++)
                w.segment().setAtIndex(ValueLayout.JAVA_INT, i, indices[i]);
        }

        // Uniform buffer for MVP matrix
        var matLayout = StructLayout.of(Mat4.class);
        var ubo = device.createBuffer(new BufferDescriptor(matLayout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC));

        var format = VertexFormat.of(
                new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0),
                new VertexAttribute(1, 3, ComponentType.FLOAT, false, 3 * Float.BYTES)
        );
        var vertexInput = device.createVertexInput(format);
        var pipeline = device.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, VS),
                new ShaderSource(ShaderStage.FRAGMENT, FS)));

        long startTime = System.nanoTime();

        while (window.isOpen()) {
            toolkit.pollEvents();

            float time = (float) ((System.nanoTime() - startTime) / 1_000_000_000.0);
            int w = window.width();
            int h = window.height();
            float aspect = (float) w / Math.max(h, 1);

            // Build MVP
            var model = Mat4.rotationY(time * 1.2f).mul(Mat4.rotationX(time * 0.7f));
            var view = Mat4.lookAt(new Vec3(0f, 0f, 3f), Vec3.ZERO, Vec3.UNIT_Y);
            var proj = Mat4.perspective((float) Math.toRadians(45), aspect, 0.1f, 100f);
            var mvp = proj.mul(view).mul(model);

            // Upload MVP (transpose for OpenGL column-major layout)
            try (var writer = device.writeBuffer(ubo)) {
                matLayout.write(writer.segment(), 0, mvp.transpose());
            }

            device.beginFrame();
            var rec = new CommandRecorder();
            rec.viewport(0, 0, w, h);
            rec.setDepthTest(true);
            rec.setCullFace(true);
            rec.clear(0.08f, 0.08f, 0.1f, 1.0f);
            rec.bindPipeline(pipeline);
            rec.bindUniformBuffer(0, ubo);
            rec.bindVertexBuffer(vbo, vertexInput);
            rec.bindIndexBuffer(ibo);
            rec.drawIndexed(indices.length, 0);
            device.submit(rec.finish());
            device.endFrame();
        }

        device.destroyPipeline(pipeline);
        device.destroyVertexInput(vertexInput);
        device.destroyBuffer(ubo);
        device.destroyBuffer(ibo);
        device.destroyBuffer(vbo);
        device.close();
        toolkit.close();
    }
}
