package dev.engine.tests.screenshot.scenes;

import java.util.Map;
import java.util.Set;

/**
 * Configuration provided by each test scene. The runner uses this to set up
 * the viewport, determine capture frames, and select tolerance for comparison.
 */
public record SceneConfig(
        int width,
        int height,
        Set<Integer> captureFrames,
        Tolerance tolerance,
        boolean requiresShaderCompiler,
        Map<String, String> hints
) {
    public static SceneConfig defaults() {
        return new SceneConfig(256, 256, Set.of(3), Tolerance.loose(), false, Map.of());
    }

    public SceneConfig withTolerance(Tolerance tolerance) {
        return new SceneConfig(width, height, captureFrames, tolerance, requiresShaderCompiler, hints);
    }

    public SceneConfig withCaptureFrames(Set<Integer> captureFrames) {
        return new SceneConfig(width, height, captureFrames, tolerance, requiresShaderCompiler, hints);
    }

    public SceneConfig withShaderCompiler() {
        return new SceneConfig(width, height, captureFrames, tolerance, true, hints);
    }

    public SceneConfig withSize(int width, int height) {
        return new SceneConfig(width, height, captureFrames, tolerance, requiresShaderCompiler, hints);
    }
}
