package dev.engine.core.material;

/**
 * Custom material — user provides a Slang shader path and a typed data record.
 * The record auto-generates the Slang struct.
 */
public record CustomMaterial(
        String shaderPath,
        Record data
) implements MaterialData {}
