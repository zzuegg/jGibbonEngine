package dev.engine.core.mesh;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Pure mesh data — no GPU resources. Just vertices, indices, and format.
 *
 * <p>Created by loaders, procedural generators, or user code.
 * Registered with the Engine to get a {@code Handle<MeshTag>} for scene assignment.
 *
 * <p>The vertex data layout is determined entirely by the format — MeshData
 * makes no assumptions about which attributes exist. Any vertex type works.
 */
public record MeshData(ByteBuffer vertexData, VertexFormat format, int[] indices, int vertexCount, int indexCount) {

    /**
     * Creates MeshData from float arrays. The most common creation path.
     */
    public static MeshData create(float[] vertices, int[] indices, VertexFormat format) {
        var buf = ByteBuffer.allocateDirect(vertices.length * Float.BYTES).order(ByteOrder.nativeOrder());
        for (float v : vertices) buf.putFloat(v);
        buf.flip();

        int vertexCount = vertices.length / (format.stride() / Float.BYTES);
        int indexCount = indices != null ? indices.length : 0;
        return new MeshData(buf, format, indices, vertexCount, indexCount);
    }

    /**
     * Creates MeshData with no indices (non-indexed geometry).
     */
    public static MeshData create(float[] vertices, VertexFormat format) {
        return create(vertices, null, format);
    }

    /** Whether this mesh uses index data. */
    public boolean isIndexed() {
        return indices != null && indices.length > 0;
    }
}
