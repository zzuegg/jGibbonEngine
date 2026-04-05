package dev.engine.ui;

/**
 * Axis-aligned rectangle.
 */
public record NkRect(float x, float y, float w, float h) {

    public static final NkRect ZERO = new NkRect(0, 0, 0, 0);

    public boolean contains(float px, float py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    public NkRect shrink(float amount) {
        return new NkRect(x + amount, y + amount, w - 2 * amount, h - 2 * amount);
    }

    public NkRect pad(float px, float py) {
        return new NkRect(x + px, y + py, w - 2 * px, h - 2 * py);
    }

    public NkRect intersect(NkRect other) {
        float ix = Math.max(x, other.x);
        float iy = Math.max(y, other.y);
        float ix2 = Math.min(x + w, other.x + other.w);
        float iy2 = Math.min(y + h, other.y + other.h);
        if (ix2 <= ix || iy2 <= iy) return new NkRect(ix, iy, 0, 0);
        return new NkRect(ix, iy, ix2 - ix, iy2 - iy);
    }
}
