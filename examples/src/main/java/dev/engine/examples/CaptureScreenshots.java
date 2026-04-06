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
import dev.engine.windowing.glfw.GlfwWindowToolkit;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.graphics.renderer.MeshRenderer;
import dev.engine.graphics.renderer.Renderable;
import dev.engine.core.mesh.ComponentType;
import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.graphics.window.WindowDescriptor;


public class CaptureScreenshots {

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
            void main() { fragColor = vec4(vColor, 1.0); }
            """;
    static final String VS_SIMPLE = """
            #version 450 core
            layout(location = 0) in vec3 position;
            layout(location = 1) in vec3 color;
            out vec3 vColor;
            void main() {
                gl_Position = vec4(position, 1.0);
                vColor = color;
            }
            """;

    record Vertex(float x, float y, float z, float r, float g, float b) {}

    public static void main(String[] args) throws Exception {
        var toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.OPENGL_HINTS);
        var window = toolkit.createWindow(new WindowDescriptor("Screenshot", 800, 600));
        var device = new GlRenderDevice(window, new dev.engine.providers.lwjgl.graphics.opengl.LwjglGlBindings());

        var layout = StructLayout.of(Vertex.class);
        var format = VertexFormat.of(
                new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0),
                new VertexAttribute(1, 3, ComponentType.FLOAT, false, 3 * Float.BYTES));
        var vertexInput = device.createVertexInput(format);
        var matLayout = StructLayout.of(Mat4.class);
        var ubo = device.createBuffer(new BufferDescriptor(matLayout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC));

        // === Screenshot 1: Triangle ===
        var triPipeline = device.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, VS_SIMPLE),
                new ShaderSource(ShaderStage.FRAGMENT, FS)));
        var triVerts = new Vertex[]{
                new Vertex(0f, 0.5f, 0f, 1f, 0f, 0f),
                new Vertex(-0.5f, -0.5f, 0f, 0f, 1f, 0f),
                new Vertex(0.5f, -0.5f, 0f, 0f, 0f, 1f),
        };
        var triVbo = uploadVertices(device, layout, triVerts);
        device.beginFrame();
        var rec = new CommandRecorder();
        rec.viewport(0, 0, 800, 600);
        rec.clear(0.1f, 0.1f, 0.12f, 1f);
        rec.bindPipeline(triPipeline);
        rec.bindVertexBuffer(triVbo, vertexInput);
        rec.draw(3, 0);
        device.submit(rec.finish());
        device.endFrame();
        ScreenshotUtil.capture(800, 600, "/tmp/engine_triangle.png");
        System.out.println("Saved /tmp/engine_triangle.png");

        // === Screenshot 2: Spinning Cube (at t=1.5s) ===
        var cubePipeline = device.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, VS),
                new ShaderSource(ShaderStage.FRAGMENT, FS)));
        float s = 0.5f;
        var cubeVerts = new Vertex[]{
                new Vertex(-s,-s, s, .9f,.2f,.2f), new Vertex(s,-s, s, .9f,.2f,.2f),
                new Vertex(s, s, s, .9f,.2f,.2f), new Vertex(-s, s, s, .9f,.2f,.2f),
                new Vertex(s,-s,-s, .2f,.9f,.2f), new Vertex(-s,-s,-s, .2f,.9f,.2f),
                new Vertex(-s, s,-s, .2f,.9f,.2f), new Vertex(s, s,-s, .2f,.9f,.2f),
                new Vertex(-s, s, s, .2f,.2f,.9f), new Vertex(s, s, s, .2f,.2f,.9f),
                new Vertex(s, s,-s, .2f,.2f,.9f), new Vertex(-s, s,-s, .2f,.2f,.9f),
                new Vertex(-s,-s,-s, .9f,.9f,.2f), new Vertex(s,-s,-s, .9f,.9f,.2f),
                new Vertex(s,-s, s, .9f,.9f,.2f), new Vertex(-s,-s, s, .9f,.9f,.2f),
                new Vertex(s,-s, s, .2f,.9f,.9f), new Vertex(s,-s,-s, .2f,.9f,.9f),
                new Vertex(s, s,-s, .2f,.9f,.9f), new Vertex(s, s, s, .2f,.9f,.9f),
                new Vertex(-s,-s,-s, .9f,.2f,.9f), new Vertex(-s,-s, s, .9f,.2f,.9f),
                new Vertex(-s, s, s, .9f,.2f,.9f), new Vertex(-s, s,-s, .9f,.2f,.9f),
        };
        var cubeVbo = uploadVertices(device, layout, cubeVerts);
        int[] idx = new int[36];
        for (int face = 0; face < 6; face++) {
            int b2 = face * 4;
            idx[face*6]=b2; idx[face*6+1]=b2+1; idx[face*6+2]=b2+2;
            idx[face*6+3]=b2; idx[face*6+4]=b2+2; idx[face*6+5]=b2+3;
        }
        var cubeIbo = device.createBuffer(new BufferDescriptor((long) idx.length * Integer.BYTES, BufferUsage.INDEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(cubeIbo)) {
            for (int i = 0; i < idx.length; i++) w.memory().putInt((long) i * Integer.BYTES, idx[i]);
        }

        float time = 1.5f;
        var model = Mat4.rotationY(time * 1.2f).mul(Mat4.rotationX(time * 0.7f));
        var view = Mat4.lookAt(new Vec3(0f, 0f, 3f), Vec3.ZERO, Vec3.UNIT_Y);
        var proj = Mat4.perspective((float) Math.toRadians(45), 800f / 600f, 0.1f, 100f);
        var mvp = proj.mul(view).mul(model);
        try (var w = device.writeBuffer(ubo)) { matLayout.write(w.memory(), 0, mvp); }

        device.beginFrame();
        rec = new CommandRecorder();
        rec.viewport(0, 0, 800, 600);
        rec.setRenderState(dev.engine.graphics.renderstate.RenderState.defaults());
        rec.clear(0.08f, 0.08f, 0.1f, 1f);
        rec.bindPipeline(cubePipeline);
        rec.bindUniformBuffer(0, ubo);
        rec.bindVertexBuffer(cubeVbo, vertexInput);
        rec.bindIndexBuffer(cubeIbo);
        rec.drawIndexed(36, 0);
        device.submit(rec.finish());
        device.endFrame();
        ScreenshotUtil.capture(800, 600, "/tmp/engine_cube.png");
        System.out.println("Saved /tmp/engine_cube.png");

        // === Screenshot 3: Scene with 3 cubes ===
        var scene = new Scene();
        var meshRenderer = new MeshRenderer();
        var camera = new Camera();
        var root = scene.createEntity();
        var c1 = scene.createEntity(); c1.setParent(root);
        var c2 = scene.createEntity(); c2.setParent(root);
        var c3 = scene.createEntity(); c3.setParent(root);
        meshRenderer.processTransactions(SceneAccess.drainTransactions(scene));

        // Reuse the same cube mesh for all 3 entities
        meshRenderer.setRenderable(c1.handle(), new Renderable(cubeVbo, cubeIbo, vertexInput, cubePipeline, 24, 36));
        meshRenderer.setRenderable(c2.handle(), new Renderable(cubeVbo, cubeIbo, vertexInput, cubePipeline, 24, 36));
        meshRenderer.setRenderable(c3.handle(), new Renderable(cubeVbo, cubeIbo, vertexInput, cubePipeline, 24, 36));

        time = 2f;
        scene.setLocalTransform(root, Mat4.rotationY(time * 0.3f));
        scene.setLocalTransform(c1, Mat4.translation(-2f, 0f, 0f).mul(Mat4.rotationX(time)));
        scene.setLocalTransform(c2, Mat4.translation(0f, (float) Math.sin(time) * 1.5f, 0f).mul(Mat4.rotationZ(time * 1.5f)));
        scene.setLocalTransform(c3, Mat4.translation(2f, 0f, 0f).mul(Mat4.rotationY(time * 2f)));
        meshRenderer.processTransactions(SceneAccess.drainTransactions(scene));

        camera.setPerspective((float) Math.toRadians(60), 800f / 600f, 0.1f, 100f);
        camera.lookAt(new Vec3(0f, 3f, 7f), Vec3.ZERO, Vec3.UNIT_Y);
        var vp = camera.viewProjectionMatrix();

        device.beginFrame();
        var setup2 = new CommandRecorder();
        setup2.viewport(0, 0, 800, 600);
        setup2.setRenderState(dev.engine.graphics.renderstate.RenderState.defaults());
        setup2.clear(0.05f, 0.05f, 0.08f, 1f);
        setup2.bindPipeline(cubePipeline);
        device.submit(setup2.finish());
        for (var cmd : meshRenderer.collectBatch()) {
            var m = vp.mul(cmd.transform());
            try (var w = device.writeBuffer(ubo)) { matLayout.write(w.memory(), 0, m); }
            var draw = new CommandRecorder();
            draw.bindUniformBuffer(0, ubo);
            draw.bindVertexBuffer(cmd.renderable().vertexBuffer(), cmd.renderable().vertexInput());
            draw.bindIndexBuffer(cmd.renderable().indexBuffer());
            draw.drawIndexed(cmd.renderable().indexCount(), 0);
            device.submit(draw.finish());
        }
        device.endFrame();
        ScreenshotUtil.capture(800, 600, "/tmp/engine_scene.png");
        System.out.println("Saved /tmp/engine_scene.png");

        device.close();
        toolkit.close();
        System.out.println("All screenshots captured.");
    }

    static dev.engine.core.handle.Handle<dev.engine.graphics.BufferResource> uploadVertices(
            GlRenderDevice device, StructLayout layout, Vertex[] verts) {
        long size = (long) layout.size() * verts.length;
        var vbo = device.createBuffer(new BufferDescriptor(size, BufferUsage.VERTEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(vbo)) {
            for (int i = 0; i < verts.length; i++) layout.write(w.memory(), (long) layout.size() * i, verts[i]);
        }
        return vbo;
    }
}
