package dev.engine.core.asset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AssetManagerTest {

    // Simple test asset type: just wraps a string
    record TextAsset(String content) {}

    // Loader that reads files as text
    static class TextLoader implements AssetLoader<TextAsset> {
        @Override
        public boolean supports(String path) {
            return path.endsWith(".txt");
        }

        @Override
        public TextAsset load(AssetSource.AssetData data) {
            return new TextAsset(new String(data.bytes()));
        }

        @Override
        public Class<TextAsset> assetType() {
            return TextAsset.class;
        }
    }

    @TempDir
    Path tempDir;

    private AssetManager manager;

    @BeforeEach
    void setUp() {
        var source = new FileSystemAssetSource(tempDir);
        manager = new AssetManager(Executors.newSingleThreadExecutor());
        manager.addSource(source);
        manager.registerLoader(new TextLoader());
    }

    @Nested
    class SyncLoading {
        @Test
        void loadExistingAsset() throws IOException {
            Files.writeString(tempDir.resolve("hello.txt"), "Hello World");
            var asset = manager.loadSync("hello.txt", TextAsset.class);
            assertNotNull(asset);
            assertEquals("Hello World", asset.content());
        }

        @Test
        void loadMissingAssetThrows() {
            assertThrows(AssetNotFoundException.class,
                    () -> manager.loadSync("missing.txt", TextAsset.class));
        }

        @Test
        void loadUnsupportedTypeThrows() throws IOException {
            Files.writeString(tempDir.resolve("data.bin"), "binary");
            assertThrows(NoLoaderException.class,
                    () -> manager.loadSync("data.bin", TextAsset.class));
        }
    }

    @Nested
    class Caching {
        @Test
        void secondLoadReturnsCachedInstance() throws IOException {
            Files.writeString(tempDir.resolve("cached.txt"), "cached");
            var first = manager.loadSync("cached.txt", TextAsset.class);
            var second = manager.loadSync("cached.txt", TextAsset.class);
            assertSame(first, second);
        }

        @Test
        void evictRemovesFromCache() throws IOException {
            Files.writeString(tempDir.resolve("evict.txt"), "v1");
            var first = manager.loadSync("evict.txt", TextAsset.class);
            manager.evict("evict.txt");
            Files.writeString(tempDir.resolve("evict.txt"), "v2");
            var second = manager.loadSync("evict.txt", TextAsset.class);
            assertNotSame(first, second);
            assertEquals("v2", second.content());
        }
    }

    @Nested
    class AsyncLoading {
        @Test
        void loadAsyncReturnsCompletableFuture() throws Exception {
            Files.writeString(tempDir.resolve("async.txt"), "async content");
            var future = manager.loadAsync("async.txt", TextAsset.class);
            var asset = future.get(5, TimeUnit.SECONDS);
            assertEquals("async content", asset.content());
        }
    }

    @Nested
    class MultipleSources {
        @Test
        void firstSourceWins() throws IOException {
            var overrideDir = tempDir.resolve("override");
            Files.createDirectories(overrideDir);
            Files.writeString(overrideDir.resolve("file.txt"), "override");
            Files.writeString(tempDir.resolve("file.txt"), "original");

            var overrideSource = new FileSystemAssetSource(overrideDir);
            manager.addSource(0, overrideSource); // higher priority

            var asset = manager.loadSync("file.txt", TextAsset.class);
            assertEquals("override", asset.content());
        }
    }
}
