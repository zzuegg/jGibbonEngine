package dev.engine.graphics.sync;

/**
 * High-level GPU fence for synchronization.
 * Used to know when the GPU has finished consuming data (e.g. from a streaming buffer).
 */
public interface GpuFence extends AutoCloseable {

    /** Returns true if the GPU has passed this fence (work is complete). */
    boolean isSignaled();

    /** Blocks until the GPU signals this fence. */
    void waitFor();

    /** Blocks with a timeout in nanoseconds. Returns true if signaled. */
    boolean waitFor(long timeoutNanos);

    @Override
    void close();
}
