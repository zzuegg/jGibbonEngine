package dev.engine.core.handle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandlePool<T> {

    private static final Logger log = LoggerFactory.getLogger(HandlePool.class);

    private final List<Integer> generations = new ArrayList<>();
    private final Deque<Integer> freeIndices = new ArrayDeque<>();
    private final Object lock = new Object();
    private final String resourceType;
    private int allocatedCount = 0;

    public HandlePool() {
        this("unknown");
    }

    public HandlePool(String resourceType) {
        this.resourceType = resourceType;
    }

    public Handle<T> allocate() {
        synchronized (lock) {
            allocatedCount++;
            if (!freeIndices.isEmpty()) {
                int index = freeIndices.poll();
                int gen = generations.get(index);
                return new Handle<>(index, gen);
            }
            int index = generations.size();
            generations.add(0);
            return new Handle<>(index, 0);
        }
    }

    public void release(Handle<T> handle) {
        synchronized (lock) {
            if (handle.index() < 0 || handle.index() >= generations.size()) return;
            if (generations.get(handle.index()) != handle.generation()) return;
            generations.set(handle.index(), handle.generation() + 1);
            freeIndices.add(handle.index());
            allocatedCount--;
        }
    }

    /**
     * Returns the number of currently allocated (not yet released) handles.
     */
    public int allocatedCount() {
        synchronized (lock) {
            return allocatedCount;
        }
    }

    /**
     * Logs a warning if any handles are still allocated. Call from device.close().
     * @return the number of leaked handles
     */
    public int reportLeaks() {
        int count = allocatedCount();
        if (count > 0) {
            log.warn("  {} {} handle(s) still alive at shutdown", count, resourceType);
        }
        return count;
    }

    public boolean isValid(Handle<T> handle) {
        synchronized (lock) {
            if (handle.index() < 0 || handle.index() >= generations.size()) return false;
            return generations.get(handle.index()) == handle.generation();
        }
    }
}
