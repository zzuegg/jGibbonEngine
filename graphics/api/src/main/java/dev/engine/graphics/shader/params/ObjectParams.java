package dev.engine.graphics.shader.params;

import dev.engine.core.layout.NativeStruct;
import dev.engine.core.math.Mat4;

/**
 * Per-object parameters uploaded to the GPU each draw call.
 * Used by {@link dev.engine.core.shader.SlangParamsBlock} to generate
 * the Slang {@code object} global (e.g., {@code object.model()}).
 */
@NativeStruct
public record ObjectParams(Mat4 world) {}
