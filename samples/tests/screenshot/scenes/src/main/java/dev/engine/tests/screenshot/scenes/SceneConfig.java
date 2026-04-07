package dev.engine.tests.screenshot.scenes;

import dev.engine.graphics.common.engine.EngineConfig;
import dev.engine.graphics.window.WindowDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Test configuration provided by each scene. Combines engine configuration
 * (viewport, debug overlay, etc.) with test-specific settings (tolerance,
 * capture frames, known limitations).
 *
 * <p>The {@link #engineConfigBuilder()} provides a partially-filled EngineConfig
 * that the runner enriches with platform/backend specifics.
 */
public record SceneConfig(
        EngineConfig.Builder engineConfigBuilder,
        Set<Integer> captureFrames,
        Tolerance tolerance,
        List<KnownLimitation> knownLimitations
) {

    /**
     * Declares a known API limitation for a specific backend. Any cross-backend
     * comparison involving that backend will be marked as "known_limitation"
     * (orange) instead of "fail" (red) when it exceeds tolerance.
     *
     * @param backend the backend with the limitation (e.g. "webgpu")
     * @param reason  human-readable explanation
     */
    public record KnownLimitation(String backend, String reason) {

        /** Returns true if this limitation applies to a comparison involving the given backends. */
        public boolean matchesPair(String a, String b) {
            return backend.equals(a) || backend.equals(b);
        }
    }

    /** Default: 256x256, capture frame 3, loose tolerance, no known limitations. */
    public static SceneConfig defaults() {
        return new SceneConfig(
                EngineConfig.builder()
                        .window(WindowDescriptor.builder("Test").size(256, 256).build())
                        .maxFrames(0)
                        .debugOverlay(false),
                Set.of(3),
                Tolerance.loose(),
                List.of());
    }

    public SceneConfig withTolerance(Tolerance tolerance) {
        return new SceneConfig(engineConfigBuilder, captureFrames, tolerance, knownLimitations);
    }

    public SceneConfig withCaptureFrames(Set<Integer> captureFrames) {
        return new SceneConfig(engineConfigBuilder, captureFrames, tolerance, knownLimitations);
    }

    /**
     * Declares a known limitation for a backend. Cross-backend comparisons
     * involving this backend will be treated as warnings, not failures.
     *
     * <pre>{@code
     * SceneConfig.defaults()
     *     .withKnownLimitation("webgpu", "Wireframe not supported on WebGPU")
     * }</pre>
     */
    public SceneConfig withKnownLimitation(String backend, String reason) {
        var updated = new ArrayList<>(knownLimitations);
        updated.add(new KnownLimitation(backend, reason));
        return new SceneConfig(engineConfigBuilder, captureFrames, tolerance, updated);
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
