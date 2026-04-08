package dev.engine.tests.screenshot.scenes.renderstate;

import dev.engine.core.Discoverable;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.renderstate.RenderState;
import dev.engine.tests.screenshot.scenes.RenderTestScene;
import dev.engine.tests.screenshot.scenes.SceneConfig;
import dev.engine.graphics.common.engine.Engine;

/**
 * Tests for wireframe rendering.
 */
@Discoverable
public class WireframeScenes {

    /** Sphere rendered in wireframe mode via forced property. */
    public static final RenderTestScene FORCED_WIREFRAME = new RenderTestScene() {
        @Override
        public SceneConfig config() {
            return SceneConfig.defaults()
                    .withKnownLimitation("webgpu", "Wireframe not supported on WebGPU")
                    .withKnownLimitation("teavm-webgpu", "Wireframe not supported on WebGPU");
        }

        @Override
        public void setup(Engine engine) {
            var renderer = engine.renderer();
            var scene = engine.scene();
            var cam = renderer.createCamera();
            cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
            cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
            renderer.setActiveCamera(cam);

            renderer.forceProperty(RenderState.WIREFRAME, true);

            var sphere = scene.createEntity();
            sphere.add(PrimitiveMeshes.sphere());
            sphere.add(MaterialData.unlit(new Vec3(1f, 1f, 1f)));
            sphere.add(Transform.IDENTITY);
        }
    };
}
