package dev.engine.tests.screenshot.scenes.input;

import dev.engine.core.input.*;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.module.AbstractModule;
import dev.engine.core.module.Time;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.engine.Engine;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.tests.screenshot.scenes.RenderTestScene;
import dev.engine.tests.screenshot.scenes.SceneConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests that verify input events affect rendering correctly.
 */
public class InputTestScenes {

    private static final DeviceId KB = new DeviceId(DeviceType.KEYBOARD, 0);
    private static final Modifiers NO_MODS = new Modifiers(0);

    /**
     * A cube that changes color from red to green when key E is pressed on frame 2.
     * Capture at frame 1 (red) and frame 3 (green).
     */
    static final RenderTestScene KEY_PRESS_CHANGES_COLOR = new RenderTestScene() {
        @Override
        public void setup(Engine engine) {
            var entity = engine.scene().createEntity();
            entity.add(PrimitiveMeshes.cube());
            entity.add(MaterialData.unlit(new Vec3(1, 0, 0))); // start red
            entity.add(Transform.IDENTITY);

            var cam = engine.renderer().createCamera();
            cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
            engine.renderer().setActiveCamera(cam);

            // Module that reacts to input events
            engine.modules().add(new AbstractModule<>() {
                @Override
                protected void doUpdate(Time time) {
                    for (var event : engine.inputEvents()) {
                        if (event instanceof InputEvent.KeyPressed kp && kp.keyCode() == KeyCode.E) {
                            entity.add(MaterialData.unlit(new Vec3(0, 1, 0))); // change to green
                        }
                    }
                }
            });
        }

        @Override
        public SceneConfig config() {
            return SceneConfig.defaults().withCaptureFrames(Set.of(1, 3));
        }

        @Override
        public Map<Integer, List<InputEvent>> inputScript() {
            var time = new Time(2, 1.0 / 60.0);
            return Map.of(
                2, List.of(new InputEvent.KeyPressed(time, KB, KeyCode.E, new ScanCode(18), NO_MODS))
            );
        }
    };
}
