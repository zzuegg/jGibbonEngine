package dev.engine.tests.screenshot.scenes.renderstate;

import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.renderstate.CompareFunc;
import dev.engine.graphics.renderstate.RenderState;
import dev.engine.tests.screenshot.scenes.RenderTestScene;

/**
 * Tests for depth function configuration.
 */
public class DepthFuncScenes {

    /**
     * Depth function ALWAYS — all fragments pass regardless of depth.
     * The back blue cube is drawn last and overwrites the front red cube.
     */
    public static final RenderTestScene DEPTH_FUNC_ALWAYS = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        renderer.setDefault(RenderState.DEPTH_FUNC, CompareFunc.ALWAYS);

        // Front: red cube
        var front = scene.createEntity();
        front.add(PrimitiveMeshes.cube());
        front.add(MaterialData.unlit(new Vec3(0.9f, 0.1f, 0.1f)));
        front.add(Transform.at(0, 0, 0));

        // Back: blue cube (drawn after red, overwrites where they overlap)
        var back = scene.createEntity();
        back.add(PrimitiveMeshes.cube());
        back.add(MaterialData.unlit(new Vec3(0.1f, 0.1f, 0.9f)));
        back.add(Transform.at(0, 0, -3));
    };
}
