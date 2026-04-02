package dev.engine.core.asset;

import dev.engine.core.scene.Component;

import java.nio.ByteBuffer;

/**
 * Raw texture data — pixels + metadata. No GPU resources.
 * Can be used as a component on entities (for textures assigned to materials)
 * or standalone for compute shader access.
 *
 * <p>Supports uncompressed RGBA and GPU-compressed formats (BC, ASTC, ETC).
 */
public record TextureData(
        int width,
        int height,
        int channels,
        PixelFormat format,
        ByteBuffer pixels,
        int mipLevels,
        boolean compressed
) implements Component {

    /** Creates standard RGBA8 texture data. */
    public static TextureData rgba(int width, int height, ByteBuffer pixels) {
        return new TextureData(width, height, 4, PixelFormat.RGBA8, pixels, 1, false);
    }

    /** Creates with explicit format. */
    public static TextureData of(int width, int height, PixelFormat format, ByteBuffer pixels) {
        return new TextureData(width, height, format.channels(), format, pixels, 1, format.isCompressed());
    }

    /** Pixel format — extensible via interface pattern. */
    public interface PixelFormat {
        String name();
        int channels();
        int bytesPerPixel();
        boolean isCompressed();

        PixelFormat RGBA8 = new SimpleFormat("RGBA8", 4, 4, false);
        PixelFormat RGB8 = new SimpleFormat("RGB8", 3, 3, false);
        PixelFormat RG8 = new SimpleFormat("RG8", 2, 2, false);
        PixelFormat R8 = new SimpleFormat("R8", 1, 1, false);
        PixelFormat RGBA16F = new SimpleFormat("RGBA16F", 4, 8, false);
        PixelFormat RGBA32F = new SimpleFormat("RGBA32F", 4, 16, false);
        PixelFormat R16F = new SimpleFormat("R16F", 1, 2, false);
        PixelFormat R32F = new SimpleFormat("R32F", 1, 4, false);

        // Compressed
        PixelFormat BC1 = new SimpleFormat("BC1", 4, 0, true);
        PixelFormat BC3 = new SimpleFormat("BC3", 4, 0, true);
        PixelFormat BC5 = new SimpleFormat("BC5", 2, 0, true);
        PixelFormat BC7 = new SimpleFormat("BC7", 4, 0, true);
        PixelFormat ASTC_4x4 = new SimpleFormat("ASTC_4x4", 4, 0, true);
        PixelFormat ETC2_RGBA = new SimpleFormat("ETC2_RGBA", 4, 0, true);
    }

    record SimpleFormat(String name, int channels, int bytesPerPixel, boolean isCompressed) implements PixelFormat {}
}
