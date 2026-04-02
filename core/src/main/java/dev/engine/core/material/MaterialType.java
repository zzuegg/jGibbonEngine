package dev.engine.core.material;

public interface MaterialType {
    String name();

    MaterialType UNLIT = NamedType.of("UNLIT");
    MaterialType PBR = NamedType.of("PBR");
    MaterialType CUSTOM = NamedType.of("CUSTOM");

    static MaterialType of(String name) { return NamedType.of(name); }
}

record NamedType(String name) implements MaterialType {
    static MaterialType of(String name) { return new NamedType(name); }
}
