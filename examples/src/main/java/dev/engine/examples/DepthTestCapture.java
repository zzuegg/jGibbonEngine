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

/**
 * Renders two cubes at known depths to verify perspective + depth ordering.
 * Red cube at z=0 (closer), blue cube at z=-3 (farther).
 * If perspective is correct: red should be bigger and occlude blue where they overlap.
 */
public class DepthTestCapture {

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

    record Vertex(float x, float y, float z, float r, float g, float b) {}

    public static void main(String[] args) throws Exception {
        var toolkit = new GlfwWindowToolkit();
        var window = toolkit.createWindow(new WindowDescriptor("Depth Test", 800, 600));
        var device = new GlRenderDevice((GlfwWindowToolkit.GlfwWindowHandle) window);

        var layout = StructLayout.of(Vertex.class);
        var format = VertexFormat.of(
                new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0),
                new VertexAttribute(1, 3, ComponentType.FLOAT, false, 3 * Float.BYTES));
        var vertexInput = device.createVertexInput(format);
        var matLayout = StructLayout.of(Mat4.class);
        var ubo = device.createBuffer(new BufferDescriptor(matLayout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC));
        var pipeline = device.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, VS),
                new ShaderSource(ShaderStage.FRAGMENT, FS)));

        // Red cube at z=0 (should be CLOSER and BIGGER)
        float s = 0.5f;
        var redVerts = makeCubeVerts(s, 0.9f, 0.2f, 0.2f);
        var redVbo = uploadVerts(device, layout, redVerts);
        var ibo = uploadIndices(device);

        // Blue cube at z=-3 (should be FARTHER and SMALLER)
        var blueVerts = makeCubeVerts(s, 0.2f, 0.2f, 0.9f);
        var blueVbo = uploadVerts(device, layout, blueVerts);

        var view = Mat4.lookAt(new Vec3(0f, 1f, 5f), Vec3.ZERO, Vec3.UNIT_Y);
        var proj = Mat4.perspective((float) Math.toRadians(60), 800f / 600f, 0.1f, 100f);

        device.beginFrame();

        var setup = new CommandRecorder();
        setup.viewport(0, 0, 800, 600);
        setup.setDepthTest(true);
        setup.setCullFace(true);
        setup.clear(0.05f, 0.05f, 0.08f, 1f);
        setup.bindPipeline(pipeline);
        device.submit(setup.finish());

        // Draw red cube at z=0 (closer)
        var mvpRed = proj.mul(view).mul(Mat4.translation(-0.5f, 0f, 0f));
        try (var w = device.writeBuffer(ubo)) { matLayout.write(w.segment(), 0, mvpRed); }
        var drawRed = new CommandRecorder();
        drawRed.bindUniformBuffer(0, ubo);
        drawRed.bindVertexBuffer(redVbo, vertexInput);
        drawRed.bindIndexBuffer(ibo);
        drawRed.drawIndexed(36, 0);
        device.submit(drawRed.finish());

        // Draw blue cube at z=-3 (farther)
        var mvpBlue = proj.mul(view).mul(Mat4.translation(0.5f, 0f, -3f));
        try (var w = device.writeBuffer(ubo)) { matLayout.write(w.segment(), 0, mvpBlue); }
        var drawBlue = new CommandRecorder();
        drawBlue.bindUniformBuffer(0, ubo);
        drawBlue.bindVertexBuffer(blueVbo, vertexInput);
        drawBlue.bindIndexBuffer(ibo);
        drawBlue.drawIndexed(36, 0);
        device.submit(drawBlue.finish());

        device.endFrame();

        ScreenshotUtil.capture(800, 600, "/tmp/engine_depth_test.png");
        System.out.println("Saved /tmp/engine_depth_test.png");
        System.out.println("Red cube at z=0 (closer) should be BIGGER");
        System.out.println("Blue cube at z=-3 (farther) should be SMALLER");

        device.close();
        toolkit.close();
    }

    static Vertex[] makeCubeVerts(float s, float r, float g, float b) {
        return new Vertex[]{
                new Vertex(-s,-s,s,r,g,b), new Vertex(s,-s,s,r,g,b), new Vertex(s,s,s,r,g,b), new Vertex(-s,s,s,r,g,b),
                new Vertex(s,-s,-s,r,g,b), new Vertex(-s,-s,-s,r,g,b), new Vertex(-s,s,-s,r,g,b), new Vertex(s,s,-s,r,g,b),
                new Vertex(-s,s,s,r,g,b), new Vertex(s,s,s,r,g,b), new Vertex(s,s,-s,r,g,b), new Vertex(-s,s,-s,r,g,b),
                new Vertex(-s,-s,-s,r,g,b), new Vertex(s,-s,-s,r,g,b), new Vertex(s,-s,s,r,g,b), new Vertex(-s,-s,s,r,g,b),
                new Vertex(s,-s,s,r,g,b), new Vertex(s,-s,-s,r,g,b), new Vertex(s,s,-s,r,g,b), new Vertex(s,s,s,r,g,b),
                new Vertex(-s,-s,-s,r,g,b), new Vertex(-s,-s,s,r,g,b), new Vertex(-s,s,s,r,g,b), new Vertex(-s,s,-s,r,g,b),
        };
    }

    static dev.engine.core.handle.Handle<dev.engine.graphics.BufferResource> uploadVerts(
            GlRenderDevice device, StructLayout layout, Vertex[] verts) {
        long size = (long) layout.size() * verts.length;
        var vbo = device.createBuffer(new BufferDescriptor(size, BufferUsage.VERTEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(vbo)) {
            for (int i = 0; i < verts.length; i++) layout.write(w.segment(), (long) layout.size() * i, verts[i]);
        }
        return vbo;
    }

    static dev.engine.core.handle.Handle<dev.engine.graphics.BufferResource> uploadIndices(GlRenderDevice device) {
        int[] idx = new int[36];
        for (int f = 0; f < 6; f++) { int b = f*4; idx[f*6]=b; idx[f*6+1]=b+1; idx[f*6+2]=b+2; idx[f*6+3]=b; idx[f*6+4]=b+2; idx[f*6+5]=b+3; }
        long size = (long) idx.length * Integer.BYTES;
        var ibo = device.createBuffer(new BufferDescriptor(size, BufferUsage.INDEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(ibo)) { for (int i = 0; i < idx.length; i++) w.segment().setAtIndex(ValueLayout.JAVA_INT, i, idx[i]); }
        return ibo;
    }
}
