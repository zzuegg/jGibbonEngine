package dev.engine.graphics.opengl;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.buffer.StreamingBuffer;
import org.lwjgl.opengl.GL45;

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
    private final long frameSize;
    private final int frameCount;
    private final Handle<BufferResource> handle;
    private final int glBuffer;
    private final ByteBuffer mappedByteBuffer;
    private final MemorySegment mappedSegment;
    private int currentFrame;

    GlStreamingBuffer(GlRenderDevice device, long frameSize, int frameCount, BufferUsage usage) {
        this.device = device;
        this.frameSize = frameSize;
        this.frameCount = frameCount;
        this.currentFrame = 0;

        long totalSize = frameSize * frameCount;

        // Create the GL buffer
        this.glBuffer = GL45.glCreateBuffers();

        // Allocate immutable storage with persistent coherent write mapping flags
        int flags = GL45.GL_MAP_WRITE_BIT | GL45.GL_MAP_PERSISTENT_BIT | GL45.GL_MAP_COHERENT_BIT;
        GL45.glNamedBufferStorage(glBuffer, totalSize, flags);

        // Persistently map the entire buffer
        this.mappedByteBuffer = GL45.glMapNamedBufferRange(glBuffer, 0, totalSize, flags);
        if (mappedByteBuffer == null) {
            GL45.glDeleteBuffers(glBuffer);
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
    public MemorySegment beginWrite() {
        long offset = (long) currentFrame * frameSize;
        return mappedSegment.asSlice(offset, frameSize);
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
        GL45.glUnmapNamedBuffer(glBuffer);
        GL45.glDeleteBuffers(glBuffer);
    }
}
