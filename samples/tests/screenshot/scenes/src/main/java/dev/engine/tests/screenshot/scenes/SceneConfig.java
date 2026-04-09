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
 *
 * <p>Two tolerances are supported:
 * <ul>
 *   <li>{@link #tolerance()} — used for reference comparisons (strict; catches regressions
 *       within the same backend across runs)</li>
 *   <li>{@link #crossBackendTolerance()} — used when comparing screenshots across different
 *       backends (permissive by default; different rendering APIs and software implementations
 *       legitimately produce small sub-pixel differences)</li>
 * </ul>
 */
public record SceneConfig(
        EngineConfig.Builder engineConfigBuilder,
        Set<Integer> captureFrames,
        Tolerance tolerance,
        Tolerance crossBackendTolerance,
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

    /**
     * Default: 256x256, capture frame 3, loose reference tolerance,
     * wide cross-backend tolerance, no known limitations.
     *
     * <p>The wide default cross-backend tolerance (0.5%) reflects that different
     * rendering APIs (OpenGL, Vulkan, WebGPU) and different software implementations
     * used in CI (Mesa lavapipe vs Chrome SwiftShader) legitimately produce
     * sub-pixel differences. Reference comparisons remain strict.
     */
    public static SceneConfig defaults() {
        return new SceneConfig(
                EngineConfig.builder()
                        .window(WindowDescriptor.builder("Test").size(256, 256).build())
                        .maxFrames(0)
                        .debugOverlay(false),
                Set.of(3),
                Tolerance.loose(),
                Tolerance.wide(),
                List.of());
    }

    public SceneConfig withTolerance(Tolerance tolerance) {
        return new SceneConfig(engineConfigBuilder, captureFrames, tolerance, crossBackendTolerance, knownLimitations);
    }

    /** Overrides the tolerance used for cross-backend comparisons. */
    public SceneConfig withCrossBackendTolerance(Tolerance crossBackendTolerance) {
        return new SceneConfig(engineConfigBuilder, captureFrames, tolerance, crossBackendTolerance, knownLimitations);
    }

    public SceneConfig withCaptureFrames(Set<Integer> captureFrames) {
        return new SceneConfig(engineConfigBuilder, captureFrames, tolerance, crossBackendTolerance, knownLimitations);
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
        return new SceneConfig(engineConfigBuilder, captureFrames, tolerance, crossBackendTolerance, updated);
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
