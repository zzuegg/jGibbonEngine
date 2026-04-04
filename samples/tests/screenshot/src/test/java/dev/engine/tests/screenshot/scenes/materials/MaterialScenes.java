package dev.engine.tests.screenshot.scenes.materials;

import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.tests.screenshot.RenderTestScene;

public class MaterialScenes {

    static final RenderTestScene PBR_MATERIALS = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 2, 5), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 256f / 256f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var rough = scene.createEntity();
        rough.add(PrimitiveMeshes.sphere());
        rough.add(MaterialData.pbr(new Vec3(0.8f, 0.3f, 0.1f), 0.9f, 0.1f));
        rough.add(Transform.at(-1.5f, 0, 0));

        var metal = scene.createEntity();
        metal.add(PrimitiveMeshes.sphere());
        metal.add(MaterialData.pbr(new Vec3(0.8f, 0.8f, 0.8f), 0.1f, 0.9f));
        metal.add(Transform.at(1.5f, 0, 0));
    };

    static final RenderTestScene SINGLE_SPHERE_PBR = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 2, 5), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 256f / 256f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var sphere = scene.createEntity();
        sphere.add(PrimitiveMeshes.sphere());
        sphere.add(MaterialData.pbr(new Vec3(0.8f, 0.3f, 0.1f), 0.5f, 0.5f));
        sphere.add(Transform.IDENTITY);
    };

    static final RenderTestScene SHADER_SWITCHING = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 256f / 256f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var pbrSphere = scene.createEntity();
        pbrSphere.add(PrimitiveMeshes.sphere());
        pbrSphere.add(MaterialData.pbr(new Vec3(0.8f, 0.3f, 0.1f), 0.5f, 0.5f));
        pbrSphere.add(Transform.at(-2, 0, 0));

        var unlitCube = scene.createEntity();
        unlitCube.add(PrimitiveMeshes.cube());
        unlitCube.add(MaterialData.unlit(new Vec3(0.2f, 0.8f, 0.2f)));
        unlitCube.add(Transform.IDENTITY);

        var pbrSphere2 = scene.createEntity();
        pbrSphere2.add(PrimitiveMeshes.sphere());
        pbrSphere2.add(MaterialData.pbr(new Vec3(0.1f, 0.3f, 0.8f), 0.3f, 0.8f));
        pbrSphere2.add(Transform.at(2, 0, 0));
    };
}
