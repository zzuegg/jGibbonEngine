package dev.engine.web;

import dev.engine.core.asset.AssetManager;
import dev.engine.core.asset.SlangShaderLoader;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.Renderer;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.webgpu.WgpuRenderDevice;
import dev.engine.providers.teavm.webgpu.FetchAssetSource;
import dev.engine.providers.teavm.webgpu.TeaVmShaderCompiler;
import dev.engine.providers.teavm.webgpu.TeaVmWgpuBindings;
import dev.engine.providers.teavm.webgpu.TeaVmWgpuInit;
import dev.engine.providers.teavm.windowing.CanvasWindowToolkit;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Entry point for the TeaVM-compiled web application.
 *
 * <p>Uses the FULL engine pipeline: Renderer + WgpuRenderDevice + ShaderManager.
 * Same code path as desktop screenshot tests — just different providers.
 */
public class WebMain {

    private static Renderer renderer;

    /**
     * Waits for the next animation frame. Uses TeaVM's @Async to suspend
     * the calling thread until requestAnimationFrame fires.
     * This allows @Async methods (like fetch) to work inside the render loop.
     */
    @org.teavm.interop.Async
    private static native void waitForNextFrame();

    private static void waitForNextFrame(org.teavm.interop.AsyncCallback<Void> callback) {
        waitForNextFrameJS(callback);
    }

    @JSBody(params = "callback", script = "requestAnimationFrame(function() { callback(null); });")
    private static native void waitForNextFrameJS(org.teavm.interop.AsyncCallback<Void> callback);

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
        // Force-load generated _NativeStruct classes (triggers RecordRegistry + StructLayout registration)
        dev.engine.core.shader.params.EngineParams_NativeStruct.init();
        dev.engine.core.shader.params.CameraParams_NativeStruct.init();
        dev.engine.core.shader.params.ObjectParams_NativeStruct.init();

        setStatus("Initializing WebGPU...");

        var wgpuBindings = new TeaVmWgpuBindings();
        if (!wgpuBindings.isAvailable()) {
            setStatus("WebGPU is not available in this browser.");
            return;
        }
        wgpuBindings.initialize();

        // Async init: request adapter + device
        int deviceId = TeaVmWgpuInit.initAsync();
        if (deviceId <= 0) {
            setStatus("Failed to initialize WebGPU adapter/device.");
            return;
        }
        consoleLog("[Engine] WebGPU device ready, id=" + deviceId);

        // Create window via canvas toolkit
        var toolkit = new CanvasWindowToolkit();
        var window = toolkit.createWindow(
                new dev.engine.graphics.window.WindowDescriptor("Engine", getCanvasWidth(), getCanvasHeight()));

        // Create WgpuRenderDevice — same class as desktop WebGPU backend
        setStatus("Creating render device...");
        var device = new WgpuRenderDevice(window, wgpuBindings);

        // Create shader compiler — Slang WASM in the browser
        setStatus("Initializing shader compiler...");
        var compiler = new TeaVmShaderCompiler();

        // Create the full engine Renderer — same as desktop
        setStatus("Creating renderer...");
        renderer = new Renderer(device, compiler);

        int width = getCanvasWidth();
        int height = getCanvasHeight();
        renderer.setViewport(width, height);

        // Wire fetch-based asset loading
        var assetManager = new AssetManager(Runnable::run);
        assetManager.addSource(new FetchAssetSource("assets/"));
        assetManager.registerLoader(new SlangShaderLoader());
        renderer.shaderManager().setAssetManager(assetManager);

        // Set up camera — same as CrossBackendScenes.TWO_CUBES_UNLIT
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) width / height, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Set up scene — same as desktop
        var cube1 = renderer.scene().createEntity();
        cube1.add(PrimitiveMeshes.cube());
        cube1.add(MaterialData.unlit(new Vec3(0.9f, 0.2f, 0.2f)));
        cube1.add(Transform.at(-1.5f, 0, 0));

        var cube2 = renderer.scene().createEntity();
        cube2.add(PrimitiveMeshes.cube());
        cube2.add(MaterialData.unlit(new Vec3(0.2f, 0.9f, 0.2f)));
        cube2.add(Transform.at(1.5f, 0, 0));

        setStatus("Rendering (full engine pipeline)");
        consoleLog("[Engine] Scene ready, entering render loop");

        // Run the render loop in a TeaVM thread so @Async methods (fetch, etc.) work.
        // waitForNextFrame() suspends the thread until requestAnimationFrame fires.
        new Thread(() -> {
            while (true) {
                waitForNextFrame();
                renderFrame();
            }
        }).start();
    }

    private static int frameCount = 0;

    private static void renderFrame() {
        frameCount++;
        if (frameCount <= 3) {
            consoleLog("[Frame " + frameCount + "] renderFrame() called");
            consoleLog("[Frame " + frameCount + "] activeCamera=" + (renderer.activeCamera() != null));
            consoleLog("[Frame " + frameCount + "] backendName=" + renderer.backendName());
        }
        try {
            renderer.renderFrame();
        } catch (Exception e) {
            consoleLog("[Frame " + frameCount + "] ERROR: " + e.getClass().getName() + ": " + e.getMessage());
        }
        if (frameCount <= 3) {
            consoleLog("[Frame " + frameCount + "] renderFrame() completed");
        }
    }
}
