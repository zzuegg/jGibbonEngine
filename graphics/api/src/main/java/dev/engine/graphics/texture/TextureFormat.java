package dev.engine.graphics.texture;

public interface TextureFormat {
    String name();

    TextureFormat RGBA8 = NamedFormat.of("RGBA8");
    TextureFormat RGB8 = NamedFormat.of("RGB8");
    TextureFormat R8 = NamedFormat.of("R8");
    TextureFormat DEPTH24 = NamedFormat.of("DEPTH24");
    TextureFormat DEPTH32F = NamedFormat.of("DEPTH32F");
    TextureFormat DEPTH24_STENCIL8 = NamedFormat.of("DEPTH24_STENCIL8");
    TextureFormat DEPTH32F_STENCIL8 = NamedFormat.of("DEPTH32F_STENCIL8");

    // HDR
    TextureFormat RGBA16F = NamedFormat.of("RGBA16F");
    TextureFormat RGBA32F = NamedFormat.of("RGBA32F");
    TextureFormat RG16F   = NamedFormat.of("RG16F");
    TextureFormat RG32F   = NamedFormat.of("RG32F");
    TextureFormat R16F    = NamedFormat.of("R16F");
    TextureFormat R32F    = NamedFormat.of("R32F");

    // Integer
    TextureFormat R32UI = NamedFormat.of("R32UI");
    TextureFormat R32I  = NamedFormat.of("R32I");
}

record NamedFormat(String name) implements TextureFormat {
    static TextureFormat of(String name) { return new NamedFormat(name); }
}
