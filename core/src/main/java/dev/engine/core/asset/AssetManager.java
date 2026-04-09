package dev.engine.core.asset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class AssetManager {

    private static final Logger log = LoggerFactory.getLogger(AssetManager.class);

    private final List<AssetSource> sources = new CopyOnWriteArrayList<>();
    private final List<AssetLoader<?>> loaders = new CopyOnWriteArrayList<>();
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private final Map<String, List<ReloadCallback<?>>> reloadCallbacks = new ConcurrentHashMap<>();
    private final Executor executor;
    private volatile FileWatcher fileWatcher;

    public AssetManager(Executor executor) {
        this.executor = executor;
    }

    public void addSource(AssetSource source) {
        sources.add(source);
    }

    public void addSource(int index, AssetSource source) {
        sources.add(index, source);
    }

    public void registerLoader(AssetLoader<?> loader) {
        loaders.add(loader);
    }

    @SuppressWarnings("unchecked")
    public <T> T loadSync(String path, Class<T> type) {
        var cached = cache.get(path);
        if (cached != null && type.isInstance(cached)) {
            return (T) cached;
        }

        var data = loadFromSources(path);
        var loader = findLoader(path, type);
        var asset = ((AssetLoader<T>) loader).load(data);
        cache.put(path, asset);
        log.debug("Loaded asset: {}", path);
        return asset;
    }

    public <T> CompletableFuture<T> loadAsync(String path, Class<T> type) {
        return CompletableFuture.supplyAsync(() -> loadSync(path, type), executor);
    }

    public void evict(String path) {
        cache.remove(path);
    }

    public <T> void onReload(String path, Class<T> type, Consumer<T> callback) {
        reloadCallbacks.computeIfAbsent(path, k -> new CopyOnWriteArrayList<>())
                .add(new ReloadCallback<>(type, callback));
    }

    @SuppressWarnings("unchecked")
    public void reloadChanged(String path) {
        cache.remove(path);
        var callbacks = reloadCallbacks.get(path);
        if (callbacks != null) {
            for (var cb : callbacks) {
                var asset = loadSync(path, cb.type);
                ((Consumer<Object>) cb.callback).accept(asset);
            }
        }
        log.debug("Reloaded asset: {}", path);
    }

    private record ReloadCallback<T>(Class<T> type, Consumer<T> callback) {}

    private AssetSource.AssetData loadFromSources(String path) {
        for (var source : sources) {
            if (source.exists(path)) {
                return source.load(path);
            }
        }
        throw new AssetNotFoundException(path);
    }

    /**
     * Enables hot-reload by watching the given directory for file changes.
     * When a watched file is modified, it is evicted from cache, reloaded,
     * and all registered onReload callbacks are fired.
     */
    public void enableHotReload(Path watchDir) {
        if (fileWatcher != null) {
            fileWatcher.stop();
        }
        fileWatcher = new FileWatcher(watchDir);
        fileWatcher.start();
    }

    /**
     * Registers a file for hot-reload watching. When the file changes,
     * the cache is evicted and the asset is reloaded, firing callbacks.
     */
    public void watchForReload(String path) {
        if (fileWatcher == null) return;
        fileWatcher.addListener(path, () -> reloadChanged(path));
    }

    /**
     * Shuts down the asset manager, stopping the file watcher if active.
     */
    public void shutdown() {
        if (fileWatcher != null) {
            fileWatcher.stop();
            fileWatcher = null;
        }
    }

    private <T> AssetLoader<?> findLoader(String path, Class<T> type) {
        for (var loader : loaders) {
            if (loader.assetType().equals(type) && loader.supports(path)) {
                return loader;
            }
        }
        throw new NoLoaderException(path, type);
    }
}
