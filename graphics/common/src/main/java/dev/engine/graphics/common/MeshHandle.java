package dev.engine.graphics.common;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.VertexInputResource;
import dev.engine.graphics.vertex.VertexFormat;

/**
 * A registered mesh in the renderer. Holds GPU resources for vertex/index data.
 */
public record MeshHandle(
        Handle<BufferResource> vertexBuffer,
        Handle<BufferResource> indexBuffer,
        Handle<VertexInputResource> vertexInput,
        VertexFormat format,
        int vertexCount,
        int indexCount
) {}
