package dev.engine.bindings.assimp;

import dev.engine.core.asset.AssetManager;
import dev.engine.core.asset.FileSystemAssetSource;
import dev.engine.core.asset.Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class AssimpModelLoaderTest {

    @TempDir Path tempDir;

    static final String SIMPLE_OBJ = """
            v 0.0 0.0 0.0
            v 1.0 0.0 0.0
            v 0.0 1.0 0.0
            v 1.0 1.0 0.0
            f 1 2 3
            f 2 4 3
            """;

    @Test
    void loadSimpleObjModel() throws IOException {
        Files.writeString(tempDir.resolve("test.obj"), SIMPLE_OBJ);

        var manager = new AssetManager(Executors.newSingleThreadExecutor());
        manager.addSource(new FileSystemAssetSource(tempDir));
        manager.registerLoader(new AssimpModelLoader());

        var model = manager.loadSync("test.obj", Model.class);
        assertNotNull(model);
        assertFalse(model.meshes().isEmpty());

        var mesh = model.meshes().getFirst();
        assertTrue(mesh.meshData().vertexCount() > 0);
        assertTrue(mesh.meshData().indexCount() > 0);
        assertNotNull(mesh.meshData().format());
    }

    @Test
    void modelHasNodes() throws IOException {
        Files.writeString(tempDir.resolve("test.obj"), SIMPLE_OBJ);

        var manager = new AssetManager(Executors.newSingleThreadExecutor());
        manager.addSource(new FileSystemAssetSource(tempDir));
        manager.registerLoader(new AssimpModelLoader());

        var model = manager.loadSync("test.obj", Model.class);
        assertNotNull(model.nodes());
        assertFalse(model.nodes().isEmpty());
    }

    @Test
    void supportsCorrectExtensions() {
        var loader = new AssimpModelLoader();
        assertTrue(loader.supports("model.gltf"));
        assertTrue(loader.supports("model.glb"));
        assertTrue(loader.supports("model.fbx"));
        assertTrue(loader.supports("model.obj"));
        assertTrue(loader.supports("scene.dae"));
        assertFalse(loader.supports("texture.png"));
        assertFalse(loader.supports("shader.slang"));
    }
}
