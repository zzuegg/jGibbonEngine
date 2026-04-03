package dev.engine.core.gpu;

/**
 * Platform-agnostic interface for reading and writing GPU-mapped memory.
 *
 * <p>Desktop: backed by {@code MemorySegment} (via {@link NativeGpuMemory}).
 * Web: backed by {@code ByteBuffer} or JS typed array.
 */
public interface GpuMemory {

    void putFloat(long offset, float value);
    void putInt(long offset, int value);
    void putByte(long offset, byte value);
    void putShort(long offset, short value);
    void putLong(long offset, long value);
    void putDouble(long offset, double value);

    float getFloat(long offset);
    int getInt(long offset);
    byte getByte(long offset);
    short getShort(long offset);
    long getLong(long offset);
    double getDouble(long offset);

    long size();

    /** Bulk write of a float array starting at the given byte offset. */
    void putFloatArray(long offset, float[] data);

    /** Bulk write of an int array starting at the given byte offset. */
    void putIntArray(long offset, int[] data);

    /** Copy all bytes from {@code src} into this memory at offset 0. */
    void copyFrom(GpuMemory src);

    /** Returns a view of a sub-region of this memory. */
    GpuMemory slice(long offset, long length);
}
