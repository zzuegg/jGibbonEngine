package dev.engine.graphics.pipeline;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Frame-based pipeline variant cache with automatic eviction.
 *
 * <p>Backends that bake render state into pipeline objects (Vulkan, WebGPU) use this
 * to cache pipeline variants keyed by render state. Variants unused for a configurable
 * number of frames are evicted automatically.
 *
 * @param <K> the variant key type (must implement equals/hashCode)
 */
public final class PipelineVariantCache<K> {

    private final Map<K, Long> variants = new HashMap<>();
    private final Map<K, Long> lastUsedFrame = new HashMap<>();
    private final long evictionFrames;
    private final int checkInterval;

    public PipelineVariantCache(long evictionFrames, int checkInterval) {
        this.evictionFrames = evictionFrames;
        this.checkInterval = checkInterval;
    }

    public PipelineVariantCache() {
        this(300, 60);
    }

    /**
     * Gets or creates a variant for the given key.
     */
    public long getOrCreate(K key, long currentFrame, Function<K, Long> factory) {
        var variant = variants.computeIfAbsent(key, factory);
        lastUsedFrame.put(key, currentFrame);
        return variant;
    }

    /**
     * Evicts variants not used for {@code evictionFrames}. Call from endFrame().
     * The destroyer callback receives the native pipeline handle to release.
     */
    public void evict(long currentFrame, Consumer<Long> destroyer) {
        if (currentFrame % checkInterval != 0) return;
        var it = lastUsedFrame.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (currentFrame - entry.getValue() > evictionFrames) {
                var pipeline = variants.remove(entry.getKey());
                if (pipeline != null && pipeline != 0) {
                    destroyer.accept(pipeline);
                }
                it.remove();
            }
        }
    }

    /**
     * Removes all variants matching a predicate (e.g., when a base pipeline is destroyed).
     */
    public void removeIf(java.util.function.Predicate<K> predicate, Consumer<Long> destroyer) {
        var it = variants.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (predicate.test(entry.getKey())) {
                if (entry.getValue() != 0) {
                    destroyer.accept(entry.getValue());
                }
                lastUsedFrame.remove(entry.getKey());
                it.remove();
            }
        }
    }

    /**
     * Destroys all cached variants.
     */
    public void clear(Consumer<Long> destroyer) {
        for (var pipeline : variants.values()) {
            if (pipeline != 0) destroyer.accept(pipeline);
        }
        variants.clear();
        lastUsedFrame.clear();
    }

    public int size() {
        return variants.size();
    }
}
