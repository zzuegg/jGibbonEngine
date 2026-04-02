package dev.engine.graphics.buffer;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.BufferResource;

import java.lang.foreign.MemorySegment;

/**
 * A streaming buffer for per-frame data uploads.
 *
 * <p>Uses triple-buffering internally: while the GPU reads from one region,
 * the CPU writes to another. Fences ensure no overlap.
 *
 * <p>Backend-specific implementations use persistent mapping (GL) or
 * staging buffers (Vulkan/WebGPU).
 */
public interface StreamingBuffer extends AutoCloseable {

    /** The underlying GPU buffer handle. */
    Handle<BufferResource> handle();

    /** Total buffer size in bytes. */
    long size();

    /** Size of a single frame's region. */
    long frameSize();

    /**
     * Begins writing for the current frame. Returns a MemorySegment
     * pointing to the current frame's region. The segment is valid
     * until {@link #endWrite()} is called.
     */
    MemorySegment beginWrite();

    /** Finishes writing. The data becomes available to the GPU. */
    void endWrite();

    /**
     * Returns the byte offset into the buffer for the current frame's data.
     * Use this for glBindBufferRange offset.
     */
    long currentOffset();

    /** Advances to the next frame. Must be called once per frame. */
    void advance();

    @Override
    void close();
}
