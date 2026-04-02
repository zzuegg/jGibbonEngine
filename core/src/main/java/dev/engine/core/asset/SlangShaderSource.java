package dev.engine.core.asset;

/**
 * Raw Slang shader source loaded from a file.
 * The ShaderManager compiles this to GLSL/SPIR-V/WGSL.
 */
public record SlangShaderSource(String path, String source) {}
