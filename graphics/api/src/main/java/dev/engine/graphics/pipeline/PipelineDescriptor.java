package dev.engine.graphics.pipeline;

import java.util.List;

public record PipelineDescriptor(List<ShaderSource> shaders) {

    public static PipelineDescriptor of(ShaderSource... shaders) {
        return new PipelineDescriptor(List.of(shaders));
    }
}
