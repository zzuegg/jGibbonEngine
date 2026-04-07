package dev.engine.tests.screenshot.scenes.basic;

import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.tests.screenshot.scenes.RenderTestScene;

/**
 * Tests camera projection and view transforms.
 */
public class CameraScenes {

    /** Close-up perspective — cube should fill most of the frame. */
    public static final RenderTestScene CLOSE_PERSPECTIVE = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 2), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var cube = scene.createEntity();
        cube.add(PrimitiveMeshes.cube());
        cube.add(MaterialData.unlit(new Vec3(0.8f, 0.3f, 0.1f)));
        cube.add(Transform.IDENTITY);
    };

    /** Top-down view — looking straight down the Y axis. */
    public static final RenderTestScene TOP_DOWN_VIEW = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 8, 0.01f), Vec3.ZERO, Vec3.UNIT_Z);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var cube = scene.createEntity();
        cube.add(PrimitiveMeshes.cube());
        cube.add(MaterialData.unlit(new Vec3(0.2f, 0.6f, 0.9f)));
        cube.add(Transform.IDENTITY);

        var plane = scene.createEntity();
        plane.add(PrimitiveMeshes.plane(4, 4));
        plane.add(MaterialData.unlit(new Vec3(0.3f, 0.3f, 0.3f)));
        plane.add(Transform.at(0, -0.5f, 0));
    };

    /** Wide FOV — strong perspective distortion. */
    public static final RenderTestScene WIDE_FOV = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 2, 4), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(120), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var cube1 = scene.createEntity();
        cube1.add(PrimitiveMeshes.cube());
        cube1.add(MaterialData.unlit(new Vec3(0.9f, 0.2f, 0.2f)));
        cube1.add(Transform.at(-3, 0, 0));

        var cube2 = scene.createEntity();
        cube2.add(PrimitiveMeshes.cube());
        cube2.add(MaterialData.unlit(new Vec3(0.2f, 0.9f, 0.2f)));
        cube2.add(Transform.IDENTITY);

        var cube3 = scene.createEntity();
        cube3.add(PrimitiveMeshes.cube());
        cube3.add(MaterialData.unlit(new Vec3(0.2f, 0.2f, 0.9f)));
        cube3.add(Transform.at(3, 0, 0));
    };
}
