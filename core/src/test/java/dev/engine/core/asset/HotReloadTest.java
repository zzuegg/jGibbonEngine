package dev.engine.core.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HotReloadTest {

    record TextAsset(String content) {}

    static class TextLoader implements AssetLoader<TextAsset> {
        @Override public boolean supports(String path) { return path.endsWith(".txt"); }
        @Override public TextAsset load(AssetSource.AssetData data) { return new TextAsset(new String(data.bytes())); }
        @Override public Class<TextAsset> assetType() { return TextAsset.class; }
    }

    @TempDir Path tempDir;

    @Test
    void reloadCallbackFiredOnChange() throws IOException, InterruptedException {
        Files.writeString(tempDir.resolve("data.txt"), "v1");

        var manager = new AssetManager(Executors.newSingleThreadExecutor());
        manager.addSource(new FileSystemAssetSource(tempDir));
        manager.registerLoader(new TextLoader());

        var reloadCount = new AtomicInteger(0);
        var latestContent = new AtomicReference<String>();
        manager.onReload("data.txt", TextAsset.class, asset -> {
            reloadCount.incrementAndGet();
            latestContent.set(asset.content());
        });

        // Load initial
        var asset = manager.loadSync("data.txt", TextAsset.class);
        assertEquals("v1", asset.content());

        // Simulate file change
        Files.writeString(tempDir.resolve("data.txt"), "v2");
        manager.reloadChanged("data.txt");

        assertEquals(1, reloadCount.get());
        assertEquals("v2", latestContent.get());

        // Cache should now return v2
        var reloaded = manager.loadSync("data.txt", TextAsset.class);
        assertEquals("v2", reloaded.content());
    }
}
