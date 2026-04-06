package dev.engine.tests.screenshot.web;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Minimal Chrome DevTools Protocol client using JDK's built-in WebSocket.
 *
 * <p>Launches headless Chrome with WebGPU enabled and communicates via CDP.
 * Zero external dependencies — uses only {@code java.net.http}.
 */
public class ChromeDevTools implements AutoCloseable {

    private static final int CDP_PORT = 9222;
    private static final long DEFAULT_TIMEOUT_MS = 30_000;

    private final Process chromeProcess;
    private final WebSocket ws;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
    private final Path userDataDir;

    private ChromeDevTools(Process chromeProcess, WebSocket ws, Path userDataDir) {
        this.chromeProcess = chromeProcess;
        this.ws = ws;
        this.userDataDir = userDataDir;
    }

    /**
     * Launches headless Chrome with WebGPU and connects via CDP.
     *
     * @param chromeBinary path to chrome/chromium binary (e.g., "google-chrome", "chromium-browser")
     */
    /**
     * Launches headless Chrome with WebGPU and connects via CDP.
     *
     * @param chromeBinary path to chrome/chromium binary
     * @param initialUrl   URL to load on startup (use "about:blank" for no page)
     */
    public static ChromeDevTools launch(String chromeBinary, String initialUrl) throws Exception {
        var userDataDir = Files.createTempDirectory("chrome-cdp-");

        var args = new ArrayList<>(List.of(
                chromeBinary,
                "--headless=new",
                "--no-sandbox",
                "--disable-gpu-sandbox",
                "--enable-unsafe-webgpu",
                "--enable-features=Vulkan,UseSkiaGraphite",
                "--use-angle=vulkan",
                "--remote-debugging-port=" + CDP_PORT,
                "--user-data-dir=" + userDataDir.toAbsolutePath(),
                "--window-size=256,256",
                "--disable-extensions",
                "--disable-background-networking",
                "--no-first-run",
                initialUrl
        ));

        var pb = new ProcessBuilder(args)
                .redirectErrorStream(true);
        var process = pb.start();

        // Wait for CDP to become available, then find the page target matching our URL
        // (not the browser target or extension background pages)
        var client = HttpClient.newHttpClient();
        String wsUrl = null;
        for (int attempt = 0; attempt < 50; attempt++) {
            try {
                Thread.sleep(200);
                var req = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + CDP_PORT + "/json"))
                        .GET().build();
                var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                var body = resp.body();
                // /json returns a JSON array of targets. Find the "page" type target
                // matching our URL (not extension background pages).
                wsUrl = findPageTargetWsUrl(body, initialUrl);
                if (wsUrl != null) break;
                // Fallback: try any page type target
                if (wsUrl == null) wsUrl = findPageTargetWsUrl(body, null);
                if (wsUrl != null) break;
            } catch (Exception ignored) {
                // Chrome not ready yet
            }
        }

        if (wsUrl == null) {
            process.destroyForcibly();
            throw new IOException("Chrome CDP did not become available within 10 seconds");
        }

        // Create the ChromeDevTools instance first so the WebSocket listener
        // can reference its pending map directly (avoiding the stale-capture bug).
        var cdpHolder = new ChromeDevTools[1];
        var messageBuffer = new StringBuilder();
        var webSocket = client.newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                messageBuffer.append(data);
                if (last) {
                    var message = messageBuffer.toString();
                    messageBuffer.setLength(0);
                    var id = extractJsonInt(message, "id");
                    if (id != null && cdpHolder[0] != null) {
                        var future = cdpHolder[0].pending.remove(id);
                        if (future != null) future.complete(message);
                    }
                }
                webSocket.request(1);
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                System.err.println("[CDP] WebSocket error: " + error.getMessage());
            }
        }).get(10, TimeUnit.SECONDS);

        var cdp = new ChromeDevTools(process, webSocket, userDataDir);
        cdpHolder[0] = cdp;
        return cdp;
    }

    /**
     * Sends a CDP command and waits for the response.
     */
    public String send(String method, String params) throws Exception {
        int id = nextId.getAndIncrement();
        var future = new CompletableFuture<String>();
        pending.put(id, future);

        String message;
        if (params != null && !params.isEmpty()) {
            message = "{\"id\":" + id + ",\"method\":\"" + method + "\",\"params\":" + params + "}";
        } else {
            message = "{\"id\":" + id + ",\"method\":\"" + method + "\"}";
        }

        ws.sendText(message, true);
        return future.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Navigates to the given URL and waits for load.
     */
    public void navigate(String url) throws Exception {
        send("Page.enable", null);
        send("Runtime.enable", null);
        // Use JS navigation which is more reliable in headless Chrome
        evaluate("window.location.href = '" + url + "'");
        // Wait for navigation and page load
        Thread.sleep(3000);
    }

    /**
     * Evaluates JavaScript in the page and returns the result value.
     */
    public String evaluate(String expression) throws Exception {
        var escaped = expression.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        var response = send("Runtime.evaluate",
                "{\"expression\":\"" + escaped + "\",\"returnByValue\":true,\"awaitPromise\":false}");
        return response;
    }

    /**
     * Evaluates JS and extracts the string value from the result.
     */
    public String evaluateString(String expression) throws Exception {
        var response = evaluate(expression);
        return extractNestedJsonString(response, "value");
    }

    /**
     * Evaluates JS and extracts a boolean value from the result.
     */
    public Boolean evaluateBoolean(String expression) throws Exception {
        var response = evaluate(expression);
        if (response.contains("\"value\":true")) return true;
        if (response.contains("\"value\":false")) return false;
        return null;
    }

    /**
     * Evaluates JS and extracts an integer value from the result.
     */
    public Integer evaluateInt(String expression) throws Exception {
        var response = evaluate(expression);
        return extractNestedJsonInt(response, "value");
    }

    /**
     * Captures a screenshot of the page as a PNG byte array.
     */
    public byte[] captureScreenshot() throws Exception {
        var response = send("Page.captureScreenshot",
                "{\"format\":\"png\",\"clip\":{\"x\":0,\"y\":0,\"width\":256,\"height\":256,\"scale\":1}}");
        var base64 = extractJsonString(response, "data");
        if (base64 == null) return null;
        return Base64.getDecoder().decode(base64);
    }

    /**
     * Reads pixels from the canvas element via a 2D context copy.
     * Returns RGBA byte array (256x256x4).
     */
    public byte[] readCanvasPixels(int width, int height) throws Exception {
        var js = """
            (() => {
                const canvas = document.getElementById('canvas');
                const off = new OffscreenCanvas(%d, %d);
                const ctx = off.getContext('2d');
                ctx.drawImage(canvas, 0, 0);
                const data = ctx.getImageData(0, 0, %d, %d).data;
                let hex = '';
                for (let i = 0; i < data.length; i++) {
                    hex += data[i].toString(16).padStart(2, '0');
                }
                return hex;
            })()
            """.formatted(width, height, width, height);
        var hexStr = evaluateString(js);
        if (hexStr == null || hexStr.isEmpty()) return null;
        return hexToBytes(hexStr);
    }

    @Override
    public void close() {
        try { ws.sendClose(1000, "done").join(); } catch (Exception ignored) {}
        chromeProcess.destroyForcibly();
        try {
            // Clean up temp user data dir
            try (var walk = Files.walk(userDataDir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        } catch (Exception ignored) {}
    }

    // --- Target discovery ---

    /**
     * Parses the /json CDP endpoint response to find a page target.
     * The response is a JSON array of target objects. We look for targets with
     * {@code "type":"page"} and optionally match the URL.
     */
    static String findPageTargetWsUrl(String jsonArray, String urlPrefix) {
        // Split the array into individual target objects (simple heuristic)
        // Each target is a {...} block in the array
        int depth = 0;
        int objStart = -1;
        for (int i = 0; i < jsonArray.length(); i++) {
            char c = jsonArray.charAt(i);
            if (c == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    var obj = jsonArray.substring(objStart, i + 1);
                    var type = extractJsonString(obj, "type");
                    if ("page".equals(type)) {
                        if (urlPrefix == null) {
                            return extractJsonString(obj, "webSocketDebuggerUrl");
                        }
                        var url = extractJsonString(obj, "url");
                        if (url != null && url.startsWith(urlPrefix)) {
                            return extractJsonString(obj, "webSocketDebuggerUrl");
                        }
                    }
                }
            }
        }
        return null;
    }

    // --- JSON helpers (minimal, no external deps) ---

    private static final Pattern STRING_PATTERN = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern INT_PATTERN = Pattern.compile("\"(\\w+)\"\\s*:\\s*(\\d+)");

    static String extractJsonString(String json, String key) {
        var matcher = STRING_PATTERN.matcher(json);
        while (matcher.find()) {
            if (matcher.group(1).equals(key)) return matcher.group(2);
        }
        return null;
    }

    static Integer extractJsonInt(String json, String key) {
        var matcher = INT_PATTERN.matcher(json);
        while (matcher.find()) {
            if (matcher.group(1).equals(key)) return Integer.parseInt(matcher.group(2));
        }
        return null;
    }

    /**
     * Extracts a string value from nested JSON (e.g., result.result.value).
     */
    static String extractNestedJsonString(String json, String key) {
        // Find all occurrences and return the last one (deepest nested)
        String result = null;
        var matcher = STRING_PATTERN.matcher(json);
        while (matcher.find()) {
            if (matcher.group(1).equals(key)) result = matcher.group(2);
        }
        return result;
    }

    static Integer extractNestedJsonInt(String json, String key) {
        Integer result = null;
        var matcher = INT_PATTERN.matcher(json);
        while (matcher.find()) {
            if (matcher.group(1).equals(key)) result = Integer.parseInt(matcher.group(2));
        }
        return result;
    }

    private static byte[] hexToBytes(String hex) {
        var bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
