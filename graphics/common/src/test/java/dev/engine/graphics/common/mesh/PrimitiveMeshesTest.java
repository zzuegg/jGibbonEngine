package dev.engine.graphics.common.mesh;

import dev.engine.graphics.mesh.MeshData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrimitiveMeshesTest {

    @Test void quad() {
        var data = PrimitiveMeshes.quad();
        assertMeshData(data, 4, 6);
    }

    @Test void cube() {
        var data = PrimitiveMeshes.cube();
        assertMeshData(data, 24, 36);
    }

    @Test void sphere() {
        var data = PrimitiveMeshes.sphere(16, 16);
        assertNotNull(data);
        assertTrue(data.vertexCount() > 0);
        assertTrue(data.indexCount() > 0);
    }

    @Test void sphereDefault() {
        var data = PrimitiveMeshes.sphere();
        assertTrue(data.vertexCount() > 100);
    }

    @Test void cylinder() {
        var data = PrimitiveMeshes.cylinder(16);
        assertTrue(data.vertexCount() > 0);
        assertTrue(data.indexCount() > 0);
    }

    @Test void cone() {
        var data = PrimitiveMeshes.cone(16);
        assertTrue(data.vertexCount() > 0);
    }

    @Test void plane() {
        var data = PrimitiveMeshes.plane(4, 4);
        assertEquals(25, data.vertexCount());
        assertEquals(96, data.indexCount());
    }

    @Test void fullscreenTriangle() {
        var data = PrimitiveMeshes.fullscreenTriangle();
        assertEquals(3, data.vertexCount());
        assertFalse(data.isIndexed());
    }

    @Test void meshDataCreate() {
        var data = MeshData.create(
                new float[]{0,0,0, 1,0,0, 0,1,0},
                new int[]{0,1,2},
                PrimitiveMeshes.STANDARD_FORMAT);
        assertNotNull(data.vertexData());
        assertTrue(data.isIndexed());
    }

    private void assertMeshData(MeshData data, int verts, int indices) {
        assertNotNull(data);
        assertEquals(verts, data.vertexCount());
        assertEquals(indices, data.indexCount());
        assertNotNull(data.vertexData());
        assertTrue(data.vertexData().remaining() > 0);
    }
}
