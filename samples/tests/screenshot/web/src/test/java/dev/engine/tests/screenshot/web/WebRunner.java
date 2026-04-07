package dev.engine.tests.screenshot.web;

import dev.engine.tests.screenshot.runner.AbstractTestRunner;
import dev.engine.tests.screenshot.runner.SceneResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Web test runner that serves the TeaVM test harness via HTTP and captures
 * screenshots from headless Chrome via CDP.
 *
 * <p>Each scene is loaded by navigating to a new URL with the scene name
 * as a query parameter. A fresh Chrome instance is created per scene for
 * isolation (WebGPU device state doesn't leak between runs).
 */
public class WebRunner extends AbstractTestRunner {

    static final String BACKEND = "teavm-webgpu";
    private static final long SCENE_TIMEOUT_MS = 60_000;

    private final Path webRoot;
    private final String chromeBinary;

    public WebRunner(Path webRoot, String chromeBinary) {
        this.webRoot = webRoot;
        this.chromeBinary = chromeBinary;
    }

    @Override
    public List<String> backends() {
        return List.of(BACKEND);
    }

    @Override
    protected SceneResult runScene(String className, String fieldName,
                                    String backend, Path outputDir) {
        String sceneName = fieldName.toLowerCase();

        try (var httpServer = new EmbeddedHttpServer(webRoot);
             var cdp = CdpClient.launch(chromeBinary, 256, 256)) {

            String url = httpServer.baseUrl() + "/test.html?scene=" + sceneName + "&frames=3";
            System.out.println("  Chrome connected via CDP");
            System.out.println("  Navigating to: " + url);
            cdp.navigate(url);
            System.out.println("  Navigation complete, waiting for test to finish...");

            // Wait for the test to complete or error out
            String status = cdp.waitForCondition("window._testStatus", SCENE_TIMEOUT_MS,
                    "done", "error");

            if (!"done".equals(status)) {
                String messageResult = cdp.evaluateJs("window._testMessage || ''");
                String msg = extractStringValue(messageResult);
                if ("error".equals(status)) {
                    return new SceneResult.ExceptionResult(
                            "Scene '" + sceneName + "' error: " + msg, "");
                }
                String logsResult = cdp.evaluateJs(
                        "JSON.stringify((window._allLogs || []).slice(-20))");
                return new SceneResult.ExceptionResult(
                        "Timeout waiting for scene '" + sceneName + "'",
                        "Message: " + msg + "\nLogs: " + extractStringValue(logsResult));
            }

            // Read screenshot data from canvas
            byte[] pngBytes = cdp.readCanvasScreenshot();
            if (pngBytes == null || pngBytes.length == 0) {
                return new SceneResult.ExceptionResult(
                        "No screenshot data for scene '" + sceneName + "'", "");
            }

            // Save the PNG
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

    private static String stackTraceStr(Exception e) {
        var sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
