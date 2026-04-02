package dev.engine.graphics.common.mesh;

import dev.engine.core.handle.Handle;
import dev.engine.core.scene.MeshTag;
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
        assertValidHandle(mesh);
    }

    @Test void cube() {
        var mesh = PrimitiveMeshes.cube(renderer);
        assertValidHandle(mesh);
    }

    @Test void sphere() {
        var mesh = PrimitiveMeshes.sphere(renderer, 16, 16);
        assertValidHandle(mesh);
    }

    @Test void sphereDefaultDetail() {
        var mesh = PrimitiveMeshes.sphere(renderer);
        assertValidHandle(mesh);
    }

    @Test void cylinder() {
        var mesh = PrimitiveMeshes.cylinder(renderer, 16);
        assertValidHandle(mesh);
    }

    @Test void cone() {
        var mesh = PrimitiveMeshes.cone(renderer, 16);
        assertValidHandle(mesh);
    }

    @Test void plane() {
        var mesh = PrimitiveMeshes.plane(renderer, 4, 4);
        assertValidHandle(mesh);
    }

    @Test void fullscreenTriangle() {
        var mesh = PrimitiveMeshes.fullscreenTriangle(renderer);
        assertValidHandle(mesh);
    }

    private void assertValidHandle(Handle<MeshTag> handle) {
        assertNotNull(handle);
        assertNotEquals(Handle.invalid(), handle);
    }
}
