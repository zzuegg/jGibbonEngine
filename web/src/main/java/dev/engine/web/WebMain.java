package dev.engine.web;

import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.Renderer;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.webgpu.WgpuRenderDevice;
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
        // Register struct layouts for TeaVM (no reflection available)
        WebStructLayouts.registerAll();

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

        // Enter the render loop
        requestAnimationFrame(WebMain::renderFrame);
    }

    private static void renderFrame() {
        // Full engine render — processes scene transactions, compiles shaders,
        // uploads UBOs, records commands, submits to GPU
        renderer.renderFrame();
    }
}
