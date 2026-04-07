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

    public static final RenderTestScene MIXED_RENDER_STATES = engine -> {
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

    public static final RenderTestScene DEPTH_WRITE_OFF = engine -> {
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

    public static final RenderTestScene BLEND_ADDITIVE = engine -> {
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

    public static final RenderTestScene FRONT_FACE_CW = engine -> {
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

    public static final RenderTestScene ALL_BLEND_MODES = new RenderTestScene() {
        @Override
        public SceneConfig config() {
            return SceneConfig.defaults()
                    .withKnownLimitation("vulkan",
                            "Vulkan multiply blend produces brighter RGB and alpha=0 — "
                            + "suspected swapchain alpha or blend state issue");
        }

        @Override
        public void setup(Engine engine) {
            var renderer = engine.renderer();
            var scene = engine.scene();
            var cam = renderer.createCamera();
            cam.lookAt(new Vec3(0, 0, 8), Vec3.ZERO, Vec3.UNIT_Y);
            cam.setPerspective((float) Math.toRadians(70), 256f / 256f, 0.1f, 100f);
            renderer.setActiveCamera(cam);

            // White background quad — all blend cubes render on top of this
            var bg = scene.createEntity();
            bg.add(PrimitiveMeshes.quad());
            bg.add(MaterialData.unlit(new Vec3(1.0f, 1.0f, 1.0f)));
            bg.add(Transform.at(0, 0, -1).withScale(new Vec3(20, 20, 1)));

            // Alpha blend (left): red cube — opaque alpha over white bg = red
            var alpha = scene.createEntity();
            alpha.add(PrimitiveMeshes.cube());
            alpha.add(MaterialData.unlit(new Vec3(0.9f, 0.0f, 0.0f))
                    .withRenderState(RenderState.BLEND_MODE, BlendMode.ALPHA)
                    .withRenderState(RenderState.DEPTH_WRITE, false));
            alpha.add(Transform.at(-3, 0, 0));

            // Additive blend (center): green cube — green + white bg = bright/saturated
            var additive = scene.createEntity();
            additive.add(PrimitiveMeshes.cube());
            additive.add(MaterialData.unlit(new Vec3(0.0f, 0.8f, 0.0f))
                    .withRenderState(RenderState.BLEND_MODE, BlendMode.ADDITIVE)
                    .withRenderState(RenderState.DEPTH_WRITE, false));
            additive.add(Transform.IDENTITY);

            // Multiply blend (right): colored cube — multiply with white bg = original color preserved
            var multiply = scene.createEntity();
            multiply.add(PrimitiveMeshes.cube());
            multiply.add(MaterialData.unlit(new Vec3(0.5f, 0.5f, 1.0f))
                    .withRenderState(RenderState.BLEND_MODE, BlendMode.MULTIPLY)
                    .withRenderState(RenderState.DEPTH_WRITE, false));
            multiply.add(Transform.at(3, 0, 0));
        }
    };
}
