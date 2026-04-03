package dev.engine.graphics.renderstate;

public interface BarrierScope {
    String name();

    BarrierScope STORAGE_BUFFER = () -> "STORAGE_BUFFER";
    BarrierScope TEXTURE        = () -> "TEXTURE";
    BarrierScope ALL            = () -> "ALL";
}
