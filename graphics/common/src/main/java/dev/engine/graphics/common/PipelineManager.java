package dev.engine.graphics.common;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.PipelineResource;
import dev.engine.graphics.pipeline.PipelineDescriptor;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages pipeline lifecycle with descriptor-based deduplication.
 *
 * <p>Identical {@link PipelineDescriptor}s return the same cached handle.
 * Supports invalidation for shader hot-reload.
 */
public class PipelineManager {

    private final GpuResourceManager gpu;
    private final Map<PipelineDescriptor, Handle<PipelineResource>> cache = new HashMap<>();

    public PipelineManager(GpuResourceManager gpu) {
        this.gpu = gpu;
    }

    /** Gets or creates a pipeline for the given descriptor. */
    public Handle<PipelineResource> getOrCreate(PipelineDescriptor descriptor) {
        return cache.computeIfAbsent(descriptor, gpu::createPipeline);
    }

    /** Creates a pipeline without caching (for unique/one-off pipelines). */
    public Handle<PipelineResource> create(PipelineDescriptor descriptor) {
        return gpu.createPipeline(descriptor);
    }

    /** Destroys a specific cached pipeline. */
    public void destroy(PipelineDescriptor descriptor) {
        var handle = cache.remove(descriptor);
        if (handle != null) {
            gpu.destroyPipeline(handle);
        }
    }

    /** Invalidates all cached pipelines (e.g., after shader hot-reload). */
    public void invalidateAll() {
        for (var handle : cache.values()) {
            gpu.destroyPipeline(handle);
        }
        cache.clear();
    }

    public void close() {
        invalidateAll();
    }
}
