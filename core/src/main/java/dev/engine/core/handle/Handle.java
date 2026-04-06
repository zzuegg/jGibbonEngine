package dev.engine.core.handle;

import dev.engine.core.resource.ResourceCleaner;

import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generational opaque handle with a phantom type parameter for compile-time safety.
 * Supports Cleaner-based automatic resource cleanup when handles become unreachable.
 *
 * @param <T> phantom type tag — prevents mixing handles of different resource types
 */
public final class Handle<T> {

    private static final Handle<?> INVALID_RAW = new Handle<>(-1, 0);

    private final int index;
    private final int generation;

    // Cleaner support: shared between the handle and its clean action.
    // Set to true by markClosed() when the resource is explicitly destroyed.
    // The Cleaner action checks this to avoid double-destroy.
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private Cleaner.Cleanable cleanable;

    public Handle(int index, int generation) {
        this.index = index;
        this.generation = generation;
    }

    public int index() { return index; }
    public int generation() { return generation; }

    /**
     * Registers a Cleaner action that runs when this handle becomes unreachable
     * and has not been explicitly closed. The action must NOT reference this handle.
     */
    public void registerCleanup(Runnable cleanupAction) {
        var closedRef = this.closed;
        this.cleanable = ResourceCleaner.register(this, () -> {
            if (closedRef.compareAndSet(false, true)) {
                cleanupAction.run();
            }
        });
    }

    /**
     * Marks this handle as explicitly closed. Prevents the Cleaner from
     * running the cleanup action. Returns true if this call actually closed it
     * (false if already closed).
     */
    public boolean markClosed() {
        boolean didClose = closed.compareAndSet(false, true);
        if (didClose && cleanable != null) {
            cleanable.clean(); // deregisters from Cleaner
        }
        return didClose;
    }

    public boolean isClosed() {
        return closed.get();
    }

    @SuppressWarnings("unchecked")
    public static <T> Handle<T> invalid() {
        return (Handle<T>) INVALID_RAW;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Handle<?> h && h.index == index && h.generation == generation;
    }

    @Override
    public int hashCode() {
        return 31 * index + generation;
    }

    @Override
    public String toString() {
        return "Handle[index=" + index + ", generation=" + generation + "]";
    }
}
