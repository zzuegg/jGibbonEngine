package dev.engine.graphics;

/**
 * Typed capability key for querying GPU/backend features and limits.
 * Uses interface (not enum) for extensibility — users can define custom capabilities.
 */
public interface DeviceCapability<T> {
    String name();
    Class<T> type();

    // --- Limits ---
    DeviceCapability<Integer> MAX_TEXTURE_SIZE = intCap("MAX_TEXTURE_SIZE");
    DeviceCapability<Integer> MAX_FRAMEBUFFER_WIDTH = intCap("MAX_FRAMEBUFFER_WIDTH");
    DeviceCapability<Integer> MAX_FRAMEBUFFER_HEIGHT = intCap("MAX_FRAMEBUFFER_HEIGHT");
    DeviceCapability<Integer> MAX_UNIFORM_BUFFER_SIZE = intCap("MAX_UNIFORM_BUFFER_SIZE");
    DeviceCapability<Integer> MAX_STORAGE_BUFFER_SIZE = intCap("MAX_STORAGE_BUFFER_SIZE");
    DeviceCapability<Float> MAX_ANISOTROPY = floatCap("MAX_ANISOTROPY");

    // --- Feature support ---
    DeviceCapability<Boolean> COMPUTE_SHADERS = boolCap("COMPUTE_SHADERS");
    DeviceCapability<Boolean> GEOMETRY_SHADERS = boolCap("GEOMETRY_SHADERS");
    DeviceCapability<Boolean> TESSELLATION = boolCap("TESSELLATION");
    DeviceCapability<Boolean> ANISOTROPIC_FILTERING = boolCap("ANISOTROPIC_FILTERING");
    DeviceCapability<Boolean> MULTI_DRAW_INDIRECT = boolCap("MULTI_DRAW_INDIRECT");
    DeviceCapability<Boolean> BINDLESS_TEXTURES = boolCap("BINDLESS_TEXTURES");

    // --- Backend binding layout ---
    DeviceCapability<Integer> TEXTURE_BINDING_OFFSET = intCap("TEXTURE_BINDING_OFFSET");
    DeviceCapability<Integer> SSBO_BINDING_OFFSET = intCap("SSBO_BINDING_OFFSET");

    // --- Shader compilation ---
    /**
     * The Slang compilation target for this backend.
     * Value is one of {@code ShaderCompiler.TARGET_GLSL},
     * {@code ShaderCompiler.TARGET_SPIRV}, or {@code ShaderCompiler.TARGET_WGSL}.
     */
    DeviceCapability<Integer> SHADER_TARGET = intCap("SHADER_TARGET");

    // --- Device info ---
    DeviceCapability<String> DEVICE_NAME = stringCap("DEVICE_NAME");
    DeviceCapability<String> API_VERSION = stringCap("API_VERSION");
    DeviceCapability<String> BACKEND_NAME = stringCap("BACKEND_NAME");

    // --- Factories ---
    @SuppressWarnings("unchecked")
    static DeviceCapability<Integer> intCap(String name) { return new Cap<>(name, Integer.class); }
    static DeviceCapability<Float> floatCap(String name) { return new Cap<>(name, Float.class); }
    static DeviceCapability<Boolean> boolCap(String name) { return new Cap<>(name, Boolean.class); }
    static DeviceCapability<String> stringCap(String name) { return new Cap<>(name, String.class); }
}

record Cap<T>(String name, Class<T> type) implements DeviceCapability<T> {}
