package dev.engine.graphics.pipeline;

public record ComputePipelineDescriptor(ShaderSource shader, ShaderBinary binary) {
    public static ComputePipelineDescriptor of(ShaderSource source) {
        return new ComputePipelineDescriptor(source, null);
    }
    public static ComputePipelineDescriptor ofSpirv(ShaderBinary binary) {
        return new ComputePipelineDescriptor(null, binary);
    }
    public boolean hasSpirv() { return binary != null; }
}
