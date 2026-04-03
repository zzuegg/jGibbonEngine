package dev.engine.graphics.texture;

public interface TextureType {
    String name();

    TextureType TEXTURE_2D       = () -> "TEXTURE_2D";
    TextureType TEXTURE_3D       = () -> "TEXTURE_3D";
    TextureType TEXTURE_2D_ARRAY = () -> "TEXTURE_2D_ARRAY";
    TextureType TEXTURE_CUBE     = () -> "TEXTURE_CUBE";
}
