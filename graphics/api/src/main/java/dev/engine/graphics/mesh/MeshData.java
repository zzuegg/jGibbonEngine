package dev.engine.graphics.mesh;

import dev.engine.graphics.vertex.VertexFormat;

import java.nio.ByteBuffer;

/**
 * Format-agnostic mesh data. Holds raw interleaved vertex bytes described
 * by a {@link VertexFormat}, plus index data.
 *
 * <p>The vertex data layout is determined entirely by the format — MeshData
 * makes no assumptions about which attributes exist. Any vertex type works.
 */
public record MeshData(ByteBuffer vertexData, VertexFormat format, int[] indices, int vertexCount, int indexCount) {}
