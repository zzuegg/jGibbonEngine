package dev.engine.examples;

import dev.engine.core.handle.Handle;
import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.EntityTag;
import dev.engine.core.scene.HierarchicalScene;
import dev.engine.graphics.common.engine.BaseApplication;
import dev.engine.graphics.common.engine.EngineConfig;
import dev.engine.graphics.opengl.OpenGlBackend;
import dev.engine.graphics.vertex.ComponentType;
import dev.engine.graphics.vertex.VertexAttribute;
import dev.engine.graphics.vertex.VertexFormat;

/**
 * The simplest possible game using BaseApplication.
 * All boilerplate is gone — just init, update, done.
 */
public class MyGame extends BaseApplication {

    private Handle<EntityTag> root, cube1, cube2, cube3;

    @Override
    protected void init() {
        // Create mesh
        var format = VertexFormat.of(
                new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0),
                new VertexAttribute(1, 3, ComponentType.FLOAT, false, 12));
        var cubeMesh = renderer().createMesh(
                HighLevelSceneExample.cubeVertices(0.5f),
                HighLevelSceneExample.cubeIndices(), format);

        // Create entities
        var scene = (HierarchicalScene) scene();
        root = scene.createEntity();
        cube1 = scene.createEntity(); scene.setParent(cube1, root);
        cube2 = scene.createEntity(); scene.setParent(cube2, root);
        cube3 = scene.createEntity(); scene.setParent(cube3, root);

        renderer().setMesh(cube1, cubeMesh);
        renderer().setMesh(cube2, cubeMesh);
        renderer().setMesh(cube3, cubeMesh);

        // Camera
        camera().lookAt(new Vec3(0, 3, 7), Vec3.ZERO, Vec3.UNIT_Y);
    }

    @Override
    protected void update(float deltaTime) {
        float t = (float) time();
        float aspect = (float) window().width() / Math.max(window().height(), 1);
        camera().setPerspective((float) Math.toRadians(60), aspect, 0.1f, 100f);

        scene().setLocalTransform(root, Mat4.rotationY(t * 0.3f));
        scene().setLocalTransform(cube1, Mat4.translation(-2, 0, 0).mul(Mat4.rotationX(t)));
        scene().setLocalTransform(cube2, Mat4.translation(0, (float) Math.sin(t) * 1.5f, 0).mul(Mat4.rotationZ(t * 1.5f)));
        scene().setLocalTransform(cube3, Mat4.translation(2, 0, 0).mul(Mat4.rotationY(t * 2)));
    }

    public static void main(String[] args) {
        var config = EngineConfig.builder()
                .windowTitle("My Game")
                .windowSize(1024, 768)
                .build();

        new MyGame().launch(config, OpenGlBackend.factory());
    }
}
