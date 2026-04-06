package dev.engine.tests.screenshot.web;

import dev.engine.graphics.ScreenshotHelper;
import dev.engine.tests.screenshot.SceneDiscovery;
import dev.engine.tests.screenshot.TestResults;
import dev.engine.tests.screenshot.Tolerance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Web screenshot tests using headless Chrome with WebGPU.
 *
 * <p>Compiles test scenes to JavaScript via TeaVM, serves them from an embedded
 * HTTP server, launches headless Chrome with WebGPU enabled, and captures
 * screenshots via the Chrome DevTools Protocol.
 *
 * <p>Requires Chrome/Chromium installed. Set {@code CHROME_BIN} environment
 * variable to override the binary path.
 */
class WebScreenshotTest {

    private static final int WIDTH = 256;
    private static final int HEIGHT = 256;
    private static final String REFERENCE_DIR = "reference-screenshots";

    private static ChromeDevTools cdp;
    private static com.sun.net.httpserver.HttpServer httpServer;
    private static int serverPort;
    private static Path webDir;
    private static boolean webGpuAvailable;

    @BeforeAll
    static void setUp() throws Exception {
        // Find the TeaVM output directory
        webDir = Path.of("build/web");
        assumeTrue(Files.exists(webDir) && Files.exists(webDir.resolve("js/web-test.js")),
                "TeaVM web build not found at " + webDir + ". Run ./gradlew :samples:tests:screenshot:web:assembleWebTest first.");

        // Start embedded HTTP server
        httpServer = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverPort = httpServer.getAddress().getPort();
        httpServer.createContext("/", exchange -> {
            var path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            var file = webDir.resolve(path.substring(1));
            if (Files.exists(file) && !Files.isDirectory(file)) {
                var bytes = Files.readAllBytes(file);
                var contentType = guessContentType(file.toString());
                exchange.getResponseHeaders().add("Content-Type", contentType);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.close();
        });
        httpServer.start();
        System.out.println("[WebTest] HTTP server on port " + serverPort);

        // Launch Chrome
        var chromeBin = findChromeBinary();
        assumeTrue(chromeBin != null, "Chrome/Chromium not found. Set CHROME_BIN env var.");

        try {
            var appUrl = "http://127.0.0.1:" + serverPort + "/";
            cdp = ChromeDevTools.launch(chromeBin, appUrl);
            cdp.send("Runtime.enable", null);
            System.out.println("[WebTest] Chrome launched with " + appUrl);

            // Wait for app to be ready (WebGPU initialized + scenes discovered)
            String status = null;
            for (int i = 0; i < 60; i++) {
                status = cdp.evaluateString("window._testStatus || 'unknown'");
                if ("ready".equals(status) || (status != null && status.contains("error"))) break;
                Thread.sleep(500);
            }
            webGpuAvailable = "ready".equals(status);
            System.out.println("[WebTest] App status: " + status + ", webGpuAvailable: " + webGpuAvailable);
        } catch (Exception e) {
            System.err.println("[WebTest] Chrome launch failed: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(System.err);
            webGpuAvailable = false;
        }
    }

    @AfterAll
    static void tearDown() {
        if (cdp != null) cdp.close();
        if (httpServer != null) httpServer.stop(0);
    }

    @TestFactory
    Collection<DynamicNode> webScreenshotTests() {
        assumeTrue(webGpuAvailable, "WebGPU not available in headless Chrome");

        // Use explicit scene registry (same as the TeaVM app) instead of reflection-based
        // SceneDiscovery, which fails when scenes are packaged in a JAR on the test classpath.
        var registeredScenes = WebSceneRegistry.discoverScenes();
        System.out.println("[WebTest] WebSceneRegistry found " + registeredScenes.size() + " scenes");

        // Signal the app to start rendering now that we're ready to capture
        try {
            cdp.evaluate("window._startRendering = true");
            Thread.sleep(1000);
        } catch (Exception e) {
            throw new RuntimeException("Failed to signal app to start", e);
        }

        var tests = new ArrayList<DynamicNode>();
        for (var registered : registeredScenes) {
            var discovered = new SceneDiscovery.DiscoveredScene(
                    "webgpu-browser", registered.name(), registered.scene(), registered.tolerance());
            tests.add(generateSceneTest(discovered));
        }

        return tests;
    }

    private DynamicTest generateSceneTest(SceneDiscovery.DiscoveredScene discovered) {
        return DynamicTest.dynamicTest(discovered.name(), () -> {
            assumeTrue(webGpuAvailable, "WebGPU not available");

            // Wait for this scene to be captured
            var sceneName = discovered.name();
            int[] frames = discovered.scene().captureFrames();
            String suffix = frames.length > 1 ? "_f" + frames[frames.length - 1] : "";
            String expectedCaptureName = sceneName + suffix;

            // Poll for the scene to become ready (max 30s)
            String captureReady = null;
            boolean sceneError = false;
            for (int i = 0; i < 300; i++) {
                captureReady = cdp.evaluateString("window._captureReady || ''");
                if (expectedCaptureName.equals(captureReady)) break;
                // Check if the scene errored
                if (("ERROR:" + sceneName).equals(captureReady)) {
                    sceneError = true;
                    break;
                }

                // Check if tests are done (scene might have been skipped)
                var done = cdp.evaluateBoolean("!!window._testsDone");
                if (Boolean.TRUE.equals(done)) break;

                Thread.sleep(100);
            }

            // If the scene errored, acknowledge and skip
            if (sceneError) {
                cdp.evaluate("window._captureAck = true");
                assumeTrue(false, "Scene " + sceneName + " failed in browser");
                return;
            }

            assumeTrue(expectedCaptureName.equals(captureReady),
                    "Scene " + expectedCaptureName + " was not rendered by web app (got: " + captureReady + ")");

            // Capture canvas pixels
            byte[] pixels = cdp.readCanvasPixels(WIDTH, HEIGHT);
            assertNotNull(pixels, "Failed to read canvas pixels");

            // Acknowledge capture so app moves to next scene
            cdp.evaluate("window._captureAck = true");

            // Save screenshot
            saveScreenshot(pixels, "webgpu-browser", sceneName + suffix);

            // Compare against reference if one exists
            var reference = loadReference("webgpu-browser", sceneName + suffix);
            if (reference != null) {
                var tolerance = discovered.tolerance();
                double diff = ScreenshotHelper.diffPercentage(pixels, reference, tolerance.maxChannelDiff());
                TestResults.instance().recordDiff(sceneName, "webgpu-browser_ref", diff);
                assertTrue(diff < tolerance.maxDiffPercent(),
                        "WebGPU-Browser '" + sceneName + suffix
                                + "' regressed: " + String.format("%.2f%%", diff)
                                + " diff (max " + tolerance.maxDiffPercent() + "%)."
                                + " Screenshot: build/screenshots/webgpu-browser/" + sceneName + suffix + ".png");
            }
        });
    }

    private void saveScreenshot(byte[] pixels, String backend, String name) {
        try {
            var dir = new File("build/screenshots/" + backend);
            dir.mkdirs();
            ScreenshotHelper.save(pixels, WIDTH, HEIGHT, dir.getPath() + "/" + name + ".png");
        } catch (IOException e) {
            System.err.println("Warning: failed to save screenshot: " + e.getMessage());
        }
    }

    private byte[] loadReference(String backend, String name) {
        var path = REFERENCE_DIR + "/" + backend + "/" + name + ".png";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) return null;
            var image = ImageIO.read(is);
            if (image == null) return null;
            var pixels = new byte[WIDTH * HEIGHT * 4];
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    int argb = image.getRGB(x, y);
                    int idx = (y * WIDTH + x) * 4;
                    pixels[idx]     = (byte) ((argb >> 16) & 0xFF);
                    pixels[idx + 1] = (byte) ((argb >> 8) & 0xFF);
                    pixels[idx + 2] = (byte) (argb & 0xFF);
                    pixels[idx + 3] = (byte) ((argb >> 24) & 0xFF);
                }
            }
            return pixels;
        } catch (IOException e) {
            return null;
        }
    }

    private static String findChromeBinary() {
        // Check environment variable first
        var envBin = System.getenv("CHROME_BIN");
        if (envBin != null && !envBin.isEmpty()) return envBin;

        // Check common locations
        var candidates = List.of(
                "google-chrome-stable",
                "google-chrome",
                "chromium-browser",
                "chromium",
                "/usr/bin/google-chrome-stable",
                "/usr/bin/google-chrome",
                "/usr/bin/chromium-browser",
                "/usr/bin/chromium",
                "/opt/google/chrome/chrome"
        );

        for (var candidate : candidates) {
            try {
                var p = new ProcessBuilder("which", candidate)
                        .redirectErrorStream(true).start();
                if (p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String guessContentType(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".mjs")) return "application/javascript";
        if (path.endsWith(".wasm")) return "application/wasm";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }
}
