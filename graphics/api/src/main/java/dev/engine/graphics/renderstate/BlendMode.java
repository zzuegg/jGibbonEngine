package dev.engine.graphics.renderstate;

public interface BlendMode {
    String name();

    BlendMode NONE          = () -> "NONE";
    BlendMode ALPHA         = () -> "ALPHA";
    BlendMode ADDITIVE      = () -> "ADDITIVE";
    BlendMode MULTIPLY      = () -> "MULTIPLY";
    BlendMode PREMULTIPLIED = () -> "PREMULTIPLIED";
}
