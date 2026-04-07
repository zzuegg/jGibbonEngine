package dev.engine.tests.screenshot.web;

import dev.engine.core.asset.AssetManager;
import dev.engine.core.asset.SlangShaderLoader;
import dev.engine.graphics.GraphicsBackend;
import dev.engine.graphics.common.engine.Engine;
import dev.engine.graphics.common.engine.EngineConfig;
import dev.engine.graphics.common.engine.Platform;
import dev.engine.graphics.shader.ShaderCompiler;
import dev.engine.graphics.webgpu.WebGpuConfig;
import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.providers.teavm.webgpu.FetchAssetSource;
import dev.engine.providers.teavm.webgpu.TeaVmShaderCompiler;
import dev.engine.providers.teavm.webgpu.TeaVmWgpuBindings;
import dev.engine.providers.teavm.webgpu.TeaVmWgpuInit;
import dev.engine.providers.teavm.windowing.CanvasWindowToolkit;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;

import dev.engine.graphics.shader.params.CameraParams_NativeStruct;
import dev.engine.graphics.shader.params.EngineParams_NativeStruct;
import dev.engine.graphics.shader.params.ObjectParams_NativeStruct;

import java.util.List;

/**
 * TeaVM entry point for screenshot tests. Renders a single scene selected
 * via URL parameters, then captures the canvas content as a PNG data URL.
 *
 * <p>URL parameters:
 * <ul>
 *   <li>{@code scene} - scene name from {@link WebTestSceneRegistry}</li>
 *   <li>{@code frames} - number of frames to render before capture (default 3)</li>
 * </ul>
 *
 * <p>After capture, sets {@code window._testStatus} to "done" and
 * {@code window._screenshotData} to the PNG data URL.
 */
public class WebTestApp {

    public static void main(String[] args) {
        // Force-load NativeStruct classes for TeaVM DCE
        CameraParams_NativeStruct.init();
        EngineParams_NativeStruct.init();
        ObjectParams_NativeStruct.init();

        String sceneName = getUrlParam("scene");
        int captureFrame = parseIntParam("frames", 3);

        if (sceneName == null || sceneName.isEmpty()) {
            setTestStatus("error", "No scene parameter in URL");
            reportAvailableScenes();
            return;
        }

        var registry = WebTestSceneRegistry.all();
        var entry = registry.get(sceneName);
        if (entry == null) {
            setTestStatus("error", "Unknown scene: " + sceneName);
            reportAvailableScenes();
            return;
        }

        System.out.println("[WebTest] Scene: " + sceneName + ", capture at frame: " + captureFrame);

        // Initialize WebGPU
        int deviceId = TeaVmWgpuInit.initAsync();
        if (deviceId == 0) {
            setTestStatus("error", "WebGPU init failed");
            return;
        }

        var sceneConfig = entry.config();
        var toolkit = new CanvasWindowToolkit();
        var bindings = new TeaVmWgpuBindings();

        // Set canvas size to match scene config
        setCanvasSize(sceneConfig.width(), sceneConfig.height());

        var gfx = new WebGpuConfig(toolkit, bindings);
        var windowDesc = WindowDescriptor.builder("Test")
                .size(sceneConfig.width(), sceneConfig.height()).build();
        GraphicsBackend backend = gfx.create(windowDesc);

        // Inline platform (avoids dependency on platforms:web module)
        Platform platform = new Platform() {
            @Override
            public void configureAssets(AssetManager assets) {
                assets.addSource(new FetchAssetSource("assets/"));
                assets.registerLoader(new SlangShaderLoader());
            }
            @Override
            public ShaderCompiler shaderCompiler() {
                return new TeaVmShaderCompiler();
            }
        };

        var engineConfig = sceneConfig.engineConfigBuilder()
                .window(windowDesc)
                .platform(platform)
                .build();

        var engine = new Engine(engineConfig, platform, backend.device());
        engine.renderer().setViewport(sceneConfig.width(), sceneConfig.height());
        entry.scene().setup(engine);

        System.out.println("[WebTest] Scene setup complete, rendering " + (captureFrame + 1) + " frames");

        // Render frames, yield to browser each frame via pollEvents
        renderFrames(engine, toolkit, captureFrame);
    }

    private static void renderFrames(Engine engine, CanvasWindowToolkit toolkit,
                                      int captureFrame) {
        for (int frame = 0; frame <= captureFrame; frame++) {
            engine.setInputEvents(List.of());
            engine.tick(1.0 / 60.0);

            if (frame == captureFrame) {
                // Try canvas.toDataURL() first (works locally with real GPU).
                // Falls back to WebGPU texture readback if canvas is blank
                // (CI: Chrome's SkSurface can't initialize with software Vulkan).
                String dataUrl = canvasToDataURL("canvas");
                boolean blank = isBlankDataUrl(dataUrl);
                if (blank) {
                    System.out.println("[WebTest] toDataURL blank, trying WebGPU readback...");
                    dataUrl = readbackViaWebGPU();
                    blank = isBlankDataUrl(dataUrl);
                }
                int dataLen = dataUrl != null ? dataUrl.length() : 0;
                String gpuInfo = getGpuDiagnostics();
                setScreenshotData(dataUrl);
                String method = blank ? "BLANK" : (dataUrl.length() > 100 ? "ok" : "empty");
                setTestStatus("done", "frame=" + frame + " len=" + dataLen + " method=" + method + " gpu=" + gpuInfo);
                System.out.println("[WebTest] Screenshot captured at frame " + frame
                        + " len=" + dataLen + " method=" + method);
                return;
            }

            // Yield to browser for the next animation frame
            toolkit.pollEvents();
        }
    }

    private static void reportAvailableScenes() {
        var scenes = WebTestSceneRegistry.all();
        System.out.println("[WebTest] Available scenes: " + scenes.keySet());
        setAvailableScenes(String.join(",", scenes.keySet()));
    }

    @JSFunctor
    private interface VoidCallback extends JSObject {
        void call();
    }

    /**
     * Waits for all submitted GPU work to complete. Uses TeaVM's @Async
     * to block until the Promise from device.queue.onSubmittedWorkDone() resolves.
     */
    @Async
    private static native void waitForGpuFlush();

    private static void waitForGpuFlush(AsyncCallback<Void> callback) {
        waitForGpuFlushJS(() -> callback.complete(null));
    }

    @JSBody(params = "callback", script = """
        var deviceId = window._wgpuDevice;
        var device = window._wgpu[deviceId];
        if (device && device.queue) {
            device.queue.onSubmittedWorkDone().then(function() { callback(); });
        } else {
            callback();
        }
    """)
    private static native void waitForGpuFlushJS(VoidCallback callback);

    /**
     * Checks if a data URL represents a blank (all transparent) image.
     * A minimal blank 256x256 PNG data URL is ~3200 chars.
     * Real rendered content is typically 4000+ chars.
     */
    private static boolean isBlankDataUrl(String dataUrl) {
        if (dataUrl == null || dataUrl.length() < 100) return true;
        // Small PNGs with uniform content (blank) compress very well.
        // A 256x256 blank PNG is ~170 bytes base64 ≈ 200 chars after prefix.
        // But toDataURL includes the full prefix so check actual decoded size.
        return dataUrl.length() < 300;
    }

    /**
     * Reads the current WebGPU texture via copyTextureToBuffer + mapAsync,
     * encodes as PNG data URL. Bypasses canvas compositor entirely.
     * Uses TeaVM @Async to bridge the async mapAsync() Promise.
     */
    @Async
    private static native String readbackViaWebGPU();

    private static void readbackViaWebGPU(AsyncCallback<String> callback) {
        readbackViaWebGPUJS(result -> callback.complete(result));
    }

    @JSFunctor
    private interface StringCallback extends JSObject {
        void call(String value);
    }

    @JSBody(params = "callback", script = """
        try {
            var deviceId = window._wgpuDevice;
            var device = window._wgpu[deviceId];
            var canvas = document.getElementById('canvas');
            var ctx = canvas.getContext('webgpu');
            var texture = ctx.getCurrentTexture();
            var w = texture.width, h = texture.height;
            var bytesPerRow = Math.ceil(w * 4 / 256) * 256;
            var bufSize = bytesPerRow * h;

            var buf = device.createBuffer({
                size: bufSize,
                usage: GPUBufferUsage.COPY_DST | GPUBufferUsage.MAP_READ
            });

            var encoder = device.createCommandEncoder();
            encoder.copyTextureToBuffer(
                { texture: texture },
                { buffer: buf, bytesPerRow: bytesPerRow },
                { width: w, height: h }
            );
            device.queue.submit([encoder.finish()]);

            buf.mapAsync(GPUMapMode.READ).then(function() {
                var data = new Uint8Array(buf.getMappedRange());
                // Create ImageData, accounting for row padding
                var imgCanvas = document.createElement('canvas');
                imgCanvas.width = w; imgCanvas.height = h;
                var imgCtx = imgCanvas.getContext('2d');
                var imgData = imgCtx.createImageData(w, h);
                for (var y = 0; y < h; y++) {
                    var srcOff = y * bytesPerRow;
                    var dstOff = y * w * 4;
                    for (var x = 0; x < w * 4; x++) {
                        imgData.data[dstOff + x] = data[srcOff + x];
                    }
                }
                imgCtx.putImageData(imgData, 0, 0);
                buf.unmap();
                buf.destroy();
                var result = imgCanvas.toDataURL('image/png');
                callback(result);
            }).catch(function(err) {
                console.error('[WebTest] readback failed:', err);
                callback('');
            });
        } catch(e) {
            console.error('[WebTest] readback error:', e);
            callback('');
        }
    """)
    private static native void readbackViaWebGPUJS(StringCallback callback);

    @JSBody(script = """
        try {
            var deviceId = window._wgpuDevice || 0;
            var device = window._wgpu ? window._wgpu[deviceId] : null;
            var canvas = document.getElementById('canvas');
            var ctx = canvas ? canvas.getContext('webgpu') : null;
            return 'device=' + (device ? 'yes' : 'no')
                + ' ctx=' + (ctx ? 'yes' : 'no')
                + ' canvas=' + (canvas ? canvas.width + 'x' + canvas.height : 'null')
                + ' ctxCanvas=' + (ctx && ctx.canvas ? ctx.canvas.width + 'x' + ctx.canvas.height : 'null');
        } catch(e) { return 'error:' + e.message; }
    """)
    private static native String getGpuDiagnostics();

    @JSBody(params = "name", script = """
        var url = new URL(window.location.href);
        return url.searchParams.get(name) || '';
    """)
    private static native String getUrlParam(String name);

    @JSBody(params = {"width", "height"}, script = """
        var c = document.getElementById('canvas');
        c.width = width;
        c.height = height;
        c.style.width = width + 'px';
        c.style.height = height + 'px';
    """)
    private static native void setCanvasSize(int width, int height);

    @JSBody(params = "id", script = """
        return document.getElementById(id).toDataURL('image/png');
    """)
    private static native String canvasToDataURL(String id);

    @JSBody(params = {"status", "message"}, script = """
        window._testStatus = status;
        window._testMessage = message;
    """)
    private static native void setTestStatus(String status, String message);

    @JSBody(params = "data", script = "window._screenshotData = data;")
    private static native void setScreenshotData(String data);

    @JSBody(params = "scenes", script = "window._availableScenes = scenes;")
    private static native void setAvailableScenes(String scenes);

    private static int parseIntParam(String name, int defaultValue) {
        String val = getUrlParam(name);
        if (val == null || val.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
