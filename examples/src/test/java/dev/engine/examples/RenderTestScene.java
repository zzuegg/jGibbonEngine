package dev.engine.examples;

import dev.engine.graphics.common.Renderer;

/**
 * A test scene that sets up entities, camera, and materials on a Renderer.
 * Implement this to define a new visual regression test scenario.
 *
 * <pre>{@code
 * RenderTestScene TWO_CUBES = renderer -> {
 *     var cube = renderer.scene().createEntity();
 *     cube.add(PrimitiveMeshes.cube());
 *     cube.add(MaterialData.unlit(new Vec3(1, 0, 0)));
 *     cube.add(Transform.at(0, 0, 0));
 *
 *     var cam = renderer.createCamera();
 *     cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
 *     cam.setPerspective(fov, aspect, 0.1f, 100f);
 *     renderer.setActiveCamera(cam);
 * };
 * }</pre>
 */
@FunctionalInterface
public interface RenderTestScene {

    /**
     * Sets up the scene. Called once before rendering.
     * The renderer's viewport is already set.
     */
    void setup(Renderer renderer, int width, int height);
}
