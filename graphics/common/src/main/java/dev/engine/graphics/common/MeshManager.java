package dev.engine.graphics.common;

import dev.engine.core.handle.Handle;
import dev.engine.core.handle.HandlePool;
import dev.engine.core.mesh.MeshData;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.core.resource.WeakCache;
import dev.engine.core.scene.MeshTag;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferUsage;

/**
 * Manages GPU mesh resources: creation, upload, and identity-based caching.
 */
public class MeshManager {

    private final GpuResourceManager gpu;
    private final HandlePool<MeshTag> meshPool = new HandlePool<>();
    private final java.util.Map<Integer, MeshHandle> meshRegistry = new java.util.HashMap<>();
    private final WeakCache<MeshData, MeshHandle> meshDataCache = new WeakCache<>();

    public MeshManager(GpuResourceManager gpu) {
        this.gpu = gpu;
    }

    public Handle<MeshTag> createMesh(float[] vertices, int[] indices, VertexFormat format) {
        long vbSize = (long) vertices.length * Float.BYTES;
        var vbo = gpu.createBuffer(vbSize, BufferUsage.VERTEX, AccessPattern.STATIC);
        try (var w = gpu.writeBuffer(vbo)) {
            for (int i = 0; i < vertices.length; i++) {
                w.memory().putFloat((long) i * Float.BYTES, vertices[i]);
            }
        }

        Handle<BufferResource> ibo = null;
        int indexCount = 0;
        if (indices != null && indices.length > 0) {
            long ibSize = (long) indices.length * Integer.BYTES;
            ibo = gpu.createBuffer(ibSize, BufferUsage.INDEX, AccessPattern.STATIC);
            try (var w = gpu.writeBuffer(ibo)) {
                for (int i = 0; i < indices.length; i++) {
                    w.memory().putInt((long) i * Integer.BYTES, indices[i]);
                }
            }
            indexCount = indices.length;
        }

        var vertexInput = gpu.createVertexInput(format);
        int vertexCount = vertices.length / (format.stride() / Float.BYTES);

        var handle = meshPool.allocate();
        meshRegistry.put(handle.index(), new MeshHandle(vbo, ibo, vertexInput, format, vertexCount, indexCount));
        return handle;
    }

    public Handle<MeshTag> createMeshFromData(MeshData data) {
        var buf = data.vertexData();
        float[] vertices = new float[buf.remaining() / Float.BYTES];
        buf.mark();
        buf.asFloatBuffer().get(vertices);
        buf.reset();
        return createMesh(vertices, data.indices(), data.format());
    }

    public MeshHandle resolve(MeshData data) {
        return meshDataCache.getOrCreate(data, this::uploadMeshData);
    }

    public MeshHandle resolve(Handle<MeshTag> handle) {
        return meshRegistry.get(handle.index());
    }

    /** Polls for garbage-collected MeshData and destroys associated GPU resources. */
    public void pollStale() {
        meshDataCache.pollStale(mesh -> {
            gpu.destroyBuffer(mesh.vertexBuffer());
            if (mesh.indexBuffer() != null) gpu.destroyBuffer(mesh.indexBuffer());
        });
    }

    private MeshHandle uploadMeshData(MeshData data) {
        var buf = data.vertexData();
        float[] vertices = new float[buf.remaining() / Float.BYTES];
        buf.mark();
        buf.asFloatBuffer().get(vertices);
        buf.reset();

        long vbSize = (long) vertices.length * Float.BYTES;
        var vbo = gpu.createBuffer(vbSize, BufferUsage.VERTEX, AccessPattern.STATIC);
        try (var w = gpu.writeBuffer(vbo)) {
            for (int i = 0; i < vertices.length; i++)
                w.memory().putFloat((long) i * Float.BYTES, vertices[i]);
        }

        Handle<BufferResource> ibo = null;
        if (data.isIndexed()) {
            long ibSize = (long) data.indices().length * Integer.BYTES;
            ibo = gpu.createBuffer(ibSize, BufferUsage.INDEX, AccessPattern.STATIC);
            try (var w = gpu.writeBuffer(ibo)) {
                for (int i = 0; i < data.indices().length; i++)
                    w.memory().putInt((long) i * Integer.BYTES, data.indices()[i]);
            }
        }

        var vertexInput = gpu.createVertexInput(data.format());
        return new MeshHandle(vbo, ibo, vertexInput, data.format(), data.vertexCount(), data.indexCount());
    }
}
