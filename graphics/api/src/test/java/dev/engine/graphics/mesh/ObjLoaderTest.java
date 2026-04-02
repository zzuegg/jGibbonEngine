package dev.engine.graphics.mesh;

import dev.engine.core.asset.AssetManager;
import dev.engine.core.asset.FileSystemAssetSource;
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
    void loadTriangleWithAllAttributes() throws IOException {
        Files.writeString(tempDir.resolve("tri.obj"), TRIANGLE_OBJ);
        var manager = new AssetManager(Executors.newSingleThreadExecutor());
        manager.addSource(new FileSystemAssetSource(tempDir));
        manager.registerLoader(new ObjLoader());

        var mesh = manager.loadSync("tri.obj", MeshData.class);
        assertEquals(3, mesh.vertexCount());
        assertEquals(3, mesh.indexCount());
        // 3 verts * (3 pos + 2 uv + 3 normal) floats * 4 bytes = 96 bytes
        assertEquals(3 * (3 + 2 + 3) * Float.BYTES, mesh.vertexData().remaining());
        assertEquals(3, mesh.format().attributes().size());
    }

    @Test
    void loadQuadWithPositionOnly() throws IOException {
        Files.writeString(tempDir.resolve("quad.obj"), QUAD_OBJ);
        var manager = new AssetManager(Executors.newSingleThreadExecutor());
        manager.addSource(new FileSystemAssetSource(tempDir));
        manager.registerLoader(new ObjLoader());

        var mesh = manager.loadSync("quad.obj", MeshData.class);
        assertEquals(4, mesh.vertexCount());
        assertEquals(6, mesh.indexCount());
        assertEquals(4 * 3 * Float.BYTES, mesh.vertexData().remaining());
        assertEquals(1, mesh.format().attributes().size());
    }

    @Test
    void vertexDataIsReadable() throws IOException {
        Files.writeString(tempDir.resolve("tri.obj"), TRIANGLE_OBJ);
        var manager = new AssetManager(Executors.newSingleThreadExecutor());
        manager.addSource(new FileSystemAssetSource(tempDir));
        manager.registerLoader(new ObjLoader());

        var mesh = manager.loadSync("tri.obj", MeshData.class);
        assertEquals(0f, mesh.vertexData().getFloat(0));
        assertEquals(0f, mesh.vertexData().getFloat(4));
        assertEquals(0f, mesh.vertexData().getFloat(8));
    }

    @Test
    void formatStrideMatchesData() throws IOException {
        Files.writeString(tempDir.resolve("tri.obj"), TRIANGLE_OBJ);
        var manager = new AssetManager(Executors.newSingleThreadExecutor());
        manager.addSource(new FileSystemAssetSource(tempDir));
        manager.registerLoader(new ObjLoader());

        var mesh = manager.loadSync("tri.obj", MeshData.class);
        assertEquals(mesh.vertexData().remaining(), mesh.vertexCount() * mesh.format().stride());
    }

    @Test
    void supportsObjExtension() {
        var loader = new ObjLoader();
        assertTrue(loader.supports("model.obj"));
        assertFalse(loader.supports("texture.png"));
    }
}
