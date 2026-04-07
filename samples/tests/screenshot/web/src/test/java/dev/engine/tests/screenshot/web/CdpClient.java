package dev.engine.tests.screenshot.web;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Minimal Chrome DevTools Protocol client using JDK's built-in
 * {@link HttpClient} and {@link WebSocket}. Zero external dependencies.
 *
 * <p>Launches Chrome in headless mode with WebGPU enabled, connects via
 * the CDP WebSocket, and provides high-level methods for navigation,
 * JavaScript evaluation, and screenshot capture.
 */
public class CdpClient implements AutoCloseable {

    private static final Pattern WS_URL_PATTERN =
            Pattern.compile("DevTools listening on (ws://\\S+)");

    private final Process chromeProcess;
    private final WebSocket webSocket;
    private final Path userDataDir;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, CompletableFuture<String>> pending;
    private final StringBuilder chromeStderr;

    private CdpClient(Process chromeProcess, WebSocket webSocket, Path userDataDir,
                       ConcurrentHashMap<Integer, CompletableFuture<String>> pending,
                       StringBuilder chromeStderr) {
        this.chromeProcess = chromeProcess;
        this.webSocket = webSocket;
        this.userDataDir = userDataDir;
        this.pending = pending;
        this.chromeStderr = chromeStderr;
    }

    /** Returns captured Chrome stderr output (GPU errors, adapter info, etc.). */
    public String chromeStderr() {
        return chromeStderr.toString();
    }

    /**
     * Launches Chrome and connects to it via CDP.
     *
     * @param headless true for headless mode (fast, local). false for headed mode
     *                 under xvfb (CI) — required for WebGPU canvas rendering
     *                 because headless Chrome has no VkSurface support.
     */
    public static CdpClient launch(String chromeBinary, int windowWidth, int windowHeight,
                                    boolean headless) throws Exception {
        var userDataDir = Files.createTempDirectory("chrome-test-");

        var args = new ArrayList<>(List.of(
                chromeBinary,
                headless ? "--headless=new" : "--no-first-run",
                "--no-sandbox",
                "--disable-extensions",
                "--disable-dev-shm-usage",
                "--disable-background-timer-throttling",
                "--disable-renderer-backgrounding",
                "--remote-debugging-port=0",
                "--user-data-dir=" + userDataDir.toAbsolutePath(),
                "--window-size=" + windowWidth + "," + windowHeight,
                "--enable-unsafe-webgpu",
                "--enable-features=Vulkan",
                "--use-angle=vulkan",
                "about:blank"
        ));

        var pb = new ProcessBuilder(args);
        pb.redirectErrorStream(false);

        // Forward Mesa/Vulkan env vars for CI software rendering.
        // Without VK_ICD_FILENAMES, Dawn can't find lavapipe.
        for (var envKey : List.of("LIBGL_ALWAYS_SOFTWARE", "MESA_GL_VERSION_OVERRIDE",
                "MESA_GLSL_VERSION_OVERRIDE", "GALLIUM_DRIVER", "VK_ICD_FILENAMES",
                "VK_DRIVER_FILES", "DISPLAY")) {
            var val = System.getenv(envKey);
            if (val != null) pb.environment().put(envKey, val);
        }

        var process = pb.start();

        // Drain stdout in background to prevent pipe blocking
        var stdoutDrain = new Thread(() -> {
            try { process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream()); }
            catch (Exception ignored) {}
        }, "chrome-stdout-drain");
        stdoutDrain.setDaemon(true);
        stdoutDrain.start();

        // Parse the WebSocket URL from stderr, capturing all stderr output
        var stderrCapture = new StringBuilder();
        String wsUrl = readWebSocketUrl(process, stderrCapture);
        if (wsUrl == null) {
            process.destroyForcibly();
            throw new RuntimeException("Failed to get CDP WebSocket URL from Chrome. stderr: "
                    + stderrCapture.toString().substring(0, Math.min(500, stderrCapture.length())));
        }

        // Get the first tab's WebSocket URL via CDP HTTP endpoint
        int port = URI.create(wsUrl).getPort();
        String tabWsUrl = discoverTabWebSocket(port);
        if (tabWsUrl == null) {
            process.destroyForcibly();
            throw new RuntimeException("No tab found via CDP /json endpoint");
        }

        // Connect WebSocket to the tab.
        // The pending map is shared between the listener and the CdpClient instance.
        var pending = new ConcurrentHashMap<Integer, CompletableFuture<String>>();
        var listener = new CdpWebSocketListener(pending);
        var ws = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(tabWsUrl), listener)
                .get(10, TimeUnit.SECONDS);

        return new CdpClient(process, ws, userDataDir, pending, stderrCapture);
    }

    /**
     * Sends a CDP command and waits for the result.
     */
    public String send(String method, String params) throws Exception {
        int id = nextId.getAndIncrement();
        var future = new CompletableFuture<String>();
        pending.put(id, future);

        String msg = "{\"id\":" + id + ",\"method\":\"" + method + "\"";
        if (params != null && !params.isEmpty()) {
            msg += ",\"params\":" + params;
        }
        msg += "}";

        webSocket.sendText(msg, true);
        return future.get(30, TimeUnit.SECONDS);
    }

    /** Navigates to a URL and waits briefly for the page to start loading. */
    public void navigate(String url) throws Exception {
        send("Page.enable", null);
        send("Page.navigate", "{\"url\":\"" + escapeJson(url) + "\"}");
        Thread.sleep(500);
    }

    /** Evaluates JavaScript and returns the raw CDP response JSON. */
    public String evaluateJs(String expression) throws Exception {
        return send("Runtime.evaluate",
                "{\"expression\":\"" + escapeJson(expression) + "\",\"returnByValue\":true}");
    }

    /**
     * Polls a JS expression until it returns one of the accepted values or timeout.
     * Returns the matched value, or null on timeout.
     */
    public String waitForCondition(String jsExpression, long timeoutMs,
                                    String... acceptedValues) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            var result = evaluateJs(jsExpression);
            for (String val : acceptedValues) {
                if (result.contains("\"value\":\"" + val + "\"")) {
                    return val;
                }
            }
            Thread.sleep(300);
        }
        return null;
    }

    /**
     * Reads the canvas data URL from window._screenshotData (set by WebTestApp).
     * Returns the raw PNG bytes decoded from the data:image/png;base64,... URL.
     */
    public byte[] readCanvasScreenshot() throws Exception {
        var result = evaluateJs("window._screenshotData");
        // Extract the string value from CDP response
        int valIdx = result.indexOf("\"value\":\"");
        if (valIdx < 0) return null;
        int start = valIdx + 9;
        int end = result.indexOf("\"", start);
        String dataUrl = result.substring(start, end);
        if (dataUrl.isEmpty() || "null".equals(dataUrl)) return null;

        // Strip data:image/png;base64, prefix
        int commaIdx = dataUrl.indexOf(",");
        if (commaIdx < 0) return null;
        String base64 = dataUrl.substring(commaIdx + 1);
        return Base64.getDecoder().decode(base64);
    }

    /**
     * Takes a screenshot via CDP Page.captureScreenshot.
     * Captures the composited page content — works even when canvas.toDataURL()
     * fails on software renderers.
     *
     * @param clip if true, clips to the given viewport dimensions
     * @param width viewport width (used for clip)
     * @param height viewport height (used for clip)
     */
    public byte[] captureScreenshot(boolean clip, int width, int height) throws Exception {
        String params;
        if (clip) {
            params = "{\"format\":\"png\",\"captureBeyondViewport\":false," +
                     "\"clip\":{\"x\":0,\"y\":0,\"width\":" + width +
                     ",\"height\":" + height + ",\"scale\":1}}";
        } else {
            params = "{\"format\":\"png\",\"captureBeyondViewport\":false}";
        }
        var result = send("Page.captureScreenshot", params);
        int dataIdx = result.indexOf("\"data\":\"");
        if (dataIdx < 0) throw new RuntimeException("No screenshot data in CDP response");
        int start = dataIdx + 8;
        int end = result.indexOf("\"", start);
        String base64 = result.substring(start, end);
        return Base64.getDecoder().decode(base64);
    }

    /**
     * Sets the browser viewport size via CDP Emulation.setDeviceMetricsOverride.
     */
    public void setViewportSize(int width, int height) throws Exception {
        send("Emulation.setDeviceMetricsOverride",
                "{\"width\":" + width + ",\"height\":" + height +
                ",\"deviceScaleFactor\":1,\"mobile\":false}");
    }

    @Override
    public void close() {
        try {
            webSocket.sendClose(1000, "done").join();
        } catch (Exception ignored) {}
        chromeProcess.destroyForcibly();
        try {
            chromeProcess.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
        cleanupDir(userDataDir);
    }

    // --- Private helpers ---

    private static String readWebSocketUrl(Process process, StringBuilder stderrCapture)
            throws Exception {
        // Read stderr in a background thread — don't close the stream since
        // Chrome keeps writing to it. We just need the WS URL line.
        var reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        var wsUrlFuture = new CompletableFuture<String>();

        var stderrThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderrCapture.append(line).append('\n');
                    var matcher = WS_URL_PATTERN.matcher(line);
                    if (matcher.find()) {
                        wsUrlFuture.complete(matcher.group(1));
                    }
                }
            } catch (Exception ignored) {}
            if (!wsUrlFuture.isDone()) {
                wsUrlFuture.completeExceptionally(
                        new RuntimeException("Chrome stderr closed without WS URL"));
            }
        }, "chrome-stderr-drain");
        stderrThread.setDaemon(true);
        stderrThread.start();

        try {
            return wsUrlFuture.get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    private static String discoverTabWebSocket(int port) throws Exception {
        var client = HttpClient.newHttpClient();
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json"))
                .GET().build();

        for (int attempt = 0; attempt < 10; attempt++) {
            var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            var body = resp.body();

            // Find a "page" type entry (skip extension background pages)
            // JSON array entries are separated by }, {
            // Look for entries with "type":"page" and extract their webSocketDebuggerUrl
            int searchFrom = 0;
            while (searchFrom < body.length()) {
                int typeIdx = body.indexOf("\"type\"", searchFrom);
                if (typeIdx < 0) break;
                int typeValStart = body.indexOf("\"", typeIdx + 7) + 1;
                int typeValEnd = body.indexOf("\"", typeValStart);
                String type = body.substring(typeValStart, typeValEnd);
                searchFrom = typeValEnd + 1;

                if ("page".equals(type)) {
                    // Find the enclosing object boundaries
                    int objStart = body.lastIndexOf("{", typeIdx);
                    int objEnd = body.indexOf("}", typeIdx);
                    if (objStart >= 0 && objEnd > objStart) {
                        String obj = body.substring(objStart, objEnd + 1);
                        int wsIdx = obj.indexOf("\"webSocketDebuggerUrl\"");
                        if (wsIdx >= 0) {
                            int wsStart = obj.indexOf("\"", wsIdx + 22) + 1;
                            int wsEnd = obj.indexOf("\"", wsStart);
                            return obj.substring(wsStart, wsEnd);
                        }
                    }
                }
            }

            // Fallback: if no "page" type found, try the first entry with webSocketDebuggerUrl
            int idx = body.indexOf("\"webSocketDebuggerUrl\"");
            if (idx >= 0) {
                int start = body.indexOf("\"", idx + 22) + 1;
                int end = body.indexOf("\"", start);
                return body.substring(start, end);
            }
            Thread.sleep(200);
        }
        return null;
    }

    private static void cleanupDir(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.delete(p); } catch (Exception ignored) {}
                    });
        } catch (Exception ignored) {}
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    /** WebSocket listener that dispatches CDP responses to pending futures. */
    private static class CdpWebSocketListener implements WebSocket.Listener {
        private final ConcurrentHashMap<Integer, CompletableFuture<String>> pending;
        private final StringBuilder buffer = new StringBuilder();

        CdpWebSocketListener(ConcurrentHashMap<Integer, CompletableFuture<String>> pending) {
            this.pending = pending;
        }

        @Override
        public void onOpen(WebSocket ws) {
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String msg = buffer.toString();
                buffer.setLength(0);

                // Extract "id" field for response dispatch
                int idIdx = msg.indexOf("\"id\":");
                if (idIdx >= 0) {
                    int start = idIdx + 5;
                    int end = start;
                    while (end < msg.length() && Character.isDigit(msg.charAt(end))) end++;
                    if (end > start) {
                        int id = Integer.parseInt(msg.substring(start, end));
                        var future = pending.remove(id);
                        if (future != null) future.complete(msg);
                    }
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            pending.values().forEach(f -> f.completeExceptionally(
                    new RuntimeException("WebSocket closed: " + code + " " + reason)));
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            pending.values().forEach(f -> f.completeExceptionally(error));
        }
    }
}
