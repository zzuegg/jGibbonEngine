package dev.engine.core.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ZipAssetSourceTest {

    @TempDir Path tempDir;

    @Test
    void loadFromZip() throws IOException {
        var zipPath = createTestZip("test.zip",
                "hello.txt", "Hello World",
                "subdir/data.txt", "Some Data");

        var source = new ZipAssetSource(zipPath);
        assertTrue(source.exists("hello.txt"));
        assertTrue(source.exists("subdir/data.txt"));
        assertFalse(source.exists("missing.txt"));

        var data = source.load("hello.txt");
        assertEquals("Hello World", new String(data.bytes()));
    }

    @Test
    void suffixMatchingFindsNestedFiles() throws IOException {
        var zipPath = createTestZip("model.zip",
                "model/scene.gltf", "{gltf content}",
                "model/textures/albedo.png", "png bytes");

        var source = new ZipAssetSource(zipPath);
        // Should find by suffix
        assertTrue(source.exists("scene.gltf"));
        assertTrue(source.exists("textures/albedo.png"));
    }

    @Test
    void stripPrefixWorks() throws IOException {
        var zipPath = createTestZip("archive.zip",
                "mymodel/mesh.obj", "obj content",
                "mymodel/texture.png", "png data");

        var source = new ZipAssetSource(zipPath, "mymodel/");
        assertTrue(source.exists("mesh.obj"));
        assertEquals("obj content", new String(source.load("mesh.obj").bytes()));
    }

    @Test
    void loadThroughAssetManager() throws IOException {
        var zipPath = createTestZip("assets.zip",
                "readme.txt", "Engine Assets v1");

        var manager = new AssetManager(Executors.newSingleThreadExecutor());
        manager.addSource(new ZipAssetSource(zipPath));
        manager.registerLoader(new TextLoader());

        var text = manager.loadSync("readme.txt", String.class);
        assertEquals("Engine Assets v1", text);
    }

    // Simple text loader for testing
    static class TextLoader implements AssetLoader<String> {
        @Override public boolean supports(String path) { return path.endsWith(".txt"); }
        @Override public String load(AssetSource.AssetData data) { return new String(data.bytes()); }
        @Override public Class<String> assetType() { return String.class; }
    }

    private Path createTestZip(String name, String... entries) throws IOException {
        var path = tempDir.resolve(name);
        try (var zos = new ZipOutputStream(Files.newOutputStream(path))) {
            for (int i = 0; i < entries.length; i += 2) {
                zos.putNextEntry(new ZipEntry(entries[i]));
                zos.write(entries[i + 1].getBytes());
                zos.closeEntry();
            }
        }
        return path;
    }
}
