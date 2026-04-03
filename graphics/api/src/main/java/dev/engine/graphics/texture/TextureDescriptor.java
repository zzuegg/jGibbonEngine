package dev.engine.graphics.texture;

public record TextureDescriptor(int width, int height, TextureFormat format, MipMode mipMode) {

    /** Backward-compatible constructor — defaults to AUTO mips for color, NONE for depth. */
    public TextureDescriptor(int width, int height, TextureFormat format) {
        this(width, height, format, isDepth(format) ? MipMode.NONE : MipMode.AUTO);
    }

    /** RGBA8 with AUTO mips. */
    public static TextureDescriptor rgba(int width, int height) {
        return new TextureDescriptor(width, height, TextureFormat.RGBA8, MipMode.AUTO);
    }

    /** Depth texture with NONE mips. */
    public static TextureDescriptor depth(int width, int height) {
        return new TextureDescriptor(width, height, TextureFormat.DEPTH32F, MipMode.NONE);
    }

    /** Creates a copy with a different MipMode. */
    public TextureDescriptor withMipMode(MipMode mode) {
        return new TextureDescriptor(width, height, format, mode);
    }

    private static boolean isDepth(TextureFormat format) {
        return format == TextureFormat.DEPTH24 || format == TextureFormat.DEPTH32F;
    }
}
