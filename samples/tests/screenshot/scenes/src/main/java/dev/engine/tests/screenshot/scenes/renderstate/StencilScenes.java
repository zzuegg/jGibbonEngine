package dev.engine.tests.screenshot.scenes.renderstate;

import dev.engine.core.Discoverable;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.renderstate.CompareFunc;
import dev.engine.graphics.renderstate.RenderState;
import dev.engine.graphics.renderstate.StencilOp;
import dev.engine.tests.screenshot.scenes.RenderTestScene;

/**
 * Tests for stencil buffer operations.
 */
@Discoverable
public class StencilScenes {

    /** Small green quad writes to stencil, large blue quad only renders where stencil == 1. */
    public static final RenderTestScene STENCIL_MASKING = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 5), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // First pass: small green quad writes 1 to stencil buffer
        var stencilWriter = scene.createEntity();
        stencilWriter.add(PrimitiveMeshes.quad());
        stencilWriter.add(MaterialData.unlit(new Vec3(0.0f, 0.8f, 0.0f))
            .withRenderState(RenderState.STENCIL_TEST, true)
            .withRenderState(RenderState.STENCIL_FUNC, CompareFunc.ALWAYS)
            .withRenderState(RenderState.STENCIL_REF, 1)
            .withRenderState(RenderState.STENCIL_MASK, 0xFF)
            .withRenderState(RenderState.STENCIL_PASS, StencilOp.REPLACE)
            .withRenderState(RenderState.STENCIL_FAIL, StencilOp.KEEP)
            .withRenderState(RenderState.STENCIL_DEPTH_FAIL, StencilOp.KEEP));
        stencilWriter.add(Transform.at(0, 0, 0).withScale(new Vec3(0.5f, 0.5f, 1)));

        // Second pass: large blue quad only renders where stencil == 1
        var stencilReader = scene.createEntity();
        stencilReader.add(PrimitiveMeshes.quad());
        stencilReader.add(MaterialData.unlit(new Vec3(0.0f, 0.0f, 0.9f))
            .withRenderState(RenderState.STENCIL_TEST, true)
            .withRenderState(RenderState.STENCIL_FUNC, CompareFunc.EQUAL)
            .withRenderState(RenderState.STENCIL_REF, 1)
            .withRenderState(RenderState.STENCIL_MASK, 0xFF)
            .withRenderState(RenderState.STENCIL_PASS, StencilOp.KEEP)
            .withRenderState(RenderState.STENCIL_FAIL, StencilOp.KEEP)
            .withRenderState(RenderState.STENCIL_DEPTH_FAIL, StencilOp.KEEP));
        stencilReader.add(Transform.at(0, 0, 0.1f).withScale(new Vec3(2, 2, 1)));
    };
}
