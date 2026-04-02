package dev.engine.core.scene.light;

public interface LightType {
    String name();

    LightType DIRECTIONAL = NamedLightType.of("DIRECTIONAL");
    LightType POINT = NamedLightType.of("POINT");
    LightType SPOT = NamedLightType.of("SPOT");

    static LightType of(String name) { return NamedLightType.of(name); }
}

record NamedLightType(String name) implements LightType {
    static LightType of(String name) { return new NamedLightType(name); }
}
