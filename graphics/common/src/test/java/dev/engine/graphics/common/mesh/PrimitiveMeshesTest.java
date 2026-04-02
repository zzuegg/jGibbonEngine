package dev.engine.graphics.common.mesh;

import dev.engine.graphics.common.HeadlessRenderDevice;
import dev.engine.graphics.common.MeshHandle;
import dev.engine.graphics.common.Renderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrimitiveMeshesTest {

    private Renderer renderer;

    @BeforeEach
    void setUp() {
        renderer = Renderer.createHeadless();
    }

    @Test void quad() {
        var mesh = PrimitiveMeshes.quad(renderer);
        assertValidMesh(mesh, 4, 6);
    }

    @Test void cube() {
        var mesh = PrimitiveMeshes.cube(renderer);
        assertValidMesh(mesh, 24, 36);
    }

    @Test void sphere() {
        var mesh = PrimitiveMeshes.sphere(renderer, 16, 16);
        assertNotNull(mesh);
        assertTrue(mesh.vertexCount() > 0);
        assertTrue(mesh.indexCount() > 0);
    }

    @Test void sphereDefaultDetail() {
        var mesh = PrimitiveMeshes.sphere(renderer);
        assertNotNull(mesh);
        assertTrue(mesh.vertexCount() > 100);
    }

    @Test void cylinder() {
        var mesh = PrimitiveMeshes.cylinder(renderer, 16);
        assertNotNull(mesh);
        assertTrue(mesh.vertexCount() > 0);
        assertTrue(mesh.indexCount() > 0);
    }

    @Test void cone() {
        var mesh = PrimitiveMeshes.cone(renderer, 16);
        assertNotNull(mesh);
        assertTrue(mesh.vertexCount() > 0);
    }

    @Test void plane() {
        var mesh = PrimitiveMeshes.plane(renderer, 4, 4);
        assertNotNull(mesh);
        assertEquals(25, mesh.vertexCount()); // (4+1) * (4+1)
        assertEquals(96, mesh.indexCount());   // 4*4*2*3
    }

    @Test void fullscreenTriangle() {
        var mesh = PrimitiveMeshes.fullscreenTriangle(renderer);
        assertValidMesh(mesh, 3, 0); // no indices
    }

    private void assertValidMesh(MeshHandle mesh, int expectedVerts, int expectedIndices) {
        assertNotNull(mesh);
        assertEquals(expectedVerts, mesh.vertexCount());
        assertEquals(expectedIndices, mesh.indexCount());
        assertNotNull(mesh.vertexBuffer());
        assertNotNull(mesh.vertexInput());
    }
}
