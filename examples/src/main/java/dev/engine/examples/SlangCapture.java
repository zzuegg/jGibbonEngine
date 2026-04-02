package dev.engine.examples;

import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.HierarchicalScene;
import dev.engine.graphics.common.Renderer;
import dev.engine.core.material.MaterialType;
import dev.engine.graphics.opengl.GlRenderDevice;
import dev.engine.graphics.opengl.GlfwWindowToolkit;
import dev.engine.core.mesh.ComponentType;
import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.graphics.window.WindowDescriptor;

public class SlangCapture {
    public static void main(String[] args) throws Exception {
        var toolkit = new GlfwWindowToolkit();
        var window = toolkit.createWindow(new WindowDescriptor("Capture", 800, 600));
        var renderer = new Renderer(new GlRenderDevice((GlfwWindowToolkit.GlfwWindowHandle) window));

        var format = VertexFormat.of(
                new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0),
                new VertexAttribute(1, 3, ComponentType.FLOAT, false, 3 * Float.BYTES));
        var cubeMesh = renderer.createMesh(
                HighLevelSceneExample.cubeVertices(0.5f),
                HighLevelSceneExample.cubeIndices(), format);

        var scene = (HierarchicalScene) renderer.scene();
        var root = scene.createEntity();
        var c1 = scene.createEntity(); c1.setParent(root);
        var c2 = scene.createEntity(); c2.setParent(root);
        var c3 = scene.createEntity(); c3.setParent(root);
        scene.setMesh(c1, cubeMesh);
        scene.setMesh(c2, cubeMesh);
        scene.setMesh(c3, cubeMesh);

        var mat = renderer.createMaterial(MaterialType.UNLIT);
        scene.setMaterial(c1, mat);
        scene.setMaterial(c2, mat);
        scene.setMaterial(c3, mat);

        var camera = renderer.createCamera();
        camera.lookAt(new Vec3(0f, 3f, 7f), Vec3.ZERO, Vec3.UNIT_Y);
        camera.setPerspective((float) Math.toRadians(60), 800f / 600f, 0.1f, 100f);
        renderer.setViewport(800, 600);

        for (int frame = 0; frame < 4; frame++) {
            float time = frame * 1.0f;
            scene.setLocalTransform(root, Mat4.rotationY(time * 0.3f));
            scene.setLocalTransform(c1, Mat4.translation(-2f, 0f, 0f).mul(Mat4.rotationX(time)));
            scene.setLocalTransform(c2, Mat4.translation(0f, (float) Math.sin(time) * 1.5f, 0f).mul(Mat4.rotationZ(time * 1.5f)));
            scene.setLocalTransform(c3, Mat4.translation(2f, 0f, 0f).mul(Mat4.rotationY(time * 2f)));
            renderer.renderFrame();
            ScreenshotUtil.capture(800, 600, "/tmp/engine_slang_t" + frame + ".png");
            System.out.println("Slang t=" + time + " → /tmp/engine_slang_t" + frame + ".png");
        }
        renderer.close();
        toolkit.close();
    }
}
