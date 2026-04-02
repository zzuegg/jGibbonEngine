package dev.engine.core.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ObjLoaderTest {

    @TempDir Path tempDir;

    static final String TRIANGLE_OBJ = """
            v 0.0 0.0 0.0
            v 1.0 0.0 0.0
            v 0.0 1.0 0.0
            vn 0.0 0.0 1.0
            vn 0.0 0.0 1.0
            vn 0.0 0.0 1.0
            vt 0.0 0.0
            vt 1.0 0.0
            vt 0.0 1.0
            f 1/1/1 2/2/2 3/3/3
            """;

    static final String QUAD_OBJ = """
            v -1.0 -1.0 0.0
            v  1.0 -1.0 0.0
            v  1.0  1.0 0.0
            v -1.0  1.0 0.0
            f 1 2 3
            f 1 3 4
            """;

    @Test
    void loadTriangleObj() throws IOException {
        Files.writeString(tempDir.resolve("tri.obj"), TRIANGLE_OBJ);
        var manager = new AssetManager(Executors.newSingleThreadExecutor());
        manager.addSource(new FileSystemAssetSource(tempDir));
        manager.registerLoader(new ObjLoader());

        var mesh = manager.loadSync("tri.obj", MeshData.class);
        assertEquals(3, mesh.vertexCount());
        assertEquals(3, mesh.indexCount());
        assertNotNull(mesh.positions());
        assertEquals(9, mesh.positions().length); // 3 verts * 3 components
    }

    @Test
    void loadQuadObj() throws IOException {
        Files.writeString(tempDir.resolve("quad.obj"), QUAD_OBJ);
        var manager = new AssetManager(Executors.newSingleThreadExecutor());
        manager.addSource(new FileSystemAssetSource(tempDir));
        manager.registerLoader(new ObjLoader());

        var mesh = manager.loadSync("quad.obj", MeshData.class);
        assertEquals(4, mesh.vertexCount());
        assertEquals(6, mesh.indexCount()); // 2 triangles
    }

    @Test
    void supportsObjExtension() {
        var loader = new ObjLoader();
        assertTrue(loader.supports("model.obj"));
        assertFalse(loader.supports("texture.png"));
    }
}
