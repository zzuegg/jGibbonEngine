package dev.engine.graphics.sampler;

public interface WrapMode {
    String name();

    WrapMode REPEAT = NamedWrap.of("REPEAT");
    WrapMode CLAMP_TO_EDGE = NamedWrap.of("CLAMP_TO_EDGE");
    WrapMode MIRRORED_REPEAT = NamedWrap.of("MIRRORED_REPEAT");
    WrapMode CLAMP_TO_BORDER = NamedWrap.of("CLAMP_TO_BORDER");
}

record NamedWrap(String name) implements WrapMode {
    static WrapMode of(String name) { return new NamedWrap(name); }
}
