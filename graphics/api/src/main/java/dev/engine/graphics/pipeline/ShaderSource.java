package dev.engine.graphics.pipeline;

public record ShaderSource(ShaderStage stage, String source, String entryPoint) {

    /** Creates a ShaderSource with the default entry point name "main". */
    public ShaderSource(ShaderStage stage, String source) {
        this(stage, source, "main");
    }
}
