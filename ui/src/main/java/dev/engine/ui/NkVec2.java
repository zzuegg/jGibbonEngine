package dev.engine.ui;

/**
 * 2D vector for UI positioning.
 */
public record NkVec2(float x, float y) {

    public static final NkVec2 ZERO = new NkVec2(0, 0);

    public NkVec2 add(float dx, float dy) {
        return new NkVec2(x + dx, y + dy);
    }

    public NkVec2 add(NkVec2 other) {
        return new NkVec2(x + other.x, y + other.y);
    }
}
