package dev.engine.examples;

import dev.engine.core.math.Vec3;
import dev.engine.core.scene.Entity;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.engine.BaseApplication;
import dev.engine.graphics.common.engine.EngineConfig;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.opengl.OpenGlBackend;

public class MyGame extends BaseApplication {

    private Entity root, cube1, cube2, cube3;

    @Override
    protected void init() {
        var cube = PrimitiveMeshes.cube();

        root = scene().createEntity();
        root.add(Transform.IDENTITY);

        cube1 = scene().createEntity();
        cube1.setParent(root);
        cube1.add(cube);
        cube1.add(Transform.at(-2, 0, 0));

        cube2 = scene().createEntity();
        cube2.setParent(root);
        cube2.add(cube);
        cube2.add(Transform.IDENTITY);

        cube3 = scene().createEntity();
        cube3.setParent(root);
        cube3.add(cube);
        cube3.add(Transform.at(2, 0, 0));

        camera().lookAt(new Vec3(0, 3, 7), Vec3.ZERO, Vec3.UNIT_Y);
    }

    @Override
    protected void update(float dt) {
        float t = (float) time();
        float aspect = (float) window().width() / Math.max(window().height(), 1);
        camera().setPerspective((float) Math.toRadians(60), aspect, 0.1f, 100f);

        root.update(Transform.class, tr -> tr.rotatedY(dt * 0.3f));
        cube1.update(Transform.class, tr -> tr.withPosition(-2, 0, 0).rotatedX(dt));
        cube2.update(Transform.class, tr -> tr.withPosition(0, (float) Math.sin(t) * 1.5f, 0).rotatedZ(dt * 1.5f));
        cube3.update(Transform.class, tr -> tr.withPosition(2, 0, 0).rotatedY(dt * 2));
    }

    public static void main(String[] args) {
        new MyGame().launch(
                EngineConfig.builder().windowTitle("My Game").windowSize(1024, 768).build(),
                OpenGlBackend.factory());
    }
}
