package dev.engine.core.profiler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks native resource lifecycle statistics: live totals and per-frame CRUD counters.
 *
 * <p>Each resource type is identified by a string key (e.g. {@code "buffer"}, {@code "texture"}).
 * New resource types can be added at any time — the first call to {@link #recordCreate} or
 * {@link #recordDestroy} with an unknown key auto-registers it.
 *
 * <p>The tracker has two layers:
 * <ul>
 *   <li><b>Live totals</b> — absolute count of currently alive resources, never reset.
 *       Thread-safe (atomic integers).</li>
 *   <li><b>Frame counters</b> — per-frame create/destroy counts, reset each frame via
 *       {@link #resetFrameCounters()}.</li>
 * </ul>
 *
 * <pre>{@code
 * // Register well-known types up front (optional, for discoverability)
 * resourceStats.register("buffer");
 * resourceStats.register("texture");
 *
 * // Record operations — auto-registers if not already known
 * resourceStats.recordCreate("buffer");
 * resourceStats.recordDestroy("buffer");
 *
 * // Query
 * int liveBuffers = resourceStats.liveCount("buffer");
 * int createdThisFrame = resourceStats.frameCreated("buffer");
 * }</pre>
 */
public class ResourceStats {

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    /**
     * Per-resource-type tracking entry.
     * Live total uses AtomicInteger for thread safety (creates/destroys may come from different threads).
     * Frame counters are plain ints — only mutated from the render thread and reset once per frame.
     */
    private static final class Entry {
        final AtomicInteger liveTotal = new AtomicInteger();
        int frameCreated;
        int frameDestroyed;
    }

    /** Pre-registers a resource type. Optional — types are auto-registered on first use. */
    public void register(String resourceType) {
        entries.computeIfAbsent(resourceType, k -> new Entry());
    }

    /** Records a resource creation. Increments live total and frame-created counter. */
    public void recordCreate(String resourceType) {
        var entry = entries.computeIfAbsent(resourceType, k -> new Entry());
        entry.liveTotal.incrementAndGet();
        entry.frameCreated++;
    }

    /** Records a resource destruction. Decrements live total and increments frame-destroyed counter. */
    public void recordDestroy(String resourceType) {
        var entry = entries.computeIfAbsent(resourceType, k -> new Entry());
        entry.liveTotal.decrementAndGet();
        entry.frameDestroyed++;
    }

    /** Returns the current live count for the given resource type, or 0 if unknown. */
    public int liveCount(String resourceType) {
        var entry = entries.get(resourceType);
        return entry != null ? entry.liveTotal.get() : 0;
    }

    /** Returns the number of resources created this frame, or 0 if unknown. */
    public int frameCreated(String resourceType) {
        var entry = entries.get(resourceType);
        return entry != null ? entry.frameCreated : 0;
    }

    /** Returns the number of resources destroyed this frame, or 0 if unknown. */
    public int frameDestroyed(String resourceType) {
        var entry = entries.get(resourceType);
        return entry != null ? entry.frameDestroyed : 0;
    }

    /** Returns the sum of all live resources across all tracked types. */
    public int totalLiveCount() {
        int total = 0;
        for (var entry : entries.values()) {
            total += entry.liveTotal.get();
        }
        return total;
    }

    /** Returns the sum of all frame-created counts across all tracked types. */
    public int totalFrameCreated() {
        int total = 0;
        for (var entry : entries.values()) {
            total += entry.frameCreated;
        }
        return total;
    }

    /** Returns the sum of all frame-destroyed counts across all tracked types. */
    public int totalFrameDestroyed() {
        int total = 0;
        for (var entry : entries.values()) {
            total += entry.frameDestroyed;
        }
        return total;
    }

    /** Returns an unmodifiable snapshot of all tracked resource type names. */
    public java.util.Set<String> resourceTypes() {
        return java.util.Collections.unmodifiableSet(entries.keySet());
    }

    /** Resets all per-frame counters to zero. Call at the start of each frame. */
    public void resetFrameCounters() {
        for (var entry : entries.values()) {
            entry.frameCreated = 0;
            entry.frameDestroyed = 0;
        }
    }

    @Override
    public String toString() {
        var sb = new StringBuilder("ResourceStats{");
        boolean first = true;
        for (var e : entries.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            var v = e.getValue();
            sb.append(e.getKey()).append("=")
              .append(v.liveTotal.get()).append(" live");
            if (v.frameCreated > 0) sb.append(" +").append(v.frameCreated);
            if (v.frameDestroyed > 0) sb.append(" -").append(v.frameDestroyed);
        }
        sb.append("}");
        return sb.toString();
    }
}
