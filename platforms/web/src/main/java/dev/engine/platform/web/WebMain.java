package dev.engine.platform.web;

import dev.engine.core.input.InputEvent;
import dev.engine.core.input.KeyCode;
import dev.engine.ui.NkColor;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.engine.BaseApplication;
import dev.engine.graphics.common.engine.EngineConfig;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.webgpu.WebGpuConfig;
import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.providers.teavm.webgpu.TeaVmWgpuBindings;
import dev.engine.providers.teavm.webgpu.TeaVmWgpuInit;
import dev.engine.providers.teavm.windowing.CanvasWindowToolkit;
import org.teavm.jso.JSBody;


import java.util.List;

/**
 * Web entry point — renders a simple PBR scene using WebGPU in the browser.
 */
public class WebMain extends BaseApplication {

    public static void main(String[] args) {
        // Initialize discovery registries (loads NativeStruct companions etc.)
        dev.engine.core.Discovery.addRegistry(
                new dev.engine.graphics.shader.params.GeneratedDiscoveryRegistry());

        System.out.println("[WebMain] Initializing WebGPU...");
        int deviceId = TeaVmWgpuInit.initAsync();
        if (deviceId == 0) {
            System.err.println("[WebMain] Failed to initialize WebGPU");
            return;
        }
        System.out.println("[WebMain] WebGPU device: " + deviceId);

        var toolkit = new CanvasWindowToolkit();
        var bindings = new TeaVmWgpuBindings();
        var platform = WebPlatform.builder().build();
        var gfx = new WebGpuConfig(toolkit, bindings);

        var config = EngineConfig.builder()
                .window(WindowDescriptor.builder("Engine - WebGPU").size(1280, 720).build())
                .platform(platform)
                .graphics(gfx)
                .debugOverlay(true)
                .build();

        new WebMain().launch(config);
    }

    @Override
    protected void init() {
        // Camera
        var cam = renderer().createCamera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60),
                (float) window().width() / window().height(), 0.1f, 100f);
        renderer().setActiveCamera(cam);

        // Red cube
        var cube = scene().createEntity();
        cube.add(PrimitiveMeshes.cube());
        cube.add(MaterialData.unlit(new Vec3(0.9f, 0.2f, 0.2f)));
        cube.add(Transform.at(-2, 0, 0));

        // Green sphere
        var sphere = scene().createEntity();
        sphere.add(PrimitiveMeshes.sphere());
        sphere.add(MaterialData.unlit(new Vec3(0.2f, 0.9f, 0.2f)));
        sphere.add(Transform.IDENTITY);

        // Blue cube
        var cube2 = scene().createEntity();
        cube2.add(PrimitiveMeshes.cube());
        cube2.add(MaterialData.unlit(new Vec3(0.2f, 0.2f, 0.9f)));
        cube2.add(Transform.at(2, 0, 0));

        System.out.println("[WebMain] Scene initialized with 3 entities");

        // Semi-transparent debug UI for web overlay
        var ui = debugUi();
        if (ui != null) {
            ui.style().windowBackground = NkColor.rgba(30, 30, 30, 180);
            ui.style().headerBackground = NkColor.rgba(25, 25, 25, 200);
        }

        // Hide the HTML status overlay now that the engine is rendering
        hideStatusOverlay();
    }

    @JSBody(script = "var el = document.getElementById('status'); if (el) el.style.display = 'none';")
    private static native void hideStatusOverlay();

    @Override
    protected void update(float deltaTime, List<InputEvent> inputEvents) {
        for (var event : inputEvents) {
            if (event instanceof InputEvent.KeyPressed kp && kp.keyCode() == KeyCode.ESCAPE) {
                window().close();
            }
        }

        // Debug overlay
        var ui = debugUi();
        if (ui != null) {
            if (ui.begin("Engine Stats", 10, 10, 280, 200)) {
                ui.layoutRowDynamic(20, 1);
                ui.label("FPS: " + (deltaTime > 0 ? String.format("%.0f", 1.0f / deltaTime) : "---"));
                ui.label("Frame: " + frameNumber());
                ui.label(String.format("Time: %.1fs", time()));
                ui.label("Entities: 3");
                ui.label("Viewport: " + window().width() + "x" + window().height());
            }
            ui.end();
        }
    }
}
