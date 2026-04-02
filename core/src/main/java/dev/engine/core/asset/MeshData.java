package dev.engine.core.asset;

public record MeshData(float[] positions, float[] normals, float[] texCoords, int[] indices, int vertexCount, int indexCount) {}
