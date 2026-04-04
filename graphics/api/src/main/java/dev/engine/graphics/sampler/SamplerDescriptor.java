package dev.engine.graphics.sampler;

/**
 * Describes a GPU sampler configuration.
 *
 * <p>Covers all sampler features common to OpenGL, Vulkan, and WebGPU:
 * filtering, wrapping, LOD control, anisotropy, comparison, and border color.
 *
 * @param minFilter    minification filter (includes mipmap mode)
 * @param magFilter    magnification filter (NEAREST or LINEAR only)
 * @param wrapS        horizontal wrap mode
 * @param wrapT        vertical wrap mode
 * @param wrapR        depth/layer wrap mode (for 3D/cube/array textures)
 * @param minLod       minimum level of detail (0.0 = highest resolution mip)
 * @param maxLod       maximum level of detail (1000.0 = use all mips)
 * @param lodBias      LOD bias added to the computed mip level
 * @param maxAnisotropy maximum anisotropy level (1.0 = disabled, 16.0 = max quality)
 * @param compareFunc  comparison function for shadow samplers (null = disabled)
 * @param borderColor  border color for CLAMP_TO_BORDER wrap mode
 */
public record SamplerDescriptor(
        FilterMode minFilter,
        FilterMode magFilter,
        WrapMode wrapS,
        WrapMode wrapT,
        WrapMode wrapR,
        float minLod,
        float maxLod,
        float lodBias,
        float maxAnisotropy,
        CompareFunc compareFunc,
        BorderColor borderColor
) {

    /** Backward-compatible 4-arg constructor. */
    public SamplerDescriptor(FilterMode minFilter, FilterMode magFilter, WrapMode wrapS, WrapMode wrapT) {
        this(minFilter, magFilter, wrapS, wrapT, WrapMode.REPEAT, 0f, 1000f, 0f, 1f, null, BorderColor.TRANSPARENT_BLACK);
    }

    // --- Common presets ---

    public static SamplerDescriptor linear() {
        return new SamplerDescriptor(FilterMode.LINEAR, FilterMode.LINEAR, WrapMode.REPEAT, WrapMode.REPEAT);
    }

    public static SamplerDescriptor nearest() {
        return new SamplerDescriptor(FilterMode.NEAREST, FilterMode.NEAREST, WrapMode.REPEAT, WrapMode.REPEAT);
    }

    public static SamplerDescriptor trilinear() {
        return new SamplerDescriptor(FilterMode.LINEAR_MIPMAP_LINEAR, FilterMode.LINEAR, WrapMode.REPEAT, WrapMode.REPEAT);
    }

    public static SamplerDescriptor anisotropic(float maxAnisotropy) {
        return new SamplerDescriptor(
                FilterMode.LINEAR_MIPMAP_LINEAR, FilterMode.LINEAR,
                WrapMode.REPEAT, WrapMode.REPEAT, WrapMode.REPEAT,
                0f, 1000f, 0f, maxAnisotropy, null, BorderColor.TRANSPARENT_BLACK);
    }

    public static SamplerDescriptor shadow() {
        return new SamplerDescriptor(
                FilterMode.LINEAR, FilterMode.LINEAR,
                WrapMode.CLAMP_TO_EDGE, WrapMode.CLAMP_TO_EDGE, WrapMode.CLAMP_TO_EDGE,
                0f, 1000f, 0f, 1f, CompareFunc.LESS_EQUAL, BorderColor.OPAQUE_WHITE);
    }

    // --- Builder-style modifiers ---

    public SamplerDescriptor withWrap(WrapMode wrap) {
        return new SamplerDescriptor(minFilter, magFilter, wrap, wrap, wrap, minLod, maxLod, lodBias, maxAnisotropy, compareFunc, borderColor);
    }

    public SamplerDescriptor withAnisotropy(float max) {
        return new SamplerDescriptor(minFilter, magFilter, wrapS, wrapT, wrapR, minLod, maxLod, lodBias, max, compareFunc, borderColor);
    }

    public SamplerDescriptor withLodRange(float min, float max) {
        return new SamplerDescriptor(minFilter, magFilter, wrapS, wrapT, wrapR, min, max, lodBias, maxAnisotropy, compareFunc, borderColor);
    }

    public SamplerDescriptor withLodBias(float bias) {
        return new SamplerDescriptor(minFilter, magFilter, wrapS, wrapT, wrapR, minLod, maxLod, bias, maxAnisotropy, compareFunc, borderColor);
    }

    public SamplerDescriptor withCompare(CompareFunc func) {
        return new SamplerDescriptor(minFilter, magFilter, wrapS, wrapT, wrapR, minLod, maxLod, lodBias, maxAnisotropy, func, borderColor);
    }

    public SamplerDescriptor withBorderColor(BorderColor color) {
        return new SamplerDescriptor(minFilter, magFilter, wrapS, wrapT, wrapR, minLod, maxLod, lodBias, maxAnisotropy, compareFunc, color);
    }
}
