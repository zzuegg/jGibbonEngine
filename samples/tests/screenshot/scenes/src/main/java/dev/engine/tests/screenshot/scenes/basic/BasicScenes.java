package dev.engine.tests.screenshot.scenes.basic;

import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.tests.screenshot.scenes.RenderTestScene;

public class BasicScenes {

    static final RenderTestScene DEPTH_TEST_CUBES = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 256f / 256f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var front = scene.createEntity();
        front.add(PrimitiveMeshes.cube());
        front.add(MaterialData.unlit(new Vec3(0.9f, 0.2f, 0.2f)));
        front.add(Transform.at(0, 0, 1));

        var back = scene.createEntity();
        back.add(PrimitiveMeshes.cube());
        back.add(MaterialData.unlit(new Vec3(0.2f, 0.2f, 0.9f)));
        back.add(Transform.at(0.5f, 0, -1));
    };

    static final RenderTestScene TWO_CUBES_UNLIT = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 256f / 256f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var cube1 = scene.createEntity();
        cube1.add(PrimitiveMeshes.cube());
        cube1.add(MaterialData.unlit(new Vec3(0.9f, 0.2f, 0.2f)));
        cube1.add(Transform.at(-1.5f, 0, 0));

        var cube2 = scene.createEntity();
        cube2.add(PrimitiveMeshes.cube());
        cube2.add(MaterialData.unlit(new Vec3(0.2f, 0.9f, 0.2f)));
        cube2.add(Transform.at(1.5f, 0, 0));
    };

    static final RenderTestScene PRIMITIVE_MESHES = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 8), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 256f / 256f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var cube = scene.createEntity();
        cube.add(PrimitiveMeshes.cube());
        cube.add(MaterialData.unlit(new Vec3(0.9f, 0.2f, 0.2f)));
        cube.add(Transform.at(-2, 0, 0));

        var sphere = scene.createEntity();
        sphere.add(PrimitiveMeshes.sphere());
        sphere.add(MaterialData.unlit(new Vec3(0.2f, 0.9f, 0.2f)));
        sphere.add(Transform.IDENTITY);

        var plane = scene.createEntity();
        plane.add(PrimitiveMeshes.plane(4, 4));
        plane.add(MaterialData.unlit(new Vec3(0.2f, 0.2f, 0.9f)));
        plane.add(Transform.at(2, 0, 0));
    };
}
