package dev.engine.graphics.renderstate;

public interface FrontFace {
    String name();

    FrontFace CCW = () -> "CCW";
    FrontFace CW  = () -> "CW";
}
