package dev.engine.graphics.common;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.SamplerResource;
import dev.engine.graphics.sampler.SamplerDescriptor;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages sampler lifecycle with descriptor-based deduplication.
 *
 * <p>Many materials share identical sampler configurations.
 * This manager ensures only one GPU sampler is created per unique descriptor.
 */
public class SamplerManager {

    private final GpuResourceManager gpu;
    private final Map<SamplerDescriptor, Handle<SamplerResource>> cache = new HashMap<>();

    public SamplerManager(GpuResourceManager gpu) {
        this.gpu = gpu;
    }

    /** Gets or creates a sampler for the given descriptor. */
    public Handle<SamplerResource> getOrCreate(SamplerDescriptor descriptor) {
        return cache.computeIfAbsent(descriptor, gpu::createSampler);
    }

    public void close() {
        for (var handle : cache.values()) {
            gpu.destroySampler(handle);
        }
        cache.clear();
    }
}
