package dev.engine.graphics;

/**
 * Defines a rectangular region of the render target to draw into.
 *
 * @param x      left edge in pixels
 * @param y      top edge in pixels
 * @param width  width in pixels
 * @param height height in pixels
 */
public record Viewport(int x, int y, int width, int height) {

    /** Full-size viewport starting at origin. */
    public static Viewport of(int width, int height) {
        return new Viewport(0, 0, width, height);
    }

    public float aspectRatio() {
        return (float) width / Math.max(height, 1);
    }
}
