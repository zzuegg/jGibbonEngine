package dev.engine.tests.screenshot.scenes.ui;

import dev.engine.core.module.AbstractModule;
import dev.engine.core.module.Time;
import dev.engine.graphics.common.engine.Engine;
import dev.engine.tests.screenshot.RenderTestScene;
import dev.engine.tests.screenshot.Tolerance;
import dev.engine.ui.NkColor;
import dev.engine.ui.NkContext;

/**
 * Screenshot tests for the debug UI overlay.
 */
public class UiScenes {

    // Loose tolerance — UI rendering differs between GL/VK due to color channel handling
    public static final Tolerance DEFAULT_TOLERANCE = Tolerance.tight();

    /**
     * A UI window with label and button rendered over an empty scene.
     */
    public static final RenderTestScene DEBUG_UI_WINDOW = engine -> {
        var renderer = engine.renderer();
        var cam = renderer.createCamera();
        cam.lookAt(new dev.engine.core.math.Vec3(0, 3, 6),
                dev.engine.core.math.Vec3.ZERO, dev.engine.core.math.Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 256f / 256f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        engine.modules().add(new AbstractModule<>() {
            @Override
            protected void doUpdate(Time time) {
                NkContext ui = engine.debugUi();
                ui.style().windowBackground = NkColor.rgba(200, 200, 200, 255);
                ui.style().headerBackground = NkColor.rgba(100, 150, 200, 255);
                ui.style().headerText = NkColor.rgba(0, 0, 0, 255);
                ui.style().labelText = NkColor.rgba(0, 0, 0, 255);
                ui.style().buttonNormal = NkColor.rgba(80, 120, 180, 255);
                ui.style().buttonText = NkColor.rgba(255, 255, 255, 255);

                if (ui.begin("Test Panel", 10, 10, 200, 150,
                        NkContext.WINDOW_BORDER | NkContext.WINDOW_TITLE)) {
                    ui.layoutRowDynamic(25, 1);
                    ui.label("Hello UI");
                    ui.layoutRowDynamic(25, 1);
                    ui.button("Click Me");
                }
                ui.end();
            }
        });
    };
}
