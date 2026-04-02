package dev.engine.core.shader;

public interface ShaderStageType {
    String name();
    String slangStage();

    ShaderStageType VERTEX = new ShaderStageType() {
        @Override public String name() { return "VERTEX"; }
        @Override public String slangStage() { return "vertex"; }
    };
    ShaderStageType FRAGMENT = new ShaderStageType() {
        @Override public String name() { return "FRAGMENT"; }
        @Override public String slangStage() { return "fragment"; }
    };
    ShaderStageType COMPUTE = new ShaderStageType() {
        @Override public String name() { return "COMPUTE"; }
        @Override public String slangStage() { return "compute"; }
    };
}
