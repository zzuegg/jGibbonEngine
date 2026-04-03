package dev.engine.web;

import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.webgpu.WgpuBindings;
import dev.engine.providers.teavm.webgpu.TeaVmWgpuBindings;
import dev.engine.providers.teavm.webgpu.TeaVmWgpuInit;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Entry point for the TeaVM-compiled web application.
 *
 * <p>Renders the TWO_CUBES_UNLIT scene (matching the desktop screenshot tests)
 * using the engine's math types, material data, and transforms. Shaders are
 * compiled from Slang via the WASM compiler at runtime.
 */
public class WebMain {

    private static TeaVmWgpuBindings bindings;
    private static int deviceId;
    private static int queueId;
    private static int contextId;
    private static WebRenderer renderer;

    @JSFunctor
    public interface FrameCallback extends JSObject {
        void onFrame();
    }

    @JSBody(params = "callback", script = """
        function loop() {
            callback();
            requestAnimationFrame(loop);
        }
        requestAnimationFrame(loop);
    """)
    private static native void requestAnimationFrame(FrameCallback callback);

    @JSBody(params = "msg", script = """
        var el = document.getElementById('status');
        if (el) el.textContent = msg;
    """)
    private static native void setStatus(String msg);

    @JSBody(params = "msg", script = "console.log(msg);")
    private static native void consoleLog(String msg);

    @JSBody(script = "return document.getElementById('canvas').width;")
    private static native int getCanvasWidth();

    @JSBody(script = "return document.getElementById('canvas').height;")
    private static native int getCanvasHeight();

    public static void main(String[] args) {
        setStatus("Initializing WebGPU...");

        bindings = new TeaVmWgpuBindings();
        if (!bindings.isAvailable()) {
            setStatus("WebGPU is not available in this browser.");
            return;
        }
        bindings.initialize();

        // Async init: request adapter + device
        deviceId = TeaVmWgpuInit.initAsync();
        if (deviceId <= 0) {
            setStatus("Failed to initialize WebGPU adapter/device.");
            return;
        }

        queueId = (int) bindings.deviceGetQueue(deviceId);

        // Configure the canvas context
        contextId = TeaVmWgpuBindings.configureCanvasContext("canvas", deviceId);
        String canvasFormat = TeaVmWgpuBindings.getPreferredCanvasFormat();

        int width = getCanvasWidth();
        int height = getCanvasHeight();

        setStatus("Compiling shaders and creating pipeline...");

        // Create the WebRenderer
        renderer = new WebRenderer(bindings, deviceId, queueId);
        renderer.init(mapCanvasFormat(canvasFormat), width, height);
        renderer.setupCamera(width, height);

        // Set up the TWO_CUBES_UNLIT scene (same as CrossBackendScenes.TWO_CUBES_UNLIT)
        renderer.addEntity(
                Transform.at(-1.5f, 0, 0),
                MaterialData.unlit(new Vec3(0.9f, 0.2f, 0.2f)));
        renderer.addEntity(
                Transform.at(1.5f, 0, 0),
                MaterialData.unlit(new Vec3(0.2f, 0.9f, 0.2f)));

        if (renderer.isSlangCompiled()) {
            setStatus("Rendering TWO_CUBES_UNLIT (Slang -> WGSL)");
        } else {
            setStatus("Rendering TWO_CUBES_UNLIT (fallback WGSL)");
        }

        // Enter the render loop
        requestAnimationFrame(WebMain::renderFrame);
    }

    private static void renderFrame() {
        int width = getCanvasWidth();
        int height = getCanvasHeight();
        int textureViewId = TeaVmWgpuBindings.getCurrentTextureView(contextId);

        renderer.renderFrame(textureViewId, width, height);

        // Release per-frame texture view
        TeaVmWgpuBindings.wgpuRelease(textureViewId);
    }

    private static int mapCanvasFormat(String format) {
        return switch (format) {
            case "rgba8unorm" -> WgpuBindings.TEXTURE_FORMAT_RGBA8_UNORM;
            case "bgra8unorm" -> WgpuBindings.TEXTURE_FORMAT_BGRA8_UNORM;
            default -> WgpuBindings.TEXTURE_FORMAT_BGRA8_UNORM;
        };
    }
}
