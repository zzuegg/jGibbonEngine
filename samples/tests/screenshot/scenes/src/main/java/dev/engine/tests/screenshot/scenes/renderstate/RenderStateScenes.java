package dev.engine.tests.screenshot.scenes.renderstate;

import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.renderstate.BlendMode;
import dev.engine.graphics.renderstate.CullMode;
import dev.engine.graphics.renderstate.FrontFace;
import dev.engine.graphics.renderstate.RenderState;
import dev.engine.tests.screenshot.scenes.RenderTestScene;
import dev.engine.tests.screenshot.scenes.SceneConfig;
import dev.engine.tests.screenshot.scenes.Tolerance;
import dev.engine.graphics.common.engine.Engine;

public class RenderStateScenes {

    static final RenderTestScene MIXED_RENDER_STATES = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 8), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 256f / 256f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var opaque = scene.createEntity();
        opaque.add(PrimitiveMeshes.cube());
        opaque.add(MaterialData.unlit(new Vec3(0.8f, 0.2f, 0.2f)));
        opaque.add(Transform.at(-2, 0, 0));

        var frontCull = scene.createEntity();
        frontCull.add(PrimitiveMeshes.cube());
        frontCull.add(MaterialData.unlit(new Vec3(0.2f, 0.8f, 0.2f))
                .withRenderState(RenderState.CULL_MODE, CullMode.FRONT));
        frontCull.add(Transform.IDENTITY);

        var noCull = scene.createEntity();
        noCull.add(PrimitiveMeshes.cube());
        noCull.add(MaterialData.unlit(new Vec3(0.2f, 0.2f, 0.8f))
                .withRenderState(RenderState.CULL_MODE, CullMode.NONE));
        noCull.add(Transform.at(2, 0, 0));
    };

    static final RenderTestScene DEPTH_WRITE_OFF = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 256f / 256f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var back = scene.createEntity();
        back.add(PrimitiveMeshes.cube());
        back.add(MaterialData.unlit(new Vec3(0.2f, 0.2f, 0.9f)));
        back.add(Transform.at(0, 0, -1));

        var front = scene.createEntity();
        front.add(PrimitiveMeshes.cube());
        front.add(MaterialData.unlit(new Vec3(0.9f, 0.2f, 0.2f))
                .withRenderState(RenderState.DEPTH_WRITE, false));
        front.add(Transform.at(0, 0, 1));
    };

    static final RenderTestScene BLEND_ADDITIVE = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 256f / 256f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var back = scene.createEntity();
        back.add(PrimitiveMeshes.cube());
        back.add(MaterialData.unlit(new Vec3(0.5f, 0.0f, 0.0f)));
        back.add(Transform.at(0, 0, -1));

        var front = scene.createEntity();
        front.add(PrimitiveMeshes.cube());
        front.add(MaterialData.unlit(new Vec3(0.0f, 0.5f, 0.0f))
                .withRenderState(RenderState.BLEND_MODE, BlendMode.ADDITIVE));
        front.add(Transform.at(0.3f, 0, 1));
    };

    static final RenderTestScene FRONT_FACE_CW = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 256f / 256f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var cube = scene.createEntity();
        cube.add(PrimitiveMeshes.cube());
        cube.add(MaterialData.unlit(new Vec3(0.8f, 0.4f, 0.1f))
                .withRenderState(RenderState.FRONT_FACE, FrontFace.CW));
        cube.add(Transform.IDENTITY);
    };

    // BUG: Vulkan multiply blend produces different results (brighter RGB, alpha=0).
    // Investigation: GL/WG agree on dark result (3,3,10,255), VK gets (61,61,123,0).
    // Likely a draw order or blend state issue in the Vulkan backend — the multiply
    // cube may be blending against the wrong dst (clear color instead of background quad).
    // See also: Vulkan swapchain alpha is undefined with opaque compositing.
    static final RenderTestScene ALL_BLEND_MODES = new RenderTestScene() {
        @Override
        public SceneConfig config() {
            return SceneConfig.defaults()
                    .withKnownLimitation("vulkan",
                            "Vulkan multiply blend produces wrong result — likely draw order "
                            + "or blend state bug (alpha=0, brighter RGB than GL/WebGPU)");
        }

        @Override
        public void setup(Engine engine) {
            var renderer = engine.renderer();
            var scene = engine.scene();
            var cam = renderer.createCamera();
            cam.lookAt(new Vec3(0, 3, 10), Vec3.ZERO, Vec3.UNIT_Y);
            cam.setPerspective((float) Math.toRadians(60), 256f / 256f, 0.1f, 100f);
            renderer.setActiveCamera(cam);

            var bg = scene.createEntity();
            bg.add(PrimitiveMeshes.quad());
            bg.add(MaterialData.unlit(new Vec3(0.3f, 0.3f, 0.3f)));
            bg.add(Transform.at(0, 0, -2).withScale(5f));

            var alpha = scene.createEntity();
            alpha.add(PrimitiveMeshes.cube());
            alpha.add(MaterialData.unlit(new Vec3(1.0f, 0.0f, 0.0f))
                    .withRenderState(RenderState.BLEND_MODE, BlendMode.ALPHA));
            alpha.add(Transform.at(-3, 0, 0));

            var additive = scene.createEntity();
            additive.add(PrimitiveMeshes.cube());
            additive.add(MaterialData.unlit(new Vec3(0.0f, 1.0f, 0.0f))
                    .withRenderState(RenderState.BLEND_MODE, BlendMode.ADDITIVE));
            additive.add(Transform.IDENTITY);

            var multiply = scene.createEntity();
            multiply.add(PrimitiveMeshes.cube());
            multiply.add(MaterialData.unlit(new Vec3(0.5f, 0.5f, 1.0f))
                    .withRenderState(RenderState.BLEND_MODE, BlendMode.MULTIPLY));
            multiply.add(Transform.at(3, 0, 0));
        }
    };
}
