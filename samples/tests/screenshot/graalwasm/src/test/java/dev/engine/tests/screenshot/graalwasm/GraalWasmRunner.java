package dev.engine.tests.screenshot.graalwasm;

import dev.engine.tests.screenshot.runner.AbstractTestRunner;
import dev.engine.tests.screenshot.runner.SceneResult;
import dev.engine.tests.screenshot.web.CdpClient;
import dev.engine.tests.screenshot.web.EmbeddedHttpServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GraalWasm test runner that serves the WASM test harness via HTTP and captures
 * screenshots from headless Chrome via CDP. Mirrors the TeaVM web runner pattern.
 */
public class GraalWasmRunner extends AbstractTestRunner {

    static final String BACKEND = "graalwasm-webgpu";
    private static final long SCENE_TIMEOUT_MS = 120_000; // longer than TeaVM — WASM boot is slow

    private final Path wasmRoot;
    private final String chromeBinary;
    private final boolean useSwiftShader;

    public GraalWasmRunner(Path wasmRoot, String chromeBinary, String profile) {
        this.wasmRoot = wasmRoot;
        this.chromeBinary = chromeBinary;
        this.useSwiftShader = "ci".equals(profile);
    }

    @Override
    public List<String> backends() {
        return List.of(BACKEND);
    }

    @Override
    protected SceneResult runScene(String className, String fieldName,
                                    String backend, Path outputDir) {
        String sceneName = fieldName.toLowerCase();

        try (var httpServer = new EmbeddedHttpServer(wasmRoot);
             var cdp = CdpClient.launch(chromeBinary, 256, 256, true, useSwiftShader)) {

            String url = httpServer.baseUrl() + "/test.html?scene=" + sceneName + "&frames=3";
            System.out.println("  [GraalWasm] Navigating to: " + url);
            cdp.navigate(url);

            String status = cdp.waitForCondition("window._testStatus", SCENE_TIMEOUT_MS,
                    "done", "error");

            if (!"done".equals(status)) {
                String messageResult = cdp.evaluateJs("window._testMessage || ''");
                String msg = extractStringValue(messageResult);
                if ("error".equals(status)) {
                    return new SceneResult.ExceptionResult(
                            "Scene '" + sceneName + "' error: " + msg, "");
                }
                return new SceneResult.ExceptionResult(
                        "Timeout waiting for scene '" + sceneName + "'", "Message: " + msg);
            }

            // Read screenshot data — try readFramebuffer data URL first (set by Java
            // code via device.readFramebuffer()), fall back to CDP screenshot.
            // This mirrors the TeaVM WebRunner pattern.
            byte[] pngBytes = cdp.readCanvasScreenshot();
            boolean usedCdpFallback = false;
            if (pngBytes == null || pngBytes.length == 0 || isBlankImage(pngBytes)) {
                // readFramebuffer failed or returned blank — use CDP screenshot
                cdp.setViewportSize(256, 256);
                Thread.sleep(200);
                pngBytes = cdp.captureScreenshot(true, 256, 256);
                usedCdpFallback = true;
            }
            if (pngBytes == null || pngBytes.length == 0) {
                return new SceneResult.ExceptionResult(
                        "No screenshot data for scene '" + sceneName + "'", "");
            }
            if (usedCdpFallback) {
                System.out.println("  Used CDP screenshot fallback for " + sceneName);
            }

            var dir = outputDir.resolve(BACKEND);
            Files.createDirectories(dir);
            String filename = sceneName + "_f3.png";
            Files.write(dir.resolve(filename), pngBytes);

            Map<Integer, String> paths = new HashMap<>();
            paths.put(3, BACKEND + "/" + filename);
            return new SceneResult.Success(paths);

        } catch (Exception e) {
            return new SceneResult.ExceptionResult(e.getMessage(), stackTraceStr(e));
        }
    }

    private static String extractStringValue(String cdpResponse) {
        int idx = cdpResponse.indexOf("\"value\":\"");
        if (idx < 0) {
            idx = cdpResponse.indexOf("\"value\":");
            if (idx >= 0) {
                int start = idx + 8;
                int end = cdpResponse.indexOf(",", start);
                if (end < 0) end = cdpResponse.indexOf("}", start);
                return end > start ? cdpResponse.substring(start, end).trim() : cdpResponse;
            }
            return cdpResponse;
        }
        int start = idx + 9;
        int end = cdpResponse.indexOf("\"", start);
        return end > start ? cdpResponse.substring(start, end) : "";
    }

    private static boolean isBlankImage(byte[] pngBytes) {
        try {
            var img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(pngBytes));
            if (img == null) return true;
            int w = img.getWidth(), h = img.getHeight();
            int firstPixel = img.getRGB(0, 0);
            int sameCount = 0, total = 0;
            for (int y = 0; y < h; y += h / 8 + 1) {
                for (int x = 0; x < w; x += w / 8 + 1) {
                    total++;
                    if (img.getRGB(x, y) == firstPixel) sameCount++;
                }
            }
            return sameCount == total;
        } catch (Exception e) {
            return false;
        }
    }

    private static String stackTraceStr(Exception e) {
        var sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
