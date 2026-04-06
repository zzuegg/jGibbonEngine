package dev.engine.tests.screenshot;

/**
 * Pixel comparison tolerance for screenshot tests.
 *
 * @param maxChannelDiff maximum per-channel difference (0-255) before a pixel counts as different
 * @param maxDiffPercent maximum percentage of different pixels allowed
 */
public record Tolerance(int maxChannelDiff, double maxDiffPercent) {

    /** Exact match — zero tolerance. */
    public static Tolerance exact() { return new Tolerance(0, 0.0); }

    /** Tight — allows minor rounding differences. */
    public static Tolerance tight() { return new Tolerance(1, 0.001); }

    /** Loose — allows minor cross-backend variation. */
    public static Tolerance loose() { return new Tolerance(2, 0.01); }

    /** Wide — for known cross-backend differences (blend mode rasterization). */
    public static Tolerance wide() { return new Tolerance(3, 0.5); }

    /** Cross-platform — for browser vs native WebGPU comparison (different GPU paths). */
    public static Tolerance crossPlatform() { return new Tolerance(5, 15.0); }
}
