package dev.engine.tests.screenshot.analysis;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Image comparison and I/O utilities for screenshot testing.
 */
public final class ImageUtils {

    private ImageUtils() {}

    /**
     * Returns the percentage of pixels that differ by more than the given threshold
     * in any channel.
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

    /** Saves RGBA8 byte array as a PNG file. */
    public static void savePng(byte[] rgba, int width, int height, Path path) throws IOException {
        var img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (y * width + x) * 4;
                int r = rgba[i] & 0xFF;
                int g = rgba[i + 1] & 0xFF;
                int b = rgba[i + 2] & 0xFF;
                int a = rgba[i + 3] & 0xFF;
                img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        ImageIO.write(img, "png", path.toFile());
    }

    /** Loads a PNG file and converts to RGBA8 byte array. */
    public static byte[] loadPng(Path path, int expectedWidth, int expectedHeight) throws IOException {
        var image = ImageIO.read(path.toFile());
        if (image == null) throw new IOException("Failed to read image: " + path);
        var pixels = new byte[expectedWidth * expectedHeight * 4];
        for (int y = 0; y < expectedHeight; y++) {
            for (int x = 0; x < expectedWidth; x++) {
                int argb = image.getRGB(x, y);
                int idx = (y * expectedWidth + x) * 4;
                pixels[idx]     = (byte) ((argb >> 16) & 0xFF); // R
                pixels[idx + 1] = (byte) ((argb >> 8) & 0xFF);  // G
                pixels[idx + 2] = (byte) (argb & 0xFF);          // B
                pixels[idx + 3] = (byte) ((argb >> 24) & 0xFF); // A
            }
        }
        return pixels;
    }
}
