package dev.engine.graphics.texture;

public record TextureDescriptor(
    TextureType type,
    int width, int height, int depth,
    int layers,
    TextureFormat format,
    MipMode mipMode
) {

    /** Backward-compatible 2D constructor (4 args). */
    public TextureDescriptor(int width, int height, TextureFormat format, MipMode mipMode) {
        this(TextureType.TEXTURE_2D, width, height, 1, 1, format, mipMode);
    }

    /** Backward-compatible 3-arg constructor — defaults to AUTO mips for color, NONE for depth. */
    public TextureDescriptor(int width, int height, TextureFormat format) {
        this(TextureType.TEXTURE_2D, width, height, 1, 1, format, isDepth(format) ? MipMode.NONE : MipMode.AUTO);
    }

    /** RGBA8 with AUTO mips. */
    public static TextureDescriptor rgba(int width, int height) {
        return new TextureDescriptor(TextureType.TEXTURE_2D, width, height, 1, 1, TextureFormat.RGBA8, MipMode.AUTO);
    }

    /** Depth texture with NONE mips. */
    public static TextureDescriptor depth(int width, int height) {
        return new TextureDescriptor(TextureType.TEXTURE_2D, width, height, 1, 1, TextureFormat.DEPTH32F, MipMode.NONE);
    }

    /** 3D volume texture. */
    public static TextureDescriptor texture3d(int width, int height, int depth, TextureFormat format) {
        return new TextureDescriptor(TextureType.TEXTURE_3D, width, height, depth, 1, format, MipMode.NONE);
    }

    /** 2D array texture with the given number of layers. */
    public static TextureDescriptor texture2dArray(int width, int height, int layers, TextureFormat format) {
        return new TextureDescriptor(TextureType.TEXTURE_2D_ARRAY, width, height, 1, layers, format, MipMode.AUTO);
    }

    /** Cube map texture (6 layers, square faces). */
    public static TextureDescriptor cubeMap(int size, TextureFormat format) {
        return new TextureDescriptor(TextureType.TEXTURE_CUBE, size, size, 1, 6, format, MipMode.AUTO);
    }

    /** Creates a copy with a different MipMode. */
    public TextureDescriptor withMipMode(MipMode mode) {
        return new TextureDescriptor(type, width, height, depth, layers, format, mode);
    }

    private static boolean isDepth(TextureFormat format) {
        return format == TextureFormat.DEPTH24 || format == TextureFormat.DEPTH32F
            || format == TextureFormat.DEPTH24_STENCIL8 || format == TextureFormat.DEPTH32F_STENCIL8;
    }
}
