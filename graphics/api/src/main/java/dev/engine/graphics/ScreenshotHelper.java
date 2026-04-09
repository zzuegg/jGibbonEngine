package dev.engine.graphics;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Saves RGBA8 framebuffer data as PNG. Backend-agnostic.
 */
public final class ScreenshotHelper {

    private ScreenshotHelper() {}

    /** Saves RGBA8 byte array as a PNG file. Alpha is forced to opaque. */
    public static void save(byte[] rgba, int width, int height, String path) throws IOException {
        var img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (y * width + x) * 4;
                int r = rgba[i] & 0xFF;
                int g = rgba[i + 1] & 0xFF;
                int b = rgba[i + 2] & 0xFF;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        ImageIO.write(img, "png", new File(path));
    }

    /**
     * Compares two RGBA8 framebuffers pixel-by-pixel.
     * Returns the maximum per-channel difference across all pixels.
     */
    public static int maxDifference(byte[] a, byte[] b) {
        if (a.length != b.length) throw new IllegalArgumentException("Size mismatch: " + a.length + " vs " + b.length);
        int maxDiff = 0;
        for (int i = 0; i < a.length; i++) {
            int diff = Math.abs((a[i] & 0xFF) - (b[i] & 0xFF));
            maxDiff = Math.max(maxDiff, diff);
        }
        return maxDiff;
    }

    /**
     * Returns the percentage of pixels that differ by more than the given threshold.
     */
    public static double diffPercentage(byte[] a, byte[] b, int threshold) {
        if (a.length != b.length) throw new IllegalArgumentException("Size mismatch");
        int pixelCount = a.length / 4;
        int diffPixels = 0;
        for (int i = 0; i < pixelCount; i++) {
            int off = i * 4;
            boolean differs = false;
            for (int c = 0; c < 4; c++) {
                if (Math.abs((a[off + c] & 0xFF) - (b[off + c] & 0xFF)) > threshold) {
                    differs = true;
                    break;
                }
            }
            if (differs) diffPixels++;
        }
        return (double) diffPixels / pixelCount * 100.0;
    }
}
