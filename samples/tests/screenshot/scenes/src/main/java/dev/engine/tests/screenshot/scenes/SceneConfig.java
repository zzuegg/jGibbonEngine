package dev.engine.tests.screenshot.scenes;

import dev.engine.graphics.common.engine.EngineConfig;
import dev.engine.graphics.window.WindowDescriptor;

import java.util.Set;

/**
 * Test configuration provided by each scene. Combines engine configuration
 * (viewport, debug overlay, etc.) with test-specific settings (tolerance,
 * capture frames).
 *
 * <p>The {@link #engineConfigBuilder()} provides a partially-filled EngineConfig
 * that the runner enriches with platform/backend specifics.
 */
public record SceneConfig(
        EngineConfig.Builder engineConfigBuilder,
        Set<Integer> captureFrames,
        Tolerance tolerance
) {
    /** Default: 256x256, capture frame 3, loose tolerance, no debug overlay. */
    public static SceneConfig defaults() {
        return new SceneConfig(
                EngineConfig.builder()
                        .window(WindowDescriptor.builder("Test").size(256, 256).build())
                        .maxFrames(0)
                        .debugOverlay(false),
                Set.of(3),
                Tolerance.loose());
    }

    public SceneConfig withTolerance(Tolerance tolerance) {
        return new SceneConfig(engineConfigBuilder, captureFrames, tolerance);
    }

    public SceneConfig withCaptureFrames(Set<Integer> captureFrames) {
        return new SceneConfig(engineConfigBuilder, captureFrames, tolerance);
    }

    /** Convenience: get window width from the builder's window descriptor. */
    public int width() {
        return engineConfigBuilder.build().window().width();
    }

    /** Convenience: get window height from the builder's window descriptor. */
    public int height() {
        return engineConfigBuilder.build().window().height();
    }
}
