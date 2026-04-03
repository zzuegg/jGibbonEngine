package dev.engine.graphics.webgpu;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.buffer.StreamingBuffer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * WebGPU implementation of {@link StreamingBuffer}.
 *
 * <p>Uses a single GPU buffer with per-frame regions. Writes are done via
 * {@code wgpuQueueWriteBuffer} on each {@link #endWrite()}, copying the
 * staging data to the appropriate region of the GPU buffer.
 */
public class WgpuStreamingBuffer implements StreamingBuffer {

    private final WgpuRenderDevice device;
    private final long frameSize;
    private final int frameCount;
    private final Handle<BufferResource> handle;
    private int currentFrame;

    private Arena writeArena;
    private MemorySegment writeSegment;

    WgpuStreamingBuffer(WgpuRenderDevice device, long frameSize, int frameCount, BufferUsage usage) {
        this.device = device;
        this.frameSize = frameSize;
        this.frameCount = frameCount;
        this.currentFrame = 0;

        long totalSize = frameSize * frameCount;
        this.handle = device.createBuffer(new BufferDescriptor(totalSize, usage, AccessPattern.DYNAMIC));
    }

    @Override
    public Handle<BufferResource> handle() {
        return handle;
    }

    @Override
    public long size() {
        return frameSize * frameCount;
    }

    @Override
    public long frameSize() {
        return frameSize;
    }

    @Override
    public MemorySegment beginWrite() {
        writeArena = Arena.ofConfined();
        writeSegment = writeArena.allocate(frameSize);
        return writeSegment;
    }

    @Override
    public void endWrite() {
        if (writeSegment != null) {
            long offset = (long) currentFrame * frameSize;
            try (var writer = device.writeBuffer(handle, offset, frameSize)) {
                writer.segment().copyFrom(writeSegment);
            }
        }
        if (writeArena != null) {
            writeArena.close();
            writeArena = null;
            writeSegment = null;
        }
    }

    @Override
    public long currentOffset() {
        return (long) currentFrame * frameSize;
    }

    @Override
    public void advance() {
        currentFrame = (currentFrame + 1) % frameCount;
    }

    @Override
    public void close() {
        device.destroyBuffer(handle);
    }
}
