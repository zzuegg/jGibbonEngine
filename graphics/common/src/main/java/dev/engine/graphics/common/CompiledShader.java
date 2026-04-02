package dev.engine.graphics.common;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.PipelineResource;

import java.util.Map;

/**
 * A compiled shader pipeline with reflection metadata.
 * The renderer uses reflection to bind UBOs and textures to the correct slots.
 */
public record CompiledShader(
        Handle<PipelineResource> pipeline,
        Map<String, ParameterBinding> bindings
) {
    /**
     * A shader parameter binding — name, slot index, and type.
     */
    public record ParameterBinding(String name, int binding, BindingType type) {}

    public enum BindingType {
        CONSTANT_BUFFER,
        TEXTURE,
        SAMPLER,
        STORAGE_BUFFER
    }

    /** Finds a binding by name, or null. */
    public ParameterBinding findBinding(String name) {
        return bindings.get(name);
    }

    /** Gets the binding index for a named parameter, or -1. */
    public int bindingIndex(String name) {
        var b = bindings.get(name);
        return b != null ? b.binding : -1;
    }
}
