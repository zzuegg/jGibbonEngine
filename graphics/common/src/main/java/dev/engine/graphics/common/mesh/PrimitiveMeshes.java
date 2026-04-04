package dev.engine.graphics.common.mesh;

import dev.engine.core.mesh.MeshData;


import dev.engine.core.mesh.ComponentType;
import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;

import java.util.ArrayList;

/**
 * Factory for common primitive meshes.
 * All meshes have position (loc 0, 3 floats) + normal (loc 1, 3 floats) + uv (loc 2, 2 floats).
 */
public final class PrimitiveMeshes {

    public static final VertexFormat STANDARD_FORMAT = VertexFormat.of(
            new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0),   // position
            new VertexAttribute(1, 3, ComponentType.FLOAT, false, 12),  // normal
            new VertexAttribute(2, 2, ComponentType.FLOAT, false, 24)   // uv
    );

    /** Vertex stride: 3 pos + 3 normal + 2 uv = 8 floats */
    private static final int STRIDE = 8;

    private PrimitiveMeshes() {}

    /** A 1x1 quad centered at origin in the XY plane, facing +Z. */
    public static MeshData quad() {
        return quad(1f);
    }

    /** A 1x1 quad with the given UV scale. uvScale=3 means UVs go 0→3, tiling the texture 3 times. */
    public static MeshData quad(float uvScale) {
        float u = uvScale;
        float[] v = {
                // pos              normal          uv
                -0.5f, -0.5f, 0f,  0f, 0f, 1f,    0f, 0f,
                 0.5f, -0.5f, 0f,  0f, 0f, 1f,    u,   0f,
                 0.5f,  0.5f, 0f,  0f, 0f, 1f,    u,   u,
                -0.5f,  0.5f, 0f,  0f, 0f, 1f,    0f,  u,
        };
        int[] idx = {0, 1, 2, 0, 2, 3};
        return MeshData.create(v, idx, STANDARD_FORMAT);
    }

    /** A 1x1x1 cube centered at origin. 24 vertices (4 per face with correct normals). */
    public static MeshData cube() {
        float s = 0.5f;
        float[] v = new float[24 * STRIDE];
        int vi = 0;

        // +Z face
        vi = face(v, vi, -s,-s,s, s,-s,s, s,s,s, -s,s,s, 0,0,1);
        // -Z face
        vi = face(v, vi, s,-s,-s, -s,-s,-s, -s,s,-s, s,s,-s, 0,0,-1);
        // +Y face
        vi = face(v, vi, -s,s,s, s,s,s, s,s,-s, -s,s,-s, 0,1,0);
        // -Y face
        vi = face(v, vi, -s,-s,-s, s,-s,-s, s,-s,s, -s,-s,s, 0,-1,0);
        // +X face
        vi = face(v, vi, s,-s,s, s,-s,-s, s,s,-s, s,s,s, 1,0,0);
        // -X face
        vi = face(v, vi, -s,-s,-s, -s,-s,s, -s,s,s, -s,s,-s, -1,0,0);

        int[] idx = new int[36];
        for (int f = 0; f < 6; f++) {
            int b = f * 4;
            idx[f*6] = b; idx[f*6+1] = b+1; idx[f*6+2] = b+2;
            idx[f*6+3] = b; idx[f*6+4] = b+2; idx[f*6+5] = b+3;
        }
        return MeshData.create(v, idx, STANDARD_FORMAT);
    }

    /** UV sphere with given segments and rings. */
    public static MeshData sphere(int segments, int rings) {
        var verts = new ArrayList<Float>();
        var indices = new ArrayList<Integer>();

        for (int r = 0; r <= rings; r++) {
            float phi = (float) (Math.PI * r / rings);
            float sinPhi = (float) Math.sin(phi);
            float cosPhi = (float) Math.cos(phi);

            for (int s = 0; s <= segments; s++) {
                float theta = (float) (2 * Math.PI * s / segments);
                float sinTheta = (float) Math.sin(theta);
                float cosTheta = (float) Math.cos(theta);

                float x = cosTheta * sinPhi;
                float y = cosPhi;
                float z = sinTheta * sinPhi;
                float u = (float) s / segments;
                float v = (float) r / rings;

                verts.add(x * 0.5f); verts.add(y * 0.5f); verts.add(z * 0.5f); // pos
                verts.add(x); verts.add(y); verts.add(z);                       // normal
                verts.add(u); verts.add(v);                                      // uv
            }
        }

        int cols = segments + 1;
        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < segments; s++) {
                int a = r * cols + s;
                int b = a + cols;
                indices.add(a); indices.add(b); indices.add(a + 1);
                indices.add(a + 1); indices.add(b); indices.add(b + 1);
            }
        }

        return MeshData.create(toFloatArray(verts), toIntArray(indices), STANDARD_FORMAT);
    }

    /** UV sphere with default detail (32 segments, 16 rings). */
    public static MeshData sphere() {
        return sphere(32, 16);
    }

    /** Cylinder along Y axis, radius 0.5, height 1. */
    public static MeshData cylinder(int segments) {
        var verts = new ArrayList<Float>();
        var indices = new ArrayList<Integer>();
        int vi = 0;

        // Side
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (2 * Math.PI * i / segments);
            float x = (float) Math.cos(angle) * 0.5f;
            float z = (float) Math.sin(angle) * 0.5f;
            float nx = (float) Math.cos(angle);
            float nz = (float) Math.sin(angle);
            float u = (float) i / segments;

            // Bottom vertex
            verts.add(x); verts.add(-0.5f); verts.add(z);
            verts.add(nx); verts.add(0f); verts.add(nz);
            verts.add(u); verts.add(0f);

            // Top vertex
            verts.add(x); verts.add(0.5f); verts.add(z);
            verts.add(nx); verts.add(0f); verts.add(nz);
            verts.add(u); verts.add(1f);
        }

        for (int i = 0; i < segments; i++) {
            int b = i * 2;
            indices.add(b); indices.add(b + 1); indices.add(b + 2);
            indices.add(b + 2); indices.add(b + 1); indices.add(b + 3);
        }

        // Caps
        int topCenter = verts.size() / STRIDE;
        verts.add(0f); verts.add(0.5f); verts.add(0f);
        verts.add(0f); verts.add(1f); verts.add(0f);
        verts.add(0.5f); verts.add(0.5f);

        int bottomCenter = verts.size() / STRIDE;
        verts.add(0f); verts.add(-0.5f); verts.add(0f);
        verts.add(0f); verts.add(-1f); verts.add(0f);
        verts.add(0.5f); verts.add(0.5f);

        int capStart = verts.size() / STRIDE;
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (2 * Math.PI * i / segments);
            float x = (float) Math.cos(angle) * 0.5f;
            float z = (float) Math.sin(angle) * 0.5f;

            // Top cap vertex
            verts.add(x); verts.add(0.5f); verts.add(z);
            verts.add(0f); verts.add(1f); verts.add(0f);
            verts.add(x + 0.5f); verts.add(z + 0.5f);

            // Bottom cap vertex
            verts.add(x); verts.add(-0.5f); verts.add(z);
            verts.add(0f); verts.add(-1f); verts.add(0f);
            verts.add(x + 0.5f); verts.add(z + 0.5f);
        }

        for (int i = 0; i < segments; i++) {
            int t = capStart + i * 2;
            indices.add(topCenter); indices.add(t); indices.add(t + 2);
            int b = capStart + i * 2 + 1;
            indices.add(bottomCenter); indices.add(b + 2); indices.add(b);
        }

        return MeshData.create(toFloatArray(verts), toIntArray(indices), STANDARD_FORMAT);
    }

    /** Cone along Y axis, radius 0.5, height 1. Tip at Y=0.5. */
    public static MeshData cone(int segments) {
        var verts = new ArrayList<Float>();
        var indices = new ArrayList<Integer>();

        // Tip
        int tipIdx = 0;
        verts.add(0f); verts.add(0.5f); verts.add(0f);
        verts.add(0f); verts.add(1f); verts.add(0f);
        verts.add(0.5f); verts.add(1f);

        // Base ring
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (2 * Math.PI * i / segments);
            float x = (float) Math.cos(angle) * 0.5f;
            float z = (float) Math.sin(angle) * 0.5f;
            // Approximate normal: pointing outward and up
            float nx = (float) Math.cos(angle);
            float nz = (float) Math.sin(angle);

            verts.add(x); verts.add(-0.5f); verts.add(z);
            verts.add(nx); verts.add(0.4472f); verts.add(nz); // approximate
            verts.add((float) i / segments); verts.add(0f);
        }

        for (int i = 0; i < segments; i++) {
            indices.add(tipIdx); indices.add(1 + i); indices.add(2 + i);
        }

        // Base cap
        int baseCenter = verts.size() / STRIDE;
        verts.add(0f); verts.add(-0.5f); verts.add(0f);
        verts.add(0f); verts.add(-1f); verts.add(0f);
        verts.add(0.5f); verts.add(0.5f);

        for (int i = 0; i < segments; i++) {
            indices.add(baseCenter); indices.add(2 + i); indices.add(1 + i);
        }

        return MeshData.create(toFloatArray(verts), toIntArray(indices), STANDARD_FORMAT);
    }

    /** Subdivided plane in the XZ plane. */
    public static MeshData plane(int subdivisionsX, int subdivisionsZ) {
        var verts = new ArrayList<Float>();
        var indices = new ArrayList<Integer>();

        int cols = subdivisionsX + 1;
        int rows = subdivisionsZ + 1;

        for (int z = 0; z < rows; z++) {
            for (int x = 0; x < cols; x++) {
                float px = (float) x / subdivisionsX - 0.5f;
                float pz = (float) z / subdivisionsZ - 0.5f;
                float u = (float) x / subdivisionsX;
                float v = (float) z / subdivisionsZ;

                verts.add(px); verts.add(0f); verts.add(pz);
                verts.add(0f); verts.add(1f); verts.add(0f);
                verts.add(u); verts.add(v);
            }
        }

        for (int z = 0; z < subdivisionsZ; z++) {
            for (int x = 0; x < subdivisionsX; x++) {
                int a = z * cols + x;
                int b = a + cols;
                indices.add(a); indices.add(b); indices.add(a + 1);
                indices.add(a + 1); indices.add(b); indices.add(b + 1);
            }
        }

        return MeshData.create(toFloatArray(verts), toIntArray(indices), STANDARD_FORMAT);
    }

    /** Fullscreen triangle for post-processing. Position only, no normals/UVs. */
    public static MeshData fullscreenTriangle() {
        var format = VertexFormat.of(
                new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0));
        float[] v = {-1, -1, 0, 3, -1, 0, -1, 3, 0};
        return MeshData.create(v, format);
    }

    // --- Helpers ---

    private static int face(float[] v, int vi,
                            float x0, float y0, float z0, float x1, float y1, float z1,
                            float x2, float y2, float z2, float x3, float y3, float z3,
                            float nx, float ny, float nz) {
        float[][] pos = {{x0,y0,z0},{x1,y1,z1},{x2,y2,z2},{x3,y3,z3}};
        float[][] uv = {{0,0},{1,0},{1,1},{0,1}};
        for (int i = 0; i < 4; i++) {
            v[vi++] = pos[i][0]; v[vi++] = pos[i][1]; v[vi++] = pos[i][2];
            v[vi++] = nx; v[vi++] = ny; v[vi++] = nz;
            v[vi++] = uv[i][0]; v[vi++] = uv[i][1];
        }
        return vi;
    }

    private static float[] toFloatArray(ArrayList<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    private static int[] toIntArray(ArrayList<Integer> list) {
        return list.stream().mapToInt(Integer::intValue).toArray();
    }
}
