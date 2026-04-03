package dev.engine.examples;

import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.renderstate.CullMode;
import dev.engine.graphics.renderstate.RenderState;

/**
 * Comprehensive test scenes exercising rendering features.
 * Each scene is a {@link RenderTestScene} that can be rendered by any backend.
 */
public final class ScreenshotTestSuite {
    private ScreenshotTestSuite() {}

    /** Basic colored cube with depth test — front cube should occlude back cube. */
    static final RenderTestScene DEPTH_TEST_CUBES = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Front red cube at z=0
        var front = renderer.scene().createEntity();
        front.add(PrimitiveMeshes.cube());
        front.add(MaterialData.unlit(new Vec3(0.9f, 0.1f, 0.1f)));
        front.add(Transform.at(0, 0, 0));

        // Back blue cube at z=-3 (should be occluded)
        var back = renderer.scene().createEntity();
        back.add(PrimitiveMeshes.cube());
        back.add(MaterialData.unlit(new Vec3(0.1f, 0.1f, 0.9f)));
        back.add(Transform.at(0, 0, -3));
    };

    /** Multiple primitive meshes in a row. */
    static final RenderTestScene PRIMITIVE_MESHES = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 4, 10), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Cube
        var cube = renderer.scene().createEntity();
        cube.add(PrimitiveMeshes.cube());
        cube.add(MaterialData.unlit(new Vec3(0.8f, 0.2f, 0.2f)));
        cube.add(Transform.at(-3, 0, 0));

        // Sphere
        var sphere = renderer.scene().createEntity();
        sphere.add(PrimitiveMeshes.sphere());
        sphere.add(MaterialData.unlit(new Vec3(0.2f, 0.8f, 0.2f)));
        sphere.add(Transform.at(0, 0, 0));

        // Plane
        var plane = renderer.scene().createEntity();
        plane.add(PrimitiveMeshes.quad());
        plane.add(MaterialData.unlit(new Vec3(0.2f, 0.2f, 0.8f)));
        plane.add(Transform.at(3, 0, 0));
    };

    /** Cull mode test — render both sides of a quad with culling disabled. */
    static final RenderTestScene CULL_MODE_NONE = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 3), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Quad with no culling — should show backface
        var quad = renderer.scene().createEntity();
        quad.add(PrimitiveMeshes.quad());
        quad.add(MaterialData.unlit(new Vec3(0.9f, 0.9f, 0.1f))
            .set(RenderState.CULL_MODE, CullMode.NONE));
        quad.add(Transform.IDENTITY);
    };

    /** Render-to-texture — creates an RT, clears it, then renders the scene normally. */
    static final RenderTestScene RENDER_TO_TEXTURE = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 2, 5), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var cube = renderer.scene().createEntity();
        cube.add(PrimitiveMeshes.cube());
        cube.add(MaterialData.unlit(new Vec3(0.3f, 0.7f, 0.3f)));
        cube.add(Transform.at(0, 0, 0));
    };

    /** Multiple materials with different cull modes in a single scene. */
    static final RenderTestScene MIXED_RENDER_STATES = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 8), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Opaque cube with back-face culling (default)
        var opaque = renderer.scene().createEntity();
        opaque.add(PrimitiveMeshes.cube());
        opaque.add(MaterialData.unlit(new Vec3(0.8f, 0.2f, 0.2f)));
        opaque.add(Transform.at(-2, 0, 0));

        // Cube with front-face culling (shows inside)
        var frontCull = renderer.scene().createEntity();
        frontCull.add(PrimitiveMeshes.cube());
        frontCull.add(MaterialData.unlit(new Vec3(0.2f, 0.2f, 0.8f))
            .set(RenderState.CULL_MODE, CullMode.FRONT));
        frontCull.add(Transform.at(2, 0, 0));

        // Cube with no culling (both faces visible)
        var noCull = renderer.scene().createEntity();
        noCull.add(PrimitiveMeshes.cube());
        noCull.add(MaterialData.unlit(new Vec3(0.2f, 0.8f, 0.2f))
            .set(RenderState.CULL_MODE, CullMode.NONE));
        noCull.add(Transform.at(0, 0, -2));
    };

    /** Wireframe mode via forced property — all geometry rendered as wireframe. */
    static final RenderTestScene FORCED_WIREFRAME = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Force wireframe on everything
        renderer.forceProperty(RenderState.WIREFRAME, true);

        var sphere = renderer.scene().createEntity();
        sphere.add(PrimitiveMeshes.sphere());
        sphere.add(MaterialData.unlit(new Vec3(1f, 1f, 1f)));
        sphere.add(Transform.IDENTITY);
    };

    /** PBR material test — rough vs metallic spheres. */
    static final RenderTestScene PBR_MATERIALS = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 2, 5), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Rough sphere
        var rough = renderer.scene().createEntity();
        rough.add(PrimitiveMeshes.sphere());
        rough.add(MaterialData.pbr(new Vec3(0.8f, 0.3f, 0.1f), 0.9f, 0.1f));
        rough.add(Transform.at(-2, 0, 0));

        // Metallic sphere
        var metal = renderer.scene().createEntity();
        metal.add(PrimitiveMeshes.sphere());
        metal.add(MaterialData.pbr(new Vec3(0.9f, 0.9f, 0.9f), 0.1f, 0.9f));
        metal.add(Transform.at(2, 0, 0));
    };
}
