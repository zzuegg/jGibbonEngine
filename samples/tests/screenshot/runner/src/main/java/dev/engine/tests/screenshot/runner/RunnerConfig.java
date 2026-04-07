package dev.engine.tests.screenshot.runner;

import java.nio.file.Path;

/**
 * Configuration for the test runner.
 *
 * @param sceneFilter if non-null, only run scenes matching this name (supports substring match)
 */
public record RunnerConfig(
        long timeoutMs,
        String profile,
        Path outputDir,
        Path referencesDir,
        String sceneFilter
) {
    public static RunnerConfig defaults(Path outputDir, Path referencesDir) {
        return new RunnerConfig(30_000, "local", outputDir, referencesDir, null);
    }

    public boolean matchesScene(String sceneName) {
        return sceneFilter == null || sceneFilter.isEmpty() || sceneName.contains(sceneFilter);
    }
}
