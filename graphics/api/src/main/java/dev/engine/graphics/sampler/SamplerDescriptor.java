package dev.engine.graphics.sampler;

public record SamplerDescriptor(FilterMode minFilter, FilterMode magFilter, WrapMode wrapS, WrapMode wrapT) {

    public static SamplerDescriptor linear() {
        return new SamplerDescriptor(FilterMode.LINEAR, FilterMode.LINEAR, WrapMode.REPEAT, WrapMode.REPEAT);
    }

    public static SamplerDescriptor nearest() {
        return new SamplerDescriptor(FilterMode.NEAREST, FilterMode.NEAREST, WrapMode.REPEAT, WrapMode.REPEAT);
    }

    public static SamplerDescriptor trilinear() {
        return new SamplerDescriptor(FilterMode.LINEAR_MIPMAP_LINEAR, FilterMode.LINEAR, WrapMode.REPEAT, WrapMode.REPEAT);
    }
}
