package dev.engine.graphics.pipeline;

import dev.engine.core.mesh.VertexFormat;

import java.util.List;

public record PipelineDescriptor(List<ShaderSource> shaders, List<ShaderBinary> binaries, VertexFormat vertexFormat) {

    /** Creates a descriptor from GLSL text sources. */
    public static PipelineDescriptor of(ShaderSource... shaders) {
        return new PipelineDescriptor(List.of(shaders), List.of(), null);
    }

    /** Creates a descriptor from pre-compiled SPIRV binaries. */
    public static PipelineDescriptor ofSpirv(ShaderBinary... binaries) {
        return new PipelineDescriptor(List.of(), List.of(binaries), null);
    }

    /** Returns a copy with the given vertex format. */
    public PipelineDescriptor withVertexFormat(VertexFormat format) {
        return new PipelineDescriptor(shaders, binaries, format);
    }

    /** True if this descriptor contains SPIRV binaries. */
    public boolean hasSpirv() {
        return !binaries.isEmpty();
    }
}
