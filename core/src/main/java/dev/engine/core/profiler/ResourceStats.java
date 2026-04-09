package dev.engine.core.profiler;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks native resource lifecycle statistics: live totals and per-frame operation counters.
 *
 * <p>Each resource type is identified by a string key (e.g. {@code "buffer"}, {@code "texture"}).
 * New resource types can be added at any time — the first call to any {@code record*} method
 * with an unknown key auto-registers it.
 *
 * <p>The tracker has two layers:
 * <ul>
 *   <li><b>Live totals</b> — absolute count of currently alive resources, never reset.
 *       Thread-safe (atomic integers).</li>
 *   <li><b>Frame counters</b> — per-frame create/destroy/use/update counts with a
 *       current/last frame model. Call {@link #newFrame()} at the start of each frame
 *       to swap: current becomes last (readable by consumers), current resets to zero.</li>
 * </ul>
 *
 * <p>Operations tracked per frame:
 * <ul>
 *   <li><b>created</b> — new resources allocated this frame</li>
 *   <li><b>destroyed</b> — resources freed this frame (when deferred deletion actually runs)</li>
 *   <li><b>used</b> — resources bound/referenced for reading (e.g. texture sampled, buffer bound)</li>
 *   <li><b>updated</b> — resources written to (e.g. buffer upload, texture data update)</li>
 * </ul>
 *
 * <pre>{@code
 * // Record operations during the frame:
 * resourceStats.recordCreate("buffer");
 * resourceStats.recordUpdate("buffer");   // wrote data into it
 * resourceStats.recordUse("texture");     // bound for sampling
 *
 * // At frame start:
 * resourceStats.newFrame();
 *
 * // Read completed previous frame:
 * int buffersCreated = resourceStats.lastFrameCreated("buffer");
 * int texturesUsed   = resourceStats.lastFrameUsed("texture");
 *
 * // Live totals are always current:
 * int liveBuffers = resourceStats.liveCount("buffer");
 * }</pre>
 */
public class ResourceStats {

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    /**
     * Per-resource-type tracking entry.
     * Live total uses AtomicInteger for thread safety.
     * Frame counters are plain ints — only mutated from the render thread.
     */
    static final class Entry {
        final AtomicInteger liveTotal = new AtomicInteger();

        // Current (in-progress) frame counters — atomic for cross-thread safety
        final AtomicInteger created = new AtomicInteger();
        final AtomicInteger destroyed = new AtomicInteger();
        final AtomicInteger used = new AtomicInteger();
        final AtomicInteger updated = new AtomicInteger();

        // Last (completed) frame counters — safe to read from any thread
        volatile int lastCreated;
        volatile int lastDestroyed;
        volatile int lastUsed;
        volatile int lastUpdated;

        void swapFrame() {
            lastCreated = created.getAndSet(0);
            lastDestroyed = destroyed.getAndSet(0);
            lastUsed = used.getAndSet(0);
            lastUpdated = updated.getAndSet(0);
        }
    }

    // --- Registration ---

    /** Pre-registers a resource type. Optional — types are auto-registered on first use. */
    public void register(String resourceType) {
        entries.computeIfAbsent(resourceType, k -> new Entry());
    }

    /** Returns an unmodifiable view of all tracked resource type names. */
    public Set<String> resourceTypes() {
        // Cast to Map to avoid ConcurrentHashMap.keySet() returning KeySetView,
        // which is not available in TeaVM's classlib.
        return Collections.unmodifiableSet(((java.util.Map<String, Entry>) entries).keySet());
    }

    // --- Record operations (call during the frame) ---

    /** Records a resource creation. Increments live total and current frame created counter. */
    public void recordCreate(String resourceType) {
        var entry = entries.computeIfAbsent(resourceType, k -> new Entry());
        entry.liveTotal.incrementAndGet();
        entry.created.incrementAndGet();
    }

    /** Records a resource destruction. Decrements live total and increments current frame destroyed counter. */
    public void recordDestroy(String resourceType) {
        var entry = entries.computeIfAbsent(resourceType, k -> new Entry());
        entry.liveTotal.decrementAndGet();
        entry.destroyed.incrementAndGet();
    }

    /** Records a resource being used (bound/read) this frame. */
    public void recordUse(String resourceType) {
        var entry = entries.computeIfAbsent(resourceType, k -> new Entry());
        entry.used.incrementAndGet();
    }

    /** Records a resource being updated (written/uploaded) this frame. */
    public void recordUpdate(String resourceType) {
        var entry = entries.computeIfAbsent(resourceType, k -> new Entry());
        entry.updated.incrementAndGet();
    }

    // --- Frame swap ---

    /**
     * Swaps frame counters: current becomes last, current resets to zero.
     * Call once at the start of each frame, before any record operations.
     */
    public void newFrame() {
        for (var entry : entries.values()) {
            entry.swapFrame();
        }
    }

    // --- Live totals (always current) ---

    /** Returns the current live count for the given resource type, or 0 if unknown. */
    public int liveCount(String resourceType) {
        var entry = entries.get(resourceType);
        return entry != null ? entry.liveTotal.get() : 0;
    }

    /** Returns the sum of all live resources across all tracked types. */
    public int totalLiveCount() {
        int total = 0;
        for (var entry : entries.values()) {
            total += entry.liveTotal.get();
        }
        return total;
    }

    // --- Last (completed) frame counters ---

    public int lastFrameCreated(String resourceType) {
        var entry = entries.get(resourceType);
        return entry != null ? entry.lastCreated : 0;
    }

    public int lastFrameDestroyed(String resourceType) {
        var entry = entries.get(resourceType);
        return entry != null ? entry.lastDestroyed : 0;
    }

    public int lastFrameUsed(String resourceType) {
        var entry = entries.get(resourceType);
        return entry != null ? entry.lastUsed : 0;
    }

    public int lastFrameUpdated(String resourceType) {
        var entry = entries.get(resourceType);
        return entry != null ? entry.lastUpdated : 0;
    }

    public int totalLastFrameCreated() {
        int total = 0;
        for (var entry : entries.values()) total += entry.lastCreated;
        return total;
    }

    public int totalLastFrameDestroyed() {
        int total = 0;
        for (var entry : entries.values()) total += entry.lastDestroyed;
        return total;
    }

    public int totalLastFrameUsed() {
        int total = 0;
        for (var entry : entries.values()) total += entry.lastUsed;
        return total;
    }

    public int totalLastFrameUpdated() {
        int total = 0;
        for (var entry : entries.values()) total += entry.lastUpdated;
        return total;
    }

    // --- Current (in-progress) frame counters ---

    public int currentFrameCreated(String resourceType) {
        var entry = entries.get(resourceType);
        return entry != null ? entry.created.get() : 0;
    }

    public int currentFrameDestroyed(String resourceType) {
        var entry = entries.get(resourceType);
        return entry != null ? entry.destroyed.get() : 0;
    }

    public int currentFrameUsed(String resourceType) {
        var entry = entries.get(resourceType);
        return entry != null ? entry.used.get() : 0;
    }

    public int currentFrameUpdated(String resourceType) {
        var entry = entries.get(resourceType);
        return entry != null ? entry.updated.get() : 0;
    }

    @Override
    public String toString() {
        var sb = new StringBuilder("ResourceStats{");
        boolean first = true;
        for (var e : entries.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            var v = e.getValue();
            sb.append(e.getKey()).append("=").append(v.liveTotal.get()).append(" live");
            if (v.lastCreated > 0) sb.append(" +").append(v.lastCreated);
            if (v.lastDestroyed > 0) sb.append(" -").append(v.lastDestroyed);
            if (v.lastUsed > 0) sb.append(" ~").append(v.lastUsed).append("used");
            if (v.lastUpdated > 0) sb.append(" ^").append(v.lastUpdated).append("upd");
        }
        sb.append("}");
        return sb.toString();
    }
}
