package dev.engine.graphics;

/**
 * Configuration passed to graphics backend factories.
 * Contains settings that affect how the backend initializes.
 *
 * @param headless if true, skip surface/presentation setup (offscreen only)
 */
public record GraphicsConfig(
        boolean headless
) {
    /** Default config for interactive rendering (presents to window surface). */
    public static final GraphicsConfig DEFAULT = new GraphicsConfig(false);

    /** Config for offscreen / headless rendering (no surface presentation). */
    public static final GraphicsConfig HEADLESS = new GraphicsConfig(true);
}
