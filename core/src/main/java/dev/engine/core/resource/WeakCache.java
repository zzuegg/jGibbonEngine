package dev.engine.core.resource;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Identity-based cache with weak reference keys and automatic cleanup.
 *
 * <p>Maps CPU-side data objects to GPU-side resources using identity semantics.
 * When the CPU-side data is garbage collected, the associated GPU resource
 * is queued for cleanup on the next {@link #pollStale(Consumer)} call.
 *
 * <p>Thread safety: intended for single-thread access (the render thread).
 * The GC may enqueue references from any thread, but {@link #pollStale} is
 * called from the render thread only.
 *
 * @param <K> the key type (e.g., MeshData, TextureData)
 * @param <V> the value type (e.g., MeshHandle, Handle&lt;TextureResource&gt;)
 */
public class WeakCache<K, V> {

    private final Map<IdentityWeakReference<K>, V> map = new HashMap<>();
    private final ReferenceQueue<K> queue = new ReferenceQueue<>();

    /**
     * Gets or creates a cached value for the given key.
     * Uses identity (==) comparison, not equals().
     */
    public V getOrCreate(K key, Function<K, V> factory) {
        // Try to find existing entry by identity scan
        for (var entry : map.entrySet()) {
            K existing = entry.getKey().get();
            if (existing == key) return entry.getValue();
        }
        // Not found — create and cache
        var ref = new IdentityWeakReference<>(key, queue);
        var value = factory.apply(key);
        map.put(ref, value);
        return value;
    }

    /**
     * Polls for stale entries (keys that were garbage collected).
     * Calls the cleanup consumer for each orphaned value.
     * Should be called once per frame.
     */
    public void pollStale(Consumer<V> cleanup) {
        IdentityWeakReference<?> ref;
        while ((ref = (IdentityWeakReference<?>) queue.poll()) != null) {
            var value = map.remove(ref);
            if (value != null) {
                cleanup.accept(value);
            }
        }
    }

    /** Returns the number of live entries in the cache. */
    public int size() {
        return map.size();
    }

    /** Drains all entries, calling cleanup on each value. */
    public void clear(Consumer<V> cleanup) {
        for (var value : map.values()) {
            cleanup.accept(value);
        }
        map.clear();
        // Drain the queue too
        while (queue.poll() != null) {}
    }

    /**
     * WeakReference with identity-based hashCode/equals.
     * Uses System.identityHashCode for the hash (stable even after GC clears the referent)
     * and == for equality.
     */
    private static class IdentityWeakReference<T> extends WeakReference<T> {
        private final int hash;

        IdentityWeakReference(T referent, ReferenceQueue<? super T> queue) {
            super(referent, queue);
            this.hash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof IdentityWeakReference<?> other)) return false;
            T thisRef = this.get();
            Object otherRef = other.get();
            return thisRef != null && thisRef == otherRef;
        }
    }
}
