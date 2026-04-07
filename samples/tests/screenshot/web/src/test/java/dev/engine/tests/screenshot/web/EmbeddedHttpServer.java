package dev.engine.tests.screenshot.web;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal HTTP server for serving TeaVM build output to headless Chrome.
 * Uses JDK's built-in {@link HttpServer} — zero external dependencies.
 */
public class EmbeddedHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final int port;

    public EmbeddedHttpServer(Path webRoot) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        server.createContext("/", exchange -> {
            var path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/test.html";
            var file = webRoot.resolve(path.substring(1));

            // Prevent directory traversal
            if (!file.normalize().startsWith(webRoot.normalize())) {
                exchange.sendResponseHeaders(403, -1);
                exchange.close();
                return;
            }

            if (Files.exists(file) && !Files.isDirectory(file)) {
                var bytes = Files.readAllBytes(file);
                var mime = guessMime(path);
                exchange.getResponseHeaders().set("Content-Type", mime);
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                boolean isHead = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
                exchange.sendResponseHeaders(200, isHead ? -1 : bytes.length);
                if (!isHead) exchange.getResponseBody().write(bytes);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.close();
        });

        server.start();
    }

    public int port() { return port; }
    public String baseUrl() { return "http://127.0.0.1:" + port; }

    @Override
    public void close() {
        server.stop(0);
    }

    private static String guessMime(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".mjs")) return "application/javascript";
        if (path.endsWith(".wasm")) return "application/wasm";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".slang")) return "text/plain";
        return "application/octet-stream";
    }
}
