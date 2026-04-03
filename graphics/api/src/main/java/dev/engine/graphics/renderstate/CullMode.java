package dev.engine.graphics.renderstate;

public interface CullMode {
    String name();

    CullMode NONE = () -> "NONE";
    CullMode BACK = () -> "BACK";
    CullMode FRONT = () -> "FRONT";
}
