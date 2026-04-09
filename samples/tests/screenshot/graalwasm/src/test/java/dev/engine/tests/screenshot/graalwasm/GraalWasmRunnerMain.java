package dev.engine.tests.screenshot.graalwasm;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JVM entry point for the GraalWasm screenshot test runner.
 * Finds Chrome, parses args, and delegates to {@link GraalWasmRunner}.
 */
public class GraalWasmRunnerMain {

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("Usage: <manifest> <outputDir> <referencesDir> <profile> <wasmRoot> [sceneFilter]");
            System.exit(1);
        }

        var manifestPath = Path.of(args[0]);
        var outputDir = Path.of(args[1]);
        var referencesDir = Path.of(args[2]);
        var profile = args[3];
        var wasmRoot = Path.of(args[4]);
        var sceneFilter = args.length > 5 ? args[5] : "";

        var chromeBin = findChrome();
        if (chromeBin == null) {
            System.err.println("Chrome not found. Set CHROME_BIN or install Chrome.");
            System.exit(1);
        }

        // Verify WASM files exist
        if (!Files.exists(wasmRoot.resolve("main.js"))) {
            System.err.println("WASM not compiled: " + wasmRoot.resolve("main.js") + " not found.");
            System.err.println("Run ./gradlew :samples:tests:screenshot:graalwasm-runner:wasmCompile first.");
            System.exit(1);
        }

        System.out.println("GraalWasm screenshot runner");
        System.out.println("  Chrome: " + chromeBin);
        System.out.println("  WASM root: " + wasmRoot);
        System.out.println("  Profile: " + profile);

        var runner = new GraalWasmRunner(wasmRoot, chromeBin, profile);
        runner.run(manifestPath,
                new dev.engine.tests.screenshot.runner.RunnerConfig(
                        120_000, profile, outputDir, referencesDir, sceneFilter));
    }

    private static String findChrome() {
        var env = System.getenv("CHROME_BIN");
        if (env != null && Files.isExecutable(Path.of(env))) return env;
        for (var p : new String[]{
                "/usr/bin/google-chrome", "/usr/bin/google-chrome-stable",
                "/usr/bin/chromium-browser", "/usr/bin/chromium",
                "/snap/bin/chromium"}) {
            if (Files.isExecutable(Path.of(p))) return p;
        }
        try {
            var proc = new ProcessBuilder("which", "google-chrome").start();
            var path = new String(proc.getInputStream().readAllBytes()).trim();
            if (proc.waitFor() == 0 && !path.isEmpty()) return path;
        } catch (Exception ignored) {}
        return null;
    }
}
