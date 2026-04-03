package dev.engine.providers.teavm.webgpu;

import dev.engine.core.memory.NativeMemory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Web implementation of {@link NativeMemory} backed by a {@code ByteBuffer}.
 *
 * <p>Used by the TeaVM WebGPU backend where {@code java.lang.foreign.MemorySegment}
 * is not available.
 */
public class ByteBufferNativeMemory implements NativeMemory {

    private final ByteBuffer buffer;

    public ByteBufferNativeMemory(int size) {
        this.buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    public ByteBufferNativeMemory(ByteBuffer buffer) {
        this.buffer = buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    /** Returns the underlying ByteBuffer for passing to WebGPU APIs. */
    public ByteBuffer buffer() { return buffer; }

    @Override public void putFloat(long offset, float value) {
        buffer.putFloat((int) offset, value);
    }

    @Override public void putInt(long offset, int value) {
        buffer.putInt((int) offset, value);
    }

    @Override public void putByte(long offset, byte value) {
        buffer.put((int) offset, value);
    }

    @Override public void putShort(long offset, short value) {
        buffer.putShort((int) offset, value);
    }

    @Override public void putLong(long offset, long value) {
        buffer.putLong((int) offset, value);
    }

    @Override public void putDouble(long offset, double value) {
        buffer.putDouble((int) offset, value);
    }

    @Override public float getFloat(long offset) {
        return buffer.getFloat((int) offset);
    }

    @Override public int getInt(long offset) {
        return buffer.getInt((int) offset);
    }

    @Override public byte getByte(long offset) {
        return buffer.get((int) offset);
    }

    @Override public short getShort(long offset) {
        return buffer.getShort((int) offset);
    }

    @Override public long getLong(long offset) {
        return buffer.getLong((int) offset);
    }

    @Override public double getDouble(long offset) {
        return buffer.getDouble((int) offset);
    }

    @Override public long size() {
        return buffer.capacity();
    }

    @Override public void putFloatArray(long offset, float[] data) {
        for (int i = 0; i < data.length; i++) {
            buffer.putFloat((int) offset + i * Float.BYTES, data[i]);
        }
    }

    @Override public void putIntArray(long offset, int[] data) {
        for (int i = 0; i < data.length; i++) {
            buffer.putInt((int) offset + i * Integer.BYTES, data[i]);
        }
    }

    @Override public void copyFrom(NativeMemory src) {
        long len = Math.min(size(), src.size());
        if (src instanceof ByteBufferNativeMemory webSrc) {
            var srcBuf = webSrc.buffer.duplicate();
            srcBuf.position(0).limit((int) len);
            buffer.position(0);
            buffer.put(srcBuf);
            buffer.position(0);
        } else {
            for (long i = 0; i < len; i++) {
                putByte(i, src.getByte(i));
            }
        }
    }

    @Override public NativeMemory slice(long offset, long length) {
        var sliced = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        sliced.position((int) offset).limit((int) (offset + length));
        return new ByteBufferNativeMemory(sliced.slice().order(ByteOrder.LITTLE_ENDIAN));
    }
}
