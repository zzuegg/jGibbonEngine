package dev.engine.graphics.renderer;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.PipelineResource;
import dev.engine.graphics.VertexInputResource;

import java.util.Map;

/**
 * A resolved renderable: mesh GPU resources + pipeline + binding map.
 * The binding map maps buffer names (e.g. "CameraBuffer") to slot indices,
 * resolved from shader reflection after compilation.
 */
public record Renderable(
        Handle<BufferResource> vertexBuffer,
        Handle<BufferResource> indexBuffer,
        Handle<VertexInputResource> vertexInput,
        Handle<PipelineResource> pipeline,
        int vertexCount,
        int indexCount,
        Map<String, Integer> bufferBindings
) {
    /** Renderable without reflection bindings (legacy / headless). */
    public Renderable(Handle<BufferResource> vertexBuffer, Handle<BufferResource> indexBuffer,
                      Handle<VertexInputResource> vertexInput, Handle<PipelineResource> pipeline,
                      int vertexCount, int indexCount) {
        this(vertexBuffer, indexBuffer, vertexInput, pipeline, vertexCount, indexCount, Map.of());
    }

    /** Finds the binding slot for a named buffer, or the fallback if unknown. */
    public int bindingFor(String bufferName, int fallback) {
        return bufferBindings.getOrDefault(bufferName, fallback);
    }
}
