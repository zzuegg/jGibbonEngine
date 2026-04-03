package dev.engine.graphics.opengl;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.buffer.StreamingBuffer;

import dev.engine.core.gpu.GpuMemory;
import dev.engine.core.gpu.NativeGpuMemory;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

/**
 * OpenGL implementation of {@link StreamingBuffer} using persistent mapped buffers.
 *
 * <p>Allocates a single large buffer of size {@code frameSize * frameCount} and
 * persistently maps it with coherent writes. Each frame writes to a different
 * region, avoiding GPU/CPU contention when combined with fences.
 */
public class GlStreamingBuffer implements StreamingBuffer {

    private final GlRenderDevice device;
    private final GlBindings gl;
    private final long frameSize;
    private final int frameCount;
    private final Handle<BufferResource> handle;
    private final int glBuffer;
    private final ByteBuffer mappedByteBuffer;
    private final MemorySegment mappedSegment;
    private int currentFrame;

    GlStreamingBuffer(GlRenderDevice device, long frameSize, int frameCount, BufferUsage usage) {
        this.device = device;
        this.gl = device.glBindings();
        this.frameSize = frameSize;
        this.frameCount = frameCount;
        this.currentFrame = 0;

        long totalSize = frameSize * frameCount;

        // Create the GL buffer
        this.glBuffer = gl.glCreateBuffers();

        // Allocate immutable storage with persistent coherent write mapping flags
        int flags = GlBindings.GL_MAP_WRITE_BIT | GlBindings.GL_MAP_PERSISTENT_BIT | GlBindings.GL_MAP_COHERENT_BIT;
        gl.glNamedBufferStorage(glBuffer, totalSize, flags);

        // Persistently map the entire buffer
        this.mappedByteBuffer = gl.glMapNamedBufferRange(glBuffer, 0, totalSize, flags);
        if (mappedByteBuffer == null) {
            gl.glDeleteBuffers(glBuffer);
            throw new IllegalStateException("Failed to persistently map streaming buffer");
        }
        this.mappedSegment = MemorySegment.ofBuffer(mappedByteBuffer);

        // Register in the device's buffer pool so getGlBufferName() works
        this.handle = device.registerStreamingBuffer(glBuffer, totalSize);
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
    public GpuMemory beginWrite() {
        long offset = (long) currentFrame * frameSize;
        return new NativeGpuMemory(mappedSegment.asSlice(offset, frameSize));
    }

    @Override
    public void endWrite() {
        // No-op: coherent mapping handles visibility automatically
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
        gl.glUnmapNamedBuffer(glBuffer);
        gl.glDeleteBuffers(glBuffer);
    }
}
