package dev.engine.tests.screenshot;

import dev.engine.graphics.common.engine.Engine;

/**
 * Defines a visual test scene. Implement {@link #setup(Engine)} to create
 * entities, cameras, materials, and optionally register modules for animation.
 *
 * <p>To create a new test, just add a {@code static final RenderTestScene} field
 * to any class in the {@code scenes} package. It will be discovered automatically.
 *
 * <pre>{@code
 * // Static scene — single capture at frame 3
 * static final RenderTestScene MY_SCENE = engine -> {
 *     var cube = engine.scene().createEntity();
 *     cube.add(PrimitiveMeshes.cube());
 *     cube.add(MaterialData.unlit(new Vec3(1, 0, 0)));
 *     cube.add(Transform.IDENTITY);
 *
 *     var cam = engine.renderer().createCamera();
 *     cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
 *     engine.renderer().setActiveCamera(cam);
 * };
 *
 * // Animated scene — capture at multiple frames
 * static final RenderTestScene SPINNING = new RenderTestScene() {
 *     public void setup(Engine engine) {
 *         // ... create entities ...
 *         engine.modules().register(time -> {
 *             cube.update(Transform.class, t -> t.rotatedY(time.delta()));
 *         });
 *     }
 *     public int[] captureFrames() { return new int[]{0, 15, 30}; }
 * };
 * }</pre>
 */
@FunctionalInterface
public interface RenderTestScene {

    /** Sets up the scene using the full engine. */
    void setup(Engine engine);

    /** Frame indices at which to capture screenshots. Default: single capture at frame 3. */
    default int[] captureFrames() { return new int[]{3}; }
}
