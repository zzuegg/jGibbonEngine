package dev.engine.core.layout;

/**
 * GPU buffer layout mode determining alignment rules.
 */
public enum LayoutMode {
    /** Packed layout — no alignment padding. For vertex buffers, CPU-side data. */
    PACKED,
    /** std140 layout — for Uniform Buffer Objects. vec3 aligned to 16 bytes. */
    STD140,
    /** std430 layout — for Shader Storage Buffer Objects. Same as std140 for scalars/vectors. */
    STD430
}
