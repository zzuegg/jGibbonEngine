package dev.engine.tests.screenshot.desktop;

import dev.engine.tests.screenshot.runner.RunnerConfig;

import java.nio.file.Path;

/**
 * Entry point for the desktop runner Gradle task.
 *
 * <p>Args: {@code <manifestPath> <outputDir> <referencesDir> <profile>}
 */
public class DesktopRunnerMain {

    public static void main(String[] args) throws Exception {
        var manifestPath = Path.of(args[0]);
        var outputDir = Path.of(args[1]);
        var referencesDir = Path.of(args[2]);
        var profile = args.length > 3 ? args[3] : "local";

        var config = new RunnerConfig(30_000, profile, outputDir, referencesDir);
        var runner = new DesktopRunner(config.timeoutMs());

        System.out.println("Running desktop screenshot tests...");
        System.out.println("  Manifest: " + manifestPath);
        System.out.println("  Output:   " + outputDir);
        System.out.println("  Profile:  " + profile);
        System.out.println("  Backends: " + runner.backends());

        runner.run(manifestPath, config);

        System.out.println("Desktop runner complete.");
    }
}
