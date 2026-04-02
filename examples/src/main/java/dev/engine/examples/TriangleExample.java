package dev.engine.examples;

import dev.engine.core.layout.StructLayout;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.command.CommandRecorder;
import dev.engine.graphics.opengl.GlRenderDevice;
import dev.engine.graphics.opengl.GlfwWindowToolkit;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.core.mesh.ComponentType;
import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.graphics.window.WindowDescriptor;

public class TriangleExample {

    static final String VERTEX_SHADER = """
            #version 450 core
            layout(location = 0) in vec3 position;
            layout(location = 1) in vec3 color;
            out vec3 vColor;
            void main() {
                gl_Position = vec4(position, 1.0);
                vColor = color;
            }
            """;

    static final String FRAGMENT_SHADER = """
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
        var window = toolkit.createWindow(new WindowDescriptor("Engine - Triangle", 800, 600));
        var device = new GlRenderDevice((GlfwWindowToolkit.GlfwWindowHandle) window);

        window.show();

        // Upload colored triangle vertices
        var layout = StructLayout.of(Vertex.class);
        var vertices = new Vertex[]{
                new Vertex(0.0f,  0.5f, 0f, 1f, 0f, 0f),  // top - red
                new Vertex(-0.5f, -0.5f, 0f, 0f, 1f, 0f),  // bottom left - green
                new Vertex(0.5f, -0.5f, 0f, 0f, 0f, 1f),  // bottom right - blue
        };
        long bufSize = (long) layout.size() * vertices.length;
        var vbo = device.createBuffer(new BufferDescriptor(bufSize, BufferUsage.VERTEX, AccessPattern.STATIC));
        try (var writer = device.writeBuffer(vbo)) {
            for (int i = 0; i < vertices.length; i++) {
                layout.write(writer.segment(), (long) layout.size() * i, vertices[i]);
            }
        }

        // Vertex format: position (loc 0, 3 floats) + color (loc 1, 3 floats)
        var format = VertexFormat.of(
                new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0),
                new VertexAttribute(1, 3, ComponentType.FLOAT, false, 3 * Float.BYTES)
        );
        var vertexInput = device.createVertexInput(format);

        // Compile shaders
        var pipeline = device.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, VERTEX_SHADER),
                new ShaderSource(ShaderStage.FRAGMENT, FRAGMENT_SHADER)
        ));

        // Render loop
        while (window.isOpen()) {
            toolkit.pollEvents();

            int w = window.width();
            int h = window.height();

            device.beginFrame();
            var rec = new CommandRecorder();
            rec.viewport(0, 0, w, h);
            rec.clear(0.1f, 0.1f, 0.12f, 1.0f);
            rec.bindPipeline(pipeline);
            rec.bindVertexBuffer(vbo, vertexInput);
            rec.draw(3, 0);
            device.submit(rec.finish());
            device.endFrame();
        }

        // Cleanup
        device.destroyPipeline(pipeline);
        device.destroyVertexInput(vertexInput);
        device.destroyBuffer(vbo);
        device.close();
        toolkit.close();
    }
}
