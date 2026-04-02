package dev.engine.graphics.pipeline;

public interface ShaderStage {
    String name();

    ShaderStage VERTEX = NamedStage.of("VERTEX");
    ShaderStage FRAGMENT = NamedStage.of("FRAGMENT");
    ShaderStage GEOMETRY = NamedStage.of("GEOMETRY");
    ShaderStage COMPUTE = NamedStage.of("COMPUTE");
}

record NamedStage(String name) implements ShaderStage {
    static ShaderStage of(String name) { return new NamedStage(name); }
}
