package dev.engine.graphics.common;

import dev.engine.core.handle.Handle;
import dev.engine.core.math.Vec2i;
import dev.engine.graphics.RenderTargetResource;
import dev.engine.graphics.TextureResource;
import dev.engine.graphics.target.RenderTargetDescriptor;
import dev.engine.graphics.texture.TextureFormat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages render target lifecycle: creation, resize, and pooling.
 *
 * <p>Supports color-only, color+depth, and multiple render target (MRT) configurations.
 * Named render targets can be resized as a group (e.g., on window resize).
 * Transient render targets are pooled for reuse by the render graph.
 */
public class RenderTargetManager {

    private final GpuResourceManager gpu;
    private final Map<String, ManagedRenderTarget> namedTargets = new HashMap<>();
    private final List<PooledRenderTarget> pool = new ArrayList<>();

    public RenderTargetManager(GpuResourceManager gpu) {
        this.gpu = gpu;
    }

    // --- Named render targets (persistent, resizable) ---

    /** Creates or retrieves a named render target with a single color attachment. */
    public Handle<RenderTargetResource> getOrCreate(String name, int width, int height, TextureFormat colorFormat) {
        return getOrCreate(name, RenderTargetDescriptor.color(width, height, colorFormat));
    }

    /** Creates or retrieves a named render target with color + depth. */
    public Handle<RenderTargetResource> getOrCreate(String name, int width, int height,
                                                     TextureFormat colorFormat, TextureFormat depthFormat) {
        return getOrCreate(name, RenderTargetDescriptor.colorDepth(width, height, colorFormat, depthFormat));
    }

    /** Creates or retrieves a named render target with a full descriptor (MRT, custom formats). */
    public Handle<RenderTargetResource> getOrCreate(String name, RenderTargetDescriptor descriptor) {
        var existing = namedTargets.get(name);
        if (existing != null && existing.matches(descriptor)) {
            return existing.handle;
        }
        // Destroy old if size/format changed
        if (existing != null) {
            gpu.destroyRenderTarget(existing.handle);
        }
        var handle = gpu.createRenderTarget(descriptor);
        namedTargets.put(name, new ManagedRenderTarget(handle, descriptor));
        return handle;
    }

    /** Gets the color texture attachment of a named render target. */
    public Handle<TextureResource> getColorTexture(String name, int index) {
        var managed = namedTargets.get(name);
        if (managed == null) throw new IllegalArgumentException("No render target named: " + name);
        return gpu.getRenderTargetColorTexture(managed.handle, index);
    }

    /** Gets the color texture attachment (index 0) of a named render target. */
    public Handle<TextureResource> getColorTexture(String name) {
        return getColorTexture(name, 0);
    }

    /** Resizes all named render targets that match the old size to the new size. */
    public void resize(Vec2i newSize) {
        for (var entry : new ArrayList<>(namedTargets.entrySet())) {
            var managed = entry.getValue();
            var desc = managed.descriptor;
            if (desc.width() != newSize.x() || desc.height() != newSize.y()) {
                var newDesc = new RenderTargetDescriptor(
                        newSize.x(), newSize.y(), desc.colorAttachments(), desc.depthFormat());
                gpu.destroyRenderTarget(managed.handle);
                var handle = gpu.createRenderTarget(newDesc);
                namedTargets.put(entry.getKey(), new ManagedRenderTarget(handle, newDesc));
            }
        }
    }

    /** Destroys a named render target. */
    public void destroy(String name) {
        var managed = namedTargets.remove(name);
        if (managed != null) {
            gpu.destroyRenderTarget(managed.handle);
        }
    }

    // --- Transient render targets (pooled, for render graph) ---

    /** Acquires a transient render target from the pool, or creates one. */
    public Handle<RenderTargetResource> acquireTransient(RenderTargetDescriptor descriptor) {
        for (int i = 0; i < pool.size(); i++) {
            var pooled = pool.get(i);
            if (!pooled.inUse && pooled.matches(descriptor)) {
                pooled.inUse = true;
                return pooled.handle;
            }
        }
        var handle = gpu.createRenderTarget(descriptor);
        pool.add(new PooledRenderTarget(handle, descriptor, true));
        return handle;
    }

    /** Releases a transient render target back to the pool. */
    public void releaseTransient(Handle<RenderTargetResource> handle) {
        for (var pooled : pool) {
            if (pooled.handle.equals(handle)) {
                pooled.inUse = false;
                return;
            }
        }
    }

    /** Releases all transient render targets (call at frame end). */
    public void releaseAllTransient() {
        for (var pooled : pool) {
            pooled.inUse = false;
        }
    }

    public void close() {
        for (var managed : namedTargets.values()) {
            gpu.destroyRenderTarget(managed.handle);
        }
        namedTargets.clear();
        for (var pooled : pool) {
            gpu.destroyRenderTarget(pooled.handle);
        }
        pool.clear();
    }

    private record ManagedRenderTarget(Handle<RenderTargetResource> handle, RenderTargetDescriptor descriptor) {
        boolean matches(RenderTargetDescriptor other) {
            return descriptor.width() == other.width()
                && descriptor.height() == other.height()
                && descriptor.colorAttachments().equals(other.colorAttachments())
                && java.util.Objects.equals(descriptor.depthFormat(), other.depthFormat());
        }
    }

    private static class PooledRenderTarget {
        final Handle<RenderTargetResource> handle;
        final RenderTargetDescriptor descriptor;
        boolean inUse;

        PooledRenderTarget(Handle<RenderTargetResource> handle, RenderTargetDescriptor descriptor, boolean inUse) {
            this.handle = handle;
            this.descriptor = descriptor;
            this.inUse = inUse;
        }

        boolean matches(RenderTargetDescriptor other) {
            return descriptor.width() == other.width()
                && descriptor.height() == other.height()
                && descriptor.colorAttachments().equals(other.colorAttachments())
                && java.util.Objects.equals(descriptor.depthFormat(), other.depthFormat());
        }
    }
}
