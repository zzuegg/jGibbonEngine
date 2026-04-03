package dev.engine.core.shader.params;

import dev.engine.core.math.Vec2;

/**
 * Engine-wide parameters uploaded to the GPU each frame.
 * Used by {@link dev.engine.core.shader.SlangParamsBlock} to generate
 * the Slang {@code engine} global (e.g., {@code engine.get_time()}).
 */
public record EngineParams(
        float time,
        float deltaTime,
        Vec2 resolution,
        int frameCount
) {}
