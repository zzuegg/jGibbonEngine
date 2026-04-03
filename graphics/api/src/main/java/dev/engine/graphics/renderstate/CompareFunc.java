package dev.engine.graphics.renderstate;

public interface CompareFunc {
    String name();

    CompareFunc LESS      = () -> "LESS";
    CompareFunc LEQUAL    = () -> "LEQUAL";
    CompareFunc GREATER   = () -> "GREATER";
    CompareFunc GEQUAL    = () -> "GEQUAL";
    CompareFunc EQUAL     = () -> "EQUAL";
    CompareFunc NOT_EQUAL = () -> "NOT_EQUAL";
    CompareFunc ALWAYS    = () -> "ALWAYS";
    CompareFunc NEVER     = () -> "NEVER";
}
