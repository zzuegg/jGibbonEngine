package dev.engine.graphics.pipeline;

/**
 * Pre-compiled shader bytecode (SPIRV).
 */
public record ShaderBinary(ShaderStage stage, byte[] spirv) {}
