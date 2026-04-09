package dev.engine.tests.screenshot.graalwasm;

import dev.engine.core.asset.AssetManager;
import dev.engine.core.asset.SlangShaderLoader;
import dev.engine.graphics.GraphicsBackend;
import dev.engine.graphics.common.engine.Engine;
import dev.engine.graphics.common.engine.Platform;
import dev.engine.graphics.shader.ShaderCompiler;
import dev.engine.graphics.webgpu.WebGpuConfig;
import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.platform.graalwasm.GraalFetchAssetSource;
import dev.engine.providers.graal.webgpu.GraalWgpuBindings;
import dev.engine.providers.graal.webgpu.GraalWgpuInit;
import dev.engine.providers.graal.windowing.GraalCanvasWindowToolkit;
import dev.engine.providers.slang.graalwasm.GraalSlangWasmBridge;
import dev.engine.providers.slang.wasm.SlangWasmCompiler;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSNumber;
import org.graalvm.webimage.api.JSString;

import java.util.List;
import java.util.function.BiFunction;

/**
 * GraalVM Web Image entry point for screenshot tests. Compiled to WASM
 * via {@code native-image --tool:svm-wasm}. Exports a {@code _renderScene}
 * function that the host HTML page calls with scene parameters.
 */
public class GraalWasmTestApp {

    private static Engine activeEngine;
    private static dev.engine.graphics.RenderDevice activeDevice;
    private static int activeFrame;
    private static int targetFrame;
    private static int sceneWidth;
    private static int sceneHeight;

    public static void main(String[] args) {
        // Register discovery registries (no reflection in WASM)
        dev.engine.core.Discovery.addRegistry(
                new dev.engine.graphics.shader.params.GeneratedDiscoveryRegistry());
        dev.engine.core.Discovery.addRegistry(
                new dev.engine.tests.screenshot.scenes.basic.GeneratedDiscoveryRegistry());

        // Export setup function (called once) and tick function (called per frame)
        exportSetupScene((paramsStr, unused) -> {
            try {
                return JSString.of(setupScene(paramsStr.asString()));
            } catch (Exception e) {
                System.err.println("ERROR: " + e.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace();
                return JSString.of("ERROR:" + e.getMessage());
            }
        });

        exportTickFrame((unused1, unused2) -> {
            try {
                activeEngine.setInputEvents(List.of());
                activeEngine.tick(1.0 / 60.0);
                activeFrame++;
                if (activeFrame >= targetFrame) {
                    captureScreenshot();
                    return JSNumber.of(1);
                }
                return JSNumber.of(0);
            } catch (Exception e) {
                System.err.println("ERROR in tick: " + e.getMessage());
                return JSNumber.of(-1);
            }
        });
    }

    /**
     * Captures the rendered frame via device.readFramebuffer() — the same path
     * TeaVM uses. Falls back to canvas.toDataURL() if readback fails.
     * Sets window._screenshotData with the PNG data URL.
     */
    private static void captureScreenshot() {
        try {
            byte[] pixels = activeDevice.readFramebuffer(sceneWidth, sceneHeight);
            if (pixels != null && pixels.length > 0) {
                String dataUrl = encodePixelsToDataUrl(pixels, sceneWidth, sceneHeight);
                setScreenshotData(JSString.of(dataUrl));
                System.out.println("[GraalWasm] Screenshot captured via readFramebuffer, pixels=" + pixels.length);
                return;
            }
        } catch (Exception e) {
            System.err.println("[GraalWasm] readFramebuffer failed: " + e.getMessage());
        }
        // Fallback: canvas.toDataURL()
        try {
            String dataUrl = canvasToDataURL("canvas");
            setScreenshotData(JSString.of(dataUrl));
            System.out.println("[GraalWasm] Screenshot captured via canvasFallback");
        } catch (Exception e) {
            System.err.println("[GraalWasm] canvasFallback failed: " + e.getMessage());
        }
    }

    private static String setupScene(String params) {
        // params format: "sceneName|captureFrame|width|height"
        String[] p = params.split("\\|");
        String sceneName = p[0];
        targetFrame = Integer.parseInt(p[1]);
        int width = Integer.parseInt(p[2]);
        int height = Integer.parseInt(p[3]);
        activeFrame = 0;

        var registry = GraalWasmTestSceneRegistry.all();
        var entry = registry.get(sceneName);
        if (entry == null) {
            return "ERROR:Unknown scene: " + sceneName
                    + ". Available: " + registry.keySet();
        }

        int deviceId = GraalWgpuInit.getDeviceId();
        if (deviceId == 0) {
            return "ERROR:WebGPU not initialized";
        }

        var sceneConfig = entry.config();
        var toolkit = new GraalCanvasWindowToolkit();
        var bindings = new GraalWgpuBindings();
        setCanvasSize(width, height);

        var gfx = new WebGpuConfig(toolkit, bindings);
        var windowDesc = WindowDescriptor.builder("Test")
                .size(width, height).build();
        GraphicsBackend backend = gfx.create(windowDesc);

        Platform platform = new Platform() {
            @Override
            public void configureAssets(AssetManager assets) {
                assets.addSource(new GraalFetchAssetSource("assets/"));
                assets.registerLoader(new SlangShaderLoader());
            }
            @Override
            public ShaderCompiler shaderCompiler() {
                var bridge = new GraalSlangWasmBridge();
                return new SlangWasmCompiler(bridge);
            }
        };

        var engineConfig = sceneConfig.engineConfigBuilder()
                .window(windowDesc)
                .platform(platform)
                .build();

        activeDevice = backend.device();
        sceneWidth = width;
        sceneHeight = height;
        activeEngine = new Engine(engineConfig, platform, activeDevice);
        activeEngine.renderer().setViewport(width, height);
        entry.scene().setup(activeEngine);

        System.out.println("[GraalWasm] Scene " + sceneName + " setup, rendering " + targetFrame + " frames");
        return "OK";
    }

    @JS(args = "fn", value = "globalThis._setupScene = fn;")
    private static native void exportSetupScene(BiFunction<JSString, JSNumber, JSString> fn);

    @JS(args = "fn", value = "globalThis._tickFrame = fn;")
    private static native void exportTickFrame(BiFunction<JSNumber, JSNumber, JSNumber> fn);

    @JS(args = "data", value = "window._screenshotData = data;")
    private static native void setScreenshotData(JSString data);

    private static void setCanvasSize(int width, int height) {
        setCanvasSizeJS(JSNumber.of(width), JSNumber.of(height));
    }

    @JS(args = {"width", "height"}, value = """
        var w = Number(width), h = Number(height);
        var c = document.getElementById('canvas');
        c.width = w; c.height = h;
        c.style.width = w + 'px'; c.style.height = h + 'px';
    """)
    private static native void setCanvasSizeJS(JSNumber width, JSNumber height);

    private static String canvasToDataURL(String id) { return canvasToDataURLJS(JSString.of(id)).asString(); }

    @JS(args = "id", value = "return document.getElementById(id).toDataURL('image/png');")
    private static native JSString canvasToDataURLJS(JSString id);

    @JS(args = {"pixels", "width", "height"}, value = """
        var canvas = document.createElement('canvas');
        canvas.width = width; canvas.height = height;
        var ctx = canvas.getContext('2d');
        var imgData = ctx.createImageData(width, height);
        for (var i = 0; i < pixels.length; i++) {
            imgData.data[i] = pixels[i] & 0xFF;
        }
        ctx.putImageData(imgData, 0, 0);
        return canvas.toDataURL('image/png');
    """)
    private static native String encodePixelsToDataUrl(byte[] pixels, int width, int height);
}
