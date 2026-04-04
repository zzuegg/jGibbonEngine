package dev.engine.examples;

import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;

/**
 * Shared test scene definitions. Each scene is used by both OpenGL and Vulkan tests.
 * Both backends verify against the same reference screenshot.
 *
 * <p>To add a new test scenario:
 * 1. Add a {@link RenderTestScene} constant here
 * 2. Add test methods in {@link OpenGlRenderTest} and {@link VulkanRenderTest}
 * 3. Generate reference: {@code new RenderTestHarness(256,256).saveReference(SCENE, "name")}
 */
final class CrossBackendScenes {

    private CrossBackendScenes() {}

    static final RenderTestScene TWO_CUBES_UNLIT = (renderer, scene, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
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

    static final RenderTestScene SINGLE_SPHERE_PBR = (renderer, scene, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 2, 5), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var sphere = scene.createEntity();
        sphere.add(PrimitiveMeshes.sphere());
        sphere.add(MaterialData.pbr(new Vec3(0.8f, 0.3f, 0.1f), 0.5f, 0.5f));
        sphere.add(Transform.IDENTITY);
    };
}
