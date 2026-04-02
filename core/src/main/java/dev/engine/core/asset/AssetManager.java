package dev.engine.core.asset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class AssetManager {

    private static final Logger log = LoggerFactory.getLogger(AssetManager.class);

    private final List<AssetSource> sources = new ArrayList<>();
    private final List<AssetLoader<?>> loaders = new ArrayList<>();
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private final Executor executor;

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

    private AssetSource.AssetData loadFromSources(String path) {
        for (var source : sources) {
            if (source.exists(path)) {
                return source.load(path);
            }
        }
        throw new AssetNotFoundException(path);
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
