package dev.engine.graphics.renderer;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.PipelineResource;
import dev.engine.graphics.VertexInputResource;

public record Renderable(
        Handle<BufferResource> vertexBuffer,
        Handle<BufferResource> indexBuffer,
        Handle<VertexInputResource> vertexInput,
        Handle<PipelineResource> pipeline,
        int vertexCount,
        int indexCount
) {}
