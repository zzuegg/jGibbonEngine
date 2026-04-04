package dev.engine.graphics.sampler;

/**
 * Border color used when wrap mode is {@link WrapMode#CLAMP_TO_BORDER}.
 */
public interface BorderColor {
    String name();

    BorderColor TRANSPARENT_BLACK = NamedBorder.of("TRANSPARENT_BLACK");
    BorderColor OPAQUE_BLACK = NamedBorder.of("OPAQUE_BLACK");
    BorderColor OPAQUE_WHITE = NamedBorder.of("OPAQUE_WHITE");
}

record NamedBorder(String name) implements BorderColor {
    static BorderColor of(String name) { return new NamedBorder(name); }
}
