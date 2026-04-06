package dev.engine.graphics.shader;

/**
 * Well-known names for the built-in global shader parameter blocks.
 *
 * <p>Use these constants instead of raw string literals when referencing the
 * built-in engine, camera, and object param blocks to avoid typos and make
 * refactoring easier.
 */
public final class GlobalParamNames {

    /** Name of the per-frame engine parameter block (time, delta, resolution, frame count). */
    public static final String ENGINE = "Engine";

    /** Name of the per-frame camera parameter block (view/projection matrices, position, clip planes). */
    public static final String CAMERA = "Camera";

    /** Name of the per-draw object parameter block (model matrix). */
    public static final String OBJECT = "Object";

    private GlobalParamNames() {}
}
