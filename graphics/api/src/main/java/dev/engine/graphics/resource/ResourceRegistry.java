package dev.engine.graphics.resource;

import dev.engine.core.handle.Handle;
import dev.engine.core.handle.HandlePool;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Generic resource registry combining a HandlePool with native resource tracking.
 * Used by backends to manage GPU resources (buffers, textures, pipelines, etc.).
 *
 * @param <R> the phantom resource type (e.g., BufferResource)
 * @param <N> the native resource data (e.g., Integer for GL names, or a record for VK allocations)
 */
public class ResourceRegistry<R, N> {

    private final HandlePool<R> pool;
    private final Map<Integer, N> resources = new HashMap<>();

    public ResourceRegistry(String resourceType) {
        this.pool = new HandlePool<>(resourceType);
    }

    /** Allocates a handle and associates native resource data. */
    public Handle<R> register(N nativeResource) {
        var handle = pool.allocate();
        resources.put(handle.index(), nativeResource);
        return handle;
    }

    /** Allocates a handle without native data (for lazy init). */
    public Handle<R> allocate() {
        return pool.allocate();
    }

    /** Associates native data with an existing handle. */
    public void put(Handle<R> handle, N nativeResource) {
        resources.put(handle.index(), nativeResource);
    }

    /** Gets the native resource for a handle. */
    public N get(Handle<R> handle) {
        return resources.get(handle.index());
    }

    /** Gets the native resource by raw index. */
    public N get(int index) {
        return resources.get(index);
    }

    /** Removes and returns the native resource, releases the handle. */
    public N remove(Handle<R> handle) {
        var native_ = resources.remove(handle.index());
        pool.release(handle);
        return native_;
    }

    /** Checks if a handle is valid (allocated and not released). */
    public boolean isValid(Handle<R> handle) {
        return pool.isValid(handle);
    }

    /** Number of currently allocated handles. */
    public int size() {
        return pool.allocatedCount();
    }

    /** Iterates all live resources. */
    public void forEach(BiConsumer<Integer, N> action) {
        resources.forEach(action);
    }

    /** Destroys all resources using the given cleanup function, then clears. */
    public void destroyAll(Consumer<N> cleanup) {
        resources.values().forEach(cleanup);
        resources.clear();
    }

    /** Reports leaked handles. */
    public int reportLeaks() {
        return pool.reportLeaks();
    }

    /** Clears all resources without cleanup. */
    public void clear() {
        resources.clear();
    }
}
