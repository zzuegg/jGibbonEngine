package dev.engine.core.gpu;

import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec2;
import dev.engine.core.math.Vec3;
import dev.engine.core.math.Vec4;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Reads typed values from a MemorySegment (GPU buffer readback).
 * Works with any buffer: UBOs, SSBOs, mapped buffers, staging buffers.
 *
 * <p>Matrices are read from column-major layout (GPU convention)
 * and returned as row-major Mat4.
 */
public final class BufferReader {

    private BufferReader() {}

    public static float readFloat(MemorySegment seg, long offset) {
        return seg.get(ValueLayout.JAVA_FLOAT, offset);
    }

    public static int readInt(MemorySegment seg, long offset) {
        return seg.get(ValueLayout.JAVA_INT, offset);
    }

    public static boolean readBoolean(MemorySegment seg, long offset) {
        return seg.get(ValueLayout.JAVA_INT, offset) != 0;
    }

    public static Vec2 readVec2(MemorySegment seg, long offset) {
        return new Vec2(
                seg.get(ValueLayout.JAVA_FLOAT, offset),
                seg.get(ValueLayout.JAVA_FLOAT, offset + 4));
    }

    public static Vec3 readVec3(MemorySegment seg, long offset) {
        return new Vec3(
                seg.get(ValueLayout.JAVA_FLOAT, offset),
                seg.get(ValueLayout.JAVA_FLOAT, offset + 4),
                seg.get(ValueLayout.JAVA_FLOAT, offset + 8));
    }

    public static Vec4 readVec4(MemorySegment seg, long offset) {
        return new Vec4(
                seg.get(ValueLayout.JAVA_FLOAT, offset),
                seg.get(ValueLayout.JAVA_FLOAT, offset + 4),
                seg.get(ValueLayout.JAVA_FLOAT, offset + 8),
                seg.get(ValueLayout.JAVA_FLOAT, offset + 12));
    }

    /**
     * Reads a Mat4 from column-major GPU layout back into row-major Mat4.
     */
    public static Mat4 readMat4(MemorySegment seg, long offset) {
        // Column-major: col0(m00,m10,m20,m30), col1(m01,m11,m21,m31), ...
        float c00 = seg.get(ValueLayout.JAVA_FLOAT, offset);
        float c01 = seg.get(ValueLayout.JAVA_FLOAT, offset + 4);
        float c02 = seg.get(ValueLayout.JAVA_FLOAT, offset + 8);
        float c03 = seg.get(ValueLayout.JAVA_FLOAT, offset + 12);
        float c10 = seg.get(ValueLayout.JAVA_FLOAT, offset + 16);
        float c11 = seg.get(ValueLayout.JAVA_FLOAT, offset + 20);
        float c12 = seg.get(ValueLayout.JAVA_FLOAT, offset + 24);
        float c13 = seg.get(ValueLayout.JAVA_FLOAT, offset + 28);
        float c20 = seg.get(ValueLayout.JAVA_FLOAT, offset + 32);
        float c21 = seg.get(ValueLayout.JAVA_FLOAT, offset + 36);
        float c22 = seg.get(ValueLayout.JAVA_FLOAT, offset + 40);
        float c23 = seg.get(ValueLayout.JAVA_FLOAT, offset + 44);
        float c30 = seg.get(ValueLayout.JAVA_FLOAT, offset + 48);
        float c31 = seg.get(ValueLayout.JAVA_FLOAT, offset + 52);
        float c32 = seg.get(ValueLayout.JAVA_FLOAT, offset + 56);
        float c33 = seg.get(ValueLayout.JAVA_FLOAT, offset + 60);
        // Transpose back to row-major
        return new Mat4(
                c00, c10, c20, c30,
                c01, c11, c21, c31,
                c02, c12, c22, c32,
                c03, c13, c23, c33);
    }

    public static long readTextureHandle(MemorySegment seg, long offset) {
        return seg.get(ValueLayout.JAVA_LONG, offset);
    }
}
