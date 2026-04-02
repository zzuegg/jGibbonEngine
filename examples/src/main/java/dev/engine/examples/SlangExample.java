package dev.engine.examples;

import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;
import dev.engine.graphics.common.Renderer;
import dev.engine.graphics.common.material.Material;
import dev.engine.graphics.common.material.MaterialType;
import dev.engine.graphics.opengl.GlRenderDevice;
import dev.engine.graphics.opengl.GlfwWindowToolkit;
import dev.engine.graphics.vertex.ComponentType;
import dev.engine.graphics.vertex.VertexAttribute;
import dev.engine.graphics.vertex.VertexFormat;
import dev.engine.graphics.window.WindowDescriptor;

/**
 * Scene rendered entirely through Slang — no raw GLSL anywhere.
 * Uses the high-level Renderer with material-driven shader selection.
 */
public class SlangExample {

    public static void main(String[] args) {
        var toolkit = new GlfwWindowToolkit();
        var window = toolkit.createWindow(new WindowDescriptor("Engine - Slang Shaders", 1024, 768));
        var renderer = new Renderer(new GlRenderDevice((GlfwWindowToolkit.GlfwWindowHandle) window));
        window.show();

        System.out.println("Backend: " + renderer.backendName());

        // Create cube mesh
        var format = VertexFormat.of(
                new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0),
                new VertexAttribute(1, 3, ComponentType.FLOAT, false, 3 * Float.BYTES));
        var cubeMesh = renderer.createMesh(
                HighLevelSceneExample.cubeVertices(0.5f),
                HighLevelSceneExample.cubeIndices(), format);

        // Create scene entities with unlit material (compiled from Slang)
        var scene = renderer.scene();
        var root = scene.createEntity();
        var cube1 = scene.createEntity(); scene.setParent(cube1, root);
        var cube2 = scene.createEntity(); scene.setParent(cube2, root);
        var cube3 = scene.createEntity(); scene.setParent(cube3, root);

        // Assign meshes and materials
        renderer.setMesh(cube1, cubeMesh);
        renderer.setMesh(cube2, cubeMesh);
        renderer.setMesh(cube3, cubeMesh);

        var mat = renderer.createMaterial(MaterialType.UNLIT);
        renderer.setMaterial(cube1, mat);
        renderer.setMaterial(cube2, mat);
        renderer.setMaterial(cube3, mat);

        var camera = renderer.createCamera();
        camera.lookAt(new Vec3(0f, 3f, 7f), Vec3.ZERO, Vec3.UNIT_Y);

        long startTime = System.nanoTime();

        while (window.isOpen()) {
            toolkit.pollEvents();
            float time = (float) ((System.nanoTime() - startTime) / 1_000_000_000.0);
            float aspect = (float) window.width() / Math.max(window.height(), 1);

            scene.setLocalTransform(root, Mat4.rotationY(time * 0.3f));
            scene.setLocalTransform(cube1, Mat4.translation(-2f, 0f, 0f).mul(Mat4.rotationX(time)));
            scene.setLocalTransform(cube2, Mat4.translation(0f, (float) Math.sin(time) * 1.5f, 0f).mul(Mat4.rotationZ(time * 1.5f)));
            scene.setLocalTransform(cube3, Mat4.translation(2f, 0f, 0f).mul(Mat4.rotationY(time * 2f)));

            camera.setPerspective((float) Math.toRadians(60), aspect, 0.1f, 100f);
            renderer.setViewport(window.width(), window.height());
            renderer.renderFrame();
        }

        renderer.close();
        toolkit.close();
    }
}
