package dev.engine.tests.screenshot.scenes.renderstate;

import dev.engine.core.Discoverable;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.renderstate.BlendMode;
import dev.engine.graphics.renderstate.CullMode;
import dev.engine.graphics.renderstate.RenderState;
import dev.engine.tests.screenshot.scenes.RenderTestScene;

/**
 * Tests that render state is correctly applied per-entity without leaking to other entities.
 */
@Discoverable
public class PerEntityRenderStateScenes {

    /** One entity with depth disabled, another with depth enabled — tests state isolation. */
    public static final RenderTestScene PER_ENTITY_DEPTH = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Far cube — should be visible through the transparent front cube
        var far = scene.createEntity();
        far.add(PrimitiveMeshes.cube());
        far.add(MaterialData.unlit(new Vec3(0.2f, 0.2f, 0.9f)));
        far.add(Transform.at(-1.5f, 0, -2));

        // Near cube with depth write off — should NOT occlude the far cube
        var nearNoDepth = scene.createEntity();
        nearNoDepth.add(PrimitiveMeshes.cube());
        nearNoDepth.add(MaterialData.unlit(new Vec3(0.9f, 0.2f, 0.2f))
                .withRenderState(RenderState.DEPTH_WRITE, false));
        nearNoDepth.add(Transform.at(-1.5f, 0, 0));

        // Far cube on the right
        var farRight = scene.createEntity();
        farRight.add(PrimitiveMeshes.cube());
        farRight.add(MaterialData.unlit(new Vec3(0.2f, 0.9f, 0.2f)));
        farRight.add(Transform.at(1.5f, 0, -2));

        // Near cube with normal depth — SHOULD occlude the far right cube
        var nearNormal = scene.createEntity();
        nearNormal.add(PrimitiveMeshes.cube());
        nearNormal.add(MaterialData.unlit(new Vec3(0.9f, 0.9f, 0.2f)));
        nearNormal.add(Transform.at(1.5f, 0, 0));
    };

    /** One entity with back-face culling off, one with front-face culling —
     *  tests per-entity cull mode isolation. */
    public static final RenderTestScene PER_ENTITY_CULL = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Default culling (back)
        var normal = scene.createEntity();
        normal.add(PrimitiveMeshes.cube());
        normal.add(MaterialData.unlit(new Vec3(0.8f, 0.2f, 0.2f)));
        normal.add(Transform.at(-2, 0, 0));

        // No culling — both faces visible
        var noCull = scene.createEntity();
        noCull.add(PrimitiveMeshes.cube());
        noCull.add(MaterialData.unlit(new Vec3(0.2f, 0.8f, 0.2f))
                .withRenderState(RenderState.CULL_MODE, CullMode.NONE));
        noCull.add(Transform.IDENTITY);

        // Front-face culling — inside visible
        var frontCull = scene.createEntity();
        frontCull.add(PrimitiveMeshes.cube());
        frontCull.add(MaterialData.unlit(new Vec3(0.2f, 0.2f, 0.8f))
                .withRenderState(RenderState.CULL_MODE, CullMode.FRONT));
        frontCull.add(Transform.at(2, 0, 0));
    };

    /** Blend mode on one entity does not affect the next entity rendered. */
    public static final RenderTestScene PER_ENTITY_BLEND = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Background
        var bg = scene.createEntity();
        bg.add(PrimitiveMeshes.quad());
        bg.add(MaterialData.unlit(new Vec3(0.4f, 0.4f, 0.4f)));
        bg.add(Transform.at(0, 0, -3).withScale(8f));

        // Additive blend entity
        var additive = scene.createEntity();
        additive.add(PrimitiveMeshes.cube());
        additive.add(MaterialData.unlit(new Vec3(0.0f, 0.5f, 0.0f))
                .withRenderState(RenderState.BLEND_MODE, BlendMode.ADDITIVE));
        additive.add(Transform.at(-1.5f, 0, 0));

        // Opaque entity next — should NOT inherit additive blend
        var opaque = scene.createEntity();
        opaque.add(PrimitiveMeshes.cube());
        opaque.add(MaterialData.unlit(new Vec3(0.8f, 0.2f, 0.2f)));
        opaque.add(Transform.at(1.5f, 0, 0));
    };
}
