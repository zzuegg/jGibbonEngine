package dev.engine.tests.screenshot.web;

import dev.engine.graphics.ScreenshotHelper;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Captures screenshots from headless Chrome and saves them as reference images
 * for regression testing.
 *
 * <p>Run via: {@code ./gradlew :samples:tests:screenshot:web:saveReferences}
 */
public class SaveWebReferences {

    private static final int WIDTH = 256;
    private static final int HEIGHT = 256;

    public static void main(String[] args) throws Exception {
        var webDir = Path.of("build/web");
        if (!Files.exists(webDir) || !Files.exists(webDir.resolve("js/web-test.js"))) {
            System.err.println("TeaVM build not found. Run assembleWebTest first.");
            System.exit(1);
        }

        // Start HTTP server
        var httpServer = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = httpServer.getAddress().getPort();
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
        System.out.println("HTTP server on port " + port);

        // Find Chrome
        var chromeBin = findChromeBinary();
        if (chromeBin == null) {
            System.err.println("Chrome not found. Set CHROME_BIN env var.");
            System.exit(1);
        }

        var appUrl = "http://127.0.0.1:" + port + "/";
        var cdp = ChromeDevTools.launch(chromeBin, appUrl);
        cdp.send("Runtime.enable", null);

        // Wait for app to be ready
        System.out.println("Waiting for app to initialize...");
        for (int i = 0; i < 60; i++) {
            var status = cdp.evaluateString("window._testStatus || 'unknown'");
            if ("ready".equals(status)) break;
            if (status != null && status.contains("error")) {
                System.err.println("App error: " + status);
                System.exit(1);
            }
            Thread.sleep(500);
        }

        // Start rendering
        cdp.evaluate("window._startRendering = true");
        Thread.sleep(1000);

        var scenes = WebSceneRegistry.discoverScenes();
        var refDir = new File("src/test/resources/reference-screenshots/webgpu-browser");
        refDir.mkdirs();
        int saved = 0;
        int skipped = 0;

        for (var scene : scenes) {
            var name = scene.name();
            int[] frames = scene.scene().captureFrames();
            String suffix = frames.length > 1 ? "_f" + frames[frames.length - 1] : "";
            String captureName = name + suffix;

            // Wait for scene
            String captureReady = null;
            for (int i = 0; i < 300; i++) {
                captureReady = cdp.evaluateString("window._captureReady || ''");
                if (captureName.equals(captureReady)) break;
                if (captureReady != null && captureReady.startsWith("ERROR:")) break;
                var done = cdp.evaluateBoolean("!!window._testsDone");
                if (Boolean.TRUE.equals(done)) break;
                Thread.sleep(100);
            }

            if (captureReady != null && captureReady.startsWith("ERROR:")) {
                System.out.println("  SKIP " + name + " (scene error)");
                cdp.evaluate("window._captureAck = true");
                skipped++;
                continue;
            }

            if (!captureName.equals(captureReady)) {
                System.out.println("  SKIP " + name + " (not rendered)");
                skipped++;
                continue;
            }

            // Capture and save
            byte[] pixels = cdp.readCanvasPixels(WIDTH, HEIGHT);
            cdp.evaluate("window._captureAck = true");

            if (pixels != null) {
                ScreenshotHelper.save(pixels, WIDTH, HEIGHT,
                        refDir.getPath() + "/" + captureName + ".png");
                System.out.println("  SAVED " + captureName + ".png");
                saved++;
            } else {
                System.out.println("  SKIP " + name + " (capture failed)");
                skipped++;
            }
        }

        cdp.close();
        httpServer.stop(0);

        System.out.println("\nSaved " + saved + " reference screenshots (" + skipped + " skipped).");
        System.out.println("Commit them to track regressions:");
        System.out.println("  git add samples/tests/screenshot/web/src/test/resources/reference-screenshots/");
    }

    private static String findChromeBinary() {
        var envBin = System.getenv("CHROME_BIN");
        if (envBin != null && !envBin.isEmpty()) return envBin;
        for (var candidate : List.of("google-chrome-stable", "google-chrome", "chromium-browser", "chromium",
                "/usr/bin/google-chrome-stable", "/usr/bin/google-chrome", "/opt/google/chrome/chrome")) {
            try {
                var p = new ProcessBuilder("which", candidate).redirectErrorStream(true).start();
                if (p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0) return candidate;
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
