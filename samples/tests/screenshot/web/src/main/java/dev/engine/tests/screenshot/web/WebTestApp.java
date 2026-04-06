package dev.engine.tests.screenshot.web;

import dev.engine.graphics.common.engine.Engine;
import dev.engine.graphics.common.engine.EngineConfig;
import dev.engine.graphics.webgpu.WgpuRenderDevice;
import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.platform.web.WebPlatform;
import dev.engine.providers.teavm.webgpu.TeaVmWgpuBindings;
import dev.engine.providers.teavm.webgpu.TeaVmWgpuInit;
import dev.engine.providers.teavm.windowing.CanvasWindowToolkit;
import org.teavm.jso.JSBody;

/**
 * TeaVM-compiled test application that renders all screenshot test scenes
 * sequentially in the browser and signals readiness for capture after each.
 *
 * <p>Communication protocol with the CDP test runner:
 * <ol>
 *   <li>App sets {@code window._sceneCount} on startup</li>
 *   <li>For each scene, renders the required frames</li>
 *   <li>After the capture frame, sets {@code window._captureReady = sceneName}</li>
 *   <li>Waits for {@code window._captureAck === true} before proceeding</li>
 *   <li>Sets {@code window._testsDone = true} when all scenes are finished</li>
 * </ol>
 */
public class WebTestApp {

    private static final int WIDTH = 256;
    private static final int HEIGHT = 256;

    public static void main(String[] args) {
        setStatus("initializing");

        // Initialize WebGPU
        int deviceId = TeaVmWgpuInit.initAsync();
        if (deviceId == 0) {
            setStatus("error: WebGPU init failed");
            markDone();
            return;
        }

        // Discover scenes (explicit registry — TeaVM can't do classpath scanning)
        var scenes = WebSceneRegistry.discoverScenes();
        setSceneCount(scenes.size());
        setStatus("discovered " + scenes.size() + " scenes");

        // Create toolkit and window once
        var toolkit = new CanvasWindowToolkit();
        var windowDesc = new WindowDescriptor("WebGPU Test", WIDTH, HEIGHT);
        var window = toolkit.createWindow(windowDesc);

        var gpu = new TeaVmWgpuBindings();
        TeaVmWgpuBindings.configureCanvasContext("canvas", deviceId);

        for (var discovered : scenes) {
            var sceneName = discovered.name();
            setStatus("rendering: " + sceneName);

            // Create device and engine for this scene
            var device = new WgpuRenderDevice(window, gpu, true);
            var platform = WebPlatform.builder().build();
            var config = EngineConfig.builder()
                    .windowTitle("WebGPU Test")
                    .windowSize(WIDTH, HEIGHT)
                    .headless(false)
                    .platform(platform)
                    .maxFrames(0)
                    .build();
            var engine = new Engine(config, platform, device);

            try {
                engine.renderer().setViewport(WIDTH, HEIGHT);
                discovered.scene().setup(engine);

                int[] captureFrames = discovered.scene().captureFrames();
                int maxFrame = 0;
                for (int f : captureFrames) maxFrame = Math.max(maxFrame, f);

                int lastCaptureFrame = captureFrames[captureFrames.length - 1];

                for (int frame = 0; frame <= maxFrame; frame++) {
                    var inputScript = discovered.scene().inputScript();
                    var frameEvents = inputScript.getOrDefault(frame,
                            java.util.List.of());
                    engine.setInputEvents(frameEvents);
                    engine.tick(1.0 / 60.0);

                    // After the last capture frame, signal ready and wait
                    if (frame == lastCaptureFrame) {
                        String suffix = captureFrames.length > 1 ? "_f" + frame : "";
                        signalCaptureReady(sceneName + suffix);

                        // Yield to browser event loop and wait for ack
                        toolkit.pollEvents();
                        waitForAck(toolkit);
                    }
                }
            } catch (Exception e) {
                logError("Scene " + sceneName + " failed: " + e.getMessage());
            } finally {
                engine.shutdown();
            }
        }

        setStatus("done");
        markDone();
    }

    /**
     * Polls until the CDP runner sets window._captureAck = true,
     * yielding to the browser event loop each iteration.
     */
    private static void waitForAck(CanvasWindowToolkit toolkit) {
        while (!isCaptureAcked()) {
            toolkit.pollEvents();
        }
        clearAck();
    }

    // --- JS interop ---

    @JSBody(params = "status", script = "window._testStatus = status; console.log('[WebTest] ' + status);")
    private static native void setStatus(String status);

    @JSBody(params = "count", script = "window._sceneCount = count;")
    private static native void setSceneCount(int count);

    @JSBody(params = "name", script = "window._captureReady = name; window._captureAck = false;")
    private static native void signalCaptureReady(String name);

    @JSBody(script = "return !!window._captureAck;")
    private static native boolean isCaptureAcked();

    @JSBody(script = "window._captureAck = false; window._captureReady = null;")
    private static native void clearAck();

    @JSBody(script = "window._testsDone = true;")
    private static native void markDone();

    @JSBody(params = "msg", script = "console.error('[WebTest] ' + msg);")
    private static native void logError(String msg);
}
