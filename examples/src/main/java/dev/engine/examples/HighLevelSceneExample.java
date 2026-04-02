package dev.engine.examples;

import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;
import dev.engine.graphics.common.Renderer;
import dev.engine.graphics.opengl.GlRenderDevice;
import dev.engine.graphics.opengl.GlfwWindowToolkit;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.graphics.vertex.ComponentType;
import dev.engine.graphics.vertex.VertexAttribute;
import dev.engine.graphics.vertex.VertexFormat;
import dev.engine.graphics.window.WindowDescriptor;

/**
 * Scene example using the high-level Renderer API.
 * Compare with SceneExample.java — this is much simpler.
 */
public class HighLevelSceneExample {

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

    public static void main(String[] args) {
        // Create window + renderer
        var toolkit = new GlfwWindowToolkit();
        var window = toolkit.createWindow(new WindowDescriptor("Engine - High Level", 1024, 768));
        var renderer = new Renderer(new GlRenderDevice((GlfwWindowToolkit.GlfwWindowHandle) window));
        window.show();

        // Create default pipeline
        var pipeline = renderer.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, VS),
                new ShaderSource(ShaderStage.FRAGMENT, FS)));
        renderer.setDefaultPipeline(pipeline);

        // Create cube mesh
        float s = 0.5f;
        float[] vertices = cubeVertices(s);
        int[] indices = cubeIndices();
        var format = VertexFormat.of(
                new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0),
                new VertexAttribute(1, 3, ComponentType.FLOAT, false, 3 * Float.BYTES));
        var cubeMesh = renderer.createMesh(vertices, indices, format);

        // Create entities via scene — no manual GPU resource management
        var scene = renderer.scene();
        var root = scene.createEntity();
        var cube1 = scene.createEntity();
        var cube2 = scene.createEntity();
        var cube3 = scene.createEntity();
        scene.setParent(cube1, root);
        scene.setParent(cube2, root);
        scene.setParent(cube3, root);
        renderer.setMesh(cube1, cubeMesh);
        renderer.setMesh(cube2, cubeMesh);
        renderer.setMesh(cube3, cubeMesh);

        // Set up camera
        var camera = renderer.createCamera();
        camera.lookAt(new Vec3(0f, 3f, 7f), Vec3.ZERO, Vec3.UNIT_Y);

        long startTime = System.nanoTime();

        while (window.isOpen()) {
            toolkit.pollEvents();
            float time = (float) ((System.nanoTime() - startTime) / 1_000_000_000.0);
            float aspect = (float) window.width() / Math.max(window.height(), 1);

            // Update scene transforms
            scene.setLocalTransform(root, Mat4.rotationY(time * 0.3f));
            scene.setLocalTransform(cube1, Mat4.translation(-2f, 0f, 0f).mul(Mat4.rotationX(time)));
            scene.setLocalTransform(cube2, Mat4.translation(0f, (float) Math.sin(time) * 1.5f, 0f).mul(Mat4.rotationZ(time * 1.5f)));
            scene.setLocalTransform(cube3, Mat4.translation(2f, 0f, 0f).mul(Mat4.rotationY(time * 2f)));

            // Update camera
            camera.setPerspective((float) Math.toRadians(60), aspect, 0.1f, 100f);
            renderer.setViewport(window.width(), window.height());

            // One call renders everything
            renderer.renderFrame();
        }

        renderer.close();
        toolkit.close();
    }

    static float[] cubeVertices(float s) {
        float[][] faces = {
                {-s,-s,s, s,-s,s, s,s,s, -s,s,s},     // front
                {s,-s,-s, -s,-s,-s, -s,s,-s, s,s,-s},   // back
                {-s,s,s, s,s,s, s,s,-s, -s,s,-s},       // top
                {-s,-s,-s, s,-s,-s, s,-s,s, -s,-s,s},   // bottom
                {s,-s,s, s,-s,-s, s,s,-s, s,s,s},       // right
                {-s,-s,-s, -s,-s,s, -s,s,s, -s,s,-s},   // left
        };
        float[][] colors = {{.9f,.2f,.2f},{.2f,.9f,.2f},{.2f,.2f,.9f},{.9f,.9f,.2f},{.2f,.9f,.9f},{.9f,.2f,.9f}};
        float[] verts = new float[24 * 6]; // 24 vertices * 6 floats (pos+color)
        int vi = 0;
        for (int f = 0; f < 6; f++) {
            for (int v = 0; v < 4; v++) {
                verts[vi++] = faces[f][v*3];
                verts[vi++] = faces[f][v*3+1];
                verts[vi++] = faces[f][v*3+2];
                verts[vi++] = colors[f][0];
                verts[vi++] = colors[f][1];
                verts[vi++] = colors[f][2];
            }
        }
        return verts;
    }

    static int[] cubeIndices() {
        int[] idx = new int[36];
        for (int f = 0; f < 6; f++) {
            int b = f * 4;
            idx[f*6]=b; idx[f*6+1]=b+1; idx[f*6+2]=b+2;
            idx[f*6+3]=b; idx[f*6+4]=b+2; idx[f*6+5]=b+3;
        }
        return idx;
    }
}
