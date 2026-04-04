package dev.engine.graphics.shader.params;

import dev.engine.core.layout.NativeStruct;
import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;

/**
 * Camera parameters uploaded to the GPU each frame.
 * Used by {@link dev.engine.core.shader.SlangParamsBlock} to generate
 * the Slang {@code camera} global (e.g., {@code camera.get_viewProjection()}).
 */
@NativeStruct
public record CameraParams(
        Mat4 viewProjection,
        Mat4 view,
        Mat4 projection,
        Vec3 position,
        float near,
        float far
) {}
