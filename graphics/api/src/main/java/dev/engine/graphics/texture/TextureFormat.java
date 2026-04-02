package dev.engine.graphics.texture;

public interface TextureFormat {
    String name();

    TextureFormat RGBA8 = NamedFormat.of("RGBA8");
    TextureFormat RGB8 = NamedFormat.of("RGB8");
    TextureFormat R8 = NamedFormat.of("R8");
    TextureFormat DEPTH24 = NamedFormat.of("DEPTH24");
    TextureFormat DEPTH32F = NamedFormat.of("DEPTH32F");
}

record NamedFormat(String name) implements TextureFormat {
    static TextureFormat of(String name) { return new NamedFormat(name); }
}
