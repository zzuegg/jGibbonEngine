package dev.engine.core.memory;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Desktop implementation of {@link NativeMemory} backed by a {@code MemorySegment}.
 *
 * <p>Used by all native backends (OpenGL, Vulkan, desktop WebGPU) where
 * the JVM has direct memory access via the Foreign Function & Memory API.
 */
public class SegmentNativeMemory implements NativeMemory {

    private final MemorySegment segment;

    public SegmentNativeMemory(MemorySegment segment) {
        this.segment = segment;
    }

    /** Returns the underlying {@code MemorySegment} for backends that need raw access. */
    public MemorySegment segment() { return segment; }

    @Override public void putFloat(long offset, float value) {
        segment.set(ValueLayout.JAVA_FLOAT, offset, value);
    }

    @Override public void putInt(long offset, int value) {
        segment.set(ValueLayout.JAVA_INT, offset, value);
    }

    @Override public void putByte(long offset, byte value) {
        segment.set(ValueLayout.JAVA_BYTE, offset, value);
    }

    @Override public void putShort(long offset, short value) {
        segment.set(ValueLayout.JAVA_SHORT, offset, value);
    }

    @Override public void putLong(long offset, long value) {
        segment.set(ValueLayout.JAVA_LONG, offset, value);
    }

    @Override public void putDouble(long offset, double value) {
        segment.set(ValueLayout.JAVA_DOUBLE, offset, value);
    }

    @Override public float getFloat(long offset) {
        return segment.get(ValueLayout.JAVA_FLOAT, offset);
    }

    @Override public int getInt(long offset) {
        return segment.get(ValueLayout.JAVA_INT, offset);
    }

    @Override public byte getByte(long offset) {
        return segment.get(ValueLayout.JAVA_BYTE, offset);
    }

    @Override public short getShort(long offset) {
        return segment.get(ValueLayout.JAVA_SHORT, offset);
    }

    @Override public long getLong(long offset) {
        return segment.get(ValueLayout.JAVA_LONG, offset);
    }

    @Override public double getDouble(long offset) {
        return segment.get(ValueLayout.JAVA_DOUBLE, offset);
    }

    @Override public long size() {
        return segment.byteSize();
    }

    @Override public void putFloatArray(long offset, float[] data) {
        MemorySegment.copy(data, 0, segment, ValueLayout.JAVA_FLOAT, offset, data.length);
    }

    @Override public void putIntArray(long offset, int[] data) {
        MemorySegment.copy(data, 0, segment, ValueLayout.JAVA_INT, offset, data.length);
    }

    @Override public void copyFrom(NativeMemory src) {
        if (src instanceof SegmentNativeMemory native_) {
            segment.copyFrom(native_.segment);
        } else {
            // Fallback: byte-by-byte copy
            long len = Math.min(size(), src.size());
            for (long i = 0; i < len; i++) {
                putByte(i, src.getByte(i));
            }
        }
    }

    @Override public NativeMemory slice(long offset, long length) {
        return new SegmentNativeMemory(segment.asSlice(offset, length));
    }
}
