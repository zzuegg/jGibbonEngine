package dev.engine.tests.screenshot.runner;

import java.nio.file.Path;

/**
 * Configuration for the test runner.
 */
public record RunnerConfig(
        long timeoutMs,
        String profile,
        Path outputDir,
        Path referencesDir
) {
    public static RunnerConfig defaults(Path outputDir, Path referencesDir) {
        return new RunnerConfig(30_000, "local", outputDir, referencesDir);
    }
}
