package dev.engine.tests.screenshot.web;

import dev.engine.tests.screenshot.runner.RunnerConfig;

import java.io.File;
import java.nio.file.Path;

/**
 * Entry point for the web runner Gradle task.
 *
 * <p>Args: {@code <manifestPath> <outputDir> <referencesDir> <profile> <webRoot> [sceneFilter]}
 */
public class WebRunnerMain {

    public static void main(String[] args) throws Exception {
        var manifestPath = Path.of(args[0]);
        var outputDir = Path.of(args[1]);
        var referencesDir = Path.of(args[2]);
        var profile = args.length > 3 ? args[3] : "local";
        var webRoot = Path.of(args[4]);
        var sceneFilter = args.length > 5 ? args[5] : null;

        String chromeBinary = findChrome();

        var config = new RunnerConfig(60_000, profile, outputDir, referencesDir, sceneFilter);
        var runner = new WebRunner(webRoot, chromeBinary);

        System.out.println("Running web screenshot tests...");
        System.out.println("  Manifest: " + manifestPath);
        System.out.println("  Output:   " + outputDir);
        System.out.println("  WebRoot:  " + webRoot);
        System.out.println("  Profile:  " + profile);
        System.out.println("  Chrome:   " + chromeBinary);
        System.out.println("  Backend:  " + runner.backends());

        runner.run(manifestPath, config);

        System.out.println("Web runner complete.");
    }

    private static String findChrome() {
        // Check env var first
        String envChrome = System.getenv("CHROME_BIN");
        if (envChrome != null && !envChrome.isEmpty()) return envChrome;

        // Try common paths
        for (var candidate : new String[]{
                "/usr/bin/google-chrome",
                "/usr/bin/chromium",
                "/usr/bin/chromium-browser",
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
        }) {
            if (new File(candidate).canExecute()) return candidate;
        }

        // Try PATH
        for (var name : new String[]{"google-chrome", "chromium", "chromium-browser"}) {
            try {
                var pb = new ProcessBuilder("which", name);
                var p = pb.start();
                if (p.waitFor() == 0) {
                    return new String(p.getInputStream().readAllBytes()).trim();
                }
            } catch (Exception ignored) {}
        }

        throw new RuntimeException(
                "Chrome not found. Set CHROME_BIN env var or install google-chrome.");
    }
}
