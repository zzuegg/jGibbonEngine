package dev.engine.examples;

import dev.engine.core.handle.Handle;
import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.EntityTag;
import dev.engine.core.scene.HierarchicalScene;
import dev.engine.graphics.common.engine.BaseApplication;
import dev.engine.graphics.common.engine.EngineConfig;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.opengl.OpenGlBackend;

/**
 * The cleanest possible game. No GPU concepts, no renderer access, no boilerplate.
 */
public class MyGame extends BaseApplication {

    private Handle<EntityTag> root, cube1, cube2, cube3;

    @Override
    protected void init() {
        var scene = (HierarchicalScene) scene();
        var cube = PrimitiveMeshes.cube();

        root = scene.createEntity();
        cube1 = scene.createEntity(); scene.setParent(cube1, root);
        cube2 = scene.createEntity(); scene.setParent(cube2, root);
        cube3 = scene.createEntity(); scene.setParent(cube3, root);

        scene.setMesh(cube1, cube);
        scene.setMesh(cube2, cube);
        scene.setMesh(cube3, cube);

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
        new MyGame().launch(
                EngineConfig.builder().windowTitle("My Game").windowSize(1024, 768).build(),
                OpenGlBackend.factory());
    }
}
