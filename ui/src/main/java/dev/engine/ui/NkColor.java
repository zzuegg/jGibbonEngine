package dev.engine.ui;

/**
 * RGBA color with 8-bit components.
 */
public record NkColor(int r, int g, int b, int a) {

    public static NkColor rgba(int r, int g, int b, int a) {
        return new NkColor(r, g, b, a);
    }

    public static NkColor rgb(int r, int g, int b) {
        return new NkColor(r, g, b, 255);
    }

    public int toPackedABGR() {
        return ((a & 0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | (r & 0xFF);
    }

    public NkColor withAlpha(int alpha) {
        return new NkColor(r, g, b, alpha);
    }

    public static NkColor lerp(NkColor a, NkColor b, float t) {
        return new NkColor(
                (int) (a.r + (b.r - a.r) * t),
                (int) (a.g + (b.g - a.g) * t),
                (int) (a.b + (b.b - a.b) * t),
                (int) (a.a + (b.a - a.a) * t));
    }
}
