package dev.engine.examples;

import dev.engine.core.layout.StructLayout;
import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.Scene;
import dev.engine.core.scene.SceneAccess;
import dev.engine.core.scene.camera.Camera;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.command.CommandRecorder;
import dev.engine.graphics.opengl.GlRenderDevice;
import dev.engine.graphics.opengl.GlfwWindowToolkit;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.graphics.renderer.MeshRenderer;
import dev.engine.graphics.renderer.Renderable;
import dev.engine.core.mesh.ComponentType;
import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.graphics.window.WindowDescriptor;

import java.lang.foreign.ValueLayout;

/**
 * Complete scene rendering example demonstrating:
 * - Scene graph with entity hierarchy
 * - Camera with perspective projection
 * - Transaction-based scene → renderer communication
 * - MeshRenderer processing transactions and collecting draw batches
 * - Multiple objects with independent transforms
 */
public class SceneExample {

    static final String VS = """
            #version 450 core
            layout(location = 0) in vec3 position;
            layout(location = 1) in vec3 color;
            layout(row_major, std140, binding = 0) uniform Matrices { mat4 mvp; };
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
        // Window + device
        var toolkit = new GlfwWindowToolkit();
        var window = toolkit.createWindow(new WindowDescriptor("Engine - Scene Demo", 1024, 768));
        var device = new GlRenderDevice((GlfwWindowToolkit.GlfwWindowHandle) window);
        window.show();

        // Scene + renderer
        var scene = new Scene();
        var meshRenderer = new MeshRenderer();
        var camera = new Camera();

        // Shared resources
        var pipeline = device.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, VS),
                new ShaderSource(ShaderStage.FRAGMENT, FS)));

        var format = VertexFormat.of(
                new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0),
                new VertexAttribute(1, 3, ComponentType.FLOAT, false, 3 * Float.BYTES));
        var vertexInput = device.createVertexInput(format);

        // Create cube mesh
        var cubeVbo = createCubeVbo(device);
        var cubeIbo = createCubeIbo(device);

        // UBO for MVP
        var matLayout = StructLayout.of(Mat4.class);
        var ubo = device.createBuffer(new BufferDescriptor(matLayout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC));

        // Create scene entities
        var root = scene.createEntity();
        var cube1 = scene.createEntity();
        var cube2 = scene.createEntity();
        var cube3 = scene.createEntity();
        scene.setParent(cube1, root);
        scene.setParent(cube2, root);
        scene.setParent(cube3, root);

        // Process initial transactions
        meshRenderer.processTransactions(SceneAccess.drainTransactions(scene));

        // Assign renderables
        meshRenderer.setRenderable(cube1, new Renderable(cubeVbo, cubeIbo, vertexInput, pipeline, 8, 36));
        meshRenderer.setRenderable(cube2, new Renderable(cubeVbo, cubeIbo, vertexInput, pipeline, 8, 36));
        meshRenderer.setRenderable(cube3, new Renderable(cubeVbo, cubeIbo, vertexInput, pipeline, 8, 36));

        long startTime = System.nanoTime();

        while (window.isOpen()) {
            toolkit.pollEvents();
            float time = (float) ((System.nanoTime() - startTime) / 1_000_000_000.0);
            int w = window.width(), h = window.height();
            float aspect = (float) w / Math.max(h, 1);

            // Update scene transforms
            scene.setLocalTransform(root, Mat4.rotationY(time * 0.3f));
            scene.setLocalTransform(cube1, Mat4.translation(-2f, 0f, 0f).mul(Mat4.rotationX(time)));
            scene.setLocalTransform(cube2, Mat4.translation(0f, (float) Math.sin(time) * 1.5f, 0f).mul(Mat4.rotationZ(time * 1.5f)));
            scene.setLocalTransform(cube3, Mat4.translation(2f, 0f, 0f).mul(Mat4.rotationY(time * 2f)));

            // Push transactions to renderer
            meshRenderer.processTransactions(SceneAccess.drainTransactions(scene));

            // Camera
            camera.setPerspective((float) Math.toRadians(60), aspect, 0.1f, 100f);
            camera.lookAt(new Vec3(0f, 3f, 7f), Vec3.ZERO, Vec3.UNIT_Y);
            var vp = camera.viewProjectionMatrix();

            // Render
            device.beginFrame();

            // Setup pass
            var setup = new CommandRecorder();
            setup.viewport(0, 0, w, h);
            setup.setDepthTest(true);
            setup.setCullFace(true);
            setup.clear(0.05f, 0.05f, 0.08f, 1f);
            setup.bindPipeline(pipeline);
            device.submit(setup.finish());

            // Draw each object: update UBO then submit draw commands
            for (var cmd : meshRenderer.collectBatch()) {
                var mvp = vp.mul(cmd.transform());
                try (var writer = device.writeBuffer(ubo)) {
                    matLayout.write(writer.segment(), 0, mvp);
                }
                var draw = new CommandRecorder();
                draw.bindUniformBuffer(0, ubo);
                draw.bindVertexBuffer(cmd.renderable().vertexBuffer(), cmd.renderable().vertexInput());
                draw.bindIndexBuffer(cmd.renderable().indexBuffer());
                draw.drawIndexed(cmd.renderable().indexCount(), 0);
                device.submit(draw.finish());
            }

            device.endFrame();
        }

        // Cleanup
        device.destroyBuffer(ubo);
        device.destroyBuffer(cubeIbo);
        device.destroyBuffer(cubeVbo);
        device.destroyVertexInput(vertexInput);
        device.destroyPipeline(pipeline);
        device.close();
        toolkit.close();
    }

    static dev.engine.core.handle.Handle<dev.engine.graphics.BufferResource> createCubeVbo(GlRenderDevice device) {
        var layout = StructLayout.of(Vertex.class);
        var verts = new Vertex[]{
                new Vertex(-0.4f, -0.4f, -0.4f, 1f, 0.3f, 0.3f),
                new Vertex(0.4f, -0.4f, -0.4f, 0.3f, 1f, 0.3f),
                new Vertex(0.4f, 0.4f, -0.4f, 0.3f, 0.3f, 1f),
                new Vertex(-0.4f, 0.4f, -0.4f, 1f, 1f, 0.3f),
                new Vertex(-0.4f, -0.4f, 0.4f, 1f, 0.3f, 1f),
                new Vertex(0.4f, -0.4f, 0.4f, 0.3f, 1f, 1f),
                new Vertex(0.4f, 0.4f, 0.4f, 1f, 1f, 1f),
                new Vertex(-0.4f, 0.4f, 0.4f, 0.6f, 0.6f, 0.6f),
        };
        long size = (long) layout.size() * verts.length;
        var vbo = device.createBuffer(new BufferDescriptor(size, BufferUsage.VERTEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(vbo)) {
            for (int i = 0; i < verts.length; i++) layout.write(w.segment(), (long) layout.size() * i, verts[i]);
        }
        return vbo;
    }

    static dev.engine.core.handle.Handle<dev.engine.graphics.BufferResource> createCubeIbo(GlRenderDevice device) {
        int[] idx = {0,1,2,0,2,3, 4,6,5,4,7,6, 0,4,5,0,5,1, 2,6,7,2,7,3, 0,3,7,0,7,4, 1,5,6,1,6,2};
        long size = (long) idx.length * Integer.BYTES;
        var ibo = device.createBuffer(new BufferDescriptor(size, BufferUsage.INDEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(ibo)) {
            for (int i = 0; i < idx.length; i++) w.segment().setAtIndex(ValueLayout.JAVA_INT, i, idx[i]);
        }
        return ibo;
    }
}
